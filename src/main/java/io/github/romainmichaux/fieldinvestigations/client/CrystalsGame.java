package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Cristaux résiduels (résidu élémentaire) : mémoriser le gisement, prélever les stables, éviter les instables
final class CrystalsGame extends MiniGame {
    static final ResourceLocation EARTH = Draw.texture("earth"), CRYSTAL = Draw.texture("crystal");
    static final int COLS = 7, RANKS = 5, CASE = 20, DULL = 0xFF566270, STABLE = 0xFF6FF0E0, UNSTABLE = 0xFFF0604E;
    final Random h;
    final int[] kind1 = new int[COLS * RANKS]; // 0 inerte, 1 stable (à prélever), 2 instable (à éviter)
    final int[] state = new int[COLS * RANKS];  // 0 intact, 1 prélevé, 2 touché à tort
    int round, shows, duration, remaining, faults, pause, flash, ticks;

    CrystalsGame(Random h) {
        this.h = h;
        newOne();
    }

    void newOne() {
        shownSound();
        java.util.Arrays.fill(kind1, 0);
        java.util.Arrays.fill(state, 0);
        remaining = 5 + round;
        for (int k = 0; k < remaining + 1 + round; ) {
            int i = h.nextInt(kind1.length);
            if (kind1[i] != 0) continue;
            kind1[i] = k < remaining ? 1 : 2;
            k++;
        }
        duration = shows = 58 - round * 6;
    }

    @Override
    void tick() {
        ticks++;
        if (flash > 0) flash--;
        if (shows > 0 && --shows == 0) hiddenSound();
        if (pause > 0 && --pause == 0 && !finished) newOne();
    }

    int cx(int i) {
        return ox + 14 + (i % COLS) * CASE;
    }

    int cy(int i) {
        return oy + 9 + (i / COLS) * CASE;
    }

    @Override
    void click(double mx, double my) {
        if (shows > 0 || pause > 0) return;
        for (int i = 0; i < kind1.length; i++) {
            if (state[i] != 0 || !Draw.in(mx, my, cx(i), cy(i), CASE, CASE)) continue;
            if (kind1[i] == 1) {
                state[i] = 1;
                Draw.sound1(1.1f + (5 + round - remaining) * 0.08f);
                if (--remaining == 0) {
                    Draw.note(1f + round * 0.25f);
                    if (++round >= 3) {
                        perfect = faults == 0;
                        end(true);
                    } else pause = 26;
                }
            } else {
                state[i] = 2;
                flash = 8;
                Draw.note(0.5f);
                if (++faults >= 3) end(false);
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Draw.ALERT : Draw.EDGE);
        Draw.image(g, EARTH, bx, by, 160, 112);
        boolean revealed = shows > 0 || pause > 0 || finished;
        boolean pulse = (ticks / 4) % 2 == 0;
        for (int i = 0; i < kind1.length; i++) {
            int x = cx(i), y = cy(i);
            int tint = DULL;
            boolean glows = false;
            if (state[i] == 1 || (revealed && kind1[i] == 1)) {
                tint = STABLE;
                glows = true;
            } else if ((state[i] == 2 && kind1[i] == 2) || (revealed && kind1[i] == 2)) {
                tint = UNSTABLE;
                glows = true;
            } else if (state[i] == 2) {
                tint = 0xFF2E3640; // cristal inerte abîmé
            } else if (shows == 0 && pause == 0 && !finished && Draw.in(mx, my, x, y, CASE, CASE)) {
                tint = 0xFF8FA0B2;
            }
            if (glows) {
                int halo = (tint & 0xFFFFFF) | (pulse ? 0x50000000 : 0x38000000);
                g.fill(x + 1, y + 3, x + CASE - 1, y + CASE - 3, halo);
                g.fill(x + 3, y + 1, x + CASE - 3, y + CASE - 1, halo);
            }
            Draw.tint(g, tint);
            Draw.image(g, CRYSTAL, x + 2, y + 2, 16, 16);
            g.setColor(1f, 1f, 1f, 1f);
        }
        int tx = ox + 172;
        Draw.write(g, T.f("deposit") + Math.min(3, round + 1) + " / 3", tx, oy + 8, Draw.TEXT, false);
        Draw.write(g, shows > 0 ? T.f("watch_closely") : pause > 0 ? T.f("deposit_sampled") : T.f("your_turn_sample"), tx, oy + 20,
                shows > 0 ? Draw.YELLOW : pause > 0 ? Draw.GREEN : Draw.ACCENT, false);
        if (shows > 0) timeGauge(g, tx, oy + 32, 100, shows / (double) duration);
        g.fill(tx, oy + 46, tx + 8, oy + 54, STABLE);
        Draw.write(g, T.f("stable") + remaining, tx + 12, oy + 46, Draw.TEXT, false);
        g.fill(tx, oy + 58, tx + 8, oy + 66, UNSTABLE);
        Draw.write(g, T.f("unstable_avoid"), tx + 12, oy + 58, Draw.GREY, false);
        Draw.write(g, T.f("faults") + faults + " / 3", tx, oy + 76, faults > 1 ? Draw.ALERT : Draw.GREY, false);
        Draw.paragraph(g, T.f("no_fault_one_extra_dose"), tx, oy + 92, 104, Draw.DARK);
    }

    @Override
    String instruction() {
        return T.f("stable_teal_and_unstable_red");
    }
}
