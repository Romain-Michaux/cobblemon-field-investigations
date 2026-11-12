package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Sorting (nest): fling each shell into the box, quickly, without the collisions scattering everything else.
final class SortingGame extends MiniGame {
    static final ResourceLocation TRAY = Draw.texture("plateau");
    static final ResourceLocation[] DEBRIS = {Draw.texture("debris_shell"), Draw.texture("debris_twig"),
            Draw.texture("debris_leaf"), Draw.texture("debris_pebble")};
    static final int N = 13, SHELLS = 5, DURATION = 260, RADIUS = 7, DISCARD = 26, BOX = L - 26;
    static final double FRICTION = 0.94, BOUNCE = 0.75, MAX_SPEED = 11;
    final double[] x = new double[N], y = new double[N], vx = new double[N], vy = new double[N];
    final int[] kind = new int[N];
    final boolean[] out = new boolean[N];
    int held = -1, boxed, remaining = SHELLS, faults, time = DURATION, flash, tic, lastKnock;
    double cx, cy;

    SortingGame(Random h) {
        for (int i = 0; i < N; i++) {
            kind[i] = i < SHELLS ? 0 : 1 + h.nextInt(3);
            for (int attempt = 0; attempt < 200; attempt++) {
                x[i] = DISCARD + 14 + h.nextInt(BOX - DISCARD - 28);
                y[i] = 12 + h.nextInt(H - 24);
                boolean free = true;
                for (int k = 0; k < i; k++) free &= Math.hypot(x[k] - x[i], y[k] - y[i]) >= RADIUS * 2 + 2;
                if (free) break;
            }
        }
    }

    void fault() {
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 3) end(false);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        tic++;
        if (!mouseDown) held = -1; // released: the object keeps its momentum
        if (held >= 0) {
            // The held object chases the mouse; its velocity is what it keeps when released.
            double dx = (cx - ox - 4 - x[held]) * 0.55, dy = (cy - oy - 3 - y[held]) * 0.55, v = Math.hypot(dx, dy);
            if (v > MAX_SPEED) {
                dx *= MAX_SPEED / v;
                dy *= MAX_SPEED / v;
            }
            vx[held] = dx;
            vy[held] = dy;
        }
        // Two half-steps per tick, so a fast object cannot pass through its neighbours.
        for (int half = 0; half < 2; half++) {
            for (int i = 0; i < N; i++) {
                if (out[i]) continue;
                x[i] += vx[i] / 2;
                y[i] += vy[i] / 2;
                if (y[i] < RADIUS) {
                    y[i] = RADIUS;
                    vy[i] = Math.abs(vy[i]) * 0.5;
                } else if (y[i] > H - RADIUS) {
                    y[i] = H - RADIUS;
                    vy[i] = -Math.abs(vy[i]) * 0.5;
                }
            }
            for (int i = 0; i < N; i++) {
                for (int k = i + 1; k < N; k++) {
                    if (out[i] || out[k]) continue;
                    double dx = x[k] - x[i], dy = y[k] - y[i], d = Math.hypot(dx, dy), overlap = RADIUS * 2 - d;
                    if (overlap <= 0) continue;
                    if (d < 0.01) {
                        dx = 1;
                        dy = 0;
                        d = 1;
                    }
                    double nx = dx / d, ny = dy / d;
                    // The held object does not give way; otherwise each one backs off by half.
                    double share = i == held ? 0 : k == held ? 1 : 0.5;
                    x[i] -= nx * overlap * share;
                    y[i] -= ny * overlap * share;
                    x[k] += nx * overlap * (1 - share);
                    y[k] += ny * overlap * (1 - share);
                    // Collision: the approach speed transfers from one to the other, like billiard balls.
                    double approach = (vx[k] - vx[i]) * nx + (vy[k] - vy[i]) * ny;
                    if (approach >= 0) continue;
                    double momentum = (1 + BOUNCE) * approach;
                    if (i == held) {
                        vx[k] -= momentum * nx;
                        vy[k] -= momentum * ny;
                    } else if (k == held) {
                        vx[i] += momentum * nx;
                        vy[i] += momentum * ny;
                    } else {
                        vx[i] += momentum / 2 * nx;
                        vy[i] += momentum / 2 * ny;
                        vx[k] -= momentum / 2 * nx;
                        vy[k] -= momentum / 2 * ny;
                    }
                    if (approach < -1.2 && tic - lastKnock > 2) {
                        lastKnock = tic;
                        Draw.beep(Sounds.KNOCK, (float) Math.min(2, 1 - approach / 10), (float) Math.min(0.8, 0.2 - approach / 12));
                    }
                }
            }
        }
        for (int i = 0; i < N; i++) {
            if (out[i]) continue;
            if (i != held) {
                vx[i] *= FRICTION;
                vy[i] *= FRICTION;
                if (Math.hypot(vx[i], vy[i]) < 0.06) {
                    vx[i] = 0;
                    vy[i] = 0;
                }
            }
            boolean discard = x[i] < DISCARD - 2, box = x[i] > BOX + 2;
            if (!discard && !box) continue;
            out[i] = true;
            if (i == held) held = -1;
            if (kind[i] == 0) {
                remaining--;
                if (box) {
                    boxed++;
                    Draw.note(1f + boxed * 0.15f);
                } else {
                    fault(); // a shell in the discard
                }
            } else if (box) {
                fault(); // debris in the sample box
            } else {
                Draw.beep(Sounds.PLUCK, 0.7f, 0.4f);
            }
        }
        if (remaining <= 0 && !finished) {
            perfect = faults == 0;
            end(boxed > 0);
        }
    }

    @Override
    void click(double mx, double my) {
        double lx = mx - ox - 4, ly = my - oy - 3, nearest = RADIUS + 4;
        held = -1;
        for (int i = 0; i < N; i++) {
            double d = Math.hypot(lx - x[i], ly - y[i]);
            if (!out[i] && d < nearest) {
                nearest = d;
                held = i;
            }
        }
        if (held >= 0) Draw.beep(Sounds.PLUCK, 1.3f, 0.4f);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        frame(g, bx, by, flash > 0);
        Draw.image(g, TRAY, bx, by, L, H);
        g.fill(bx, by, bx + DISCARD, by + H, 0xB0241A12);
        g.fill(bx + DISCARD - 1, by, bx + DISCARD, by + H, Draw.ALERT);
        g.fill(bx + BOX, by, bx + L, by + H, 0xB0102A22);
        g.fill(bx + BOX, by, bx + BOX + 1, by + H, Draw.GREEN);
        g.setColor(1f, 1f, 1f, 0.45f);
        Draw.image(g, DEBRIS[1], bx + 5, by + 6, 16, 16);
        Draw.image(g, DEBRIS[3], bx + 5, by + 26, 16, 16);
        Draw.image(g, DEBRIS[0], bx + BOX + 5, by + 6, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        g.enableScissor(bx, by, bx + L, by + H);
        for (int i = 0; i < N; i++) {
            if (out[i]) continue;
            // Between two ticks the object is drawn where its momentum carries it, so motion stays smooth.
            int x0 = bx + (int) Math.round(x[i] + (finished ? 0 : vx[i] * pt)), y0 = by + (int) Math.round(y[i] + (finished ? 0 : vy[i] * pt));
            if (i == held) Draw.circle(g, x0 - 0.5, y0 - 0.5, RADIUS + 2, 1, Draw.YELLOW);
            Draw.image(g, DEBRIS[kind[i]], x0 - 8, y0 - 8, 16, 16);
        }
        g.disableScissor();
        int tx = ox + 172;
        text(g, T.f("shells_2") + boxed + " / " + SHELLS, tx, oy + 10, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("throw_the_shells_they_keep"), tx, oy + 42, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("grab_a_shell_and_throw");
    }
}
