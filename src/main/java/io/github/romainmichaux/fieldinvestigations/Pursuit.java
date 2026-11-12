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

// Boucle exécutée deux fois par seconde pour chaque enquêteur, et apparition de la cible.
final class Pursuit {
    private Pursuit() {}

    static void follow(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        if (d == null || !d.level1.equals(Engine.dimension(player))) return;
        boolean kit = player.isHolding(PacketContent.KIT);
        long now = System.currentTimeMillis();
        ServerLevel level1 = player.serverLevel();
        Packet signal = Packet.from("signal");
        signal.data().putString("phase", d.phase);
        switch (d.phase) {
            case "SAMPLING": {
                boolean day = Engine.asleep(d, level1);
                int range = Engine.rank(player) >= 1 ? 14 : 9; // radar affiné
                double min = Double.MAX_VALUE;
                Trace close = null;
                for (Trace t : d.traces) {
                    if (t.over()) continue;
                    double dist = Engine.distance(player, t.x, t.z);
                    if (dist < 56 && !day) Traces.placeIt(level1, d, t);
                    if (dist < min) {
                        min = dist;
                        close = t;
                    }
                    // Les traces sont discrètes : kit en main, celles qui sont à portée scintillent (pour ce joueur seulement).
                    boolean hidden = t.buried && !t.uncovered;
                    if (kit && !day && t.placedOne && dist < range && (!hidden || level1.getBlockState(new BlockPos(t.px, t.py + 1, t.pz)).isAir())) {
                        level1.sendParticles(player, ParticleTypes.WAX_OFF, false, t.px + 0.5, t.py + (hidden ? 1.15 : 0.4), t.pz + 0.5,
                                2, 0.25, 0.1, 0.25, 0.0);
                    }
                }
                if (player.isUsingItem() && player.getUseItem().is(Items.BRUSH)) Traces.brush(player, d, level1);
                if (!kit) return;
                // Trace en grotte (ou enquête souterraine) : la distance compte la hauteur, sinon le radar crierait « juste
                // ici » à un joueur qui marche quinze blocs au-dessus.
                boolean inCave = close != null && close.placedOne && (close.cave || (Engine.is(d, "underground") && close.failures < 6));
                if (inCave) min = Math.sqrt(player.distanceToSqr(close.px + 0.5, close.py + 0.5, close.pz + 0.5));
                signal.data().putBoolean("day", day);
                signal.data().putInt("level", min == Double.MAX_VALUE ? -1 : min > 90 ? 0 : min > 60 ? 1 : min > 35 ? 2 : min > 14 ? 3 : min > 5 ? 4 : 5);
                boolean underground = close != null && close.buried && !close.uncovered;
                signal.data().putBoolean("buried", underground);
                // Pour un indice enfoui, le kit donne la distance exacte (en trois dimensions) : on creuse au plus près.
                if (underground && close.placedOne) {
                    signal.data().putDouble("metres", Math.sqrt(player.distanceToSqr(close.px + 0.5, close.py + 0.5, close.pz + 0.5)));
                    signal.data().putInt("below", (int) Math.round(player.getY() - (close.py + 1)));
                }
                // Le radar donne aussi la distance réelle et la profondeur de la trace en grotte.
                if (inCave) {
                    signal.data().putBoolean("cave", true);
                    signal.data().putDouble("metres", Math.sqrt(player.distanceToSqr(close.px + 0.5, close.py + 0.5, close.pz + 0.5)));
                    signal.data().putInt("below", (int) Math.round(player.getY() - close.py));
                }
                signal.data().putInt("zone", (int) Engine.distance(player, d.zoneX, d.zoneZ));
                Messages.send(player, signal);
                return;
            }
            case "TRACKING": {
                if (d.nextMove > 0 && now > d.nextMove) {
                    Tracking.move(d, level1, 70, 120);
                    Engine.save();
                    Messages.announce(player, "target_moved", 0xF2C93A);
                    Messages.sync(player);
                }
                boolean day = Engine.asleep(d, level1);
                if (!day && Engine.distance(player, d.lairX, d.lairZ) < Engine.settings.approachRadius) {
                    spawnTarget(player, d, level1);
                } else if (kit) {
                    signal.data().putBoolean("day", day);
                    signal.data().putBoolean("ready", now >= d.nextBearing);
                    Messages.send(player, signal);
                }
                return;
            }
            case "SPOTTING":
            case "APPROACH":
            case "FLUSHED": {
                PokemonEntity e = Engine.ENTITIES.get(d.leader);
                if (e == null && d.entity != null && level1.getEntity(d.entity) instanceof PokemonEntity found) {
                    e = found; // serveur relancé : le spécimen est toujours là
                    Engine.ENTITIES.put(d.leader, e);
                }
                if (e == null || e.isRemoved() || !e.isAlive()) {
                    Engine.ENTITIES.remove(d.leader);
                    boolean dead = e != null && e.getHealth() <= 0f;
                    if (dead && defeatedByPlayer(player, d, e, now)) {
                        CaseClosing.solve(player, d, false);
                    } else if (Engine.caseFile(player) == d) {
                        // Disparue sans que le joueur l'ait vaincue ni capturée (trop loin, serveur relancé, accident, autre
                        // créature...) : l'enquête continue, sans pénalité ni butin. Si elle est morte, elle est repérée ailleurs.
                        d.phase = "TRACKING";
                        d.announceApproach = false;
                        Engine.resetScan(d);
                        if (dead) Tracking.move(d, level1, 60, 110);
                        Engine.save();
                        Messages.announce(player, dead ? "target_vanished" : "contact_lost", 0xF2C93A);
                        Messages.sync(player);
                    }
                    return;
                }
                double dist = Math.sqrt(player.distanceToSqr(e));
                boolean reperage = "SPOTTING".equals(d.phase);
                if (e.isBattling() && dist < 64) d.lastBattle = now;
                if (reperage || "APPROACH".equals(d.phase)) {
                    // La cible file si on l'approche avant de l'avoir identifiée, ou en courant une fois identifiée.
                    boolean sensed = reperage && dist < Engine.alert(d);
                    boolean heard = !reperage && dist < Engine.alert(d) && player.isSprinting();
                    if ((sensed || heard) && !e.isBattling()) {
                        e.discard();
                        Engine.ENTITIES.remove(d.leader);
                        d.phase = "TRACKING";
                        d.leaks++;
                        Engine.resetScan(d);
                        Tracking.move(d, level1, 80, 130);
                        Engine.save();
                        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1.4f);
                        Messages.announce(player, sensed ? "sensed" : "heard", 0xE8543A, Engine.alert(d), Messages.starsText(Engine.stars(d, false)));
                        Messages.sync(player);
                        return;
                    }
                    if (reperage) {
                        int scanDuration = Engine.scanDuration(player);
                        int[] scan = Engine.SCANS.computeIfAbsent(player.getUUID(), k -> new int[2]);
                        if (e.isBattling()) scan[0] = scanDuration; // combat engagé de loin : la cible est de fait identifiée
                        else if (player.isUsingItem() && player.getUseItem().is(PacketContent.KIT)) scanner(player, scan, level1, e);
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
                            if (correct) Engine.progress1(player, "keen_nose");
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
                    signal.data().putBoolean("course", player.isSprinting());
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

    // Une cible K.O. ne vaut victoire que si elle était identifiée et que c'est bien le joueur qui l'a vaincue :
    // en combat Cobblemon juste avant, ou frappée par lui (ou par un de ses Pokémon).
    private static boolean defeatedByPlayer(ServerPlayer player, CaseFile d, PokemonEntity e, long now) {
        if ("SPOTTING".equals(d.phase)) return false;
        if (now - d.lastBattle < 5000) return true;
        var author = e.getKillCredit();
        for (UUID u : Engine.members(d)) {
            if (author instanceof ServerPlayer p && p.getUUID().equals(u)) return true;
            if (author instanceof PokemonEntity other && u.equals(other.getOwnerUUID())) return true;
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

    // Scan au kit (clic droit maintenu, viseur sur le Pokémon) : trois secondes sur la cible pour l'identifier.
    // Un autre Pokémon dans le viseur est simplement signalé comme n'étant pas la cible.
    private static void scanner(ServerPlayer player, int[] scan, ServerLevel level1, PokemonEntity target) {
        if (aimed(player, target)) {
            scan[1] = 0;
            scan[0]++; // les bips sont joués par le client, qui les accélère et les rend plus aigus au fil de l'analyse
            return;
        }
        scan[0] = Math.max(0, scan[0] - 1);
        boolean other = false;
        for (PokemonEntity p : level1.getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(48), x -> x != target)) {
            if (aimed(player, p)) {
                other = true;
                break;
            }
        }
        scan[1] = other ? 3 : Math.max(0, scan[1] - 1);
    }

    private static void spawnTarget(ServerPlayer player, CaseFile d, ServerLevel level1) {
        Species s = PokemonSpecies.getByName(d.species);
        if (s == null) {
            CaseClosing.finish(player, d, Messages.tr("announce.cancelled.title"), Messages.tr("announce.cancelled.text"), 0xE8543A);
            return;
        }
        Pokemon p = new Pokemon();
        p.setSpecies(s);
        p.setLevel(Math.max(1, d.level2));
        p.initialize();
        int stars = Engine.stars(d);
        List<Stat> stats = new ArrayList<>(List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));
        Collections.shuffle(stats, Engine.RANDOM);
        // Mieux l'enquête est menée, meilleur est le Pokémon : une statistique parfaite (IV 31) par étoile au-delà de la première.
        for (int i = 0; i < Math.min(stats.size(), Engine.perfectIvs(stars)); i++) p.setIV(stats.get(i), 31);
        float rate1 = Cobblemon.INSTANCE.getConfig().getShinyRate();
        double shiny = (1 + Engine.settings.shinyMultiplierPerStar * stars) * (Engine.is(d, "nocturnal") ? 2 : 1);
        if (rate1 > 0 && Engine.RANDOM.nextDouble() < shiny / rate1) p.setShiny(true);
        if (Engine.is(d, "exceptional")) {
            try {
                new HiddenAbilityProperty(true).apply(p); // sans effet si l'espèce n'a pas de talent caché
            } catch (Exception e) {
                System.err.println("[Field Investigations] Talent caché impossible à donner : " + e);
            }
        }
        // Elle apparaît sur la terre ferme : jamais dans l'eau, la lave ou au-dessus du vide.
        int tx = (int) Math.floor(d.lairX), tz = (int) Math.floor(d.lairZ);
        BlockPos ground = Engine.is(d, "aquatic") ? openWater(level1, tx, tz) : Engine.is(d, "underground") ? Traces.cave(level1, tx, tz, 12) : solidGround(level1, tx, tz);
        if (ground == null && Engine.is(d, "underground")) ground = solidGround(level1, tx, tz); // aucune grotte par ici : elle sort en surface
        if (ground == null) {
            Tracking.move(d, level1, 40, 80); // rien de praticable ici : elle est repérée un peu plus loin
            Engine.save();
            Messages.sync(player);
            return;
        }
        PokemonEntity e = new PokemonEntity(level1, p, CobblemonEntities.POKEMON);
        e.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Engine.RANDOM.nextFloat() * 360f, 0f);
        // Tant qu'elle n'est pas identifiée, rien ne peut la blesser (chute, noyade, autre créature).
        e.setInvulnerable(true);
        level1.addFreshEntity(e);
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

    // Pour une cible aquatique : un endroit où il y a au moins deux blocs d'eau au-dessus du fond.
    private static BlockPos openWater(ServerLevel level1, int bx, int bz) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level1.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level1.getBlockState(pos).is(Blocks.WATER) && level1.getBlockState(pos.above()).is(Blocks.WATER)) return pos;
                }
            }
        }
        return null;
    }

    private static BlockPos solidGround(ServerLevel level1, int bx, int bz) {
        for (int radius = 0; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level1.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level1.getBlockState(pos.below()).isFaceSturdy(level1, pos.below(), Direction.UP)) continue;
                    if (!level1.getBlockState(pos).getFluidState().isEmpty() || !level1.getBlockState(pos.above()).getFluidState().isEmpty()) continue;
                    if (!level1.getBlockState(pos).getCollisionShape(level1, pos).isEmpty()) continue;
                    return pos;
                }
            }
        }
        return null;
    }
}
