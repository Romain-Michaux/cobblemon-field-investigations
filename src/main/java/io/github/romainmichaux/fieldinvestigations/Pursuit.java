package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.HiddenAbilityProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Loop run twice per second for each investigator, and spawning of the target.
final class Pursuit {
    private Pursuit() {}

    static void follow(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        if (d == null || !d.dimension.equals(Engine.dimension(player))) return;
        boolean kit = player.isHolding(ModContent.KIT);
        long now = System.currentTimeMillis();
        ServerLevel level = player.serverLevel();
        Packet signal = Packet.from("signal");
        signal.data().putString("phase", d.phase);
        switch (d.phase) {
            case "SAMPLING": {
                boolean asleep = Engine.asleep(d, level);
                int range = Engine.rank(player) >= 1 ? 14 : 9; // rank 1 perk: sharper radar
                double min = Double.MAX_VALUE;
                Trace nearest = null;
                for (Trace t : d.traces) {
                    if (t.over()) continue;
                    double dist = Engine.distance(player, t.x, t.z);
                    if (dist < 56 && !asleep) Traces.placeTrace(level, d, t);
                    if (dist < min) {
                        min = dist;
                        nearest = t;
                    }
                    // Traces are subtle: with the kit in hand, those in range sparkle (for this player only).
                    boolean hidden = t.buried && !t.uncovered;
                    if (kit && !asleep && t.placed && dist < range && (!hidden || level.getBlockState(new BlockPos(t.px, t.py + 1, t.pz)).isAir())) {
                        level.sendParticles(player, ParticleTypes.WAX_OFF, false, t.px + 0.5, t.py + (hidden ? 1.15 : 0.4), t.pz + 0.5,
                                2, 0.25, 0.1, 0.25, 0.0);
                    }
                }
                if (player.isUsingItem() && player.getUseItem().is(Items.BRUSH)) Traces.brush(player, d, level);
                if (!kit) return;
                // Cave trace (or underground investigation): the distance includes height, otherwise the radar would say
                // "right here" to a player walking fifteen blocks above.
                boolean inCave = nearest != null && nearest.placed && (nearest.cave || (Engine.is(d, "underground") && nearest.failures < 6));
                if (inCave) min = Math.sqrt(player.distanceToSqr(nearest.px + 0.5, nearest.py + 0.5, nearest.pz + 0.5));
                signal.data().putBoolean("day", asleep);
                signal.data().putInt("level", min == Double.MAX_VALUE ? -1 : min > 90 ? 0 : min > 60 ? 1 : min > 35 ? 2 : min > 14 ? 3 : min > 5 ? 4 : 5);
                boolean buried = nearest != null && nearest.buried && !nearest.uncovered;
                signal.data().putBoolean("buried", buried);
                // For a buried clue the kit gives the exact 3D distance, so the player can dig as close as possible.
                if (buried && nearest.placed) {
                    signal.data().putDouble("metres", Math.sqrt(player.distanceToSqr(nearest.px + 0.5, nearest.py + 0.5, nearest.pz + 0.5)));
                    signal.data().putInt("below", (int) Math.round(player.getY() - (nearest.py + 1)));
                }
                // The radar also gives the real distance and depth of the cave trace.
                if (inCave) {
                    signal.data().putBoolean("cave", true);
                    signal.data().putDouble("metres", Math.sqrt(player.distanceToSqr(nearest.px + 0.5, nearest.py + 0.5, nearest.pz + 0.5)));
                    signal.data().putInt("below", (int) Math.round(player.getY() - nearest.py));
                }
                signal.data().putInt("zone", (int) Engine.distance(player, d.zoneX, d.zoneZ));
                Messages.send(player, signal);
                return;
            }
            case "TRACKING": {
                if (d.nextMove > 0 && now > d.nextMove) {
                    Tracking.move(d, level, 70, 120);
                    Engine.save();
                    Messages.announce(player, "target_moved", 0xF2C93A);
                    Messages.sync(player);
                }
                boolean asleep = Engine.asleep(d, level);
                if (!asleep && Engine.distance(player, d.lairX, d.lairZ) < Engine.settings.approachRadius) {
                    spawnTarget(player, d, level);
                } else if (kit) {
                    signal.data().putBoolean("day", asleep);
                    signal.data().putBoolean("ready", now >= d.nextBearing);
                    Messages.send(player, signal);
                }
                return;
            }
            case "SPOTTING":
            case "APPROACH":
            case "FLUSHED": {
                PokemonEntity e = Engine.ENTITIES.get(d.leader);
                if (e == null && d.entity != null && level.getEntity(d.entity) instanceof PokemonEntity found) {
                    e = found; // server restarted: the specimen is still there
                    Engine.ENTITIES.put(d.leader, e);
                }
                if (e == null || e.isRemoved() || !e.isAlive()) {
                    Engine.ENTITIES.remove(d.leader);
                    boolean dead = e != null && e.getHealth() <= 0f;
                    if (dead && defeatedByPlayer(player, d, e, now)) {
                        CaseClosing.solve(player, d, false);
                    } else if (Engine.caseFile(player) == d) {
                        // Gone without the player defeating or catching it (too far, server restart, accident, another
                        // creature...): the investigation goes on, with no penalty and no loot. If it died, it is located elsewhere.
                        d.phase = "TRACKING";
                        d.announceApproach = false;
                        Engine.resetScan(d);
                        if (dead) Tracking.move(d, level, 60, 110);
                        Engine.save();
                        Messages.announce(player, dead ? "target_vanished" : "contact_lost", 0xF2C93A);
                        Messages.sync(player);
                    }
                    return;
                }
                double dist = Math.sqrt(player.distanceToSqr(e));
                boolean spotting = "SPOTTING".equals(d.phase);
                if (e.isBattling() && dist < 64) d.lastBattle = now;
                if (spotting || "APPROACH".equals(d.phase)) {
                    // The target flees if approached before being identified, or by a sprinting player once identified.
                    boolean sensed = spotting && dist < Engine.alert(d);
                    boolean heard = !spotting && dist < Engine.alert(d) && player.isSprinting();
                    if ((sensed || heard) && !e.isBattling()) {
                        e.discard();
                        Engine.ENTITIES.remove(d.leader);
                        d.phase = "TRACKING";
                        d.flees++;
                        Engine.resetScan(d);
                        Tracking.move(d, level, 80, 130);
                        Engine.save();
                        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1.4f);
                        Messages.announce(player, sensed ? "sensed" : "heard", 0xE8543A, Engine.alert(d), Messages.starsText(Engine.stars(d, false)));
                        Messages.sync(player);
                        return;
                    }
                    if (spotting) {
                        int scanDuration = Engine.scanDuration(player);
                        int[] scan = Engine.SCANS.computeIfAbsent(player.getUUID(), k -> new int[2]);
                        if (e.isBattling()) scan[0] = scanDuration; // battle started from afar: the target counts as identified
                        else if (player.isUsingItem() && player.getUseItem().is(ModContent.KIT)) updateScan(player, scan, level, e);
                        else scan[0] = Math.max(0, scan[0] - 1);
                        if (scan[0] >= scanDuration) {
                            d.phase = "APPROACH";
                            Engine.resetScan(d);
                            e.setInvulnerable(false);
                            Engine.save();
                            player.stopUsingItem();
                            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.5f);
                            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.3f);
                            boolean correct = d.species.equals(d.guess);
                            if (correct) Engine.grantAdvancement(player, "keen_nose");
                            Component titleId = Messages.tr("announce.identified.title", Messages.starsText(Engine.stars(d)));
                            Component textId = Messages.tr("announce.identified.text", Engine.speciesName(d.species), Messages.tr(correct ? "announce.identified.right" : "announce.identified.wrong"), Engine.perfectIvs(Engine.stars(d)));
                            for (ServerPlayer m : Engine.online(d, player.server)) Messages.announce(m, titleId, textId, 0xF2C93A);
                            Messages.sync(player);
                            return;
                        }
                        signal.data().putInt("distance", (int) Math.round(dist));
                        signal.data().putInt("scan", scan[0]);
                        signal.data().putInt("scanMax", scanDuration);
                        signal.data().putBoolean("other", scan[1] > 0);
                        signal.data().putInt("alert", Engine.alert(d));
                        Messages.send(player, signal);
                        return;
                    }
                    if (dist < Engine.settings.flushOutRadius || e.isBattling()) {
                        d.phase = "FLUSHED";
                        d.glowEnd = now + 12_000;
                        e.setGlowingTag(true);
                        Engine.save();
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1f);
                        Messages.announce(player, "flushed", 0xF2C93A, Engine.speciesName(d.species), Engine.perfectIvs(Engine.stars(d)));
                        Messages.sync(player);
                        return;
                    }
                    signal.data().putInt("distance", (int) Math.round(dist));
                    signal.data().putBoolean("sprinting", player.isSprinting());
                    Messages.send(player, signal);
                } else if (d.glowEnd > 0 && now > d.glowEnd) {
                    d.glowEnd = 0;
                    e.setGlowingTag(false);
                }
                return;
            }
            default:
        }
    }

    // A fainted target only counts as a win if it was identified and the player really defeated it:
    // in a Cobblemon battle just before, or hit by them (or by one of their Pokémon).
    private static boolean defeatedByPlayer(ServerPlayer player, CaseFile d, PokemonEntity e, long now) {
        if ("SPOTTING".equals(d.phase)) return false;
        if (now - d.lastBattle < 5000) return true;
        var killer = e.getKillCredit();
        for (UUID u : Engine.members(d)) {
            if (killer instanceof ServerPlayer p && p.getUUID().equals(u)) return true;
            if (killer instanceof PokemonEntity other && u.equals(other.getOwnerUUID())) return true;
        }
        return false;
    }

    private static boolean aimed(ServerPlayer player, PokemonEntity e) {
        Vec3 eye = player.getEyePosition(), towards = e.getBoundingBox().getCenter().subtract(eye);
        double dist = towards.length();
        if (dist < 0.5 || dist > 64) return false;
        double angle = Math.acos(Math.max(-1, Math.min(1, player.getLookAngle().dot(towards.normalize()))));
        double tolerance = Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5);
        return angle <= tolerance && player.hasLineOfSight(e);
    }

    // Kit scan (right click held, crosshair on the Pokémon): three seconds on the target to identify it.
    // Another Pokémon in the crosshair is just reported as not being the target.
    private static void updateScan(ServerPlayer player, int[] scan, ServerLevel level, PokemonEntity target) {
        if (aimed(player, target)) {
            scan[1] = 0;
            scan[0]++; // the beeps are played client-side, getting faster and higher-pitched as the scan progresses
            return;
        }
        scan[0] = Math.max(0, scan[0] - 1);
        boolean other = false;
        for (PokemonEntity p : level.getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(48), x -> x != target)) {
            if (aimed(player, p)) {
                other = true;
                break;
            }
        }
        scan[1] = other ? 3 : Math.max(0, scan[1] - 1);
    }

    private static void spawnTarget(ServerPlayer player, CaseFile d, ServerLevel level) {
        Species s = PokemonSpecies.getByName(d.species);
        if (s == null) {
            CaseClosing.finish(player, d, Messages.tr("announce.cancelled.title"), Messages.tr("announce.cancelled.text"), 0xE8543A);
            return;
        }
        Pokemon p = new Pokemon();
        p.setSpecies(s);
        p.setLevel(Math.max(1, d.level));
        p.initialize();
        int stars = Engine.stars(d);
        List<Stat> stats = new ArrayList<>(List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));
        Collections.shuffle(stats, Engine.RANDOM);
        // The better the investigation, the better the Pokémon: one perfect stat (IV 31) per star beyond the first.
        for (int i = 0; i < Math.min(stats.size(), Engine.perfectIvs(stars)); i++) p.setIV(stats.get(i), 31);
        float shinyRate = Cobblemon.INSTANCE.getConfig().getShinyRate();
        double shiny = (1 + Engine.settings.shinyMultiplierPerStar * stars) * (Engine.is(d, "nocturnal") ? 2 : 1);
        if (shinyRate > 0 && Engine.RANDOM.nextDouble() < shiny / shinyRate) p.setShiny(true);
        if (Engine.is(d, "exceptional")) {
            try {
                new HiddenAbilityProperty(true).apply(p); // no effect if the species has no hidden ability
            } catch (Exception e) {
                System.err.println("[Field Investigations] Could not give hidden ability: " + e);
            }
        }
        // It spawns on solid ground: never in water, lava or above the void.
        int tx = (int) Math.floor(d.lairX), tz = (int) Math.floor(d.lairZ);
        BlockPos ground = Engine.is(d, "aquatic") ? openWater(level, tx, tz) : Engine.is(d, "underground") ? Traces.cave(level, tx, tz, 12) : solidGround(level, tx, tz);
        if (ground == null && Engine.is(d, "underground")) ground = solidGround(level, tx, tz); // no cave around here: it comes out on the surface
        if (ground == null) {
            Tracking.move(d, level, 40, 80); // nowhere usable here: it is located a bit further away
            Engine.save();
            Messages.sync(player);
            return;
        }
        PokemonEntity e = new PokemonEntity(level, p, CobblemonEntities.POKEMON);
        e.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Engine.RANDOM.nextFloat() * 360f, 0f);
        // Until identified, nothing can hurt it (falling, drowning, other creatures).
        e.setInvulnerable(true);
        level.addFreshEntity(e);
        Engine.ENTITIES.put(d.leader, e);
        d.pokemon = p.getUuid();
        d.entity = e.getUUID();
        d.phase = "SPOTTING";
        Engine.resetScan(d);
        Engine.save();
        if (!d.announceApproach) {
            d.announceApproach = true;
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.6f);
            Messages.announce(player, "very_close", 0xF2C93A, Engine.alert(d));
        }
        Messages.sync(player);
    }

    // For an aquatic target: a spot with at least two blocks of water above the floor.
    private static BlockPos openWater(ServerLevel level, int bx, int bz) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos.above()).is(Blocks.WATER)) return pos;
                }
            }
        }
        return null;
    }

    private static BlockPos solidGround(ServerLevel level, int bx, int bz) {
        for (int radius = 0; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                    if (!level.getBlockState(pos).getFluidState().isEmpty() || !level.getBlockState(pos.above()).getFluidState().isEmpty()) continue;
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) continue;
                    return pos;
                }
            }
        }
        return null;
    }
}
