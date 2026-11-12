package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Abseiling (altitude investigation): pay out the rope and stop level with each trace
final class AbseilGame extends MiniGame {
    static final ResourceLocation ROCK_FACE = Draw.texture("rock_face");
    static final int DURATION = 540;
    final int[] targets = new int[3];
    double pos = 6, speed, sectionStart = 6;
    int step, time = DURATION, faults, flash, tic;
    boolean waitRelease;

    AbseilGame(Random h) {
        for (int i = 0; i < 3; i++) targets[i] = 30 + i * 30 + h.nextInt(9);
    }

    void fault() {
        pos = sectionStart;
        speed = 0;
        waitRelease = true;
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 3) end(false);
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
        if (!mouseDown) waitRelease = false;
        boolean lowering = mouseDown && !waitRelease;
        speed = lowering ? Math.min(2.4, speed + 0.05) : Math.max(0, speed - 0.1);
        pos += speed;
        if (speed > 0 && tic % 4 == 0) Draw.beep(Sounds.ROPE, (float) (0.7 + speed * 0.4), 0.6f);
        int target = targets[step];
        if (pos > target + 4) {
            fault(); // went too low: climb back up and redo this section
        } else if (speed == 0 && !lowering && Math.abs(pos - target) <= 4) {
            sectionStart = pos;
            Draw.beep(Sounds.CARABINER, 1.2f, 0.8f);
            Draw.note(1f + step * 0.25f);
            waitRelease = true;
            if (++step >= 3) {
                step = 2;
                perfect = faults == 0;
                end(true);
            }
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        Draw.image(g, ROCK_FACE, bx, by, L, H);
        for (int i = 0; i < 3; i++) {
            int y = by + targets[i] + 6;
            boolean done = i < step || (finished && success);
            g.fill(bx + 92, y - 4, bx + 150, y + 4, done ? 0x6040C060 : i == step ? 0x60F2C93A : 0x30FFFFFF);
            g.fill(bx + 92, y, bx + 150, y + 1, done ? Draw.GREEN : i == step ? Draw.YELLOW : Draw.GREY);
            text(g, done ? "✔" : String.valueOf(i + 1), bx + 152 - 8, y - 4, done ? Draw.GREEN : Draw.TEXT);
        }
        int gx = bx + 80, gy = by + (int) pos + 6;
        g.fill(gx, by, gx + 1, gy - 6, 0xFFE9D8A8);
        g.fill(gx - 3, gy - 6, gx + 4, gy + 5, 0xFFE8743A);
        g.fill(gx - 2, gy - 10, gx + 3, gy - 6, 0xFFF2D2B0);
        g.fill(gx - 3, gy + 5, gx - 1, gy + 9, 0xFF3A4A5C);
        g.fill(gx + 2, gy + 5, gx + 4, gy + 9, 0xFF3A4A5C);
        int tx = ox + 172;
        text(g, T.f("traces") + (finished && success ? 3 : step) + " / 3", tx, oy + 10, Draw.TEXT);
        text(g, T.f("too_low") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("speed"), tx, oy + 40, speed > 1.6 ? Draw.ALERT : Draw.YELLOW);
        gauge(g, tx, oy + 51, 100, speed / 2.4, speed > 1.6 ? Draw.ALERT : Draw.YELLOW);
        Draw.paragraph(g, T.f("the_rope_takes_a_while"), tx, oy + 64, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_to_let");
    }
}
