package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Colony counting: the dish lies in the dark and the mouse is a lens that lights what is under it. One strain is to be
// counted among look-alikes (a close colour, or the same colour in another shape). Clicks pin colonies to keep track; when
// the time is up (or the player is done), the count is set with the wheel or the arrows and confirmed.
final class CountGame extends MiniGame {
    static final ResourceLocation PETRI = Draw.texture("petri");
    // Strains: colour and shape (0 = round, 1 = star). Each round's target is one of the first three; the others are decoys.
    static final int[] COLOURS = {0xFF4A7AD9, 0xFF42B8C8, 0xFF4A7AD9, 0xFFEFE6C8, 0xFFE07A5F};
    static final int[] SHAPES = {0, 0, 1, 0, 1};
    static final int LENS = 18, DISH = 45, ROUNDS = 3, MAX_PINS = 30;
    final Random h;
    final List<int[]> colonies = new ArrayList<>(); // x, y (from the dish centre), strain, radius
    final List<int[]> pins = new ArrayList<>();
    int strain, answer, guess, round, errors, time, duration, pause, tic;
    boolean answering, lastOk;
    double mx, my;

    CountGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        colonies.clear();
        pins.clear();
        strain = h.nextInt(3);
        answer = 5 + round * 2 + h.nextInt(4);
        int decoys = 7 + round * 2 + h.nextInt(4);
        for (int i = 0; i < answer + decoys; i++) {
            int s = i < answer ? strain : pickDecoy();
            for (int attempt = 0; attempt < 120; attempt++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * (DISH - 4);
                int x = (int) Math.round(Math.cos(a) * r), y = (int) Math.round(Math.sin(a) * r);
                boolean free = true;
                for (int[] c : colonies) free &= Math.hypot(c[0] - x, c[1] - y) >= 9;
                if (free || attempt == 119) {
                    colonies.add(new int[] {x, y, s, 3 + h.nextInt(2)});
                    break;
                }
            }
        }
        duration = time = 260 - round * 30;
        answering = false;
        guess = 0;
        shownSound();
    }

    // Decoys lean on the look-alikes of the target: the close colour and the other shape come up most.
    int pickDecoy() {
        int s;
        do {
            s = h.nextInt(COLOURS.length);
        } while (s == strain);
        return s;
    }

    int dishX() {
        return ox + 76;
    }

    int dishY() {
        return oy + 60;
    }

    @Override
    void tick() {
        tic++;
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (!answering && --time <= 0) {
            answering = true;
            hiddenSound();
        } else if (!answering) {
            hurryTicks(time, duration);
        }
    }

    @Override
    void click(double x, double y) {
        if (pause > 0 || finished) return;
        if (!answering) {
            if (Draw.in(x, y, ox + 160, oy + 92, 112, 18)) {
                answering = true;
                hiddenSound();
                return;
            }
            double lx = x - dishX(), ly = y - dishY();
            if (Math.hypot(lx, ly) > DISH + 2) return;
            // A click near a pin takes it back; otherwise it pins the spot.
            for (int i = 0; i < pins.size(); i++) {
                if (Math.hypot(pins.get(i)[0] - lx, pins.get(i)[1] - ly) < 4) {
                    pins.remove(i);
                    Draw.click(0.7f);
                    return;
                }
            }
            if (pins.size() < MAX_PINS) {
                pins.add(new int[] {(int) Math.round(lx), (int) Math.round(ly)});
                Draw.click(1.3f);
            }
            return;
        }
        if (Draw.in(x, y, ox + 196, oy + 50, 20, 14)) adjust(1);
        else if (Draw.in(x, y, ox + 196, oy + 76, 20, 14)) adjust(-1);
        else if (Draw.in(x, y, ox + 160, oy + 92, 112, 18)) confirm();
    }

    void adjust(int d) {
        if (!answering || pause > 0) return;
        guess = Math.max(0, Math.min(40, guess + d));
        Draw.beep(Sounds.TICK, 1f + guess * 0.02f, 0.5f);
    }

    void confirm() {
        if (!answering || pause > 0 || finished) return;
        lastOk = guess == answer;
        pause = 40;
        if (lastOk) {
            Draw.note(1f + round * 0.25f);
            if (++round >= ROUNDS) {
                perfect = errors == 0;
                end(true);
            }
        } else {
            Draw.note(0.5f);
            if (++errors >= 2) end(false);
        }
    }

    @Override
    void scrolled(double amount) {
        if (amount != 0) adjust(amount > 0 ? 1 : -1);
    }

    @Override
    boolean keyPressed(int key) {
        if (key == InputConstants.KEY_UP) adjust(1);
        else if (key == InputConstants.KEY_DOWN) adjust(-1);
        else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) {
            if (answering) confirm();
            else if (pause == 0) {
                answering = true;
                hiddenSound();
            }
        } else return false;
        return true;
    }

    // A colony: a soft halo, a body, a darker rim; star-shaped strains have four short arms.
    static void colony(GuiGraphics g, int x, int y, int strain, int r) {
        int c = COLOURS[strain], rim = Draw.darken(c, 0.55);
        disc(g, x, y, r + 1, (c & 0x00FFFFFF) | 0x30000000);
        if (SHAPES[strain] == 1) {
            g.fill(x - r - 2, y, x + r + 3, y + 1, rim);
            g.fill(x, y - r - 2, x + 1, y + r + 3, rim);
            g.fill(x - r - 1, y - r - 1, x - r + 1, y - r + 1, rim);
            g.fill(x + r, y + r, x + r + 2, y + r + 2, rim);
            g.fill(x + r, y - r - 1, x + r + 2, y - r + 1, rim);
            g.fill(x - r - 1, y + r, x - r + 1, y + r + 2, rim);
        }
        disc(g, x, y, r, rim);
        disc(g, x, y, r - 1, c);
        g.fill(x - 1, y - r + 1, x + 1, y - r + 2, 0x80FFFFFF);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, float pt) {
        mx = x;
        my = y;
        int ax = ox, ay = oy, cx = dishX(), cy = dishY();
        g.fill(ax, ay, ax + AREA_W, ay + AREA_H, 0xFF141B23);
        g.pose().pushPose();
        g.pose().translate(cx - 54, cy - 54, 0);
        g.pose().scale(1.125f, 1.125f, 1f);
        Draw.image(g, PETRI, 0, 0, 96, 96);
        g.pose().popPose();
        boolean exploring = !answering && pause == 0 && !finished;
        boolean reveal = pause > 0 || finished;
        double lx = x - cx, ly = y - cy;
        boolean lensOn = exploring && Math.hypot(lx, ly) < DISH + LENS;
        for (int[] c : colonies) {
            boolean lit = reveal || lensOn && Math.hypot(c[0] - lx, c[1] - ly) < LENS + 2;
            if (lit) colony(g, cx + c[0], cy + c[1], c[2], c[3]);
        }
        // The dark: everything in the dish outside the lens.
        if (!reveal) {
            for (int row = -DISH - 4; row <= DISH + 4; row++) {
                int half = (int) Math.round(Math.sqrt(Math.max(0, (DISH + 4) * (DISH + 4) - row * row)));
                int x0 = cx - half, x1 = cx + half + 1;
                int dy = (int) Math.round(cy + row - y);
                if (lensOn && Math.abs(dy) < LENS) {
                    int lh = (int) Math.round(Math.sqrt(LENS * LENS - dy * dy)), lx0 = x - lh, lx1 = x + lh + 1;
                    if (lx0 > x0) g.fill(x0, cy + row, Math.min(x1, lx0), cy + row + 1, 0xEE0A0E13);
                    if (lx1 < x1) g.fill(Math.max(x0, lx1), cy + row, x1, cy + row + 1, 0xEE0A0E13);
                } else {
                    g.fill(x0, cy + row, x1, cy + row + 1, 0xEE0A0E13);
                }
            }
            if (lensOn) Draw.circle(g, x - 1, y - 1, LENS, 1, 0x90E8F4F2);
        }
        // Pins: a red head on a short shadow.
        for (int[] p : pins) {
            int px = cx + p[0], py = cy + p[1];
            g.fill(px, py, px + 2, py + 2, 0x80000000);
            g.fill(px - 1, py - 3, px + 2, py, 0xFFD8433B);
            g.fill(px - 1, py - 3, px, py - 2, 0xFFFF9A90);
        }
        if (reveal) {
            // Verdict: every colony of the strain gets a ring.
            for (int[] c : colonies) {
                if (c[2] == strain) Draw.circle(g, cx + c[0] - 1, cy + c[1] - 1, c[3] + 3, 1, lastOk ? Draw.GREEN : Draw.YELLOW);
            }
        }

        // Right: the strain to count, the time, then the count.
        int rx = ax + 160;
        text(g, T.f("count_these"), rx, ay + 22, Draw.GREY);
        colony(g, rx + 100, ay + 26, strain, 4);
        String left = T.f("dish") + Math.min(ROUNDS, round + (finished || pause > 0 && lastOk ? 0 : 1)) + " / " + ROUNDS, right = T.f("mistakes") + errors + " / 2";
        text(g, left, ax + 6, ay + 4, Draw.TEXT);
        text(g, right, ax + AREA_W - 6 - Draw.textFont().width(right), ay + 4, errors > 0 ? Draw.ALERT : Draw.GREY);
        if (exploring) {
            g.fill(rx, ay + 38, rx + 112, ay + 41, Draw.EDGE);
            g.fill(rx + 1, ay + 39, rx + 1 + 110 * time / duration, ay + 40, time * 4 > duration ? Draw.ACCENT : Draw.ALERT);
            text(g, T.f("pins") + pins.size(), rx, ay + 48, Draw.GREY);
            Draw.button(g, rx, ay + 92, 112, 18, T.f("done"), Draw.in(x, y, rx, ay + 92, 112, 18), true, 0xFF7C8FA3);
        } else if (answering && pause == 0) {
            Draw.centre(g, T.f("how_many_were_there"), rx + 56, ay + 38, Draw.YELLOW);
            boolean up = Draw.in(x, y, ax + 196, ay + 50, 20, 14), down = Draw.in(x, y, ax + 196, ay + 76, 20, 14);
            Draw.button(g, ax + 196, ay + 50, 20, 14, "▲", up, true, 0xFF7C8FA3);
            Draw.centre(g, String.valueOf(guess), ax + 206, ay + 66, Draw.TEXT);
            Draw.button(g, ax + 196, ay + 76, 20, 14, "▼", down, true, 0xFF7C8FA3);
            Draw.button(g, rx, ay + 92, 112, 18, T.f("confirm"), Draw.in(x, y, rx, ay + 92, 112, 18), true, Draw.GREEN);
        } else if (pause > 0 || finished) {
            String state = lastOk ? T.f("correct") + answer + T.f("colonies") : T.f("wrong_there_were") + answer;
            Draw.centre(g, state, rx + 56, ay + 60, lastOk ? Draw.GREEN : Draw.ALERT);
        }
    }

    @Override
    String instruction() {
        return T.f("a_dish_teeming_with_colonies");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("pin"), Draw.MOUSE), new Control(T.f("number"), Draw.ARROW_UP, Draw.ARROW_DOWN),
                new Control(T.f("confirm"), InputConstants.KEY_RETURN)};
    }
}
