package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Grimpe (enquête des cimes) : attraper chaque prise du bon côté, au bon moment, sans toucher aux branches mortes
final class JeuGrimpe extends Jeu {
    // Le tronc est une bande de 56 px de haut, bien droite, qui se répète sans couture et défile ; la forêt du fond reste fixe.
    static final ResourceLocation TRONC = Dessin.texture("tronc"), FORET = Dessin.texture("foret");
    static final int BANDE = 56, TRONC_X = 56, TRONC_L = 48;
    // Branches vivantes et mortes, tournées vers la gauche ou vers la droite.
    static final ResourceLocation[][] BRANCHES = {{Dessin.texture("branche_g"), Dessin.texture("branche_d")},
            {Dessin.texture("branche_morte_g"), Dessin.texture("branche_morte_d")}};
    static final int N = 20, LIGNE = 84, MARGE = 7;
    final int[] cote = new int[N], hauteur = new int[N];
    final boolean[] morte = new boolean[N];
    // 0 = à venir, 1 = passée (attrapée, ou branche morte évitée), 2 = manquée.
    final int[] etat = new int[N];
    double avancee;
    int prochaine, fautes, flash, prises, bonnes;

    JeuGrimpe(Random h) {
        int y = 0;
        for (int i = 0; i < N; i++) {
            cote[i] = h.nextInt(2);
            // Des écarts irréguliers : parfois deux prises coup sur coup, parfois un long vide.
            y += 20 + h.nextInt(26);
            hauteur[i] = y;
            morte[i] = i > 1 && h.nextInt(5) == 0;
            if (!morte[i]) bonnes++;
        }
    }

    double y(int i) {
        return avancee - 20 - hauteur[i];
    }

    double vitesse() {
        return 1.7 + avancee / 330;
    }

    void rate() {
        etat[prochaine++] = 2;
        flash = 8;
        Dessin.bip(Sons.CRAQUEMENT, 0.9f, 0.8f);
        if (++fautes >= 3) terminer(false);
    }

    void suite() {
        if (!fini && prochaine >= N) {
            parfait = fautes == 0;
            terminer(true);
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        avancee += vitesse();
        if (prochaine < N && y(prochaine) > LIGNE + MARGE) {
            if (morte[prochaine]) etat[prochaine++] = 1; // une branche morte qu'on a laissée passer : c'est ce qu'il fallait
            else rate();
            suite();
        }
    }

    @Override
    void clic(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (prochaine >= N || !Dessin.dans(mx, my, bx, by, L, H)) return;
        int c = mx < bx + 80 ? 0 : 1;
        boolean aPortee = Math.abs(y(prochaine) - LIGNE) <= MARGE;
        if (aPortee && !morte[prochaine] && c == cote[prochaine]) {
            etat[prochaine++] = 1;
            Dessin.bip(Sons.BRANCHE, 0.8f + prises * 0.05f, 0.9f);
            prises++;
        } else {
            rate(); // trop tôt, trop tard, du mauvais côté, ou une branche morte qui casse
        }
        suite();
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        // Entre deux tics, tout est dessiné un peu plus loin : la montée reste fluide.
        double glisse = fini ? 0 : pt * vitesse(), monte = avancee + glisse;
        g.enableScissor(bx, by, bx + L, by + H);
        Dessin.image(g, FORET, bx, by, L, H);
        // Les branches sont dessinées avant le tronc, qui recouvre leur base : elles ont l'air d'en sortir.
        for (int i = 0; i < N; i++) {
            double y = y(i) + glisse;
            if (etat[i] != 0 || y < -18 || y > H + 18) continue;
            int x = bx + (cote[i] == 0 ? TRONC_X - 26 : TRONC_X + TRONC_L - 6), py = by + (int) y;
            boolean pret = i == prochaine && Math.abs(y(i) - LIGNE) <= MARGE && !morte[i];
            if (pret) g.fill(x - 2, py - 10, x + 34, py + 10, 0x7040E070);
            Dessin.image(g, BRANCHES[morte[i] ? 1 : 0][cote[i]], x, py - 16, 32, 32);
        }
        int tronc = (int) monte % BANDE;
        g.fill(bx + TRONC_X - 2, by, bx + TRONC_X + TRONC_L + 2, by + H, 0x60000000);
        for (int k = -1; k <= 1; k++) Dessin.image(g, TRONC, bx + TRONC_X, by + tronc + k * BANDE, TRONC_L, BANDE);
        // La zone d'atteinte : c'est là qu'il faut saisir la branche.
        g.fill(bx, by + LIGNE - MARGE, bx + L, by + LIGNE + MARGE, 0x30FFFFFF);
        g.fill(bx, by + LIGNE - MARGE, bx + L, by + LIGNE - MARGE + 1, 0xA0FFFFFF);
        g.fill(bx, by + LIGNE + MARGE - 1, bx + L, by + LIGNE + MARGE, 0xA0FFFFFF);
        g.disableScissor();
        int tx = ox + 172;
        texte(g, T.f("holds") + prises + " / " + bonnes, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("slips_2") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("grey_split_branches_are_dead"), tx, oy + 42, 104, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("click_left_or_right_on");
    }
}
