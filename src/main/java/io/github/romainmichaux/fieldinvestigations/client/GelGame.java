package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Electrophoresis: the gel has run, but its bands only show under the UV lamp that follows the pointer, so lanes are
// compared a patch at a time, from memory. The lane identical to the sample is clicked. The difference to spot gets
// finer over the three gels: a band added or missing, then its thickness or brightness, then a band shifted by one step.
final class GelGame extends MiniGame {
    static final ResourceLocation TANK = Draw.texture("gel"), LAMP = Draw.texture("gel_lamp");
    static final int SLOTS = 14, STEP = 34, WIDTH = 22, TOP = 28, BOTTOM = 99, LAMP_R = 17, MAX_ERRORS = 2;
    // How far the gel ran: the fastest band ends near the bottom, one slot apart is about three pixels.
    static final double DIST = 66, DYE = 1.08;
    static final int[] DURATION = {640, 600, 560};
    final Random h;
    int[][] lanes; // lanes[0] = the sample; each lane: SLOTS values (0 = no band, else 4 | bit 0 = wide | bit 1 = vivid)
    int good, round, errors, time, duration, pause, chosen = -1, phase, tic, differs = -1;
    double dist = DIST, mx, my;
    boolean lastOk, lampOn;

    // Phases: 2 reading, 3 verdict.
    GelGame(Random h) {
        this.h = h;
        newRound();
    }

    static double mobility(int slot) {
        return 0.30 + slot * 0.052;
    }

    int band(boolean wide, boolean vivid) {
        return 4 | (wide ? 1 : 0) | (vivid ? 2 : 0);
    }

    boolean free(int[] p, int i, int except) {
        for (int j = i - 1; j <= i + 1; j++) if (j >= 0 && j < SLOTS && j != except && p[j] != 0) return false;
        return true;
    }

    // A decoy lane: one difference from the model, of the kind this gel tests.
    int[] variant(int[] model) {
        for (int attempt = 0; attempt < 300; attempt++) {
            int[] p = model.clone();
            int i = h.nextInt(SLOTS);
            switch (round) {
                case 0 -> {
                    if (h.nextBoolean()) {
                        if (p[i] == 0) continue;
                        p[i] = 0;
                    } else {
                        if (!free(p, i, -1)) continue;
                        p[i] = band(false, h.nextBoolean());
                    }
                }
                case 1 -> {
                    if (p[i] == 0) continue;
                    p[i] ^= h.nextBoolean() ? 1 : 2;
                }
                default -> {
                    int dest = i + (h.nextBoolean() ? 1 : -1);
                    if (p[i] == 0 || dest < 0 || dest >= SLOTS || !free(p, dest, i)) continue;
                    p[dest] = p[i];
                    p[i] = 0;
                }
            }
            return p;
        }
        return model.clone();
    }

    void newRound() {
        int laneCount = round == 0 ? 5 : 6;
        lanes = new int[laneCount + 1][];
        int[] model = new int[SLOTS];
        for (int n = 0, guard = 0; n < 5 + Math.min(1, round) && guard < 500; guard++) {
            int i = h.nextInt(SLOTS);
            if (!free(model, i, -1)) continue;
            model[i] = band(h.nextInt(3) == 0, h.nextBoolean());
            n++;
        }
        lanes[0] = model;
        good = 1 + h.nextInt(laneCount);
        for (int k = 1; k <= laneCount; k++) {
            if (k == good) {
                lanes[k] = model.clone();
                continue;
            }
            for (int attempt = 0; attempt < 100; attempt++) {
                int[] p = variant(model);
                boolean distinct = !java.util.Arrays.equals(p, model);
                for (int j = 1; j < k; j++) distinct &= lanes[j] == null || !java.util.Arrays.equals(p, lanes[j]);
                lanes[k] = p;
                if (distinct) break;
            }
        }
        duration = time = DURATION[round];
        chosen = -1;
        differs = -1;
        phase = 2;
    }

    void judge(int lane) {
        chosen = lane;
        lastOk = lane == good;
        phase = 3;
        pause = lastOk ? 60 : 70;
        differs = -1;
        if (lane > 0 && !lastOk) {
            for (int i = 0; i < SLOTS; i++) if (lanes[lane][i] != lanes[0][i]) differs = i;
        }
        if (lastOk) Draw.note(1f + round * 0.25f);
        else {
            errors++;
            Draw.note(0.5f);
        }
    }

    int px(int k) {
        return ox + 24 + k * STEP + (k > 0 ? 8 : 0);
    }

    @Override
    void tick() {
        tic++;
        if (phase == 3) {
            if (--pause == 0 && !finished) {
                if (lastOk) {
                    if (++round >= 3) {
                        perfect = errors == 0;
                        end(true);
                        return;
                    }
                } else if (errors >= MAX_ERRORS) {
                    end(false);
                    return;
                }
                newRound();
            }
            return;
        }
        if (--time <= 0) {
            judge(-1);
            return;
        }
        hurryTicks(time, duration);
        if (phase == 2) {
            boolean lamp = inTank(mx, my);
            if (lamp && !lampOn) Draw.beep(Sounds.LAMP, 1.2f, 0.5f);
            lampOn = lamp;
        }
    }

    boolean inTank(double x, double y) {
        return Draw.in(x, y, ox + 4, oy + 16, AREA_W - 8, 100);
    }

    @Override
    void click(double x, double y) {
        if (finished || phase == 3) return;
        if (phase != 2) return;
        for (int k = 1; k < lanes.length; k++) {
            if (Draw.in(x, y, px(k) - 2, oy + 18, WIDTH + 4, BOTTOM - 18)) {
                judge(k);
                return;
            }
        }
    }

    // One lane's bands. In reading, only the part under the lamp shows; the verdict shows everything.
    void drawBands(GuiGraphics g, int k, boolean all) {
        int x = px(k);
        for (int i = 0; i < SLOTS; i++) {
            int b = lanes[k][i];
            if (b == 0) continue;
            boolean wide = (b & 1) != 0, vivid = (b & 2) != 0;
            int thick = wide ? 4 : 2, colour = vivid ? 0xFF8CFAEC : 0xFF3C9C98;
            double centre = oy + TOP + dist * mobility(i);
            if (centre > oy + BOTTOM) continue;
            int y0 = (int) Math.round(centre - thick / 2.0);
            for (int y = y0 - 1; y < y0 + thick + 1; y++) {
                boolean core = y >= y0 && y < y0 + thick;
                int c = core ? colour : (colour & 0x00FFFFFF) | (vivid ? 0x60000000 : 0x40000000);
                int a = x + 3 - (core ? 0 : 1), z = x + WIDTH - 3 + (core ? 0 : 1);
                fillLit(g, a, y, z, c, all);
            }
        }
    }

    // A row segment, clipped to the lamp's circle unless everything is shown.
    void fillLit(GuiGraphics g, int a, int y, int z, int c, boolean all) {
        if (!all) {
            double dy = y + 0.5 - my;
            if (Math.abs(dy) >= LAMP_R) return;
            double half = Math.sqrt(LAMP_R * LAMP_R - dy * dy);
            a = Math.max(a, (int) Math.ceil(mx - half));
            z = Math.min(z, (int) Math.floor(mx + half));
        }
        if (z > a) g.fill(a, y, z, y + 1, c);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy;
        Draw.image(g, TANK, ax + 2, ay + 15, AREA_W - 4, 103);
        boolean verdict = phase == 3 || finished;
        // Lanes: the gel under each, its well at the top, the blue loading dye where the run has taken it.
        for (int k = 0; k < lanes.length; k++) {
            int lx = px(k);
            g.fill(lx, ay + 20, lx + WIDTH, ay + BOTTOM + 1, 0x40081420);
            g.fill(lx + 3, ay + 21, lx + WIDTH - 3, ay + 25, 0xFF0A1622);
            double dye = ay + TOP + dist * DYE;
            if (dye < ay + BOTTOM + 2) g.fill(lx + 4, (int) dye, lx + WIDTH - 4, (int) dye + 2, 0xA04A6CF0);
            int border = 0;
            if (verdict) border = k == good ? Draw.GREEN : k == chosen ? Draw.ALERT : k == 0 ? Draw.YELLOW : 0;
            else if (k == 0) border = Draw.YELLOW;
            else if (phase == 2 && Draw.in(x, y, lx - 2, ay + 18, WIDTH + 4, BOTTOM - 18)) border = Draw.TEXT;
            if (border != 0) {
                g.fill(lx - 2, ay + 18, lx + WIDTH + 2, ay + 19, border);
                g.fill(lx - 2, ay + BOTTOM + 2, lx + WIDTH + 2, ay + BOTTOM + 3, border);
                g.fill(lx - 2, ay + 18, lx - 1, ay + BOTTOM + 3, border);
                g.fill(lx + WIDTH + 1, ay + 18, lx + WIDTH + 2, ay + BOTTOM + 3, border);
            }
            String label = k == 0 ? T.f("smp") : String.valueOf(k);
            Draw.centre(g, label, lx + WIDTH / 2, ay + BOTTOM + 4, border == 0 ? Draw.GREY : border);
        }
        // Under the lamp: a violet glow, and the bands it lights.
        if (lampOn && !verdict) disc(g, x, y, LAMP_R, 0x307A3CFF);
        for (int k = 0; k < lanes.length; k++) {
            if (verdict) drawBands(g, k, true);
            else if (lampOn) drawBands(g, k, false);
        }
        if (lampOn && !verdict) {
            Draw.circle(g, x - 1, y - 1, LAMP_R, 1, 0x80B89CFF);
            g.pose().pushPose();
            g.pose().translate(x + LAMP_R - 4, y + LAMP_R - 6, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            Draw.image(g, LAMP, 0, 0, 21, 35);
            g.pose().popPose();
        }
        if (verdict) drawVerdict(g, ax, ay);

        // Counters, time line and hints.
        String left = T.f("gel") + " " + Math.min(3, round + 1) + " / 3", right = T.f("mistakes") + errors + " / " + MAX_ERRORS;
        text(g, left, ax + 6, ay + 2, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 2, errors > 0 ? Draw.ALERT : Draw.GREY);
        double part = phase == 3 ? 0 : time / (double) duration;
        g.fill(ax + 6, ay + 12, ax + 6 + (int) ((AREA_W - 12) * part), ay + 13, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
        String hint = "";
        int hc = Draw.GREY;
        if (phase == 2) hint = T.f("sweep_with_the_lamp");
        else if (phase == 3) {
            hint = chosen < 0 ? T.f("time_s_up") : lastOk ? T.f("same_profile") : T.f("not_the_same_profile");
            hc = lastOk ? Draw.GREEN : Draw.ALERT;
        }
        if (!hint.isEmpty()) Draw.centre(g, hint, ax + AREA_W / 2, ay + 2, hc);
    }

    // Verdict: a size ladder beside the sample, and the sample's bands joined to the right lane by glowing lines; on a
    // wrong pick, the band that differs is ringed in red.
    void drawVerdict(GuiGraphics g, int ax, int ay) {
        int lx = ax + 9;
        g.fill(lx, ay + 20, lx + 6, ay + BOTTOM + 1, 0x60081420);
        for (int i = 0; i < SLOTS; i += 2) {
            double c = ay + TOP + dist * mobility(i);
            if (c <= ay + BOTTOM) g.fill(lx + 1, (int) c, lx + 5, (int) c + 1, 0xC0E8F0F6);
        }
        double grow = Math.min(1, (70 - pause) / 18.0);
        if (lastOk) {
            int from = px(0) + WIDTH, to = px(good);
            for (int i = 0; i < SLOTS; i++) {
                if (lanes[0][i] == 0) continue;
                double c = ay + TOP + dist * mobility(i);
                if (c > ay + BOTTOM) continue;
                int end = from + (int) ((to - from) * grow);
                for (int xx = from + 1; xx < end; xx += 3) g.fill(xx, (int) c, xx + 2, (int) c + 1, 0xC06CF0A0);
            }
        } else if (differs >= 0 && chosen > 0) {
            double c = ay + TOP + dist * mobility(differs);
            if (c <= ay + BOTTOM + 2) {
                int cx = px(chosen);
                Draw.circle(g, cx + WIDTH / 2.0 - 1, c - 1, 8, 1, Draw.ALERT);
            }
        }
    }

    @Override
    String instruction() {
        return T.f("the_gel_has_run_its");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("lamp_then_pick_a_lane"), Draw.MOUSE)};
    }
}
