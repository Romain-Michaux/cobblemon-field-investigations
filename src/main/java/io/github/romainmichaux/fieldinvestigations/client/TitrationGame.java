package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Titration: a burette on its stand above a flask turning on a magnetic stirrer. The tap is opened with the wheel, from
// a slow drip to a running stream; the level goes down the graduations. Each arrival leaves a pink swirl in the flask
// that fades as the stirrer mixes it in, and the nearer the endpoint, the longer it takes to fade: that is what is read.
// The tap has inertia of a kind: a drop hanging at the tip still falls once closed, and a stream still in the air
// lands. Overshooting the endpoint by more than four drops spoils the titration. Three are needed, the later ones with
// a more concentrated solution that turns more abruptly. Each result is written on a card; at the end it is stamped
// and the three pink flasks stand in a row.
final class TitrationGame extends MiniGame {
    static final ResourceLocation BENCH = Draw.texture("pip_bench"), STAND = Draw.texture("tit_stand"), STIRRER = Draw.texture("tit_stirrer"),
            FLASK = Draw.texture("tit_flask");
    static final int TIP_X = 100, TIP_Y = 57, COL_TOP = 4, COL_BOT = 45, FX = 88, FY = 53, STIR_X = 78, STIR_Y = 85;
    static final int CARD_X = 170, CARD_Y = 8, CARD_W = 104, CARD_H = 96, ROUNDS = 3, MAX_FAULTS = 3, END = 70;
    static final double CAPACITY = 50, DROP = 0.1, TOL = 0.45;
    static final double[] WINDOW = {3.0, 2.6, 2.2};
    static final int[] DURATION = {700, 650, 600};
    static final int PINK = 0xFFE0508C, WATER = 0xFFE8EEF2;
    // The flask's inside, row by row from its liquid's surface: left and right edge.
    static final int[][] LIQUID = {{4, 19}, {4, 19}, {3, 20}, {3, 20}, {2, 21}, {2, 21}, {1, 22}, {1, 22}, {1, 22}, {1, 22}, {2, 21}, {3, 20}};
    static final int LIQ_TOP = 20, LENS_X = 146, LENS_Y = 50, LENS_R = 19;
    final Random h;
    final List<double[]> swirls = new ArrayList<>(); // angle, radius, life, max life
    final List<double[]> falling = new ArrayList<>(); // amount, ticks to land, is drop
    final List<Double> results = new ArrayList<>();
    double endpoint, poured, open, hanging, angle;
    int round, faults, time, duration, pause, flash, tic, low, hangTicks, settled, ending = -1, messageTicks, messageColour;
    boolean turned;
    String message = "";

    TitrationGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        endpoint = 12 + h.nextDouble() * 22;
        low = (int) Math.floor(endpoint - 3 - h.nextDouble() * 2.5);
        poured = 0;
        open = 0;
        hanging = 0;
        hangTicks = 0;
        settled = 0;
        turned = false;
        swirls.clear();
        falling.clear();
        duration = time = DURATION[Math.min(round, ROUNDS - 1)];
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 46;
    }

    double flow() {
        return open <= 0 ? 0 : 0.003 + open * open * 0.18;
    }

    @Override
    void tick() {
        tic++;
        angle += 0.12;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        for (int k = swirls.size() - 1; k >= 0; k--) {
            double[] s = swirls.get(k);
            s[0] += 0.09 + 0.05 * (1 - s[2] / s[3]);
            if (--s[2] <= 0) swirls.remove(k);
        }
        if (ending >= 0) {
            if (ending == 30) Draw.beep(Sounds.PAPER, 1.0f, 0.6f);
            if (++ending >= END) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (finished) return;
        if (pause > 0) {
            if (--pause == 0) newRound();
            return;
        }
        if (--time <= 0) {
            fault(T.f("too_slow"));
            return;
        }
        hurryTicks(time, duration);
        pourTick();
        // Judged once everything has landed with the tap closed: a clean endpoint, or still short.
        if (turned && open == 0 && falling.isEmpty() && hanging < DROP / 2) {
            if (++settled >= 16) success();
        } else {
            settled = 0;
        }
    }

    void pourTick() {
        double f = flow();
        if (f > 0.06) {
            // A running stream: it goes straight down.
            if (hanging > 0) {
                f += hanging;
                hanging = 0;
            }
            falling.add(new double[] {f, 4, 0});
        } else if (f > 0) {
            hanging += f;
            hangTicks = 0;
            if (hanging >= DROP) drip();
        } else if (hanging >= DROP / 2 && ++hangTicks >= 12) {
            drip();
        }
        for (int k = falling.size() - 1; k >= 0; k--) {
            double[] d = falling.get(k);
            if (--d[1] > 0) continue;
            falling.remove(k);
            land(d[0], d[2] > 0);
        }
    }

    void drip() {
        falling.add(new double[] {hanging, 7, 1});
        hanging = 0;
        hangTicks = 0;
    }

    // Liquid reaching the flask: a pink swirl that lingers longer the nearer the endpoint; past it, pink for good.
    void land(double amount, boolean drop) {
        poured += amount;
        if (drop) Draw.beep(Sounds.DROP, 1.1f, 0.4f);
        double d = endpoint - poured, w = WINDOW[Math.min(round, ROUNDS - 1)];
        if (d <= 0) {
            if (!turned) {
                turned = true;
                Draw.beep(Sounds.CHIME, 1.2f, 0.5f);
            }
            if (-d > TOL) fault(T.f("endpoint_overshot"));
            return;
        }
        if (d < w && (drop || tic % 2 == 0)) {
            double life = 6 + (1 - d / w) * 110;
            swirls.add(new double[] {h.nextDouble() * Math.PI * 2, 0.3 + h.nextDouble() * 0.7, life, life});
        }
    }

    void success() {
        results.add(poured);
        say(T.f("clean_endpoint"), Draw.GREEN);
        Draw.note(1f + round * 0.25f);
        if (++round >= ROUNDS) {
            ending = 0;
        } else {
            pause = 40;
        }
    }

    void fault(String reason) {
        say(reason, Draw.ALERT);
        flash = 8;
        open = 0;
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
        else pause = 40;
    }

    void turn(double amount) {
        if (finished || pause > 0 || ending >= 0) return;
        double before = open;
        open = Math.max(0, Math.min(1, Math.round((open + amount) * 40) / 40.0));
        if (open != before) Draw.beep(Sounds.KNOB, 1f + (float) open * 0.5f, 0.4f);
    }

    @Override
    void scrolled(double amount) {
        turn(amount * (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0.025 : 0.1));
    }

    @Override
    boolean keyPressed(int key) {
        if (key == InputConstants.KEY_UP) turn(0.1);
        else if (key == InputConstants.KEY_DOWN) turn(-0.1);
        else return false;
        return true;
    }

    @Override
    void click(double mx, double my) {
        if (open > 0) turn(-1);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        Draw.image(g, BENCH, ax, ay, AREA_W, AREA_H);
        Draw.image(g, STAND, ax + 64, ay + 7, 35, 98);
        drawBurette(g, ax, ay);
        Draw.image(g, STIRRER, ax + STIR_X, ay + STIR_Y, 44, 21);
        double pink = turned || ending >= 0 ? 1 : pinkness();
        drawFlask(g, ax + FX, ay + FY, pink, true);
        drawLens(g, ax + LENS_X, ay + LENS_Y, pink);
        // The flasks already titrated, in a row on the bench.
        for (int k = 0; k < results.size(); k++) drawSmallFlask(g, ax + 128 + k * 13, ay + 88);
        drawCard(g, ax + CARD_X, ay + CARD_Y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        g.fill(ax + 2, ay + 108, ax + 66, ay + 116, 0xC0101418);
        timeGauge(g, ax + 5, ay + 109, 58, pause > 0 || ending >= 0 ? 0 : time / (double) duration);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 84 - w / 2 - 4, ay + 2, ax + 84 + w / 2 + 4, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 84 - w / 2, ay + 3, messageColour, false);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        g.pose().popPose();
    }

    // How pink the flask looks between arrivals: the lingering swirls tint it a little.
    double pinkness() {
        double p = 0;
        for (double[] s : swirls) p += s[2] / s[3] * Math.min(1, s[3] / 60);
        return Math.min(0.22, p * 0.06);
    }

    void drawBurette(GuiGraphics g, int ax, int ay) {
        int x0 = ax + TIP_X - 4, x1 = ax + TIP_X + 4;
        // The glass column, the titrant in it going down, and the graduations every millilitre (longer every five).
        g.fill(x0, ay + COL_TOP, x1, ay + COL_BOT, 0x50D8E8F0);
        int level = ay + COL_TOP + (int) Math.round(Math.min(CAPACITY, poured) / CAPACITY * (COL_BOT - COL_TOP));
        g.fill(x0 + 1, level, x1 - 1, ay + COL_BOT, 0xA0BFD8F0);
        g.fill(x0 + 1, level, x1 - 1, level + 1, 0xFFE8F4FF);
        for (int v = 0; v <= 50; v += 5) {
            int y = ay + COL_TOP + (int) Math.round(v / CAPACITY * (COL_BOT - COL_TOP));
            g.fill(x1 - (v % 10 == 0 ? 5 : 3), y, x1 - 1, y + 1, 0xFF33475C);
        }
        g.fill(x0 - 1, ay + COL_TOP - 1, x0, ay + COL_BOT, 0xFF6F879E);
        g.fill(x1, ay + COL_TOP - 1, x1 + 1, ay + COL_BOT, 0xFF6F879E);
        g.fill(x0 - 2, ay + COL_TOP - 2, x1 + 2, ay + COL_TOP, 0xFF6F879E);
        // The tap: its body, a key turning from across (closed) to along the tube (open), and the thin tip below.
        int ty = ay + COL_BOT;
        g.fill(ax + TIP_X - 2, ty, ax + TIP_X + 2, ty + 9, 0xFFB8C8D4);
        g.fill(ax + TIP_X - 1, ty, ax + TIP_X + 1, ty + 9, 0x80D8E8F0);
        g.fill(ax + TIP_X - 1, ty + 9, ax + TIP_X + 1, ay + TIP_Y, 0xFFB8C8D4);
        g.pose().pushPose();
        g.pose().translate(ax + TIP_X, ty + 4.5f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) (open * 90)));
        g.fill(-7, -1, 7, 1, 0xFF2A6FB0);
        g.fill(-8, -2, -5, 2, 0xFF2A6FB0);
        g.fill(5, -2, 8, 2, 0xFF2A6FB0);
        g.pose().popPose();
        g.fill(ax + TIP_X - 2, ty + 3, ax + TIP_X + 2, ty + 6, 0xFFE8EEF2);
        // What leaves the tip: a hanging drop swelling, drops falling, or a thin stream.
        int surface = ay + FY + LIQ_TOP;
        if (hanging > 0) {
            int r = hanging > DROP * 0.6 ? 2 : 1;
            g.fill(ax + TIP_X - r / 2 - 1 + 1, ay + TIP_Y, ax + TIP_X + r / 2 + 1, ay + TIP_Y + r + 1, 0xE0D8ECFF);
        }
        for (double[] d : falling) {
            if (d[2] > 0) {
                int y = ay + TIP_Y + (int) ((7 - d[1]) / 7 * (surface - ay - TIP_Y));
                g.fill(ax + TIP_X - 1, y, ax + TIP_X + 1, y + 2, 0xE0D8ECFF);
            }
        }
        if (flow() > 0.06) g.fill(ax + TIP_X, ay + TIP_Y, ax + TIP_X + 1, surface, 0xC0D8ECFF);
    }

    void drawFlask(GuiGraphics g, int x, int y, double pink, boolean swirling) {
        Draw.image(g, FLASK, x, y, 24, 34);
        int colour = mix(WATER, PINK, pink);
        for (int r = 0; r < LIQUID.length; r++) {
            int yy = y + LIQ_TOP + r;
            g.fill(x + LIQUID[r][0] + 1, yy, x + LIQUID[r][1], yy + 1, 0xB0 << 24 | (colour & 0xFFFFFF));
        }
        // The swirls, turning with the stirrer, fading as they mix in.
        if (swirling && !turned) {
            double cx = x + 12, cy = y + LIQ_TOP + 6;
            for (double[] s : swirls) {
                double k = s[2] / s[3];
                int a = (int) (0xE0 * Math.min(1, k * 1.4));
                for (int t = 0; t < 3; t++) {
                    double an = s[0] - t * 0.35;
                    int px = (int) Math.round(cx + Math.cos(an) * 9 * s[1]), py = (int) Math.round(cy + Math.sin(an) * 3.5 * s[1]);
                    g.fill(px, py, px + 2, py + 1, (a * (3 - t) / 3) << 24 | (PINK & 0xFFFFFF));
                }
            }
        }
        // The stir bar, spinning at the bottom.
        int dx = (int) Math.round(Math.abs(Math.cos(angle * 2.5)) * 4);
        g.fill(x + 12 - dx, y + LIQ_TOP + 9, x + 13 + dx, y + LIQ_TOP + 10, 0xFFF4F4F4);
    }

    // A magnifier on the flask: the liquid enlarged, so the swirls can be watched as they fade.
    void drawLens(GuiGraphics g, int cx, int cy, double pink) {
        int fx = ox + FX + 22, fy = oy + FY + LIQ_TOP + 4;
        for (int k = 0; k <= 6; k++) {
            int x = fx + (cx - LENS_R - fx) * k / 6, y = fy + (cy - fy) * k / 6;
            g.fill(x, y, x + 1, y + 1, 0xFF33475C);
        }
        int colour = mix(WATER, PINK, pink);
        for (int dy = -LENS_R - 2; dy <= LENS_R + 2; dy++) {
            int w = (int) Math.sqrt(Math.max(0, (LENS_R + 2) * (LENS_R + 2) - dy * dy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, 0xFF33475C);
        }
        for (int dy = -LENS_R; dy <= LENS_R; dy++) {
            int w = (int) Math.sqrt(Math.max(0, LENS_R * LENS_R - dy * dy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, colour);
        }
        if (!turned && ending < 0) {
            for (double[] sw : swirls) {
                double k = sw[2] / sw[3];
                int a = (int) (0xF0 * Math.min(1, k * 1.4));
                for (int t = 0; t < 5; t++) {
                    double an = sw[0] - t * 0.3;
                    int px = (int) Math.round(cx + Math.cos(an) * 15 * sw[1]), py = (int) Math.round(cy + Math.sin(an) * 9 * sw[1]);
                    int sz = 4 - t / 2;
                    g.fill(px - sz / 2, py - 1, px + sz / 2 + 1, py + 2, (a * (5 - t) / 5) << 24 | (PINK & 0xFFFFFF));
                }
            }
        }
        // The stir bar spinning, seen close, and the glint on the glass.
        int dx = (int) Math.round(Math.cos(angle * 2.5) * 9);
        g.fill(cx - Math.abs(dx), cy + 12, cx + Math.abs(dx) + 1, cy + 14, 0xFFF4F4F4);
        g.fill(cx - 12, cy - 13, cx - 7, cy - 12, 0xA0FFFFFF);
        g.fill(cx - 14, cy - 11, cx - 12, cy - 7, 0xA0FFFFFF);
    }

    void drawSmallFlask(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y + 5, x + 9, y + 10, 0xFF33475C);
        g.fill(x + 3, y + 5, x + 8, y + 9, PINK);
        g.fill(x + 1, y + 9, x + 10, y + 12, 0xFF33475C);
        g.fill(x + 2, y + 9, x + 9, y + 11, PINK);
        g.fill(x + 4, y, x + 7, y + 5, 0xFFB8C8D4);
    }

    String ml(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v) + " mL";
    }

    void drawCard(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, 0xFF8A7A62);
        g.fill(x, y, x + CARD_W, y + CARD_H, 0xFFF4F0E6);
        for (int k = 1; k < 9; k++) g.fill(x + 3, y + 3 + k * 10, x + CARD_W - 3, y + 4 + k * 10, 0x30406080);
        var f = Draw.textFont();
        int tx = x + 5, ty = y + 4;
        g.drawString(f, T.f("titration") + Math.min(ROUNDS, round + 1) + " / " + ROUNDS, tx, ty, Draw.INK, false);
        if (ending < 0) {
            g.drawString(f, T.f("expected") + low + "–" + (low + 7) + " mL", tx, ty + 10, Draw.INK_LIGHT, false);
            g.drawString(f, T.f("poured") + ml(poured), tx, ty + 20, Draw.INK, false);
            String tap = T.f("tap");
            g.drawString(f, tap, tx, ty + 30, Draw.INK, false);
            int gx = tx + f.width(tap) + 2, gw = CARD_W - (gx - x) - 6;
            g.fill(gx, ty + 31, gx + gw, ty + 37, 0xFF8A7A62);
            g.fill(gx + 1, ty + 32, gx + 1 + (int) ((gw - 2) * open), ty + 36, 0xFF2A6FB0);
        }
        g.drawString(f, T.f("mistakes") + faults + " / " + MAX_FAULTS, tx, ty + 40, faults > 0 ? Draw.INK_RED : Draw.INK_LIGHT, false);
        for (int k = 0; k < results.size(); k++) {
            g.drawString(f, (k + 1) + " : " + ml(results.get(k)), tx, ty + 52 + k * 10, Draw.INK, false);
        }
        if (ending >= 30) stamp(g, x + CARD_W - 28, y + CARD_H - 18);
    }

    void stamp(GuiGraphics g, int cx, int cy) {
        String s = T.f("titrated");
        int w = Draw.textFont().width(s) + 12;
        double k = Math.min(1, (ending - 30) / 6.0);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 300);
        float sc = (float) (1.6 - 0.6 * k);
        g.pose().scale(sc, sc, 1);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8));
        g.fill(-w / 2 - 2, -9, w / 2 + 2, 9, 0xFF2E7D32);
        g.fill(-w / 2, -7, w / 2, 7, 0xF0F4F0E6);
        g.drawString(Draw.textFont(), s, -Draw.textFont().width(s) / 2, -4, 0xFF2E7D32, false);
        g.pose().popPose();
    }

    static int mix(int a, int b, double k) {
        k = Math.max(0, Math.min(1, k));
        int r = 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            int ca = (a >> sh) & 0xFF, cb = (b >> sh) & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * k)) << sh;
        }
        return r;
    }

    @Override
    String instruction() {
        return T.f("open_the_tap_with_the");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("wheel_open_the_tap"), Draw.MOUSE), new Control(T.f("click_close"), Draw.MOUSE)};
    }
}
