package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Pipetting (strange deposit): draw up just the right amount
final class PipetteGame extends MiniGame {
    final Random h;
    double level, targetLow, targetHigh, speed;
    int round, faults, pause, tic;
    boolean suction, lastOk, inGreen;

    PipetteGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        double zoneWidth = 0.17 - round * 0.035;
        targetLow = 0.28 + h.nextDouble() * (0.62 - zoneWidth);
        targetHigh = targetLow + zoneWidth;
        level = 0;
        suction = false;
        speed = 0.024 + round * 0.009;
    }

    @Override
    void tick() {
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (!suction) return;
        level += speed;
        // Suction is audible: a note that rises with the level, and a chime on entering the green zone.
        if (++tic % 2 == 0) Draw.beep(Sounds.SCAN, (float) (0.6 + Math.min(1, level) * 1.3), 0.35f);
        if (!inGreen && level >= targetLow && level <= targetHigh) {
            inGreen = true;
            Draw.beep(Sounds.CHIME, 1.5f, 0.45f);
        }
        if (level >= 1) {
            level = 1;
            judge();
        }
    }

    void judge() {
        suction = false;
        lastOk = level >= targetLow && level <= targetHigh;
        pause = 14;
        if (lastOk) {
            Draw.note(1f + round * 0.25f);
            if (++round >= 3) {
                perfect = faults == 0;
                end(true);
            }
        } else {
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        }
    }

    @Override
    void click(double mx, double my) {
        if (pause == 0 && !suction) {
            suction = true;
            inGreen = false;
            tic = 0;
            Draw.beep(Sounds.VIAL, 1.3f, 0.5f);
        }
    }

    @Override
    void released(double mx, double my) {
        if (suction) judge();
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int tx = ox + 44, ty = oy + 9, tl = 26, th = 100;
        g.fill(tx - 2, ty - 2, tx + tl + 2, ty + th + 2, Draw.EDGE);
        g.fill(tx - 1, ty - 1, tx + tl + 1, ty + th + 1, 0xFF9FB0C0);
        g.fill(tx, ty, tx + tl, ty + th, 0xFF14202B);
        double n = Math.min(1, suction ? level + speed * pt : level);
        int top = (int) Math.round(th * n);
        int liquid = pause > 0 ? (lastOk ? Draw.GREEN : Draw.ALERT) : 0xFF9B5FC0;
        g.fill(tx, ty + th - top, tx + tl, ty + th, liquid);
        if (top > 2) g.fill(tx, ty + th - top, tx + tl, ty + th - top + 2, Draw.darken(liquid, 1.35));
        int yLow = ty + th - (int) Math.round(th * targetLow), yHigh = ty + th - (int) Math.round(th * targetHigh);
        g.fill(tx - 8, yHigh, tx - 3, yLow, Draw.GREEN);
        g.fill(tx + tl + 3, yHigh, tx + tl + 8, yLow, Draw.GREEN);
        g.fill(tx, yHigh, tx + tl, yHigh + 1, 0xC06CCB5F);
        g.fill(tx, yLow - 1, tx + tl, yLow, 0xC06CCB5F);
        for (int i = 1; i < 10; i++) g.fill(tx, ty + i * 10, tx + (i % 5 == 0 ? 8 : 4), ty + i * 10 + 1, 0xFF5D6E80);
        int px = ox + 110;
        Draw.write(g, T.f("dose") + Math.min(3, round + 1) + " / 3", px, oy + 14, Draw.TEXT, false);
        Draw.write(g, T.f("faults") + faults + " / 3", px, oy + 26, faults > 1 ? Draw.ALERT : Draw.GREY, false);
        Draw.write(g, pause > 0 ? (lastOk ? T.f("right_dose") : level >= 1 ? T.f("overflow") : T.f("wrong_dose")) : suction ? T.f("release_in_the_green") : T.f("hold_the_click"),
                px, oy + 46, pause > 0 ? (lastOk ? Draw.GREEN : Draw.ALERT) : Draw.YELLOW, false);
        Draw.paragraph(g, T.f("the_green_zone_shrinks_and"), px, oy + 66, 160, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_to_draw");
    }
}
