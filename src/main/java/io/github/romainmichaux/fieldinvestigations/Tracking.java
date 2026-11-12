package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// Tracking phase: call bearings taken with the kit, cross-fixing, target movements.
final class Tracking {
    private Tracking() {}

    record CrossFix(boolean valid, double x, double z, double uncertainty, String message) {}

    static void launch(ServerPlayer player, String guess) {
        CaseFile d = Engine.caseFile(player);
        if (d == null || !"SAMPLING".equals(d.phase) || d.pins.size() < Engine.required(d)) return;
        Species target = PokemonSpecies.getByName(d.species), choice = PokemonSpecies.getByName(guess);
        // The player may name any suspect they have not crossed out, even if the analyses contradict it:
        // being wrong will cost them stars.
        if (target == null || choice == null || !d.suspects.contains(guess) || (d.ruledOut != null && d.ruledOut.contains(guess))) return;
        d.guess = guess;
        d.phase = "TRACKING";
        d.nextMove = Engine.nextMove(d);
        if (d.dimension.equals(Engine.dimension(player))) Traces.removeTraces(player.serverLevel(), d);
        Engine.save();
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.3f);
        Messages.announce(player, "kit_calibrated", 0x4FD1C5);
        Messages.sync(player);
    }

    static void abandon(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        if (d == null) return;
        if (!player.getUUID().equals(d.leader)) {
            Team.leave(player, d);
            return;
        }
        PokemonEntity e = Engine.ENTITIES.remove(d.leader);
        if (e != null && !e.isBattling() && ("APPROACH".equals(d.phase) || "SPOTTING".equals(d.phase))) e.discard();
        CaseClosing.finish(player, d, Messages.tr("announce.closed.title"), Messages.tr("announce.closed.text"), 0x9AA0A6);
    }

    public static void useKit(ServerPlayer player, boolean crouching) {
        CaseFile d = Engine.caseFile(player);
        if (d == null) {
            Messages.open(player, "board");
            return;
        }
        if ("TRACKING".equals(d.phase) && !crouching && d.dimension.equals(Engine.dimension(player))) bearing(player, d);
        else Messages.open(player, "kit");
    }

    // The target has appeared but is not identified yet: the kit then works as a scanner (hold right click).
    public static boolean inSpotting(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        return d != null && "SPOTTING".equals(d.phase) && d.dimension.equals(Engine.dimension(player));
    }

    private static void bearing(ServerPlayer player, CaseFile d) {
        long now = System.currentTimeMillis();
        if (now < d.nextBearing) return;
        if (Engine.asleep(d, player.serverLevel())) {
            Messages.announce(player, "nocturnal_silence", 0xF2C93A);
            return;
        }
        if (Engine.is(d, "mist")) {
            // In mist, the call must first be isolated from the noise: the bearing is taken after the mini-game.
            Engine.PENDING_FILTERS.put(player.getUUID(), Boolean.TRUE);
            Packet p = Packet.from("open");
            p.data().putString("screen", "filter");
            p.data().putBoolean("first", Engine.firstTime(player, "filter"));
            p.data().putLong("seed", Engine.RANDOM.nextLong());
            Messages.send(player, p);
            return;
        }
        takeBearing(player, d, 1);
    }

    // Call filtering result: on success the bearing is as precise as elsewhere; on failure it is much blurrier.
    static void filterResult(ServerPlayer player, boolean success) {
        if (Engine.PENDING_FILTERS.remove(player.getUUID()) == null) return;
        CaseFile d = Engine.caseFile(player);
        if (d == null || !"TRACKING".equals(d.phase) || !d.dimension.equals(Engine.dimension(player))) return;
        takeBearing(player, d, success ? 1 : 1.8);
    }

    private static void takeBearing(ServerPlayer player, CaseFile d, double blur) {
        long now = System.currentTimeMillis();
        Bearing last = d.bearings.isEmpty() ? null : d.bearings.get(d.bearings.size() - 1);
        if (last != null && !last.expired && Engine.distance(player, last.x, last.z) < Engine.settings.distanceBetweenBearings) {
            Messages.announce(player, "same_spot", 0xF2C93A, Engine.settings.distanceBetweenBearings);
            return;
        }
        d.nextBearing = now + Engine.settings.secondsBetweenBearings * 1000L;
        player.getCooldowns().addCooldown(ModContent.KIT, Engine.settings.secondsBetweenBearings * 20);
        double dx = d.lairX - player.getX(), dz = d.lairZ - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double real = Math.toDegrees(Math.atan2(dx, -dz));
        double error = Math.max(Engine.settings.bearingErrorMinDegrees, Math.min(Engine.settings.bearingErrorMaxDegrees, dist / 22)) * blur * (Engine.rank(player) >= 5 ? 0.75 : 1);
        // Noise depends on the spot (not the time): retaking a bearing at the same spot does not improve it.
        Random noise = new Random(d.seed ^ (((long) player.getBlockX() >> 3) * 73428767L) ^ (((long) player.getBlockZ() >> 3) * 912931L) ^ d.flees);
        Bearing r = new Bearing();
        r.x = player.getX();
        r.z = player.getZ();
        r.heading = ((real + (noise.nextDouble() * 2 - 1) * error) % 360 + 360) % 360;
        r.error = error;
        d.bearings.add(r);
        Engine.save();

        double rad = Math.toRadians(r.heading);
        double ux = Math.sin(rad), uz = -Math.cos(rad);
        float volume = (float) Math.max(0.25, Math.min(1.0, 1.25 - dist / 320));
        SoundEvent cry = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon." + d.species + ".cry"));
        player.connection.send(new ClientboundSoundPacket(Holder.direct(cry), SoundSource.NEUTRAL,
                player.getX() + ux * 14, player.getEyeY(), player.getZ() + uz * 14, volume * 2.5f, 1f, Engine.RANDOM.nextLong()));
        Packet p = Packet.from("bearing");
        p.data().putDouble("cap", r.heading);
        p.data().putDouble("error", error);
        p.data().putInt("force", dist > 260 ? 0 : dist > 170 ? 1 : dist > 100 ? 2 : 3);
        Messages.send(player, p);
        Messages.sync(player);
    }

    // Cross-fix of the valid bearings (weighted least squares): estimated position and uncertainty.
    static CrossFix crossFix(CaseFile d) {
        List<Bearing> valid = new ArrayList<>();
        for (Bearing r : d.bearings) {
            if (!r.expired) valid.add(r);
        }
        if (valid.size() < 2) return new CrossFix(false, 0, 0, 0, "cobblemon_field_investigations.fix.two");
        double ex = 0, ez = 0, uncertainty = 0;
        for (int pass = 0; pass < 2; pass++) {
            double a11 = 0, a12 = 0, a22 = 0, b1 = 0, b2 = 0;
            for (Bearing r : valid) {
                double rad = Math.toRadians(r.heading);
                double nx = Math.cos(rad), nz = Math.sin(rad);
                double w = 1;
                if (pass == 1) {
                    double sigma = Math.max(8, Math.hypot(ex - r.x, ez - r.z) * Math.toRadians(r.error) * 0.6);
                    w = 1 / (sigma * sigma);
                }
                double np = nx * r.x + nz * r.z;
                a11 += w * nx * nx;
                a12 += w * nx * nz;
                a22 += w * nz * nz;
                b1 += w * nx * np;
                b2 += w * nz * np;
            }
            double det = a11 * a22 - a12 * a12;
            if (pass == 0 && det < 0.03) return new CrossFix(false, 0, 0, 0, "cobblemon_field_investigations.fix.aligned");
            if (Math.abs(det) < 1e-12) return new CrossFix(false, 0, 0, 0, "cobblemon_field_investigations.fix.impossible");
            ex = (a22 * b1 - a12 * b2) / det;
            ez = (a11 * b2 - a12 * b1) / det;
            if (pass == 1) uncertainty = 2.5 * Math.sqrt((a11 + a22) / det);
        }
        int ahead = 0;
        for (Bearing r : valid) {
            double rad = Math.toRadians(r.heading);
            if ((ex - r.x) * Math.sin(rad) - (ez - r.z) * Math.cos(rad) > 0) ahead++;
        }
        if (ahead * 2 <= valid.size()) return new CrossFix(false, 0, 0, 0, "cobblemon_field_investigations.fix.contradict");
        return new CrossFix(true, ex, ez, Math.max(10, uncertainty), "cobblemon_field_investigations.fix.ok");
    }

    // The target relocates while staying in its medium: an aquatic target stays in water, others on land.
    static void move(CaseFile d, ServerLevel level, double min, double max) {
        double x = d.lairX, z = d.lairZ;
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = Engine.RANDOM.nextDouble() * Math.PI * 2, dist = min + Engine.RANDOM.nextDouble() * (max - min);
            x = d.lairX + Math.cos(a) * dist;
            z = d.lairZ + Math.sin(a) * dist;
            if (Reports.aquatic(level, x, z) == Engine.is(d, "aquatic")) break;
        }
        d.lairX = x;
        d.lairZ = z;
        for (Bearing r : d.bearings) r.expired = true;
        d.nextMove = Engine.nextMove(d);
        d.announceApproach = false;
    }
}
