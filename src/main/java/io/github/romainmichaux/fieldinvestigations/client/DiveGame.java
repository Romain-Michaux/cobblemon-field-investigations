package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Breath-hold dive (aquatic investigation): fill the vial on the trace the current keeps moving, before running out of air
final class DiveGame extends MiniGame {
    static final ResourceLocation BACKGROUND = Draw.texture("bg_water");
    final double p, q;
    double air = 100, vial;
    int tic;
    boolean onTrace;
    double cx, cy;

    DiveGame(Random h) {
        p = h.nextDouble() * 6;
        q = h.nextDouble() * 6;
    }

    double tx() {
        double v = 1 + vial / 120;
        return 80 + 52 * Math.sin(tic * 0.03 * v + p) + 8 * Math.sin(tic * 0.11);
    }

    double ty() {
        double v = 1 + vial / 120;
        return 62 + 30 * Math.sin(tic * 0.047 * v + q);
    }

    @Override
    void tick() {
        tic++;
        air -= 0.22;
        onTrace = mouseDown && Math.hypot(cx - ox - 4 - tx(), cy - oy - 3 - ty()) < 12;
        if (onTrace) {
            vial += 1;
            if (tic % 12 == 0) Draw.beep(Sounds.VIAL, (float) (0.8 + vial / 120), 0.6f);
        } else {
            vial = Math.max(0, vial - 0.15);
        }
        if (tic % 14 == 0) Draw.beep(Sounds.BUBBLE, 0.8f + (tic % 5) * 0.1f, 0.35f);
        // The heartbeat races as air runs low.
        if (air < 35 && tic % (air < 15 ? 8 : 14) == 0) Draw.beep(Sounds.HEARTBEAT, 1.2f, 0.9f);
        if (vial >= 100) {
            perfect = air >= 40;
            end(true);
        } else if (air <= 0) {
            end(false);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        frame(g, bx, by, air < 15 && (tic / 4) % 2 == 0);
        Draw.image(g, BACKGROUND, bx, by, L, H);
        for (int i = 0; i < 9; i++) {
            int x = bx + 8 + (i * 37) % 146, y = by + H - 4 - (tic * (1 + i % 3) + i * 29) % (H - 8);
            g.fill(x, y, x + 2, y + 2, 0x90DDF4FF);
        }
        int x = bx + (int) tx(), y = by + (int) ty();
        Draw.circle(g, x, y, 12, 1, onTrace ? Draw.GREEN : 0xC0FFFFFF);
        disc(g, x, y, 4, onTrace ? 0xFFB8FFD0 : 0xFF7FE0D0);
        if (!finished && Draw.in(mx, my, bx, by, L, H)) Draw.circle(g, mx, my, 5, 2, mouseDown ? Draw.YELLOW : Draw.TEXT);
        int tx = ox + 172;
        text(g, T.f("air"), tx, oy + 10, air < 35 ? Draw.ALERT : 0xFF8FC8F0);
        gauge(g, tx, oy + 21, 100, air / 100, air < 35 ? Draw.ALERT : 0xFF5BB8F0);
        text(g, T.f("vial"), tx, oy + 36, Draw.YELLOW);
        gauge(g, tx, oy + 47, 100, vial / 100, Draw.GREEN);
        Draw.paragraph(g, T.f("stay_on_the_trace_40"), tx, oy + 60, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_on_the_2");
    }
}
