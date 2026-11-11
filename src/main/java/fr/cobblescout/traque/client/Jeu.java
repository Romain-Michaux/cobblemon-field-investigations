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
}
