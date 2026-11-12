package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Empreinte dentaire : glisser la réglette de moulage sous la morsure, puis la presser pour voir si elle s'emboîte
final class BiteGame extends MiniGame {
    // La morsure a 7 dents ; la réglette en a 13 et peut prendre 7 positions. Une seule épouse la morsure.
    static final int N = 7, M = 13, MAX = 6, U = 7, COL = 12, REST = 30;
    final Random h;
    final int[] bite = new int[N], slider = new int[M];
    int position, round, faults, time, duration, flash, pause, rise = -1;
    boolean fitted;

    BiteGame(Random h) {
        this.h = h;
        newB();
    }

    void newB() {
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

    // Espace entre la dent i de la morsure et la dent de la réglette placée dessous (0 = elles se touchent déjà au repos).
    int game(int i, int pos) {
        return REST + (MAX - bite[i] - slider[pos + i]) * U;
    }

    int course(int pos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < N; i++) min = Math.min(min, game(i, pos));
        return min;
    }

    boolean follows(int pos) {
        for (int i = 0; i < N; i++) {
            if (game(i, pos) != REST) return false;
        }
        return true;
    }

    int solutions() {
        int n = 0;
        for (int pos = 0; pos <= M - N; pos++) n += follows(pos) ? 1 : 0;
        return n;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (rise >= 0) {
            // La réglette monte jusqu'à buter, puis on regarde si toutes les dents sont en contact.
            rise++;
            if (rise == 8) {
                if (follows(position)) {
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
                rise = -1; // elle retombe : on peut réessayer ailleurs
            } else if (fitted && pause > 0 && --pause == 0 && !finished) {
                newB(); // la réglette reste emboîtée un instant, puis la morsure suivante arrive
            }
            return;
        }
        if (pause > 0) {
            if (--pause == 0 && !finished) newB();
            return;
        }
        if (--time <= 0) {
            flash = 8;
            Draw.note(0.5f);
            if (++faults >= 2) end(false);
            else pause = 24;
        } else {
            press1(time, duration);
        }
    }

    void slide(int direction) {
        int newB = Math.max(0, Math.min(M - N, position + direction));
        if (newB == position) return;
        position = newB;
        Draw.beep(Sounds.STONE, 0.9f + position * 0.05f, 0.6f);
    }

    boolean free() {
        return pause == 0 && rise < 0 && !finished;
    }

    // Une dent : un corps plein et une pointe qui s'affine sur ses quatre derniers pixels (vers le bas ou vers le haut).
    static void tooth(GuiGraphics g, int x, int root, int tip, int colour, int shadow) {
        int l = COL - 1, direction = tip > root ? 1 : -1, length = Math.abs(tip - root);
        for (int k = 0; k < length; k++) {
            int left1 = length - 1 - k, r = left1 < 2 ? 3 : left1 < 4 ? 1 : 0, y = direction > 0 ? root + k : root - 1 - k;
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
    boolean pressedKey(int pressedKey) {
        if (!free()) return false;
        if (pressedKey == 263) slide(1);
        else if (pressedKey == 262) slide(-1);
        else if (pressedKey == 32 || pressedKey == 257) rise = 0;
        else return false;
        return true;
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        int course = course(position);
        int raised = rise < 0 ? 0 : Math.min(course, rise * 5);
        boolean hitStop = rise >= 8 && !fitted;
        int x0 = ax + 2 + 3 * COL, top = ay + 6, base = top + MAX * U + REST + 6 - raised;
        g.fill(x0 - 4, top - 5, x0 + N * COL + 3, top + 1, 0xFFC97A70);
        g.fill(x0 - 4, top - 5, x0 + N * COL + 3, top - 3, 0xFFE0A096);
        for (int i = 0; i < N; i++) {
            int x = x0 + i * COL, bottom = top + bite[i] * U;
            boolean contact = hitStop && game(i, position) == course;
            tooth(g, x, top, bottom, contact ? Draw.ALERT : fitted ? 0xFFBFE8C8 : 0xFFF4EEDC, contact ? 0xFF8E2B26 : fitted ? 0xFF8FC8A0 : 0xFFCFC4A6);
        }
        // La réglette de moulage : treize dents, dont sept seulement se trouvent sous la morsure.
        g.fill(ax + 2, base - 1, ax + 2 + M * COL - 1, base + 6, 0xFFC97A70);
        g.fill(ax + 2, base + 4, ax + 2 + M * COL - 1, base + 6, 0xFF9C5850);
        for (int j = 0; j < M; j++) {
            int screen = j - position + 3;
            if (screen < 0 || screen >= M) continue;
            int x = ax + 2 + screen * COL, summit = base - slider[j] * U;
            boolean below = screen >= 3 && screen < 3 + N;
            boolean contact = hitStop && below && game(screen - 3, position) == course;
            tooth(g, x, base - 1, summit - 1, contact ? Draw.ALERT : !below ? 0xFF6E7A88 : fitted ? 0xFF8FD8A0 : 0xFFF2B56A,
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
