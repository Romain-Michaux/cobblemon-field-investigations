package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tweezers (egg investigation): fibres are woven into the hollow of a nest, each with a free end and the rest tucked
// in, running under a twig. A fibre is gripped by its free end, the click held, and drawn out by pulling. Pulled along
// its own line it slides out; pulled askew, it kinks at the exit and the tension rises, as it does when pulled too
// fast. Where its tail passes under the twig it snags, and only a gentle pull gets it through. Too much tension snaps
// it. Let go, it stays where it was left. A fibre fully out goes into the sample bag; four are needed, out of five.
final class TweezersGame extends MiniGame {
    static final ResourceLocation NEST = Draw.texture("sort_nest"), TWEEZERS = Draw.texture("tweezers"), BAG = Draw.texture("tw_bag");
    static final int FIBRES = 5, NEEDED = 4, P = 18, DURATION = 1200, END = 70, BAG_X = 252, BAG_Y = 78;
    static final double SPEED = 2.4, SNAG_SPEED = 0.9, KINK = 40;
    final double[][] fx = new double[FIBRES][P], fy = new double[FIBRES][P];
    final double[] length = new double[FIBRES], pulled = new double[FIBRES], twig = new double[FIBRES], endX = new double[FIBRES], endY = new double[FIBRES];
    final int[] colours = new int[FIBRES];
    final int[] state = new int[FIBRES]; // 0 in the nest, 1 bagged, 2 snapped
    final int[] flying = new int[FIBRES];
    int time = DURATION, tic, held = -1, bagged, snapped, ending = -1, flash, messageTicks, messageColour;
    double tension, mx = -100, my = -100, lastX, lastY;
    String message = "";

    TweezersGame(Random h) {
        int[] palette = {0xFFF2EEE4, 0xFFE8D8B0, 0xFFD8E4F0, 0xFFF0D8D0, 0xFFE4E8D0};
        for (int f = 0; f < FIBRES; f++) {
            colours[f] = palette[f];
            for (int attempt = 0; attempt < 200; attempt++) {
                double x = 34 + h.nextInt(170), y = 30 + h.nextInt(64), a = h.nextDouble() * Math.PI * 2, bend = (h.nextDouble() - 0.5) * 0.12;
                double step = (36 + h.nextInt(18)) / (double) (P - 1);
                boolean ok = true;
                for (int i = 0; i < P; i++) {
                    fx[f][i] = x;
                    fy[f][i] = y;
                    ok &= x > 14 && x < 226 && y > 18 && y < 108;
                    x += Math.cos(a) * step;
                    y += Math.sin(a) * step;
                    a += bend;
                }
                // Free ends apart from each other, so the one gripped is clear.
                for (int k = 0; k < f; k++) ok &= Math.hypot(fx[k][0] - fx[f][0], fy[k][0] - fy[f][0]) > 26;
                if (ok) {
                    length[f] = step * (P - 1);
                    break;
                }
            }
            twig[f] = length[f] * (0.55 + h.nextDouble() * 0.15);
            endX[f] = fx[f][0];
            endY[f] = fy[f][0];
        }
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 36;
    }

    // The point at the given distance along a fibre's path from its free end.
    double[] along(int f, double d) {
        double step = length[f] / (P - 1), t = Math.max(0, Math.min(P - 1, d / step));
        int i = Math.min(P - 2, (int) t);
        double k = t - i;
        return new double[] {fx[f][i] + (fx[f][i + 1] - fx[f][i]) * k, fy[f][i] + (fy[f][i + 1] - fy[f][i]) * k};
    }

    boolean snagging(int f) {
        double tail = length[f] - pulled[f];
        return tail <= twig[f] + 3 && tail >= twig[f] - 6;
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        for (int f = 0; f < FIBRES; f++) if (flying[f] > 0) flying[f]--;
        if (ending >= 0) {
            if (ending == 30) Draw.beep(Sounds.PAPER, 1.1f, 0.6f);
            if (++ending >= END) {
                perfect = snapped == 0;
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
        tension = Math.max(0, tension - 0.025);
        double x = mx - ox, y = my - oy;
        if (held >= 0 && !mouseDown) {
            held = -1;
            Draw.beep(Sounds.TWEEZERS, 1.8f, 0.3f);
        }
        if (held >= 0) pull(held, x, y);
        lastX = x;
        lastY = y;
    }

    void pull(int f, double x, double y) {
        double hx = fx[f][0], hy = fy[f][0];
        double out = Math.hypot(x - hx, y - hy);
        double moved = Math.hypot(x - lastX, y - lastY);
        // Askew: the angle between the pull and the fibre's own line at its exit.
        double tx = fx[f][0] - fx[f][1], ty = fy[f][0] - fy[f][1];
        double angle = out < 2 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ((x - hx) * tx + (y - hy) * ty) / (out * Math.hypot(tx, ty))))));
        double gained = Math.max(0, out - pulled[f]);
        double limit = snagging(f) ? SNAG_SPEED : SPEED;
        if (gained > limit) tension += (gained - limit) * 0.22;
        if (angle > KINK && moved > 0.3) tension += (angle - KINK) / 90 * 0.07;
        if (gained > 0) {
            pulled[f] = Math.min(length[f], out);
            if (tic % 3 == 0) Draw.beep(Sounds.RUB, 1.6f - (float) Math.min(0.6, tension), 0.25f);
        }
        endX[f] = x;
        endY[f] = y;
        if (snagging(f) && messageTicks == 0) say(T.f("snag_gently"), Draw.YELLOW);
        if (tension >= 1) {
            state[f] = 2;
            held = -1;
            tension = 0;
            snapped++;
            flash = 8;
            say(T.f("snapped"), Draw.ALERT);
            Draw.beep(Sounds.BRANCH, 1.6f, 0.6f);
            if (FIBRES - snapped < NEEDED) end(false);
            return;
        }
        if (pulled[f] >= length[f]) {
            state[f] = 1;
            flying[f] = 16;
            held = -1;
            tension = 0;
            Draw.beep(Sounds.PLUCK, 1.2f + bagged * 0.12f, 0.7f);
            if (++bagged >= NEEDED) ending = 0;
        }
    }

    @Override
    void click(double px, double py) {
        if (finished || ending >= 0) return;
        double x = px - ox, y = py - oy;
        int best = -1;
        double bd = 7;
        for (int f = 0; f < FIBRES; f++) {
            if (state[f] != 0) continue;
            double d = Math.hypot(endX[f] - x, endY[f] - y);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        if (best < 0) return;
        held = best;
        lastX = x;
        lastY = y;
        Draw.beep(Sounds.TWEEZERS, 1.4f, 0.5f);
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, NEST, ax, ay, AREA_W, AREA_H);
        for (int f = 0; f < FIBRES; f++) {
            if (state[f] == 1) continue;
            int c = state[f] == 2 ? 0xFF8A7A6A : f == held ? mix(colours[f], 0xFFE8543A, Math.min(1, tension)) : colours[f];
            // In the nest: the path from the exit to the tail, which moves up as the fibre is drawn out.
            double rest = length[f] - pulled[f];
            double[] prev = along(f, 0);
            for (double d = 2; d <= rest + 0.01; d += 2) {
                double[] p = along(f, Math.min(d, rest));
                Draw.segment(g, ax + prev[0], ay + prev[1], ax + p[0], ay + p[1], 1, c);
                prev = p;
            }
            // Out of the nest: straight from the exit to where the end was left.
            if (pulled[f] > 0.5 && state[f] == 0) Draw.segment(g, ax + fx[f][0], ay + fy[f][0], ax + endX[f], ay + endY[f], 1, c);
            // The twig it runs under, over it.
            double[] tw = along(f, twig[f]), tw2 = along(f, twig[f] + 2);
            double nx = -(tw2[1] - tw[1]), ny = tw2[0] - tw[0], nl = Math.max(0.01, Math.hypot(nx, ny));
            nx /= nl;
            ny /= nl;
            Draw.segment(g, ax + tw[0] - nx * 7, ay + tw[1] - ny * 7, ax + tw[0] + nx * 7, ay + tw[1] + ny * 7, 3, 0xFF6A4A2A);
            Draw.segment(g, ax + tw[0] - nx * 6, ay + tw[1] - ny * 6 - 1, ax + tw[0] + nx * 6, ay + tw[1] + ny * 6 - 1, 1, 0xFF9A7A4A);
            // The free end: a little tuft, pulsing while waiting to be gripped.
            if (state[f] == 0 && f != held) {
                int ex = ax + (int) endX[f], ey = ay + (int) endY[f];
                boolean near = Math.hypot(px - ex, py - ey) < 7;
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, near || (tic / 8 + f) % 3 == 0 ? 0xFFFFFFFF : c);
            }
        }
        drawBag(g, ax + BAG_X, ay + BAG_Y);
        // Fibres flying to the bag.
        for (int f = 0; f < FIBRES; f++) {
            if (flying[f] <= 0) continue;
            double k = 1 - flying[f] / 16.0;
            int x = (int) (ax + endX[f] + (BAG_X + 9 - endX[f]) * k), y = (int) (ay + endY[f] + (BAG_Y + 15 - endY[f]) * k - Math.sin(k * Math.PI) * 18);
            Draw.segment(g, x - 6, y - 3, x + 6, y + 3, 1, colours[f]);
        }
        // The tweezers, their tips on the cursor; squeezed shut while gripping.
        if (!finished && ending < 0 && Draw.in(px, py, ax, ay, AREA_W, AREA_H)) {
            g.pose().pushPose();
            g.pose().translate(px, py, 100);
            if (held >= 0) g.pose().scale(0.55f, 1f, 1f);
            Draw.image(g, TWEEZERS, -16, -64, 32, 64);
            g.pose().popPose();
        }
        drawHud(g, ax, ay);
    }

    void drawBag(GuiGraphics g, int x, int y) {
        Draw.image(g, BAG, x, y, 18, 31);
        for (int f = 0, n = 0; f < FIBRES; f++) {
            if (state[f] != 1 || flying[f] > 0) continue;
            Draw.segment(g, x + 4, y + 14 + n * 3, x + 14, y + 16 + n * 3 - (n % 2) * 3, 1, colours[f]);
            n++;
        }
        if (ending >= 30) {
            g.fill(x + 1, y + 3, x + 17, y + 5, 0xFF2A6FB0);
            String s = T.f("fibres_bagged");
            int w = Draw.textFont().width(s), cx = Math.min(x + 9, ox + AREA_W - w / 2 - 6);
            g.fill(cx - w / 2 - 4, y - 14, cx + w / 2 + 4, y - 4, 0xC0101418);
            g.drawString(Draw.textFont(), s, cx - w / 2, y - 13, Draw.GREEN, false);
        }
    }

    void drawHud(GuiGraphics g, int ax, int ay) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        String left = T.f("fibres") + bagged + " / " + NEEDED;
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), left, ax + 6, ay + 3, Draw.TEXT, false);
        String right = T.f("tension");
        int rw = Draw.textFont().width(right), gx = ax + AREA_W - 48;
        g.fill(gx - rw - 8, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, gx - rw - 4, ay + 3, Draw.TEXT, false);
        double k = Math.min(1, tension);
        g.fill(gx, ay + 4, gx + 42, ay + 10, Draw.EDGE);
        g.fill(gx + 1, ay + 5, gx + 1 + (int) (40 * k), ay + 9, k > 0.6 ? Draw.ALERT : Draw.YELLOW);
        g.fill(ax + 2, ay + 108, ax + 66, ay + 116, 0xC0101418);
        timeGauge(g, ax + 5, ay + 109, 58, time / (double) DURATION);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + 120 - w / 2 - 4, ay + 2, ax + 120 + w / 2 + 4, ay + 12, 0xC0101418);
            g.drawString(Draw.textFont(), message, ax + 120 - w / 2, ay + 3, messageColour, false);
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        g.pose().popPose();
    }

    static int mix(int a, int b, double k) {
        int r = 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            int ca = (a >> sh) & 0xFF, cb = (b >> sh) & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * k)) << sh;
        }
        return r;
    }

    @Override
    String instruction() {
        return T.f("grip_a_fibre_by_its");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("hold_grip_drag_pull"), Draw.MOUSE)};
    }
}
