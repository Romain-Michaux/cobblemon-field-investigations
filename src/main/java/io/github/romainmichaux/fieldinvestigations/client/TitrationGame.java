package io.github.romainmichaux.fieldinvestigations.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Dosage : ajouter le réactif jusqu'au virage, sans le dépasser
final class TitrationGame extends MiniGame {
    static final ResourceLocation BURETTE = Draw.texture("burette"), VIAL = Draw.texture("erlenmeyer"), LIQUID = Draw.texture("erlenmeyer_liquid");
    final Random h;
    int turn, drops, waiting, round, faults, time, duration, pause, flash;
    boolean turns;
    String message = "";

    TitrationGame(Random h) {
        this.h = h;
        newB();
    }

    void newB() {
        turn = 16 + h.nextInt(17);
        drops = 0;
        turns = false;
        duration = time = 230 - round * 15;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (waiting > 0) waiting--;
        if (pause > 0) {
            if (--pause == 0 && !finished) newB();
            return;
        }
        if (--time <= 0) fault(T.f("too_slow"));
        else press1(time, duration);
    }

    void fault(String reason) {
        message = reason;
        flash = 8;
        Draw.note(0.5f);
        if (++faults >= 2) end(false);
        else pause = 34;
    }

    void verser(int n) {
        drops += n;
        waiting = 6;
        // Plus on approche du virage, plus la goutte sonne aigu.
        Draw.beep(Sounds.DROP, (float) (0.6 + 1.2 * Math.max(0, 1 - (turn - drops) / 30.0)), 0.5f);
        if (drops < turn) return;
        turns = true;
        if (drops - turn <= 1) {
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
        if (pause > 0 || waiting > 0) return;
        int tx = ox + 172;
        if (Draw.in(mx, my, tx, oy + 60, 50, 20)) verser(1);
        else if (Draw.in(mx, my, tx + 54, oy + 60, 50, 20)) verser(3);
    }

    @Override
    void draw(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        frame(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        Draw.image(g, BURETTE, ax + 40, ay + 2, 32, 96);
        if (waiting > 0) g.fill(ax + 55, ay + 96 + (6 - waiting), ax + 57, ay + 100 + (6 - waiting), 0xFFD0407A);
        // Le liquide rosit à l'approche du virage, puis vire franchement.
        int tint = turns ? 0xFFD0407A : mix(0xFFE8EEF2, 0xFFF6C4D6, (drops - (turn - 5)) / 5.0);
        Draw.image(g, VIAL, ax + 84, ay + 46, 48, 64);
        Draw.tint(g, tint);
        Draw.image(g, LIQUID, ax + 84, ay + 46, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);
        int tx = ox + 172;
        text(g, T.f("titration") + Math.min(3, round + 1) + " / 3", tx, oy + 8, Draw.TEXT);
        text(g, T.f("mistakes") + faults + " / 2", tx, oy + 20, faults > 0 ? Draw.ALERT : Draw.GREY);
        text(g, T.f("drops_2") + drops, tx, oy + 34, Draw.YELLOW);
        text(g, pause > 0 || finished ? message : "", tx, oy + 46, message.equals(T.f("clean_endpoint")) ? Draw.GREEN : Draw.ALERT);
        boolean free = pause == 0 && waiting == 0 && !finished;
        Draw.button(g, tx, oy + 60, 50, 20, T.f("drop"), free && Draw.in(mx, my, tx, oy + 60, 50, 20), free, Draw.ACCENT);
        Draw.button(g, tx + 54, oy + 60, 50, 20, T.f("squirt_3"), free && Draw.in(mx, my, tx + 54, oy + 60, 50, 20), free, 0xFFE9A15B);
        timeGauge(g, tx, oy + 104, 100, pause > 0 ? 0 : time / (double) duration);
    }

    @Override
    String instruction() {
        return T.f("pour_until_the_liquid_turns");
    }
}
