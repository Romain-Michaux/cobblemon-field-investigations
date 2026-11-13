package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Centrifugation: the rotor spins with the sample tube in one of its buckets. Brake so that the tube comes to rest under
// the window of the lid: the rotor coasts for about a turn before it stops, so the brake has to come early. Each good
// stop separates one more layer of the sample in the test tube; the window narrows and the rotor spins faster.
final class CentrifugeGame extends MiniGame {
    static final ResourceLocation HOUSING = Draw.texture("centrifuge_housing");
    static final int SPINUP = 34, REST = 26, RADIUS = 36;
    // Angle of the marked bucket on the rotor sprite (degrees, clockwise from the top).
    static final double BUCKET = -6;
    static final double[] SPEED = {7, 9, 11}, WINDOW = {54, 42, 32}, COAST = {300, 380, 470};
    final Random rng;
    double angle, speed, brake;
    int round, misses, flash, phase = 1, timer, tic, grown;
    boolean lastGood;
    // Layer animation in the test tube: how far each separated layer has grown (0..1).
    final double[] layer = new double[3];

    // Phases: 1 spinning up, 2 spinning, 3 braking, 4 stopped (showing the result).
    CentrifugeGame(Random rng) {
        this.rng = rng;
        angle = rng.nextInt(360);
    }

    // Where the marked tube is, relative to the window at the top (degrees in -180..180).
    double offset(double a) {
        return ((a + BUCKET) % 360 + 540) % 360 - 180;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        for (int k = 0; k < 3; k++) {
            if (k < grown && layer[k] < 1) layer[k] = Math.min(1, layer[k] + 0.06);
        }
        double before = angle;
        switch (phase) {
            case 1 -> {
                speed = SPEED[round] * Math.min(1, ++timer / (double) SPINUP);
                if (timer >= SPINUP) phase = 2;
            }
            case 3 -> {
                speed = Math.max(0, speed - brake);
                if (speed == 0) stopped();
            }
            case 4 -> {
                if (++timer >= REST && !finished) {
                    phase = 1;
                    timer = 0;
                }
            }
            default -> {}
        }
        angle = (angle + speed) % 360;
        if (angle < before && speed > 0.5) Draw.beep(Sounds.TICK, (float) (0.6 + speed * 0.08), 0.35f);
        if (speed > 3 && tic % 7 == 0) Draw.beep(Sounds.WHISTLE, (float) (0.5 + speed * 0.07), 0.12f);
    }

    void stopped() {
        phase = 4;
        timer = 0;
        lastGood = Math.abs(offset(angle)) <= WINDOW[round] / 2;
        Draw.beep(Sounds.KNOB, 0.8f, 0.6f);
        if (lastGood) {
            grown = round + 1;
            Draw.note(1f + round * 0.25f);
            if (++round >= 3) {
                perfect = misses == 0;
                end(true);
            }
        } else {
            flash = 8;
            Draw.note(0.5f);
            if (++misses >= 3) end(false);
        }
    }

    void brake() {
        if (phase != 2 || finished) return;
        // Constant deceleration that stops the rotor after COAST degrees.
        brake = speed * speed / (2 * COAST[round]);
        phase = 3;
        Draw.beep(Sounds.CLICK, 0.7f, 0.7f);
    }

    @Override
    void click(double mx, double my) {
        brake();
    }

    @Override
    boolean keyPressed(int key) {
        if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN) {
            brake();
            return true;
        }
        return false;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int cx = ox + 116, cy = oy + 64;
        double spin = finished || phase == 4 ? 0 : speed, degrees = angle + spin * pt;
        // A fast rotor shakes the machine a little.
        int shake = spin > 8 && tic % 2 == 0 ? (rng.nextBoolean() ? 1 : -1) : 0;
        g.fill(ox, oy, ox + AREA_W, oy + AREA_H, 0xFF151C24);
        g.pose().pushPose();
        g.pose().translate(cx + shake, cy, 0);
        // Housing: its bowl (radius about 36 texels) holds the rotor drawn at three quarters of its size.
        g.pose().pushPose();
        g.pose().scale(1f, 1f, 1f);
        Draw.image(g, HOUSING, -64, -64, 128, 128);
        g.pose().popPose();
        // Rotor, with a blurred copy trailing behind at speed.
        if (spin > 4) {
            g.setColor(1f, 1f, 1f, 0.35f);
            rotor(g, degrees - spin * 0.6);
            g.setColor(1f, 1f, 1f, 1f);
        }
        rotor(g, degrees);
        // The window of the lid: a glass slot at the top, narrowing with each round.
        double half = WINDOW[Math.min(2, round)] / 2;
        boolean show = !finished || lastGood;
        int glass = phase == 4 ? (lastGood ? 0x9A6CCB5F : 0x9AE8543A) : 0x704FD1C5;
        for (double a = -half; a <= half; a += 1.5) {
            double r = Math.toRadians(a);
            for (int k = RADIUS - 7; k <= RADIUS + 5; k += 2) Draw.point(g, Math.sin(r) * k - 1, -Math.cos(r) * k - 1, 2, glass);
        }
        if (show) {
            for (int s = -1; s <= 1; s += 2) {
                double r = Math.toRadians(s * half);
                Draw.segment(g, Math.sin(r) * (RADIUS - 10), -Math.cos(r) * (RADIUS - 10), Math.sin(r) * (RADIUS + 5), -Math.cos(r) * (RADIUS + 5), 1, 0xFFE8F4F2);
            }
        }
        g.pose().popPose();

        testTube(g, ox + 220, oy + 24);

        String left = T.f("phases_separated") + Math.min(3, round) + " / 3", right = T.f("misses") + misses + " / 3";
        text(g, left, ox + 6, oy + 4, round > 0 ? Draw.GREEN : Draw.TEXT);
        text(g, right, ox + AREA_W - 6 - Draw.textFont().width(right), oy + 4, misses > 0 ? Draw.ALERT : Draw.GREY);
        if (phase == 4 && !finished) {
            String s = lastGood ? T.f("separated") : T.f("missed");
            Draw.centre(g, s, cx, oy + AREA_H - 12, lastGood ? Draw.GREEN : Draw.ALERT);
        } else if (phase == 1 && !finished) {
            Draw.centre(g, T.f("spinning_up"), cx, oy + AREA_H - 12, Draw.GREY);
        }
        if (flash > 0) {
            g.fill(ox, oy, ox + AREA_W, oy + 1, Draw.ALERT);
            g.fill(ox, oy + AREA_H - 1, ox + AREA_W, oy + AREA_H, Draw.ALERT);
            g.fill(ox, oy, ox + 1, oy + AREA_H, Draw.ALERT);
            g.fill(ox + AREA_W - 1, oy, ox + AREA_W, oy + AREA_H, Draw.ALERT);
        }
    }

    // The rotor sprite and the sample tube in its marked bucket (an orange cap).
    void rotor(GuiGraphics g, double degrees) {
        g.pose().pushPose();
        g.pose().mulPose(Axis.ZP.rotationDegrees((float) degrees));
        g.pose().scale(0.72f, 0.72f, 1f);
        Draw.image(g, Draw.ROTOR, -48, -48, 96, 96);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float) BUCKET));
        g.fill(-3, -48 + 6, 4, -48 + 22, 0xFF2A1A12);
        g.fill(-2, -48 + 7, 3, -48 + 21, 0xFFB0606A);
        g.fill(-3, -48 + 4, 4, -48 + 8, 0xFFF29A3A);
        g.fill(-2, -48 + 4, 3, -48 + 5, 0xFFFFD08A);
        g.pose().popPose();
    }

    // The test tube on its stand: the cloudy sample settles into three layers, one per good stop.
    void testTube(GuiGraphics g, int x, int y) {
        int w = 22, hh = 76, inner = hh - 10;
        g.fill(x - 14, y + hh + 2, x + w + 14, y + hh + 6, 0xFF3A4654);
        g.fill(x - 14, y + hh + 2, x + w + 14, y + hh + 3, 0xFF6C7C8C);
        g.fill(x - 1, y - 1, x + w + 1, y + hh, 0xFF0B1118);
        g.fill(x, y, x + w, y + hh - 6, 0x30D8E8F0);
        g.fill(x + 2, y + hh - 6, x + w - 2, y + hh, 0x30D8E8F0);
        int top = y + 10, bottom = y + hh - 2;
        // Bottom: dark red pellet. Top: clear yellow serum. Middle: a thin pale band. Between them, what is still mixed.
        int pellet = (int) (inner * 0.32 * layer[0]), serum = (int) (inner * 0.42 * layer[1]), band = (int) (inner * 0.1 * layer[2]);
        int mixTop = top + serum, mixBottom = bottom - pellet;
        if (layer[2] > 0) {
            int mid = (mixTop + mixBottom) / 2;
            mixTop = Math.max(mixTop, mid - band / 2);
            mixBottom = Math.min(mixBottom, mid + (band + 1) / 2);
        }
        if (serum > 0) g.fill(x + 2, top, x + w - 2, top + serum, 0xFFE8CF6A);
        if (mixBottom > mixTop) {
            int mix = layer[2] > 0 ? mix(0xFFB0606A, 0xFFEDE6D6, layer[2]) : 0xFFB0606A;
            g.fill(x + 2, mixTop, x + w - 2, mixBottom, mix);
            if (layer[2] < 1) {
                // Specks drifting in the cloudy part.
                for (int k = 0; k < 6; k++) {
                    int sy = mixTop + Math.floorMod(k * 13 + tic / 3, Math.max(1, mixBottom - mixTop)), sx = x + 4 + Math.floorMod(k * 7, w - 8);
                    g.fill(sx, sy, sx + 1, sy + 1, 0xFF7A2A34);
                }
            }
        }
        if (pellet > 0) g.fill(x + 2, bottom - pellet, x + w - 2, bottom, 0xFF6E1A26);
        if (pellet > 0) g.fill(x + 4, bottom, x + w - 4, bottom + 2, 0xFF6E1A26);
        // Glass highlight and cap.
        g.fill(x + 3, y + 4, x + 4, y + hh - 8, 0x60FFFFFF);
        g.fill(x - 2, y - 4, x + w + 2, y + 2, 0xFFF29A3A);
        g.fill(x - 2, y - 4, x + w + 2, y - 3, 0xFFFFD08A);
    }

    @Override
    String instruction() {
        return T.f("spinning_the_sample_sorts_itself");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("brake"), InputConstants.KEY_SPACE, Draw.OR, Draw.MOUSE)};
    }
}
