package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Colony counting: count at a glance, then answer
final class CountGame extends MiniGame {
    static final ResourceLocation PETRI = Draw.texture("petri");
    static final int[] TINTS = {0xFFFFFFFF, 0xFFE07A5F, 0xFF7FB069, 0xFF6C8EBF};
    final Random h;
    int[] cx, cy, size, tint, choices = new int[4];
    int colonyCount, round, errors, visible, pause;
    boolean lastOk;

    CountGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        shownSound();
        colonyCount = 7 + round * 2 + h.nextInt(4);
        cx = new int[colonyCount];
        cy = new int[colonyCount];
        size = new int[colonyCount];
        tint = new int[colonyCount];
        for (int i = 0; i < colonyCount; i++) {
            size[i] = 4 + h.nextInt(3);
            tint[i] = h.nextInt(TINTS.length);
            for (int attempt = 0; attempt < 80; attempt++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 33;
                cx[i] = (int) Math.round(Math.cos(a) * r);
                cy[i] = (int) Math.round(Math.sin(a) * r);
                boolean free = true;
                for (int k = 0; k < i; k++) free &= Math.hypot(cx[k] - cx[i], cy[k] - cy[i]) >= 9;
                if (free) break;
            }
        }
        List<Integer> options = new ArrayList<>(List.of(colonyCount - 2, colonyCount - 1, colonyCount + 1, colonyCount + 2));
        Collections.shuffle(options, h);
        List<Integer> kept = new ArrayList<>(options.subList(0, 3));
        kept.add(colonyCount);
        Collections.sort(kept);
        for (int i = 0; i < 4; i++) choices[i] = kept.get(i);
        visible = 56 - round * 6;
    }

    @Override
    void tick() {
        if (visible > 0 && --visible == 0) hiddenSound();
        if (pause > 0 && --pause == 0 && !finished) newRound();
    }

    @Override
    void click(double mx, double my) {
        if (visible > 0 || pause > 0) return;
        for (int i = 0; i < 4; i++) {
            if (!Draw.in(mx, my, ox + 126 + (i % 2) * 74, oy + 46 + (i / 2) * 28, 68, 22)) continue;
            lastOk = choices[i] == colonyCount;
            pause = 24;
            if (lastOk) {
                Draw.note(1f + round * 0.25f);
                if (++round >= 3) {
                    perfect = errors == 0;
                    end(true);
                }
            } else {
                Draw.note(0.5f);
                if (++errors >= 2) end(false);
            }
            return;
        }
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int px = ox + 12, py = oy + 11;
        Draw.image(g, PETRI, px, py, 96, 96);
        boolean showing = visible > 0 || pause > 0;
        if (showing) {
            for (int i = 0; i < colonyCount; i++) {
                int x = px + 48 + cx[i] - size[i] / 2, y = py + 48 + cy[i] - size[i] / 2, c = TINTS[tint[i]];
                g.fill(x - 1, y, x + size[i] + 1, y + size[i], Draw.darken(c, 0.45));
                g.fill(x, y - 1, x + size[i], y + size[i] + 1, Draw.darken(c, 0.45));
                g.fill(x, y, x + size[i], y + size[i], c);
            }
        } else {
            Draw.centre(g, "?", px + 48, py + 44, Draw.EDGE);
        }
        int tx = ox + 126;
        Draw.write(g, T.f("dish") + Math.min(3, round + 1) + T.f("3_mistakes") + errors + " / 2", tx, oy + 10, errors > 0 ? Draw.ALERT : Draw.TEXT, false);
        String state = pause > 0 ? (lastOk ? T.f("correct") + colonyCount + T.f("colonies") : T.f("wrong_there_were") + colonyCount) : visible > 0 ? T.f("count_the_colonies") : T.f("how_many_were_there");
        Draw.write(g, state, tx, oy + 26, pause > 0 ? (lastOk ? Draw.GREEN : Draw.ALERT) : Draw.YELLOW, false);
        boolean enabled = visible == 0 && pause == 0 && !finished;
        for (int i = 0; i < 4; i++) {
            int x = tx + (i % 2) * 74, y = oy + 46 + (i / 2) * 28;
            Draw.button(g, x, y, 68, 22, String.valueOf(choices[i]), enabled && Draw.in(mx, my, x, y, 68, 22), enabled, Draw.ACCENT);
        }
        if (visible > 0) timeGauge(g, tx, oy + 106, 142, visible / (double) (56 - round * 6));
    }

    @Override
    String instruction() {
        return T.f("the_dish_is_only_visible");
    }
}
