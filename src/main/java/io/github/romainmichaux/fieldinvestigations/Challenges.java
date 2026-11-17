package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

// Challenge traces: the trace does not wait on the ground, it is earned. The radar leads to a starting point; from there the
// player has three tries at the challenge, and the trace appears at its end. After three failures it appears anyway, but
// damaged (one dose fewer). Nothing is placed or broken in the world: the course is drawn with particles for this player only.
//
// "track": a trail of footprints winds away from the start. Only the next few prints show; beacons along the way add time to
// a countdown. Reach the last beacon before the time runs out.
final class Challenges {
    private Challenges() {}

    // A long run: about 200 blocks, a marker every 30. Each leg's time is what a sprint takes with a small margin, so it is
    // a race against the clock: sprint, jump the bumps, do not stop.
    static final int TRIES = 3, PRINTS = 68, STEP = 3, EVERY = 10, AHEAD = 14;
    static final double SPRINT = 5.6, MARGIN = 1.3;
    // Prints are bright and paired with a spark that glows by itself, so the trail shows at night as well as by day.
    static final ParticleOptions PRINT = new DustParticleOptions(new Vector3f(1f, 0.62f, 0.2f), 1.4f);
    static final ParticleOptions START = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.25f), 1.6f);

    static boolean pending(Trace t) {
        return t.challenge != null && !t.challengeDone;
    }

    // The beacons: every EVERY prints, and the last one.
    static List<Integer> beacons(Trace t) {
        List<Integer> list = new ArrayList<>();
        for (int i = EVERY; i < t.course.size() - 1; i += EVERY) list.add(i);
        list.add(t.course.size() - 1);
        return list;
    }

    // Called twice per second for each investigator in the sampling phase. Returns true while this challenge owns the radar
    // (running), so the signal shows its countdown.
    static boolean update(ServerPlayer player, ServerLevel level, CaseFile d, Trace t, boolean kit) {
        long now = System.currentTimeMillis();
        if (!t.running) {
            double toStart = Math.hypot(player.getX() - t.sx, player.getZ() - t.sz);
            // The start: a golden column, visible from afar with the kit in hand.
            if (kit && toStart < 64) column(player, level, t.sx, t.sz, START);
            if (kit && toStart < 2.5 && player.onGround()) start(player, level, d, t, now);
            return false;
        }
        List<Integer> beacons = beacons(t);
        int next = beacons.get(Math.min(t.stage, beacons.size() - 1));
        // Footprints: the next few after the last reached beacon or the player's nearest print, whichever is further along.
        int from = t.stage == 0 ? 0 : beacons.get(t.stage - 1);
        int nearest = from;
        double best = Double.MAX_VALUE;
        for (int i = from; i <= next; i++) {
            double[] p = t.course.get(i);
            double dist = player.distanceToSqr(p[0], p[1], p[2]);
            if (dist < best) {
                best = dist;
                nearest = i;
            }
        }
        for (int i = nearest; i <= Math.min(next, nearest + AHEAD); i++) {
            double[] p = t.course.get(i);
            level.sendParticles(player, PRINT, true, p[0], p[1] + 0.08, p[2], 3, 0.08, 0.0, 0.08, 0.0);
            level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, p[0], p[1] + 0.15, p[2], 1, 0.05, 0.0, 0.05, 0.0);
        }
        double[] b = t.course.get(next);
        column(player, level, b[0], b[2], ParticleTypes.END_ROD);
        if (player.distanceToSqr(b[0], b[1] + 1, b[2]) < 3.2 * 3.2) {
            t.stage++;
            if (t.stage >= beacons.size()) {
                finish(player, d, t, false);
                return false;
            }
            // Each leg has its own time: the clock restarts at every marker, what was left is not carried over.
            long added = legTime(t, next, beacons.get(t.stage));
            t.deadline = System.currentTimeMillis() + added;
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f + t.stage * 0.1f);
            Messages.announce(player, "track_beacon", 0x6CCB5F, t.stage, beacons.size(), added / 1000);
        } else if (now > t.deadline) {
            t.running = false;
            t.tries++;
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.6f);
            if (t.tries >= TRIES) {
                finish(player, d, t, true);
            } else {
                Messages.announce(player, "track_lost", 0xE8543A, t.tries + 1, TRIES);
                Engine.save();
            }
            return false;
        } else {
            long left = t.deadline - now;
            if (left < 5000 && (left / 500) % 2 == 0) player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.7f, 1.6f);
        }
        return true;
    }

    static void start(ServerPlayer player, ServerLevel level, CaseFile d, Trace t, long now) {
        if (t.course == null || t.course.isEmpty()) {
            t.course = course(level, d, t);
            if (t.course.size() < EVERY + 2) {
                // No room for a trail here (water, unloaded land...): the trace simply appears.
                finish(player, d, t, false);
                return;
            }
        }
        t.running = true;
        t.stage = 0;
        t.deadline = now + legTime(t, 0, beacons(t).get(0)) + 1500; // a moment to get going
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
        Messages.announce(player, "track_start", 0xF2C93A, t.tries + 1, TRIES);
        Engine.save();
    }

    // Time for a leg, in milliseconds: its length at sprinting speed, plus a little per block climbed, with a small margin.
    static long legTime(Trace t, int from, int to) {
        double metres = 0, climb = 0;
        for (int i = from + 1; i <= to; i++) {
            double[] a = t.course.get(i - 1), b = t.course.get(i);
            metres += Math.hypot(b[0] - a[0], b[2] - a[2]);
            climb += Math.max(0, b[1] - a[1]);
        }
        return (long) ((metres / SPRINT * MARGIN + climb * 0.35 + 1) * 1000);
    }

    static void finish(ServerPlayer player, CaseFile d, Trace t, boolean damaged) {
        t.running = false;
        t.challengeDone = true;
        t.damaged = damaged;
        // The trace appears where the trail ends (or at the start if there was no trail).
        double[] end = t.course == null || t.course.isEmpty() ? new double[] {t.sx, 0, t.sz} : t.course.get(t.course.size() - 1);
        t.x = Math.floor(end[0]) + 0.5;
        t.z = Math.floor(end[2]) + 0.5;
        t.placed = false;
        player.playNotifySound(damaged ? SoundEvents.NOTE_BLOCK_BASS.value() : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, damaged ? 0.5f : 1.3f);
        Messages.announce(player, damaged ? "track_faded" : "track_done", damaged ? 0xE8543A : 0x6CCB5F);
        Engine.save();
    }

    // Test command (/hunt challenge): during the sampling phase, the first trace still to find (not buried) becomes a track
    // whose start is a few blocks in front of the player, with its tries reset.
    static String debugStart(ServerPlayer player) {
        CaseFile d = Engine.caseFile(player);
        if (d == null || !"SAMPLING".equals(d.phase)) return "No investigation in the sampling phase.";
        for (Trace t : d.traces) {
            if (t.over() || t.buried) continue;
            ServerLevel level = player.serverLevel();
            if (t.placed) {
                BlockPos pos = new BlockPos(t.px, t.py, t.pz);
                if (level.getBlockState(pos).is(ModContent.TRACE)) level.removeBlock(pos, false);
            }
            var look = player.getLookAngle();
            t.challenge = "track";
            t.challengeDone = false;
            t.damaged = false;
            t.tries = 0;
            t.course = null;
            t.running = false;
            t.placed = false;
            t.cave = false;
            t.sx = player.getX() + look.x * 4;
            t.sz = player.getZ() + look.z * 4;
            t.x = t.sx;
            t.z = t.sz;
            Engine.save();
            return "Track ready 4 blocks ahead (trace: " + t.kind + "). Hold the kit and step on the golden column.";
        }
        return "No trace left to turn into a track.";
    }

    // A column of particles over the ground at (x, z), for this player only.
    static void column(ServerPlayer player, ServerLevel level, double x, double z, ParticleOptions particle) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return;
        BlockPos ground = groundAt(level, bx, bz);
        int y = ground != null ? ground.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        // A tall beam: seen over trees and bumps from the previous marker.
        for (int k = 0; k < 14; k++) level.sendParticles(player, particle, true, x, y + 0.3 + k * 0.8, z, 2, 0.06, 0.2, 0.06, 0.0);
    }

    // The trail: it sets off in one direction and keeps roughly to it (a slow wobble of about thirty degrees either way),
    // stepping around water and walls, one print every STEP blocks (alternating left and right feet). It runs on the real
    // ground, under the foliage: trunks, leaves and vines are not ground.
    static List<double[]> course(ServerLevel level, CaseFile d, Trace t) {
        // Try up to eight directions (from a random one) and keep the first full trail, or the longest: a start on a shore
        // or a cliff edge simply sets off the other way.
        java.util.Random h = new java.util.Random(d.seed + t.kind.hashCode() * 31L + t.tries);
        double first = h.nextDouble() * Math.PI * 2;
        List<double[]> best = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            List<double[]> prints = walk(level, t, first + k * Math.PI / 4, h);
            if (prints.size() > best.size()) best = prints;
            if (best.size() >= PRINTS) break;
        }
        return best;
    }

    static List<double[]> walk(ServerLevel level, Trace t, double main, java.util.Random h) {
        List<double[]> prints = new ArrayList<>();
        double x = t.sx, z = t.sz, wobble = 0;
        for (int i = 0; i < PRINTS; i++) {
            wobble = Math.max(-0.55, Math.min(0.55, wobble * 0.85 + h.nextGaussian() * 0.22));
            double heading = main + wobble;
            double[] step = null;
            // Straight on, then up to 90 degrees either side: never back, and never onto ground already walked.
            for (int turn = 0; turn < 5 && step == null; turn++) {
                double a = heading + (turn == 0 ? 0 : (turn % 2 == 1 ? 1 : -1) * ((turn + 1) / 2) * Math.PI / 4);
                double nx = x + Math.cos(a) * STEP, nz = z + Math.sin(a) * STEP;
                if (Math.hypot(nx - t.sx, nz - t.sz) < 2.5) continue;
                boolean walked = false;
                for (int j = 0; j < prints.size() - 1 && !walked; j++) walked = Math.hypot(nx - prints.get(j)[0], nz - prints.get(j)[2]) < 2.5;
                if (walked) continue;
                BlockPos ground = groundAt(level, (int) Math.floor(nx), (int) Math.floor(nz));
                if (ground == null) continue;
                if (!prints.isEmpty() && Math.abs(ground.getY() - prints.get(prints.size() - 1)[1]) > 1) continue; // bumps to jump, no walls
                step = new double[] {nx, ground.getY(), nz, a};
            }
            if (step == null) break;
            x = step[0];
            z = step[2];
            double side = (i % 2 == 0 ? 0.3 : -0.3);
            prints.add(new double[] {x - Math.sin(step[3]) * side, step[1], z + Math.cos(step[3]) * side});
        }
        return prints;
    }

    // The free spot just above the real ground of a column (not on a trunk, leaves or vines), or null over water. Loads the
    // chunk if needed: a trail can run beyond what is loaded around the player.
    static BlockPos groundAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int min = level.getMinBuildHeight() + 1;
        while (y > min) {
            BlockPos below = new BlockPos(x, y - 1, z);
            var state = level.getBlockState(below);
            if (!state.getFluidState().isEmpty()) return null;
            boolean tree = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(net.minecraft.world.level.block.Blocks.VINE)
                    || state.is(net.minecraft.world.level.block.Blocks.BAMBOO);
            if (!tree && state.isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                BlockPos here = below.above();
                return level.getBlockState(here).canBeReplaced() || level.getBlockState(here).is(BlockTags.LEAVES) ? here : null;
            }
            y--;
        }
        return null;
    }
}
