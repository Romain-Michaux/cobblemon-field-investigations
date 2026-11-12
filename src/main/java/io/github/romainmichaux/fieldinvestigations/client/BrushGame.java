package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Brushing (buried clue): uncover the fragments hidden under the soil by brushing gently.
final class BrushGame extends MiniGame {
    static final ResourceLocation EARTH = Draw.texture("earth");
    // The soil is 40 x 28 cells of 4 px. Each fragment (the clue's icon, scaled up twice) covers 8 x 8 cells.
    static final int COLS = 40, ROWS = 28, CELL = 4, DURATION = 700, FRAGMENTS = 3, SIZE = 8, RADIUS = 9;
    static final double MAX_SPEED = 17;
    final ResourceLocation clue;
    // Soil left on each cell: 1 = untouched, 0 = cleared. It wears away gradually under the brush.
    final float[] soil = new float[COLS * ROWS];
    final int[] fc = new int[FRAGMENTS], fr = new int[FRAGMENTS];
    final boolean[] found = new boolean[FRAGMENTS];
    int time = DURATION, faults, flash, cooldown, foundCount, tic;
    double prevX = -1, prevY = -1, cx, cy;

    BrushGame(Random h, String kind, int variant) {
        clue = Draw.buriedIcon(kind, variant);
        java.util.Arrays.fill(soil, 1f);
        // Fragments are buried anywhere, without overlapping.
        for (int i = 0; i < FRAGMENTS; i++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                fc[i] = h.nextInt(COLS - SIZE + 1);
                fr[i] = h.nextInt(ROWS - SIZE + 1);
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

    int clearedPercent(int i) {
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
        if (cooldown > 0) cooldown--;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        int bx = ox + 4, by = oy + 3;
        boolean overSoil = Draw.in(cx, cy, bx - RADIUS, by - RADIUS, 160 + 2 * RADIUS, 112 + 2 * RADIUS);
        if (mouseDown && overSoil && prevX >= 0) {
            double speed = Math.hypot(cx - prevX, cy - prevY);
            if (cooldown == 0 && speed > MAX_SPEED) {
                // A stroke that is too rough damages what lies buried.
                flash = 8;
                cooldown = 12;
                Draw.note(0.5f);
                if (++faults >= 3) end(false);
            } else if (cooldown == 0) {
                // The brush removes soil gradually, more at its centre than at its edge, so the player has to linger.
                for (int i = 0; i < soil.length; i++) {
                    if (soil[i] <= 0) continue;
                    double d = Math.hypot(cx - (bx + (i % COLS) * CELL + 2), cy - (by + (i / COLS) * CELL + 2));
                    if (d < RADIUS) soil[i] -= (float) (0.3 * (1 - d / RADIUS) + 0.03);
                }
                if (++tic % 5 == 0) Draw.beep(Sounds.BRUSH_SOUND, (float) (0.9 + Math.min(0.5, speed / 30)), 0.7f);
                for (int i = 0; i < FRAGMENTS; i++) {
                    if (found[i] || clearedPercent(i) < 80) continue;
                    found[i] = true;
                    Draw.note(1f + foundCount * 0.25f);
                    if (++foundCount >= FRAGMENTS) {
                        perfect = faults == 0;
                        end(true);
                    }
                }
            }
        }
        prevX = cx;
        prevY = cy;
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
            int c = i % COLS, r = i / COLS, x = bx + c * CELL, y = by + r * CELL, f = fragment(c, r);
            if (e <= 0) {
                g.fill(x, y, x + CELL, y + CELL, 0xFF2B2018);
                // The part of the fragment under this cell (2 x 2 pixels of the icon, scaled up twice).
                if (f >= 0) Draw.pieceEnlarged(g, clue, x, y, CELL, CELL, (c - fc[f]) * 2, (r - fr[f]) * 2, 2, 2, 16, 16);
            } else {
                // Partly brushed soil darkens; above a fragment, a pale patch hints at it.
                g.fill(x, y, x + CELL, y + CELL, ((int) ((1 - e) * 150) << 24) | 0x1E1610);
                if (f >= 0 && e < 0.75f) g.fill(x, y, x + CELL, y + CELL, ((int) ((0.75f - e) * 110) << 24) | 0xE6D8B4);
            }
        }
        for (int i = 0; i < FRAGMENTS; i++) {
            if (!found[i]) continue;
            int x = bx + fc[i] * CELL, y = by + fr[i] * CELL, l = SIZE * CELL;
            g.fill(x - 1, y - 1, x + l + 1, y, Draw.GREEN);
            g.fill(x - 1, y + l, x + l + 1, y + l + 1, Draw.GREEN);
            g.fill(x - 1, y, x, y + l, Draw.GREEN);
            g.fill(x + l, y, x + l + 1, y + l, Draw.GREEN);
        }
        if (!finished && Draw.in(mx, my, bx, by, 160, 112)) Draw.circle(g, mx - 1, my - 1, RADIUS, 2, mouseDown ? Draw.YELLOW : Draw.TEXT);
        int tx = ox + 172;
        Draw.write(g, T.f("fragments") + foundCount + " / " + FRAGMENTS, tx, oy + 12, foundCount > 0 ? Draw.GREEN : Draw.TEXT, false);
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
