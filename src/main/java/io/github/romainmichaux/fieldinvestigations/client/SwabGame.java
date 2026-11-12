package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Écouvillon : frotter toute la tache, sans repasser sur une zone déjà prélevée
final class SwabGame extends MiniGame {
    static final ResourceLocation BARK = Draw.texture("bark"), COTTON = Draw.texture("swab");
    static final int COLS = 20, RANKS = 14, CASE = 8, DURATION = 600;
    final boolean[] task = new boolean[COLS * RANKS];
    final int[] own = new int[COLS * RANKS];
    int time = DURATION, faults, flash, tic, total, doneOnes, lastOne = -1;
    double cx, cy;

    SwabGame(Random h) {
        for (int k = 0; k < 3; k++) {
            double ccx = 5 + h.nextInt(10), ccy = 4 + h.nextInt(6), r = 2.6 + h.nextDouble() * 1.4;
            for (int i = 0; i < task.length; i++) {
                if (Math.hypot(i % COLS - ccx, i / COLS - ccy) <= r) task[i] = true;
            }
        }
        for (boolean t : task) total += t ? 1 : 0;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--time <= 0) {
            end(false);
            return;
        }
        press1(time, DURATION);
        tic++;
        int c = (int) Math.floor((cx - ox - 4) / CASE), r = (int) Math.floor((cy - oy - 3) / CASE);
        int i = c >= 0 && c < COLS && r >= 0 && r < RANKS ? r * COLS + c : -1;
        if (!press || i < 0 || !task[i]) {
            lastOne = -1;
            return;
        }
        if (own[i] == 0) {
            own[i] = tic;
            if (++doneOnes % 2 == 0) Draw.beep(Sounds.RUB, 1.3f + doneOnes * 0.6f / total, 0.6f);
            if (doneOnes >= total) {
                perfect = faults == 0;
                Draw.note(1.5f);
                end(true);
            }
        } else if (i != lastOne && tic - own[i] > 14) {
            // Repasser sur une zone déjà frottée contamine l'écouvillon.
            flash = 8;
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        }
        if (own[i] != 0) own[i] = tic;
        lastOne = i;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        frame(g, bx, by, flash > 0);
        Draw.image(g, BARK, bx, by, L, H);
        for (int i = 0; i < task.length; i++) {
            if (!task[i]) continue;
            int x = bx + (i % COLS) * CASE, y = by + (i / COLS) * CASE;
            if (own[i] == 0) {
                g.fill(x, y, x + CASE, y + CASE, 0xD07A3FA8);
                g.fill(x + 1, y + 1, x + 4, y + 3, 0x60E0B0FF);
            } else {
                g.fill(x, y, x + CASE, y + CASE, 0x50F4F0E0);
            }
        }
        if (!finished && Draw.in(mx, my, bx, by, L, H)) Draw.image(g, COTTON, mx - 16, my - 62, 32, 64);
        int tx = ox + 172;
        text(g, T.f("swabbed") + (doneOnes * 100 / Math.max(1, total)) + " %", tx, oy + 10, Draw.TEXT);
        text(g, T.f("soiled") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("you_may_lift_the_swab"), tx, oy + 42, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_and_rub");
    }
}
