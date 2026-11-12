package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Dégagement au pinceau (indice enfoui) : trouver les fragments cachés sous la terre, en brossant doucement
final class BrushGame extends MiniGame {
    static final ResourceLocation EARTH = Draw.texture("earth");
    // La terre fait 40 x 28 cases de 4 px. Chaque fragment (le dessin de l'indice, agrandi deux fois) en couvre 8 x 8.
    static final int COLS = 40, RANKS = 28, CASE = 4, DURATION = 700, FRAGMENTS = 3, SIZE = 8, RADIUS = 9;
    static final double MAX_SPEED = 17;
    final ResourceLocation clue;
    // Épaisseur de terre restante sur chaque case : 1 = intacte, 0 = dégagée. Elle part peu à peu sous le pinceau.
    final float[] soil = new float[COLS * RANKS];
    final int[] fc = new int[FRAGMENTS], fr = new int[FRAGMENTS];
    final boolean[] found1 = new boolean[FRAGMENTS];
    int time = DURATION, faults, flash, rest, foundOnes, tic;
    double sx = -1, sy = -1, cx, cy;

    BrushGame(Random h, String kind, int variant) {
        clue = Draw.buriedIcon(kind, variant);
        java.util.Arrays.fill(soil, 1f);
        // Les fragments sont enfouis n'importe où, sans se chevaucher.
        for (int i = 0; i < FRAGMENTS; i++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                fc[i] = h.nextInt(COLS - SIZE + 1);
                fr[i] = h.nextInt(RANKS - SIZE + 1);
                boolean free = true;
                for (int k = 0; k < i; k++) free &= Math.abs(fc[k] - fc[i]) > SIZE || Math.abs(fr[k] - fr[i]) > SIZE;
                if (free) break;
            }
        }
    }

    int fragment(int c, int r) {
        for (int i = 0; i < FRAGMENTS; i++) {
            if (c >= fc[i] && c < fc[i] + SIZE && r >= fr[i] && r < fr[i] + SIZE) return i;
        }
        return -1;
    }

    int part(int i) {
        int n = 0;
        for (int r = fr[i]; r < fr[i] + SIZE; r++) {
            for (int c = fc[i]; c < fc[i] + SIZE; c++) {
                if (soil[r * COLS + c] <= 0) n++;
            }
        }
        return n * 100 / (SIZE * SIZE);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (rest > 0) rest--;
        if (--time <= 0) {
            end(false);
            return;
        }
        press1(time, DURATION);
        int bx = ox + 4, by = oy + 3;
        boolean onLand = Draw.in(cx, cy, bx - RADIUS, by - RADIUS, 160 + 2 * RADIUS, 112 + 2 * RADIUS);
        if (press && onLand && sx >= 0) {
            double speed = Math.hypot(cx - sx, cy - sy);
            if (rest == 0 && speed > MAX_SPEED) {
                // Un geste trop brusque abîme ce qui est enfoui.
                flash = 8;
                rest = 12;
                Draw.note(0.5f);
                if (++faults >= 3) end(false);
            } else if (rest == 0) {
                // Le pinceau enlève la terre peu à peu : plus au centre que sur les bords, et il faut s'attarder.
                for (int i = 0; i < soil.length; i++) {
                    if (soil[i] <= 0) continue;
                    double d = Math.hypot(cx - (bx + (i % COLS) * CASE + 2), cy - (by + (i / COLS) * CASE + 2));
                    if (d < RADIUS) soil[i] -= (float) (0.3 * (1 - d / RADIUS) + 0.03);
                }
                if (++tic % 5 == 0) Draw.beep(Sounds.BRUSH_SOUND, (float) (0.9 + Math.min(0.5, speed / 30)), 0.7f);
                for (int i = 0; i < FRAGMENTS; i++) {
                    if (found1[i] || part(i) < 80) continue;
                    found1[i] = true;
                    Draw.note(1f + foundOnes * 0.25f);
                    if (++foundOnes >= FRAGMENTS) {
                        perfect = faults == 0;
                        end(true);
                    }
                }
            }
        }
        sx = cx;
        sy = cy;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Draw.ALERT : Draw.EDGE);
        Draw.image(g, EARTH, bx, by, 160, 112);
        for (int i = 0; i < soil.length; i++) {
            float e = soil[i];
            if (e >= 1f) continue;
            int c = i % COLS, r = i / COLS, x = bx + c * CASE, y = by + r * CASE, f = fragment(c, r);
            if (e <= 0) {
                g.fill(x, y, x + CASE, y + CASE, 0xFF2B2018);
                // Le morceau du fragment sous cette case (2 x 2 pixels du dessin, agrandis deux fois).
                if (f >= 0) Draw.pieceEnlarged(g, clue, x, y, CASE, CASE, (c - fc[f]) * 2, (r - fr[f]) * 2, 2, 2, 16, 16);
            } else {
                // Terre entamée : elle fonce ; au-dessus d'un fragment, une pâleur le laisse deviner.
                g.fill(x, y, x + CASE, y + CASE, ((int) ((1 - e) * 150) << 24) | 0x1E1610);
                if (f >= 0 && e < 0.75f) g.fill(x, y, x + CASE, y + CASE, ((int) ((0.75f - e) * 110) << 24) | 0xE6D8B4);
            }
        }
        for (int i = 0; i < FRAGMENTS; i++) {
            if (!found1[i]) continue;
            int x = bx + fc[i] * CASE, y = by + fr[i] * CASE, l = SIZE * CASE;
            g.fill(x - 1, y - 1, x + l + 1, y, Draw.GREEN);
            g.fill(x - 1, y + l, x + l + 1, y + l + 1, Draw.GREEN);
            g.fill(x - 1, y, x, y + l, Draw.GREEN);
            g.fill(x + l, y, x + l + 1, y + l, Draw.GREEN);
        }
        if (!finished && Draw.in(mx, my, bx, by, 160, 112)) Draw.circle(g, mx - 1, my - 1, RADIUS, 2, press ? Draw.YELLOW : Draw.TEXT);
        int tx = ox + 172;
        Draw.write(g, T.f("fragments") + foundOnes + " / " + FRAGMENTS, tx, oy + 12, foundOnes > 0 ? Draw.GREEN : Draw.TEXT, false);
        Draw.write(g, T.f("snags") + faults + " / 3", tx, oy + 24, faults > 1 ? Draw.ALERT : Draw.GREY, false);
        Draw.write(g, flash > 0 ? T.f("too_rough") : T.f("brush_gently"), tx, oy + 44, flash > 0 ? Draw.ALERT : Draw.YELLOW, false);
        Draw.paragraph(g, T.f("go_slowly_a_pale_patch"), tx, oy + 60, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("brush_the_soil_hold_the");
    }
}
