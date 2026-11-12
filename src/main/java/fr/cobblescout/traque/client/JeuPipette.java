package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Pipetage (dépôt étrange) : aspirer juste ce qu'il faut
final class JeuPipette extends Jeu {
    final Random h;
    double niveau, cibleBas, cibleHaut, vitesse;
    int manche, fautes, pause, tic;
    boolean aspire, dernierOk, dansLeVert;

    JeuPipette(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        double largeur = 0.17 - manche * 0.035;
        cibleBas = 0.28 + h.nextDouble() * (0.62 - largeur);
        cibleHaut = cibleBas + largeur;
        niveau = 0;
        aspire = false;
        vitesse = 0.024 + manche * 0.009;
    }

    @Override
    void tick() {
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (!aspire) return;
        niveau += vitesse;
        // L'aspiration s'entend : une note qui monte avec le niveau, et un tintement à l'entrée dans la zone verte.
        if (++tic % 2 == 0) Dessin.bip(Sons.SCAN, (float) (0.6 + Math.min(1, niveau) * 1.3), 0.35f);
        if (!dansLeVert && niveau >= cibleBas && niveau <= cibleHaut) {
            dansLeVert = true;
            Dessin.bip(Sons.CARILLON, 1.5f, 0.45f);
        }
        if (niveau >= 1) {
            niveau = 1;
            juger();
        }
    }

    void juger() {
        aspire = false;
        dernierOk = niveau >= cibleBas && niveau <= cibleHaut;
        pause = 14;
        if (dernierOk) {
            Dessin.note(1f + manche * 0.25f);
            if (++manche >= 3) {
                parfait = fautes == 0;
                terminer(true);
            }
        } else {
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
        }
    }

    @Override
    void clic(double mx, double my) {
        if (pause == 0 && !aspire) {
            aspire = true;
            dansLeVert = false;
            tic = 0;
            Dessin.bip(Sons.FIOLE, 1.3f, 0.5f);
        }
    }

    @Override
    void relache(double mx, double my) {
        if (aspire) juger();
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int tx = ox + 44, ty = oy + 9, tl = 26, th = 100;
        g.fill(tx - 2, ty - 2, tx + tl + 2, ty + th + 2, Dessin.BORD);
        g.fill(tx - 1, ty - 1, tx + tl + 1, ty + th + 1, 0xFF9FB0C0);
        g.fill(tx, ty, tx + tl, ty + th, 0xFF14202B);
        double n = Math.min(1, aspire ? niveau + vitesse * pt : niveau);
        int haut = (int) Math.round(th * n);
        int liquide = pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : 0xFF9B5FC0;
        g.fill(tx, ty + th - haut, tx + tl, ty + th, liquide);
        if (haut > 2) g.fill(tx, ty + th - haut, tx + tl, ty + th - haut + 2, Dessin.assombrir(liquide, 1.35));
        int yBas = ty + th - (int) Math.round(th * cibleBas), yHaut = ty + th - (int) Math.round(th * cibleHaut);
        g.fill(tx - 8, yHaut, tx - 3, yBas, Dessin.VERT);
        g.fill(tx + tl + 3, yHaut, tx + tl + 8, yBas, Dessin.VERT);
        g.fill(tx, yHaut, tx + tl, yHaut + 1, 0xC06CCB5F);
        g.fill(tx, yBas - 1, tx + tl, yBas, 0xC06CCB5F);
        for (int i = 1; i < 10; i++) g.fill(tx, ty + i * 10, tx + (i % 5 == 0 ? 8 : 4), ty + i * 10 + 1, 0xFF5D6E80);
        int px = ox + 110;
        Dessin.ecrire(g, T.f("dose") + Math.min(3, manche + 1) + " / 3", px, oy + 14, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("faults") + fautes + " / 3", px, oy + 26, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
        Dessin.ecrire(g, pause > 0 ? (dernierOk ? T.f("right_dose") : niveau >= 1 ? T.f("overflow") : T.f("wrong_dose")) : aspire ? T.f("release_in_the_green") : T.f("hold_the_click"),
                px, oy + 46, pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.JAUNE, false);
        Dessin.paragraphe(g, T.f("the_green_zone_shrinks_and"), px, oy + 66, 160, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_to_draw");
    }
}
