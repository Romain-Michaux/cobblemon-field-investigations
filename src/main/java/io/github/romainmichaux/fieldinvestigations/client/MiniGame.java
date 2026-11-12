package io.github.romainmichaux.fieldinvestigations.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

// Un mini-jeu (prélèvement sur le terrain ou analyse au séquenceur), dessiné dans une zone de ZL x ZH dont le coin est (ox, oy).
abstract class MiniGame {
    static final int ZL = 280, ZH = 118;
    boolean finished, succes, perfect;
    int ox, oy;
    // Bouton de la souris maintenu (pour les jeux où l'on aspire, trace ou brosse).
    boolean press;

    void tick() {}

    abstract void draw(GuiGraphics g, int mx, int my, float pt);

    void click(double mx, double my) {}

    void released(double mx, double my) {}

    boolean pressedKey(int pressedKey) {
        return false;
    }

    abstract String instruction();

    void end(boolean ok) {
        finished = true;
        succes = ok;
    }

    // Dernier quart du temps : un tic toutes les demi-secondes, de plus en plus aigu, pour presser le joueur.
    void press1(int time, int duration) {
        if (time > 0 && time * 4 < duration && time % 10 == 0) {
            Draw.beep(Sounds.TICK, 1.2f + 0.8f * (1 - time * 4f / duration), 0.6f);
        }
    }

    // Quelque chose à mémoriser apparaît / disparaît.
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

    // Aire de jeu des mini-jeux de la 1.1 : 160 x 112 à gauche, compteurs et consigne à droite.
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
