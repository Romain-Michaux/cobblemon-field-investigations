package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Arbre de parenté : descendre l'arbre, en prenant à chaque embranchement le caractère que porte l'échantillon
final class FamilyTreeGame extends MiniGame {
    // Trois caractères, deux formes possibles pour chacun : la peau, le membre, la défense.
    static final ResourceLocation[][] ICONS = {
            {Draw.texture("buried_egg_1"), Draw.texture("buried_stat_2")},
            {Draw.texture("trait_wing"), Draw.texture("trait_fin")},
            {Draw.texture("buried_stat_1"), Draw.texture("buried_stat")}};
    static final String[][] NAMES = {{T.f("feathers"), T.f("scales")}, {T.f("wings"), T.f("fins")}, {T.f("claws"), T.f("fangs")}};
    static final int[] YS = {10, 38, 66, 96};
    final Random h;
    // ordre[n] : le caractère testé au niveau n ; gauche[n] : la forme placée sur la branche de gauche.
    final int[] order = new int[3], left = new int[3], values = new int[3];
    // Position dans l'arbre : niveau atteint (0 = racine, 3 = arrivé) et numéro du nœud à ce niveau.
    int level2, node, round, faults, time, duration, flash, pause;

    FamilyTreeGame(Random h) {
        this.h = h;
        newB();
    }

    void newB() {
        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        System.arraycopy(permutations[h.nextInt(6)], 0, order, 0, 3);
        for (int l = 0; l < 3; l++) {
            left[l] = h.nextInt(2);
            values[l] = h.nextInt(2);
        }
        level2 = 0;
        node = 0;
        duration = time = 240 - round * 50;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (pause > 0) {
            if (--pause == 0 && !finished) newB();
            return;
        }
        if (--time <= 0) {
            fault();
            if (!finished) pause = 24;
        } else {
            press1(time, duration);
        }
    }

    void fault() {
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 2) end(false);
    }

    double x(int l, int n) {
        return ox + 4 + L * (n + 0.5) / (1 << l);
    }

    // La forme affichée sur la branche de ce côté (0 = gauche, 1 = droite), au niveau donné.
    int shape(int l, int side) {
        return side == 0 ? left[l] : 1 - left[l];
    }

    @Override
    void click(double mx, double my) {
        if (pause > 0 || level2 >= 3) return;
        for (int side = 0; side < 2; side++) {
            int cx = (int) x(level2 + 1, node * 2 + side), cy = oy + 3 + YS[level2 + 1];
            if (!Draw.in(mx, my, cx - 13, cy - 13, 26, 26)) continue;
            if (shape(level2, side) == values[order[level2]]) {
                node = node * 2 + side;
                level2++;
                Draw.note(0.9f + level2 * 0.2f);
                if (level2 >= 3) {
                    Draw.beep(Sounds.CHIME, 1.5f, 0.6f);
                    if (++round >= 3) {
                        perfect = faults == 0;
                        end(true);
                    } else {
                        pause = 30;
                    }
                }
            } else {
                fault(); // ce caractère n'est pas celui de l'échantillon
            }
            return;
        }
    }

    static void thumbnail(GuiGraphics g, int cx, int cy, ResourceLocation icon, int edge) {
        g.fill(cx - 11, cy - 11, cx + 11, cy + 11, edge);
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, 0xFF2B3A4C);
        Draw.image(g, icon, cx - 8, cy - 8, 16, 16);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF16202B);
        for (int l = 0; l < 3; l++) {
            for (int n = 0; n < (1 << l); n++) {
                for (int side = 0; side < 2; side++) {
                    Draw.segment(g, x(l, n), ay + YS[l], x(l + 1, n * 2 + side), ay + YS[l + 1], 1, 0xFF34475C);
                }
            }
        }
        for (int f = 0; f < 8; f++) g.fill((int) x(3, f) - 3, ay + YS[3] - 3, (int) x(3, f) + 3, ay + YS[3] + 3, 0xFF34475C);
        for (int l = 0; l < level2; l++) {
            int n0 = node >> (level2 - l), n1 = node >> (level2 - l - 1);
            Draw.segment(g, x(l, n0), ay + YS[l], x(l + 1, n1), ay + YS[l + 1], 3, Draw.GREEN);
        }
        for (int l = 0; l < level2; l++) {
            int n1 = node >> (level2 - l - 1);
            thumbnail(g, (int) x(l + 1, n1), ay + YS[l + 1], ICONS[order[l]][values[order[l]]], Draw.GREEN);
        }
        g.fill((int) x(0, 0) - 4, ay + YS[0] - 4, (int) x(0, 0) + 4, ay + YS[0] + 4, level2 == 0 ? Draw.YELLOW : Draw.GREEN);
        if (level2 < 3 && !finished) {
            for (int side = 0; side < 2; side++) {
                int cx = (int) x(level2 + 1, node * 2 + side), cy = ay + YS[level2 + 1];
                boolean hover = pause == 0 && Draw.in(mx, my, cx - 13, cy - 13, 26, 26);
                Draw.segment(g, x(level2, node), ay + YS[level2], cx, cy, 2, Draw.YELLOW);
                thumbnail(g, cx, cy, ICONS[order[level2]][shape(level2, side)], hover ? Draw.TEXT : Draw.YELLOW);
            }
            // Le nom des deux caractères proposés, sous l'aire de jeu si la place manque sur les branches.
            if (level2 < 2) {
                for (int side = 0; side < 2; side++) {
                    int cx = (int) x(level2 + 1, node * 2 + side), cy = ay + YS[level2 + 1];
                    String name = NAMES[order[level2]][shape(level2, side)];
                    int l = Draw.textFont().width(name), tx = Math.max(ax + 2, Math.min(ax + L - l - 2, cx - l / 2));
                    g.fill(tx - 1, cy + 12, tx + l + 1, cy + 22, 0xC016202B);
                    text(g, name, tx, cy + 13, Draw.TEXT);
                }
            }
        }
        // À droite : la fiche de l'échantillon, avec ses trois caractères ; celui qu'on cherche est entouré.
        int tx = ox + 172;
        text(g, T.f("lineage") + Math.min(3, round + 1) + " / 3", tx, oy + 5, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 2", tx, oy + 16, faults > 0 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("the_sample_has"), tx, oy + 30, Draw.YELLOW);
        for (int c = 0; c < 3; c++) {
            int ly = oy + 42 + c * 20;
            boolean search = level2 < 3 && order[level2] == c, done = false;
            for (int l = 0; l < level2; l++) done |= order[l] == c;
            thumbnail(g, tx + 10, ly + 9, ICONS[c][values[c]], search ? Draw.YELLOW : done ? Draw.GREEN : Draw.EDGE);
            text(g, NAMES[c][values[c]], tx + 26, ly + 5, search ? Draw.YELLOW : done ? Draw.GREEN : Draw.GREY);
        }
        timeGauge(g, tx, oy + 104, 100, pause > 0 ? 0 : time / (double) duration);
    }

    @Override
    String instruction() {
        return T.f("go_down_the_tree_at");
    }
}
