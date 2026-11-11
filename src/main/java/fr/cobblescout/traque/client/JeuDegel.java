package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Dégel (enquête givre) : chauffer l'échantillon gelé en restant dans la bonne plage de température
final class JeuDegel extends Jeu {
    static final ResourceLocation GLACON = Dessin.texture("glacon");
    static final int DUREE = 580;
    final double p;
    double temperature = 20, chauffe, degel;
    int temps = DUREE, fautes, flash, tic, paliers;

    JeuDegel(Random h) {
        p = h.nextDouble() * 6;
    }

    double bas() {
        return 44 + 13 * Math.sin(tic * 0.022 + p);
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
            chauffe = Math.min(30, chauffe + 1);
            temperature += 0.5 + chauffe * 0.05;
            if (tic % 9 == 0) Dessin.bip(Sons.FLAMME, 1.4f, 0.5f);
        } else {
            chauffe = 0;
            temperature = Math.max(0, temperature - 0.8);
        }
        if (temperature >= 90) {
            temperature = 35;
            chauffe = 0;
            flash = 8;
            Dessin.bip(Sons.SIFFLEMENT, 1.2f, 0.6f);
            if (++fautes >= 3) terminer(false);
        }
        if (temperature >= bas() && temperature <= bas() + 18) degel += 0.55;
        int palier = (int) (degel / 34);
        if (palier > paliers) {
            paliers = palier;
            Dessin.bip(Sons.GLACE, 1.2f, 0.4f);
        }
        if (degel >= 100 && !fini) {
            parfait = fautes == 0;
            terminer(true);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
        int taille = (int) (64 - degel * 0.34), gx = bx + 52 - taille / 2, gy = by + 82 - taille;
        g.fill(bx + 52 - 20 - (int) (degel * 0.14), by + 82, bx + 52 + 20 + (int) (degel * 0.14), by + 85, 0xFF5BB8F0);
        Dessin.morceauAgrandi(g, GLACON, gx, gy, taille, taille, 0, 0, 48, 48, 48, 48);
        if (appui && !fini) {
            for (int i = 0; i < 5; i++) {
                int fx = bx + 34 + i * 9, fh = 4 + (tic * 3 + i * 5) % 7;
                g.fill(fx, by + 100 - fh, fx + 5, by + 100, i % 2 == 0 ? 0xFFF2C93A : 0xFFE8543A);
            }
        }
        g.fill(bx + 26, by + 100, bx + 80, by + 104, 0xFF46525E);
        // Le thermomètre : la plage verte se déplace lentement, la zone rouge fait cuire l'échantillon.
        int tx0 = bx + 118, haut = by + 6, h = 100;
        g.fill(tx0 - 1, haut - 1, tx0 + 15, haut + h + 1, Dessin.BORD);
        g.fill(tx0, haut, tx0 + 14, haut + h, 0xFF0F1820);
        g.fill(tx0, haut, tx0 + 14, haut + 10, 0xFF6B2420);
        int yb = haut + h - (int) bas(), yh = yb - 18;
        g.fill(tx0, yh, tx0 + 14, yb, 0xFF245C3A);
        int yt = haut + h - (int) temperature;
        g.fill(tx0 + 4, yt, tx0 + 10, haut + h, temperature >= 80 ? Dessin.ALERTE : 0xFFE9A15B);
        g.fill(tx0 - 3, yt - 1, tx0 + 17, yt + 1, Dessin.TEXTE);
        int tx = ox + 172;
        texte(g, T.f("thawed") + (int) degel + " %", tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("overheats") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("the_ice_only_melts_in"), tx, oy + 42, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_to_heat");
    }
}
