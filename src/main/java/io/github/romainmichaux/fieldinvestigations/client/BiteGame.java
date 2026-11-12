package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Bite mark: slide the casting strip under the bite, then press it up to see whether it fits.
final class BiteGame extends MiniGame {
    // The bite has 7 teeth; the strip has 13 and can take 7 positions. Exactly one matches the bite.
    static final int N = 7, M = 13, MAX = 6, U = 7, COL = 12, REST = 30;
    final Random h;
    final int[] bite = new int[N], slider = new int[M];
    int position, round, faults, time, duration, flash, pause, rise = -1;
    boolean fitted;

    BiteGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        int good;
        do {
            for (int i = 0; i < N; i++) bite[i] = 1 + h.nextInt(5);
            for (int j = 0; j < M; j++) slider[j] = 1 + h.nextInt(5);
            good = h.nextInt(M - N + 1);
            for (int i = 0; i < N; i++) slider[good + i] = MAX - bite[i];
        } while (solutions() != 1);
        do {
            position = h.nextInt(M - N + 1);
        } while (position == good);
        duration = time = 420 - round * 80;
        rise = -1;
        fitted = false;
    }

    // Gap between bite tooth i and the strip tooth under it, at rest. All gaps equal REST means a perfect fit.
    int gap(int i, int pos) {
        return REST + (MAX - bite[i] - slider[pos + i]) * U;
    }

    int travel(int pos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < N; i++) min = Math.min(min, gap(i, pos));
        return min;
    }

    boolean fits(int pos) {
        for (int i = 0; i < N; i++) {
            if (gap(i, pos) != REST) return false;
        }
        return true;
    }

    int solutions() {
        int n = 0;
        for (int pos = 0; pos <= M - N; pos++) n += fits(pos) ? 1 : 0;
        return n;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (rise >= 0) {
            // The strip rises until it hits, then every tooth must be in contact.
            rise++;
            if (rise == 8) {
                if (fits(position)) {
                    fitted = true;
                    Draw.beep(Sounds.STONE, 0.6f, 1f);
                    Draw.note(1f + round * 0.3f);
                    if (++round >= 2) {
                        perfect = faults == 0;
                        end(true);
                    } else {
                        pause = 34;
                    }
                } else {
                    flash = 10;
                    Draw.beep(Sounds.STONE, 0.6f, 1f);
                    Draw.note(0.5f);
                    if (++faults >= 2) end(false);
                }
            } else if (rise >= 22 && !fitted) {
                rise = -1; // it drops back: the player can try another position
            } else if (fitted && pause > 0 && --pause == 0 && !finished) {
                newRound(); // the strip stays fitted for a moment, then the next bite comes in
            }
            return;
        }
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (--time <= 0) {
            flash = 8;
            Draw.note(0.5f);
            if (++faults >= 2) end(false);
            else pause = 24;
        } else {
            hurryTicks(time, duration);
        }
    }

    void slide(int direction) {
        int next = Math.max(0, Math.min(M - N, position + direction));
        if (next == position) return;
        position = next;
        Draw.beep(Sounds.STONE, 0.9f + position * 0.05f, 0.6f);
    }

    boolean free() {
        return pause == 0 && rise < 0 && !finished;
    }

    // A tooth: a solid body and a tip that tapers over its last four pixels (pointing down or up).
    static void tooth(GuiGraphics g, int x, int root, int tip, int colour, int shadow) {
        int l = COL - 1, direction = tip > root ? 1 : -1, length = Math.abs(tip - root);
        for (int k = 0; k < length; k++) {
            int fromTip = length - 1 - k, r = fromTip < 2 ? 3 : fromTip < 4 ? 1 : 0, y = direction > 0 ? root + k : root - 1 - k;
            g.fill(x + r, y, x + l - r, y + 1, colour);
            g.fill(x + l - r - 2, y, x + l - r, y + 1, shadow);
        }
    }

    @Override
    void click(double mx, double my) {
        if (!free()) return;
        int tx = ox + 172;
        if (Draw.in(mx, my, tx, oy + 40, 50, 18)) slide(1);
        else if (Draw.in(mx, my, tx + 54, oy + 40, 50, 18)) slide(-1);
        else if (Draw.in(mx, my, tx, oy + 66, 104, 22)) rise = 0;
    }

    @Override
    boolean keyPressed(int keyPressed) {
        if (!free()) return false;
        if (keyPressed == 263) slide(1);
        else if (keyPressed == 262) slide(-1);
        else if (keyPressed == 32 || keyPressed == 257) rise = 0;
        else return false;
        return true;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        int travel = travel(position);
        int raised = rise < 0 ? 0 : Math.min(travel, rise * 5);
        boolean hitStop = rise >= 8 && !fitted;
        int x0 = ax + 2 + 3 * COL, top = ay + 6, base = top + MAX * U + REST + 6 - raised;
        g.fill(x0 - 4, top - 5, x0 + N * COL + 3, top + 1, 0xFFC97A70);
        g.fill(x0 - 4, top - 5, x0 + N * COL + 3, top - 3, 0xFFE0A096);
        for (int i = 0; i < N; i++) {
            int x = x0 + i * COL, bottom = top + bite[i] * U;
            boolean contact = hitStop && gap(i, position) == travel;
            tooth(g, x, top, bottom, contact ? Draw.ALERT : fitted ? 0xFFBFE8C8 : 0xFFF4EEDC, contact ? 0xFF8E2B26 : fitted ? 0xFF8FC8A0 : 0xFFCFC4A6);
        }
        // The casting strip: thirteen teeth, only seven of which sit under the bite.
        g.fill(ax + 2, base - 1, ax + 2 + M * COL - 1, base + 6, 0xFFC97A70);
        g.fill(ax + 2, base + 4, ax + 2 + M * COL - 1, base + 6, 0xFF9C5850);
        for (int j = 0; j < M; j++) {
            int slot = j - position + 3;
            if (slot < 0 || slot >= M) continue;
            int x = ax + 2 + slot * COL, tip = base - slider[j] * U;
            boolean below = slot >= 3 && slot < 3 + N;
            boolean contact = hitStop && below && gap(slot - 3, position) == travel;
            tooth(g, x, base - 1, tip - 1, contact ? Draw.ALERT : !below ? 0xFF6E7A88 : fitted ? 0xFF8FD8A0 : 0xFFF2B56A,
                    contact ? 0xFF8E2B26 : !below ? 0xFF55606C : fitted ? 0xFF5FA870 : 0xFFC98A3A);
        }
        g.fill(x0 - 2, ay + H - 6, x0 + N * COL + 1, ay + H - 4, Draw.YELLOW);
        int tx = ox + 172;
        text(g, T.f("bite") + Math.min(2, round + 1) + " / 2", tx, oy + 5, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 2", tx, oy + 16, faults > 0 ? Draw.ALERT : Draw.GREY);
        text(g, fitted ? T.f("it_fits") : hitStop ? T.f("red_teeth_are_blocking") : T.f("slide_the_strip"), tx, oy + 29, fitted ? Draw.GREEN : hitStop ? Draw.ALERT : Draw.YELLOW);
        boolean free = free();
        Draw.button(g, tx, oy + 40, 50, 18, "<", free && Draw.in(mx, my, tx, oy + 40, 50, 18), free && position < M - N, 0xFF7C8FA3);
        Draw.button(g, tx + 54, oy + 40, 50, 18, ">", free && Draw.in(mx, my, tx + 54, oy + 40, 50, 18), free && position > 0, 0xFF7C8FA3);
        Draw.button(g, tx, oy + 66, 104, 22, T.f("press"), free && Draw.in(mx, my, tx, oy + 66, 104, 22), free, Draw.GREEN);
        timeGauge(g, tx, oy + 104, 100, pause > 0 ? 0 : time / (double) duration);
    }

    @Override
    String instruction() {
        return T.f("slide_the_orange_strip_buttons");
    }
}
