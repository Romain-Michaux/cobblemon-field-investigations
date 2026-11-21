package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Thaw (frost investigation): a feather is caught in a block of ice standing on the snow. A heat gun follows the mouse
// and warms the ice around where it points. Only the faces in the open melt, so the block recedes from its edges, cell
// by cell, dripping into a growing puddle. The warmth lingers and spreads: lingering above the feather cooks it, so the
// gun has to keep sweeping. Now and then a cold gust, heralded by flakes blowing in from one side, refreezes the edge
// on that side, except where the ice is still warm. Once the whole feather is out, it is lifted from the water,
// dripping, and laid on a cloth.
final class ThawGame extends MiniGame {
    static final ResourceLocation SCENE = Draw.texture("thaw_scene"), ICE = Draw.texture("thaw_ice"), FEATHER = Draw.texture("thaw_feather"),
            GUN = Draw.texture("thaw_lamp"), CLOTH = Draw.texture("thaw_cloth");
    static final int COLS = 24, ROWS = 18, CELL = 4, BX = 44, BY = 26, DURATION = 1000, END = 80, REACH = 15;
    static final int FW = 33, FH = 40, FX = BX + COLS * CELL / 2 - FW / 2, FY = BY + ROWS * CELL / 2 - FH / 2;
    static final int CLOTH_X = 214, CLOTH_Y = 90;
    static final double MELT_FROM = 0.08, COOK_FROM = 0.9, WARM = 0.35;
    final double[] ice = new double[COLS * ROWS], temp = new double[COLS * ROWS], frost = new double[COLS * ROWS];
    final boolean[] clue = new boolean[COLS * ROWS];
    final List<double[]> drips = new ArrayList<>(), flakes = new ArrayList<>(); // x, y, vx, vy
    final Random rng;
    int time = DURATION, tic, clueCells, freed, melted, ending = -1, nextGust, warning, gust, gustSide, messageTicks, messageColour;
    double mx = -100, my = -100, cooked;
    boolean aiming;
    String message = "";

    ThawGame(Random h) {
        rng = h;
        java.util.Arrays.fill(ice, 1);
        // The feather's cells: those its drawing covers (it sits on the grid, 9 cells by 10).
        for (int r = 0; r < SHAPE.length; r++) {
            for (int c = 0; c < SHAPE[r].length(); c++) {
                if (SHAPE[r].charAt(c) != '#') continue;
                clue[(r + (FY - BY) / CELL) * COLS + c + (FX - BX) / CELL] = true;
                clueCells++;
            }
        }
        nextGust = 260 + h.nextInt(120);
        gustSide = h.nextBoolean() ? -1 : 1;
    }

    static final String[] SHAPE = {"......###", ".....####", "...#####.", "...#####.", "..#####..", ".#####...", "#####....", "####.....",
            "###......", "#........"};

    boolean exposed(int i) {
        int c = i % COLS, r = i / COLS;
        if (c == 0 || c == COLS - 1 || r == 0) return true;
        return ice[i - 1] <= 0 || ice[i + 1] <= 0 || ice[i - COLS] <= 0 || (r < ROWS - 1 && ice[i + COLS] <= 0);
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    @Override
    void tick() {
        tic++;
        if (messageTicks > 0) messageTicks--;
        moveParticles();
        if (ending >= 0) {
            if (ending == 34) Draw.beep(Sounds.PAPER, 1.1f, 0.5f);
            if (++ending >= END) {
                perfect = cooked < 25 && time > DURATION / 3;
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
        heat();
        weather();
        // The feather: too warm, it cooks.
        double hottest = 0;
        freed = 0;
        for (int i = 0; i < ice.length; i++) {
            if (!clue[i]) continue;
            hottest = Math.max(hottest, temp[i]);
            if (ice[i] <= 0) freed++;
        }
        if (hottest > COOK_FROM) {
            cooked += (hottest - COOK_FROM) * 3;
            if (messageTicks == 0) say(T.f("the_feather_is_cooking"), Draw.ALERT);
            if (cooked >= 100) {
                end(false);
                return;
            }
        }
        if (freed >= clueCells) {
            ending = 0;
            Draw.note(1.5f);
            Draw.beep(Sounds.ICE, 1.4f, 0.6f);
        }
    }

    // The gun warms the cells around where it points; warmth seeps to the neighbours and fades. Faces in the open melt
    // when warm enough.
    void heat() {
        aiming = Draw.in(mx, my, ox + BX - 8, oy + BY - 8, COLS * CELL + 16, ROWS * CELL + 16);
        double[] next = temp.clone();
        for (int i = 0; i < ice.length; i++) {
            int c = i % COLS, r = i / COLS;
            double around = 0;
            int n = 0;
            if (c > 0) { around += temp[i - 1]; n++; }
            if (c < COLS - 1) { around += temp[i + 1]; n++; }
            if (r > 0) { around += temp[i - COLS]; n++; }
            if (r < ROWS - 1) { around += temp[i + COLS]; n++; }
            next[i] += (around / n - temp[i]) * 0.08;
            if (aiming) {
                double d = Math.hypot(ox + BX + c * CELL + 2 - mx, oy + BY + r * CELL + 2 - my);
                if (d < REACH) next[i] += 0.032 * (1 - d / REACH);
            }
            next[i] = Math.max(0, Math.min(1.3, next[i] - 0.007));
        }
        System.arraycopy(next, 0, temp, 0, temp.length);
        for (int i = 0; i < ice.length; i++) {
            if (ice[i] <= 0 || !exposed(i) || temp[i] <= MELT_FROM) continue;
            ice[i] -= (temp[i] - MELT_FROM) * 0.6;
            if (ice[i] <= 0) {
                ice[i] = 0;
                melted++;
                if (melted % 6 == 0) Draw.beep(Sounds.ICE, 1.5f + rng.nextFloat() * 0.3f, 0.3f);
                int c = i % COLS, r = i / COLS;
                if (drips.size() < 40) drips.add(new double[] {BX + c * CELL + 2, BY + r * CELL + 3, 0, 0.3});
            }
        }
    }

    // The gusts: flakes come in from one side first, then the cold refreezes that side's open edge, warm ice excepted.
    void weather() {
        if (gust > 0) {
            gust--;
            for (int i = 0; i < ice.length; i++) {
                int c = i % COLS;
                if ((gustSide < 0) != (c < COLS / 2)) continue;
                temp[i] *= 0.985;
                if (ice[i] >= 1 || temp[i] > WARM || !nextToIce(i)) continue;
                ice[i] = Math.min(1, ice[i] + 0.02);
                frost[i] = 1;
            }
            if (gust == 0) {
                nextGust = 240 + rng.nextInt(140);
                gustSide = rng.nextBoolean() ? -1 : 1;
            }
        } else if (warning > 0) {
            if (--warning == 0) {
                gust = 40;
                Draw.beep(Sounds.WHISTLE, 0.7f, 0.6f);
            }
        } else if (--nextGust <= 0) {
            warning = 60;
            say(T.f("cold_gust_coming"), 0xFFBFE4FF);
        }
        if ((warning > 0 || gust > 0) && flakes.size() < 70) {
            for (int k = 0; k < (gust > 0 ? 3 : 1); k++) {
                double x = gustSide < 0 ? -4 : AREA_W + 4;
                flakes.add(new double[] {x, rng.nextDouble() * 100, -gustSide * (gust > 0 ? 4.5 : 2.5) * (0.7 + rng.nextDouble() * 0.6),
                        0.3 + rng.nextDouble() * 0.5});
            }
        }
        for (int i = 0; i < frost.length; i++) frost[i] = Math.max(0, frost[i] - 0.01);
    }

    boolean nextToIce(int i) {
        int c = i % COLS, r = i / COLS;
        return ice[i] > 0 || (c > 0 && ice[i - 1] > 0.5) || (c < COLS - 1 && ice[i + 1] > 0.5) || (r > 0 && ice[i - COLS] > 0.5)
                || (r < ROWS - 1 && ice[i + COLS] > 0.5);
    }

    void moveParticles() {
        for (int k = drips.size() - 1; k >= 0; k--) {
            double[] d = drips.get(k);
            d[3] += 0.18;
            d[1] += d[3];
            if (d[1] > BY + ROWS * CELL + 2) drips.remove(k);
        }
        for (int k = flakes.size() - 1; k >= 0; k--) {
            double[] f = flakes.get(k);
            f[0] += f[2];
            f[1] += f[3] + Math.sin((tic + k) * 0.2) * 0.3;
            if (f[0] < -8 || f[0] > AREA_W + 8 || f[1] > AREA_H) flakes.remove(k);
        }
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, SCENE, ax, ay, AREA_W, AREA_H);
        int ground = ay + BY + ROWS * CELL;
        // The puddle under the block grows with what has melted.
        int pw = 20 + melted * 90 / (COLS * ROWS);
        g.fill(ax + BX + 48 - pw, ground - 1, ax + BX + 48 + pw, ground + 3, 0xA07FB6E0);
        g.fill(ax + BX + 50 - pw, ground - 1, ax + BX + 46 + pw, ground, 0xC0D8F0FF);
        Draw.image(g, CLOTH, ax + CLOTH_X, ay + CLOTH_Y, 39, 22);
        // The feather, under the ice; outlined so it reads through the blue.
        if (ending < 0) drawFeather(g, ax + FX, ay + FY, 1, 0);
        for (int i = 0; i < ice.length; i++) {
            int c = i % COLS, r = i / COLS, x = ax + BX + c * CELL, y = ay + BY + r * CELL;
            if (ice[i] > 0) {
                g.setColor(1f, 1f, 1f, (float) ((clue[i] ? 0.2 : 0.45) + 0.45 * ice[i]));
                Draw.pieceEnlarged(g, ICE, x, y, CELL, CELL, c * CELL, r * CELL, CELL, CELL, COLS * CELL, ROWS * CELL);
                g.setColor(1f, 1f, 1f, 1f);
                if (frost[i] > 0) g.fill(x, y, x + CELL, y + CELL, (int) (frost[i] * 0xA0) << 24 | 0xF4FAFF);
                // The open faces: a crisp frosty rim, wet and glistening while melting.
                int rim = exposed(i) && temp[i] > MELT_FROM && tic % 10 < 5 ? 0xFFFFFFFF : 0xFF5E9CD0;
                if (c == 0 || ice[i - 1] <= 0) g.fill(x, y, x + 1, y + CELL, rim);
                if (c == COLS - 1 || ice[i + 1] <= 0) g.fill(x + CELL - 1, y, x + CELL, y + CELL, rim);
                if (r == 0 || ice[i - COLS] <= 0) g.fill(x, y, x + CELL, y + 1, rim);
                if (r == ROWS - 1) g.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF4A80B0);
            }
            // Warmth: orange, then red over the feather when it nears cooking.
            if (temp[i] > 0.25) {
                boolean danger = clue[i] && temp[i] > 0.6;
                int a = (int) (Math.min(1, (temp[i] - 0.25) / 0.8) * (danger ? 0xB0 : 0x70));
                if (danger && tic % 8 < 4) a = a * 2 / 3;
                g.fill(x, y, x + CELL, y + CELL, a << 24 | (danger ? 0xE8402A : 0xF29A3A));
            }
        }
        for (double[] d : drips) g.fill(ax + (int) d[0], ay + (int) d[1], ax + (int) d[0] + 1, ay + (int) d[1] + 2, 0xE0BFE4FF);
        if (ending >= 0) drawLift(g);
        for (double[] f : flakes) {
            int x = ax + (int) f[0], y = ay + (int) f[1];
            g.fill(x, y, x + 2, y + 2, gust > 0 ? 0xF0FFFFFF : 0xB0FFFFFF);
        }
        // The heat gun, blowing down at the cursor; its hot spot glows on the ice.
        if (!finished && ending < 0 && Draw.in(px, py, ax, ay, AREA_W, AREA_H)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            if (aiming) {
                for (int k = 3; k >= 1; k--) {
                    int rad = k * 5;
                    g.fill(px - rad, py - rad, px + rad, py + rad, 0x30FF7030);
                }
                for (int k = 0; k < 3; k++) {
                    int wy = py - 12 + (tic * 2 + k * 4) % 10;
                    g.fill(px - 4 + k * 3, wy, px - 3 + k * 3, wy + 2, 0x80FFC070);
                }
            }
            Draw.image(g, GUN, px - 14, py - 48, 42, 35);
            g.pose().popPose();
        }
        drawHud(g, ax, ay);
    }

    void drawFeather(GuiGraphics g, int x, int y, double scale, double tilt) {
        g.pose().pushPose();
        g.pose().translate(x + FW / 2.0, y + FH / 2.0, 0);
        if (tilt != 0) g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) tilt));
        g.pose().scale((float) scale, (float) scale, 1);
        g.setColor(0.12f, 0.2f, 0.36f, 1f);
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) Draw.image(g, FEATHER, -FW / 2 + o[0], -FH / 2 + o[1], FW, FH);
        g.setColor(1f, 1f, 1f, 1f);
        Draw.image(g, FEATHER, -FW / 2, -FH / 2, FW, FH);
        g.pose().popPose();
    }

    // The freed feather is lifted out of the water, dripping, then laid down on the cloth.
    void drawLift(GuiGraphics g) {
        double up = Math.min(1, ending / 18.0), go = Math.max(0, Math.min(1, (ending - 18) / 18.0));
        double e = go * go * (3 - 2 * go);
        double x0 = ox + FX, y0 = oy + FY, x1 = ox + CLOTH_X + 3, y1 = oy + CLOTH_Y - 22;
        double x = x0 + (x1 - x0) * e, y = y0 - up * 14 + (y1 - y0 + 14) * e - Math.sin(go * Math.PI) * 16;
        double tilt = ending < 36 ? Math.sin(ending * 0.3) * 6 : -55 * Math.min(1, (ending - 36) / 8.0);
        double scale = ending < 36 ? 1 : 0.8;
        drawFeather(g, (int) x, (int) y, scale, tilt);
        if (ending < 40 && ending % 4 == 0 && drips.size() < 40) drips.add(new double[] {x - ox + FW / 2.0 - 4 + rng.nextInt(9), y - oy + FH - 4, 0, 0.5});
        if (ending > 44) {
            String s = T.f("feather_freed_2");
            int w = Draw.textFont().width(s), cx = ox + CLOTH_X + 19;
            g.fill(cx - w / 2 - 4, oy + 76, cx + w / 2 + 4, oy + 86, 0xC0101418);
            g.drawString(Draw.textFont(), s, cx - w / 2, oy + 77, Draw.GREEN, false);
        }
    }

    void drawHud(GuiGraphics g, int ax, int ay) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        String left = T.f("feather_freed") + (freed * 100 / Math.max(1, clueCells)) + " %";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        String right = T.f("feather_heat");
        int rw = Draw.textFont().width(right), gx = ax + AREA_W - 48;
        g.fill(gx - rw - 8, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, gx - rw - 4, ay + 3, Draw.TEXT, false);
        double k = Math.min(1, cooked / 100);
        g.fill(gx, ay + 4, gx + 42, ay + 10, Draw.EDGE);
        g.fill(gx + 1, ay + 5, gx + 1 + (int) (40 * k), ay + 9, k > 0.6 ? Draw.ALERT : 0xFFF29A3A);
        g.fill(ax + 2, ay + 108, ax + 66, ay + 116, 0xC0101418);
        timeGauge(g, ax + 5, ay + 109, 58, time / (double) DURATION);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 140 - w / 2 - 4, ay + 14, ax + 140 + w / 2 + 4, ay + 24, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 140 - w / 2, ay + 15, messageColour, false);
        }
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("sweep_the_heat_gun_over");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("move_aim_the_heat_gun"), Draw.MOUSE)};
    }
}
