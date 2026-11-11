package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Électrophorèse : retrouver, parmi des pistes presque identiques, celle de l'échantillon
final class JeuGel extends Jeu {
    static final ResourceLocation BAC = Dessin.texture("gel");
    // Une bande = sa position (FENTES crans de 5 px), son épaisseur (fine ou large) et son intensité (pâle ou vive).
    static final int FENTES = 14, PAS = 30, LARGEUR = 22;
    final Random h;
    int[][] pistes;          // pistes[0] = l'échantillon ; chaque piste : FENTES valeurs (0 = rien, sinon bit 0 = large, bit 1 = vive, +4)
    int bonne, manche, erreurs, temps, duree, pause, choisie = -1;
    boolean dernierOk;

    JeuGel(Random h) {
        this.h = h;
        nouvelle();
    }

    int bande(boolean large, boolean vive) {
        return 4 | (large ? 1 : 0) | (vive ? 2 : 0);
    }

    // Une piste voisine : une seule différence discrète avec le modèle (bande décalée d'un cran, épaisseur ou
    // intensité changée, bande pâle en plus ou en moins).
    int[] variante(int[] modele) {
        for (int essai = 0; essai < 200; essai++) {
            int[] p = modele.clone();
            int i = h.nextInt(FENTES);
            switch (h.nextInt(manche == 0 ? 3 : 5)) {
                case 0 -> {
                    int vers = i + (h.nextBoolean() ? 1 : -1);
                    if (p[i] == 0 || vers < 0 || vers >= FENTES || p[vers] != 0) continue;
                    p[vers] = p[i];
                    p[i] = 0;
                }
                case 1 -> {
                    if (p[i] == 0) continue;
                    p[i] ^= 1;
                }
                case 2 -> {
                    if (p[i] == 0) continue;
                    p[i] ^= 2;
                }
                case 3 -> {
                    if (p[i] != 0) continue;
                    p[i] = bande(false, false);
                }
                default -> {
                    if (p[i] == 0 || (p[i] & 2) != 0) continue;
                    p[i] = 0;
                }
            }
            return p;
        }
        return modele.clone();
    }

    void nouvelle() {
        int nombre = manche == 0 ? 5 : 6;
        pistes = new int[nombre + 1][];
        int[] modele = new int[FENTES];
        for (int n = 0; n < 6 + manche; ) {
            int i = h.nextInt(FENTES);
            if (modele[i] != 0) continue;
            modele[i] = bande(h.nextInt(3) == 0, h.nextBoolean());
            n++;
        }
        pistes[0] = modele;
        bonne = 1 + h.nextInt(nombre);
        for (int k = 1; k <= nombre; k++) {
            if (k == bonne) {
                pistes[k] = modele.clone();
                continue;
            }
            for (int essai = 0; essai < 100; essai++) {
                int[] p = variante(modele);
                boolean nouveau = !java.util.Arrays.equals(p, modele);
                for (int j = 1; j < k; j++) nouveau &= pistes[j] == null || !java.util.Arrays.equals(p, pistes[j]);
                pistes[k] = p;
                if (nouveau) break;
            }
        }
        duree = temps = 320 - manche * 30;
        choisie = -1;
    }

    void juger(int piste) {
        choisie = piste;
        dernierOk = piste == bonne;
        pause = dernierOk ? 16 : 40;
        if (dernierOk) {
            Dessin.note(1f + manche * 0.25f);
            if (++manche >= 3) {
                parfait = erreurs == 0;
                terminer(true);
            }
        } else {
            Dessin.note(0.5f);
            if (++erreurs >= 2) terminer(false);
        }
    }

    @Override
    void tick() {
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (--temps <= 0) juger(-1);
        else presser(temps, duree);
    }

    int px(int k) {
        return ox + 16 + k * PAS + (k > 0 ? 6 : 0);
    }

    @Override
    void clic(double mx, double my) {
        if (pause > 0) return;
        for (int k = 1; k < pistes.length; k++) {
            if (Dessin.dans(mx, my, px(k), oy + 20, LARGEUR, 78)) {
                juger(k);
                return;
            }
        }
    }

    void piste(GuiGraphics g, int k, int cadre) {
        int x = px(k);
        if (cadre != 0) {
            g.fill(x - 2, oy + 18, x + LARGEUR + 2, oy + 19, cadre);
            g.fill(x - 2, oy + 99, x + LARGEUR + 2, oy + 100, cadre);
            g.fill(x - 2, oy + 18, x - 1, oy + 100, cadre);
            g.fill(x + LARGEUR + 1, oy + 18, x + LARGEUR + 2, oy + 100, cadre);
        }
        g.fill(x, oy + 20, x + LARGEUR, oy + 98, 0x50081420);
        g.fill(x + 4, oy + 21, x + LARGEUR - 4, oy + 24, 0xFF0A1622);
        for (int i = 0; i < FENTES; i++) {
            int b = pistes[k][i];
            if (b == 0) continue;
            boolean large = (b & 1) != 0, vive = (b & 2) != 0;
            int y = oy + 27 + i * 5, ep = large ? 4 : 2, couleur = vive ? 0xFF8CFAEC : 0xFF3C9C98;
            if (vive) g.fill(x + 2, y - 1, x + LARGEUR - 2, y + ep + 1, 0x7030C8C0);
            g.fill(x + 3, y, x + LARGEUR - 3, y + ep, couleur);
            // Traînée de migration sous la bande.
            g.fill(x + 5, y + ep, x + LARGEUR - 5, y + ep + 1, 0x4030C8C0);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        jaugeTemps(g, ox + 6, oy + 3, 232, pause > 0 ? 0 : temps / (double) duree);
        Dessin.image(g, BAC, ox + 2, oy + 11, 240, 104);
        piste(g, 0, Dessin.JAUNE);
        Dessin.centre(g, T.f("smp"), px(0) + LARGEUR / 2, oy + 103, Dessin.JAUNE);
        for (int k = 1; k < pistes.length; k++) {
            boolean survol = pause == 0 && !fini && Dessin.dans(mx, my, px(k), oy + 20, LARGEUR, 78);
            int cadre = pause > 0 || fini ? (k == bonne ? Dessin.VERT : k == choisie ? Dessin.ALERTE : 0) : survol ? Dessin.TEXTE : 0;
            piste(g, k, cadre);
            Dessin.centre(g, String.valueOf(k), px(k) + LARGEUR / 2, oy + 103, cadre == 0 ? Dessin.GRIS : cadre);
        }
        int tx = ox + 246;
        Dessin.ecrire(g, T.f("gel"), tx, oy + 16, Dessin.GRIS, false);
        Dessin.ecrire(g, Math.min(3, manche + (pause > 0 || fini ? 0 : 1)) + " / 3", tx, oy + 27, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("err"), tx, oy + 46, Dessin.GRIS, false);
        Dessin.ecrire(g, erreurs + " / 2", tx, oy + 57, erreurs > 0 ? Dessin.ALERTE : Dessin.TEXTE, false);
        if (pause > 0 || fini) Dessin.ecrire(g, dernierOk ? T.f("yes") : T.f("no"), tx, oy + 78, dernierOk ? Dessin.VERT : Dessin.ALERTE, false);
    }

    @Override
    String consigne() {
        return T.f("click_the_lane_identical_to");
    }
}
