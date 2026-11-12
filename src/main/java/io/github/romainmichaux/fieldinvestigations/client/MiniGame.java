package io.github.romainmichaux.fieldinvestigations.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

// A mini-game (field sampling or sequencer analysis), drawn in a ZL x ZH area whose corner is (ox, oy).
abstract class MiniGame {
    static final int AREA_W = 280, AREA_H = 118;
    boolean finished, success, perfect;
    int ox, oy;
    // Mouse button held down (for games where you suck up, trace or brush).
    boolean mouseDown;

    void tick() {}

    abstract void draw(GuiGraphics g, int mx, int my, float pt);

    void click(double mx, double my) {}

    void released(double mx, double my) {}

    boolean keyPressed(int key) {
        return false;
    }

    abstract String instruction();

    void end(boolean ok) {
        finished = true;
        success = ok;
    }

    // Last quarter of the time: a tick every half second, rising in pitch, to hurry the player.
    void hurryTicks(int time, int duration) {
        if (time > 0 && time * 4 < duration && time % 10 == 0) {
            Draw.beep(Sounds.TICK, 1.2f + 0.8f * (1 - time * 4f / duration), 0.6f);
        }
    }

    // Something to memorize appears / disappears.
    void shownSound() {
        Draw.beep(Sounds.CHIME, 1.2f, 0.5f);
    }

    void hiddenSound() {
        Draw.beep(Sounds.PAPER, 1.4f, 0.8f);
    }

    void timeGauge(GuiGraphics g, int x, int y, int l, double part) {
        g.fill(x, y, x + l, y + 6, Draw.EDGE);
        g.fill(x + 1, y + 1, x + 1 + (int) ((l - 2) * Math.max(0, Math.min(1, part))), y + 5, part > 0.25 ? Draw.ACCENT : Draw.ALERT);
    }

    // Play area of the 1.1 mini-games: 160 x 112 on the left, counters and instruction on the right.
    static final int L = 160, H = 112;

    static void frame(GuiGraphics g, int bx, int by, boolean alert) {
        g.fill(bx - 1, by - 1, bx + L + 1, by + H + 1, alert ? Draw.ALERT : Draw.EDGE);
    }

    static void text(GuiGraphics g, String s, int x, int y, int colour) {
        Draw.write(g, s, x, y, colour, false);
    }

    static void gauge(GuiGraphics g, int x, int y, int l, double part, int colour) {
        g.fill(x, y, x + l, y + 6, Draw.EDGE);
        g.fill(x + 1, y + 1, x + 1 + (int) ((l - 2) * Math.max(0, Math.min(1, part))), y + 5, colour);
    }

    static void disc(GuiGraphics g, int cx, int cy, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, colour);
        }
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t), v = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (v << 8) | bl;
    }
}
