package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Abseiling (altitude investigation): the climber hangs from a piton at the top of a gorge. Swinging and paying out rope,
// they go down past rock bars jutting out of the walls, left and right in turn, each ending in a boulder. The rope wraps
// around the boulder, and the clue lies in a hollow under the bar: only reachable by swinging from that boulder.
final class AbseilGame extends MiniGame {
    static final ResourceLocation ROCK_FACE = Draw.texture("rock_face"), CLIMBER = Draw.texture("abseil_climber"),
            BOULDER = Draw.texture("abseil_boulder"), LEDGE = Draw.texture("abseil_ledge");
    static final java.util.Set<String> KINDS = java.util.Set.of("stat", "size", "egg", "type1", "type2");
    // The view covers the whole play area; the gorge is taller than the view and the camera follows the climber.
    // There is no floor: the gorge drops into darkness, and the rope stops running once it could reach DEPTH.
    static final int VW = 272, VH = 112, WH = 300, AX = VW / 2, AY = 8, DEPTH = WH + 20, DURATION = 1800, SIDES = 14;
    static final double G = 0.45, PUMP = 0.08, PAY = 2.4, HAUL = 1.6, MIN_ROPE = 18, MIN_FREE = 9, MAX_ROPE = 700, R = 5;
    // Highest speed (px/tick) at which a trace can be taken. Any contact with the rock is a mistake, counted at most
    // once per CONTACT_GRACE ticks so that one bump is not counted twice.
    static final double GENTLE = 2.4, BOUNCE = 0.35;
    static final int CONTACT_GRACE = 20;
    static final int[] LEFT = {InputConstants.KEY_LEFT, InputConstants.KEY_A, InputConstants.KEY_Q};
    static final int[] RIGHT = {InputConstants.KEY_RIGHT, InputConstants.KEY_D};
    static final int[] UP = {InputConstants.KEY_UP, InputConstants.KEY_W, InputConstants.KEY_Z};
    static final int[] DOWN = {InputConstants.KEY_DOWN, InputConstants.KEY_S};

    final ResourceLocation icon;
    final double[] wallLeft = new double[WH / 30 + 2], wallRight = new double[WH / 30 + 2];
    // Rock bars: attached to the left (side 0) or right wall at height barY, half-thickness HALF, ending in a boulder of
    // radius rockR centred at (rockX, barY). The rope wraps around the boulder's free corners; a hollow is cut into
    // the underside of the bar at nicheX.
    static final int BARS = 3, HALF = 7, NICHE_W = 18, NICHE_D = 4;
    final int[] barSide = new int[BARS];
    final double[] barY = new double[BARS], rockX = new double[BARS], rockR = new double[BARS], nicheX = new double[BARS];
    final double[][] cornerX = new double[BARS][SIDES], cornerY = new double[BARS][SIDES];
    final boolean[][] usable = new boolean[BARS][SIDES];
    final double[] traceX = new double[3], traceY = new double[3];
    final boolean[] taken = new boolean[3];
    // Points the rope bends around, from the piton (index 0) to the one the climber swings from (pivots - 1).
    // bend[k] is the side the rope turned to at pivot k: when the climber swings back past it, the rope unwraps.
    final double[] pivotX = new double[16], pivotY = new double[16], bend = new double[16];
    int pivots = 1;
    // rope = free length between the last pivot and the climber; wrapped = length laid along the pivots.
    double theta, omega, rope = 34, wrapped, prevX, prevY, x, y, impact, contactX;
    boolean touched, built;
    int time = DURATION, faults, flash, shake, tic, cooldown, takenCount, message, messageTicks, contact, lastKnock;

    AbseilGame(Random h, String kind) {
        icon = Draw.texture("abseil_" + (KINDS.contains(kind) ? kind : "stat"));
        for (int k = 0; k < wallLeft.length; k++) {
            wallLeft[k] = 8 + h.nextInt(12);
            wallRight[k] = VW - 8 - h.nextInt(12);
        }
        pivotX[0] = AX;
        pivotY[0] = AY;
        // The bars alternate sides, so the way down zigzags around their boulders.
        int first = h.nextInt(2);
        for (int k = 0; k < BARS; k++) {
            barSide[k] = (first + k) % 2;
            barY[k] = 72 + k * 84 + h.nextInt(13) - 6;
            rockR[k] = 11 + h.nextInt(3);
            double gap = 84 + h.nextInt(16);
            rockX[k] = barSide[k] == 0 ? wallRight(barY[k]) - gap - rockR[k] : wallLeft(barY[k]) + gap + rockR[k];
            int towardWall = barSide[k] == 0 ? -1 : 1;
            nicheX[k] = rockX[k] + towardWall * (30 + h.nextInt(20));
            traceX[k] = nicheX[k];
            traceY[k] = barY[k] + HALF + 5;
            for (int c = 0; c < SIDES; c++) {
                double t = Math.PI * 2 * c / SIDES;
                cornerX[k][c] = rockX[k] + Math.sin(t) * (rockR[k] + 0.5);
                cornerY[k][c] = barY[k] - Math.cos(t) * (rockR[k] + 0.5);
                // Corners buried in the bar itself cannot hold the rope.
                boolean wallSide = (cornerX[k][c] - rockX[k]) * towardWall > 0;
                usable[k][c] = !(wallSide && Math.abs(cornerY[k][c] - barY[k]) < HALF + 1);
            }
        }
        built = true;
        place();
        prevX = x;
        prevY = y;
    }

    double wallLeft(double wy) {
        return wall(wallLeft, wy) + buttress(0, wy);
    }

    double wallRight(double wy) {
        return wall(wallRight, wy) - buttress(1, wy);
    }

    // Each bar grows out of its wall: the rock swells into a short slope under it (and a smaller one above).
    double buttress(int side, double wy) {
        if (!built) return 0;
        double b = 0;
        for (int k = 0; k < BARS; k++) {
            if (barSide[k] != side) continue;
            double below = wy - (barY[k] + HALF), above = barY[k] - HALF - wy;
            if (below >= 0 && below < 9) b = Math.max(b, 9 - below);
            if (above >= 0 && above < 5) b = Math.max(b, 5 - above);
        }
        return b;
    }

    static double wall(double[] nodes, double wy) {
        double t = Math.max(0, Math.min(nodes.length - 1.001, wy / 30));
        int k = (int) t;
        return nodes[k] + (nodes[k + 1] - nodes[k]) * (t - k);
    }

    double px() {
        return pivotX[pivots - 1];
    }

    double py() {
        return pivotY[pivots - 1];
    }

    void place() {
        x = px() + rope * Math.sin(theta);
        y = py() + rope * Math.cos(theta);
    }

    double speed() {
        return Math.abs(omega) * rope;
    }

    double minimum() {
        return pivots > 1 ? MIN_FREE : MIN_ROPE;
    }

    static boolean held(int[] keys) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int k : keys) {
            if (InputConstants.isKeyDown(window, k)) return true;
        }
        return false;
    }

    // Re-measures the free rope from the current pivot to the climber, keeping their speed along the swing.
    void remeasure() {
        double before = rope;
        rope = Math.max(1, Math.hypot(x - px(), y - py()));
        theta = Math.atan2(x - px(), y - py());
        omega = omega * before / rope;
    }

    // Pushes the climber out of a surface (outward normal nx, ny; depth in px) and bounces the velocity off it.
    void push(double nx, double ny, double depth) {
        double tx = Math.cos(theta), ty = -Math.sin(theta);
        double vx = omega * rope * tx, vy = omega * rope * ty, vn = vx * nx + vy * ny;
        if (vn < 0) {
            impact = Math.max(impact, -vn);
            vx -= (1 + BOUNCE) * vn * nx;
            vy -= (1 + BOUNCE) * vn * ny;
        }
        x += nx * depth;
        y += ny * depth;
        // The rope stays taut: it now ends exactly where the climber was pushed to.
        rope = Math.max(1, Math.hypot(x - px(), y - py()));
        theta = Math.atan2(x - px(), y - py());
        omega = (vx * Math.cos(theta) - vy * Math.sin(theta)) / rope;
        contact = 2;
        touched = true;
        contactX = nx;
    }

    void collide() {
        double left = wallLeft(y) + R, right = wallRight(y) - R;
        if (x < left) push(1, 0, left - x);
        if (x > right) push(-1, 0, x - right);
        if (y < AY + R) push(0, 1, AY + R - y);
        for (int k = 0; k < BARS; k++) {
            double x0 = barSide[k] == 0 ? 0 : rockX[k], x1 = barSide[k] == 0 ? rockX[k] : VW;
            if (x > x0 && x < x1) {
                double top = barY[k] - HALF, bottom = barY[k] + HALF - (Math.abs(x - nicheX[k]) < NICHE_W / 2.0 ? NICHE_D : 0);
                if (y > top - R && y < bottom + R) {
                    if (y < barY[k]) push(0, -1, y - (top - R));
                    else push(0, 1, bottom + R - y);
                }
            }
            double dx = x - rockX[k], dy = y - barY[k], d = Math.hypot(dx, dy);
            if (d < rockR[k] + R && d > 0.01) push(dx / d, dy / d, rockR[k] + R - d);
        }
    }

    static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    static boolean inTriangle(double vx, double vy, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = cross(bx - ax, by - ay, vx - ax, vy - ay), d2 = cross(cx - bx, cy - by, vx - bx, vy - by), d3 = cross(ax - cx, ay - cy, vx - cx, vy - cy);
        return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
    }

    // The rope swept from (oldX, oldY) to the climber: unwrap from the last pivot if the climber swung back past it,
    // then wrap around the first rock corner the rope ran into.
    void wrap(double oldX, double oldY) {
        if (pivots > 1) {
            double qx = pivotX[pivots - 2], qy = pivotY[pivots - 2];
            if (cross(px() - qx, py() - qy, x - px(), y - py()) * bend[pivots - 1] < 0) {
                double segment = Math.hypot(px() - qx, py() - qy);
                pivots--;
                wrapped -= segment;
                remeasure();
                return;
            }
        }
        if (pivots >= pivotX.length || Math.hypot(x - oldX, y - oldY) < 0.01) return;
        double best = Double.MAX_VALUE, bestX = 0, bestY = 0;
        double ox0 = oldX - px(), oy0 = oldY - py(), oldLength = Math.hypot(ox0, oy0);
        for (int s = 0; s < BARS; s++) {
            for (int k = 0; k < SIDES; k++) {
                if (!usable[s][k]) continue;
                double vx = cornerX[s][k], vy = cornerY[s][k];
                if (Math.hypot(vx - px(), vy - py()) < 1 || Math.hypot(vx - px(), vy - py()) > rope - 2) continue;
                if (!inTriangle(vx, vy, px(), py(), oldX, oldY, x, y)) continue;
                // The first corner reached is the one closest in angle to where the rope was.
                double dot = (ox0 * (vx - px()) + oy0 * (vy - py())) / (oldLength * Math.hypot(vx - px(), vy - py()));
                double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
                if (angle < best) {
                    best = angle;
                    bestX = vx;
                    bestY = vy;
                }
            }
        }
        if (best == Double.MAX_VALUE) return;
        double turn = cross(bestX - px(), bestY - py(), x - bestX, y - bestY);
        if (Math.abs(turn) < 1e-6) return;
        wrapped += Math.hypot(bestX - px(), bestY - py());
        pivotX[pivots] = bestX;
        pivotY[pivots] = bestY;
        bend[pivots] = Math.signum(turn);
        pivots++;
        remeasure();
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (shake > 0) shake--;
        if (cooldown > 0) cooldown--;
        if (messageTicks > 0) messageTicks--;
        if (contact > 0) contact--;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        tic++;
        prevX = x;
        prevY = y;
        boolean left = held(LEFT), right = held(RIGHT), up = held(UP), down = held(DOWN) || mouseDown;
        int push = (right ? 1 : 0) - (left ? 1 : 0);
        // Changing the rope length keeps the angular momentum: hauling in at the bottom of the swing speeds it up,
        // paying out at the top of the swing carries the climber further at the same angle.
        double before = rope;
        if (up && !down) rope = Math.max(minimum(), rope - HAUL);
        else if (down && !up && py() + rope < DEPTH) rope = Math.min(Math.min(MAX_ROPE - wrapped, rope + PAY), Math.max(rope, DEPTH - py()));
        if (rope < before - 1e-9 || rope > before + 1e-9) {
            omega *= (before * before) / (rope * rope);
            if (tic % 4 == 0) Draw.beep(Sounds.ROPE, rope > before ? 0.9f : 1.25f, 0.5f);
        }
        impact = 0;
        touched = false;
        for (int sub = 0; sub < 2; sub++) {
            double oldX = x, oldY = y;
            omega += (-G * Math.sin(theta) + push * PUMP) / rope * 0.5;
            omega *= 0.999;
            theta += omega * 0.5;
            place();
            wrap(oldX, oldY);
            collide();
        }
        boolean tookOne = false;
        for (int i = 0; i < 3; i++) {
            if (taken[i] || Math.hypot(x - traceX[i], y - traceY[i]) > 12) continue;
            if (speed() <= GENTLE) {
                taken[i] = true;
                Draw.beep(Sounds.CARABINER, 1.2f, 0.8f);
                Draw.note(1f + takenCount * 0.25f);
                message = 1;
                messageTicks = 24;
                if (++takenCount >= 3) {
                    perfect = faults == 0;
                    end(true);
                    return;
                }
                tookOne = true;
            } else if (messageTicks == 0) {
                message = 2;
                messageTicks = 20;
            }
        }
        // Reaching a trace under a bar may graze the rock: that touch is forgiven.
        if (touched && !tookOne && cooldown == 0) {
            cooldown = CONTACT_GRACE;
            flash = 8;
            shake = 8;
            Draw.beep(Sounds.CRACK, 1f, 0.9f);
            Draw.note(0.5f);
            if (++faults >= 3) end(false);
        }
    }

    @Override
    boolean keyPressed(int key) {
        for (int[] keys : new int[][] {LEFT, RIGHT, UP, DOWN}) {
            for (int k : keys) {
                if (k == key) return true;
            }
        }
        return false;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4 + (shake > 0 ? (shake % 2) * 2 - 1 : 0), by = oy + 3;
        double cx = finished ? x : prevX + (x - prevX) * pt, cy = finished ? y : prevY + (y - prevY) * pt;
        double cam = Math.max(0, Math.min(DEPTH + 14 - VH, cy - VH * 0.45));
        g.fill(bx - 1, by - 1, bx + VW + 1, by + VH + 1, flash > 0 ? Draw.ALERT : Draw.EDGE);
        g.enableScissor(bx, by, bx + VW, by + VH);
        g.fill(bx, by, bx + VW, by + VH, 0xFF151D24);
        // Far wall of the gorge, dimmed, scrolling at half speed for depth.
        g.setColor(0.32f, 0.34f, 0.4f, 1f);
        int offset = (int) -((cam * 0.5) % 112);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) Draw.image(g, ROCK_FACE, bx + i * 160, by + offset + j * 112, 160, 112);
        }
        g.setColor(1f, 1f, 1f, 1f);
        // Near walls: the same rock as the far wall, lit, with a light rim on the left wall and a shadow on the right one.
        g.setColor(0.98f, 0.93f, 0.86f, 1f);
        for (int row = 0; row < VH; row += 2) {
            double wy = cam + row;
            int v = Math.floorMod((int) wy, 110), xl = (int) wallLeft(wy), xr = (int) wallRight(wy);
            Draw.piece(g, ROCK_FACE, bx, by + row, 30, v, xl, 2, 160, 112);
            Draw.piece(g, ROCK_FACE, bx + xr, by + row, 90, v, VW - xr, 2, 160, 112);
        }
        g.setColor(1f, 1f, 1f, 1f);
        for (int row = 0; row < VH; row += 2) {
            double wy = cam + row;
            int xl = (int) wallLeft(wy), xr = (int) wallRight(wy);
            g.fill(bx + xl - 2, by + row, bx + xl, by + row + 2, 0x50E8DCC8);
            g.fill(bx + xl, by + row, bx + xl + 1, by + row + 2, 0xC0100C0A);
            g.fill(bx + xr - 1, by + row, bx + xr, by + row + 2, 0xC0100C0A);
            g.fill(bx + xr, by + row, bx + xr + 3, by + row + 2, 0x70000000);
        }
        // Rock arch holding the piton; at the bottom, the gorge fades into darkness.
        g.fill(bx, by + (int) (0 - cam), bx + VW, by + (int) (6 - cam), 0xFF5E5246);
        g.fill(bx, by + (int) (6 - cam), bx + VW, by + (int) (7 - cam), 0xFF3A3129);
        for (int row = 0; row < VH; row += 2) {
            double dark = Math.max(0, Math.min(1, (cam + row - (WH - 40)) / 80.0));
            if (dark > 0) g.fill(bx, by + row, bx + VW, by + row + 2, ((int) (dark * 0xF0) << 24) | 0x05080B);
        }
        for (int k = 0; k < BARS; k++) {
            int ry = by + (int) Math.round(barY[k] - cam);
            if (ry < by - 30 || ry > by + VH + 30) continue;
            int wallX = (int) (barSide[k] == 0 ? wall(wallLeft, barY[k]) : wall(wallRight, barY[k]));
            int x0 = bx + (barSide[k] == 0 ? wallX - 4 : (int) rockX[k]), x1 = bx + (barSide[k] == 0 ? (int) rockX[k] : wallX + 4);
            // The slab, tiled at twice the resolution of the scene.
            int len = (x1 - x0) * 2;
            g.pose().pushPose();
            g.pose().translate(x0, ry - HALF - 1, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            for (int t = 0; t < len; t += 160) {
                int part = Math.min(160, len - t), u = (k * 47) % 160;
                Draw.piece(g, LEDGE, t, 0, Math.min(u, 160 - part), 0, part, 32, 160, 32);
            }
            g.pose().popPose();
            // The hollow under the bar, where the clue lies: a dark recess with softened corners.
            int nx = bx + (int) nicheX[k], bottom = ry + HALF;
            g.fill(nx - NICHE_W / 2 + 1, bottom - NICHE_D, nx + NICHE_W / 2 - 1, bottom - NICHE_D + 1, 0xFF14110E);
            g.fill(nx - NICHE_W / 2, bottom - NICHE_D + 1, nx + NICHE_W / 2, bottom + 2, 0xFF14110E);
            g.fill(nx - NICHE_W / 2 + 2, bottom - NICHE_D + 1, nx + NICHE_W / 2 - 2, bottom - NICHE_D + 2, 0xFF2A231D);
            // Boulder, drawn at the size of its hit circle (plus its outline).
            float d = (float) (rockR[k] * 2 + 3);
            g.pose().pushPose();
            g.pose().translate((float) (bx + rockX[k]) - d / 2, (float) (by + barY[k] - cam) - d * 34 / 36 / 2, 0);
            g.pose().scale(d / 36, d / 36, 1f);
            Draw.image(g, BOULDER, 0, 0, 36, 34);
            g.pose().popPose();
        }
        boolean pulse = (tic / 5) % 2 == 0;
        for (int i = 0; i < 3; i++) {
            int tx = bx + (int) traceX[i], ty = by + (int) (traceY[i] - cam);
            if (taken[i]) {
                g.setColor(1f, 1f, 1f, 0.35f);
                g.pose().pushPose();
                g.pose().translate(tx - 8, ty - 8, 0);
                g.pose().scale(0.5f, 0.5f, 1f);
                Draw.image(g, icon, 0, 0, 32, 32);
                g.pose().popPose();
                g.setColor(1f, 1f, 1f, 1f);
                text(g, "\u2714", tx - 3, ty - 4, Draw.GREEN);
                continue;
            }
            // A shadowed recess behind it, so that it sits in the rock rather than floating, and a glint around it.
            disc(g, tx, ty - 1, 9, 0x38000000);
            disc(g, tx, ty - 1, 6, 0x30000000);
            g.pose().pushPose();
            g.pose().translate(tx - 8, ty - 8, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            Draw.image(g, icon, 0, 0, 32, 32);
            g.pose().popPose();
            int phase = (tic + i * 13) % 40;
            if (phase < 12) {
                double angle = (tic + i * 13) * 0.4 + i;
                int gx = tx + (int) Math.round(Math.cos(angle) * 8), gy = ty + (int) Math.round(Math.sin(angle) * 7);
                int size = phase < 6 ? phase / 2 : (12 - phase) / 2, glint = 0xFFFFF6D8;
                g.fill(gx - size, gy, gx + size + 1, gy + 1, glint);
                g.fill(gx, gy - size, gx + 1, gy + size + 1, glint);
            }
        }
        int sx = bx + (int) Math.round(cx), sy = by + (int) Math.round(cy - cam);
        g.fill(bx + AX - 2, by + (int) (AY - 3 - cam), bx + AX + 3, by + (int) (AY + 1 - cam), 0xFFB8C0C8);
        for (int k = 0; k < pivots; k++) {
            double ex = k + 1 < pivots ? bx + pivotX[k + 1] : sx, ey = k + 1 < pivots ? by + pivotY[k + 1] - cam : sy;
            Draw.segment(g, bx + pivotX[k], by + pivotY[k] - cam, ex, ey, 1, 0xFFE9D8A8);
        }
        // Red while touching the rock or during the grace period that follows.
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float) -Math.toDegrees(theta)));
        // The sprite (32 x 48, drawn at 16 x 24) hangs from its harness; red while touching the rock or just after.
        if (cooldown > 0 && !finished && pulse) g.setColor(1f, 0.45f, 0.4f, 1f);
        g.pose().scale(0.5f, 0.5f, 1f);
        Draw.image(g, CLIMBER, -16, -26, 32, 48);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
        if (messageTicks > 0) {
            String s = message == 1 ? T.f("trace_taken") : T.f("too_fast");
            Draw.centre(g, s, sx, sy - 24, message == 1 ? Draw.GREEN : Draw.ALERT);
        }
        // Side strip: the whole gorge, with the traces and the climber.
        int mapX = bx + VW - 8, mapY = by + 4, mapH = VH - 18;
        g.fill(mapX - 1, mapY - 1, mapX + 6, mapY + mapH + 1, 0xC0101418);
        g.fill(mapX, mapY + (int) (cam / WH * mapH), mapX + 5, mapY + (int) ((cam + VH) / WH * mapH), 0x40FFFFFF);
        for (int k = 0; k < BARS; k++) {
            int ry = mapY + (int) (barY[k] / WH * mapH), end = mapX + (int) Math.round(rockX[k] / VW * 5);
            g.fill(barSide[k] == 0 ? mapX : end, ry, barSide[k] == 0 ? end : mapX + 5, ry + 1, 0xFF948674);
        }
        for (int i = 0; i < 3; i++) {
            int ty = mapY + (int) (traceY[i] / WH * mapH);
            g.fill(traceX[i] < AX ? mapX : mapX + 3, ty, traceX[i] < AX ? mapX + 2 : mapX + 5, ty + 2, taken[i] ? Draw.GREEN : Draw.YELLOW);
        }
        int cyMap = mapY + (int) (cy / WH * mapH);
        g.fill(mapX + 1, cyMap - 1, mapX + 4, cyMap + 2, 0xFFFFFFFF);
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.fill(bx + 2, by + 2, bx + 84, by + 24, 0xA0101418);
        Draw.write(g, T.f("traces") + takenCount + " / 3", bx + 5, by + 4, Draw.TEXT, false);
        Draw.write(g, T.f("impacts") + faults + " / 3", bx + 5, by + 14, faults > 1 ? Draw.ALERT : Draw.GREY, false);
        String length = T.f("rope") + Math.round((rope + wrapped) / 10) + " m";
        int lw = Draw.textFont().width(length);
        g.fill(bx + VW - 14 - Math.max(lw, 44) - 4, by + 2, bx + VW - 12, by + 24, 0xA0101418);
        Draw.write(g, length, bx + VW - 14 - lw, by + 4, Draw.TEXT, false);
        double v = speed();
        gauge(g, bx + VW - 14 - 44, by + 15, 44, v / (GENTLE * 2), v > GENTLE ? Draw.YELLOW : Draw.GREEN);
        timeGauge(g, bx + 4, by + VH - 9, VW - 20, time / (double) DURATION);
        g.pose().popPose();
    }

    @Override
    String instruction() {
        return T.f("swing_from_boulder_to_boulder");
    }

    @Override
    Control[] controls() {
        return new Control[] {
                new Control(T.f("swing"), InputConstants.KEY_A, InputConstants.KEY_D, Draw.OR, Draw.ARROW_LEFT, Draw.ARROW_RIGHT),
                new Control(T.f("haul_in"), InputConstants.KEY_W, Draw.OR, Draw.ARROW_UP),
                new Control(T.f("pay_out"), InputConstants.KEY_S, Draw.OR, Draw.ARROW_DOWN, Draw.OR, Draw.MOUSE)};
    }
}
