package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Apnée (enquête aquatique) : remplir la fiole sur la trace que le courant déplace, avant de manquer d'air
final class JeuApnee extends Jeu {
    static final ResourceLocation FOND = Dessin.texture("fond_eau");
    final double p, q;
    double air = 100, fiole;
    int tic;
    boolean dessus;
    double cx, cy;

    JeuApnee(Random h) {
        p = h.nextDouble() * 6;
        q = h.nextDouble() * 6;
    }

    double tx() {
        double v = 1 + fiole / 120;
        return 80 + 52 * Math.sin(tic * 0.03 * v + p) + 8 * Math.sin(tic * 0.11);
    }

    double ty() {
        double v = 1 + fiole / 120;
        return 62 + 30 * Math.sin(tic * 0.047 * v + q);
    }

    @Override
    void tick() {
        tic++;
        air -= 0.22;
        dessus = appui && Math.hypot(cx - ox - 4 - tx(), cy - oy - 3 - ty()) < 12;
        if (dessus) {
            fiole += 1;
            if (tic % 12 == 0) Dessin.bip(Sons.FIOLE, (float) (0.8 + fiole / 120), 0.6f);
        } else {
            fiole = Math.max(0, fiole - 0.15);
        }
        if (tic % 14 == 0) Dessin.bip(Sons.BULLE, 0.8f + (tic % 5) * 0.1f, 0.35f);
        // Le cœur s'emballe quand l'air vient à manquer.
        if (air < 35 && tic % (air < 15 ? 8 : 14) == 0) Dessin.bip(Sons.COEUR, 1.2f, 0.9f);
        if (fiole >= 100) {
            parfait = air >= 40;
            terminer(true);
        } else if (air <= 0) {
            terminer(false);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cy = my;
        cadre(g, bx, by, air < 15 && (tic / 4) % 2 == 0);
        Dessin.image(g, FOND, bx, by, L, H);
        for (int i = 0; i < 9; i++) {
            int x = bx + 8 + (i * 37) % 146, y = by + H - 4 - (tic * (1 + i % 3) + i * 29) % (H - 8);
            g.fill(x, y, x + 2, y + 2, 0x90DDF4FF);
        }
        int x = bx + (int) tx(), y = by + (int) ty();
        Dessin.cercle(g, x, y, 12, 1, dessus ? Dessin.VERT : 0xC0FFFFFF);
        disque(g, x, y, 4, dessus ? 0xFFB8FFD0 : 0xFF7FE0D0);
        if (!fini && Dessin.dans(mx, my, bx, by, L, H)) Dessin.cercle(g, mx, my, 5, 2, appui ? Dessin.JAUNE : Dessin.TEXTE);
        int tx = ox + 172;
        texte(g, T.f("air"), tx, oy + 10, air < 35 ? Dessin.ALERTE : 0xFF8FC8F0);
        jauge(g, tx, oy + 21, 100, air / 100, air < 35 ? Dessin.ALERTE : 0xFF5BB8F0);
        texte(g, T.f("vial"), tx, oy + 36, Dessin.JAUNE);
        jauge(g, tx, oy + 47, 100, fiole / 100, Dessin.VERT);
        Dessin.paragraphe(g, T.f("stay_on_the_trace_40"), tx, oy + 60, 104, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_on_the_2");
    }
}
