package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tweezers: grab each fibre as the tweezers close, lined up with it
final class TweezersGame extends MiniGame {
    static final ResourceLocation NEST = Draw.texture("nest"), TWEEZERS = Draw.texture("tweezers");
    static final int N = 5, DURATION = 560;
    final int[] fx = new int[N];
    final boolean[] taken = new boolean[N];
    int time = DURATION, faults, flash, tic, remaining = N;
    double phase;

    TweezersGame(Random h) {
        for (int i = 0; i < N; i++) fx[i] = 16 + i * 28 + h.nextInt(14);
    }

    // 0 = tweezers closed, 1 = wide open. They snap faster and faster.
    double opening() {
        return (Math.sin(phase) + 1) / 2;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--time <= 0) {
            end(false);
            return;
        }
        hurryTicks(time, DURATION);
        tic++;
        double prevOpening = opening();
        phase += 0.15 + 0.035 * (N - remaining);
        if (prevOpening >= 0.3 && opening() < 0.3) Draw.beep(Sounds.TWEEZERS, 1.6f, 0.4f);
    }

    @Override
    void click(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Draw.in(mx, my, bx, by, L, H)) return;
        for (int i = 0; i < N; i++) {
            if (taken[i] || Math.abs(mx - (bx + fx[i])) > 5) continue;
            if (opening() < 0.3) {
                taken[i] = true;
                Draw.beep(Sounds.PLUCK, 1.2f + (N - remaining) * 0.12f, 0.7f);
                if (--remaining == 0) {
                    perfect = faults == 0;
                    end(true);
                }
                return;
            }
        }
        // Tweezers open or beside a fibre: they slip on the bark.
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 3) end(false);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        frame(g, bx, by, flash > 0);
        Draw.image(g, NEST, bx, by, L, H);
        for (int i = 0; i < N; i++) {
            if (taken[i]) continue;
            int x = bx + fx[i];
            g.fill(x - 2, by + 76, x + 3, by + 98, 0xA0140C06);
            g.fill(x, by + 80, x + 1, by + 97, 0xFFFFFFFF);
            g.fill(x - 2, by + 76, x + 3, by + 81, 0xFFF4F0E0);
            g.fill(x - 1, by + 74, x + 2, by + 76, 0xFFFFFFFF);
        }
        int px = Math.max(bx + 16, Math.min(bx + L - 16, mx));
        double o = opening();
        if (!finished) {
            Draw.image(g, TWEEZERS, px - 16, by + 8, 32, 64);
            int e = 2 + (int) Math.round(o * 6);
            int c = o < 0.3 ? Draw.GREEN : 0xFFC8D4E0;
            g.fill(px - e - 1, by + 72, px - e + 1, by + 80, c);
            g.fill(px + e - 1, by + 72, px + e + 1, by + 80, c);
        }
        int tx = ox + 172;
        text(g, T.f("fibres") + (N - remaining) + " / " + N, tx, oy + 10, Draw.TEXT);
        text(g, T.f("misses") + faults + " / 3", tx, oy + 22, faults > 1 ? Draw.ALERT : Draw.GREY);
        text(g, o < 0.3 ? T.f("tweezers_closed") : T.f("tweezers_open"), tx, oy + 42, o < 0.3 ? Draw.GREEN : Draw.GREY);
        gauge(g, tx, oy + 53, 100, 1 - o, o < 0.3 ? Draw.GREEN : Draw.DARK);
        Draw.paragraph(g, T.f("the_tips_turn_green_when"), tx, oy + 66, 104, Draw.DARK);
        timeGauge(g, tx, oy + 104, 100, time / (double) DURATION);
    }

    @Override
    String instruction() {
        return T.f("line_the_tweezers_up_with");
    }
}
