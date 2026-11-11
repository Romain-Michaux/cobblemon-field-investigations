package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Éboulis (enquête souterraine) : dégager la pierre qui cache l'indice, en retirant d'abord celles qui pèsent dessus
final class JeuEboulis extends Jeu {
    static final ResourceLocation PAROI = Dessin.texture("paroi");
    // Cinq rangées en quinconce, comme un mur de pierres sèches : six pierres sur les rangées paires, cinq sur les impaires.
    static final int RANGS = 5, PL = 24, PH = 17, DUREE = 460;
    final boolean[][] presente = new boolean[RANGS][6];
    // Coups de pioche restant à donner : une pierre ordinaire en demande un, une pierre massive trois.
    final int[][] coups = new int[RANGS][6];
    final int cible;
    int temps = DUREE, fautes, flash, secousse, retirees;

    JeuEboulis(Random h) {
        for (int r = 0; r < RANGS; r++) {
            for (int k = 0; k < nombre(r); k++) {
                presente[r][k] = true;
                coups[r][k] = r > 0 && h.nextInt(5) == 0 ? 3 : 1;
            }
        }
        cible = 1 + h.nextInt(4);
    }

    static int nombre(int rang) {
        return rang % 2 == 0 ? 6 : 5;
    }

    // Coin haut-gauche de la pierre (rang 0 = en bas), dans l'aire de jeu.
    int px(int rang, int k) {
        return ox + 4 + 8 + k * PL + (rang % 2 == 0 ? 0 : PL / 2);
    }

    int py(int rang) {
        return oy + 3 + 104 - (rang + 1) * PH;
    }

    // Une pierre est libre si aucune de celles qui reposent sur elle n'est encore en place.
    boolean libre(int rang, int k) {
        if (rang == RANGS - 1) return true;
        int a = rang % 2 == 0 ? k - 1 : k, b = a + 1;
        return !(a >= 0 && a < nombre(rang + 1) && presente[rang + 1][a]) && !(b >= 0 && b < nombre(rang + 1) && presente[rang + 1][b]);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (secousse > 0) secousse--;
        if (--temps <= 0) terminer(false);
        else presser(temps, DUREE);
    }

    @Override
    void clic(double mx, double my) {
        for (int r = RANGS - 1; r >= 0; r--) {
            for (int k = 0; k < nombre(r); k++) {
                if (!presente[r][k] || !Dessin.dans(mx, my, px(r, k), py(r), PL, PH)) continue;
                if (!libre(r, k)) {
                    // Tirer une pierre chargée fait tout trembler.
                    flash = 8;
                    secousse = 10;
                    Dessin.bip(Sons.EBOULEMENT, 0.6f, 1f);
                    if (++fautes >= 3) terminer(false);
                } else if (--coups[r][k] > 0) {
                    Dessin.bip(Sons.PIOCHE, 0.8f + (3 - coups[r][k]) * 0.2f, 0.9f);
                } else {
                    presente[r][k] = false;
                    retirees++;
                    Dessin.bip(Sons.PIERRE, 0.9f + r * 0.08f, 0.8f);
                    if (r == 0 && k == cible) {
                        parfait = fautes == 0;
                        Dessin.note(1.5f);
                        terminer(true);
                    }
                }
                return;
            }
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        g.setColor(0.55f, 0.55f, 0.6f, 1f);
        Dessin.image(g, PAROI, bx, by, L, H);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(bx, by + 104, bx + L, by + H, 0xFF2A2420);
        // La lueur de l'indice, derrière la pierre à dégager.
        int lx = px(0, cible), ly = py(0);
        boolean pulse = (System.currentTimeMillis() / 350) % 2 == 0;
        g.fill(lx + 2, ly + 2, lx + PL - 2, ly + PH - 1, pulse ? 0xFF7FE0D0 : 0xFF4FB8A8);
        g.fill(lx + 8, ly + 5, lx + PL - 8, ly + PH - 5, 0xFFE0FFF8);
        for (int r = 0; r < RANGS; r++) {
            for (int k = 0; k < nombre(r); k++) {
                if (!presente[r][k]) continue;
                int dx = secousse > 0 ? ((secousse + r + k) % 2) * 2 - 1 : 0, x = px(r, k) + dx, y = py(r);
                boolean libre = libre(r, k), massive = coups[r][k] > 1, visee = r == 0 && k == cible;
                boolean survol = !fini && libre && Dessin.dans(mx, my, px(r, k), y, PL, PH);
                int corps = massive ? 0xFF5A5660 : 0xFF8C8884;
                g.fill(x, y + 1, x + PL, y + PH, survol ? Dessin.JAUNE : visee ? 0xFF4FD1C5 : 0xFF1C1A1E);
                g.fill(x + 1, y + 2, x + PL - 1, y + PH - 1, corps);
                g.fill(x + 2, y + 2, x + PL - 2, y + 4, Dessin.assombrir(corps, 1.3));
                g.fill(x + 1, y + PH - 4, x + PL - 1, y + PH - 1, Dessin.assombrir(corps, 0.75));
                g.fill(x + 1, y + 2, x + 2, y + 3, 0xFF1C1A1E);
                g.fill(x + PL - 2, y + 2, x + PL - 1, y + 3, 0xFF1C1A1E);
                // Une pierre massive porte autant d'entailles qu'il lui reste de coups à recevoir.
                for (int c = 0; massive && c < coups[r][k]; c++) g.fill(x + 6 + c * 5, y + 7, x + 8 + c * 5, y + 12, 0xFF1C1A1E);
                if (!libre) g.fill(x + 1, y + 2, x + PL - 1, y + PH - 1, 0x50000000);
            }
        }
        int tx = ox + 172;
        texte(g, T.f("stones_removed") + retirees, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("rockslides") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("darkened_stones_still_carry_weight"), tx, oy + 40, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("free_the_glowing_stone_at");
    }
}
