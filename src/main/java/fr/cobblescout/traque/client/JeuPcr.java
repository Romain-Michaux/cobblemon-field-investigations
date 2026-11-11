package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Cycles PCR : répéter la suite de températures
final class JeuPcr extends Jeu {
    static final String[] PALIERS = {"95°", "72°", "55°", "4°"};
    static final int[] COULEURS = {0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9, 0xFF6CCB5F};
    // Une note par palier, de la plus aiguë (95°) à la plus grave (4°) : la suite se retient aussi à l'oreille.
    static final float[] NOTES = {1.5f, 1.19f, 0.94f, 0.75f};

    static void sonner(int palier) {
        Dessin.bip(Sons.CARILLON, NOTES[palier], 0.8f);
    }
    final Random h;
    int[] suite;
    int manche, erreurs, montre, saisie, allume = -1, duree;

    JeuPcr(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        suite = new int[3 + manche];
        for (int i = 0; i < suite.length; i++) suite[i] = h.nextInt(4);
        montre = -16;
        saisie = -1;
    }

    @Override
    void tick() {
        if (duree > 0 && --duree == 0) allume = -1;
        if (saisie >= 0) return;
        montre++;
        if (montre >= 0 && montre % 13 == 0) {
            int i = montre / 13;
            if (i >= suite.length) {
                saisie = 0;
                return;
            }
            allume = suite[i];
            duree = 9;
            sonner(suite[i]);
        }
    }

    @Override
    void clic(double mx, double my) {
        if (saisie < 0) return;
        for (int b = 0; b < 4; b++) {
            if (!Dessin.dans(mx, my, ox + 12 + (b % 2) * 70, oy + 8 + (b / 2) * 52, 64, 46)) continue;
            allume = b;
            duree = 5;
            if (b != suite[saisie]) {
                Dessin.bip(Sons.ECHEC, 0.5f, 0.9f);
                if (++erreurs >= 2) terminer(false);
                else nouvelle();
                return;
            }
            sonner(b);
            if (++saisie >= suite.length) {
                Dessin.bip(Sons.CARILLON, 1.6f, 0.6f); // cycle réussi
                if (++manche >= 3) {
                    parfait = erreurs == 0;
                    terminer(true);
                } else nouvelle();
            }
            return;
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        for (int b = 0; b < 4; b++) {
            int x = ox + 12 + (b % 2) * 70, y = oy + 8 + (b / 2) * 52;
            boolean vif = allume == b;
            boolean survol = saisie >= 0 && !fini && Dessin.dans(mx, my, x, y, 64, 46);
            Dessin.bouton(g, x, y, 64, 46, PALIERS[b], false, true, vif ? Dessin.assombrir(COULEURS[b], 1.25) : Dessin.assombrir(COULEURS[b], survol ? 0.75 : 0.5));
        }
        int tx = ox + 160;
        Dessin.ecrire(g, T.f("cycle") + Math.min(3, manche + 1) + " / 3", tx, oy + 12, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("mistakes") + erreurs + " / 2", tx, oy + 24, erreurs > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
        Dessin.ecrire(g, saisie < 0 ? T.f("watch_the_program") : T.f("your_turn_repeat_it"), tx, oy + 44, saisie < 0 ? Dessin.JAUNE : Dessin.ACCENT, false);
        for (int i = 0; i < suite.length; i++) {
            g.fill(tx + i * 12, oy + 60, tx + i * 12 + 9, oy + 69, Dessin.BORD);
            g.fill(tx + i * 12 + 1, oy + 61, tx + i * 12 + 8, oy + 68, saisie > i ? COULEURS[suite[i]] : Dessin.ENCART_CLAIR);
        }
        Dessin.paragraphe(g, T.f("the_program_gets_one_step"), tx, oy + 80, 112, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("watch_the_sequence_of_temperature");
    }
}
