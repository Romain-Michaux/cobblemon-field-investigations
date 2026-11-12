package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Microscope: bring the slide into focus, then point out the abnormal cell.
final class MicroscopeGame extends MiniGame {
    static final int N = 10;
    final Random h;
    final int[] px = new int[N], py = new int[N];
    int focus, target, abnormal, round, faults, time, duration, flash, pause;
    boolean sharp;

    MicroscopeGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        target = 15 + h.nextInt(70);
        int gap = 18 + h.nextInt(20);
        focus = target + (target > 50 ? -gap : gap);
        abnormal = h.nextInt(N);
        for (int i = 0; i < N; i++) {
            for (int attempt = 0; attempt < 80; attempt++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 40;
                px[i] = (int) (80 + Math.cos(a) * r);
                py[i] = (int) (56 + Math.sin(a) * r);
                boolean free = true;
                for (int k = 0; k < i; k++) free &= Math.hypot(px[k] - px[i], py[k] - py[i]) > 19;
                if (free) break;
            }
        }
        duration = time = 300 - round * 40;
        sharp = false;
    }

    int blur() {
        return Math.abs(focus - target);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (--time <= 0) fault();
        else hurryTicks(time, duration);
    }

    void fault() {
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 2) end(false);
        else pause = 24;
    }

    int bx(int i) {
        return ox + 172 + (i % 2) * 52;
    }

    int by(int i) {
        return oy + 44 + (i / 2) * 22;
    }

    @Override
    void click(double mx, double my) {
        if (pause > 0) return;
        int[] steps = {-5, 5, -1, 1};
        for (int i = 0; i < 4; i++) {
            if (!Draw.in(mx, my, bx(i), by(i), 48, 18)) continue;
            focus = Math.max(0, Math.min(100, focus + steps[i]));
            Draw.beep(Sounds.KNOB, 0.8f + (float) Math.max(0, 30 - blur()) / 30f, 0.6f);
            if (blur() <= 2 && !sharp) {
                sharp = true;
                Draw.note(1.6f);
            } else if (blur() > 2) {
                sharp = false;
            }
            return;
        }
        if (blur() > 2) return;
        int ax = ox + 4, ay = oy + 3;
        for (int i = 0; i < N; i++) {
            if (Math.hypot(mx - ax - px[i], my - ay - py[i]) > 8) continue;
            if (i == abnormal) {
                Draw.note(1f + round * 0.25f);
                if (++round >= 3) {
                    perfect = faults == 0;
                    end(true);
                } else {
                    pause = 24;
                }
            } else {
                fault();
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3, f = blur();
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF0A0E12);
        disc(g, ax + 80, ay + 56, 54, 0xFF1B3A40);
        disc(g, ax + 80, ay + 56, 52, 0xFFCFE8DC);
        for (int i = 0; i < N; i++) {
            int x = ax + px[i], y = ay + py[i];
            if (f > 2) {
                // Out of focus: the cell is just a pale blob, wider the further off the focus is.
                disc(g, x, y, 7 + Math.min(6, f / 4), 0x3060A080);
                disc(g, x, y, 5, 0x4060A080);
            } else {
                disc(g, x, y, 7, 0xFF3F8F70);
                disc(g, x, y, 6, 0xFF9FD8B8);
                if (i == abnormal) {
                    g.fill(x - 4, y - 2, x - 1, y + 1, 0xFF2A5C48);
                    g.fill(x + 1, y, x + 4, y + 3, 0xFF2A5C48);
                } else {
                    g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF2A5C48);
                }
            }
        }
        Draw.circle(g, ax + 80, ay + 56, 53, 2, 0xFF0A0E12);
        int tx = ox + 172;
        text(g, T.f("slide") + Math.min(3, round + 1) + " / 3", tx, oy + 6, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 2", tx, oy + 90, faults > 0 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("sharpness"), tx, oy + 20, f <= 2 ? Draw.GREEN : Draw.YELLOW);
        gauge(g, tx, oy + 31, 100, 1 - f / 40.0, f <= 2 ? Draw.GREEN : Draw.YELLOW);
        String[] labels = {"<<", ">>", "<", ">"};
        for (int i = 0; i < 4; i++) Draw.button(g, bx(i), by(i), 48, 18, labels[i], pause == 0 && Draw.in(mx, my, bx(i), by(i), 48, 18), pause == 0 && !finished, 0xFF7C8FA3);
        timeGauge(g, tx, oy + 104, 100, pause > 0 ? 0 : time / (double) duration);
    }

    @Override
    String instruction() {
        return T.f("focus_with_the_knobs_coarse");
    }
}
