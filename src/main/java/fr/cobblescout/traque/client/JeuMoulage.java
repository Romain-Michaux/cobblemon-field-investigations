package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Moulage : couler le plâtre dans chaque alvéole de la mâchoire, à ras bord, sans déborder
final class JeuMoulage extends Jeu {
    static final int N = 5, DUREE = 560, HAUT_BLOC = 24, PAS = 30;
    // Chaque alvéole a sa largeur et sa profondeur (une canine est étroite et profonde, une molaire large et courte).
    final int[] largeur = new int[N], cible = new int[N];
    final double[] niveau = new double[N];
    int temps = DUREE, fautes, flash, tic;
    double cx;

    JeuMoulage(Random h) {
        for (int i = 0; i < N; i++) {
            largeur[i] = 16 + h.nextInt(3) * 4;
            cible[i] = 34 + h.nextInt(44);
        }
    }

    int gauche(int i) {
        return ox + 4 + 6 + i * PAS + (PAS - largeur[i]) / 2;
    }

    int colonne(double mx) {
        for (int i = 0; i < N; i++) {
            if (mx >= gauche(i) && mx < gauche(i) + largeur[i]) return i;
        }
        return -1;
    }

    boolean plein(int i) {
        return niveau[i] >= cible[i] - 6;
    }

    // Largeur de l'alvéole à cette hauteur au-dessus du fond : la racine est plus étroite que la couronne.
    int retrait(int i, int hauteur) {
        return hauteur < cible[i] * 0.4 ? 4 : hauteur < cible[i] * 0.55 ? 2 : 0;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (--temps <= 0) {
            terminer(false);
            return;
        }
        presser(temps, DUREE);
        tic++;
        int i = appui ? colonne(cx) : -1;
        if (i >= 0) {
            niveau[i] += 1.1;
            if (tic % 5 == 0) Dessin.bip(Sons.PLATRE, (float) (0.6 + niveau[i] / cible[i]), 0.5f);
            if (niveau[i] > cible[i] + 1.5) {
                // Débordement : il faut gratter et recommencer cette alvéole presque à zéro.
                niveau[i] = cible[i] * 0.3;
                flash = 8;
                Dessin.bip(Sons.DEBORDEMENT, 1f, 0.9f);
                if (++fautes >= 3) terminer(false);
            }
        }
        // Le moulage n'est validé qu'une fois le bec relevé : c'est au joueur de s'arrêter à temps.
        boolean tous = !appui;
        for (int k = 0; k < N; k++) tous &= plein(k);
        if (tous && !fini) {
            parfait = fautes == 0;
            Dessin.bip(Sons.PIERRE, 1.2f, 0.8f);
            terminer(true);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3, haut = by + HAUT_BLOC;
        cx = mx;
        cadre(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
        // Le bloc de gencive, vu en coupe.
        g.fill(bx, haut, bx + L, by + H, 0xFFB5746A);
        g.fill(bx, haut, bx + L, haut + 3, 0xFFD69A8C);
        g.fill(bx, by + H - 10, bx + L, by + H, 0xFF8F564E);
        int survol = fini ? -1 : colonne(mx);
        for (int i = 0; i < N; i++) {
            int x = gauche(i), fond = haut + cible[i];
            for (int k = 0; k < cible[i]; k++) {
                int r = retrait(i, k), y = fond - 1 - k;
                g.fill(x + r, y, x + largeur[i] - r, y + 1, k < niveau[i] ? (k >= (int) niveau[i] - 1 ? 0xFFFFFFFF : 0xFFE9E4D8) : 0xFF3A1A1E);
            }
            // La bonne hauteur : les six derniers pixels sous le bord.
            g.fill(x, haut, x + 2, haut + 6, Dessin.VERT);
            g.fill(x + largeur[i] - 2, haut, x + largeur[i], haut + 6, Dessin.VERT);
            if (i == survol) g.fill(x, haut - 2, x + largeur[i], haut - 1, Dessin.JAUNE);
            if (plein(i)) texte(g, "✔", x + largeur[i] / 2 - 3, haut - 11, Dessin.VERT);
        }
        // Le bec verseur suit la souris.
        if (!fini && Dessin.dans(mx, my, bx, by, L, H)) {
            g.fill(mx - 3, by + 1, mx + 3, by + 9, 0xFF9FB0C0);
            if (appui && survol >= 0) g.fill(mx - 1, by + 9, mx + 1, haut + cible[survol] - (int) niveau[survol], 0xFFF4F0E6);
        }
        int tx = ox + 172, faits = 0;
        for (int i = 0; i < N; i++) faits += plein(i) ? 1 : 0;
        texte(g, T.f("sockets") + faits + " / " + N, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("spilled") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("fill_up_to_the_green"), tx, oy + 40, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("put_the_spout_over_a_2");
    }
}
