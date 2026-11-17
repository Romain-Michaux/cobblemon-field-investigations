package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Colorimetry: memorise the sample's tint, then recreate it drop by drop from four reagents. Colours mix like paint (red,
// yellow, blue; white lightens), so the player can reason about them. The sample can be uncovered again by holding the
// click on it, at the cost of a small reserve of time. Drops cannot be taken back: only emptying the flask undoes them.
final class ColorimetryGame extends MiniGame {
    static final ResourceLocation VIAL = Draw.texture("erlenmeyer"), LIQUID = Draw.texture("erlenmeyer_liquid");
    static final ResourceLocation[] BOTTLES = {Draw.texture("colour_red"), Draw.texture("colour_yellow"), Draw.texture("colour_blue"),
            Draw.texture("colour_white")};
    static final int[] REAGENTS = {0xFFE8433A, 0xFFF2C93A, 0xFF3A78D8, 0xFFF4F4F0};
    static final int[] KEYS = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4};
    static final int EMPTY = 0xFFB9C6D2, MAX = 10, THRESHOLD = 88, SHOWN = 90, PEEK = 40, FLIGHT = 9, BLEND = 14, RESULT = 54;
    // The red-yellow-blue cube (painters' primaries) and the colour at each corner, as in Gossett and Chen's paint model.
    static final double[][] CORNERS = {{1, 1, 1}, {1, 0, 0}, {1, 1, 0}, {1, 0.5, 0}, {0.163, 0.373, 0.6}, {0.5, 0, 0.5}, {0, 0.66, 0.2},
            {0.2, 0.094, 0}};
    final Random h;
    final int[] drops = new int[4];
    // 0 = keep going, 1 = failed, 2 = succeeded: set by the last verdict, applied once it has been shown.
    int ending;
    int target, round, failures, visible, pause, score, peek, flying = -1, flight, blend, shownColour, fromColour, tic;

    ColorimetryGame(Random h) {
        this.h = h;
        newRound();
    }

    // The target is always reachable: 4 to 7 drops of two or three reagents.
    void newRound() {
        shownSound();
        int[] recipe = new int[4];
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order, h);
        int kinds = 2 + h.nextInt(2), n = 4 + h.nextInt(4);
        for (int i = 0; i < kinds; i++) recipe[order.get(i)] = 1;
        for (int i = kinds; i < n; i++) recipe[order.get(h.nextInt(kinds))]++;
        target = mix(recipe);
        java.util.Arrays.fill(drops, 0);
        visible = SHOWN;
        peek = PEEK;
        flying = -1;
        shownColour = fromColour = EMPTY;
        blend = 0;
    }

    // Paint mixing: the red, yellow and blue drops set a point in the cube (scaled so the strongest is full), the cube gives
    // its colour, and white drops lighten it.
    static int mix(int[] amounts) {
        int total = amounts[0] + amounts[1] + amounts[2] + amounts[3];
        if (total == 0) return EMPTY;
        double r = amounts[0], y = amounts[1], b = amounts[2], m = Math.max(r, Math.max(y, b));
        double[] rgb = {1, 1, 1};
        if (m > 0) {
            r /= m;
            y /= m;
            b /= m;
            rgb = new double[3];
            for (int c = 0; c < 8; c++) {
                double w = ((c & 1) != 0 ? r : 1 - r) * ((c & 2) != 0 ? y : 1 - y) * ((c & 4) != 0 ? b : 1 - b);
                for (int k = 0; k < 3; k++) rgb[k] += w * CORNERS[c][k];
            }
        }
        double white = amounts[3] / (double) total * 0.85;
        int[] out = new int[3];
        for (int k = 0; k < 3; k++) out[k] = (int) Math.round(Math.min(1, rgb[k] * (1 - white) + 0.96 * white) * 255);
        return 0xFF000000 | (out[0] << 16) | (out[1] << 8) | out[2];
    }

    // Likeness in percent, 100 = identical.
    static int likeness(int a, int b) {
        double dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.round(Math.max(0, 1 - Math.sqrt(dr * dr + dg * dg + db * db) / 330) * 100);
    }

    int dropCount() {
        return drops[0] + drops[1] + drops[2] + drops[3];
    }

    boolean idle() {
        return visible == 0 && pause == 0 && !finished && ending == 0;
    }

    int bottleX(int i) {
        return ox + 146 + i * 32;
    }

    boolean onSample(double mx, double my) {
        return Draw.in(mx, my, ox + 10, oy + 18, 44, 70);
    }

    double cx, cy;

    @Override
    void tick() {
        tic++;
        if (visible > 0 && --visible == 0) hiddenSound();
        if (blend > 0) blend--;
        if (flying >= 0 && ++flight >= FLIGHT) {
            // The drop lands: the flask's colour swirls over to the new mix.
            fromColour = currentShown();
            drops[flying]++;
            shownColour = mix(drops);
            blend = BLEND;
            flying = -1;
            Draw.beep(Sounds.DROP, 0.9f + dropCount() * 0.05f, 0.6f);
        }
        if (idle() && mouseDown && onSample(cx, cy) && peek > 0) peek--;
        if (pause > 0 && --pause == 0 && !finished) {
            if (ending == 0) {
                newRound();
            } else {
                perfect = ending == 2 && failures == 0;
                end(ending == 2);
            }
        }
        if (pause == RESULT - 22) Draw.note(score >= THRESHOLD ? 0.9f + (score - THRESHOLD) * 0.05f : 0.5f);
    }

    int currentShown() {
        return blend > 0 ? MiniGame.mix(shownColour, fromColour, blend / (double) BLEND) : shownColour;
    }

    void pour(int i) {
        if (!idle() || flying >= 0 || dropCount() >= MAX) return;
        flying = i;
        flight = 0;
        Draw.click(0.9f + i * 0.12f);
    }

    void empty() {
        if (!idle() || dropCount() == 0 || flying >= 0) return;
        java.util.Arrays.fill(drops, 0);
        fromColour = currentShown();
        shownColour = EMPTY;
        blend = BLEND;
        Draw.beep(Sounds.VIAL, 0.7f, 0.7f);
    }

    void confirm() {
        if (!idle() || dropCount() == 0 || flying >= 0) return;
        score = likeness(mix(drops), target);
        pause = RESULT;
        if (score < THRESHOLD) failures++;
        round++;
        // The last verdict is shown in full before the game ends.
        if (failures >= 2) ending = 1;
        else if (round >= 3) ending = 2;
    }

    @Override
    void click(double mx, double my) {
        for (int i = 0; i < 4; i++) {
            if (Draw.in(mx, my, bottleX(i), oy + 40, 30, 50)) {
                pour(i);
                return;
            }
        }
        if (Draw.in(mx, my, ox + 146, oy + 103, 60, 14)) empty();
        else if (Draw.in(mx, my, ox + 210, oy + 103, 62, 14)) confirm();
    }

    @Override
    boolean keyPressed(int key) {
        for (int i = 0; i < 4; i++) {
            if (key == KEYS[i]) {
                pour(i);
                return true;
            }
        }
        if (key == InputConstants.KEY_BACKSPACE) empty();
        else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) confirm();
        else return false;
        return true;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        cx = mx;
        cy = my;
        int ax = ox, ay = oy;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF18202A);
        // The bench.
        g.fill(ax, ay + 92, ax + AREA_W, ay + AREA_H, 0xFF3A2E24);
        g.fill(ax, ay + 92, ax + AREA_W, ay + 94, 0xFF5A4636);

        // The sample: a test tube on a stand, shown while memorising, then under a cover that lifts while the click is held.
        boolean peeking = idle() && mouseDown && onSample(mx, my) && peek > 0;
        boolean open = visible > 0 || peeking || pause > 0 || finished;
        int sx = ax + 22, sy = ay + 18;
        g.fill(sx - 1, sy - 1, sx + 21, sy + 66, Draw.EDGE);
        g.fill(sx, sy + 10, sx + 20, sy + 58, target);
        g.fill(sx + 2, sy + 58, sx + 18, sy + 62, target);
        g.fill(sx, sy, sx + 20, sy + 10, 0x40D8E8F0);
        g.fill(sx + 3, sy + 12, sx + 5, sy + 56, 0x50FFFFFF);
        g.fill(sx - 3, sy - 4, sx + 23, sy, 0xFF8A98A6);
        if (!open) {
            // The cover, with what is left of the peek reserve.
            g.fill(sx - 4, sy - 5, sx + 24, sy + 66, 0xFF26323F);
            g.fill(sx - 4, sy - 5, sx + 24, sy - 4, 0xFF41566B);
            boolean help = FieldInvestigationsClient.colourAid && dropCount() > 0;
            Draw.centre(g, help ? likeness(mix(drops), target) + "%" : "?", sx + 10, sy + 26, help ? Draw.TEXT : Draw.GREY);
        }
        g.fill(ax + 10, ay + 86, ax + 54, ay + 88, Draw.EDGE);
        if (visible > 0) {
            g.fill(ax + 10, ay + 86, ax + 10 + 44 * visible / SHOWN, ay + 88, Draw.YELLOW);
            Draw.centre(g, T.f("memorise"), sx + 10, ay + 6, Draw.YELLOW);
        } else {
            g.fill(ax + 10, ay + 86, ax + 10 + 44 * peek / PEEK, ay + 88, peek > 0 ? Draw.ACCENT : Draw.DARK);
            if (idle()) Draw.centre(g, T.f("sample"), sx + 10, ay + 6, Draw.GREY);
        }

        // The flask, its liquid swirling to the new colour, with the drops poured so far underneath.
        int fx = ax + 78, fy = ay + 28, colour = currentShown();
        Draw.image(g, VIAL, fx, fy, 48, 64);
        Draw.tint(g, colour);
        Draw.image(g, LIQUID, fx, fy, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);
        if (blend > 0) {
            // Swirl: a few strokes of the new colour over the old one, and a ripple on the surface.
            int age = BLEND - blend;
            for (int k = 0; k < 4; k++) {
                double a = tic * 0.5 + k * Math.PI / 2;
                int px = fx + 24 + (int) Math.round(Math.cos(a) * (6 + age * 0.6)), py = fy + 48 + (int) Math.round(Math.sin(a) * 4);
                g.fill(px - 2, py, px + 3, py + 2, shownColour);
            }
            g.fill(fx + 24 - 2 - age, fy + 37, fx + 24 + 2 + age, fy + 38, 0x80FFFFFF);
        }
        for (int i = 0, k = 0; i < 4; i++) {
            for (int n = 0; n < drops[i]; n++, k++) {
                int dx = fx + 4 + k * 4;
                g.fill(dx, ay + 98, dx + 3, ay + 102, Draw.EDGE);
                g.fill(dx, ay + 99, dx + 3, ay + 101, REAGENTS[i]);
            }
        }
        text(g, dropCount() + " / " + MAX, fx + 6, ay + 105, Draw.GREY);

        // The four reagent bottles; a drop in flight arcs from the pipette to the flask.
        for (int i = 0; i < 4; i++) {
            int bx = bottleX(i);
            boolean hover = idle() && flying < 0 && Draw.in(mx, my, bx, ay + 40, 30, 50);
            if (hover) g.fill(bx, ay + 42, bx + 30, ay + 92, 0x30FFFFFF);
            Draw.image(g, BOTTLES[i], bx - 1, ay + (hover ? 40 : 43), 32, 48);
            Draw.centre(g, Draw.keyLabel(KEYS[i]), bx + 15, ay + 94, hover ? Draw.YELLOW : Draw.GREY);
        }
        if (flying >= 0) {
            double t = (flight + pt) / FLIGHT;
            double x0 = bottleX(flying) + 15, y0 = ay + 42, x1 = fx + 24, y1 = fy + 30;
            int dx = (int) Math.round(x0 + (x1 - x0) * t), dy = (int) Math.round(y0 + (y1 - y0) * t - Math.sin(t * Math.PI) * 18);
            g.fill(dx - 1, dy - 2, dx + 2, dy + 2, REAGENTS[flying]);
            g.fill(dx, dy - 3, dx + 1, dy - 2, REAGENTS[flying]);
        }
        boolean act = idle() && dropCount() > 0 && flying < 0;
        Draw.button(g, ax + 146, ay + 103, 60, 14, T.f("empty"), act && Draw.in(mx, my, ax + 146, ay + 103, 60, 14), act, 0xFF7C8FA3);
        Draw.button(g, ax + 210, ay + 103, 62, 14, T.f("confirm"), act && Draw.in(mx, my, ax + 210, ay + 103, 62, 14), act, Draw.GREEN);

        String left = T.f("flask") + Math.min(3, round + (pause > 0 || finished ? 0 : 1)) + " / 3", right = T.f("fails") + failures + " / 2";
        Draw.centre(g, left, ax + 102, ay + 4, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 4, failures > 0 ? Draw.ALERT : Draw.GREY);

        // The verdict: both colours side by side, and the likeness filling up to the score.
        if (pause > 0 || finished) {
            int px = ax + 64, py = ay + 18, pw = 152;
            g.fill(px, py, px + pw, py + 60, 0xF0101418);
            g.fill(px, py, px + pw, py + 1, Draw.EDGE_LIGHT);
            text(g, T.f("target"), px + 8, py + 5, Draw.GREY);
            text(g, T.f("your_mix"), px + 80, py + 5, Draw.GREY);
            g.fill(px + 8, py + 15, px + 72, py + 33, target);
            g.fill(px + 80, py + 15, px + 144, py + 33, mix(drops));
            int shown = finished ? score : (int) Math.round(score * Math.min(1, (RESULT - pause) / 22.0));
            boolean ok = shown >= THRESHOLD;
            g.fill(px + 8, py + 39, px + 144, py + 45, Draw.EDGE);
            g.fill(px + 9, py + 40, px + 9 + 134 * shown / 100, py + 44, ok ? Draw.GREEN : Draw.ALERT);
            g.fill(px + 9 + 134 * THRESHOLD / 100, py + 37, px + 10 + 134 * THRESHOLD / 100, py + 47, Draw.YELLOW);
            Draw.centre(g, shown + " %" + (RESULT - pause >= 22 || finished ? " " + (score >= THRESHOLD ? T.f("passed") : T.f("failed")) : ""),
                    px + pw / 2, py + 49, ok ? Draw.GREEN : Draw.ALERT);
        }
    }

    @Override
    String instruction() {
        return T.f("every_creature_leaves_its_hue");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("add_a_drop"), InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4, Draw.OR, Draw.MOUSE),
                new Control(T.f("look_again_hold"), Draw.MOUSE)};
    }
}
