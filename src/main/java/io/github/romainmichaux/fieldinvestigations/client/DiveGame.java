package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Breath-hold dive (aquatic investigation): the glowing trace drifts with the current. The vial swims after the pointer with
// some inertia, so its path has to be anticipated; holding the click with the vial in the glow fills it. Air runs out: come
// up to the surface to breathe, while the trace drifts on and the vial loses a little. Currents sweep the water now and
// then, and a fast pass near the bottom stirs up silt that hides the trace.
final class DiveGame extends MiniGame {
    static final ResourceLocation LAKE = Draw.texture("dive_lake"), VIAL = Draw.texture("dive_vial");
    static final int SURFACE = 10, FLOOR = 98, VIAL_W = 20, VIAL_H = 41;
    final double p, q;
    final Random h;
    double air = 100, vial, x = 140, y = 60, vx, vy, gust, gustDir;
    int tic, gustTicks, nextGust = 120;
    boolean onTrace, breathing;
    double cx, cy;
    final List<double[]> bubbles = new ArrayList<>(), silt = new ArrayList<>(), streaks = new ArrayList<>();

    DiveGame(Random h) {
        this.h = h;
        p = h.nextDouble() * 6;
        q = h.nextDouble() * 6;
    }

    // The trace's drift: its phases advance a little each tick, faster as the vial fills (never jumping).
    double phaseX, phaseY;

    double tx() {
        return 140 + 108 * Math.sin(phaseX + p) + 10 * Math.sin(tic * 0.09);
    }

    double ty() {
        return 56 + 30 * Math.sin(phaseY + q);
    }

    @Override
    void tick() {
        tic++;
        double pace = 1 + vial / 160;
        phaseX += 0.026 * pace;
        phaseY += 0.041 * pace;
        // Swimming: the vial is pulled towards the pointer and the water slows it down.
        double ax = cx - ox, ay = cy - oy;
        vx = (vx + (ax - x) * 0.05) * 0.84;
        vy = (vy + (ay - y) * 0.05) * 0.84;
        // Currents: a gust now and then, shown by streaks, pushes the vial sideways.
        if (gustTicks > 0) {
            gustTicks--;
            vx += gustDir * gust;
            if (tic % 2 == 0) streaks.add(new double[] {gustDir > 0 ? -10 : AREA_W + 10, SURFACE + 6 + h.nextInt(FLOOR - SURFACE - 6), 18});
        } else if (--nextGust <= 0) {
            gustTicks = 50;
            gustDir = h.nextBoolean() ? 1 : -1;
            gust = 0.35 + h.nextDouble() * 0.2;
            nextGust = 100 + h.nextInt(80);
            Draw.beep(Sounds.BUBBLE, 0.5f, 0.6f);
        }
        x = Math.max(6, Math.min(AREA_W - 6, x + vx));
        y = Math.max(11, Math.min(FLOOR + 6, y + vy));
        double speed = Math.hypot(vx, vy);
        if (speed > 2 && tic % 3 == 0) bubbles.add(new double[] {x + h.nextInt(5) - 2, y - 6, 0});
        // A fast pass near the bottom stirs up a cloud of silt.
        if (y > FLOOR - 10 && speed > 2.2 && tic % 4 == 0) silt.add(new double[] {x, FLOOR + 2, 4, 70});
        bubbles.removeIf(b -> (b[1] -= 0.8) < SURFACE);
        silt.removeIf(s -> {
            s[2] += 0.35;
            s[1] -= 0.08;
            return --s[3] <= 0;
        });
        streaks.removeIf(s -> {
            s[0] += gustDir * 7;
            return --s[2] <= 0;
        });

        breathing = y < SURFACE + 6;
        if (breathing) {
            air = Math.min(100, air + 1.6);
            vial = Math.max(0, vial - 0.3);
            if (tic % 10 == 0) Draw.beep(Sounds.BUBBLE, 1.4f, 0.5f);
        } else {
            // About eleven seconds of air for about ten seconds of contact needed: a dive takes a breath or two.
            air -= 0.45;
        }
        boolean hidden = false;
        for (double[] s : silt) hidden |= Math.hypot(s[0] - tx(), s[1] - ty()) < s[2] + 4 && s[3] > 20;
        onTrace = mouseDown && !breathing && Math.hypot(x - tx(), y - ty()) < 12;
        if (onTrace) {
            vial += 0.5;
            if (tic % 12 == 0) Draw.beep(Sounds.VIAL, (float) (0.8 + vial / 120), 0.6f);
        } else {
            vial = Math.max(0, vial - 0.12);
        }
        if (tic % 16 == 0) Draw.beep(Sounds.BUBBLE, 0.8f + (tic % 5) * 0.1f, 0.3f);
        // The heartbeat races as air runs low.
        if (air < 35 && tic % (air < 15 ? 8 : 14) == 0) Draw.beep(Sounds.HEARTBEAT, 1.2f, 0.9f);
        if (vial >= 100) {
            perfect = air >= 40;
            end(true);
        } else if (air <= 0) {
            end(false);
        }
        this.traceHidden = hidden;
    }

    boolean traceHidden;

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        cx = mx;
        cy = my;
        int ax = ox, ay = oy;
        Draw.piece(g, LAKE, ax, ay, 0, 0, AREA_W, AREA_H, 280, 120);
        // The surface glitters while breathing, and blinks when air is low.
        if (breathing || air < 35) {
            int c = breathing ? 0x50E8F8FF : ((tic / 6) % 2 == 0 ? 0x40FFFFFF : 0x10FFFFFF);
            g.fill(ax, ay, ax + AREA_W, ay + SURFACE, c);
        }
        for (double[] s : streaks) g.fill(ax + (int) s[0], ay + (int) s[1], ax + (int) s[0] + 14, ay + (int) s[1] + 1, 0x60E8F8FF);
        // The trace: a glowing wisp with a soft halo.
        int tx = ax + (int) tx(), ty = ay + (int) ty();
        disc(g, tx, ty, 9, onTrace ? 0x406CF0A0 : 0x3060E0D0);
        disc(g, tx, ty, 5, onTrace ? 0xFFB8FFD0 : 0xFF7FE0D0);
        disc(g, tx, ty, 2, 0xFFFFFFFF);
        for (int k = 0; k < 4; k++) {
            double a = tic * 0.2 + k * Math.PI / 2;
            g.fill(tx + (int) (Math.cos(a) * 8), ty + (int) (Math.sin(a) * 8), tx + (int) (Math.cos(a) * 8) + 1, ty + (int) (Math.sin(a) * 8) + 1, 0xC0E8FFF4);
        }
        // Silt clouds over the trace.
        for (double[] s : silt) {
            int a = (int) Math.min(0x70, s[3] * 2);
            disc(g, ax + (int) s[0], ay + (int) s[1], (int) s[2], (a << 24) | 0x6A5A40);
            disc(g, ax + (int) s[0] - 2, ay + (int) s[1] + 1, (int) (s[2] * 0.7), (a << 24) | 0x7A6A50);
        }
        for (double[] b : bubbles) g.fill(ax + (int) b[0], ay + (int) b[1], ax + (int) b[0] + 2, ay + (int) b[1] + 2, 0x90DDF4FF);

        // The vial, tilted by its motion, filling with the glow.
        float tilt = (float) Math.max(-25, Math.min(25, vx * 6));
        g.pose().pushPose();
        g.pose().translate(ax + (float) x, ay + (float) y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        Draw.image(g, VIAL, -VIAL_W / 2, -VIAL_H / 2, VIAL_W, VIAL_H);
        g.pose().popPose();
        // The glow collected so far, rising in the glass.
        int fill = (int) Math.round(9 * Math.min(1, vial / 100));
        if (fill > 0) g.fill(-3, 8 - fill, 4, 8, onTrace ? 0xE0B8FFD0 : 0xD07FE0D0);
        g.pose().popPose();
        if (onTrace) Draw.circle(g, ax + x - 1, ay + y - 1, 12, 1, 0x806CF0A0);

        // Air in the top left corner, the vial's fill in the top right; a hint to come up when air runs low.
        String v = (int) Math.min(100, vial) + " %";
        g.fill(ax + 2, ay + 2, ax + 70 + Draw.textFont().width(T.f("air")) + 4, ay + 13, 0xB0101418);
        g.fill(ax + AREA_W - 10 - Draw.textFont().width(v), ay + 2, ax + AREA_W - 2, ay + 13, 0xB0101418);
        g.fill(ax + 4, ay + 4, ax + 66, ay + 10, Draw.EDGE);
        g.fill(ax + 5, ay + 5, ax + 5 + (int) (60 * Math.max(0, air) / 100), ay + 9, air < 35 ? Draw.ALERT : 0xFF5BB8F0);
        text(g, T.f("air"), ax + 70, ay + 3, air < 35 ? Draw.ALERT : 0xFF8FC8F0);
        text(g, v, ax + AREA_W - 6 - Draw.textFont().width(v), ay + 3, Draw.GREEN);
        if (air < 35 && !breathing) Draw.centre(g, T.f("go_up_for_air"), ax + AREA_W / 2, ay + 14, (tic / 8) % 2 == 0 ? Draw.ALERT : Draw.YELLOW);
        else if (breathing) Draw.centre(g, T.f("breathing"), ax + AREA_W / 2, ay + 14, 0xFF8FC8F0);
    }

    @Override
    String instruction() {
        return T.f("below_the_surface_the_trace");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("swim"), Draw.MOUSE), new Control(T.f("fill_hold"), Draw.MOUSE)};
    }
}
