package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// UV lamp (night investigation): a trail of drops crosses the forest floor in the dark. They only glow under the UV
// beam, and keep glowing a moment after it has passed, fading; each is drawn out towards the next, so the trail can be
// followed. A drop is collected by holding the click on it while it glows, and goes into a vial. Blue glints (fungi,
// petals) glow too but are not the trace: collecting one wastes battery. The beam narrows as the battery runs down.
// With all the drops in their vials, the vials glow in a row.
final class UvLampGame extends MiniGame {
    static final ResourceLocation GROUND = Draw.texture("uv_ground"), VIAL = Draw.texture("dive_vial");
    static final int DROPS = 6, DECOYS = 4, COLLECT = 24, END = 70;
    final double[] sx = new double[DROPS + DECOYS], sy = new double[DROPS + DECOYS], glow = new double[DROPS + DECOYS];
    final boolean[] taken = new boolean[DROPS + DECOYS];
    double battery = 100, mx = -100, my = -100;
    int collected, flash, tic, holding = -1, hold, ending = -1, messageTicks, messageColour;
    String message = "";

    UvLampGame(Random h) {
        // The trail: from one side, each drop 24 to 38 pixels on from the last, wandering but heading across.
        for (int attempt = 0; attempt < 300; attempt++) {
            boolean ok = true;
            double x = h.nextBoolean() ? 18 : AREA_W - 18, y = 24 + h.nextInt(AREA_H - 48), dir = x < 100 ? 0 : Math.PI;
            for (int i = 0; i < DROPS; i++) {
                sx[i] = x;
                sy[i] = y;
                ok &= x > 12 && x < AREA_W - 12 && y > 16 && y < AREA_H - 14;
                dir += (h.nextDouble() - 0.5) * 1.6;
                double step = 24 + h.nextInt(15);
                x += Math.cos(dir) * step;
                y += Math.sin(dir) * step;
            }
            if (ok) break;
        }
        for (int d = DROPS; d < DROPS + DECOYS; d++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                sx[d] = 16 + h.nextInt(AREA_W - 32);
                sy[d] = 16 + h.nextInt(AREA_H - 30);
                boolean free = true;
                for (int k = 0; k < d; k++) free &= Math.hypot(sx[k] - sx[d], sy[k] - sy[d]) > 18;
                if (free) break;
            }
        }
    }

    double range() {
        return 14 + 12 * battery / 100;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 36;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending % 8 == 0 && ending < 48) Draw.beep(Sounds.VIAL, 1.0f + ending * 0.015f, 0.4f);
            if (++ending >= END) {
                perfect = battery >= 35;
                end(true);
            }
            return;
        }
        if (finished) return;
        battery -= 0.12;
        if (tic % 30 == 0) Draw.beep(Sounds.LAMP, 1.8f, 0.2f);
        if (battery <= 0) {
            end(false);
            return;
        }
        boolean inside = Draw.in(mx, my, ox, oy, AREA_W, AREA_H);
        for (int i = 0; i < sx.length; i++) {
            boolean lit = inside && Math.hypot(mx - ox - sx[i], my - oy - sy[i]) < range();
            glow[i] = lit ? Math.min(1, glow[i] + 0.2) : Math.max(0, glow[i] - 0.012);
        }
        // Holding the click on a glowing spot collects it.
        int under = -1;
        if (mouseDown && inside) {
            for (int i = 0; i < sx.length; i++) {
                if (!taken[i] && glow[i] > 0.2 && Math.hypot(mx - ox - sx[i], my - oy - sy[i]) < 7) under = i;
            }
        }
        if (under != holding) {
            holding = under;
            hold = 0;
        }
        if (holding < 0) return;
        if (++hold % 6 == 0) Draw.beep(Sounds.RUB, 1.3f + hold * 0.02f, 0.3f);
        if (hold < COLLECT) return;
        taken[holding] = true;
        if (holding < DROPS) {
            Draw.note(1f + collected * 0.15f);
            if (++collected >= DROPS) ending = 0;
        } else {
            battery -= 6;
            flash = 6;
            say(T.f("not_the_trace"), Draw.ALERT);
            Draw.beep(Sounds.FAILURE, 1.2f, 0.6f);
        }
        holding = -1;
        hold = 0;
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        g.setColor(0.32f, 0.32f, 0.42f, 1f);
        Draw.image(g, GROUND, ax, ay, AREA_W, AREA_H);
        g.setColor(1f, 1f, 1f, 1f);
        boolean inside = !finished && ending < 0 && Draw.in(px, py, ax, ay, AREA_W, AREA_H);
        int range = (int) range();
        if (inside) {
            // The beam: a violet pool, brighter at its heart, showing the ground a little.
            for (int dy = -range; dy <= range; dy++) {
                int dx = (int) Math.sqrt(range * range - dy * dy), y = py + dy;
                if (y < ay || y >= ay + AREA_H) continue;
                g.fill(Math.max(ax, px - dx), y, Math.min(ax + AREA_W, px + dx), y + 1, 0x309060FF);
                int dx2 = dx * 2 / 3;
                if (Math.abs(dy) < range * 2 / 3) g.fill(Math.max(ax, px - dx2), y, Math.min(ax + AREA_W, px + dx2), y + 1, 0x18B090FF);
            }
        }
        for (int i = 0; i < sx.length; i++) {
            if (glow[i] <= 0.02 || taken[i]) continue;
            int x = ax + (int) sx[i], y = ay + (int) sy[i];
            int a = (int) (glow[i] * 255);
            if (i < DROPS) {
                // A drop, drawn out towards the next one: the trail's direction.
                double dx = 0, dy = 0;
                if (i + 1 < DROPS) {
                    double l = Math.hypot(sx[i + 1] - sx[i], sy[i + 1] - sy[i]);
                    dx = (sx[i + 1] - sx[i]) / l;
                    dy = (sy[i + 1] - sy[i]) / l;
                }
                g.fill(x - 2, y - 2, x + 3, y + 3, (a / 3) << 24 | 0xB8FF5A);
                g.fill(x - 1, y - 1, x + 2, y + 2, a << 24 | 0xC8FF6A);
                for (int k = 1; k <= 4; k++) {
                    int tx = x + (int) Math.round(dx * k * 1.6), ty = y + (int) Math.round(dy * k * 1.6);
                    g.fill(tx, ty, tx + 1, ty + 1, (a * (5 - k) / 5) << 24 | 0xE0FF90);
                }
            } else {
                // A glint: bluer, starry.
                g.fill(x - 1, y, x + 2, y + 1, a << 24 | 0x8AD8FF);
                g.fill(x, y - 1, x + 1, y + 2, a << 24 | 0x8AD8FF);
                g.fill(x - 3, y, x - 2, y + 1, (a / 2) << 24 | 0xC0E8FF);
                g.fill(x + 3, y, x + 4, y + 1, (a / 2) << 24 | 0xC0E8FF);
            }
        }
        // Collecting: a ring closing on the spot.
        if (holding >= 0) {
            int x = ax + (int) sx[holding], y = ay + (int) sy[holding];
            Draw.circle(g, x, y, 9 - hold * 6 / COLLECT, 1, 0xFFE0FF90);
        }
        drawVials(g, ax + AREA_W - 8 - DROPS * 12, ay + AREA_H - 26);
        drawHud(g, ax, ay);
    }

    void drawVials(GuiGraphics g, int x, int y) {
        g.fill(x - 3, y - 3, x + DROPS * 12 + 1, y + 23, 0xA0101418);
        for (int i = 0; i < DROPS; i++) {
            int vx = x + i * 12;
            boolean full = i < collected;
            if (full) {
                double pulse = ending >= 0 ? 0.6 + 0.4 * Math.sin((ending - i * 4) * 0.25) : 0.8;
                g.fill(vx + 2, y + 9, vx + 8, y + 19, (int) (pulse * 255) << 24 | 0xB8FF5A);
            }
            Draw.image(g, VIAL, vx, y, 10, 20);
        }
        if (ending > 20) {
            String s = T.f("traces_collected");
            int w = Draw.textFont().width(s), cx = x + DROPS * 6;
            g.fill(cx - w / 2 - 4, y - 16, cx + w / 2 + 4, y - 6, 0xC0101418);
            g.drawString(Draw.textFont(), s, cx - w / 2, y - 15, Draw.GREEN, false);
        }
    }

    void drawHud(GuiGraphics g, int ax, int ay) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        String left = T.f("stains") + collected + " / " + DROPS;
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        String right = T.f("battery");
        int rw = Draw.textFont().width(right), gx = ax + AREA_W - 48;
        g.fill(gx - rw - 8, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, gx - rw - 4, ay + 3, Draw.TEXT, false);
        double k = Math.max(0, battery / 100);
        g.fill(gx, ay + 4, gx + 42, ay + 10, Draw.EDGE);
        g.fill(gx + 1, ay + 5, gx + 1 + (int) (40 * k), ay + 9, k < 0.3 ? Draw.ALERT : 0xFF9060FF);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 140 - w / 2 - 4, ay + 2, ax + 140 + w / 2 + 4, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 140 - w / 2, ay + 3, messageColour, false);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("sweep_the_beam_the_drops");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("move_sweep_the_beam"), Draw.MOUSE), new Control(T.f("hold_on_a_stain_collect"), Draw.MOUSE)};
    }
}
