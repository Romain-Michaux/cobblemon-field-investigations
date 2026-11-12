package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Colorimétrie : mémoriser une teinte, puis la recréer en mélangeant les réactifs
final class ColorimetryGame extends MiniGame {
    static final ResourceLocation VIAL = Draw.texture("erlenmeyer"), LIQUID = Draw.texture("erlenmeyer_liquid");
    static final String[] NAMES = {T.f("red"), T.f("yellow"), T.f("blue"), T.f("white")};
    static final int[] REAGENTS = {0xFFE8433A, 0xFFF2C93A, 0xFF3A78D8, 0xFFF4F4F0};
    static final int EMPTY = 0xFFB9C6D2, MAX = 10, THRESHOLD = 85, SHOWN = 90;
    final Random h;
    final int[] drops = new int[4];
    int target, round, failures, visible, pause, fall, last = -1, score, total;

    ColorimetryGame(Random h) {
        this.h = h;
        newB();
    }

    // La teinte cible est toujours un mélange réalisable : 4 à 7 gouttes de deux ou trois réactifs.
    void newB() {
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
        last = -1;
    }

    static int mix(int[] amounts) {
        int n = 0, r = 0, v = 0, b = 0;
        for (int i = 0; i < 4; i++) {
            n += amounts[i];
            r += ((REAGENTS[i] >> 16) & 0xFF) * amounts[i];
            v += ((REAGENTS[i] >> 8) & 0xFF) * amounts[i];
            b += (REAGENTS[i] & 0xFF) * amounts[i];
        }
        return n == 0 ? EMPTY : 0xFF000000 | ((r / n) << 16) | ((v / n) << 8) | (b / n);
    }

    // Ressemblance en pourcentage : 100 = teinte identique. Réglé pour qu'une teinte visiblement proche passe le seuil :
    // une goutte d'écart coûte 5 à 15 points selon les réactifs, deux gouttes font en général échouer.
    static int likeness(int a, int b) {
        double dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dv = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.round(Math.max(0, 1 - Math.sqrt(dr * dr + dv * dv + db * db) / 330) * 100);
    }

    int count1() {
        return drops[0] + drops[1] + drops[2] + drops[3];
    }

    @Override
    void tick() {
        if (fall > 0) fall--;
        if (visible > 0 && --visible == 0) hiddenSound();
        if (pause > 0 && --pause == 0 && !finished) newB();
    }

    int bx(int i) {
        return ox + 152 + (i % 2) * 62;
    }

    int by(int i) {
        return oy + 34 + (i / 2) * 24;
    }

    @Override
    void click(double mx, double my) {
        if (visible > 0 || pause > 0) return;
        for (int i = 0; i < 4; i++) {
            if (!Draw.in(mx, my, bx(i), by(i), 58, 20) || count1() >= MAX) continue;
            drops[i]++;
            last = i;
            fall = 4;
            Draw.sound1(0.9f + i * 0.12f);
            return;
        }
        if (Draw.in(mx, my, ox + 152, oy + 88, 58, 22) && count1() > 0) {
            java.util.Arrays.fill(drops, 0);
            Draw.sound1(0.7f);
        } else if (Draw.in(mx, my, ox + 214, oy + 88, 58, 22) && count1() > 0) {
            score = likeness(mix(drops), target);
            total += score;
            pause = 44;
            if (score >= THRESHOLD) {
                Draw.note(1f + round * 0.25f);
            } else {
                Draw.note(0.5f);
                failures++;
            }
            round++;
            if (failures >= 2) end(false);
            else if (round >= 3) {
                perfect = failures == 0;
                end(true);
            }
        }
    }

    void dot1(GuiGraphics g, int x, int y, int l, int ht, int colour) {
        g.fill(x, y, x + l, y + ht, Draw.EDGE);
        g.fill(x + 1, y + 1, x + l - 1, y + ht - 1, colour);
        g.fill(x + 1, y + 1, x + l - 1, y + 2, Draw.darken(colour, 1.2));
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        // La fiole : elle prend la teinte du mélange en cours (ou la teinte à mémoriser, tant qu'elle est montrée).
        int fx = ox + 14, fy = oy + 34;
        int current = visible > 0 ? target : mix(drops);
        g.fill(fx + 22, oy + 10, fx + 26, oy + 26, 0xFF9FB0C0);
        g.fill(fx + 23, oy + 26, fx + 25, oy + 30, 0xFF6B7C8C);
        if (fall > 0 && last >= 0) g.fill(fx + 23, oy + 30 + (4 - fall) * 3, fx + 25, oy + 34 + (4 - fall) * 3, REAGENTS[last]);
        // Seul le liquide prend la teinte ; le verre de la fiole garde ses couleurs.
        Draw.image(g, VIAL, fx, fy, 48, 64);
        Draw.tint(g, current);
        Draw.image(g, LIQUID, fx, fy, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);

        // Colonne du milieu : la teinte à mémoriser, puis cachée, puis la comparaison chiffrée.
        int cx = ox + 74;
        if (visible > 0) {
            Draw.write(g, T.f("memorise"), cx, oy + 8, Draw.YELLOW, false);
            dot1(g, cx, oy + 20, 64, 52, target);
            timeGauge(g, cx, oy + 78, 64, visible / (double) SHOWN);
        } else if (pause > 0 || finished) {
            boolean ok = score >= THRESHOLD;
            Draw.write(g, T.f("target"), cx, oy + 8, Draw.GREY, false);
            dot1(g, cx, oy + 19, 64, 22, target);
            Draw.write(g, T.f("your_mix"), cx, oy + 46, Draw.GREY, false);
            dot1(g, cx, oy + 57, 64, 22, mix(drops));
            Draw.write(g, score + " % " + (ok ? T.f("passed") : T.f("failed")), cx, oy + 86, ok ? Draw.GREEN : Draw.ALERT, false);
            Draw.write(g, T.f("needed") + THRESHOLD + " %", cx, oy + 97, Draw.DARK, false);
        } else {
            Draw.write(g, T.f("target"), cx, oy + 8, Draw.GREY, false);
            dot1(g, cx, oy + 19, 64, 22, 0xFF1A2633);
            // Aide aux couleurs : la ressemblance du mélange en cours s'affiche à la place du point d'interrogation.
            boolean help = FieldInvestigationsClient.colourAid && count1() > 0;
            Draw.centre(g, help ? likeness(mix(drops), target) + " %" : "?", cx + 32, oy + 26, help ? Draw.TEXT : Draw.DARK);
            Draw.write(g, T.f("your_mix"), cx, oy + 46, Draw.GREY, false);
            dot1(g, cx, oy + 57, 64, 22, mix(drops));
            int k = 0;
            for (int i = 0; i < 4; i++) {
                for (int n = 0; n < drops[i]; n++, k++) g.fill(cx + k * 6, oy + 86, cx + k * 6 + 5, oy + 93, REAGENTS[i]);
            }
            Draw.write(g, count1() + " / " + MAX + T.f("drops"), cx, oy + 97, Draw.DARK, false);
        }

        int tx = ox + 152;
        Draw.write(g, T.f("flask") + Math.min(3, round + (pause > 0 || finished ? 0 : 1)) + " / 3", tx, oy + 8, Draw.TEXT, false);
        Draw.write(g, T.f("fails") + failures + " / 2", tx, oy + 20, failures > 0 ? Draw.ALERT : Draw.GREY, false);
        boolean free = visible == 0 && pause == 0 && !finished;
        for (int i = 0; i < 4; i++) {
            boolean active = free && count1() < MAX;
            Draw.button(g, bx(i), by(i), 58, 20, NAMES[i], active && Draw.in(mx, my, bx(i), by(i), 58, 20), active, REAGENTS[i]);
        }
        boolean filled = free && count1() > 0;
        Draw.button(g, tx, oy + 88, 58, 22, T.f("empty"), filled && Draw.in(mx, my, tx, oy + 88, 58, 22), filled, 0xFF7C8FA3);
        Draw.button(g, tx + 62, oy + 88, 58, 22, T.f("confirm"), filled && Draw.in(mx, my, tx + 62, oy + 88, 58, 22), filled, Draw.GREEN);
    }

    @Override
    String instruction() {
        return T.f("memorise_the_colour_then_recreate");
    }
}
