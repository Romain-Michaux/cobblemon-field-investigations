package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Chromatography: stop each strip once its three bands are clearly separated.
final class ChromatographyGame extends MiniGame {
    static final int N = 3, TOP = 96, GAP = 13;
    static final int[][] TINTS = {{0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9}, {0xFF9B5FC0, 0xFF5DBB5D, 0xFFE9A15B}, {0xFF4FC1C5, 0xFFEE6A9A, 0xFFB8B8A0}};
    final double[] front = new double[N], speed = new double[N];
    final double[][] ratio = new double[N][3];
    // 0 = running, 1 = stopped in time, 2 = failed.
    final int[] state = new int[N];
    int faults, waiting = 24, tic, flash;

    ChromatographyGame(Random h) {
        for (int i = 0; i < N; i++) {
            speed[i] = 0.22 + h.nextDouble() * 0.2;
            double e1 = 0.15 + h.nextDouble() * 0.1, e2 = 0.15 + h.nextDouble() * 0.1;
            ratio[i][0] = 0.3 + h.nextDouble() * 0.08;
            ratio[i][1] = ratio[i][0] + e1;
            ratio[i][2] = ratio[i][1] + e2;
        }
    }

    boolean separated(int i) {
        return (ratio[i][1] - ratio[i][0]) * front[i] >= GAP && (ratio[i][2] - ratio[i][1]) * front[i] >= GAP;
    }

    void missed(int i) {
        state[i] = 2;
        flash = 8;
        faults++;
        Draw.note(0.5f);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (waiting > 0) {
            if (--waiting == 0) Draw.beep(Sounds.DROP, 1.0f, 0.7f);
            return;
        }
        tic++;
        boolean running = false;
        double max = 0;
        for (int i = 0; i < N; i++) {
            if (state[i] != 0) continue;
            front[i] += speed[i];
            max = Math.max(max, front[i]);
            if (front[i] >= TOP) missed(i); // the solvent reached the top: the strip is lost
            else running = true;
        }
        if (running && tic % 8 == 0) Draw.beep(Sounds.TICK, (float) (0.7 + max / TOP), 0.35f);
        if (!running && !finished) {
            perfect = faults == 0;
            end(faults <= 1);
        }
    }

    int bx(int i) {
        return ox + 4 + 18 + i * 48;
    }

    @Override
    void click(double mx, double my) {
        if (waiting > 0) return;
        for (int i = 0; i < N; i++) {
            if (state[i] != 0 || !Draw.in(mx, my, bx(i), oy + 3 + 8, 30, TOP)) continue;
            if (separated(i)) {
                state[i] = 1;
                Draw.note(1.2f + i * 0.15f);
            } else {
                missed(i); // stopped too early: the bands still overlap
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        for (int i = 0; i < N; i++) {
            int x = bx(i), top = ay + 8, bottom = top + TOP, f = (int) front[i];
            boolean hover = state[i] == 0 && waiting == 0 && Draw.in(mx, my, x, top, 30, TOP);
            g.fill(x - 1, top - 1, x + 31, bottom + 1, state[i] == 1 ? Draw.GREEN : state[i] == 2 ? Draw.ALERT : hover ? Draw.YELLOW : Draw.EDGE);
            g.fill(x, top, x + 30, bottom, 0xFFF1EBDD);
            g.fill(x, bottom - f, x + 30, bottom, 0xFFD8E2E8); // paper wetted by the solvent
            g.fill(x, bottom - f, x + 30, bottom - f + 1, 0xFF8FB0C8);
            for (int k = 0; k < 3; k++) {
                int y = bottom - (int) (ratio[i][k] * front[i]);
                g.fill(x + 3, y - 3, x + 27, y + 3, TINTS[i][k]);
            }
            g.fill(x, bottom - 2, x + 30, bottom - 1, 0xFF7D6248);
            g.fill(x + 33, top + 2, x + 35, top + 2 + GAP, Draw.GREY);
            g.fill(x + 32, top + 2, x + 36, top + 3, Draw.GREY);
            g.fill(x + 32, top + 1 + GAP, x + 36, top + 2 + GAP, Draw.GREY);
        }
        int tx = ox + 172, ok = 0;
        for (int e : state) ok += e == 1 ? 1 : 0;
        text(g, T.f("strips") + ok + " / " + N, tx, oy + 10, Draw.TEXT);
        text(g, T.f("failed_2") + faults + " / 2", tx, oy + 22, faults > 0 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("the_small_grey_mark_shows"), tx, oy + 42, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("click_a_strip_to_stop");
    }
}
