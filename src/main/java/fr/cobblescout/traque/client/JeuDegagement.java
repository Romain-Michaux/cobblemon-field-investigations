package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Dégagement au pinceau (indice enfoui) : trouver les fragments cachés sous la terre, en brossant doucement
final class JeuDegagement extends Jeu {
    static final ResourceLocation TERRE = Dessin.texture("terre");
    // La terre fait 40 x 28 cases de 4 px. Chaque fragment (le dessin de l'indice, agrandi deux fois) en couvre 8 x 8.
    static final int COLS = 40, RANGS = 28, CASE = 4, DUREE = 700, FRAGMENTS = 3, TAILLE = 8, RAYON = 9;
    static final double VITESSE_MAX = 17;
    final ResourceLocation indice;
    // Épaisseur de terre restante sur chaque case : 1 = intacte, 0 = dégagée. Elle part peu à peu sous le pinceau.
    final float[] terre = new float[COLS * RANGS];
    final int[] fc = new int[FRAGMENTS], fr = new int[FRAGMENTS];
    final boolean[] trouve = new boolean[FRAGMENTS];
    int temps = DUREE, fautes, flash, repos, trouves, tic;
    double sx = -1, sy = -1, cx, cy;

    JeuDegagement(Random h, String genre, int variante) {
        indice = Dessin.iconeEnfoui(genre, variante);
        java.util.Arrays.fill(terre, 1f);
        // Les fragments sont enfouis n'importe où, sans se chevaucher.
        for (int i = 0; i < FRAGMENTS; i++) {
            for (int essai = 0; essai < 200; essai++) {
                fc[i] = h.nextInt(COLS - TAILLE + 1);
                fr[i] = h.nextInt(RANGS - TAILLE + 1);
                boolean libre = true;
                for (int k = 0; k < i; k++) libre &= Math.abs(fc[k] - fc[i]) > TAILLE || Math.abs(fr[k] - fr[i]) > TAILLE;
                if (libre) break;
            }
        }
    }

    int fragment(int c, int r) {
        for (int i = 0; i < FRAGMENTS; i++) {
            if (c >= fc[i] && c < fc[i] + TAILLE && r >= fr[i] && r < fr[i] + TAILLE) return i;
        }
        return -1;
    }

    int part(int i) {
        int n = 0;
        for (int r = fr[i]; r < fr[i] + TAILLE; r++) {
            for (int c = fc[i]; c < fc[i] + TAILLE; c++) {
                if (terre[r * COLS + c] <= 0) n++;
            }
        }
        return n * 100 / (TAILLE * TAILLE);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (repos > 0) repos--;
        if (--temps <= 0) {
            terminer(false);
            return;
        }
        presser(temps, DUREE);
        int bx = ox + 4, by = oy + 3;
        boolean surLaTerre = Dessin.dans(cx, cy, bx - RAYON, by - RAYON, 160 + 2 * RAYON, 112 + 2 * RAYON);
        if (appui && surLaTerre && sx >= 0) {
            double vitesse = Math.hypot(cx - sx, cy - sy);
            if (repos == 0 && vitesse > VITESSE_MAX) {
                // Un geste trop brusque abîme ce qui est enfoui.
                flash = 8;
                repos = 12;
                Dessin.note(0.5f);
                if (++fautes >= 3) terminer(false);
            } else if (repos == 0) {
                // Le pinceau enlève la terre peu à peu : plus au centre que sur les bords, et il faut s'attarder.
                for (int i = 0; i < terre.length; i++) {
                    if (terre[i] <= 0) continue;
                    double d = Math.hypot(cx - (bx + (i % COLS) * CASE + 2), cy - (by + (i / COLS) * CASE + 2));
                    if (d < RAYON) terre[i] -= (float) (0.3 * (1 - d / RAYON) + 0.03);
                }
                if (++tic % 5 == 0) Dessin.bip(Sons.PINCEAU, (float) (0.9 + Math.min(0.5, vitesse / 30)), 0.7f);
                for (int i = 0; i < FRAGMENTS; i++) {
                    if (trouve[i] || part(i) < 80) continue;
                    trouve[i] = true;
                    Dessin.note(1f + trouves * 0.25f);
                    if (++trouves >= FRAGMENTS) {
                        parfait = fautes == 0;
                        terminer(true);
                    }
                }
            }
        }
        sx = cx;
        sy = cy;
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
        Dessin.image(g, TERRE, bx, by, 160, 112);
        for (int i = 0; i < terre.length; i++) {
            float e = terre[i];
            if (e >= 1f) continue;
            int c = i % COLS, r = i / COLS, x = bx + c * CASE, y = by + r * CASE, f = fragment(c, r);
            if (e <= 0) {
                g.fill(x, y, x + CASE, y + CASE, 0xFF2B2018);
                // Le morceau du fragment sous cette case (2 x 2 pixels du dessin, agrandis deux fois).
                if (f >= 0) Dessin.morceauAgrandi(g, indice, x, y, CASE, CASE, (c - fc[f]) * 2, (r - fr[f]) * 2, 2, 2, 16, 16);
            } else {
                // Terre entamée : elle fonce ; au-dessus d'un fragment, une pâleur le laisse deviner.
                g.fill(x, y, x + CASE, y + CASE, ((int) ((1 - e) * 150) << 24) | 0x1E1610);
                if (f >= 0 && e < 0.75f) g.fill(x, y, x + CASE, y + CASE, ((int) ((0.75f - e) * 110) << 24) | 0xE6D8B4);
            }
        }
        for (int i = 0; i < FRAGMENTS; i++) {
            if (!trouve[i]) continue;
            int x = bx + fc[i] * CASE, y = by + fr[i] * CASE, l = TAILLE * CASE;
            g.fill(x - 1, y - 1, x + l + 1, y, Dessin.VERT);
            g.fill(x - 1, y + l, x + l + 1, y + l + 1, Dessin.VERT);
            g.fill(x - 1, y, x, y + l, Dessin.VERT);
            g.fill(x + l, y, x + l + 1, y + l, Dessin.VERT);
        }
        if (!fini && Dessin.dans(mx, my, bx, by, 160, 112)) Dessin.cercle(g, mx - 1, my - 1, RAYON, 2, appui ? Dessin.JAUNE : Dessin.TEXTE);
        int tx = ox + 172;
        Dessin.ecrire(g, T.f("fragments") + trouves + " / " + FRAGMENTS, tx, oy + 12, trouves > 0 ? Dessin.VERT : Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("snags") + fautes + " / 3", tx, oy + 24, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
        Dessin.ecrire(g, flash > 0 ? T.f("too_rough") : T.f("brush_gently"), tx, oy + 44, flash > 0 ? Dessin.ALERTE : Dessin.JAUNE, false);
        Dessin.paragraphe(g, T.f("go_slowly_a_pale_patch"), tx, oy + 60, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("brush_the_soil_hold_the");
    }
}
