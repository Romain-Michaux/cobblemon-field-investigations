package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Chromatography: the sample strip climbs beside reference strips of known inks. The sample is a blend of two
// references. Lift the rack when the spots can be read (early: they still overlap; late: the solvent reaches the top),
// then name the two references whose spots, colour and height, make up the sample. Two rounds.
final class ChromatographyGame extends MiniGame {
    static final int TOP = 64, STRIP_W = 22, PAPER_TOP = 25, START = 92, ROUNDS = 2;
    static final ResourceLocation CLIP = Draw.texture("chroma_clip");
    static final int PAPER = 0xFFF3EEE2, WET = 0xFFDCE4E8;
    // The inks: five hues, each in two inks that travel to different heights (fraction of the front). Neighbouring heights
    // are always different hues, close enough that they only come apart late in the run; until then they blend.
    static final int[] INK = {0xFFE0473A, 0xFFF2C93A, 0xFF4A7AD9, 0xFF9B5FC0, 0xFF4FAF5A, 0xFFE0473A, 0xFFF2C93A, 0xFF4A7AD9, 0xFF9B5FC0, 0xFF4FAF5A};
    static final double[] RF = {0.16, 0.225, 0.29, 0.355, 0.42, 0.485, 0.55, 0.615, 0.68, 0.745};
    final Random h;
    final double[] fibre = new double[TOP + 2];
    final int[][] specks = new int[6][16];
    // The strips of this round: index 0 is the sample; each strip lists its inks.
    final List<int[]> strips = new ArrayList<>();
    int answerA, answerB, round, faults, flash, tic, waiting = 24, lifted = -1, check = -1, verdictTicks;
    double front, lift;
    final boolean[] picked = new boolean[6];
    String verdict;
    boolean verdictGood;

    ChromatographyGame(Random h) {
        this.h = h;
        for (int k = 0; k < specks.length; k++) {
            for (int s = 0; s < specks[k].length; s++) specks[k][s] = h.nextInt(STRIP_W - 2) + 1 + 100 * h.nextInt(START - PAPER_TOP);
        }
        newRound();
    }

    void newRound() {
        int refs = round == 0 ? 4 : 5;
        // References with two inks each, until the sample's set of inks points to a single pair.
        while (true) {
            strips.clear();
            List<int[]> list = new ArrayList<>();
            for (int r = 0; r < refs; r++) {
                int a = h.nextInt(INK.length), b;
                do {
                    b = h.nextInt(INK.length);
                } while (b == a);
                list.add(new int[] {a, b});
            }
            answerA = h.nextInt(refs);
            do {
                answerB = h.nextInt(refs);
            } while (answerB == answerA);
            long target = mask(list.get(answerA)) | mask(list.get(answerB));
            int matches = 0;
            boolean distinct = true;
            for (int a = 0; a < refs; a++) {
                for (int b = a + 1; b < refs; b++) {
                    if ((mask(list.get(a)) | mask(list.get(b))) == target) matches++;
                    distinct &= mask(list.get(a)) != mask(list.get(b));
                }
            }
            if (matches != 1 || !distinct) continue;
            List<Integer> sample = new ArrayList<>();
            for (int k = 0; k < INK.length; k++) if ((target & (1L << k)) != 0) sample.add(k);
            strips.add(sample.stream().mapToInt(Integer::intValue).toArray());
            strips.addAll(list);
            break;
        }
        double v = 1;
        for (int y = 0; y < fibre.length; y++) {
            v += (h.nextDouble() - 0.5) * 0.3 + (1 - v) * 0.15;
            fibre[y] = Math.max(0.5, Math.min(1.6, v));
        }
        front = 0;
        lift = 0;
        lifted = -1;
        check = -1;
        java.util.Arrays.fill(picked, false);
    }

    static long mask(int[] inks) {
        long m = 0;
        for (int k : inks) m |= 1L << k;
        return m;
    }

    // Half height of a spot. Neighbouring inks (0.065 apart) only come apart in the last quarter of the run.
    double spread(int ink) {
        return 2.2 + 0.004 * RF[ink] * front;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (verdictTicks > 0) verdictTicks--;
        if (waiting > 0) {
            if (--waiting == 0) Draw.beep(Sounds.DROP, 1.0f, 0.7f);
            return;
        }
        if (lifted >= 0) {
            lifted++;
            if (lift < 8) lift += 1;
        } else {
            front += 0.32 * fibre[Math.min(TOP, (int) front)];
            if (tic % 9 == 0) Draw.beep(Sounds.RUB, (float) (0.6 + 0.8 * front / TOP), 0.18f);
            if (front >= TOP) {
                // The solvent ran off the top: the spots are lost, a new rack goes in.
                verdict = T.f("too_far");
                verdictGood = false;
                verdictTicks = 40;
                miss();
                if (!finished) {
                    waiting = 30;
                    newRound();
                }
            }
        }
        if (check >= 0 && ++check >= 10) {
            check = -1;
            boolean right = picked[answerA + 1] && picked[answerB + 1];
            verdictTicks = 40;
            if (right) {
                verdict = T.f("blend_identified");
                verdictGood = true;
                Draw.note(1.2f + round * 0.3f);
                Draw.beep(Sounds.CHIME, 1.5f, 0.5f);
                if (++round >= ROUNDS) {
                    perfect = faults == 0;
                    end(true);
                } else {
                    waiting = 30;
                    newRound();
                }
            } else {
                verdict = T.f("not_this_blend");
                verdictGood = false;
                java.util.Arrays.fill(picked, false);
                miss();
            }
        }
    }

    void miss() {
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 2) end(false);
    }

    int stripX(int i) {
        int n = strips.size(), gap = 37, left = ox + (AREA_W - (n - 1) * gap - STRIP_W) / 2;
        return left + i * gap;
    }

    int stripAt(double mx, double my) {
        for (int i = 0; i < strips.size(); i++) {
            if (Draw.in(mx, my, stripX(i) - 6, oy + PAPER_TOP - 12, STRIP_W + 12, START - PAPER_TOP + 30)) return i;
        }
        return -1;
    }

    @Override
    void click(double mx, double my) {
        if (waiting > 0 || check >= 0) return;
        int i = stripAt(mx, my);
        if (i < 0) return;
        if (lifted < 0) {
            // Any click on the rack lifts it: the reading starts.
            lifted = 0;
            Draw.beep(Sounds.PAPER, 1.3f, 0.8f);
            return;
        }
        if (i == 0) return;
        picked[i] = !picked[i];
        Draw.beep(Sounds.CLICK, picked[i] ? 1.2f : 0.9f, 0.6f);
        int count = 0;
        for (boolean p : picked) count += p ? 1 : 0;
        if (count == 2) check = 0;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF151C24);
        int tl = ax + 14, tr = ax + AREA_W - 14, tt = ay + 16, tb = ay + AREA_H - 6, liquid = ay + START + 6;
        g.fill(tl, tt, tr, tb, 0x40A8C8D8);
        g.fill(tl + 2, tt + 2, tr - 2, tb - 2, 0x18000000);
        g.fill(tl + 6, ay + 13, tr - 6, ay + 15, 0xFF8A98A6);
        g.fill(tl + 6, ay + 13, tr - 6, ay + 14, 0xFFC8D2DC);
        int hover = lifted >= 0 && check < 0 && waiting == 0 ? stripAt(mx, my) : -1;
        boolean rackHover = lifted < 0 && waiting == 0 && stripAt(mx, my) >= 0;
        for (int i = 0; i < strips.size(); i++) strip(g, i, hover == i, rackHover);
        g.fill(tl + 1, liquid, tr - 1, tb - 1, 0x704A90C8);
        g.fill(tl + 1, liquid, tr - 1, liquid + 1, 0xC0A8D8F0);
        for (int x = tl + 4; x < tr - 4; x += 9) {
            int wave = (int) Math.round(Math.sin(x * 0.3 + tic * 0.12));
            g.fill(x, liquid + 1 + wave, x + 4, liquid + 2 + wave, 0x50FFFFFF);
        }
        g.fill(tl, tt, tl + 1, tb, 0xC0D8E8F0);
        g.fill(tr - 1, tt, tr, tb, 0x80A8C0D0);
        g.fill(tl, tb - 3, tr, tb, 0x90B8D0DC);
        g.fill(tl, tb - 1, tr, tb, 0xFF6C8494);
        g.fill(tl + 4, tt + 4, tl + 5, tb - 8, 0x50FFFFFF);
        g.fill(tl, tt, tr, tt + 1, 0x90D8E8F0);
        // Tags under the strips: "?" for the sample, letters for the references.
        for (int i = 0; i < strips.size(); i++) {
            int x = stripX(i) + STRIP_W / 2;
            String tag = i == 0 ? "?" : String.valueOf((char) ('A' + i - 1));
            int bg = i == 0 ? 0xFFF29A3A : picked[i] ? Draw.YELLOW : 0xFF2A3644;
            g.fill(x - 6, tb - 13, x + 6, tb - 3, bg);
            Draw.centre(g, tag, x, tb - 12, i == 0 || picked[i] ? Draw.EDGE : Draw.TEXT);
        }

        String left = T.f("blends") + round + " / " + ROUNDS, right = T.f("mistakes") + faults + " / 2";
        text(g, left, ax + 6, ay + 2, round > 0 ? Draw.GREEN : Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 2, faults > 0 ? Draw.ALERT : Draw.GREY);
        if (verdictTicks > 0 && verdict != null) {
            int w = Draw.textFont().width(verdict);
            g.fill(ax + AREA_W / 2 - w / 2 - 6, ay + 48, ax + AREA_W / 2 + w / 2 + 6, ay + 62, 0xD0101418);
            Draw.centre(g, verdict, ax + AREA_W / 2, ay + 51, verdictGood ? Draw.GREEN : Draw.ALERT);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
            g.fill(ax, ay, ax + 1, ay + AREA_H, Draw.ALERT);
            g.fill(ax + AREA_W - 1, ay, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
    }

    void strip(GuiGraphics g, int i, boolean hover, boolean rackHover) {
        int x = stripX(i), up = (int) Math.round(lift), top = oy + PAPER_TOP - up, start = oy + START - up, bottom = start + 12;
        int f = (int) front;
        int edge = picked[i] ? Draw.YELLOW : hover && i > 0 ? 0xFFF2F5F7 : rackHover ? 0xFFB8C4D0 : i == 0 ? 0xFFF29A3A : 0xFF8C8676;
        if (picked[i]) g.fill(x - 2, top - 2, x + STRIP_W + 2, bottom + 2, 0x60F2C93A);
        g.fill(x - 1, top - 1, x + STRIP_W + 1, bottom + 1, edge);
        g.fill(x, top, x + STRIP_W, bottom, PAPER);
        g.fill(x, start - f, x + STRIP_W, bottom, WET);
        for (int s : specks[i]) {
            int sx = x + s % 100, sy = top + s / 100;
            if (sx < x + STRIP_W) g.fill(sx, sy, sx + 1, sy + 1, sy > start - f ? 0x14304860 : 0x18806040);
        }
        if (f > 0 && lifted < 0) {
            for (int c = 0; c < STRIP_W; c++) {
                int wave = (int) Math.round(1.2 * Math.sin(c * 0.55 + tic * 0.13 + i * 2));
                g.fill(x + c, start - f + wave, x + c + 1, start - f + wave + 2, 0xFFA8C4D8);
                if (wave < 0) g.fill(x + c, start - f + wave + 2, x + c + 1, start - f, WET);
            }
        }
        g.fill(x + 2, start, x + STRIP_W - 2, start + 1, 0x60505050);
        int[] inks = strips.get(i);
        if (f < 2) {
            g.fill(x + STRIP_W / 2 - 3, start - 2, x + STRIP_W / 2 + 3, start + 3, 0xFF4A3048);
        } else {
            // Overlapping inks blend into one muddy colour: the weighted mean of the inks present on this row.
            for (int y = top; y < start + 4; y++) {
                double sum = 0, r = 0, gr = 0, b = 0;
                for (int k : inks) {
                    double d = (start - y - RF[k] * front) / spread(k), a = Math.exp(-d * d);
                    sum += a;
                    r += a * ((INK[k] >> 16) & 0xFF);
                    gr += a * ((INK[k] >> 8) & 0xFF);
                    b += a * (INK[k] & 0xFF);
                }
                if (sum < 0.1) continue;
                // The more inks share a row, the darker and duller it gets, like real ink piling up.
                double dull = Math.max(0, Math.min(0.45, (sum - 1) * 0.35));
                int colour = 0xFF000000 | ((int) (r / sum * (1 - dull) + 0x5A * dull) << 16) | ((int) (gr / sum * (1 - dull) + 0x40 * dull) << 8)
                        | (int) (b / sum * (1 - dull) + 0x48 * dull);
                double a = Math.min(1, sum);
                int half = (int) Math.round(3 + 6 * Math.sqrt(a)), base = y > start - f ? WET : PAPER;
                g.fill(x + STRIP_W / 2 - half, y, x + STRIP_W / 2 + half, y + 1, mix(base, colour, Math.min(1, a * 1.15)));
            }
        }
        g.pose().pushPose();
        g.pose().translate(x + STRIP_W / 2f - 8, top - 12, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        if (i == 0) g.setColor(1f, 0.75f, 0.45f, 1f);
        Draw.image(g, CLIP, 0, 0, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("every_trace_is_a_blend");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("lift_the_rack"), Draw.MOUSE), new Control(T.f("name_two_references"), Draw.MOUSE)};
    }
}
