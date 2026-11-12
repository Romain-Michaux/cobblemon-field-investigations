package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Cycles PCR : répéter la suite de températures
final class PcrGame extends MiniGame {
    static final String[] MILESTONES = {"95°", "72°", "55°", "4°"};
    static final int[] COLOURS = {0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9, 0xFF6CCB5F};
    // Une note par palier, de la plus aiguë (95°) à la plus grave (4°) : la suite se retient aussi à l'oreille.
    static final float[] NOTES = {1.5f, 1.19f, 0.94f, 0.75f};

    static void ring(int milestone) {
        Draw.beep(Sounds.CHIME, NOTES[milestone], 0.8f);
    }
    final Random h;
    int[] rest1;
    int round, errors, shows, input, lit = -1, duration;

    PcrGame(Random h) {
        this.h = h;
        newB();
    }

    void newB() {
        rest1 = new int[3 + round];
        for (int i = 0; i < rest1.length; i++) rest1[i] = h.nextInt(4);
        shows = -16;
        input = -1;
    }

    @Override
    void tick() {
        if (duration > 0 && --duration == 0) lit = -1;
        if (input >= 0) return;
        shows++;
        if (shows >= 0 && shows % 13 == 0) {
            int i = shows / 13;
            if (i >= rest1.length) {
                input = 0;
                return;
            }
            lit = rest1[i];
            duration = 9;
            ring(rest1[i]);
        }
    }

    @Override
    void click(double mx, double my) {
        if (input < 0) return;
        for (int b = 0; b < 4; b++) {
            if (!Draw.in(mx, my, ox + 12 + (b % 2) * 70, oy + 8 + (b / 2) * 52, 64, 46)) continue;
            lit = b;
            duration = 5;
            if (b != rest1[input]) {
                Draw.beep(Sounds.FAILURE, 0.5f, 0.9f);
                if (++errors >= 2) end(false);
                else newB();
                return;
            }
            ring(b);
            if (++input >= rest1.length) {
                Draw.beep(Sounds.CHIME, 1.6f, 0.6f); // cycle réussi
                if (++round >= 3) {
                    perfect = errors == 0;
                    end(true);
                } else newB();
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        for (int b = 0; b < 4; b++) {
            int x = ox + 12 + (b % 2) * 70, y = oy + 8 + (b / 2) * 52;
            boolean bright = lit == b;
            boolean hover = input >= 0 && !finished && Draw.in(mx, my, x, y, 64, 46);
            Draw.button(g, x, y, 64, 46, MILESTONES[b], false, true, bright ? Draw.darken(COLOURS[b], 1.25) : Draw.darken(COLOURS[b], hover ? 0.75 : 0.5));
        }
        int tx = ox + 160;
        Draw.write(g, T.f("cycle") + Math.min(3, round + 1) + " / 3", tx, oy + 12, Draw.TEXT, false);
        Draw.write(g, T.f("mistakes") + errors + " / 2", tx, oy + 24, errors > 0 ? Draw.ALERT : Draw.GREY, false);
        Draw.write(g, input < 0 ? T.f("watch_the_program") : T.f("your_turn_repeat_it"), tx, oy + 44, input < 0 ? Draw.YELLOW : Draw.ACCENT, false);
        for (int i = 0; i < rest1.length; i++) {
            g.fill(tx + i * 12, oy + 60, tx + i * 12 + 9, oy + 69, Draw.EDGE);
            g.fill(tx + i * 12 + 1, oy + 61, tx + i * 12 + 8, oy + 68, input > i ? COLOURS[rest1[i]] : Draw.INSET_LIGHT);
        }
        Draw.paragraph(g, T.f("the_program_gets_one_step"), tx, oy + 80, 112, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("watch_the_sequence_of_temperature");
    }
}
