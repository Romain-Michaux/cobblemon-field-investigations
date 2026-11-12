package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Arbre de parenté : descendre l'arbre, en prenant à chaque embranchement le caractère que porte l'échantillon
final class JeuParente extends Jeu {
    // Trois caractères, deux formes possibles pour chacun : la peau, le membre, la défense.
    static final ResourceLocation[][] ICONES = {
            {Dessin.texture("enfoui_oeuf_1"), Dessin.texture("enfoui_stat_2")},
            {Dessin.texture("caractere_aile"), Dessin.texture("caractere_nageoire")},
            {Dessin.texture("enfoui_stat_1"), Dessin.texture("enfoui_stat")}};
    static final String[][] NOMS = {{T.f("feathers"), T.f("scales")}, {T.f("wings"), T.f("fins")}, {T.f("claws"), T.f("fangs")}};
    static final int[] YS = {10, 38, 66, 96};
    final Random h;
    // ordre[n] : le caractère testé au niveau n ; gauche[n] : la forme placée sur la branche de gauche.
    final int[] ordre = new int[3], gauche = new int[3], valeurs = new int[3];
    // Position dans l'arbre : niveau atteint (0 = racine, 3 = arrivé) et numéro du nœud à ce niveau.
    int niveau, noeud, manche, fautes, temps, duree, flash, pause;

    JeuParente(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        System.arraycopy(permutations[h.nextInt(6)], 0, ordre, 0, 3);
        for (int l = 0; l < 3; l++) {
            gauche[l] = h.nextInt(2);
            valeurs[l] = h.nextInt(2);
        }
        niveau = 0;
        noeud = 0;
        duree = temps = 240 - manche * 50;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (--temps <= 0) {
            faute();
            if (!fini) pause = 24;
        } else {
            presser(temps, duree);
        }
    }

    void faute() {
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 2) terminer(false);
    }

    double x(int l, int n) {
        return ox + 4 + L * (n + 0.5) / (1 << l);
    }

    // La forme affichée sur la branche de ce côté (0 = gauche, 1 = droite), au niveau donné.
    int forme(int l, int cote) {
        return cote == 0 ? gauche[l] : 1 - gauche[l];
    }

    @Override
    void clic(double mx, double my) {
        if (pause > 0 || niveau >= 3) return;
        for (int cote = 0; cote < 2; cote++) {
            int cx = (int) x(niveau + 1, noeud * 2 + cote), cy = oy + 3 + YS[niveau + 1];
            if (!Dessin.dans(mx, my, cx - 13, cy - 13, 26, 26)) continue;
            if (forme(niveau, cote) == valeurs[ordre[niveau]]) {
                noeud = noeud * 2 + cote;
                niveau++;
                Dessin.note(0.9f + niveau * 0.2f);
                if (niveau >= 3) {
                    Dessin.bip(Sons.CARILLON, 1.5f, 0.6f);
                    if (++manche >= 3) {
                        parfait = fautes == 0;
                        terminer(true);
                    } else {
                        pause = 30;
                    }
                }
            } else {
                faute(); // ce caractère n'est pas celui de l'échantillon
            }
            return;
        }
    }

    static void vignette(GuiGraphics g, int cx, int cy, ResourceLocation icone, int bord) {
        g.fill(cx - 11, cy - 11, cx + 11, cy + 11, bord);
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, 0xFF2B3A4C);
        Dessin.image(g, icone, cx - 8, cy - 8, 16, 16);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        cadre(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF16202B);
        for (int l = 0; l < 3; l++) {
            for (int n = 0; n < (1 << l); n++) {
                for (int cote = 0; cote < 2; cote++) {
                    Dessin.segment(g, x(l, n), ay + YS[l], x(l + 1, n * 2 + cote), ay + YS[l + 1], 1, 0xFF34475C);
                }
            }
        }
        for (int f = 0; f < 8; f++) g.fill((int) x(3, f) - 3, ay + YS[3] - 3, (int) x(3, f) + 3, ay + YS[3] + 3, 0xFF34475C);
        for (int l = 0; l < niveau; l++) {
            int n0 = noeud >> (niveau - l), n1 = noeud >> (niveau - l - 1);
            Dessin.segment(g, x(l, n0), ay + YS[l], x(l + 1, n1), ay + YS[l + 1], 3, Dessin.VERT);
        }
        for (int l = 0; l < niveau; l++) {
            int n1 = noeud >> (niveau - l - 1);
            vignette(g, (int) x(l + 1, n1), ay + YS[l + 1], ICONES[ordre[l]][valeurs[ordre[l]]], Dessin.VERT);
        }
        g.fill((int) x(0, 0) - 4, ay + YS[0] - 4, (int) x(0, 0) + 4, ay + YS[0] + 4, niveau == 0 ? Dessin.JAUNE : Dessin.VERT);
        if (niveau < 3 && !fini) {
            for (int cote = 0; cote < 2; cote++) {
                int cx = (int) x(niveau + 1, noeud * 2 + cote), cy = ay + YS[niveau + 1];
                boolean survol = pause == 0 && Dessin.dans(mx, my, cx - 13, cy - 13, 26, 26);
                Dessin.segment(g, x(niveau, noeud), ay + YS[niveau], cx, cy, 2, Dessin.JAUNE);
                vignette(g, cx, cy, ICONES[ordre[niveau]][forme(niveau, cote)], survol ? Dessin.TEXTE : Dessin.JAUNE);
            }
            // Le nom des deux caractères proposés, sous l'aire de jeu si la place manque sur les branches.
            if (niveau < 2) {
                for (int cote = 0; cote < 2; cote++) {
                    int cx = (int) x(niveau + 1, noeud * 2 + cote), cy = ay + YS[niveau + 1];
                    String nom = NOMS[ordre[niveau]][forme(niveau, cote)];
                    int l = Dessin.police().width(nom), tx = Math.max(ax + 2, Math.min(ax + L - l - 2, cx - l / 2));
                    g.fill(tx - 1, cy + 12, tx + l + 1, cy + 22, 0xC016202B);
                    texte(g, nom, tx, cy + 13, Dessin.TEXTE);
                }
            }
        }
        // À droite : la fiche de l'échantillon, avec ses trois caractères ; celui qu'on cherche est entouré.
        int tx = ox + 172;
        texte(g, T.f("lineage") + Math.min(3, manche + 1) + " / 3", tx, oy + 5, Dessin.TEXTE);
        texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 16, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("the_sample_has"), tx, oy + 30, Dessin.JAUNE);
        for (int c = 0; c < 3; c++) {
            int ly = oy + 42 + c * 20;
            boolean cherche = niveau < 3 && ordre[niveau] == c, fait = false;
            for (int l = 0; l < niveau; l++) fait |= ordre[l] == c;
            vignette(g, tx + 10, ly + 9, ICONES[c][valeurs[c]], cherche ? Dessin.JAUNE : fait ? Dessin.VERT : Dessin.BORD);
            texte(g, NOMS[c][valeurs[c]], tx + 26, ly + 5, cherche ? Dessin.JAUNE : fait ? Dessin.VERT : Dessin.GRIS);
        }
        jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
    }

    @Override
    String consigne() {
        return T.f("go_down_the_tree_at");
    }
}
