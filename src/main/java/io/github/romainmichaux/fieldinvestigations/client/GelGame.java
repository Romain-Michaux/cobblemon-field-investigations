package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Electrophoresis: among nearly identical lanes, find the one matching the sample.
final class GelGame extends MiniGame {
    static final ResourceLocation TANK = Draw.texture("gel");
    // A band = its position (SLOTS steps of 5 px), its thickness (thin or wide) and its intensity (pale or vivid).
    static final int SLOTS = 14, STEP = 30, WIDTH = 22;
    final Random h;
    int[][] lanes;          // leads[0] = the sample; each lane: SLOTS values (0 = no band, else 4 | bit 0 = wide | bit 1 = vivid)
    int good, round, errors, time, duration, pause, chosen = -1;
    boolean lastOk;

    GelGame(Random h) {
        this.h = h;
        newRound();
    }

    int band(boolean wide, boolean vivid) {
        return 4 | (wide ? 1 : 0) | (vivid ? 2 : 0);
    }

    // A decoy lane: a single subtle difference from the model (a band shifted one step, thickness or
    // intensity changed, or a pale band added or removed).
    int[] variant(int[] model) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int[] p = model.clone();
            int i = h.nextInt(SLOTS);
            switch (h.nextInt(round == 0 ? 3 : 5)) {
                case 0 -> {
                    int dest = i + (h.nextBoolean() ? 1 : -1);
                    if (p[i] == 0 || dest < 0 || dest >= SLOTS || p[dest] != 0) continue;
                    p[dest] = p[i];
                    p[i] = 0;
                }
                case 1 -> {
                    if (p[i] == 0) continue;
                    p[i] ^= 1;
                }
                case 2 -> {
                    if (p[i] == 0) continue;
                    p[i] ^= 2;
                }
                case 3 -> {
                    if (p[i] != 0) continue;
                    p[i] = band(false, false);
                }
                default -> {
                    if (p[i] == 0 || (p[i] & 2) != 0) continue;
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
        for (int n = 0; n < 6 + round; ) {
            int i = h.nextInt(SLOTS);
            if (model[i] != 0) continue;
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
        duration = time = 320 - round * 30;
        chosen = -1;
    }

    void judge(int lane) {
        chosen = lane;
        lastOk = lane == good;
        pause = lastOk ? 16 : 40;
        if (lastOk) {
            Draw.note(1f + round * 0.25f);
            if (++round >= 3) {
                perfect = errors == 0;
                end(true);
            }
        } else {
            Draw.note(0.5f);
            if (++errors >= 2) end(false);
        }
    }

    @Override
    void tick() {
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (--time <= 0) judge(-1);
        else hurryTicks(time, duration);
    }

    int px(int k) {
        return ox + 16 + k * STEP + (k > 0 ? 6 : 0);
    }

    @Override
    void click(double mx, double my) {
        if (pause > 0) return;
        for (int k = 1; k < lanes.length; k++) {
            if (Draw.in(mx, my, px(k), oy + 20, WIDTH, 78)) {
                judge(k);
                return;
            }
        }
    }

    void drawLane(GuiGraphics g, int k, int border) {
        int x = px(k);
        if (border != 0) {
            g.fill(x - 2, oy + 18, x + WIDTH + 2, oy + 19, border);
            g.fill(x - 2, oy + 99, x + WIDTH + 2, oy + 100, border);
            g.fill(x - 2, oy + 18, x - 1, oy + 100, border);
            g.fill(x + WIDTH + 1, oy + 18, x + WIDTH + 2, oy + 100, border);
        }
        g.fill(x, oy + 20, x + WIDTH, oy + 98, 0x50081420);
        g.fill(x + 4, oy + 21, x + WIDTH - 4, oy + 24, 0xFF0A1622);
        for (int i = 0; i < SLOTS; i++) {
            int b = lanes[k][i];
            if (b == 0) continue;
            boolean wide = (b & 1) != 0, vivid = (b & 2) != 0;
            int y = oy + 27 + i * 5, thickness = wide ? 4 : 2, colour = vivid ? 0xFF8CFAEC : 0xFF3C9C98;
            if (vivid) g.fill(x + 2, y - 1, x + WIDTH - 2, y + thickness + 1, 0x7030C8C0);
            g.fill(x + 3, y, x + WIDTH - 3, y + thickness, colour);
            g.fill(x + 5, y + thickness, x + WIDTH - 5, y + thickness + 1, 0x4030C8C0);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        timeGauge(g, ox + 6, oy + 3, 232, pause > 0 ? 0 : time / (double) duration);
        Draw.image(g, TANK, ox + 2, oy + 11, 240, 104);
        drawLane(g, 0, Draw.YELLOW);
        Draw.centre(g, T.f("smp"), px(0) + WIDTH / 2, oy + 103, Draw.YELLOW);
        for (int k = 1; k < lanes.length; k++) {
            boolean hover = pause == 0 && !finished && Draw.in(mx, my, px(k), oy + 20, WIDTH, 78);
            int border = pause > 0 || finished ? (k == good ? Draw.GREEN : k == chosen ? Draw.ALERT : 0) : hover ? Draw.TEXT : 0;
            drawLane(g, k, border);
            Draw.centre(g, String.valueOf(k), px(k) + WIDTH / 2, oy + 103, border == 0 ? Draw.GREY : border);
        }
        int tx = ox + 246;
        Draw.write(g, T.f("gel"), tx, oy + 16, Draw.GREY, false);
        Draw.write(g, Math.min(3, round + (pause > 0 || finished ? 0 : 1)) + " / 3", tx, oy + 27, Draw.TEXT, false);
        Draw.write(g, T.f("err"), tx, oy + 46, Draw.GREY, false);
        Draw.write(g, errors + " / 2", tx, oy + 57, errors > 0 ? Draw.ALERT : Draw.TEXT, false);
        if (pause > 0 || finished) Draw.write(g, lastOk ? T.f("yes") : T.f("no"), tx, oy + 78, lastOk ? Draw.GREEN : Draw.ALERT, false);
    }

    @Override
    String instruction() {
        return T.f("click_the_lane_identical_to");
    }
}
