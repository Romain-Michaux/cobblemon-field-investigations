package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Relevé des griffures : suivre chaque entaille sans en sortir
final class TracingGame extends MiniGame {
    static final ResourceLocation BARK = Draw.texture("bark");
    static final int N = 44;
    final double[][] px = new double[3][N], py = new double[3][N];
    int line, progress, faults, flash;
    boolean ongoing, waitRelease;

    TracingGame(Random h) {
        for (int k = 0; k < 3; k++) {
            double x0 = 26 + k * 40 + h.nextInt(8), tilt = 22 + h.nextInt(14), curve = 3 + h.nextDouble() * 4, phaseShift = h.nextDouble() * 6.28;
            for (int i = 0; i < N; i++) {
                double t = i / (double) (N - 1);
                px[k][i] = x0 + t * tilt + Math.sin(t * Math.PI * 2 + phaseShift) * curve;
                py[k][i] = 14 + t * 88;
            }
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
    }

    void fault() {
        ongoing = false;
        waitRelease = true;
        progress = 0;
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 3) end(false);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        if (!finished) follow(mx - bx, my - by);
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Draw.ALERT : Draw.EDGE);
        Draw.image(g, BARK, bx, by, 160, 112);
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < N - 1; i++) {
                Draw.segment(g, bx + px[k][i] - 2, by + py[k][i] - 1, bx + px[k][i + 1] - 2, by + py[k][i + 1] - 1, 5, 0xFF1B120C);
            }
            for (int i = 0; i < N - 1; i++) {
                boolean done = k < line || (k == line && i < progress);
                Draw.segment(g, bx + px[k][i] - 1, by + py[k][i] - 1, bx + px[k][i + 1] - 1, by + py[k][i + 1] - 1, 3, done ? Draw.YELLOW : 0xFFE3C993);
            }
        }
        if (line < 3 && !ongoing) {
            boolean pulse = (System.currentTimeMillis() / 300) % 2 == 0;
            g.fill(bx + (int) px[line][0] - 4, by + (int) py[line][0] - 4, bx + (int) px[line][0] + 4, by + (int) py[line][0] + 4, Draw.EDGE);
            g.fill(bx + (int) px[line][0] - 3, by + (int) py[line][0] - 3, bx + (int) px[line][0] + 3, by + (int) py[line][0] + 3, pulse ? Draw.ACCENT : Draw.TEXT);
        }
        int tx = ox + 172;
        Draw.write(g, T.f("scratch") + Math.min(3, line + 1) + " / 3", tx, oy + 12, Draw.TEXT, false);
        Draw.write(g, T.f("slips") + faults + " / 3", tx, oy + 24, faults > 1 ? Draw.ALERT : Draw.GREY, false);
        Draw.write(g, ongoing ? T.f("follow_the_groove") : waitRelease ? T.f("release_and_retry") : T.f("start_from_the_dot"), tx, oy + 44,
                ongoing ? Draw.ACCENT : Draw.YELLOW, false);
        Draw.paragraph(g, T.f("hold_the_click_from_top"), tx, oy + 64, 104, Draw.DARK);
    }

    void follow(double mx, double my) {
        if (!press) {
            ongoing = false;
            waitRelease = false;
            progress = 0;
            return;
        }
        if (waitRelease || line >= 3) return;
        if (!ongoing) {
            if (Math.hypot(mx - px[line][0], my - py[line][0]) < 8) {
                ongoing = true;
                progress = 0;
                Draw.sound1(1.3f);
            }
            return;
        }
        while (progress < N - 1 && Math.hypot(mx - px[line][progress + 1], my - py[line][progress + 1]) < 8) {
            if (++progress % 5 == 0) Draw.beep(Sounds.RUB, 1.1f + progress * 0.012f, 0.5f);
        }
        if (progress >= N - 1) {
            Draw.note(1f + line * 0.25f);
            ongoing = false;
            waitRelease = true;
            progress = 0;
            if (++line >= 3) {
                perfect = faults == 0;
                end(true);
            }
        } else if (Math.hypot(mx - px[line][progress], my - py[line][progress]) > 13) {
            fault();
        }
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_on_the");
    }
}
