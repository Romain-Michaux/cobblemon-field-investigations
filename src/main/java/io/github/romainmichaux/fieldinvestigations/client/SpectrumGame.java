package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

// Spectrum tuning: on a spectrometer's screen, the sample's curve shows through noise that clears as the measure builds
// up. Three knobs, turned with the wheel, set the frequency, amplitude and phase of the player's curve; under each a
// lamp glows as that setting nears the sample's. The player validates (Space) when the curves look one: at 92 % match or
// more it is done, below it is a mistake. The validated curve is printed on a paper ticket.
final class SpectrumGame extends MiniGame {
    static final ResourceLocation MACHINE = Draw.texture("spec_machine"), KNOB = Draw.texture("spec_knob");
    static final int DURATION = 1000, MAX_FAULTS = 3, END = 110, PASS = 92;
    // Screen, in the play area.
    static final int SX = 28, SY = 19, SW = 96, SH = 72;
    static final int[] KNOB_X = {156, 206, 256};
    static final int KNOB_Y = 76;
    final double[] target = new double[3], setting = new double[3];
    final Random h;
    int time = DURATION, faults, tic, ending = -1, messageTicks, messageColour, last = -1;
    double mx, my;
    String message = "";

    SpectrumGame(Random h) {
        this.h = h;
        for (int k = 0; k < 3; k++) target[k] = 0.15 + h.nextDouble() * 0.7;
        do {
            for (int k = 0; k < 3; k++) setting[k] = h.nextDouble();
        } while (error(0) + error(1) + error(2) < 0.7);
    }

    static double curve(double[] p, double t) {
        return Math.sin(t * (1.5 + p[0] * 3) * Math.PI * 2 + p[2] * Math.PI * 2) * (8 + p[1] * 22);
    }

    // How far a setting is from the sample's (phase wraps round).
    double error(int k) {
        double d = Math.abs(setting[k] - target[k]);
        return k == 2 ? Math.min(d, 1 - d) : d;
    }

    int match() {
        double total = 0;
        for (int i = 0; i < 96; i++) total += Math.abs(curve(target, i / 96.0) - curve(setting, i / 96.0));
        return (int) Math.round(Math.max(0, 1 - total / 96 / 18) * 100);
    }

    // The noise over the sample's curve, clearing as the measure builds up.
    double noise() {
        return 1.2 + 13 * Math.exp(-tic / 220.0);
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 45;
    }

    @Override
    void tick() {
        tic++;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending < 50 && ending % 3 == 0) Draw.beep(Sounds.TICK, 1.6f, 0.25f);
            if (ending == 70) Draw.beep(Sounds.PAPER, 1f, 0.8f);
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
    }

    int knobAt(double px, double py) {
        for (int k = 0; k < 3; k++) if (Math.hypot(px - ox - KNOB_X[k], py - oy - KNOB_Y) < 15) return k;
        return -1;
    }

    @Override
    void scrolled(double amount) {
        if (amount == 0 || finished || ending >= 0) return;
        int k = knobAt(mx, my);
        if (k < 0) k = last;
        if (k < 0) return;
        last = k;
        double step = (Screen.hasShiftDown() ? 0.005 : 0.02) * (amount > 0 ? 1 : -1);
        setting[k] = k == 2 ? ((setting[k] + step) % 1 + 1) % 1 : Math.max(0, Math.min(1, setting[k] + step));
        Draw.beep(Sounds.KNOB, 0.9f + (float) setting[k] * 0.6f, 0.3f);
    }

    @Override
    boolean keyPressed(int key) {
        if (key != InputConstants.KEY_SPACE || finished || ending >= 0) return false;
        int m = match();
        if (m >= PASS) {
            ending = 0;
            Draw.note(1.5f);
        } else {
            say(T.f("not_yet") + m + " %", Draw.ALERT);
            Draw.note(0.5f);
            if (++faults >= MAX_FAULTS) end(false);
        }
        return true;
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, MACHINE, ax, ay, AREA_W, AREA_H);
        // The screen: graticule, the sample through its noise, the player's curve.
        int x0 = ax + SX, y0 = ay + SY, cy = y0 + SH / 2;
        for (int i = 0; i <= 4; i++) g.fill(x0, y0 + i * SH / 4, x0 + SW, y0 + i * SH / 4 + 1, 0x3060E0A0);
        for (int i = 0; i <= 6; i++) g.fill(x0 + i * SW / 6, y0, x0 + i * SW / 6 + 1, y0 + SH, 0x3060E0A0);
        Random jitter = new Random(tic / 2);
        double n = ending >= 0 ? 0 : noise();
        for (int i = 0; i < SW; i++) {
            double t = i / (double) SW;
            int ys = (int) Math.round(cy - curve(target, t) + jitter.nextGaussian() * n);
            if (ys > y0 && ys < y0 + SH) g.fill(x0 + i, ys, x0 + i + 1, ys + 1, 0xC0B8C8B8);
            int yp = (int) Math.round(cy - curve(setting, t));
            if (yp > y0 && yp < y0 + SH) g.fill(x0 + i, yp, x0 + i + 1, yp + 2, match() >= PASS ? 0xFF7CFFA0 : 0xFF4FD8F0);
        }
        // The readout: the match, and the mistakes.
        g.fill(ax + 142, ay + 14, ax + 272, ay + 40, 0xFF1A2420);
        g.fill(ax + 143, ay + 15, ax + 271, ay + 39, 0xFF2C3E2E);
        String m = T.f("match") + match() + " %";
        g.drawString(Draw.textFont(), m, ax + 147, ay + 17, 0xFF9CF0B0, false);
        g.drawString(Draw.textFont(), T.f("mistakes") + faults + " / " + MAX_FAULTS, ax + 147, ay + 28, faults > 0 ? 0xFFFF9080 : 0xFF6A9A78, false);
        // The printer's slot.
        g.fill(ax + 160, ay + 46, ax + 256, ay + 48, 0xFF30302A);
        // The knobs, their names and lamps.
        int hover = knobAt(px, py);
        String[] names = {T.f("frequency"), T.f("amplitude"), T.f("phase")};
        for (int k = 0; k < 3; k++) {
            int kx = ax + KNOB_X[k], ky = ay + KNOB_Y;
            String s = names[k];
            g.drawString(Draw.textFont(), s, kx - Draw.textFont().width(s) / 2, ky - 22, k == hover ? 0xFF2A2A20 : 0xFF5A584C, false);
            g.pose().pushPose();
            g.pose().translate(kx, ky, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) (-135 + setting[k] * 270)));
            Draw.image(g, KNOB, -13, -13, 26, 26);
            g.pose().popPose();
            if (k == hover) Draw.circle(g, kx - 0.5, ky - 0.5, 15, 1, 0xFF8A7A40);
            // The lamp: dark when far, bright green when near.
            double near = Math.max(0, 1 - error(k) / 0.25);
            int lamp = mix(0xFF402020, 0xFF60FF70, near * near);
            g.fill(kx - 3, ky + 17, kx + 4, ky + 22, 0xFF202018);
            g.fill(kx - 2, ky + 18, kx + 3, ky + 21, lamp);
        }
        timeGauge(g, ax + SX, ay + 100, SW, time / (double) DURATION);
        if (ending >= 0) drawTicket(g, ax, ay);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            int cx = ax + SX + SW / 2;
            g.fill(cx - w / 2 - 4, ay + 2, cx + w / 2 + 4, ay + 12, 0xD0101418);
            Draw.centre(g, message, cx, ay + 3, messageColour);
        }
    }

    static int mix(int a, int b, double k) {
        int r = 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            int ca = (a >> sh) & 0xFF, cb = (b >> sh) & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * k)) << sh;
        }
        return r;
    }

    // The ticket printed out of the slot: it rises with the curve on it, the peak marked, then a stamp.
    void drawTicket(GuiGraphics g, int ax, int ay) {
        int hgt = (int) Math.min(44, ending * 0.9);
        int x0 = ax + 162, x1 = ax + 254, bottom = ay + 46, top = bottom - hgt;
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        g.fill(x0 - 1, top - 1, x1 + 1, bottom, 0x60000000);
        g.fill(x0, top, x1, bottom, 0xFFF6F4EC);
        // The curve, printed line by line: it scrolls up with the paper.
        int w = x1 - x0 - 6, peakX = -1;
        double peak = -99;
        for (int i = 0; i < w; i++) {
            double v = curve(target, i / (double) w) * 0.6;
            int y = top + 22 - (int) Math.round(v);
            if (v > peak) {
                peak = v;
                peakX = i;
            }
            if (y > top + 1 && y < bottom - 1) g.fill(x0 + 3 + i, y, x0 + 4 + i, y + 1, 0xFF2A3A6A);
        }
        if (ending > 50 && peakX >= 0) {
            int px = x0 + 3 + peakX, py = top + 22 - (int) Math.round(peak);
            g.fill(px, py - 6, px + 1, py - 2, 0xFFB83A30);
            g.drawString(Draw.textFont(), T.f("peak"), px + 2, py - 12, 0xFFB83A30, false);
        }
        g.pose().popPose();
        if (ending >= 70) {
            String s = T.f("spectrum_validated");
            int sw = Draw.textFont().width(s) + 12;
            double k = Math.min(1, (ending - 70) / 6.0);
            g.pose().pushPose();
            g.pose().translate(x0 + (x1 - x0) / 2.0, top + 24, 300);
            float sc = (float) (1.6 - 0.6 * k);
            g.pose().scale(sc, sc, 1);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8));
            g.fill(-sw / 2 - 2, -9, sw / 2 + 2, 9, 0xFF2E7D32);
            g.fill(-sw / 2, -7, sw / 2, 7, 0xF0F4F0E6);
            g.drawString(Draw.textFont(), s, -Draw.textFont().width(s) / 2, -4, 0xFF2E7D32, false);
            g.pose().popPose();
        }
    }

    @Override
    String instruction() {
        return T.f("turn_the_three_knobs_with");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("wheel_turn_a_knob_shift"), Draw.MOUSE), new Control(T.f("validate"), InputConstants.KEY_SPACE)};
    }
}
