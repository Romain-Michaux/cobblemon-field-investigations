package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Tamisage (indice enfoui) : secouer par à-coups pour faire tomber le sable sans perdre les fragments
final class JeuTamis extends Jeu {
    static final ResourceLocation TAMIS = Dessin.texture("tamis");
    static final int DUREE = 620, GRAINS = 110;
    final ResourceLocation indice;
    final int[] gx = new int[GRAINS], gy = new int[GRAINS], fx = new int[3], fy = new int[3];
    double sable = 100, secousse;
    int temps = DUREE, fautes, flash, tic, visibles;

    JeuTamis(Random h, String genre, int variante) {
        indice = Dessin.iconeEnfoui(genre, variante);
        for (int i = 0; i < GRAINS; i++) {
            double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 31;
            gx[i] = (int) (48 + Math.cos(a) * r);
            gy[i] = (int) (48 + Math.sin(a) * r);
        }
        for (int i = 0; i < 3; i++) {
            double a = h.nextDouble() * Math.PI * 2 + i * 2.1, r = 8 + h.nextDouble() * 12;
            fx[i] = (int) (40 + Math.cos(a) * r);
            fy[i] = (int) (40 + Math.sin(a) * r);
        }
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
        if (appui) {
            sable -= 0.55;
            secousse += 2.2;
            if (tic % 4 == 0) Dessin.bip(Sons.TAMIS, (float) (0.8 + secousse / 160), 0.5f);
        } else {
            secousse = Math.max(0, secousse - 3);
        }
        if (secousse >= 100) {
            // Secoué trop longtemps d'affilée : un fragment passe à travers les mailles.
            secousse = 35;
            flash = 8;
            Dessin.bip(Sons.CRAQUEMENT, 1.2f, 0.7f);
            if (++fautes >= 3) terminer(false);
        }
        int v = sable <= 0 ? 3 : sable < 34 ? 2 : sable < 67 ? 1 : 0;
        if (v > visibles) {
            visibles = v;
            Dessin.note(1f + v * 0.2f);
        }
        if (sable <= 0 && !fini) {
            parfait = fautes == 0;
            terminer(true);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF2A2019);
        int dx = appui && !fini ? (tic % 2) * 4 - 2 : 0, sx = bx + 32 + dx, sy = by + 8;
        Dessin.image(g, TAMIS, sx, sy, 96, 96);
        for (int i = 0; i < 3; i++) {
            if (i < visibles) Dessin.image(g, indice, sx + fx[i], sy + fy[i], 16, 16);
        }
        int n = (int) (Math.max(0, sable) / 100 * GRAINS);
        for (int i = 0; i < n; i++) g.fill(sx + gx[i], sy + gy[i], sx + gx[i] + 3, sy + gy[i] + 3, i % 3 == 0 ? 0xFFB98F4E : 0xFFD9B36A);
        if (appui && !fini) {
            for (int i = 0; i < 6; i++) g.fill(sx + 20 + (tic * 7 + i * 13) % 56, by + 100 + (tic + i * 5) % 10, sx + 22 + (tic * 7 + i * 13) % 56, by + 102 + (tic + i * 5) % 10, 0xFFD9B36A);
        }
        int tx = ox + 172;
        texte(g, T.f("sand") + (int) Math.ceil(Math.max(0, sable)) + " %", tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("lost") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("shaking"), tx, oy + 40, secousse > 70 ? Dessin.ALERTE : Dessin.JAUNE);
        jauge(g, tx, oy + 51, 100, secousse / 100, secousse > 70 ? Dessin.ALERTE : Dessin.JAUNE);
        Dessin.paragraphe(g, T.f("let_go_before_the_gauge"), tx, oy + 64, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_to_shake");
    }
}
