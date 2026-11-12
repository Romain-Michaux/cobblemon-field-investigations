package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Casting: pour plaster into each socket of the jaw, right up to the brim, without spilling over.
final class CastGame extends MiniGame {
    static final int N = 5, DURATION = 560, BLOCK_TOP = 24, STEP = 30;
    // Each socket has its own width and depth (a canine is narrow and deep, a molar wide and shallow).
    final int[] widthPx = new int[N], target = new int[N];
    final double[] fillLevel = new double[N];
    int time = DURATION, faults, flash, tic;
    double cx;

    CastGame(Random h) {
        for (int i = 0; i < N; i++) {
            widthPx[i] = 16 + h.nextInt(3) * 4;
            target[i] = 34 + h.nextInt(44);
        }
    }

    int left(int i) {
        return ox + 4 + 6 + i * STEP + (STEP - widthPx[i]) / 2;
    }

    int column(double mx) {
        for (int i = 0; i < N; i++) {
            if (mx >= left(i) && mx < left(i) + widthPx[i]) return i;
        }
        return -1;
    }

    boolean full(int i) {
        return fillLevel[i] >= target[i] - 6;
    }

    // Inset of the socket wall at this height above the bottom: the root is narrower than the crown.
    int inset(int i, int heightPx) {
        return heightPx < target[i] * 0.4 ? 4 : heightPx < target[i] * 0.55 ? 2 : 0;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        tic++;
        int i = mouseDown ? column(cx) : -1;
        if (i >= 0) {
            fillLevel[i] += 1.1;
            if (tic % 5 == 0) Draw.beep(Sounds.PLASTER, (float) (0.6 + fillLevel[i] / target[i]), 0.5f);
            if (fillLevel[i] > target[i] + 1.5) {
                // Overflow: the socket is scraped and has to be refilled almost from scratch.
                fillLevel[i] = target[i] * 0.3;
                flash = 8;
                Draw.beep(Sounds.OVERFLOW, 1f, 0.9f);
                if (++faults >= 3) end(false);
            }
        }
        // The cast only counts once the spout is lifted: stopping in time is up to the player.
        boolean all = !mouseDown;
        for (int k = 0; k < N; k++) all &= full(k);
        if (all && !finished) {
            perfect = faults == 0;
            Draw.beep(Sounds.STONE, 1.2f, 0.8f);
            end(true);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3, top = by + BLOCK_TOP;
        cx = mx;
        frame(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
        g.fill(bx, top, bx + L, by + H, 0xFFB5746A);
        g.fill(bx, top, bx + L, top + 3, 0xFFD69A8C);
        g.fill(bx, by + H - 10, bx + L, by + H, 0xFF8F564E);
        int hover = finished ? -1 : column(mx);
        for (int i = 0; i < N; i++) {
            int x = left(i), bottom = top + target[i];
            for (int k = 0; k < target[i]; k++) {
                int r = inset(i, k), y = bottom - 1 - k;
                g.fill(x + r, y, x + widthPx[i] - r, y + 1, k < fillLevel[i] ? (k >= (int) fillLevel[i] - 1 ? 0xFFFFFFFF : 0xFFE9E4D8) : 0xFF3A1A1E);
            }
            // The right height: the last six pixels below the rim.
            g.fill(x, top, x + 2, top + 6, Draw.GREEN);
            g.fill(x + widthPx[i] - 2, top, x + widthPx[i], top + 6, Draw.GREEN);
            if (i == hover) g.fill(x, top - 2, x + widthPx[i], top - 1, Draw.YELLOW);
            if (full(i)) text(g, "✔", x + widthPx[i] / 2 - 3, top - 11, Draw.GREEN);
        }
        if (!finished && Draw.in(mx, my, bx, by, L, H)) {
            g.fill(mx - 3, by + 1, mx + 3, by + 9, 0xFF9FB0C0);
            if (mouseDown && hover >= 0) g.fill(mx - 1, by + 9, mx + 1, top + target[hover] - (int) fillLevel[hover], 0xFFF4F0E6);
        }
        int tx = ox + 172, filled = 0;
        for (int i = 0; i < N; i++) filled += full(i) ? 1 : 0;
        text(g, T.f("sockets") + filled + " / " + N, tx, oy + 10, Draw.TEXT);
        text(g, T.f("spilled") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("fill_up_to_the_green"), tx, oy + 40, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("put_the_spout_over_a_2");
    }
}
