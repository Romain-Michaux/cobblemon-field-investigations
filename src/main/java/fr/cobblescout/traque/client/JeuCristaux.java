package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Cristaux résiduels (résidu élémentaire) : mémoriser le gisement, prélever les stables, éviter les instables
final class JeuCristaux extends Jeu {
    static final ResourceLocation TERRE = Dessin.texture("terre"), CRISTAL = Dessin.texture("cristal");
    static final int COLS = 7, RANGS = 5, CASE = 20, TERNE = 0xFF566270, STABLE = 0xFF6FF0E0, INSTABLE = 0xFFF0604E;
    final Random h;
    final int[] sorte = new int[COLS * RANGS]; // 0 inerte, 1 stable (à prélever), 2 instable (à éviter)
    final int[] etat = new int[COLS * RANGS];  // 0 intact, 1 prélevé, 2 touché à tort
    int manche, montre, duree, restants, fautes, pause, flash, ticks;

    JeuCristaux(Random h) {
        this.h = h;
        nouveau();
    }

    void nouveau() {
        sonMontre();
        java.util.Arrays.fill(sorte, 0);
        java.util.Arrays.fill(etat, 0);
        restants = 5 + manche;
        for (int k = 0; k < restants + 1 + manche; ) {
            int i = h.nextInt(sorte.length);
            if (sorte[i] != 0) continue;
            sorte[i] = k < restants ? 1 : 2;
            k++;
        }
        duree = montre = 58 - manche * 6;
    }

    @Override
    void tick() {
        ticks++;
        if (flash > 0) flash--;
        if (montre > 0 && --montre == 0) sonCache();
        if (pause > 0 && --pause == 0 && !fini) nouveau();
    }

    int cx(int i) {
        return ox + 14 + (i % COLS) * CASE;
    }

    int cy(int i) {
        return oy + 9 + (i / COLS) * CASE;
    }

    @Override
    void clic(double mx, double my) {
        if (montre > 0 || pause > 0) return;
        for (int i = 0; i < sorte.length; i++) {
            if (etat[i] != 0 || !Dessin.dans(mx, my, cx(i), cy(i), CASE, CASE)) continue;
            if (sorte[i] == 1) {
                etat[i] = 1;
                Dessin.son(1.1f + (5 + manche - restants) * 0.08f);
                if (--restants == 0) {
                    Dessin.note(1f + manche * 0.25f);
                    if (++manche >= 3) {
                        parfait = fautes == 0;
                        terminer(true);
                    } else pause = 26;
                }
            } else {
                etat[i] = 2;
                flash = 8;
                Dessin.note(0.5f);
                if (++fautes >= 3) terminer(false);
            }
            return;
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
        Dessin.image(g, TERRE, bx, by, 160, 112);
        boolean revele = montre > 0 || pause > 0 || fini;
        boolean pulse = (ticks / 4) % 2 == 0;
        for (int i = 0; i < sorte.length; i++) {
            int x = cx(i), y = cy(i);
            int teinte = TERNE;
            boolean brille = false;
            if (etat[i] == 1 || (revele && sorte[i] == 1)) {
                teinte = STABLE;
                brille = true;
            } else if ((etat[i] == 2 && sorte[i] == 2) || (revele && sorte[i] == 2)) {
                teinte = INSTABLE;
                brille = true;
            } else if (etat[i] == 2) {
                teinte = 0xFF2E3640; // cristal inerte abîmé
            } else if (montre == 0 && pause == 0 && !fini && Dessin.dans(mx, my, x, y, CASE, CASE)) {
                teinte = 0xFF8FA0B2;
            }
            if (brille) {
                int halo = (teinte & 0xFFFFFF) | (pulse ? 0x50000000 : 0x38000000);
                g.fill(x + 1, y + 3, x + CASE - 1, y + CASE - 3, halo);
                g.fill(x + 3, y + 1, x + CASE - 3, y + CASE - 1, halo);
            }
            Dessin.teinte(g, teinte);
            Dessin.image(g, CRISTAL, x + 2, y + 2, 16, 16);
            g.setColor(1f, 1f, 1f, 1f);
        }
        int tx = ox + 172;
        Dessin.ecrire(g, T.f("deposit") + Math.min(3, manche + 1) + " / 3", tx, oy + 8, Dessin.TEXTE, false);
        Dessin.ecrire(g, montre > 0 ? T.f("watch_closely") : pause > 0 ? T.f("deposit_sampled") : T.f("your_turn_sample"), tx, oy + 20,
                montre > 0 ? Dessin.JAUNE : pause > 0 ? Dessin.VERT : Dessin.ACCENT, false);
        if (montre > 0) jaugeTemps(g, tx, oy + 32, 100, montre / (double) duree);
        g.fill(tx, oy + 46, tx + 8, oy + 54, STABLE);
        Dessin.ecrire(g, T.f("stable") + restants, tx + 12, oy + 46, Dessin.TEXTE, false);
        g.fill(tx, oy + 58, tx + 8, oy + 66, INSTABLE);
        Dessin.ecrire(g, T.f("unstable_avoid"), tx + 12, oy + 58, Dessin.GRIS, false);
        Dessin.ecrire(g, T.f("faults") + fautes + " / 3", tx, oy + 76, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
        Dessin.paragraphe(g, T.f("no_fault_one_extra_dose"), tx, oy + 92, 104, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("stable_teal_and_unstable_red");
    }
}
