package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tri des fragments (nid) : lancer chaque coquille dans la boîte, vite, sans que les chocs envoient le reste n'importe où
final class SortingGame extends MiniGame {
    static final ResourceLocation TRAY = Draw.texture("plateau");
    static final ResourceLocation[] DEBRIS = {Draw.texture("debris_shell"), Draw.texture("debris_twig"),
            Draw.texture("debris_leaf"), Draw.texture("debris_pebble")};
    static final int N = 13, SHELLS = 5, DURATION = 260, RADIUS = 7, DISCARD = 26, BOX = L - 26;
    static final double RUB = 0.94, BOUNCE = 0.75, MAX_SPEED = 11;
    final double[] x = new double[N], y = new double[N], vx = new double[N], vy = new double[N];
    final int[] kind1 = new int[N];
    final boolean[] out = new boolean[N];
    int held = -1, rows, remainingOnes = SHELLS, faults, time = DURATION, flash, tic, lastKnock;
    double cx, cy;

    SortingGame(Random h) {
        for (int i = 0; i < N; i++) {
            kind1[i] = i < SHELLS ? 0 : 1 + h.nextInt(3);
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
        press1(time, DURATION);
        tic++;
        if (!press) held = -1; // lâché : l'objet garde son élan et continue sur sa lancée
        if (held >= 0) {
            // L'objet tenu file vers la souris ; sa vitesse est celle qu'il gardera quand on le lâchera.
            double dx = (cx - ox - 4 - x[held]) * 0.55, dy = (cy - oy - 3 - y[held]) * 0.55, v = Math.hypot(dx, dy);
            if (v > MAX_SPEED) {
                dx *= MAX_SPEED / v;
                dy *= MAX_SPEED / v;
            }
            vx[held] = dx;
            vy[held] = dy;
        }
        // Deux demi-pas par tic, pour qu'un objet lancé vite ne traverse pas ses voisins.
        for (int stepSize = 0; stepSize < 2; stepSize++) {
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
                    double dx = x[k] - x[i], dy = y[k] - y[i], d = Math.hypot(dx, dy), missing = RADIUS * 2 - d;
                    if (missing <= 0) continue;
                    if (d < 0.01) {
                        dx = 1;
                        dy = 0;
                        d = 1;
                    }
                    double nx = dx / d, ny = dy / d;
                    // Celui qu'on tient ne cède pas ; sinon chacun recule de moitié.
                    double pi = i == held ? 0 : k == held ? 1 : 0.5;
                    x[i] -= nx * missing * pi;
                    y[i] -= ny * missing * pi;
                    x[k] += nx * missing * (1 - pi);
                    y[k] += ny * missing * (1 - pi);
                    // Choc : la vitesse d'approche passe de l'un à l'autre, comme au billard.
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
                vx[i] *= RUB;
                vy[i] *= RUB;
                if (Math.hypot(vx[i], vy[i]) < 0.06) {
                    vx[i] = 0;
                    vy[i] = 0;
                }
            }
            boolean discard = x[i] < DISCARD - 2, box = x[i] > BOX + 2;
            if (!discard && !box) continue;
            out[i] = true;
            if (i == held) held = -1;
            if (kind1[i] == 0) {
                remainingOnes--;
                if (box) {
                    rows++;
                    Draw.note(1f + rows * 0.15f);
                } else {
                    fault(); // une coquille au rebut
                }
            } else if (box) {
                fault(); // un débris dans la boîte d'échantillons
            } else {
                Draw.beep(Sounds.PLUCK, 0.7f, 0.4f);
            }
        }
        if (remainingOnes <= 0 && !finished) {
            perfect = faults == 0;
            end(rows > 0);
        }
    }

    @Override
    void click(double mx, double my) {
        double lx = mx - ox - 4, ly = my - oy - 3, close = RADIUS + 4;
        held = -1;
        for (int i = 0; i < N; i++) {
            double d = Math.hypot(lx - x[i], ly - y[i]);
            if (!out[i] && d < close) {
                close = d;
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
            // Entre deux tics, l'objet est dessiné là où son élan le porte : le mouvement reste fluide.
            int x0 = bx + (int) Math.round(x[i] + (finished ? 0 : vx[i] * pt)), y0 = by + (int) Math.round(y[i] + (finished ? 0 : vy[i] * pt));
            if (i == held) Draw.circle(g, x0 - 0.5, y0 - 0.5, RADIUS + 2, 1, Draw.YELLOW);
            Draw.image(g, DEBRIS[kind1[i]], x0 - 8, y0 - 8, 16, 16);
        }
        g.disableScissor();
        int tx = ox + 172;
        text(g, T.f("shells_2") + rows + " / " + SHELLS, tx, oy + 10, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        Draw.paragraph(g, T.f("throw_the_shells_they_keep"), tx, oy + 42, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("grab_a_shell_and_throw");
    }
}
