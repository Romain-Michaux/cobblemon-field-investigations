package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Écouvillon : frotter toute la tache, sans repasser sur une zone déjà prélevée
final class JeuEcouvillon extends Jeu {
    static final ResourceLocation ECORCE = Dessin.texture("ecorce"), COTON = Dessin.texture("ecouvillon");
    static final int COLS = 20, RANGS = 14, CASE = 8, DUREE = 600;
    final boolean[] tache = new boolean[COLS * RANGS];
    final int[] propre = new int[COLS * RANGS];
    int temps = DUREE, fautes, flash, tic, total, faits, derniere = -1;
    double cx, cy;

    JeuEcouvillon(Random h) {
        for (int k = 0; k < 3; k++) {
            double ccx = 5 + h.nextInt(10), ccy = 4 + h.nextInt(6), r = 2.6 + h.nextDouble() * 1.4;
            for (int i = 0; i < tache.length; i++) {
                if (Math.hypot(i % COLS - ccx, i / COLS - ccy) <= r) tache[i] = true;
            }
        }
        for (boolean t : tache) total += t ? 1 : 0;
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
        int c = (int) Math.floor((cx - ox - 4) / CASE), r = (int) Math.floor((cy - oy - 3) / CASE);
        int i = c >= 0 && c < COLS && r >= 0 && r < RANGS ? r * COLS + c : -1;
        if (!appui || i < 0 || !tache[i]) {
            derniere = -1;
            return;
        }
        if (propre[i] == 0) {
            propre[i] = tic;
            if (++faits % 2 == 0) Dessin.bip(Sons.FROTTEMENT, 1.3f + faits * 0.6f / total, 0.6f);
            if (faits >= total) {
                parfait = fautes == 0;
                Dessin.note(1.5f);
                terminer(true);
            }
        } else if (i != derniere && tic - propre[i] > 14) {
            // Repasser sur une zone déjà frottée contamine l'écouvillon.
            flash = 8;
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
        }
        if (propre[i] != 0) propre[i] = tic;
        derniere = i;
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        cadre(g, bx, by, flash > 0);
        Dessin.image(g, ECORCE, bx, by, L, H);
        for (int i = 0; i < tache.length; i++) {
            if (!tache[i]) continue;
            int x = bx + (i % COLS) * CASE, y = by + (i / COLS) * CASE;
            if (propre[i] == 0) {
                g.fill(x, y, x + CASE, y + CASE, 0xD07A3FA8);
                g.fill(x + 1, y + 1, x + 4, y + 3, 0x60E0B0FF);
            } else {
                g.fill(x, y, x + CASE, y + CASE, 0x50F4F0E0);
            }
        }
        if (!fini && Dessin.dans(mx, my, bx, by, L, H)) Dessin.image(g, COTON, mx - 16, my - 62, 32, 64);
        int tx = ox + 172;
        texte(g, T.f("swabbed") + (faits * 100 / Math.max(1, total)) + " %", tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("soiled") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("you_may_lift_the_swab"), tx, oy + 42, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_and_rub");
    }
}
