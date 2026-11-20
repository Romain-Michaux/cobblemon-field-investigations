package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Sorting (nest): the hollow of a nest holds eggshells, twigs, pebbles and a few dry leaves. Things are flicked like
// marbles: pull back from one, a dotted line shows where it will go and what it will hit first, and release. Only the
// shells with the pattern on the card go into the sample box on the right; the rest goes into the bucket on the left.
// A hard knock cracks a shell, a second breaks it. Leaves hide what lies under them and must be flicked off first.
// At the end the boxed pieces fit back into an egg.
final class SortingGame extends MiniGame {
    static final ResourceLocation NEST = Draw.texture("sort_nest"), SHELL = Draw.texture("sort_shell"), TWIG = Draw.texture("sort_twig"),
            LEAF = Draw.texture("sort_leaf"), PEBBLE = Draw.texture("sieve_pebble"), BUCKET = Draw.texture("sort_bucket"), BOX = Draw.texture("sort_box");
    static final int MATCH = 0, OTHER = 1, TWIG_ITEM = 2, PEBBLE_ITEM = 3, LEAF_ITEM = 4;
    static final int N = 15, DURATION = 680, MAX_FAULTS = 3, RADIUS = 7, LEFT = 60, RIGHT = 224, TOP = 22, BOTTOM = 112, END = 100;
    static final double FRICTION = 0.94, BOUNCE = 0.7, MAX_SPEED = 9, CRACK = 4.2;
    // Patterns: spots or stripes, in a colour.
    static final int[] PATTERN_COLOUR = {0xFF3F7A30, 0xFF3A60B8, 0xFF7A4424, 0xFFB83A30};
    static final boolean[] STRIPES = {false, true, false, true};
    final double[] x = new double[N], y = new double[N], vx = new double[N], vy = new double[N];
    final int[] kind = new int[N], pattern = new int[N], cracks = new int[N];
    final boolean[] out = new boolean[N];
    final int target;
    int aiming = -1, boxed, faults, time = DURATION, flash, tic, lastKnock, ending = -1, matches, messageTicks, messageColour;
    double mx, my;
    String message = "";

    SortingGame(Random h) {
        target = h.nextInt(4);
        int[] kinds = {MATCH, MATCH, MATCH, MATCH, OTHER, OTHER, OTHER, TWIG_ITEM, TWIG_ITEM, TWIG_ITEM, PEBBLE_ITEM, PEBBLE_ITEM, LEAF_ITEM, LEAF_ITEM, LEAF_ITEM};
        for (int i = 0; i < N; i++) {
            kind[i] = kinds[i];
            if (kind[i] == MATCH) {
                pattern[i] = target;
                matches++;
            } else if (kind[i] == OTHER) {
                pattern[i] = (target + 1 + h.nextInt(3)) % 4;
            }
        }
        // Spread the solid things out, then lay each leaf on a shell or a twig.
        for (int i = 0; i < N; i++) {
            if (kind[i] == LEAF_ITEM) continue;
            for (int attempt = 0; attempt < 300; attempt++) {
                x[i] = LEFT + 20 + h.nextInt(RIGHT - LEFT - 40);
                y[i] = TOP + 10 + h.nextInt(BOTTOM - TOP - 20);
                boolean free = true;
                for (int k = 0; k < i; k++) if (kind[k] != LEAF_ITEM) free &= Math.hypot(x[k] - x[i], y[k] - y[i]) >= RADIUS * 2 + 4;
                if (free) break;
            }
        }
        int[] covered = {0, 1, 7};
        int leaf = 0;
        for (int i = 0; i < N; i++) {
            if (kind[i] != LEAF_ITEM) continue;
            int under = covered[leaf++];
            x[i] = x[under] + 1;
            y[i] = y[under] - 1;
        }
    }

    boolean solid(int i) {
        return kind[i] != LEAF_ITEM;
    }

    boolean shell(int i) {
        return kind[i] == MATCH || kind[i] == OTHER;
    }

    // Under a leaf, a thing can't be seen nor flicked.
    boolean hidden(int i) {
        if (!solid(i)) return false;
        for (int k = 0; k < N; k++) if (kind[k] == LEAF_ITEM && !out[k] && Math.hypot(x[k] - x[i], y[k] - y[i]) < 9) return true;
        return false;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    void fault(String why) {
        flash = 8;
        say(why, Draw.ALERT);
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    // A knock on a shell: a hard one cracks it, a second breaks it.
    void knock(int i, double speed) {
        if (!shell(i) || out[i] || speed < CRACK) return;
        if (++cracks[i] >= 2) {
            out[i] = true;
            Draw.beep(Sounds.CRACK, 1.4f, 0.8f);
            if (kind[i] == MATCH) fault(T.f("a_shell_broke"));
        } else {
            Draw.beep(Sounds.CRACK, 1.8f, 0.5f);
            say(T.f("cracked"), Draw.YELLOW);
        }
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        if (ending >= 0) {
            if (ending == 40) Draw.beep(Sounds.CHIME, 1.4f, 0.6f);
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
        step(x, y, vx, vy, out, true);
        // What leaves the nest: into the box on the right, the bucket on the left.
        int left = 0;
        for (int i = 0; i < N; i++) {
            if (!out[i]) {
                boolean bucket = x[i] < LEFT - 4, box = x[i] > RIGHT + 4;
                if (bucket || box) {
                    out[i] = true;
                    if (aiming == i) aiming = -1;
                    if (box && kind[i] == MATCH) {
                        boxed++;
                        Draw.note(1f + boxed * 0.15f);
                        say(T.f("right_shell"), Draw.GREEN);
                    } else if (box) {
                        fault(kind[i] == OTHER ? T.f("wrong_pattern") : T.f("debris_in_the_box"));
                    } else if (kind[i] == MATCH) {
                        fault(T.f("a_good_shell_binned"));
                    } else {
                        Draw.beep(Sounds.PLUCK, 0.7f, 0.4f);
                    }
                }
            }
            if (kind[i] == MATCH && !out[i]) left++;
        }
        if (left == 0 && !finished) {
            if (boxed > 0) ending = 0;
            else end(false);
        }
    }

    // Two half-steps of motion, bounces off the nest's sides and knocks between solid things. With live = false it
    // only predicts (no sound, no cracks).
    void step(double[] x, double[] y, double[] vx, double[] vy, boolean[] out, boolean live) {
        for (int half = 0; half < 2; half++) {
            for (int i = 0; i < N; i++) {
                if (out[i]) continue;
                x[i] += vx[i] / 2;
                y[i] += vy[i] / 2;
                if (y[i] < TOP || y[i] > BOTTOM) {
                    double speed = Math.abs(vy[i]);
                    y[i] = Math.max(TOP, Math.min(BOTTOM, y[i]));
                    vy[i] = -vy[i] * 0.5;
                    if (live) knock(i, speed);
                }
            }
            for (int i = 0; i < N; i++) {
                for (int k = i + 1; k < N; k++) {
                    if (out[i] || out[k] || !solid(i) || !solid(k)) continue;
                    double dx = x[k] - x[i], dy = y[k] - y[i], d = Math.hypot(dx, dy), overlap = RADIUS * 2 - d;
                    if (overlap <= 0) continue;
                    if (d < 0.01) {
                        dx = 1;
                        dy = 0;
                        d = 1;
                    }
                    double nx = dx / d, ny = dy / d;
                    x[i] -= nx * overlap / 2;
                    y[i] -= ny * overlap / 2;
                    x[k] += nx * overlap / 2;
                    y[k] += ny * overlap / 2;
                    double approach = (vx[k] - vx[i]) * nx + (vy[k] - vy[i]) * ny;
                    if (approach >= 0) continue;
                    double momentum = (1 + BOUNCE) * approach / 2;
                    vx[i] += momentum * nx;
                    vy[i] += momentum * ny;
                    vx[k] -= momentum * nx;
                    vy[k] -= momentum * ny;
                    if (!live) continue;
                    knock(i, -approach);
                    knock(k, -approach);
                    if (approach < -1.2 && tic - lastKnock > 2) {
                        lastKnock = tic;
                        Draw.beep(Sounds.KNOCK, (float) Math.min(2, 1 - approach / 10), (float) Math.min(0.8, 0.2 - approach / 12));
                    }
                }
            }
        }
        for (int i = 0; i < N; i++) {
            if (out[i]) continue;
            vx[i] *= FRICTION;
            vy[i] *= FRICTION;
            if (Math.hypot(vx[i], vy[i]) < 0.06) {
                vx[i] = 0;
                vy[i] = 0;
            }
        }
    }

    // The flick being aimed: pulled back from the thing, released the other way.
    double[] launch() {
        double dx = x[aiming] - (mx - ox), dy = y[aiming] - (my - oy), d = Math.hypot(dx, dy);
        if (d < 4) return null;
        double v = Math.min(MAX_SPEED, d * 0.2);
        return new double[] {dx / d * v, dy / d * v};
    }

    int thingAt(double px, double py) {
        double lx = px - ox, ly = py - oy, nearest = RADIUS + 4;
        int best = -1;
        // Leaves lie on top: they are picked first.
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            for (int i = 0; i < N; i++) {
                if (out[i] || (pass == 0) != (kind[i] == LEAF_ITEM) || hidden(i)) continue;
                double d = Math.hypot(lx - x[i], ly - y[i]);
                if (d < nearest) {
                    nearest = d;
                    best = i;
                }
            }
        }
        return best;
    }

    @Override
    void click(double px, double py) {
        if (finished || ending >= 0) return;
        aiming = thingAt(px, py);
        if (aiming >= 0) Draw.beep(Sounds.PLUCK, 1.3f, 0.4f);
    }

    @Override
    void released(double px, double py) {
        if (aiming < 0) return;
        mx = px;
        my = py;
        double[] v = launch();
        if (v != null && !finished) {
            vx[aiming] = v[0];
            vy[aiming] = v[1];
            Draw.beep(Sounds.KNOCK, 1.5f, 0.4f);
        }
        aiming = -1;
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, NEST, ax, ay, AREA_W, AREA_H);
        Draw.image(g, BUCKET, ax + 6, ay + 40, 42, 44);
        Draw.image(g, BOX, ax + 232, ay + 32, 42, 54);
        drawCard(g, ax + 4, ay + 4);
        if (ending >= 0) {
            drawFinale(g, ax, ay);
        } else {
            // Solid things first, then the leaves over them.
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < N; i++) {
                    if (out[i] || (pass == 1) != (kind[i] == LEAF_ITEM)) continue;
                    int x0 = ax + (int) Math.round(x[i] + vx[i] * pt), y0 = ay + (int) Math.round(y[i] + vy[i] * pt);
                    thing(g, i, x0, y0);
                }
            }
            if (aiming >= 0) drawAim(g, ax, ay);
            else {
                int hover = finished ? -1 : thingAt(px, py);
                if (hover >= 0) Draw.circle(g, ax + x[hover] - 0.5, ay + y[hover] - 0.5, RADIUS + 2, 1, 0x80FFFFFF);
            }
        }
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        // Counters and the last message.
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        String right = T.f("mistakes") + faults + " / " + MAX_FAULTS;
        int rw = Draw.textFont().width(right);
        g.fill(ax + AREA_W - 10 - rw, ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        g.drawString(Draw.textFont(), right, ax + AREA_W - 6 - rw, ay + 3, faults > 0 ? Draw.ALERT : Draw.GREY, false);
        String boxedText = boxed + " / " + matches;
        int bw = Draw.textFont().width(boxedText);
        g.fill(ax + 253 - bw / 2 - 3, ay + 88, ax + 253 + bw / 2 + 3, ay + 98, 0xC0101418);
        g.drawString(Draw.textFont(), boxedText, ax + 253 - bw / 2, ay + 89, Draw.GREEN, false);
        g.fill(ax + 2, ay + 106, ax + 66, ay + 114, 0xC0101418);
        timeGauge(g, ax + 5, ay + 107, 58, time / (double) DURATION);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + AREA_W / 2 - w / 2 - 4, ay + 2, ax + AREA_W / 2 + w / 2 + 4, ay + 12, 0xC0101418);
            Draw.centre(g, message, ax + AREA_W / 2, ay + 3, messageColour);
        }
        g.pose().popPose();
    }

    void thing(GuiGraphics g, int i, int x0, int y0) {
        switch (kind[i]) {
            case MATCH, OTHER -> {
                Draw.image(g, SHELL, x0 - 8, y0 - 8, 16, 15);
                patternOn(g, pattern[i], x0, y0, 1);
                if (cracks[i] > 0) {
                    g.fill(x0 - 3, y0 - 4, x0 - 2, y0 - 1, 0xFF3A3028);
                    g.fill(x0 - 2, y0 - 1, x0 - 1, y0 + 2, 0xFF3A3028);
                    g.fill(x0 - 1, y0 + 2, x0, y0 + 4, 0xFF3A3028);
                }
            }
            case TWIG_ITEM -> Draw.image(g, TWIG, x0 - 9, y0 - 9, 18, 17);
            case PEBBLE_ITEM -> Draw.image(g, PEBBLE, x0 - 7, y0 - 6, 14, 13);
            default -> Draw.image(g, LEAF, x0 - 11, y0 - 12, 22, 24);
        }
    }

    // A shell's pattern: spots or stripes in its colour, scaled with the shell.
    static void patternOn(GuiGraphics g, int p, int cx, int cy, int k) {
        int c = PATTERN_COLOUR[p];
        if (STRIPES[p]) {
            g.fill(cx - 4 * k, cy - 3 * k, cx + 4 * k, cy - 2 * k, c);
            g.fill(cx - 5 * k, cy + k, cx + 4 * k, cy + 2 * k, c);
        } else {
            int[][] dots = {{-3, -3}, {2, -2}, {-1, 1}, {3, 2}, {-4, 3}};
            for (int[] d : dots) g.fill(cx + d[0] * k, cy + d[1] * k, cx + d[0] * k + 2 * k, cy + d[1] * k + 2 * k, c);
        }
    }

    // The card: the pattern to keep, on a whole egg.
    void drawCard(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 37, y + 33, 0xFF30281E);
        g.fill(x, y, x + 36, y + 32, 0xFFF4F0E6);
        egg(g, x + 18, y + 16, 10, 13, target);
    }

    static void egg(GuiGraphics g, int cx, int cy, int rx, int ry, int p) {
        for (int dy = -ry; dy <= ry; dy++) {
            double t = dy / (double) ry;
            int half = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - t * t)) * (1 - 0.15 * t * -1 * (t < 0 ? 1 : 0)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, dy < -ry * 0.5 ? 0xFFFAF6EC : 0xFFEDE4D0);
            g.fill(cx - half - 1, cy + dy, cx - half, cy + dy + 1, 0xFF5A4A3A);
            g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF5A4A3A);
        }
        patternOn(g, p, cx, cy, Math.max(1, rx / 8));
    }

    // The aim: the pull-back line and the predicted path to the first knock, red where that knock would crack a shell.
    void drawAim(GuiGraphics g, int ax, int ay) {
        double[] v = launch();
        int sx = ax + (int) Math.round(x[aiming]), sy = ay + (int) Math.round(y[aiming]);
        Draw.circle(g, sx - 0.5, sy - 0.5, RADIUS + 2, 1, Draw.YELLOW);
        if (v == null) return;
        // The rubber band, back to the pointer.
        int steps = 8;
        for (int i = 1; i < steps; i++) {
            int bx = (int) Math.round(sx + (mx - sx) * i / steps), by = (int) Math.round(sy + (my - sy) * i / steps);
            g.fill(bx, by, bx + 1, by + 1, 0x90FFFFFF);
        }
        double px = x[aiming], py = y[aiming], pvx = v[0], pvy = v[1];
        boolean hard = false;
        int hit = -1;
        for (int t = 0; t < 90 && hit < 0; t++) {
            for (int half = 0; half < 2 && hit < 0; half++) {
                px += pvx / 2;
                py += pvy / 2;
                if (py < TOP || py > BOTTOM) {
                    py = Math.max(TOP, Math.min(BOTTOM, py));
                    pvy = -pvy * 0.5;
                }
                if (solid(aiming)) {
                    for (int k = 0; k < N; k++) {
                        if (k == aiming || out[k] || !solid(k)) continue;
                        if (Math.hypot(x[k] - px, y[k] - py) < RADIUS * 2) {
                            hit = k;
                            hard = Math.hypot(pvx, pvy) >= CRACK && (shell(k) || shell(aiming));
                        }
                    }
                }
            }
            pvx *= FRICTION;
            pvy *= FRICTION;
            if (Math.hypot(pvx, pvy) < 0.06 || px < LEFT - 6 || px > RIGHT + 6) break;
            if (t % 2 == 0) {
                int c = Math.hypot(pvx, pvy) >= CRACK && shell(aiming) ? 0xE0FF7060 : 0xE0FFF0A0;
                g.fill(ax + (int) px, ay + (int) py, ax + (int) px + 1, ay + (int) py + 1, c);
            }
        }
        if (hit >= 0) Draw.circle(g, ax + x[hit] - 0.5, ay + y[hit] - 0.5, RADIUS + 2, 1, hard ? Draw.ALERT : 0xFFFFF0A0);
    }

    // The end: the boxed pieces fly out and close up into a whole egg with its pattern.
    void drawFinale(GuiGraphics g, int ax, int ay) {
        double t = Math.min(1, ending / 35.0), e = t * t * (3 - 2 * t);
        int cx = ax + 140, cy = ay + 62;
        g.pose().pushPose();
        g.pose().translate(0, 0, 40);
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, (int) (e * 110) << 24);
        if (ending < 40) {
            for (int i = 0; i < boxed; i++) {
                double a = i * Math.PI * 2 / Math.max(1, boxed);
                double tx = cx + Math.cos(a) * 10, ty = cy + Math.sin(a) * 13;
                double fx = ax + 253, fy = ay + 58;
                int x0 = (int) Math.round(fx + (tx - fx) * e), y0 = (int) Math.round(fy + (ty - fy) * e);
                Draw.image(g, SHELL, x0 - 8, y0 - 8, 16, 15);
                patternOn(g, target, x0, y0, 1);
            }
        } else {
            double grow = Math.min(1, (ending - 40) / 10.0);
            egg(g, cx, cy, (int) (10 + 6 * grow), (int) (13 + 8 * grow), target);
            if (ending > 52) {
                for (int i = 0; i < 4; i++) {
                    double a = ending * 0.08 + i * Math.PI / 2;
                    int sx = (int) (cx + Math.cos(a) * 30), sy = (int) (cy + Math.sin(a) * 26);
                    g.fill(sx - 1, sy, sx + 2, sy + 1, 0xFFFFFFFF);
                    g.fill(sx, sy - 1, sx + 1, sy + 2, 0xFFFFFFFF);
                }
                String s = T.f("egg_pieced_together");
                int w = Draw.textFont().width(s);
                g.fill(cx - w / 2 - 4, ay + 96, cx + w / 2 + 4, ay + 106, 0xC0101418);
                g.drawString(Draw.textFont(), s, cx - w / 2, ay + 97, Draw.GREEN, false);
            }
        }
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("pull_back_then_release_to");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("pull_back_release_flick"), Draw.MOUSE)};
    }
}
