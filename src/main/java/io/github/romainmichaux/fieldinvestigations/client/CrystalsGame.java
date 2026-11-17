package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Residual crystals (elemental residue): clusters on a rock wall glow for a moment, sound ones teal, unstable ones red.
// Then they all go dull. Hold the click on a sound one to pry it loose; an unstable one starts to crack under the hand
// (let go in time!) and, if it bursts, shatters its neighbours. Sound crystals give off a rare spark, unstable ones a
// faint shiver: a sharp eye helps the memory. What is pried loose flies into the vial.
final class CrystalsGame extends MiniGame {
    static final ResourceLocation ROCK = Draw.texture("crystals_rock");
    static final ResourceLocation[] CLUSTERS = {Draw.texture("crystals_a"), Draw.texture("crystals_b"), Draw.texture("crystals_c")};
    static final int DULL = 0xFF6A7682, STABLE = 0xFF6FF0E0, UNSTABLE = 0xFFF0604E, PRY = 7, BLAST = 30, FIELD_W = 228;
    static final int INERT = 0, SOUND = 1, BURST = 2;
    // Each crystal: x, y (centre), variant, size (percent), kind, state (0 in place, 1 pried, 2 wrongly pried, 3 shattered).
    final List<int[]> crystals = new ArrayList<>();
    final Random h;
    // Time to pry the deposit once it has gone dull: what is still on the wall then is lost (one fault).
    int playTime, playDuration;
    int round, showTime, duration, remaining, faults, pause, flash, tic, holding = -1, hold, collected, total;
    // Flights to the vial: crystal index and age; shards of a burst.
    final List<int[]> flights = new ArrayList<>();
    final List<double[]> shards = new ArrayList<>();
    int shiver = -1, shiverTicks, spark = -1, sparkTicks;
    double mx, my;

    CrystalsGame(Random h) {
        this.h = h;
        total = 5 + 6 + 7;
        newRound();
    }

    void newRound() {
        shownSound();
        crystals.clear();
        int sound = 5 + round, burst = 2 + round, n = 17 + round * 2;
        for (int i = 0; i < n; i++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                int x = ox + 14 + h.nextInt(FIELD_W - 20), y = oy + 22 + h.nextInt(84);
                boolean free = true;
                for (int[] c : crystals) free &= Math.hypot(c[0] - x, c[1] - y) >= 23;
                if (free || attempt == 199) {
                    int kind = i < sound ? SOUND : i < sound + burst ? BURST : INERT;
                    crystals.add(new int[] {x - ox, y - oy, h.nextInt(3), 95 + h.nextInt(35), kind, 0});
                    break;
                }
            }
        }
        remaining = sound;
        duration = showTime = 64 - round * 6;
        playDuration = playTime = 130 - round * 10;
        holding = -1;
    }

    int at(double x, double y) {
        for (int i = 0; i < crystals.size(); i++) {
            int[] c = crystals.get(i);
            if (c[5] == 0 && Math.abs(x - (ox + c[0])) <= 9 && Math.abs(y - (oy + c[1])) <= 10) return i;
        }
        return -1;
    }

    boolean playing() {
        return showTime == 0 && pause == 0 && !finished;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (showTime > 0 && --showTime == 0) hiddenSound();
        flights.removeIf(f -> ++f[1] > 14);
        shards.removeIf(s -> {
            s[0] += s[2];
            s[1] += s[3];
            s[3] += 0.25;
            return --s[4] <= 0;
        });
        if (shiverTicks > 0) shiverTicks--;
        if (sparkTicks > 0) sparkTicks--;
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (!playing()) return;
        if (--playTime <= 0) {
            // Out of time: the sound crystals still on the wall are lost.
            for (int[] c : crystals) if (c[4] == SOUND && c[5] == 0) c[5] = 3;
            remaining = 0;
            holding = -1;
            flash = 10;
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
            else if (++round >= 3) {
                perfect = false;
                end(true);
            } else pause = 30;
            return;
        }
        hurryTicks(playTime, playDuration);
        // The tells (rare: a bonus for a sharp eye, too slow to wait for): now and then a sound crystal sparks, or an unstable one shivers.
        if (sparkTicks == 0 && h.nextInt(70) == 0) {
            spark = pick(SOUND);
            sparkTicks = spark >= 0 ? 6 : 0;
        }
        if (shiverTicks == 0 && h.nextInt(80) == 0) {
            shiver = pick(BURST);
            shiverTicks = shiver >= 0 ? 5 : 0;
        }
        // Prying: the click must stay on the same crystal.
        int under = mouseDown ? at(mx, my) : -1;
        if (holding >= 0 && (under != holding || !mouseDown)) holding = -1;
        if (holding < 0 && under >= 0) {
            holding = under;
            hold = 0;
        }
        if (holding >= 0) {
            hold++;
            int[] c = crystals.get(holding);
            if (hold % 3 == 1) Draw.beep(c[4] == BURST ? Sounds.CRACK : Sounds.PICKAXE, c[4] == BURST ? 1.6f + hold * 0.08f : 1.5f, 0.35f);
            if (hold >= PRY) resolve(holding);
        }
    }

    int pick(int kind) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < crystals.size(); i++) if (crystals.get(i)[4] == kind && crystals.get(i)[5] == 0) list.add(i);
        return list.isEmpty() ? -1 : list.get(h.nextInt(list.size()));
    }

    void resolve(int i) {
        int[] c = crystals.get(i);
        holding = -1;
        if (c[4] == SOUND) {
            c[5] = 1;
            collected++;
            flights.add(new int[] {i, 0});
            Draw.beep(Sounds.CHIME, 1.2f + (5 + round - remaining) * 0.08f, 0.6f);
            remaining--;
        } else if (c[4] == BURST) {
            // It bursts and takes its neighbours with it.
            c[5] = 3;
            burst(c);
            for (int[] o : crystals) {
                if (o[5] != 0 || Math.hypot(o[0] - c[0], o[1] - c[1]) > BLAST) continue;
                o[5] = 3;
                burst(o);
                if (o[4] == SOUND) remaining--;
            }
            flash = 10;
            Draw.beep(Sounds.CRACK, 0.7f, 1f);
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        } else {
            c[5] = 2;
            flash = 8;
            Draw.beep(Sounds.STONE, 0.8f, 0.8f);
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        }
        if (!finished && remaining <= 0) {
            Draw.note(1f + round * 0.25f);
            if (++round >= 3) {
                perfect = faults == 0 && collected == total;
                end(true);
            } else {
                pause = 30;
            }
        }
    }

    void burst(int[] c) {
        for (int k = 0; k < 7; k++) {
            double a = h.nextDouble() * Math.PI * 2, v = 1 + h.nextDouble() * 1.8;
            shards.add(new double[] {ox + c[0], oy + c[1], Math.cos(a) * v, Math.sin(a) * v - 1.2, 12 + h.nextInt(6), c[4]});
        }
    }

    void cluster(GuiGraphics g, int[] c, int tint, int dx, int dy) {
        float s = c[3] / 100f * 0.55f;
        g.pose().pushPose();
        g.pose().translate(ox + c[0] + dx - 16 * s, oy + c[1] + dy - 18 * s, 0);
        g.pose().scale(s, s, 1f);
        Draw.tint(g, tint);
        Draw.image(g, CLUSTERS[c[2]], 0, 0, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy;
        // The rock wall, tiled at half size.
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        for (int ty = 0; ty < AREA_H * 2; ty += 112) {
            for (int tx = 0; tx < AREA_W * 2; tx += 112) Draw.piece(g, ROCK, tx, ty, 0, 0, Math.min(112, AREA_W * 2 - tx), Math.min(112, AREA_H * 2 - ty), 112, 112);
        }
        g.pose().popPose();
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0x68000000);
        boolean revealed = showTime > 0 || pause > 0 || finished;
        boolean pulse = (tic / 4) % 2 == 0;
        for (int i = 0; i < crystals.size(); i++) {
            int[] c = crystals.get(i);
            if (c[5] == 1 || c[5] == 3) continue;
            int px = ax + c[0], py = ay + c[1];
            int tint = DULL;
            boolean glows = false;
            if (revealed && c[4] == SOUND) {
                tint = STABLE;
                glows = true;
            } else if (revealed && c[4] == BURST) {
                tint = UNSTABLE;
                glows = true;
            } else if (c[5] == 2) {
                tint = 0xFF3A424C;
            } else if (playing() && at(x, y) == i) {
                tint = 0xFF9FB0C2;
            }
            if (glows) disc(g, px, py, 10, (tint & 0xFFFFFF) | (pulse ? 0x48000000 : 0x30000000));
            int jitter = i == shiver && shiverTicks > 0 ? (shiverTicks % 2 == 0 ? 1 : -1) : 0;
            cluster(g, c, tint, jitter, 0);
            // Prying: scratches on a sound crystal, a crack spreading over an unstable one.
            if (i == holding) {
                int k = hold;
                if (c[4] == BURST) {
                    for (int s = 0; s < k; s++) g.fill(px - 1 + (s % 2 == 0 ? s : -s) / 2, py - 6 + s * 2, px + (s % 2 == 0 ? s : -s) / 2, py - 4 + s * 2, 0xFFFF6A50);
                } else {
                    g.fill(px - 7, py + 7, px - 7 + k * 2, py + 8, 0xFFFFFFFF);
                }
            }
            if (i == spark && sparkTicks > 0) {
                int size = sparkTicks > 3 ? 6 - sparkTicks : sparkTicks;
                g.fill(px + 3 - size, py - 8, px + 4 + size, py - 7, 0xFFE8FFFC);
                g.fill(px + 3, py - 8 - size, px + 4, py - 7 + size, 0xFFE8FFFC);
            }
        }
        for (double[] s : shards) g.fill((int) s[0], (int) s[1], (int) s[0] + 2, (int) s[1] + 2, s[5] == BURST ? UNSTABLE : s[5] == SOUND ? STABLE : DULL);

        // The vial on the right: what has been pried loose flies into it.
        int vx = ax + AREA_W - 34, vy = ay + 26, vh = 74;
        g.fill(vx - 1, vy - 1, vx + 21, vy + vh + 1, Draw.EDGE);
        g.fill(vx, vy, vx + 20, vy + vh, 0x40A8C8D8);
        int level = vh * collected / total;
        g.fill(vx + 1, vy + vh - level, vx + 19, vy + vh - 1, STABLE);
        if (level > 0) g.fill(vx + 1, vy + vh - level, vx + 19, vy + vh - level + 1, 0xFFE8FFFC);
        g.fill(vx + 3, vy + 4, vx + 4, vy + vh - 6, 0x60FFFFFF);
        g.fill(vx - 3, vy - 5, vx + 23, vy - 1, 0xFF8A98A6);
        for (int[] f : flights) {
            int[] c = crystals.get(f[0]);
            double t = f[1] / 14.0;
            int fx = (int) Math.round(ax + c[0] + (vx + 10 - ax - c[0]) * t), fy = (int) Math.round(ay + c[1] + (vy - 4 - ay - c[1]) * t - Math.sin(t * Math.PI) * 20);
            cluster(g, new int[] {fx - ax, fy - ay, c[2], (int) (c[3] * (1 - t * 0.5)), c[4], 0}, STABLE, 0, 0);
        }

        String left = T.f("deposit") + Math.min(3, round + (pause > 0 || finished ? 0 : 1)) + " / 3", right = T.f("faults") + faults + " / 3";
        text(g, left, ax + 6, ay + 4, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 4, faults > 0 ? Draw.ALERT : Draw.GREY);
        String state = showTime > 0 ? T.f("watch_closely") : pause > 0 ? T.f("deposit_sampled") : null;
        if (state != null) Draw.centre(g, state, ax + 150, ay + 4, showTime > 0 ? Draw.YELLOW : Draw.GREEN);
        if (showTime > 0) {
            g.fill(ax + 100, ay + 14, ax + 200, ay + 16, Draw.EDGE);
            g.fill(ax + 100, ay + 14, ax + 100 + 100 * showTime / duration, ay + 16, Draw.YELLOW);
        }
        if (playing()) {
            Draw.centre(g, String.valueOf(remaining), vx + 10, vy + vh + 4, STABLE);
            g.fill(ax + 100, ay + 14, ax + 200, ay + 16, Draw.EDGE);
            g.fill(ax + 100, ay + 14, ax + 100 + 100 * playTime / playDuration, ay + 16, playTime * 4 > playDuration ? Draw.ACCENT : Draw.ALERT);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
            g.fill(ax, ay, ax + 1, ay + AREA_H, Draw.ALERT);
            g.fill(ax + AREA_W - 1, ay, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
    }

    @Override
    String instruction() {
        return T.f("the_residue_has_set_into");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("pry_loose_hold"), Draw.MOUSE)};
    }
}
