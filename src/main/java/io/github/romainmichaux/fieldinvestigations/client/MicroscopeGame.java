package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

// Microscope: the eyepiece shows a patch of a larger slide, dragged around like the stage. The wheel turns the focus
// (shift for the fine knob), with no gauge: the image itself sharpens, and the sharp point drifts a little as the slide
// warms. About thirty cells, none quite alike; one shows the anomaly on the card (two nuclei, a torn membrane, an odd
// tint), sometimes on a deeper plane of the slide that only comes sharp at another focus. Clicking it zooms in on the
// stained cell, and its photo is pinned on the board.
final class MicroscopeGame extends MiniGame {
    static final int CX = 140, CY = 63, R = 52, SLIDE_W = 230, SLIDE_H = 180, CELLS = 28, SLIDES = 3, MAX_ERRORS = 3, ZOOM = 56;
    static final double DEPTH = 18;
    static final int[] DURATION = {640, 600, 560};
    static final String[] ANOMALY = {"two_nuclei", "torn_membrane", "odd_tint"};

    static final class Cell {
        double x, y, rx, ry, light, nx, ny;
        int plane, kind; // kind: 0 normal, 1 two nuclei, 2 torn membrane, 3 odd tint; 4 a harmless lobed nucleus
        int tear; // direction of the tear (degrees)
    }

    final Random h;
    final List<Cell> cells = new ArrayList<>();
    final List<double[]> specks = new ArrayList<>();
    final int[] photos = {-1, -1, -1};
    int slide, errors, time, duration, tic, flash, zoom, messageTicks;
    double focus, sharp, vx, vy, pressX, pressY, lastX, lastY, mx, my;
    boolean pressed, dragging;
    String message = "";
    Cell picked;

    MicroscopeGame(Random h) {
        this.h = h;
        newSlide();
    }

    void newSlide() {
        cells.clear();
        specks.clear();
        for (int i = 0; i < 70; i++) specks.add(new double[] {h.nextDouble() * SLIDE_W, h.nextDouble() * SLIDE_H, h.nextInt(3)});
        for (int i = 0; i < CELLS; i++) {
            for (int attempt = 0; attempt < 200; attempt++) {
                Cell c = new Cell();
                c.rx = 6 + h.nextDouble() * 3;
                c.ry = c.rx * (0.8 + h.nextDouble() * 0.3);
                c.x = 12 + h.nextDouble() * (SLIDE_W - 24);
                c.y = 12 + h.nextDouble() * (SLIDE_H - 24);
                boolean free = true;
                for (Cell o : cells) free &= Math.hypot(o.x - c.x, o.y - c.y) > c.rx + o.rx + 5;
                if (!free && attempt < 199) continue;
                c.light = 0.92 + h.nextDouble() * 0.16;
                c.nx = (h.nextDouble() - 0.5) * c.rx * 0.5;
                c.ny = (h.nextDouble() - 0.5) * c.ry * 0.5;
                c.plane = h.nextInt(4) == 0 ? 1 : 0;
                c.kind = h.nextInt(5) == 0 ? 4 : 0;
                cells.add(c);
                break;
            }
        }
        // The anomaly of this slide, on the deep plane from the second slide on (always on the third).
        Cell odd = cells.get(h.nextInt(cells.size()));
        odd.kind = slide + 1;
        odd.tear = h.nextInt(360);
        odd.plane = slide == 2 || slide == 1 && h.nextBoolean() ? 1 : 0;
        vx = SLIDE_W / 2.0;
        vy = SLIDE_H / 2.0;
        sharp = 30 + h.nextDouble() * 20;
        focus = sharp + (h.nextBoolean() ? 1 : -1) * (22 + h.nextInt(10));
        duration = time = DURATION[slide];
        picked = null;
        zoom = 0;
        shownSound();
    }

    // How far a cell is from focus; the sharp point drifts slowly, as the slide warms under the lamp.
    double blur(Cell c) {
        double s = sharp + 2.5 * Math.sin(tic * 0.013) + c.plane * DEPTH;
        return Math.abs(focus - s);
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (messageTicks > 0) messageTicks--;
        if (zoom > 0) {
            if (++zoom >= ZOOM) {
                photos[slide] = picked.kind;
                if (++slide >= SLIDES) {
                    perfect = errors == 0;
                    end(true);
                } else {
                    newSlide();
                }
            }
            return;
        }
        if (--time <= 0) {
            mistake(T.f("time_s_up"));
            if (!finished) newSlide();
            return;
        }
        hurryTicks(time, duration);
    }

    void mistake(String why) {
        flash = 10;
        Draw.note(0.5f);
        say(why);
        if (++errors >= MAX_ERRORS) end(false);
    }

    void say(String s) {
        message = s;
        messageTicks = 40;
    }

    @Override
    void scrolled(double amount) {
        if (amount == 0 || zoom > 0 || finished) return;
        double step = Screen.hasShiftDown() ? 1 : 4;
        focus = Math.max(-10, Math.min(110, focus + (amount > 0 ? step : -step)));
        Draw.beep(Sounds.KNOB, Screen.hasShiftDown() ? 1.5f : 1f, 0.4f);
    }

    boolean inEyepiece(double x, double y) {
        return Math.hypot(x - ox - CX, y - oy - CY) < R;
    }

    @Override
    void click(double x, double y) {
        if (zoom > 0 || finished || !inEyepiece(x, y)) return;
        pressed = true;
        dragging = false;
        pressX = lastX = x;
        pressY = lastY = y;
    }

    @Override
    void released(double x, double y) {
        if (!pressed) return;
        pressed = false;
        if (dragging || zoom > 0 || finished) return;
        // A click (no drag): the cell under the pointer, if it is sharp enough to judge.
        double sx = vx + (x - ox - CX), sy = vy + (y - oy - CY);
        for (Cell c : cells) {
            if (Math.hypot((sx - c.x) / (c.rx + 2), (sy - c.y) / (c.ry + 2)) > 1) continue;
            if (blur(c) > 2.5) {
                say(T.f("too_blurry_to_tell"));
                Draw.click(0.7f);
                return;
            }
            if (c.kind >= 1 && c.kind <= 3) {
                picked = c;
                zoom = 1;
                Draw.beep(Sounds.SHUTTER, 1f, 0.8f);
                Draw.note(1f + slide * 0.25f);
            } else {
                mistake(T.f("a_normal_cell"));
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        if (pressed) {
            if (!dragging && Math.hypot(x - pressX, y - pressY) > 3) dragging = true;
            if (dragging) {
                vx = Math.max(R * 0.6, Math.min(SLIDE_W - R * 0.6, vx - (x - lastX)));
                vy = Math.max(R * 0.6, Math.min(SLIDE_H - R * 0.6, vy - (y - lastY)));
            }
            lastX = x;
            lastY = y;
        }
        int ax = ox, ay = oy, cx = ax + CX, cy = ay + CY;
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF0A0E12);
        // The field of view: the lit slide, its specks and cells, then the dark around the round eyepiece.
        g.enableScissor(cx - R, Math.max(ay, cy - R), cx + R + 1, Math.min(ay + AREA_H, cy + R + 1));
        disc(g, cx, cy, R, 0xFFCFE8DC);
        double global = Math.abs(focus - sharp);
        for (double[] s : specks) {
            int sx = cx + (int) Math.round(s[0] - vx), sy = cy + (int) Math.round(s[1] - vy);
            if (Math.hypot(sx - cx, sy - cy) > R - 1) continue;
            int a = global < 4 ? 0x70 : 0x30;
            g.fill(sx, sy, sx + 1 + (int) s[2] / 2, sy + 1, (a << 24) | 0x4A6A60);
        }
        for (Cell c : cells) {
            double sx = cx + c.x - vx, sy = cy + c.y - vy;
            if (Math.hypot(sx - cx, sy - cy) > R + c.rx + 6) continue;
            if (c == picked && zoom > 0) continue;
            drawCell(g, sx, sy, c, blur(c), 1, false);
        }
        mask(g, cx, cy);
        g.disableScissor();
        Draw.circle(g, cx - 0.5, cy - 0.5, R, 2, flash > 0 ? Draw.ALERT : 0xFF2A3440);
        if (zoom > 0 && picked != null) drawZoom(g, cx, cy, pt);

        drawCard(g, ax, ay);
        drawBoard(g, ax, ay);
        String left = T.f("slide") + Math.min(SLIDES, slide + 1) + " / " + SLIDES, right = T.f("mistakes") + errors + " / " + MAX_ERRORS;
        text(g, left, ax + 4, ay + 2, Draw.TEXT);
        text(g, right, ax + AREA_W - 4 - Draw.textFont().width(right), ay + 2, errors > 0 ? Draw.ALERT : Draw.GREY);
        if (messageTicks > 0) Draw.centre(g, message, cx, ay + AREA_H - 10, Draw.YELLOW);
        double part = zoom > 0 ? 0 : time / (double) duration;
        g.fill(ax + 4, ay + AREA_H - 2, ax + 4 + (int) ((AREA_W - 8) * part), ay + AREA_H - 1, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
    }

    // Black around the eyepiece's circle, over whatever cells spill past it.
    void mask(GuiGraphics g, int cx, int cy) {
        int pad = 20;
        for (int r = -R - pad; r <= R + pad; r++) {
            int yy = cy + r;
            if (yy < oy || yy >= oy + AREA_H) continue;
            if (Math.abs(r + 0.5) >= R) {
                g.fill(cx - R - pad, yy, cx + R + pad, yy + 1, 0xFF0A0E12);
                continue;
            }
            int half = (int) Math.round(Math.sqrt(R * R - (r + 0.5) * (r + 0.5)));
            g.fill(cx - R - pad, yy, cx - half, yy + 1, 0xFF0A0E12);
            g.fill(cx + half, yy, cx + R + pad, yy + 1, 0xFF0A0E12);
        }
    }

    // A cell. Out of focus it is a pale, spreading blob; sharp, its membrane, body and nucleus show, with its anomaly.
    // Stained (in the zoom and the photos) it takes the dye's violet.
    void drawCell(GuiGraphics g, double sx, double sy, Cell c, double b, double k, boolean stained) {
        int x = (int) Math.round(sx), y = (int) Math.round(sy);
        double rx = c.rx * k, ry = c.ry * k;
        if (b > 2.5 && !stained) {
            double spread = Math.min(6, b / 3);
            int a = (int) (0x70 * Math.max(0.35, 1 - b / 30));
            ellipse(g, x, y, rx + spread, ry + spread, (a << 24) | 0x5FA888);
            ellipse(g, x, y, rx * 0.8, ry * 0.8, (a << 24) | 0x5FA888);
            if (b < 9) ellipse(g, x + (int) (c.nx * k), y + (int) (c.ny * k), 3 * k, 3 * k, ((a / 2) << 24) | 0x2A5C48);
            return;
        }
        int rim = stained ? 0xFF6A3A90 : 0xFF3F8F70, nucleus = stained ? 0xFF3E1A60 : 0xFF2A5C48;
        int body = stained ? 0xFFE6CCF0 : Draw.darken(0xFF9FD8B8, c.light);
        if (c.kind == 3) body = stained ? 0xFFF0B8D0 : Draw.darken(0xFFB8C8B0, c.light);
        ellipse(g, x, y, rx + k, ry + k, rim);
        ellipse(g, x, y, rx, ry, body);
        if (c.kind == 2) {
            // The tear: a gap in the membrane and the content leaking out.
            double a = Math.toRadians(c.tear);
            int tx = x + (int) Math.round(Math.cos(a) * rx), ty = y + (int) Math.round(Math.sin(a) * ry);
            int s = (int) Math.max(1, Math.round(k * 1.5));
            g.fill(tx - s, ty - s, tx + s + 1, ty + s + 1, body);
            for (int d = 1; d <= 3; d++) {
                int px = x + (int) Math.round(Math.cos(a + d * 0.3) * (rx + 2 * k + d * k)), py = y + (int) Math.round(Math.sin(a + d * 0.3) * (ry + 2 * k + d * k));
                g.fill(px, py, px + (int) Math.max(1, k), py + (int) Math.max(1, k), rim);
            }
        }
        int nx = x + (int) Math.round(c.nx * k), ny = y + (int) Math.round(c.ny * k);
        double nr = 1.8 * k;
        if (c.kind == 1) {
            ellipse(g, nx - (int) Math.round(2 * k), ny - (int) Math.round(k), nr * 0.85, nr * 0.85, nucleus);
            ellipse(g, nx + (int) Math.round(2 * k), ny + (int) Math.round(k), nr * 0.85, nr * 0.85, nucleus);
        } else if (c.kind == 4) {
            // A harmless decoy: a lobed nucleus, close to two nuclei at a glance.
            ellipse(g, nx, ny, nr * 1.2, nr * 0.85, nucleus);
            ellipse(g, nx + (int) Math.round(1.5 * k), ny - (int) Math.round(k), nr * 0.6, nr * 0.6, nucleus);
        } else {
            ellipse(g, nx, ny, nr, nr, nucleus);
        }
        g.fill(x - (int) (rx / 2), y - (int) (ry / 2), x - (int) (rx / 2) + (int) Math.max(1, k), y - (int) (ry / 2) + 1, 0x80FFFFFF);
    }

    static void ellipse(GuiGraphics g, int cx, int cy, double rx, double ry, int colour) {
        int h = (int) Math.round(ry);
        for (int dy = -h; dy <= h; dy++) {
            double t = 1 - (dy * dy) / Math.max(0.5, ry * ry);
            if (t < 0) continue;
            int dx = (int) Math.round(rx * Math.sqrt(t));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, colour);
        }
    }

    // The reward: the picked cell grows to the middle of the eyepiece, stained, with a handwritten note; then its photo
    // flies to the board.
    void drawZoom(GuiGraphics g, int cx, int cy, float pt) {
        double t = Math.min(1, (zoom + pt) / 18.0), fly = Math.max(0, (zoom + pt - 40) / (ZOOM - 40.0));
        double sx = cx + (picked.x - vx) * (1 - t), sy = cy + (picked.y - vy) * (1 - t);
        double k = 1 + 3 * t;
        if (fly > 0) {
            int[] slot = slot(slide);
            sx += (slot[0] + 12 - sx) * fly;
            sy += (slot[1] + 14 - sy) * fly;
            k += (1 - k) * fly;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 40);
        drawCell(g, sx, sy, picked, 0, k, true);
        if (fly == 0 && t >= 1) {
            String note = T.f(ANOMALY[picked.kind - 1]);
            int nw = Draw.textFont().width(note);
            g.fill(cx - nw / 2 - 3, cy + 34, cx + nw / 2 + 3, cy + 45, 0xE0F4ECD8);
            g.drawString(Draw.textFont(), note, cx - nw / 2, cy + 36, Draw.INK_RED, false);
            Draw.segment(g, cx + 10, cy + 33, cx + 18, cy + 22, 1, Draw.INK_RED);
        }
        g.pose().popPose();
    }

    int[] slot(int i) {
        return new int[] {ox + 200, oy + 16 + i * 32};
    }

    // Left: the card of what to look for this slide.
    void drawCard(GuiGraphics g, int ax, int ay) {
        if (slide >= SLIDES) return;
        int x = ax + 6, y = ay + 14;
        g.fill(x, y, x + 76, y + 52, 0xFFE8DFC8);
        g.fill(x, y, x + 76, y + 1, 0xFFF8F0DC);
        g.drawString(Draw.textFont(), T.f("look_for"), x + 4, y + 3, Draw.INK_LIGHT, false);
        Cell sample = new Cell();
        sample.rx = 7;
        sample.ry = 6;
        sample.light = 1;
        sample.kind = slide + 1;
        sample.tear = 300;
        drawCell(g, x + 38, y + 21, sample, 0, 1.1, false);
        Draw.paragraph(g, T.f(ANOMALY[slide]), x + 4, y + 33, 70, Draw.INK);
        // Where the eyepiece is on the slide, and which plane the focus is on.
        int mxs = ax + 6, mys = ay + 70, mw = 40, mh = 30;
        g.fill(mxs, mys, mxs + mw, mys + mh, 0xFF1E262E);
        g.fill(mxs + 1, mys + 1, mxs + mw - 1, mys + mh - 1, 0xFFB8CCC4);
        int px = mxs + (int) (vx / SLIDE_W * mw), py = mys + (int) (vy / SLIDE_H * mh);
        Draw.circle(g, px - 0.5, py - 0.5, R * mw / (double) SLIDE_W, 1, 0xFF2A3440);
        g.drawString(Draw.textFont(), focusOnDeep() ? T.f("plane_deep") : T.f("plane_surface"), mxs, mys + mh + 4, focusOnDeep() ? Draw.ACCENT : Draw.GREY, false);
    }

    boolean focusOnDeep() {
        return focus > sharp + DEPTH / 2;
    }

    // Right: the board where each slide's photo is pinned.
    void drawBoard(GuiGraphics g, int ax, int ay) {
        for (int i = 0; i < SLIDES; i++) {
            int[] s = slot(i);
            if (photos[i] < 0) {
                g.fill(s[0], s[1], s[0] + 74, s[1] + 28, 0x30FFFFFF);
                continue;
            }
            g.fill(s[0], s[1], s[0] + 74, s[1] + 28, 0xFFF2EEE4);
            g.fill(s[0] + 2, s[1] + 2, s[0] + 24, s[1] + 26, 0xFF101418);
            Cell c = new Cell();
            c.rx = 7;
            c.ry = 6;
            c.light = 1;
            c.kind = photos[i];
            c.tear = 300;
            drawCell(g, s[0] + 13, s[1] + 14, c, 0, 1, true);
            Draw.paragraph(g, T.f(ANOMALY[photos[i] - 1]), s[0] + 27, s[1] + 5, 46, Draw.INK);
            g.fill(s[0] + 36, s[1] - 2, s[0] + 39, s[1] + 1, Draw.RED);
        }
    }

    @Override
    String instruction() {
        return T.f("a_drop_on_the_slide");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("wheel_focus_shift_fine"), Draw.MOUSE), new Control(T.f("drag_the_slide_click_a"), Draw.MOUSE)};
    }
}
