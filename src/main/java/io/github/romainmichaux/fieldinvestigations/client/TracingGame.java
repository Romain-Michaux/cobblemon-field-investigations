package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tracing (scratch investigation): three claw gouges run down a trunk. Their cast is taken with silicone: the click
// held squeezes the cartridge and the paste runs into the groove under the nozzle. The flow builds up and dies down with
// a lag, so it goes on a moment after letting go. Moving too slowly overflows (a blob on the bark, paste lost); moving
// too fast leaves the bead thin, to be gone over again. The cartridge holds a little over half again what the gouges
// take. Once the three are filled, the set casts are peeled off and laid side by side.
final class TracingGame extends MiniGame {
    static final ResourceLocation BARK = Draw.texture("trace_bark"), GUN = Draw.texture("trace_gun");
    static final int GOUGES = 3, N = 42, DURATION = 1100, END = 80, REACH = 6;
    static final double GOOD = 0.6, OVER = 2.4, CARTRIDGE = GOUGES * N * 1.6;
    final double[][] px = new double[GOUGES][N], py = new double[GOUGES][N], fill = new double[GOUGES][N];
    final List<double[]> blobs = new ArrayList<>(); // x, y, size
    double flow, paste = CARTRIDGE, mx = -100, my = -100;
    int time = DURATION, tic, ending = -1, overflows, messageTicks, messageColour;
    String message = "";

    TracingGame(Random h) {
        double x0 = 80 + h.nextInt(50), lean = 22 + h.nextInt(16), bend = 3 + h.nextDouble() * 4, phase = h.nextDouble() * 6.28;
        for (int k = 0; k < GOUGES; k++) {
            double gx = x0 + k * (15 + h.nextInt(4)), top = 14 + h.nextInt(8) + (k == 1 ? -4 : 0), len = 74 + h.nextInt(12) + (k == 1 ? 8 : 0);
            for (int i = 0; i < N; i++) {
                double t = i / (double) (N - 1);
                px[k][i] = gx + t * lean + Math.sin(t * Math.PI * 1.5 + phase) * bend;
                py[k][i] = top + t * len;
            }
        }
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 30;
    }

    double done(int k) {
        int n = 0;
        for (int i = 0; i < N; i++) if (fill[k][i] >= GOOD) n++;
        return n / (double) N;
    }

    double total() {
        double s = 0;
        for (int k = 0; k < GOUGES; k++) s += done(k);
        return s / GOUGES;
    }

    @Override
    void tick() {
        tic++;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending == 10 || ending == 22 || ending == 34) Draw.beep(Sounds.PAPER, 1.3f, 0.5f);
            if (++ending >= END) {
                perfect = overflows <= 2 && time > DURATION / 3;
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
        // The paste lags the trigger: it takes a moment to come, and a moment to stop.
        boolean squeezing = mouseDown && paste > 0 && Draw.in(mx, my, ox, oy, AREA_W, AREA_H);
        flow = squeezing ? Math.min(1, flow + 0.12) : Math.max(0, flow - 0.14);
        if (flow > 0 && paste > 0) deposit(Math.min(paste, flow * 0.32));
        if (total() >= 0.9 && allDone()) {
            ending = 0;
            Draw.note(1.5f);
            return;
        }
        if (paste <= 0 && flow <= 0) end(false);
    }

    boolean allDone() {
        for (int k = 0; k < GOUGES; k++) if (done(k) < 0.9) return false;
        return true;
    }

    // The paste goes into the groove points under the nozzle, nearest first; with no groove there, it falls on the bark.
    void deposit(double amount) {
        paste -= amount;
        if (tic % 4 == 0) Draw.beep(Sounds.PLASTER, 1.3f + (float) flow * 0.3f, 0.25f);
        double x = mx - ox, y = my - oy, weights = 0;
        for (int k = 0; k < GOUGES; k++) for (int i = 0; i < N; i++) weights += weight(k, i, x, y);
        if (weights == 0) {
            spill(x, y, amount);
            return;
        }
        for (int k = 0; k < GOUGES; k++) {
            for (int i = 0; i < N; i++) {
                double w = weight(k, i, x, y);
                if (w == 0) continue;
                fill[k][i] += amount * w / weights;
                if (fill[k][i] > OVER) {
                    spill(px[k][i] + 3, py[k][i], fill[k][i] - OVER);
                    fill[k][i] = OVER;
                }
            }
        }
    }

    double weight(int k, int i, double x, double y) {
        double d = Math.hypot(px[k][i] - x, py[k][i] - y);
        return d < REACH ? 1 - d / REACH : 0;
    }

    void spill(double x, double y, double amount) {
        for (double[] b : blobs) {
            if (Math.hypot(b[0] - x, b[1] - y) < 4) {
                b[2] += amount;
                return;
            }
        }
        blobs.add(new double[] {x, y, amount});
        overflows++;
        say(T.f("overflowing"), Draw.ALERT);
        Draw.note(0.6f);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy;
        g.setColor(0.8f, 0.78f, 0.76f, 1f);
        Draw.image(g, BARK, ax, ay, AREA_W, AREA_H);
        g.setColor(1f, 1f, 1f, 1f);
        double lift = ending < 0 ? 0 : Math.min(1, ending / 30.0);
        for (int k = 0; k < GOUGES; k++) {
            // The gouge: torn wood, pale at its lips, dark in the hollow.
            for (int i = 0; i < N - 1; i++) {
                Draw.segment(g, ax + px[k][i] - 1, ay + py[k][i], ax + px[k][i + 1] - 1, ay + py[k][i + 1], 9, 0xFFF0CC90);
            }
            for (int i = 0; i < N - 1; i++) {
                Draw.segment(g, ax + px[k][i], ay + py[k][i], ax + px[k][i + 1], ay + py[k][i + 1], 5, 0xFF1E0E04);
            }
        }
        // The silicone: a bead as thick as what went in, white once enough, pale and thin until then. At the end the casts
        // come off and turn to lie side by side.
        for (int k = 0; k < GOUGES; k++) {
            g.pose().pushPose();
            if (lift > 0) {
                double e = lift * lift * (3 - 2 * lift);
                g.pose().translate(e * (-40 + k * 4), -e * 4, 0);
            }
            for (int i = 0; i < N - 1; i++) {
                double f = Math.min(fill[k][i], fill[k][i + 1]);
                if (f <= 0.05) continue;
                int w = f >= GOOD ? 3 : f >= 0.35 ? 2 : 1;
                int c = f >= GOOD ? (f > 1.35 ? 0xFFFFFFFF : 0xFFE8ECEE) : 0xA0D8DEE2;
                Draw.segment(g, ax + px[k][i], ay + py[k][i], ax + px[k][i + 1], ay + py[k][i + 1], w, c);
            }
            g.pose().popPose();
        }
        for (double[] b : blobs) {
            int r = (int) Math.min(5, 1 + b[2] * 4);
            int bx = ax + (int) b[0], by = ay + (int) b[1];
            g.fill(bx - r, by - r / 2, bx + r + 1, by + r / 2 + 2, 0xFFD8DEE2);
            g.fill(bx - r + 1, by - r / 2, bx, by - r / 2 + 1, 0xFFFFFFFF);
        }
        if (ending > 34) {
            String s = T.f("cast_taken");
            int w = Draw.textFont().width(s);
            g.fill(ax + 140 - w / 2 - 4, ay + 104, ax + 140 + w / 2 + 4, ay + 114, 0xC0101418);
            g.drawString(Draw.textFont(), s, ax + 140 - w / 2, ay + 105, Draw.GREEN, false);
        }
        // The cartridge at the cursor, its nozzle on it; paste beading at the tip while it flows.
        if (!finished && ending < 0 && Draw.in(x, y, ax, ay, AREA_W, AREA_H)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            if (flow > 0.05) g.fill(x - 1, y - 1, x + 2, y + 1 + (int) (flow * 2), 0xFFF4F6F8);
            Draw.image(g, GUN, x, y - 35, 36, 35);
            g.pose().popPose();
        }
        drawHud(g, ax, ay);
    }

    void drawHud(GuiGraphics g, int ax, int ay) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        String left = T.f("cast") + (int) Math.round(total() * 100) + " %";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        String right = T.f("cartridge");
        int rw = Draw.textFont().width(right), gx = ax + AREA_W - 48;
        g.fill(gx - rw - 8, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, gx - rw - 4, ay + 3, Draw.TEXT, false);
        double k = Math.max(0, paste / CARTRIDGE);
        g.fill(gx, ay + 4, gx + 42, ay + 10, Draw.EDGE);
        g.fill(gx + 1, ay + 5, gx + 1 + (int) (40 * k), ay + 9, k < 0.25 ? Draw.ALERT : 0xFFE8ECEE);
        g.fill(ax + 2, ay + 108, ax + 66, ay + 116, 0xC0101418);
        timeGauge(g, ax + 5, ay + 109, 58, time / (double) DURATION);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 140 - w / 2 - 4, ay + 2, ax + 140 + w / 2 + 4, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 140 - w / 2, ay + 3, messageColour, false);
        }
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("hold_the_click_to_squeeze");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("hold_squeeze_the_silicone"), Draw.MOUSE)};
    }
}
