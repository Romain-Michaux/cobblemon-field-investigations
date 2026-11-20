package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Sequencing: find the sample's six bases on a sequencer. A read is typed (or clicked) then run, which spends one of six
// reagent vials: its trace is drawn on the screen, then each base comes back coloured, in place (green), present
// elsewhere (yellow) or absent (grey). A notebook on the side panel greys out, under each position, the bases already
// ruled out. Once found, the clean trace scrolls by and coils into a double helix.
final class SequencingGame extends MiniGame {
    static final ResourceLocation MACHINE = Draw.texture("seq_machine");
    static final int LENGTH = 6, READS = 6, TILE = 10, STEP = 11, READ_TIME = 48, END = 120;
    static final int GREY = 0, YELLOW = 1, GREEN = 2;
    // Screen and panel, in the play area.
    static final int SX = 26, SY = 27, SW = 146, SH = 61, PX = 183, PY = 25, BX = 200, BY = 93, BW = 50, BH = 11;
    final int[] code = new int[LENGTH];
    final int[][] guesses = new int[READS][LENGTH];
    final int[][] feedback = new int[READS][LENGTH];
    final boolean[][] possible = new boolean[LENGTH][4];
    final int[] keys = new int[4];
    int line, cursor, reading = -1, ending = -1, failing = -1, tic;

    SequencingGame(Random h) {
        for (int k = 0; k < LENGTH; k++) code[k] = h.nextInt(4);
        for (int[] g : guesses) java.util.Arrays.fill(g, -1);
        for (boolean[] p : possible) java.util.Arrays.fill(p, true);
        for (int b = 0; b < 4; b++) keys[b] = PairingGame.keyFor(Draw.BASES[b]);
    }

    boolean complete() {
        for (int v : guesses[line]) if (v < 0) return false;
        return true;
    }

    void type(int b) {
        guesses[line][cursor] = b;
        cursor = Math.min(LENGTH - 1, cursor + 1);
        Draw.click(1.2f + b * 0.1f);
    }

    void run() {
        if (!complete()) return;
        reading = 0;
        Draw.beep(Sounds.SCAN, 1f, 0.6f);
    }

    // Each base of the read against the code, with repeated bases counted only as often as the code has them.
    void judge() {
        int[] g = guesses[line], f = feedback[line];
        int[] left = new int[4];
        for (int k = 0; k < LENGTH; k++) {
            if (g[k] == code[k]) f[k] = GREEN;
            else left[code[k]]++;
        }
        for (int k = 0; k < LENGTH; k++) {
            if (f[k] == GREEN) continue;
            if (left[g[k]] > 0) {
                f[k] = YELLOW;
                left[g[k]]--;
            } else {
                f[k] = GREY;
            }
        }
        // The notebook: what each position can no longer be.
        for (int k = 0; k < LENGTH; k++) {
            if (f[k] == GREEN) {
                for (int b = 0; b < 4; b++) possible[k][b] = b == g[k];
            } else {
                possible[k][g[k]] = false;
                if (f[k] == GREY) {
                    boolean elsewhere = false;
                    for (int j = 0; j < LENGTH; j++) if (g[j] == g[k] && f[j] != GREY) elsewhere = true;
                    if (!elsewhere) for (int j = 0; j < LENGTH; j++) if (f[j] != GREEN && possible[j][g[k]] && count(j) > 1) possible[j][g[k]] = false;
                }
            }
        }
    }

    int count(int k) {
        int n = 0;
        for (boolean b : possible[k]) if (b) n++;
        return n;
    }

    @Override
    void tick() {
        tic++;
        if (ending >= 0) {
            if (ending % 8 == 0 && ending < 70) Draw.beep(Sounds.SCAN, 1.2f + ending / 100f, 0.3f);
            if (ending == 84) Draw.beep(Sounds.PAPER, 1f, 0.8f);
            if (++ending >= END) {
                perfect = line <= 3;
                end(true);
            }
            return;
        }
        if (failing >= 0) {
            if (++failing >= 60) end(false);
            return;
        }
        if (reading < 0) return;
        reading++;
        if (reading < 30 && reading % 3 == 0) Draw.beep(Sounds.SCAN, 0.8f + reading / 40f, 0.3f);
        if (reading == 30) judge();
        if (reading > 30 && (reading - 30) % 3 == 0 && (reading - 30) / 3 <= LENGTH) {
            int k = (reading - 30) / 3 - 1;
            if (k >= 0) Draw.note(feedback[line][k] == GREEN ? 1.4f : feedback[line][k] == YELLOW ? 1.1f : 0.7f);
        }
        if (reading >= READ_TIME) {
            reading = -1;
            boolean solved = true;
            for (int f : feedback[line]) if (f != GREEN) solved = false;
            line++;
            cursor = 0;
            if (solved) {
                ending = 0;
                Draw.note(1.6f);
            } else if (line >= READS) {
                failing = 0;
            }
        }
    }

    boolean busy() {
        return finished || reading >= 0 || ending >= 0 || failing >= 0;
    }

    @Override
    boolean keyPressed(int key) {
        if (busy()) return false;
        if (key == InputConstants.KEY_BACKSPACE) {
            if (guesses[line][cursor] < 0 && cursor > 0) cursor--;
            guesses[line][cursor] = -1;
            Draw.click(0.7f);
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            run();
            return true;
        }
        String label = Draw.keyLabel(key);
        for (int b = 0; b < 4; b++) {
            if (label.equalsIgnoreCase(Draw.BASES[b])) {
                type(b);
                return true;
            }
        }
        return false;
    }

    // Where a read's row sits on the screen: two columns of three.
    int rowX(int l) {
        return ox + SX + 6 + (l / 3) * 74;
    }

    int rowY(int l) {
        return oy + SY + 22 + (l % 3) * 13;
    }

    @Override
    void click(double mx, double my) {
        if (busy()) return;
        if (Draw.in(mx, my, ox + BX, oy + BY, BW, BH)) {
            run();
            return;
        }
        for (int k = 0; k < LENGTH; k++) {
            if (Draw.in(mx, my, rowX(line) + k * STEP, rowY(line), TILE, TILE)) {
                guesses[line][k] = (guesses[line][k] + 1) % 4;
                cursor = k;
                Draw.click(1f + guesses[line][k] * 0.15f);
                return;
            }
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox, ay = oy;
        Draw.image(g, MACHINE, ax, ay, AREA_W, AREA_H);
        // The screen.
        int x0 = ax + SX, y0 = ay + SY;
        g.fill(x0 - 1, y0 - 1, x0 + SW + 1, y0 + SH + 1, 0xFF0A0C10);
        g.fill(x0, y0, x0 + SW, y0 + SH, 0xFF141A22);
        if (ending >= 0) {
            drawFinale(g, x0, y0);
        } else {
            drawTrace(g, x0 + 2, y0 + 2, SW - 4, 17);
            for (int l = 0; l < READS; l++) drawRow(g, l, mx, my);
            if (failing >= 0) {
                String s = T.f("sequence_not_found");
                int w = Draw.textFont().width(s);
                g.fill(x0 + SW / 2 - w / 2 - 4, y0 + 24, x0 + SW / 2 + w / 2 + 4, y0 + 36, 0xF0141A22);
                g.drawString(Draw.textFont(), s, x0 + SW / 2 - w / 2, y0 + 26, Draw.ALERT, false);
            }
        }
        drawNotebook(g, ax + PX, ay + PY);
        drawVials(g, ax + PX, ay + 66);
        // The read button.
        boolean on = !busy() && complete();
        boolean hover = on && Draw.in(mx, my, ax + BX, ay + BY, BW, BH);
        g.fill(ax + BX - 1, ay + BY - 1, ax + BX + BW + 1, ay + BY + BH + 1, 0xFF30303D);
        g.fill(ax + BX, ay + BY, ax + BX + BW, ay + BY + BH, hover ? 0xFF5FD49A : on ? 0xFF3FA878 : 0xFF6A7480);
        String read = T.f("read");
        g.drawString(Draw.textFont(), read, ax + BX + (BW - Draw.textFont().width(read)) / 2, ay + BY + 2, on ? 0xFF0E1A14 : 0xFFB0B8C0, false);
    }

    // One read: six tiles, coloured by what came back (or by base while typed), the current one with its cursor.
    void drawRow(GuiGraphics g, int l, int mx, int my) {
        int x = rowX(l), y = rowY(l);
        g.drawString(Draw.textFont(), String.valueOf(l + 1), x - 6, y + 1, l == line && failing < 0 ? Draw.YELLOW : 0xFF3A4654, false);
        for (int k = 0; k < LENGTH; k++) {
            int b = guesses[l][k], tx = x + k * STEP;
            boolean revealed = l < line || (l == line && reading > 30 + (k + 1) * 3);
            int bg;
            if (revealed) bg = feedback[l][k] == GREEN ? 0xFF4FB86A : feedback[l][k] == YELLOW ? 0xFFD8B43A : 0xFF4A525E;
            else if (b >= 0) bg = Draw.darken(Draw.BASE_COLOURS[b], 0.55);
            else bg = 0xFF222A34;
            g.fill(tx, y, tx + TILE, y + TILE, Draw.darken(bg, 0.6));
            g.fill(tx + 1, y + 1, tx + TILE - 1, y + TILE - 1, bg);
            if (b >= 0) {
                String s = Draw.BASES[b];
                g.drawString(Draw.textFont(), s, tx + (TILE - Draw.textFont().width(s) + 1) / 2, y + 1, revealed || l < line ? 0xFF10161C : 0xFFE8EEF4, false);
            }
            if (l == line && reading < 0 && failing < 0) {
                if (k == cursor && (tic / 8) % 2 == 0) g.fill(tx, y + TILE, tx + TILE, y + TILE + 1, Draw.YELLOW);
                if (Draw.in(mx, my, tx, y, TILE, TILE)) g.fill(tx, y - 1, tx + TILE, y, Draw.YELLOW);
            }
        }
    }

    // The trace of the read being run (drawn as the machine goes), or of the last read: one coloured peak per base,
    // taller when it came back in place.
    void drawTrace(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y + h, x + w, y + h + 1, 0xFF2A3440);
        int l = reading >= 0 ? line : line - 1;
        if (l < 0) {
            // Idle: a faint noisy baseline.
            for (int i = 0; i < w; i += 2) g.fill(x + i, y + h - 1 - (int) ((Math.sin(i * 1.7 + tic * 0.3) + 1)), x + i + 1, y + h, 0xFF3A4654);
            return;
        }
        double progress = reading >= 0 ? Math.min(1, reading / 30.0) : 1;
        double slot = w / (double) LENGTH;
        for (int i = 0; i < w * progress; i++) {
            double pos = i / slot;
            int k = Math.min(LENGTH - 1, (int) pos);
            double d = pos - k - 0.5;
            double height = reading >= 0 && reading <= 30 ? 0.8 : feedback[l][k] == GREEN ? 1 : feedback[l][k] == YELLOW ? 0.6 : 0.3;
            int ph = (int) Math.round((h - 2) * height * Math.exp(-d * d * 18));
            int c = Draw.BASE_COLOURS[guesses[l][k]];
            g.fill(x + i, y + h - ph, x + i + 1, y + h, Draw.darken(c, 0.5));
            g.fill(x + i, y + h - ph, x + i + 1, y + h - ph + 1, c);
        }
        if (reading >= 0 && reading <= 30) {
            int sx = x + (int) (w * progress);
            g.fill(sx, y, sx + 1, y + h, 0xC0FFFFFF);
        }
    }

    // The notebook on the side panel: under each position, the four bases, the ruled-out ones greyed.
    void drawNotebook(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 6 * 12 + 1, y + 37, 0xFF30303D);
        g.fill(x, y, x + 6 * 12, y + 36, 0xFFF2F4EC);
        for (int k = 0; k < LENGTH; k++) {
            int cx = x + k * 12 + 6;
            boolean known = count(k) == 1;
            if (known) g.fill(cx - 5, y + 1, cx + 5, y + 35, 0xFFD4F0D8);
            for (int b = 0; b < 4; b++) {
                String s = Draw.BASES[b];
                int c = possible[k][b] ? Draw.darken(Draw.BASE_COLOURS[b], 0.7) : 0xFFC4C8C0;
                g.drawString(Draw.textFont(), s, cx - Draw.textFont().width(s) / 2 + 1, y + 1 + b * 9, c, false);
                if (!possible[k][b]) g.fill(cx - 3, y + 4 + b * 9, cx + 4, y + 5 + b * 9, 0xFFA8ACA4);
            }
        }
    }

    // The reagent vials: one spent per read.
    void drawVials(GuiGraphics g, int x, int y) {
        int used = line + (reading >= 0 ? 1 : 0);
        for (int i = 0; i < READS; i++) {
            int vx = x + 3 + i * 12;
            g.fill(vx - 1, y, vx + 7, y + 18, 0xFF30303D);
            g.fill(vx, y + 1, vx + 6, y + 17, 0xFFE8F0F8);
            g.fill(vx - 1, y - 2, vx + 7, y, 0xFF5D6E80);
            if (i >= used) {
                g.fill(vx + 1, y + 5, vx + 5, y + 16, 0xFF4A90D9);
                g.fill(vx + 1, y + 5, vx + 5, y + 6, 0xFF8CC4F4);
            } else if (i == used - 1 && reading >= 0) {
                int left = (int) Math.round(11 * (1 - Math.min(1, reading / 20.0)));
                if (left > 0) g.fill(vx + 1, y + 16 - left, vx + 5, y + 16, 0xFF4A90D9);
            }
        }
    }

    // The end: the clean trace scrolls by, then coils into a double helix, and the read is stamped.
    void drawFinale(GuiGraphics g, int x0, int y0) {
        int w = SW - 4;
        double coil = Math.max(0, Math.min(1, (ending - 30) / 40.0));
        // The trace, scrolling and fading as the helix forms.
        if (coil < 1) {
            double slot = w / (double) LENGTH;
            int shift = ending * 2;
            for (int i = 0; i < w; i++) {
                double pos = ((i + shift) % w) / slot;
                int k = Math.min(LENGTH - 1, (int) pos);
                double d = pos - k - 0.5;
                int ph = (int) Math.round(15 * (1 - coil) * Math.exp(-d * d * 18));
                g.fill(x0 + 2 + i, y0 + 19 - ph, x0 + 3 + i, y0 + 19, Draw.BASE_COLOURS[code[k]]);
            }
        }
        // The helix: two strands winding along the screen, rungs coloured by the code.
        int cy = y0 + 38, span = (int) (w * coil);
        for (int i = 0; i < span; i++) {
            double a = i * 0.16 + ending * 0.08;
            int ya = cy + (int) Math.round(Math.sin(a) * 11), yb = cy - (int) Math.round(Math.sin(a) * 11);
            if (i % 6 == 0) {
                int c = Draw.BASE_COLOURS[code[(i / 6) % LENGTH]];
                g.fill(x0 + 2 + i, Math.min(ya, yb), x0 + 3 + i, Math.max(ya, yb), Draw.darken(c, 0.8));
            }
            boolean front = Math.cos(a) > 0;
            g.fill(x0 + 2 + i, ya, x0 + 3 + i, ya + 2, front ? 0xFFE8EEF4 : 0xFF7A8696);
            g.fill(x0 + 2 + i, yb, x0 + 3 + i, yb + 2, front ? 0xFF7A8696 : 0xFFE8EEF4);
        }
        if (ending >= 84) {
            String s = T.f("sequence_read");
            int sw = Draw.textFont().width(s) + 12;
            double k = Math.min(1, (ending - 84) / 6.0);
            g.pose().pushPose();
            g.pose().translate(x0 + SW / 2.0, y0 + 38, 300);
            float sc = (float) (1.6 - 0.6 * k);
            g.pose().scale(sc, sc, 1);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8));
            g.fill(-sw / 2 - 2, -9, sw / 2 + 2, 9, 0xFF2E7D32);
            g.fill(-sw / 2, -7, sw / 2, 7, 0xF0F4F0E6);
            g.drawString(Draw.textFont(), s, -Draw.textFont().width(s) / 2, -4, 0xFF2E7D32, false);
            g.pose().popPose();
        }
    }

    @Override
    String instruction() {
        return T.f("type_a_read_of_6");
    }

    @Override
    Control[] controls() {
        return new Control[] {new Control(T.f("type_the_bases"), keys[0], keys[1], keys[2], keys[3]), new Control(T.f("read"), InputConstants.KEY_RETURN),
                new Control(T.f("erase"), InputConstants.KEY_BACKSPACE)};
    }
}
