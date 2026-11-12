package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Thawing (frost investigation): heat the frozen sample while staying in the right temperature range
final class ThawGame extends MiniGame {
    static final ResourceLocation ICE_CUBE = Draw.texture("ice_cube");
    static final int DURATION = 580;
    final double p;
    double temperature = 20, heat, thaw;
    int time = DURATION, faults, flash, tic, milestones;

    ThawGame(Random h) {
        p = h.nextDouble() * 6;
    }

    double bottom() {
        return 44 + 13 * Math.sin(tic * 0.022 + p);
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
            heat = Math.min(30, heat + 1);
            temperature += 0.5 + heat * 0.05;
            if (tic % 9 == 0) Draw.beep(Sounds.FLAME, 1.4f, 0.5f);
        } else {
            heat = 0;
            temperature = Math.max(0, temperature - 0.8);
        }
        if (temperature >= 90) {
            temperature = 35;
            heat = 0;
            flash = 8;
            Draw.beep(Sounds.WHISTLE, 1.2f, 0.6f);
            if (++faults >= 3) end(false);
        }
        if (temperature >= bottom() && temperature <= bottom() + 18) thaw += 0.55;
        int milestone = (int) (thaw / 34);
        if (milestone > milestones) {
            milestones = milestone;
            Draw.beep(Sounds.ICE, 1.2f, 0.4f);
        }
        if (thaw >= 100 && !finished) {
            perfect = faults == 0;
            end(true);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
        int size = (int) (64 - thaw * 0.34), gx = bx + 52 - size / 2, gy = by + 82 - size;
        g.fill(bx + 52 - 20 - (int) (thaw * 0.14), by + 82, bx + 52 + 20 + (int) (thaw * 0.14), by + 85, 0xFF5BB8F0);
        Draw.pieceEnlarged(g, ICE_CUBE, gx, gy, size, size, 0, 0, 48, 48, 48, 48);
        if (mouseDown && !finished) {
            for (int i = 0; i < 5; i++) {
                int fx = bx + 34 + i * 9, fh = 4 + (tic * 3 + i * 5) % 7;
                g.fill(fx, by + 100 - fh, fx + 5, by + 100, i % 2 == 0 ? 0xFFF2C93A : 0xFFE8543A);
            }
        }
        g.fill(bx + 26, by + 100, bx + 80, by + 104, 0xFF46525E);
        // The thermometer: the green range drifts slowly, the red zone cooks the sample.
        int tx0 = bx + 118, top = by + 6, h = 100;
        g.fill(tx0 - 1, top - 1, tx0 + 15, top + h + 1, Draw.EDGE);
        g.fill(tx0, top, tx0 + 14, top + h, 0xFF0F1820);
        g.fill(tx0, top, tx0 + 14, top + 10, 0xFF6B2420);
        int yb = top + h - (int) bottom(), yh = yb - 18;
        g.fill(tx0, yh, tx0 + 14, yb, 0xFF245C3A);
        int yt = top + h - (int) temperature;
        g.fill(tx0 + 4, yt, tx0 + 10, top + h, temperature >= 80 ? Draw.ALERT : 0xFFE9A15B);
        g.fill(tx0 - 3, yt - 1, tx0 + 17, yt + 1, Draw.TEXT);
        int tx = ox + 172;
        text(g, T.f("thawed") + (int) thaw + " %", tx, oy + 10, Draw.TEXT);
        text(g, T.f("overheats") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("the_ice_only_melts_in"), tx, oy + 42, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_to_heat");
    }
}
