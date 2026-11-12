package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Titration: add reagent up to the endpoint, without overshooting it
final class TitrationGame extends MiniGame {
    static final ResourceLocation BURETTE = Draw.texture("burette"), FLASK = Draw.texture("erlenmeyer"), LIQUID = Draw.texture("erlenmeyer_liquid");
    final Random h;
    int endpoint, drops, dropFalling, round, faults, time, duration, pause, flash;
    boolean turned;
    String message = "";

    TitrationGame(Random h) {
        this.h = h;
        newRound();
    }

    void newRound() {
        endpoint = 16 + h.nextInt(17);
        drops = 0;
        turned = false;
        duration = time = 230 - round * 15;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (dropFalling > 0) dropFalling--;
        if (pause > 0) {
            if (--pause == 0 && !finished) newRound();
            return;
        }
        if (--time <= 0) fault(T.f("too_slow"));
        else hurryTicks(time, duration);
    }

    void fault(String reason) {
        message = reason;
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 2) end(false);
        else pause = 34;
    }

    void pour(int n) {
        drops += n;
        dropFalling = 6;
        // The closer to the endpoint, the higher the drop sounds.
        Draw.beep(Sounds.DROP, (float) (0.6 + 1.2 * Math.max(0, 1 - (endpoint - drops) / 30.0)), 0.5f);
        if (drops < endpoint) return;
        turned = true;
        if (drops - endpoint <= 1) {
            message = T.f("clean_endpoint");
            Draw.note(1f + round * 0.25f);
            if (++round >= 3) {
                perfect = faults == 0;
                end(true);
            } else {
                pause = 34;
            }
        } else {
            fault(T.f("endpoint_overshot"));
        }
    }

    @Override
    void click(double mx, double my) {
        if (pause > 0 || dropFalling > 0) return;
        int tx = ox + 172;
        if (Draw.in(mx, my, tx, oy + 60, 50, 20)) pour(1);
        else if (Draw.in(mx, my, tx + 54, oy + 60, 50, 20)) pour(3);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        Draw.image(g, BURETTE, ax + 40, ay + 2, 32, 96);
        if (dropFalling > 0) g.fill(ax + 55, ay + 96 + (6 - dropFalling), ax + 57, ay + 100 + (6 - dropFalling), 0xFFD0407A);
        // The liquid turns pink as the endpoint nears, then changes colour sharply.
        int tint = turned ? 0xFFD0407A : mix(0xFFE8EEF2, 0xFFF6C4D6, (drops - (endpoint - 5)) / 5.0);
        Draw.image(g, FLASK, ax + 84, ay + 46, 48, 64);
        Draw.tint(g, tint);
        Draw.image(g, LIQUID, ax + 84, ay + 46, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);
        int tx = ox + 172;
        text(g, T.f("titration") + Math.min(3, round + 1) + " / 3", tx, oy + 8, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 2", tx, oy + 20, faults > 0 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("drops_2") + drops, tx, oy + 34, Draw.YELLOW);
        text(g, pause > 0 || finished ? message : "", tx, oy + 46, message.equals(T.f("clean_endpoint")) ? Draw.GREEN : Draw.ALERT);
        boolean enabled = pause == 0 && dropFalling == 0 && !finished;
        Draw.button(g, tx, oy + 60, 50, 20, T.f("drop"), enabled && Draw.in(mx, my, tx, oy + 60, 50, 20), enabled, Draw.ACCENT);
        Draw.button(g, tx + 54, oy + 60, 50, 20, T.f("squirt_3"), enabled && Draw.in(mx, my, tx + 54, oy + 60, 50, 20), enabled, 0xFFE9A15B);
        timeGauge(g, tx, oy + 104, 100, pause > 0 ? 0 : time / (double) duration);
    }

    @Override
    String instruction() {
        return T.f("pour_until_the_liquid_turns");
    }
}
