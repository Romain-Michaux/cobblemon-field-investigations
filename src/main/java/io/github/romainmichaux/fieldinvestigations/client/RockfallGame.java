package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Rockfall (underground investigation): a rockfall blocks a mine gallery, and the clue is caught at its foot. Its glow
// seeps through the gaps, stronger as the stones around it go. Stones sit in staggered rows, each on two below (or one
// and the gallery wall). A stone can be pulled only if every stone resting on it keeps its other support; pulled, it
// tumbles away. A stone left on a single support wobbles, then gives way and drops into the hole below it, which may
// block a way just cleared. Massive stones are split by holding the pickaxe on them. The freed clue comes out into the
// lamplight.
final class RockfallGame extends MiniGame {
    static final ResourceLocation CAVE = Draw.texture("rock_cave"), MASSIVE = Draw.texture("rock_massive"), SCALE = Draw.texture("rock_scale");
    static final ResourceLocation[] STONES = {Draw.texture("rock_stone0"), Draw.texture("rock_stone1"), Draw.texture("rock_stone2")};
    static final int ROWS = 5, PL = 26, PH = 19, X0 = 56, FLOOR = 112, DURATION = 640, MAX_FAULTS = 3, WOBBLE = 110, SPLIT = 40, END = 80;
    final Random h;
    final boolean[][] present = new boolean[ROWS][6], massive = new boolean[ROWS][6];
    final int[][] variant = new int[ROWS][6], wobble = new int[ROWS][6], drop = new int[ROWS][6];
    final double[][] crack = new double[ROWS][6];
    final int target;
    final List<double[]> debris = new ArrayList<>(); // x, y, vx, vy, angle, spin, sprite (-1 massive half), scale
    final List<double[]> dust = new ArrayList<>(); // x, y, vx, vy, age
    int time = DURATION, faults, shake, tic, ending = -1;
    boolean held;
    double mx, my;

    RockfallGame(Random h) {
        this.h = h;
        for (int r = 0; r < ROWS; r++) {
            for (int k = 0; k < stonesInRow(r); k++) {
                present[r][k] = true;
                massive[r][k] = r > 0 && h.nextInt(5) == 0;
                variant[r][k] = h.nextInt(STONES.length);
                wobble[r][k] = -1;
            }
        }
        target = 1 + h.nextInt(4);
    }

    static int stonesInRow(int row) {
        return row % 2 == 0 ? 6 : 5;
    }

    boolean in(int r, int k) {
        return r >= 0 && r < ROWS && k >= 0 && k < stonesInRow(r);
    }

    // Top-left corner of a slot, relative to the play area.
    static int sx(int r, int k) {
        return X0 + k * PL + (r % 2 == 0 ? 0 : PL / 2);
    }

    static int sy(int r) {
        return FLOOR - (r + 1) * PH;
    }

    // The two slots a stone rests on (row below); off the row means the gallery wall.
    int[] supports(int r, int k) {
        return r % 2 == 0 ? new int[] {k - 1, k} : new int[] {k, k + 1};
    }

    // The two slots resting on a stone (row above).
    int[] above(int r, int k) {
        return r % 2 == 0 ? new int[] {k - 1, k} : new int[] {k, k + 1};
    }

    int supportCount(int r, int k) {
        if (r == 0) return 2;
        int n = 0;
        for (int j : supports(r, k)) if (!in(r - 1, j) || present[r - 1][j]) n++;
        return n;
    }

    // Whether pulling a stone leaves every stone on it with its other support.
    boolean pullable(int r, int k) {
        if (r == ROWS - 1) return true;
        for (int j : above(r, k)) {
            if (!in(r + 1, j) || !present[r + 1][j]) continue;
            for (int s : supports(r + 1, j)) if (s != k && in(r, s) && !present[r][s]) return false;
        }
        return true;
    }

    @Override
    void tick() {
        tic++;
        if (shake > 0) shake--;
        for (double[] d : debris) {
            d[3] += 0.35;
            d[0] += d[2];
            d[1] += d[3];
            d[4] += d[5];
        }
        debris.removeIf(d -> d[1] > AREA_H + 30);
        for (double[] d : dust) {
            d[0] += d[2];
            d[1] += d[3];
            d[3] -= 0.01;
            d[4]++;
        }
        dust.removeIf(d -> d[4] > 30);
        for (int r = 0; r < ROWS; r++) for (int k = 0; k < 6; k++) if (drop[r][k] > 0) drop[r][k]--;
        if (ending >= 0) {
            if (ending % 6 == 0 && ending < 50) puff(140, 60, 3, 0xFFE8DCC0);
            if (++ending >= END) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (finished) return;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        // Wobbling stones give way in the end.
        for (int r = 1; r < ROWS; r++) {
            for (int k = 0; k < stonesInRow(r); k++) {
                if (!present[r][k] || wobble[r][k] < 0) continue;
                if (tic % 12 == 0) puff(sx(r, k) + PL / 2, sy(r) + PH, 1, 0xFF8A7A68);
                if (++wobble[r][k] >= WOBBLE) giveWay(r, k);
            }
        }
        // Holding the pickaxe on a massive stone splits it.
        int[] at = slotAt(mx, my);
        if (held && at != null && massive[at[0]][at[1]] && pullable(at[0], at[1])) {
            int r = at[0], k = at[1];
            crack[r][k] += 1.0 / SPLIT;
            if (tic % 7 == 0) Draw.beep(Sounds.PICKAXE, 0.8f + (float) crack[r][k] * 0.6f, 0.8f);
            if (tic % 7 == 0) puff(sx(r, k) + PL / 2, sy(r) + PH / 2, 2, 0xFFA8A0A0);
            if (crack[r][k] >= 1) {
                remove(r, k);
            }
        }
    }

    void puff(double x, double y, int n, int colour) {
        for (int i = 0; i < n; i++) dust.add(new double[] {x + h.nextGaussian() * 4, y + h.nextGaussian() * 2, h.nextGaussian() * 0.4, -0.2 - h.nextDouble() * 0.3, 0, colour});
    }

    // The stone goes: it tumbles away (a massive one in two halves), and the ones on it are left with one support.
    void remove(int r, int k) {
        present[r][k] = false;
        wobble[r][k] = -1;
        double x = sx(r, k), y = sy(r), dir = x + PL / 2.0 < 140 ? -1 : 1;
        if (massive[r][k]) {
            debris.add(new double[] {x, y, dir * 1.2 - 0.6, -2.2, 0, -6, -1, 0.55});
            debris.add(new double[] {x + PL / 2.0, y, dir * 1.2 + 0.6, -2.0, 0, 6, -1, 0.55});
            Draw.beep(Sounds.ROCKSLIDE, 1.3f, 0.6f);
        } else {
            debris.add(new double[] {x, y, dir * (1.4 + h.nextDouble()), -2.4, 0, dir * 7, variant[r][k], 1});
            Draw.beep(Sounds.STONE, 0.9f + r * 0.08f, 0.8f);
        }
        puff(x + PL / 2.0, y + PH, 4, 0xFF9A8A78);
        if (r == 0 && k == target) {
            ending = 0;
            Draw.note(1.5f);
            return;
        }
        settle();
    }

    // A wobbling stone drops into the hole below it.
    void giveWay(int r, int k) {
        for (int j : supports(r, k)) {
            if (in(r - 1, j) && !present[r - 1][j]) {
                move(r, k, r - 1, j);
                Draw.beep(Sounds.ROCKSLIDE, 0.9f, 0.7f);
                shake = 6;
                settle();
                return;
            }
        }
        wobble[r][k] = -1;
    }

    void move(int r, int k, int r2, int k2) {
        present[r][k] = false;
        present[r2][k2] = true;
        massive[r2][k2] = massive[r][k];
        variant[r2][k2] = variant[r][k];
        crack[r2][k2] = crack[r][k];
        crack[r][k] = 0;
        wobble[r][k] = -1;
        wobble[r2][k2] = -1;
        drop[r2][k2] = 6;
        puff(sx(r2, k2) + PL / 2.0, sy(r2) + PH, 5, 0xFF9A8A78);
    }

    // Stones with no support left fall at once; those with only one start to wobble.
    void settle() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int r = 1; r < ROWS; r++) {
                for (int k = 0; k < stonesInRow(r); k++) {
                    if (!present[r][k]) continue;
                    int n = supportCount(r, k);
                    if (n == 0) {
                        int[] s = supports(r, k);
                        move(r, k, r - 1, in(r - 1, s[0]) && !present[r - 1][s[0]] ? s[0] : s[1]);
                        changed = true;
                    } else if (n == 1 && wobble[r][k] < 0) {
                        wobble[r][k] = 0;
                    } else if (n == 2) {
                        wobble[r][k] = -1;
                    }
                }
            }
        }
    }

    int[] slotAt(double px, double py) {
        for (int r = ROWS - 1; r >= 0; r--) {
            for (int k = 0; k < stonesInRow(r); k++) {
                if (present[r][k] && Draw.in(px, py, ox + sx(r, k), oy + sy(r), PL, PH)) return new int[] {r, k};
            }
        }
        return null;
    }

    @Override
    void click(double px, double py) {
        held = true;
        if (finished || ending >= 0) return;
        int[] at = slotAt(px, py);
        if (at == null) return;
        int r = at[0], k = at[1];
        if (!pullable(r, k)) {
            // Pulling a stone that holds another one up brings the pile down on you.
            shake = 12;
            Draw.beep(Sounds.ROCKSLIDE, 0.6f, 1f);
            for (int i = 0; i < 3; i++) debris.add(new double[] {X0 + h.nextInt(160), -10, h.nextGaussian() * 0.5, 1, 0, h.nextGaussian() * 8, h.nextInt(STONES.length), 0.5});
            puff(sx(r, k) + PL / 2.0, sy(r), 6, 0xFF9A8A78);
            if (++faults >= MAX_FAULTS) end(false);
        } else if (!massive[r][k]) {
            remove(r, k);
        }
    }

    @Override
    void released(double px, double py) {
        held = false;
    }

    // How much of the clue's glow gets through: more as the stones near it go.
    double glow() {
        double open = 0;
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < stonesInRow(r); k++) {
                double d = Math.abs(sx(r, k) - sx(0, target)) / (double) PL + r;
                if (!present[r][k] && d < 3) open += 1 / (1 + d);
            }
        }
        return Math.min(1, 0.25 + open * 0.35);
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox + (shake > 0 ? (shake % 2) * 2 - 1 : 0), ay = oy;
        Draw.image(g, CAVE, ox, oy, AREA_W, AREA_H);
        // The clue's glow, behind the stones: it only shows through the gaps.
        int cx = ax + sx(0, target) + PL / 2, cy = ay + sy(0) + PH / 2;
        double gl = ending >= 0 ? 1 : glow() * (0.85 + 0.15 * Math.sin(tic * 0.15));
        for (int i = 6; i >= 1; i--) {
            int rad = i * 7;
            g.fill(cx - rad, cy - rad * 2 / 3, cx + rad, cy + rad * 2 / 3, ((int) (gl * 48) << 24) | 0x4FE0D0);
        }
        int[] hover = finished || ending >= 0 ? null : slotAt(px, py);
        for (int r = 0; r < ROWS; r++) {
            for (int k = 0; k < stonesInRow(r); k++) {
                if (!present[r][k]) continue;
                stone(g, ax, ay, r, k, hover != null && hover[0] == r && hover[1] == k, pt);
            }
        }
        // Tumbling stones and dust.
        for (double[] d : debris) {
            g.pose().pushPose();
            g.pose().translate(ox + d[0] + PL / 2.0, oy + d[1] + PH / 2.0, 50);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) d[4]));
            g.pose().scale((float) d[7], (float) d[7], 1);
            Draw.image(g, d[6] < 0 ? MASSIVE : STONES[(int) d[6]], -PL / 2, -PH / 2, PL, PH);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        for (double[] d : dust) {
            int a = (int) (180 * (1 - d[4] / 30));
            int x = ox + (int) d[0], y = oy + (int) d[1];
            g.fill(x, y, x + 2, y + 2, (a << 24) | ((int) d[5] & 0xFFFFFF));
        }
        if (ending >= 0) drawClue(g);
        // Counters.
        String right = T.f("rockslides") + faults + " / " + MAX_FAULTS;
        int rw = Draw.textFont().width(right);
        g.fill(ox + AREA_W - 10 - rw, oy + 2, ox + AREA_W - 2, oy + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, ox + AREA_W - 6 - rw, oy + 3, faults > 0 ? Draw.ALERT : Draw.GREY, false);
        g.fill(ox + 2, oy + 2, ox + 66, oy + 12, 0xC0101418);
        timeGauge(g, ox + 5, oy + 4, 58, time / (double) DURATION);
        g.pose().popPose();
    }

    // One stone: its sprite, rocking if it wobbles, dropping into place if it just fell, cracked if being split; lit
    // yellow under the pointer when it can be pulled, red when it holds another one up.
    void stone(GuiGraphics g, int ax, int ay, int r, int k, boolean hover, float pt) {
        int x = ax + sx(r, k), y = ay + sy(r) - drop[r][k] * 3;
        g.pose().pushPose();
        g.pose().translate(x + PL / 2.0, y + PH, 0);
        if (wobble[r][k] >= 0) {
            double w = wobble[r][k] / (double) WOBBLE;
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) (Math.sin((tic + pt) * (0.3 + w * 0.5)) * (2 + w * 6))));
        }
        boolean ok = pullable(r, k);
        if (hover) g.setColor(ok ? 1f : 1f, ok ? 1f : 0.55f, ok ? 0.55f : 0.5f, 1f);
        // Near the clue, the stones catch its light as they are cleared.
        else if (r == 0 && k == target && glow() > 0.6) {
            float l = (float) ((glow() - 0.6) / 0.4);
            g.setColor(1f - 0.25f * l, 1f, 1f - 0.05f * l, 1f);
        }
        Draw.image(g, massive[r][k] ? MASSIVE : STONES[variant[r][k]], -PL / 2 - 1, -PH - 1, PL + 2, PH + 2);
        g.setColor(1f, 1f, 1f, 1f);
        // The split spreading across a massive stone.
        double c = crack[r][k];
        if (c > 0) {
            int n = (int) (c * 8);
            int cxp = 0, cyp = -PH;
            for (int i = 0; i < n; i++) {
                int nx = (i % 2 == 0 ? 2 : -2) + (i == 3 ? 3 : 0), ny = cyp + 2;
                g.fill(Math.min(cxp, nx), cyp, Math.max(cxp, nx) + 1, ny, 0xFFD8E0F0);
                g.fill(Math.min(cxp, nx) + 1, cyp + 1, Math.max(cxp, nx) + 2, ny + 1, 0xFF08080C);
                cxp = nx;
                cyp = ny;
            }
        }
        if (massive[r][k] && hover && ok && c == 0) {
            g.fill(-6, -PH / 2 - 1, 6, -PH / 2, 0x80FFFFFF);
        }
        g.pose().popPose();
    }

    // The clue, freed: it rises from its slot into the lamplight, grows and shines.
    void drawClue(GuiGraphics g) {
        double t = Math.min(1, ending / 30.0), e = t * t * (3 - 2 * t);
        double x0 = ox + sx(0, target) + PL / 2.0, y0 = oy + sy(0) + PH / 2.0, x1 = ox + 140, y1 = oy + 52;
        double x = x0 + (x1 - x0) * e, y = y0 + (y1 - y0) * e, s = 1 + e * 1.4;
        for (int i = 4; i >= 1; i--) {
            int rad = (int) (i * 6 * s);
            g.fill((int) x - rad, (int) y - rad, (int) x + rad, (int) y + rad, (int) (e * 26) << 24 | 0x7FF0E0);
        }
        g.pose().pushPose();
        g.pose().translate(x, y + Math.sin(ending * 0.2) * e, 0);
        g.pose().scale((float) s, (float) s, 1);
        Draw.image(g, SCALE, -10, -11, 20, 22);
        g.pose().popPose();
        if (ending > 30) {
            for (int i = 0; i < 4; i++) {
                double a = ending * 0.08 + i * Math.PI / 2;
                int sx = (int) (x + Math.cos(a) * 24), sy = (int) (y + Math.sin(a) * 18);
                int on = (ending / 3 + i) % 4 == 0 ? 0xFFFFFFFF : 0xC0BFF8F0;
                g.fill(sx - 1, sy, sx + 2, sy + 1, on);
                g.fill(sx, sy - 1, sx + 1, sy + 2, on);
            }
            String s2 = T.f("clue_freed");
            int w = Draw.textFont().width(s2);
            g.fill((int) x - w / 2 - 4, oy + 86, (int) x + w / 2 + 4, oy + 96, 0xC0101418);
            g.drawString(Draw.textFont(), s2, (int) x - w / 2, oy + 87, 0xFF7FF0E0, false);
        }
    }

    @Override
    String instruction() {
        return T.f("the_clue_glows_at_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("click_pull_a_stone"), Draw.MOUSE), new Control(T.f("hold_split_a_big_one"), Draw.MOUSE)};
    }
}
