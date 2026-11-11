package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Empreinte dentaire : glisser la réglette de moulage sous la morsure, puis la presser pour voir si elle s'emboîte
final class JeuMorsure extends Jeu {
    // La morsure a 7 dents ; la réglette en a 13 et peut prendre 7 positions. Une seule épouse la morsure.
    static final int N = 7, M = 13, MAX = 6, U = 7, COL = 12, REPOS = 30;
    final Random h;
    final int[] morsure = new int[N], reglette = new int[M];
    int position, manche, fautes, temps, duree, flash, pause, montee = -1;
    boolean emboite;

    JeuMorsure(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        int bonne;
        do {
            for (int i = 0; i < N; i++) morsure[i] = 1 + h.nextInt(5);
            for (int j = 0; j < M; j++) reglette[j] = 1 + h.nextInt(5);
            bonne = h.nextInt(M - N + 1);
            for (int i = 0; i < N; i++) reglette[bonne + i] = MAX - morsure[i];
        } while (solutions() != 1);
        do {
            position = h.nextInt(M - N + 1);
        } while (position == bonne);
        duree = temps = 420 - manche * 80;
        montee = -1;
        emboite = false;
    }

    // Espace entre la dent i de la morsure et la dent de la réglette placée dessous (0 = elles se touchent déjà au repos).
    int jeu(int i, int pos) {
        return REPOS + (MAX - morsure[i] - reglette[pos + i]) * U;
    }

    // De combien la réglette peut monter avant de buter.
    int course(int pos) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < N; i++) min = Math.min(min, jeu(i, pos));
        return min;
    }

    boolean epouse(int pos) {
        for (int i = 0; i < N; i++) {
            if (jeu(i, pos) != REPOS) return false;
        }
        return true;
    }

    int solutions() {
        int n = 0;
        for (int pos = 0; pos <= M - N; pos++) n += epouse(pos) ? 1 : 0;
        return n;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (montee >= 0) {
            // La réglette monte jusqu'à buter, puis on regarde si toutes les dents sont en contact.
            montee++;
            if (montee == 8) {
                if (epouse(position)) {
                    emboite = true;
                    Dessin.bip(Sons.PIERRE, 0.6f, 1f);
                    Dessin.note(1f + manche * 0.3f);
                    if (++manche >= 2) {
                        parfait = fautes == 0;
                        terminer(true);
                    } else {
                        pause = 34;
                    }
                } else {
                    flash = 10;
                    Dessin.bip(Sons.PIERRE, 0.6f, 1f);
                    Dessin.note(0.5f);
                    if (++fautes >= 2) terminer(false);
                }
            } else if (montee >= 22 && !emboite) {
                montee = -1; // elle retombe : on peut réessayer ailleurs
            } else if (emboite && pause > 0 && --pause == 0 && !fini) {
                nouvelle(); // la réglette reste emboîtée un instant, puis la morsure suivante arrive
            }
            return;
        }
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (--temps <= 0) {
            flash = 8;
            Dessin.note(0.5f);
            if (++fautes >= 2) terminer(false);
            else pause = 24;
        } else {
            presser(temps, duree);
        }
    }

    void glisser(int sens) {
        int nouvelle = Math.max(0, Math.min(M - N, position + sens));
        if (nouvelle == position) return;
        position = nouvelle;
        Dessin.bip(Sons.PIERRE, 0.9f + position * 0.05f, 0.6f);
    }

    boolean libre() {
        return pause == 0 && montee < 0 && !fini;
    }

    // Une dent : un corps plein et une pointe qui s'affine sur ses quatre derniers pixels (vers le bas ou vers le haut).
    static void dent(GuiGraphics g, int x, int racine, int pointe, int couleur, int ombre) {
        int l = COL - 1, sens = pointe > racine ? 1 : -1, longueur = Math.abs(pointe - racine);
        for (int k = 0; k < longueur; k++) {
            int reste = longueur - 1 - k, r = reste < 2 ? 3 : reste < 4 ? 1 : 0, y = sens > 0 ? racine + k : racine - 1 - k;
            g.fill(x + r, y, x + l - r, y + 1, couleur);
            g.fill(x + l - r - 2, y, x + l - r, y + 1, ombre);
        }
    }

    @Override
    void clic(double mx, double my) {
        if (!libre()) return;
        int tx = ox + 172;
        if (Dessin.dans(mx, my, tx, oy + 40, 50, 18)) glisser(1);
        else if (Dessin.dans(mx, my, tx + 54, oy + 40, 50, 18)) glisser(-1);
        else if (Dessin.dans(mx, my, tx, oy + 66, 104, 22)) montee = 0;
    }

    @Override
    boolean touche(int touche) {
        if (!libre()) return false;
        if (touche == 263) glisser(1);
        else if (touche == 262) glisser(-1);
        else if (touche == 32 || touche == 257) montee = 0;
        else return false;
        return true;
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        cadre(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        int course = course(position);
        int leve = montee < 0 ? 0 : Math.min(course, montee * 5);
        boolean bute = montee >= 8 && !emboite;
        int x0 = ax + 2 + 3 * COL, haut = ay + 6, base = haut + MAX * U + REPOS + 6 - leve;
        // La morsure : une gencive en haut, des dents qui pendent.
        g.fill(x0 - 4, haut - 5, x0 + N * COL + 3, haut + 1, 0xFFC97A70);
        g.fill(x0 - 4, haut - 5, x0 + N * COL + 3, haut - 3, 0xFFE0A096);
        for (int i = 0; i < N; i++) {
            int x = x0 + i * COL, bas = haut + morsure[i] * U;
            boolean contact = bute && jeu(i, position) == course;
            dent(g, x, haut, bas, contact ? Dessin.ALERTE : emboite ? 0xFFBFE8C8 : 0xFFF4EEDC, contact ? 0xFF8E2B26 : emboite ? 0xFF8FC8A0 : 0xFFCFC4A6);
        }
        // La réglette de moulage : treize dents, dont sept seulement se trouvent sous la morsure.
        g.fill(ax + 2, base - 1, ax + 2 + M * COL - 1, base + 6, 0xFFC97A70);
        g.fill(ax + 2, base + 4, ax + 2 + M * COL - 1, base + 6, 0xFF9C5850);
        for (int j = 0; j < M; j++) {
            int ecran = j - position + 3;
            if (ecran < 0 || ecran >= M) continue;
            int x = ax + 2 + ecran * COL, sommet = base - reglette[j] * U;
            boolean dessous = ecran >= 3 && ecran < 3 + N;
            boolean contact = bute && dessous && jeu(ecran - 3, position) == course;
            dent(g, x, base - 1, sommet - 1, contact ? Dessin.ALERTE : !dessous ? 0xFF6E7A88 : emboite ? 0xFF8FD8A0 : 0xFFF2B56A,
                    contact ? 0xFF8E2B26 : !dessous ? 0xFF55606C : emboite ? 0xFF5FA870 : 0xFFC98A3A);
        }
        // Repères : la partie de la réglette qui sera pressée contre la morsure.
        g.fill(x0 - 2, ay + H - 6, x0 + N * COL + 1, ay + H - 4, Dessin.JAUNE);
        int tx = ox + 172;
        texte(g, T.f("bite") + Math.min(2, manche + 1) + " / 2", tx, oy + 5, Dessin.TEXTE);
        texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 16, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, emboite ? T.f("it_fits") : bute ? T.f("red_teeth_are_blocking") : T.f("slide_the_strip"), tx, oy + 29, emboite ? Dessin.VERT : bute ? Dessin.ALERTE : Dessin.JAUNE);
        boolean libre = libre();
        Dessin.bouton(g, tx, oy + 40, 50, 18, "<", libre && Dessin.dans(mx, my, tx, oy + 40, 50, 18), libre && position < M - N, 0xFF7C8FA3);
        Dessin.bouton(g, tx + 54, oy + 40, 50, 18, ">", libre && Dessin.dans(mx, my, tx + 54, oy + 40, 50, 18), libre && position > 0, 0xFF7C8FA3);
        Dessin.bouton(g, tx, oy + 66, 104, 22, T.f("press"), libre && Dessin.dans(mx, my, tx, oy + 66, 104, 22), libre, Dessin.VERT);
        jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
    }

    @Override
    String consigne() {
        return T.f("slide_the_orange_strip_buttons");
    }
}
