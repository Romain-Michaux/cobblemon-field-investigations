package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Électrophorèse : retrouver, parmi des pistes presque identiques, celle de l'échantillon
final class GelGame extends MiniGame {
    static final ResourceLocation TANK = Draw.texture("gel");
    // Une bande = sa position (FENTES crans de 5 px), son épaisseur (fine ou large) et son intensité (pâle ou vive).
    static final int SLOTS = 14, STEP = 30, WIDTH = 22;
    final Random h;
    int[][] leads;          // pistes[0] = l'échantillon ; chaque piste : FENTES valeurs (0 = rien, sinon bit 0 = large, bit 1 = vive, +4)
    int good, round, errors, time, duration, pause, chosenOne = -1;
    boolean lastOk;

    GelGame(Random h) {
        this.h = h;
        newB();
    }

    int band(boolean wide, boolean vivid) {
        return 4 | (wide ? 1 : 0) | (vivid ? 2 : 0);
    }

    // Une piste voisine : une seule différence discrète avec le modèle (bande décalée d'un cran, épaisseur ou
    // intensité changée, bande pâle en plus ou en moins).
    int[] variant(int[] model) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int[] p = model.clone();
            int i = h.nextInt(SLOTS);
            switch (h.nextInt(round == 0 ? 3 : 5)) {
                case 0 -> {
                    int towards = i + (h.nextBoolean() ? 1 : -1);
                    if (p[i] == 0 || towards < 0 || towards >= SLOTS || p[towards] != 0) continue;
                    p[towards] = p[i];
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

    void newB() {
        int count1 = round == 0 ? 5 : 6;
        leads = new int[count1 + 1][];
        int[] model = new int[SLOTS];
        for (int n = 0; n < 6 + round; ) {
            int i = h.nextInt(SLOTS);
            if (model[i] != 0) continue;
            model[i] = band(h.nextInt(3) == 0, h.nextBoolean());
            n++;
        }
        leads[0] = model;
        good = 1 + h.nextInt(count1);
        for (int k = 1; k <= count1; k++) {
            if (k == good) {
                leads[k] = model.clone();
                continue;
            }
            for (int attempt = 0; attempt < 100; attempt++) {
                int[] p = variant(model);
                boolean newOne = !java.util.Arrays.equals(p, model);
                for (int j = 1; j < k; j++) newOne &= leads[j] == null || !java.util.Arrays.equals(p, leads[j]);
                leads[k] = p;
                if (newOne) break;
            }
        }
        duration = time = 320 - round * 30;
        chosenOne = -1;
    }

    void judge(int lead) {
        chosenOne = lead;
        lastOk = lead == good;
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
            if (--pause == 0 && !finished) newB();
            return;
        }
        if (--time <= 0) judge(-1);
        else press1(time, duration);
    }

    int px(int k) {
        return ox + 16 + k * STEP + (k > 0 ? 6 : 0);
    }

    @Override
    void click(double mx, double my) {
        if (pause > 0) return;
        for (int k = 1; k < leads.length; k++) {
            if (Draw.in(mx, my, px(k), oy + 20, WIDTH, 78)) {
                judge(k);
                return;
            }
        }
    }

    void lead(GuiGraphics g, int k, int frame) {
        int x = px(k);
        if (frame != 0) {
            g.fill(x - 2, oy + 18, x + WIDTH + 2, oy + 19, frame);
            g.fill(x - 2, oy + 99, x + WIDTH + 2, oy + 100, frame);
            g.fill(x - 2, oy + 18, x - 1, oy + 100, frame);
            g.fill(x + WIDTH + 1, oy + 18, x + WIDTH + 2, oy + 100, frame);
        }
        g.fill(x, oy + 20, x + WIDTH, oy + 98, 0x50081420);
        g.fill(x + 4, oy + 21, x + WIDTH - 4, oy + 24, 0xFF0A1622);
        for (int i = 0; i < SLOTS; i++) {
            int b = leads[k][i];
            if (b == 0) continue;
            boolean wide = (b & 1) != 0, vivid = (b & 2) != 0;
            int y = oy + 27 + i * 5, ep = wide ? 4 : 2, colour = vivid ? 0xFF8CFAEC : 0xFF3C9C98;
            if (vivid) g.fill(x + 2, y - 1, x + WIDTH - 2, y + ep + 1, 0x7030C8C0);
            g.fill(x + 3, y, x + WIDTH - 3, y + ep, colour);
            g.fill(x + 5, y + ep, x + WIDTH - 5, y + ep + 1, 0x4030C8C0);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        timeGauge(g, ox + 6, oy + 3, 232, pause > 0 ? 0 : time / (double) duration);
        Draw.image(g, TANK, ox + 2, oy + 11, 240, 104);
        lead(g, 0, Draw.YELLOW);
        Draw.centre(g, T.f("smp"), px(0) + WIDTH / 2, oy + 103, Draw.YELLOW);
        for (int k = 1; k < leads.length; k++) {
            boolean hover = pause == 0 && !finished && Draw.in(mx, my, px(k), oy + 20, WIDTH, 78);
            int frame = pause > 0 || finished ? (k == good ? Draw.GREEN : k == chosenOne ? Draw.ALERT : 0) : hover ? Draw.TEXT : 0;
            lead(g, k, frame);
            Draw.centre(g, String.valueOf(k), px(k) + WIDTH / 2, oy + 103, frame == 0 ? Draw.GREY : frame);
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
