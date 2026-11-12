package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Sieving (buried clue): shake in bursts to let the sand through without losing the fragments
final class SieveGame extends MiniGame {
    static final ResourceLocation SIEVE = Draw.texture("sieve");
    static final int DURATION = 620, GRAINS = 110;
    final ResourceLocation clue;
    final int[] gx = new int[GRAINS], gy = new int[GRAINS], fx = new int[3], fy = new int[3];
    double sand = 100, shake;
    int time = DURATION, faults, flash, tic, revealed;

    SieveGame(Random h, String kind, int variant) {
        clue = Draw.buriedIcon(kind, variant);
        for (int i = 0; i < GRAINS; i++) {
            double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 31;
            gx[i] = (int) (48 + Math.cos(a) * r);
            gy[i] = (int) (48 + Math.sin(a) * r);
        }
        for (int i = 0; i < 3; i++) {
            double a = h.nextDouble() * Math.PI * 2 + i * 2.1, r = 8 + h.nextDouble() * 12;
            fx[i] = (int) (40 + Math.cos(a) * r);
            fy[i] = (int) (40 + Math.sin(a) * r);
        }
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
        if (mouseDown) {
            sand -= 0.55;
            shake += 2.2;
            if (tic % 4 == 0) Draw.beep(Sounds.SIEVE, (float) (0.8 + shake / 160), 0.5f);
        } else {
            shake = Math.max(0, shake - 3);
        }
        if (shake >= 100) {
            // Shaken too long in one go: a fragment slips through the mesh.
            shake = 35;
            flash = 8;
            Draw.beep(Sounds.CRACK, 1.2f, 0.7f);
            if (++faults >= 3) end(false);
        }
        int v = sand <= 0 ? 3 : sand < 34 ? 2 : sand < 67 ? 1 : 0;
        if (v > revealed) {
            revealed = v;
            Draw.note(1f + v * 0.2f);
        }
        if (sand <= 0 && !finished) {
            perfect = faults == 0;
            end(true);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF2A2019);
        int dx = mouseDown && !finished ? (tic % 2) * 4 - 2 : 0, sx = bx + 32 + dx, sy = by + 8;
        Draw.image(g, SIEVE, sx, sy, 96, 96);
        for (int i = 0; i < 3; i++) {
            if (i < revealed) Draw.image(g, clue, sx + fx[i], sy + fy[i], 16, 16);
        }
        int n = (int) (Math.max(0, sand) / 100 * GRAINS);
        for (int i = 0; i < n; i++) g.fill(sx + gx[i], sy + gy[i], sx + gx[i] + 3, sy + gy[i] + 3, i % 3 == 0 ? 0xFFB98F4E : 0xFFD9B36A);
        if (mouseDown && !finished) {
            for (int i = 0; i < 6; i++) g.fill(sx + 20 + (tic * 7 + i * 13) % 56, by + 100 + (tic + i * 5) % 10, sx + 22 + (tic * 7 + i * 13) % 56, by + 102 + (tic + i * 5) % 10, 0xFFD9B36A);
        }
        int tx = ox + 172;
        text(g, T.f("sand") + (int) Math.ceil(Math.max(0, sand)) + " %", tx, oy + 10, Draw.TEXT);
        text(g, T.f("lost") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("shaking"), tx, oy + 40, shake > 70 ? Draw.ALERT : Draw.YELLOW);
        gauge(g, tx, oy + 51, 100, shake / 100, shake > 70 ? Draw.ALERT : Draw.YELLOW);
        Draw.paragraph(g, T.f("let_go_before_the_gauge"), tx, oy + 64, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_to_shake");
    }
}
