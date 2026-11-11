package fr.cobblescout.traque.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

// Un mini-jeu (prélèvement sur le terrain ou analyse au séquenceur), dessiné dans une zone de ZL x ZH dont le coin est (ox, oy).
abstract class Jeu {
    static final int ZL = 280, ZH = 118;
    boolean fini, succes, parfait;
    int ox, oy;
    // Bouton de la souris maintenu (pour les jeux où l'on aspire, trace ou brosse).
    boolean appui;

    void tick() {}

    abstract void dessiner(GuiGraphics g, int mx, int my, float pt);

    void clic(double mx, double my) {}

    void relache(double mx, double my) {}

    boolean touche(int touche) {
        return false;
    }

    abstract String consigne();

    void terminer(boolean ok) {
        fini = true;
        succes = ok;
    }

    // Dernier quart du temps : un tic toutes les demi-secondes, de plus en plus aigu, pour presser le joueur.
    void presser(int temps, int duree) {
        if (temps > 0 && temps * 4 < duree && temps % 10 == 0) {
            Dessin.bip(Sons.TIC, 1.2f + 0.8f * (1 - temps * 4f / duree), 0.6f);
        }
    }

    // Quelque chose à mémoriser apparaît / disparaît.
    void sonMontre() {
        Dessin.bip(Sons.CARILLON, 1.2f, 0.5f);
    }

    void sonCache() {
        Dessin.bip(Sons.PAPIER, 1.4f, 0.8f);
    }

    // Jauge de temps en bas de la zone.
    void jaugeTemps(GuiGraphics g, int x, int y, int l, double part) {
        g.fill(x, y, x + l, y + 6, Dessin.BORD);
        g.fill(x + 1, y + 1, x + 1 + (int) ((l - 2) * Math.max(0, Math.min(1, part))), y + 5, part > 0.25 ? Dessin.ACCENT : Dessin.ALERTE);
    }

    // Aire de jeu des mini-jeux de la 1.1 : 160 x 112 à gauche, compteurs et consigne à droite.
    static final int L = 160, H = 112;

    static void cadre(GuiGraphics g, int bx, int by, boolean alerte) {
        g.fill(bx - 1, by - 1, bx + L + 1, by + H + 1, alerte ? Dessin.ALERTE : Dessin.BORD);
    }

    static void texte(GuiGraphics g, String s, int x, int y, int couleur) {
        Dessin.ecrire(g, s, x, y, couleur, false);
    }

    // Jauge horizontale (part de 0 à 1).
    static void jauge(GuiGraphics g, int x, int y, int l, double part, int couleur) {
        g.fill(x, y, x + l, y + 6, Dessin.BORD);
        g.fill(x + 1, y + 1, x + 1 + (int) ((l - 2) * Math.max(0, Math.min(1, part))), y + 5, couleur);
    }

    static void disque(GuiGraphics g, int cx, int cy, int rayon, int couleur) {
        for (int dy = -rayon; dy <= rayon; dy++) {
            int dx = (int) Math.round(Math.sqrt(rayon * rayon - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, couleur);
        }
    }

    static int melange(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t), v = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (v << 8) | bl;
    }
}
