package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

// Field photo: a local Pokémon roams a forest clearing. It walks, stops to sniff (slowing down first), dashes, comes
// close, or slips behind a bush; if the viewfinder stays on it too long it bolts, and every shutter click startles it.
// The focus is set with the wheel from the image itself, and must follow its distance. Three sharp, framed shots are
// asked for. The light fades as time goes. Each good shot becomes a polaroid.
final class PhotoGame extends MiniGame {
    static final ResourceLocation CLEARING = Draw.texture("photo_clearing"), BUSH = Draw.texture("photo_bush");
    static final String[] FALLBACK_SPECIES = {"rattata", "pidgey", "oddish", "zubat", "eevee", "pikachu"};
    static final int DURATION = 900, MAX_FAULTS = 3, SPREAD = 70;
    static final double[][] BUSHES = {{72, 0.32}, {208, 0.46}};
    final String species;
    final Random h;
    final List<double[]> polaroids = new ArrayList<>(); // number, scale, darkness, age, facing left, x, z
    int time = DURATION, faults, flash, burst, tic, pause, hiddenTicks, ending = -1, messageTicks;
    double x = 140, z = 0.4, tx, tz, speed, vx, focus = 0.1, nerves, bob;
    boolean hidden, running, facingLeft, close;
    String message = "";
    int messageColour;
    double mx, my;

    PhotoGame(Random h) {
        this.h = h;
        // The subject is a random suspect of the investigation (unrelated to the actual target).
        net.minecraft.nbt.ListTag suspects = FieldInvestigationsClient.state.getList("suspects", net.minecraft.nbt.Tag.TAG_COMPOUND);
        species = suspects.isEmpty() ? FALLBACK_SPECIES[h.nextInt(FALLBACK_SPECIES.length)] : suspects.getCompound(h.nextInt(suspects.size())).getString("species");
        tx = x;
        tz = z;
        nextAction();
    }

    // Where the subject stands on screen: its feet, and its size, both from its distance (z: 0 far, 1 near).
    static double feetY(double z) {
        return 72 + z * 40;
    }

    static double scaleOf(double z) {
        return 1.6 + z * 1.9;
    }

    // Whether a spot is hidden by one of the bushes (behind it and within its width).
    static boolean behindBush(double x, double z) {
        for (double[] b : BUSHES) {
            double half = 25 * (0.9 + b[1] * 0.8) + 10;
            if (z < b[1] + 0.02 && Math.abs(x - b[0]) < half) return true;
        }
        return false;
    }

    // What the subject does next: walk (often stopping to sniff), dash, come close, or hide behind a bush. Except when
    // hiding, it never stops where a bush hides it.
    void nextAction() {
        for (int attempt = 0; attempt < 30; attempt++) {
            pick();
            if (pause == -1 || !behindBush(tx, tz)) return;
        }
        tx = 140;
        tz = 0.7;
    }

    void pick() {
        running = false;
        close = false;
        int r = h.nextInt(100);
        if (r < 50) {
            tx = 20 + h.nextDouble() * 240;
            tz = h.nextDouble() * 0.6;
            speed = 1.3;
            pause = h.nextInt(10) < 4 ? 25 + h.nextInt(20) : 0;
        } else if (r < 70) {
            tx = x < 140 ? 170 + h.nextDouble() * 90 : 20 + h.nextDouble() * 90;
            tz = h.nextDouble() * 0.5;
            speed = 2.6;
            running = true;
            pause = 0;
        } else if (r < 82) {
            tx = 40 + h.nextDouble() * 200;
            tz = 0.82 + h.nextDouble() * 0.12;
            speed = 1.1;
            close = true;
            pause = 20;
        } else {
            hide(1.3);
        }
    }

    void hide(double pace) {
        double[] b = BUSHES[h.nextInt(BUSHES.length)];
        tx = b[0];
        tz = b[1] - 0.06;
        speed = pace;
        running = pace > 2;
        pause = -1; // -1: hide on arrival
    }

    @Override
    void tick() {
        tic++;
        if (flash > 0) flash--;
        if (burst > 0) burst--;
        if (messageTicks > 0) messageTicks--;
        for (double[] p : polaroids) p[3]++;
        if (ending >= 0) {
            if (++ending >= 80) {
                perfect = faults == 0;
                end(true);
            }
            return;
        }
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        bob = 0;
        if (hidden) {
            if (--hiddenTicks <= 0) {
                // Out again from behind the other bush.
                hidden = false;
                double[] b = BUSHES[x < 140 ? 1 : 0];
                x = b[0];
                z = b[1] - 0.06;
                running = false;
                close = false;
                tx = b[0] + (b[0] < 140 ? 45 : -45);
                tz = b[1] + 0.2 + h.nextDouble() * 0.2;
                speed = 1.3;
                pause = 10 + h.nextInt(15);
            }
            vx = 0;
            return;
        }
        double dx = tx - x, dz = (tz - z) * 160, d = Math.hypot(dx, dz);
        if (d > 1) {
            // Walking slows down before a stop: the stop can be seen coming.
            double v = speed * (running || pause == 0 ? 1 : Math.min(1, d / 22 + 0.2));
            // The last step lands on the target (a fast dash would otherwise overshoot it back and forth).
            if (v >= d) {
                vx = dx;
                x = tx;
                z = tz;
            } else {
                vx = dx / d * v;
                x += vx;
                z += dz / d * v / 160;
            }
            if (Math.abs(vx) > 0.05) facingLeft = vx < 0;
            if (running && tic % 4 == 0) Draw.beep(Sounds.BRUSH_SOUND, 1.6f, 0.2f);
        } else {
            vx = 0;
            if (pause == -1) {
                hidden = true;
                hiddenTicks = 40 + h.nextInt(25);
                Draw.beep(Sounds.BRANCH, 1.4f, 0.4f);
            } else if (pause > 0) {
                pause--;
                bob = Math.sin(tic * 0.6) * 0.8;
                if (tic % 20 == 0) Draw.beep(Sounds.RUB, 1.8f, 0.2f);
            } else {
                nextAction();
            }
        }
        // Watched too long, it bolts for a bush.
        boolean watched = framed(mx, my);
        nerves = watched ? nerves + 1 : Math.max(0, nerves - 0.5);
        if (nerves > 70) {
            nerves = 0;
            hide(3);
            say(T.f("it_bolted"), Draw.ALERT);
            Draw.beep(Sounds.BRANCH, 1f, 0.7f);
        }
    }

    double screenX() {
        return ox + x;
    }

    double screenY() {
        return oy + feetY(z) - 8 * scaleOf(z) + bob;
    }

    boolean framed(double px, double py) {
        double r = 7 * scaleOf(z) + 4;
        return !hidden && Math.abs(px - screenX()) < r && Math.abs(py - screenY()) < r;
    }

    // How far off the focus is, in pixels of doubling: the focus follows the subject's distance.
    double blur() {
        return Math.abs(focus - z) * 40;
    }

    double darkness() {
        return 1 - time / (double) DURATION;
    }

    void say(String s, int colour) {
        message = s;
        messageColour = colour;
        messageTicks = 40;
    }

    @Override
    void scrolled(double amount) {
        if (amount == 0 || finished || ending >= 0) return;
        double step = Screen.hasShiftDown() ? 0.015 : 0.05;
        focus = Math.max(0, Math.min(1, focus + (amount > 0 ? step : -step)));
        Draw.beep(Sounds.KNOB, 1.2f + (float) focus * 0.6f, 0.35f);
    }

    @Override
    void click(double px, double py) {
        if (finished || ending >= 0 || !Draw.in(px, py, ox, oy, AREA_W, AREA_H)) return;
        Draw.beep(Sounds.SHUTTER, 1.6f, 0.7f);
        burst = 4;
        nerves += 20;
        if (!framed(px, py)) {
            fault(T.f("badly_framed"));
            return;
        }
        if (blur() > 2.5) {
            fault(T.f("blurry_photo"));
            return;
        }
        polaroids.add(new double[] {polaroids.size(), scaleOf(z), darkness(), 0, facingLeft ? 1 : 0, x, z});
        say(T.f("sharp_photo"), Draw.GREEN);
        Draw.note(1f + polaroids.size() * 0.25f);
        if (polaroids.size() >= 3) {
            ending = 0;
            Draw.beep(Sounds.PAPER, 1f, 0.8f);
            return;
        }
        // The click startled it: off to a bush.
        nerves = 0;
        hide(2.6);
    }

    void fault(String why) {
        flash = 10;
        say(why, Draw.ALERT);
        Draw.note(0.5f);
        if (++faults >= MAX_FAULTS) end(false);
    }

    // The subject, turned the way it goes (portraits face left), blurred by the given amount.
    void subject(GuiGraphics g, int px, int py, float s, boolean left, double blur) {
        Blur.portrait(g, species, px, py, s, blur, !left);
    }

    @Override
    void draw(GuiGraphics g, int px, int py, float pt) {
        mx = px;
        my = py;
        int ax = ox, ay = oy;
        Draw.image(g, CLEARING, ax, ay, AREA_W, AREA_H);
        if (ending >= 0) {
            drawSpread(g, ax, ay);
            return;
        }
        // The bushes behind the subject, drawn first.
        for (double[] b : BUSHES) if (!hidden && b[1] <= z) bush(g, b, ox, oy);
        float s = (float) scaleOf(z);
        int sx = (int) Math.round(screenX() - 8 * s), sy = (int) Math.round(oy + feetY(z) - 16 * s + bob);
        if (!hidden) {
            // Out of focus: a real blur, wider the further off the focus is.
            subject(g, sx, sy, s, facingLeft, blur() >= 1 ? Math.min(8, blur()) : 0);
            if (nerves > 55) Draw.centre(g, "!", (int) screenX(), sy - 8, (tic / 4) % 2 == 0 ? Draw.ALERT : Draw.YELLOW);
        }
        // The bushes in front of the subject (nearer than it, or hiding it), drawn over it.
        g.pose().pushPose();
        g.pose().translate(0, 0, 220);
        for (double[] b : BUSHES) if (hidden || b[1] > z) bush(g, b, ox, oy);
        // The light fading: a deepening blue and grain.
        double dark = darkness();
        if (dark > 0.05) {
            g.fill(ax, ay, ax + AREA_W, ay + AREA_H, ((int) (dark * 150) << 24) | 0x0A1430);
            if (dark > 0.4) {
                Random grainRng = new Random(tic / 2);
                int n = (int) ((dark - 0.4) * 300);
                for (int i = 0; i < n; i++) {
                    int gx = ax + grainRng.nextInt(AREA_W), gy = ay + grainRng.nextInt(AREA_H);
                    g.fill(gx, gy, gx + 1, gy + 1, grainRng.nextBoolean() ? 0x30FFFFFF : 0x40000000);
                }
            }
        }
        if (burst > 0) g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0x90FFFFFF);
        if (flash > 0) {
            g.fill(ax, ay, ax + AREA_W, ay + 1, Draw.ALERT);
            g.fill(ax, ay + AREA_H - 1, ax + AREA_W, ay + AREA_H, Draw.ALERT);
        }
        // The viewfinder.
        if (!finished && Draw.in(px, py, ax, ay, AREA_W, AREA_H)) {
            int c = framed(px, py) && blur() <= 2.5 ? Draw.GREEN : Draw.TEXT;
            for (int cx = -1; cx <= 1; cx += 2) {
                for (int cy = -1; cy <= 1; cy += 2) {
                    int ex = px + cx * 22, ey = py + cy * 18;
                    g.fill(Math.min(ex, ex - cx * 7), cy < 0 ? ey : ey - 1, Math.max(ex, ex - cx * 7), cy < 0 ? ey + 1 : ey, c);
                    g.fill(cx < 0 ? ex : ex - 1, Math.min(ey, ey - cy * 7), cx < 0 ? ex + 1 : ex, Math.max(ey, ey - cy * 7), c);
                }
            }
            g.fill(px - 1, py, px + 2, py + 1, c);
            g.fill(px, py - 1, px + 1, py + 2, c);
        }
        drawPolaroids(g, ax, ay);
        // The counters.
        String right = T.f("failed_2") + faults + " / " + MAX_FAULTS;
        g.fill(ax + AREA_W - 10 - Draw.textFont().width(right), ay + 2, ax + AREA_W - 2, ay + 12, 0xC0101418);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 3, faults > 0 ? Draw.ALERT : Draw.GREY);
        String left = T.f("photos") + polaroids.size() + " / 3";
        g.fill(ax + 2, ay + 2, ax + 10 + Draw.textFont().width(left), ay + 12, 0xC0101418);
        text(g, left, ax + 6, ay + 3, Draw.TEXT);
        if (messageTicks > 0) {
            int w = Draw.textFont().width(message);
            g.fill(ax + AREA_W / 2 - w / 2 - 4, ay + 2, ax + AREA_W / 2 + w / 2 + 4, ay + 12, 0xC0101418);
            Draw.centre(g, message, ax + AREA_W / 2, ay + 3, messageColour);
        }
        g.pose().popPose();
    }

    void bush(GuiGraphics g, double[] b, int bx, int by) {
        double bs = 0.9 + b[1] * 0.8;
        int bw = (int) (50 * bs), bh = (int) (42 * bs);
        Draw.image(g, BUSH, bx + (int) b[0] - bw / 2, by + (int) feetY(b[1]) - bh + 4, bw, bh);
    }

    // One polaroid: the white frame, the shot itself (the clearing around the subject, as it was framed, darker if
    // taken late) and its handwritten caption.
    // Everything in a polaroid lies within a few units of depth, so that the next polaroid, stacked a little higher,
    // covers it entirely.
    void polaroid(GuiGraphics g, int x, int y, double[] p, boolean caption) {
        g.fill(x - 1, y - 1, x + 41, y + 49, 0x60000000);
        g.fill(x, y, x + 40, y + 48, 0xFFF4F0E6);
        double px = p[5], pz = p[6], s = p[1];
        // The crop: a window around the subject, kept inside the clearing.
        double cw = Math.min(AREA_W, 16 * s * 2.4), ch = cw * 32 / 34.0;
        double cx = Math.max(cw / 2, Math.min(AREA_W - cw / 2, Math.max(8 * s, Math.min(AREA_W - 8 * s, px))));
        double cy = Math.max(ch / 2, Math.min(AREA_H - ch / 2, feetY(pz) - 8 * s));
        double k = 34 / cw;
        g.enableScissor(x + 3, y + 3, x + 37, y + 35);
        g.pose().pushPose();
        g.pose().translate(x + 20, y + 19, 1);
        g.pose().scale((float) k, (float) k, 1f);
        g.pose().translate((float) -cx, (float) -cy, 0);
        Draw.image(g, CLEARING, 0, 0, AREA_W, AREA_H);
        for (double[] b : BUSHES) if (b[1] <= pz) bush(g, b, 0, 0);
        // The subject is rendered offscreen first, which a scissor would clip: it is laid down unclipped (the crop keeps
        // it inside the window).
        g.disableScissor();
        subject(g, (int) Math.round(px - 8 * s), (int) Math.round(feetY(pz) - 16 * s), (float) s, p[4] > 0, 0);
        g.pose().pushPose();
        g.pose().setIdentity();
        g.enableScissor(x + 3, y + 3, x + 37, y + 35);
        g.pose().popPose();
        for (double[] b : BUSHES) if (b[1] > pz) bush(g, b, 0, 0);
        g.pose().popPose();
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0, 0, 3);
        g.fill(x + 3, y + 3, x + 37, y + 35, ((int) (p[2] * 120) << 24) | 0x0A1430);
        if (caption) g.drawString(Draw.textFont(), "n° " + ((int) p[0] + 1), x + 3, y + 38, Draw.INK, false);
        g.pose().popPose();
    }

    // Taken shots slide down to the bottom right corner, stacked.
    void drawPolaroids(GuiGraphics g, int ax, int ay) {
        for (int i = 0; i < polaroids.size(); i++) {
            double[] p = polaroids.get(i);
            double t = Math.min(1, p[3] / 14.0);
            int x0 = ax + AREA_W / 2 - 20, y0 = ay + 30, x1 = ax + AREA_W - 46 - i * 8, y1 = ay + AREA_H - 52 + i * 2;
            g.pose().pushPose();
            g.pose().translate(0, 0, i * 8);
            polaroid(g, (int) (x0 + (x1 - x0) * t), (int) (y0 + (y1 - y0) * t), p, false);
            g.pose().popPose();
        }
    }

    // The end: the three polaroids spread across the clearing, captioned.
    void drawSpread(GuiGraphics g, int ax, int ay) {
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0x80101418);
        double t = Math.min(1, ending / 20.0);
        for (int i = 0; i < polaroids.size(); i++) {
            int x1 = ax + AREA_W / 2 - 70 + i * 50, y1 = ay + 30 + (i == 1 ? -6 : 0);
            int x0 = ax + AREA_W - 46, y0 = ay + AREA_H - 52;
            g.pose().pushPose();
            g.pose().translate(0, 0, i * 8);
            polaroid(g, (int) (x0 + (x1 - x0) * t), (int) (y0 + (y1 - y0) * t), polaroids.get(i), true);
            g.pose().popPose();
        }
    }

    @Override
    String instruction() {
        return T.f("a_local_pok_mon_roams");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("aim_shoot"), Draw.MOUSE), new Control(T.f("wheel_focus_shift_fine"), Draw.MOUSE)};
    }
}
