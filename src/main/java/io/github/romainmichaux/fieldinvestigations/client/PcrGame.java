package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

// PCR cycles: a thermocycler is driven by hand. Holding heats the block, releasing lets it cool; the heater has inertia
// (the temperature keeps rising a little after release) so it has to be let go early. Each cycle follows the real path:
// 95° to separate the strands, 55° for the primers to bind, 72° to copy, each held for a while. A full cycle doubles
// the copies in the tube; going past 99° burns some of them. At the end the tube glows and the amplification curve is
// drawn, with the threshold it crosses.
final class PcrGame extends MiniGame {
    static final int CYCLES = 4, DURATION = 1000, HISTORY = 186, GX = 6, GY = 16, GH = 92, END = 100;
    static final double AMBIENT = 22, BURN = 99, ZONE = 3;
    static final int[] TARGET = {95, 55, 72};
    static final int[] HOLD = {24, 30, 30};
    static final String[] STEP = {"separate", "bind", "copy"};
    static final int[] STEP_COLOURS = {0xFFE8543A, 0xFF4A90D9, 0xFFF2C93A};
    final Random h;
    final double[] history = new double[HISTORY];
    double temp = 60, power, copies = 1, held, popped;
    int cycle, step, time = DURATION, tic, flash, ending = -1, burns;
    boolean pressed, burning;

    PcrGame(Random h) {
        this.h = h;
        java.util.Arrays.fill(history, temp);
    }

    boolean heating() {
        return pressed || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_SPACE);
    }

    int holdTicks() {
        return (int) Math.round(HOLD[step] * (1 - cycle * 0.08));
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (popped > 0) popped = Math.max(0, popped - 0.05);
        if (ending >= 0) {
            if (++ending == 30) Draw.beep(Sounds.SCAN, 1.2f, 0.7f);
            if (ending >= END) {
                perfect = burns == 0;
                end(copies >= 8);
            }
            return;
        }
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        // The heater's power follows the click slowly; the block loses heat towards the room.
        power += ((heating() ? 1 : 0) - power) * 0.12;
        temp += power * 2.1 - (temp - AMBIENT) * 0.02;
        System.arraycopy(history, 1, history, 0, HISTORY - 1);
        history[HISTORY - 1] = temp;
        if (power > 0.3 && tic % 6 == 0) Draw.beep(Sounds.FLAME, 1.4f, 0.25f);
        // Past 99°, a quarter of the copies is lost (once per excursion).
        if (temp > BURN && !burning) {
            burning = true;
            burns++;
            copies *= 0.75;
            flash = 14;
            Draw.note(0.5f);
        } else if (temp < BURN - 3) {
            burning = false;
        }
        // The current step counts while the temperature stays in its band.
        if (Math.abs(temp - TARGET[step]) <= ZONE) {
            held++;
            if (tic % 8 == 0) Draw.beep(Sounds.TICK, 1f + step * 0.2f, 0.35f);
            if (held >= holdTicks()) {
                held = 0;
                Draw.beep(Sounds.CHIME, 1.1f + step * 0.2f, 0.6f);
                if (++step >= 3) {
                    step = 0;
                    copies *= 2;
                    popped = 1;
                    Draw.beep(Sounds.VIAL, 1.3f, 0.8f);
                    if (++cycle >= CYCLES) {
                        ending = 0;
                        Draw.beep(Sounds.IDENTIFIED, 1f, 0.8f);
                    }
                }
            }
        }
    }

    @Override
    void click(double x, double y) {
        pressed = true;
    }

    @Override
    void released(double x, double y) {
        pressed = false;
    }

    @Override
    boolean keyPressed(int key) {
        return key == InputConstants.KEY_SPACE;
    }

    int gy(double t) {
        // The scale runs from 30° to 105°, the useful range.
        return oy + GY + GH - (int) Math.round((Math.max(30, Math.min(105, t)) - 30) / 75 * GH);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF0E151C);
        g.fill(ax + GX - 1, ay + GY - 1, ax + GX + HISTORY + 1, ay + GY + GH + 1, flash > 0 ? Draw.ALERT : Draw.EDGE);
        g.fill(ax + GX, ay + GY, ax + GX + HISTORY, ay + GY + GH, 0xFF0A1016);
        if (ending >= 0) {
            drawCurve(g, ax, ay, pt);
        } else {
            drawGraph(g, ax, ay);
        }
        drawTube(g, ax + 214, ay + 18);
        String left = T.f("cycle") + Math.min(CYCLES, cycle + 1) + " / " + CYCLES, right = T.f("copies") + (int) Math.floor(copies);
        text(g, left, ax + 6, ay + 2, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 2, burns > 0 ? Draw.YELLOW : Draw.GREEN);
        double part = ending >= 0 ? 0 : time / (double) DURATION;
        g.fill(ax + 6, ay + 12, ax + 6 + (int) ((AREA_W - 12) * part), ay + 13, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
    }

    // The thermocycler's graph: the three bands, the current one bright with its hold filling, and the temperature trace.
    void drawGraph(GuiGraphics g, int ax, int ay) {
        for (int s = 0; s < 3; s++) {
            int y0 = gy(TARGET[s] + ZONE), y1 = gy(TARGET[s] - ZONE);
            boolean now = s == step;
            int c = STEP_COLOURS[s];
            g.fill(ax + GX, y0, ax + GX + HISTORY, y1 + 1, (c & 0x00FFFFFF) | (now ? 0x40000000 : 0x14000000));
            if (now) {
                int fill = (int) (HISTORY * held / holdTicks());
                g.fill(ax + GX, y1, ax + GX + fill, y1 + 1, c);
                String label = TARGET[s] + "° · " + T.f(STEP[s]);
                g.drawString(Draw.textFont(), label, ax + GX + 3, y1 + 2, c, false);
            }
        }
        // The burn line.
        int by = gy(BURN);
        for (int x = 0; x < HISTORY; x += 4) g.fill(ax + GX + x, by, ax + GX + x + 2, by + 1, 0x90E8543A);
        int prev = gy(history[0]);
        for (int x = 1; x < HISTORY; x++) {
            int y = gy(history[x]);
            int c = history[x] > BURN ? Draw.ALERT : mix(0xFF4A90D9, 0xFFE8543A, (history[x] - 40) / 60);
            g.fill(ax + GX + x, Math.min(prev, y), ax + GX + x + 1, Math.max(prev, y) + 1, c);
            prev = y;
        }
        // The current temperature, read out at the tip of the trace.
        String t = (int) Math.round(temp) + "°";
        int ty = Math.max(ay + GY, Math.min(ay + GY + GH - 9, gy(temp) - 4));
        g.fill(ax + GX + HISTORY - Draw.textFont().width(t) - 4, ty - 1, ax + GX + HISTORY, ty + 9, 0xC0101418);
        g.drawString(Draw.textFont(), t, ax + GX + HISTORY - Draw.textFont().width(t) - 2, ty, heating() ? Draw.ALERT : Draw.TEXT, false);
    }

    // The tube: its liquid, and one little double strand per copy (popping in when a cycle doubles them).
    void drawTube(GuiGraphics g, int x, int y) {
        int w = 26, hgt = 80;
        g.fill(x - 2, y - 4, x + w + 2, y, 0xFFB8C4D0);
        g.fill(x, y, x + w, y + hgt - 10, 0xFF5E6A76);
        g.fill(x + 1, y, x + w - 1, y + hgt - 10, 0xFF1A2430);
        for (int r = 0; r < 10; r++) {
            int half = (int) Math.round((w / 2.0 - 1) * Math.sqrt(1 - (r / 10.0) * (r / 10.0)));
            g.fill(x + w / 2 - half - 1, y + hgt - 10 + r, x + w / 2 + half + 1, y + hgt - 9 + r, 0xFF5E6A76);
            g.fill(x + w / 2 - half, y + hgt - 10 + r, x + w / 2 + half, y + hgt - 9 + r, 0xFF1A2430);
        }
        boolean glow = ending >= 0;
        int liquid = glow ? mix(0xFF2A6A78, 0xFF8CFAEC, 0.5 + 0.5 * Math.sin(tic * 0.2)) : 0xFF2A5A68;
        g.fill(x + 1, y + 20, x + w - 1, y + hgt - 10, liquid);
        int n = Math.min(16, (int) Math.floor(copies));
        for (int i = 0; i < n; i++) {
            int cx = x + 4 + (i % 2) * 10, cy = y + hgt - 18 - (i / 2) * 6;
            boolean fresh = popped > 0 && i >= n / 2;
            int c = fresh ? 0xFFFFFFFF : 0xFFC9F0E8;
            g.fill(cx, cy, cx + 8, cy + 1, c);
            g.fill(cx, cy + 3, cx + 8, cy + 4, c);
            for (int k = 1; k < 8; k += 2) g.fill(cx + k, cy + 1, cx + k + 1, cy + 3, Draw.BASE_COLOURS[(i + k) % 4]);
        }
        g.fill(x + 3, y + 2, x + 5, y + hgt - 12, 0x40FFFFFF);
    }

    // The result: the amplification curve rises in an S and crosses the threshold; fewer copies, a later crossing.
    void drawCurve(GuiGraphics g, int ax, int ay, float pt) {
        double t = Math.min(1, (ending + pt) / 60.0);
        double ct = 14 + (16 - Math.min(16, copies)) * 0.8;
        int threshold = ay + GY + GH - 30;
        for (int x = 0; x < HISTORY; x += 4) g.fill(ax + GX + x, threshold, ax + GX + x + 2, threshold + 1, 0xA0F2C93A);
        int prev = -1, crossX = -1;
        for (int x = 0; x < (int) (HISTORY * t); x++) {
            double cyc = x / (double) HISTORY * 40;
            double v = 1 / (1 + Math.exp(-(cyc - ct - 3) * 0.55));
            int y = ay + GY + GH - 4 - (int) Math.round(v * (GH - 12));
            if (prev >= 0) g.fill(ax + GX + x, Math.min(prev, y), ax + GX + x + 1, Math.max(prev, y) + 1, 0xFF8CFAEC);
            if (crossX < 0 && y <= threshold) crossX = x;
            prev = y;
        }
        if (crossX >= 0) {
            Draw.circle(g, ax + GX + crossX - 0.5, threshold - 0.5, 3, 1, Draw.YELLOW);
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            String v = String.format(java.util.Locale.ROOT, "Ct = %.1f", crossX / (double) HISTORY * 40);
            if (lang.startsWith("fr")) v = v.replace('.', ',');
            g.drawString(Draw.textFont(), v, ax + GX + crossX + 5, threshold - 11, Draw.YELLOW, false);
        }
        String label = copies >= 8 ? T.f("amplified") : T.f("too_few_copies");
        g.drawString(Draw.textFont(), label, ax + GX + 4, ay + GY + 3, copies >= 8 ? Draw.GREEN : Draw.ALERT, false);
    }

    @Override
    String instruction() {
        return T.f("hold_to_heat_release_to");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("heat_hold"), Draw.MOUSE, Draw.OR, InputConstants.KEY_SPACE)};
    }
}
