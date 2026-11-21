package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Swab: a stain of the strange deposit runs along the furrows of a tree's bark. It is rubbed with a cotton swab, the
// click held, leaving a wet trail. A swab holds only so much (a little over a third of the stain), and bare bark
// fills it with dirt too; once full it takes nothing more. Lifting it puts it away in its tube, and there are only
// three: no cotton is to be wasted on bark. The cotton takes the stain's colour as it absorbs. At the end the tubes are
// capped and labelled.
final class SwabGame extends MiniGame {
    static final ResourceLocation BARK = Draw.texture("swab_bark"), STICK = Draw.texture("swab_stick"), TUBE = Draw.texture("swab_tube");
    static final int COLS = 45, ROWS = 25, CELL = 4, GX = 18, GY = 8, DURATION = 900, SWABS = 3, END = 90;
    static final int STAIN = 0xFF8A4FC8;
    final boolean[] stain = new boolean[COLS * ROWS];
    final int[] rubbedAt = new int[COLS * ROWS], rubbedBy = new int[COLS * ROWS];
    final int[] absorbed = new int[SWABS], dirt = new int[SWABS];
    final List<int[]> trail = new ArrayList<>(); // x, y, swab
    int time = DURATION, flash, tic, total, rubbed, lastCell = -1, used, ending = -1, messageTicks, messageColour;
    boolean rubbing;
    double mx, my;
    String message = "";

    SwabGame(Random h) {
        // One or two blobs stretched along the furrows, with a few holes; retried until it can be done in three strokes.
        for (int attempt = 0; attempt < 200; attempt++) {
            java.util.Arrays.fill(stain, false);
            int blobs = 3 + h.nextInt(3);
            double[][] b = new double[blobs][4];
            double bx = 12 + h.nextInt(COLS - 24), by = 8 + h.nextInt(ROWS - 16);
            for (int k = 0; k < blobs; k++) {
                // Each blob near the last, so the stain runs on; tall and narrow, like a drip down a furrow.
                bx = Math.max(5, Math.min(COLS - 6, bx + h.nextGaussian() * 6));
                by = Math.max(4, Math.min(ROWS - 5, by + h.nextGaussian() * 4));
                b[k] = new double[] {bx, by, 2.2 + h.nextDouble() * 2.2, 4 + h.nextDouble() * 5};
            }
            for (int i = 0; i < stain.length; i++) {
                int c = i % COLS, r = i / COLS;
                double f = 0;
                for (double[] p : b) f += Math.exp(-(Math.pow((c - p[0]) / p[2], 2) + Math.pow((r - p[1]) / p[3], 2)));
                f += (h.nextDouble() - 0.5) * 0.3;
                stain[i] = f > 0.5;
            }
            total = 0;
            for (boolean s : stain) total += s ? 1 : 0;
            if (total >= 130 && total <= 240 && components() <= 2) break;
        }
    }

    int components() {
        boolean[] seen = new boolean[stain.length];
        int n = 0;
        for (int i = 0; i < stain.length; i++) {
            if (!stain[i] || seen[i]) continue;
            n++;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            seen[i] = true;
            while (!q.isEmpty()) {
                int j = q.poll(), c = j % COLS, r = j / COLS;
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] e : d) {
                    int cc = c + e[0], rr = r + e[1], k = rr * COLS + cc;
                    if (cc >= 0 && cc < COLS && rr >= 0 && rr < ROWS && stain[k] && !seen[k]) {
                        seen[k] = true;
                        q.add(k);
                    }
                }
            }
        }
        return n;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    int cellAt(double px, double py) {
        int c = (int) Math.floor((px - ox - GX) / CELL), r = (int) Math.floor((py - oy - GY) / CELL);
        return c >= 0 && c < COLS && r >= 0 && r < ROWS ? r * COLS + c : -1;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending % 20 == 10 && ending < 70) Draw.beep(Sounds.PAPER, 1.2f, 0.5f);
            if (++ending >= END) {
                perfect = used <= SWABS && time > DURATION / 2;
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
        if (!rubbing) return;
        if (!mouseDown) {
            lift();
            return;
        }
        int i = cellAt(mx, my);
        if (tic % 2 == 0) trail.add(new int[] {(int) (mx - ox), (int) (my - oy), used});
        if (i < 0) return;
        // Bare bark under the tip: dirt in the cotton.
        if (!stain[i] && i != lastCell) dirt[used]++;
        lastCell = i;
        // The cotton tip covers the cell under it and those around.
        int c0 = i % COLS, r0 = i / COLS;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int c = c0 + dc, r = r0 + dr, k = r * COLS + c;
                if (c < 0 || c >= COLS || r < 0 || r >= ROWS || !stain[k] || rubbedAt[k] != 0) continue;
                if (full(used)) {
                    if (messageTicks == 0) say(T.f("swab_full_release"), Draw.YELLOW);
                    return;
                }
                rubbedAt[k] = tic;
                rubbedBy[k] = used + 1;
                absorbed[used]++;
                if (++rubbed % 2 == 0) Draw.beep(Sounds.RUB, 1.3f + rubbed * 0.6f / total, 0.6f);
                if (rubbed >= total) {
                    lift();
                    ending = 0;
                    Draw.note(1.5f);
                    return;
                }
            }
        }
    }

    // What a swab can hold: a little over a third of the stain, dirt from bare bark counting for a quarter.
    int capacity() {
        return (int) Math.ceil(total / 2.6);
    }

    double load(int s) {
        return absorbed[s] + dirt[s] * 0.25;
    }

    boolean full(int s) {
        return load(s) >= capacity();
    }

    // The swab is lifted: away in its tube. With none left and stain still there, the sample is incomplete.
    void lift() {
        rubbing = false;
        lastCell = -1;
        if (absorbed[used] == 0) return;
        used++;
        Draw.beep(Sounds.VIAL, 1.2f, 0.5f);
        if (rubbed < total && used >= SWABS) {
            say(T.f("no_swab_left"), Draw.ALERT);
            end(false);
        } else if (rubbed < total) {
            say(T.f("new_swab"), Draw.YELLOW);
        }
    }

    @Override
    void click(double px, double py) {
        if (finished || ending >= 0 || used >= SWABS) return;
        if (cellAt(px, py) < 0) return;
        mx = px;
        my = py;
        rubbing = true;
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, BARK, ax, ay, AREA_W, AREA_H);
        // The stain: softer at its edges; rubbed cells turn to wet, darker bark.
        for (int i = 0; i < stain.length; i++) {
            if (!stain[i]) continue;
            int c = i % COLS, r = i / COLS, x = ax + GX + c * CELL, y = ay + GY + r * CELL;
            if (rubbedAt[i] != 0) {
                g.fill(x, y, x + CELL, y + CELL, 0x40C8D8E8);
                continue;
            }
            int around = 0;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] e : d) {
                int cc = c + e[0], rr = r + e[1];
                if (cc >= 0 && cc < COLS && rr >= 0 && rr < ROWS && stain[rr * COLS + cc]) around++;
            }
            int a = around == 4 ? 0xD0 : around >= 2 ? 0x98 : 0x60;
            // A faint halo first, so the edge is soft rather than square.
            if (around < 4) g.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, (0x30 << 24) | (STAIN & 0xFFFFFF));
            g.fill(x, y, x + CELL, y + CELL, (a << 24) | (STAIN & 0xFFFFFF));
            if ((c + r * 3) % 7 == 0) g.fill(x + 1, y + 1, x + 2, y + 2, 0x80E8C8FF);
        }
        // The wet trail of the strokes.
        for (int k = 1; k < trail.size(); k++) {
            int[] a = trail.get(k - 1), b = trail.get(k);
            if (a[2] != b[2] || Math.hypot(a[0] - b[0], a[1] - b[1]) > 12) continue;
            int steps = Math.max(1, (int) Math.hypot(a[0] - b[0], a[1] - b[1]));
            for (int s = 0; s <= steps; s++) {
                int x = ax + a[0] + (b[0] - a[0]) * s / steps, y = ay + a[1] + (b[1] - a[1]) * s / steps;
                g.fill(x - 1, y - 1, x + 2, y + 2, 0x30F0F4FF);
            }
        }
        drawRack(g, ax + 218, ay + 26);
        // The swab in hand: its cotton coloured by what it took, browned by bark.
        if (!finished && ending < 0 && used < SWABS && Draw.in(px, py, ax, ay, AREA_W, AREA_H)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            Draw.image(g, STICK, px - 4, py - 78, 9, 81);
            int tip = tipColour(used);
            g.fill(px - 3, py - 6, px + 4, py + 2, tip);
            g.fill(px - 2, py - 7, px + 3, py + 3, tip);
            g.pose().popPose();
        }
        // Counters and the last message.
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        String left = T.f("swabbed") + (rubbed * 100 / Math.max(1, total)) + " %";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        // How full the swab in hand is.
        if (used < SWABS) {
            String right = T.f("swab");
            int rw = Draw.textFont().width(right);
            int gx = ax + AREA_W - 48;
            g.fill(gx - rw - 8, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), right, gx - rw - 4, ay + 3, Draw.TEXT, false);
            double k = Math.min(1, load(used) / capacity());
            g.fill(gx, ay + 4, gx + 42, ay + 10, Draw.EDGE);
            g.fill(gx + 1, ay + 5, gx + 1 + (int) (40 * k), ay + 9, k >= 1 ? Draw.ALERT : 0xFFB070E0);
        }
        g.fill(ax + 2, ay + 108, ax + 66, ay + 116, 0xC0101418);
        timeGauge(g, ax + 5, ay + 109, 58, time / (double) DURATION);
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 110 - w / 2 - 4, ay + 2, ax + 110 + w / 2 + 4, ay + 12, 0xC0101418);
            Draw.centre(g, message, ax + 110, ay + 3, messageColour);
        }
        g.pose().popPose();
    }

    int tipColour(int s) {
        double take = Math.min(1, absorbed[s] / (double) capacity()), brown = Math.min(1, dirt[s] * 0.25 / capacity() * 3);
        return mix(mix(0xFFF6F4EE, STAIN, take * 0.85), 0xFF6A4A30, brown * 0.7);
    }

    static int mix(int a, int b, double k) {
        int r = 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            int ca = (a >> sh) & 0xFF, cb = (b >> sh) & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * k)) << sh;
        }
        return r;
    }

    // The rack of three tubes: used ones hold their swab and its colour; at the end they are capped and labelled.
    void drawRack(GuiGraphics g, int x, int y) {
        g.fill(x - 4, y + 52, x + 58, y + 58, 0xFF5A4030);
        g.fill(x - 4, y + 52, x + 58, y + 53, 0xFF8A6A50);
        for (int s = 0; s < SWABS; s++) {
            int tx = x + s * 20;
            int lift = ending >= 0 ? (int) Math.max(0, Math.min(8, (ending - s * 20) / 2.0)) : 0;
            int ty = y - lift;
            boolean full = s < used;
            if (full) {
                Draw.image(g, STICK, tx + 4, ty - 6, 6, 54);
                int tip = tipColour(s);
                g.fill(tx + 4, ty + 44, tx + 10, ty + 50, tip);
            }
            Draw.image(g, TUBE, tx, ty, 14, 63);
            if (full) g.fill(tx + 3, ty + 40, tx + 11, ty + 58, (0x70 << 24) | (tipColour(s) & 0xFFFFFF));
            if (ending >= 0 && full && ending > s * 20 + 12) {
                g.fill(tx + 1, ty + 22, tx + 13, ty + 34, 0xFFF4F0E6);
                g.fill(tx + 1, ty + 22, tx + 13, ty + 23, 0xFFB83A30);
                g.drawString(Draw.textFont(), String.valueOf(s + 1), tx + 5, ty + 25, Draw.INK, false);
            }
            if (s == used && ending < 0 && !finished) g.fill(tx, ty + 64, tx + 14, ty + 65, Draw.YELLOW);
        }
        if (ending > 60) {
            String s = T.f("swabs_stored");
            int w = Draw.textFont().width(s);
            g.fill(x + 27 - w / 2 - 4, y + 62, x + 27 + w / 2 + 4, y + 72, 0xC0101418);
            g.drawString(Draw.textFont(), s, x + 27 - w / 2, y + 63, Draw.GREEN, false);
        }
    }

    @Override
    String instruction() {
        return T.f("rub_the_stain_holding_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("hold_rub_release_new_swab"), Draw.MOUSE)};
    }
}
