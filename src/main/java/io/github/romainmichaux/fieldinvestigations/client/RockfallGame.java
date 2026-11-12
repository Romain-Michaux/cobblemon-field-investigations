package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Éboulis (enquête souterraine) : dégager la pierre qui cache l'indice, en retirant d'abord celles qui pèsent dessus
final class RockfallGame extends MiniGame {
    static final ResourceLocation ROCK_FACE = Draw.texture("rock_face");
    // Cinq rangées en quinconce, comme un mur de pierres sèches : six pierres sur les rangées paires, cinq sur les impaires.
    static final int RANKS = 5, PL = 24, PH = 17, DURATION = 460;
    final boolean[][] shown = new boolean[RANKS][6];
    // Coups de pioche restant à donner : une pierre ordinaire en demande un, une pierre massive trois.
    final int[][] strokes = new int[RANKS][6];
    final int target;
    int time = DURATION, faults, flash, shake, removed1;

    RockfallGame(Random h) {
        for (int r = 0; r < RANKS; r++) {
            for (int k = 0; k < count1(r); k++) {
                shown[r][k] = true;
                strokes[r][k] = r > 0 && h.nextInt(5) == 0 ? 3 : 1;
            }
        }
        target = 1 + h.nextInt(4);
    }

    static int count1(int rank) {
        return rank % 2 == 0 ? 6 : 5;
    }

    // Coin haut-gauche de la pierre (rang 0 = en bas), dans l'aire de jeu.
    int px(int rank, int k) {
        return ox + 4 + 8 + k * PL + (rank % 2 == 0 ? 0 : PL / 2);
    }

    int py(int rank) {
        return oy + 3 + 104 - (rank + 1) * PH;
    }

    // Une pierre est libre si aucune de celles qui reposent sur elle n'est encore en place.
    boolean free(int rank, int k) {
        if (rank == RANKS - 1) return true;
        int a = rank % 2 == 0 ? k - 1 : k, b = a + 1;
        return !(a >= 0 && a < count1(rank + 1) && shown[rank + 1][a]) && !(b >= 0 && b < count1(rank + 1) && shown[rank + 1][b]);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (shake > 0) shake--;
        if (--time <= 0) end(false);
        else press1(time, DURATION);
    }

    @Override
    void click(double mx, double my) {
        for (int r = RANKS - 1; r >= 0; r--) {
            for (int k = 0; k < count1(r); k++) {
                if (!shown[r][k] || !Draw.in(mx, my, px(r, k), py(r), PL, PH)) continue;
                if (!free(r, k)) {
                    // Tirer une pierre chargée fait tout trembler.
                    flash = 8;
                    shake = 10;
                    Draw.beep(Sounds.ROCKSLIDE, 0.6f, 1f);
                    if (++faults >= 3) end(false);
                } else if (--strokes[r][k] > 0) {
                    Draw.beep(Sounds.PICKAXE, 0.8f + (3 - strokes[r][k]) * 0.2f, 0.9f);
                } else {
                    shown[r][k] = false;
                    removed1++;
                    Draw.beep(Sounds.STONE, 0.9f + r * 0.08f, 0.8f);
                    if (r == 0 && k == target) {
                        perfect = faults == 0;
                        Draw.note(1.5f);
                        end(true);
                    }
                }
                return;
            }
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        g.setColor(0.55f, 0.55f, 0.6f, 1f);
        Draw.image(g, ROCK_FACE, bx, by, L, H);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(bx, by + 104, bx + L, by + H, 0xFF2A2420);
        int lx = px(0, target), ly = py(0);
        boolean pulse = (System.currentTimeMillis() / 350) % 2 == 0;
        g.fill(lx + 2, ly + 2, lx + PL - 2, ly + PH - 1, pulse ? 0xFF7FE0D0 : 0xFF4FB8A8);
        g.fill(lx + 8, ly + 5, lx + PL - 8, ly + PH - 5, 0xFFE0FFF8);
        for (int r = 0; r < RANKS; r++) {
            for (int k = 0; k < count1(r); k++) {
                if (!shown[r][k]) continue;
                int dx = shake > 0 ? ((shake + r + k) % 2) * 2 - 1 : 0, x = px(r, k) + dx, y = py(r);
                boolean free = free(r, k), massive = strokes[r][k] > 1, aim = r == 0 && k == target;
                boolean hover = !finished && free && Draw.in(mx, my, px(r, k), y, PL, PH);
                int body = massive ? 0xFF5A5660 : 0xFF8C8884;
                g.fill(x, y + 1, x + PL, y + PH, hover ? Draw.YELLOW : aim ? 0xFF4FD1C5 : 0xFF1C1A1E);
                g.fill(x + 1, y + 2, x + PL - 1, y + PH - 1, body);
                g.fill(x + 2, y + 2, x + PL - 2, y + 4, Draw.darken(body, 1.3));
                g.fill(x + 1, y + PH - 4, x + PL - 1, y + PH - 1, Draw.darken(body, 0.75));
                g.fill(x + 1, y + 2, x + 2, y + 3, 0xFF1C1A1E);
                g.fill(x + PL - 2, y + 2, x + PL - 1, y + 3, 0xFF1C1A1E);
                // Une pierre massive porte autant d'entailles qu'il lui reste de coups à recevoir.
                for (int c = 0; massive && c < strokes[r][k]; c++) g.fill(x + 6 + c * 5, y + 7, x + 8 + c * 5, y + 12, 0xFF1C1A1E);
                if (!free) g.fill(x + 1, y + 2, x + PL - 1, y + PH - 1, 0x50000000);
            }
        }
        int tx = ox + 172;
        text(g, T.f("stones_removed") + removed1, tx, oy + 10, Draw.TEXT);
        text(g, T.f("rockslides") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("darkened_stones_still_carry_weight"), tx, oy + 40, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("free_the_glowing_stone_at");
    }
}
