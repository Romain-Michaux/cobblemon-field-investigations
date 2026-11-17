package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;

// Cry filtering (mist investigation): a spectrogram scrolls down the whole area, frequency across, time downwards. The cry is
// a bright line that drifts and comes in bursts; another creature hums a fainter, unbroken line that sometimes crosses it.
// The filter is a band the mouse slides across and the wheel narrows: narrow isolates the cry faster and holds through gusts
// of noise, but is easier to lose. Only the cry fills the gauge; once isolated, the noise clears around its signature.
final class FilterGame extends MiniGame {
    static final int DURATION = 460, REQUIRED = 140, BINS = 68, ROWS = 46, CELL_W = 4, CELL_H = 2, SX = 4, SY = 15, END = 50;
    static final double[] WIDTHS = {0.05, 0.08, 0.11, 0.15, 0.2};
    final double p, q;
    final Random h, noise = new Random();
    final float[][] history = new float[ROWS][BINS], cryRows = new float[ROWS][BINS];
    int time = DURATION, tic, level = 2, head, gustTicks, nextGust = 110, ending;
    double acquired, cx = -1;
    boolean onCry, onDecoy, pulse;

    FilterGame(Random h) {
        this.h = h;
        p = h.nextDouble() * 6;
        q = h.nextDouble() * 6;
        noise.setSeed(h.nextLong());
    }

    double target() {
        return 0.5 + 0.36 * Math.sin(tic * 0.021 + p) + 0.06 * Math.sin(tic * 0.09);
    }

    double decoy() {
        return 0.5 + 0.38 * Math.sin(tic * 0.016 + q + 2.4) + 0.05 * Math.sin(tic * 0.11 + 1);
    }

    double width() {
        return WIDTHS[level];
    }

    double setting() {
        return cx < 0 ? 0.5 : Math.max(0, Math.min(1, (cx - ox - SX) / (double) (BINS * CELL_W)));
    }

    // The cry comes in bursts: short calls with silences between them, a little irregular.
    boolean pulseAt(int t) {
        int phase = (t + (int) (p * 7)) % 17;
        return phase < 9 || phase == 11 || phase == 12;
    }

    @Override
    void tick() {
        tic++;
        if (ending > 0) {
            if (tic % 2 == 0) pushRow(true);
            if (--ending == 0) end(true);
            return;
        }
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        // Gusts: the noise floor rises for a while; only a narrow band still gets the cry through cleanly.
        if (gustTicks > 0) gustTicks--;
        else if (--nextGust <= 0) {
            gustTicks = 45;
            nextGust = 130 + h.nextInt(90);
            Draw.beep(Sounds.SCAN, 0.4f, 0.5f);
        }
        pulse = pulseAt(tic);
        double s = setting(), w = width();
        onCry = Math.abs(s - target()) < w / 2;
        onDecoy = Math.abs(s - decoy()) < w / 2;
        if (onCry) {
            if (pulse) {
                double narrow = (0.2 - w) / 0.15, gain = 0.45 + narrow * 1.15;
                if (onDecoy) gain *= 0.5;
                if (gustTicks > 0) gain *= 1 - w * 3.5;
                acquired += gain;
                if (tic % 3 == 0) Draw.beep(Sounds.SCAN, (float) (1.0 + narrow * 0.8 + acquired / REQUIRED * 0.4), 0.45f);
            }
        } else {
            acquired = Math.max(0, acquired - 0.5);
            if (onDecoy && tic % 8 == 0) Draw.beep(Sounds.SCAN, 0.55f, 0.25f);
        }
        if (tic % 2 == 0) pushRow(false);
        if (acquired >= REQUIRED) {
            perfect = true;
            ending = END;
            Draw.beep(Sounds.IDENTIFIED, 1f, 0.8f);
        }
    }

    // A new row at the top of the spectrogram: noise, the decoy's hum, the cry when it calls.
    void pushRow(boolean clearing) {
        head = (head + ROWS - 1) % ROWS;
        float[] row = history[head], cry = cryRows[head];
        double t = target(), d = decoy(), fade = clearing ? ending / (double) END : 1;
        double floor = gustTicks > 0 ? 0.6 : 0.24;
        boolean calling = pulse || clearing;
        for (int b = 0; b < BINS; b++) {
            double f = (b + 0.5) / BINS, v = noise.nextDouble() * floor * fade;
            if (noise.nextInt(40) == 0) v += 0.35 * fade;
            v += 0.4 * Math.exp(-Math.pow((f - d) * BINS / 1.3, 2)) * fade;
            double c = calling ? Math.exp(-Math.pow((f - t) * BINS / 1.1, 2)) : 0;
            row[b] = (float) Math.min(1, v + c);
            cry[b] = (float) c;
        }
    }

    @Override
    void scrolled(double amount) {
        if (amount != 0) narrow(amount > 0 ? -1 : 1);
    }

    @Override
    boolean keyPressed(int key) {
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_W || key == InputConstants.KEY_Z) narrow(-1);
        else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_S) narrow(1);
        else return false;
        return true;
    }

    void narrow(int d) {
        int next = Math.max(0, Math.min(WIDTHS.length - 1, level + d));
        if (next != level && ending == 0) {
            level = next;
            Draw.beep(Sounds.KNOB, 1.4f - level * 0.12f, 0.5f);
        }
    }

    static int heat(double v) {
        if (v < 0.5) return mix(0xFF0A1418, 0xFF1F6A78, v * 2);
        return mix(0xFF1F6A78, 0xFFD8FFE8, (v - 0.5) * 2);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        cx = mx;
        int ax = ox, ay = oy, sw = BINS * CELL_W, sh = ROWS * CELL_H;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF0B1116);
        g.fill(ax + SX - 1, ay + SY - 1, ax + SX + sw + 1, ay + SY + sh + 1, Draw.EDGE);
        g.fill(ax + SX, ay + SY, ax + SX + sw, ay + SY + sh, 0xFF0A1418);
        double s = setting(), w = width();
        int b0 = (int) Math.floor((s - w / 2) * BINS), b1 = (int) Math.ceil((s + w / 2) * BINS);
        boolean done = ending > 0 || finished && success;
        // The spectrogram: inside the band the filter lets the cry through and damps the noise, outside it all dims.
        for (int r = 0; r < ROWS; r++) {
            float[] row = history[(head + r) % ROWS], cry = cryRows[(head + r) % ROWS];
            int y = ay + SY + r * CELL_H;
            for (int b = 0; b < BINS; b++) {
                double v = row[b];
                boolean in = b >= b0 && b < b1;
                if (done) v = Math.max(v, cry[b]);
                else if (in) v = cry[b] + (v - cry[b]) * (0.35 + w * 2.5);
                else v *= 0.72;
                if (v < 0.06) continue;
                int c = heat(v);
                if (done && cry[b] > 0.3) c = mix(c, 0xFFB8FFD0, cry[b]);
                g.fill(ax + SX + b * CELL_W, y, ax + SX + (b + 1) * CELL_W, y + CELL_H, c);
            }
        }
        // The filter band: two uprights, green while it holds the calling cry, amber on the hum.
        if (!done) {
            int x0 = ax + SX + Math.max(0, b0) * CELL_W, x1 = ax + SX + Math.min(BINS, b1) * CELL_W;
            int c = onCry ? Draw.GREEN : onDecoy ? Draw.YELLOW : 0xFF8FA3B5;
            g.fill(x0, ay + SY, x1, ay + SY + sh, (c & 0x00FFFFFF) | 0x18000000);
            g.fill(x0 - 1, ay + SY - 3, x0, ay + SY + sh + 3, c);
            g.fill(x1, ay + SY - 3, x1 + 1, ay + SY + sh + 3, c);
            g.fill(x0 - 1, ay + SY - 3, x1 + 1, ay + SY - 2, c);
        }
        // Counters: the cry isolated so far on the left, the band's state on the right; the time at the bottom.
        String left = T.f("cry_isolated_2") + " " + (int) Math.min(100, acquired * 100 / REQUIRED) + " %";
        text(g, left, ax + 4, ay + 3, onCry && pulse ? Draw.GREEN : Draw.TEXT);
        String state = done ? "" : gustTicks > 0 ? T.f("gust_of_wind") : onCry ? T.f("on_the_cry") : onDecoy ? T.f("not_this_one") : "";
        int sc = gustTicks > 0 ? ((tic / 6) % 2 == 0 ? Draw.ALERT : Draw.YELLOW) : onCry ? Draw.GREEN : Draw.YELLOW;
        if (!state.isEmpty()) text(g, state, ax + AREA_W - 4 - Draw.textFont().width(state), ay + 3, sc);
        if (done) Draw.centre(g, T.f("cry_isolated_3"), ax + AREA_W / 2, ay + 3, Draw.GREEN);
        g.fill(ax + SX, ay + AREA_H - 3, ax + SX + sw, ay + AREA_H - 1, Draw.EDGE);
        g.fill(ax + SX, ay + AREA_H - 3, ax + SX + (int) (sw * Math.min(1, acquired / REQUIRED)), ay + AREA_H - 1, Draw.GREEN);
        double part = time / (double) DURATION;
        g.fill(ax + SX, ay + 12, ax + SX + (int) (sw * part), ay + 13, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
    }

    @Override
    String instruction() {
        return T.f("in_the_mist_a_cry");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("frequency"), Draw.MOUSE), new Control(T.f("band_wheel"), Draw.ARROW_UP, Draw.ARROW_DOWN)};
    }
}
