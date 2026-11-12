package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Filtrage du cri (enquête dans la brume) : suivre la fréquence où le cri ressort du bruit
final class FilterGame extends MiniGame {
    static final int DURATION = 320, REQUIRED = 70;
    final double p;
    final Random noise = new Random(5);
    int time = DURATION, tic;
    double acquired, clarity, cx;

    FilterGame(Random h) {
        p = h.nextDouble() * 6;
    }

    double target() {
        return 0.5 + 0.33 * Math.sin(tic * 0.019 + p) + 0.07 * Math.sin(tic * 0.083);
    }

    @Override
    void tick() {
        if (--time <= 0) {
            end(false);
            return;
        }
        press1(time, DURATION);
        tic++;
        double setting = Math.max(0, Math.min(1, (cx - ox - 4) / L));
        clarity = Math.max(0, 1 - Math.abs(setting - target()) * 6);
        acquired = clarity > 0.8 ? acquired + 1 : Math.max(0, acquired - 0.3);
        // Le signal sonne d'autant plus aigu et fort que le cri est net.
        if (tic % 5 == 0) Draw.beep(Sounds.SCAN, (float) (0.5 + clarity * 1.4), (float) (0.2 + clarity * 0.4));
        if (acquired >= REQUIRED) {
            perfect = true;
            end(true);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        frame(g, bx, by, false);
        g.fill(bx, by, bx + L, by + H, 0xFF0A1418);
        for (int k = 1; k < 4; k++) g.fill(bx, by + k * 24, bx + L, by + k * 24 + 1, 0xFF12303A);
        // L'onde : un cri régulier quand le filtre est bien réglé, du bruit sinon.
        noise.setSeed(tic / 2);
        int before = by + 48;
        for (int x = 0; x < L; x += 2) {
            double envelope = Math.sin(Math.PI * x / L);
            int y = by + 48 + (int) (Math.sin(x * 0.22 + tic * 0.35) * 26 * clarity * envelope + (noise.nextDouble() * 2 - 1) * 26 * (1 - clarity));
            g.fill(bx + x, Math.min(before, y), bx + x + 2, Math.max(before, y) + 2, mix(0xFF5D6E80, 0xFF6CF0A0, clarity));
            before = y;
        }
        g.fill(bx, by + 100, bx + L, by + 104, 0xFF12303A);
        int rx = Math.max(bx + 2, Math.min(bx + L - 2, mx));
        g.fill(rx - 2, by + 96, rx + 2, by + 108, clarity > 0.8 ? Draw.GREEN : Draw.YELLOW);
        int tx = ox + 172;
        text(g, T.f("clarity"), tx, oy + 10, clarity > 0.8 ? Draw.GREEN : Draw.YELLOW);
        gauge(g, tx, oy + 21, 100, clarity, clarity > 0.8 ? Draw.GREEN : Draw.YELLOW);
        text(g, T.f("cry_isolated_2"), tx, oy + 36, Draw.TEXT);
        gauge(g, tx, oy + 47, 100, acquired / REQUIRED, Draw.ACCENT);
        Draw.paragraph(g, T.f("trust_the_sound_the_higher"), tx, oy + 60, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("move_the_mouse_left_and");
    }
}
