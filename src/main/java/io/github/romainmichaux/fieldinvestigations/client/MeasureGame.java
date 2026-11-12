package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Mesure de l'empreinte : poser les quatre mâchoires du pied à coulisse
final class MeasureGame extends MiniGame {
    static final ResourceLocation FOOTPRINT = Draw.texture("footprint");
    static final int EL = 74, EH = 70;
    static final String[] EDGES = {T.f("left_edge"), T.f("right_edge"), T.f("top_edge"), T.f("bottom_edge")};
    final int ex, ey;
    final double[] placements = new double[4];
    double pos, direction = 1;
    int step, faults, flash;
    boolean flashOk;

    MeasureGame(Random h) {
        ex = 22 + h.nextInt(28);
        ey = 14 + h.nextInt(20);
        pos = h.nextInt(100);
    }

    double speed() {
        return 2.2 + step * 0.35;
    }

    int length() {
        return step < 2 ? 128 : 106;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        pos += direction * speed();
        if (pos >= length()) {
            pos = length();
            direction = -1;
            Draw.beep(Sounds.TICK, 1.1f, 0.45f);
        } else if (pos <= 0) {
            pos = 0;
            direction = 1;
            Draw.beep(Sounds.TICK, 0.9f, 0.45f);
        }
    }

    double target() {
        return switch (step) {
            case 0 -> ex;
            case 1 -> ex + EL;
            case 2 -> ey;
            default -> ey + EH;
        };
    }

    @Override
    void click(double mx, double my) {
        // Position de la règle dans le repère de la zone (8 px de marge à gauche, 6 en haut).
        double here = pos + (step < 2 ? 8 : 6);
        flashOk = Math.abs(here - target()) <= 4.5;
        flash = 8;
        placements[step] = here;
        if (flashOk) Draw.note(1f + step * 0.2f);
        else {
            Draw.note(0.5f);
            faults++;
        }
        step++;
        pos = step == 2 ? 0 : pos;
        if (faults >= 2) end(false);
        else if (step >= 4) {
            perfect = faults == 0;
            end(true);
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        g.fill(ox + 6, oy + 4, ox + 140, oy + 114, Draw.EDGE);
        g.fill(ox + 7, oy + 5, ox + 139, oy + 113, 0xFF4A3828);
        g.fill(ox + 7, oy + 5, ox + 139, oy + 6, 0xFF5E4834);
        Draw.image(g, FOOTPRINT, ox + ex, oy + ey, EL, EH);
        for (int i = 0; i < Math.min(step, 4); i++) rule(g, i, placements[i], Draw.YELLOW);
        if (!finished) {
            double p = Math.max(0, Math.min(length(), pos + direction * speed() * pt)) + (step < 2 ? 8 : 6);
            rule(g, step, p, flash > 0 ? (flashOk ? Draw.GREEN : Draw.ALERT) : Draw.TEXT);
        }
        int px = ox + 150;
        Draw.write(g, T.f("jaw") + Math.min(4, step + 1) + " / 4", px, oy + 12, Draw.TEXT, false);
        if (step < 4) Draw.write(g, EDGES[step], px, oy + 24, Draw.YELLOW, false);
        Draw.write(g, T.f("faults") + faults + " / 2", px, oy + 40, faults > 0 ? Draw.ALERT : Draw.GREY, false);
        if (step >= 2) Draw.write(g, T.f("width") + Math.round((placements[1] - placements[0]) / 2) + T.f("cm"), px, oy + 58, Draw.ACCENT, false);
        if (step >= 4) Draw.write(g, T.f("length") + Math.round((placements[3] - placements[2]) / 2) + T.f("cm"), px, oy + 70, Draw.ACCENT, false);
        Draw.paragraph(g, T.f("click_when_the_ruler_touches"), px, oy + 88, 124, Draw.DARK);
    }

    void rule(GuiGraphics g, int i, double p, int colour) {
        int q = (int) Math.round(p);
        if (i < 2) g.fill(ox + q, oy + 6, ox + q + 1, oy + 112, colour);
        else g.fill(ox + 8, oy + q, ox + 138, oy + q + 1, colour);
    }

    @Override
    String instruction() {
        return T.f("click_when_the_ruler_touches_2");
    }
}
