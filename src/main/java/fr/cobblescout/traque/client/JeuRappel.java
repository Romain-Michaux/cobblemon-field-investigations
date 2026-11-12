package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Rappel (enquête d'altitude) : laisser filer la corde et s'arrêter à hauteur de chaque trace
final class JeuRappel extends Jeu {
    static final ResourceLocation PAROI = Dessin.texture("paroi");
    static final int DUREE = 540;
    final int[] cibles = new int[3];
    double pos = 6, vitesse, depart = 6;
    int etape, temps = DUREE, fautes, flash, tic;
    boolean attendRelache;

    JeuRappel(Random h) {
        for (int i = 0; i < 3; i++) cibles[i] = 30 + i * 30 + h.nextInt(9);
    }

    void faute() {
        pos = depart;
        vitesse = 0;
        attendRelache = true;
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 3) terminer(false);
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
        if (!appui) attendRelache = false;
        boolean file = appui && !attendRelache;
        vitesse = file ? Math.min(2.4, vitesse + 0.05) : Math.max(0, vitesse - 0.1);
        pos += vitesse;
        if (vitesse > 0 && tic % 4 == 0) Dessin.bip(Sons.CORDE, (float) (0.7 + vitesse * 0.4), 0.6f);
        int cible = cibles[etape];
        if (pos > cible + 4) {
            faute(); // descendu trop bas : il faut remonter et recommencer ce tronçon
        } else if (vitesse == 0 && !file && Math.abs(pos - cible) <= 4) {
            depart = pos;
            Dessin.bip(Sons.MOUSQUETON, 1.2f, 0.8f);
            Dessin.note(1f + etape * 0.25f);
            attendRelache = true;
            if (++etape >= 3) {
                etape = 2;
                parfait = fautes == 0;
                terminer(true);
            }
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        Dessin.image(g, PAROI, bx, by, L, H);
        for (int i = 0; i < 3; i++) {
            int y = by + cibles[i] + 6;
            boolean fait = i < etape || (fini && succes);
            g.fill(bx + 92, y - 4, bx + 150, y + 4, fait ? 0x6040C060 : i == etape ? 0x60F2C93A : 0x30FFFFFF);
            g.fill(bx + 92, y, bx + 150, y + 1, fait ? Dessin.VERT : i == etape ? Dessin.JAUNE : Dessin.GRIS);
            texte(g, fait ? "✔" : String.valueOf(i + 1), bx + 152 - 8, y - 4, fait ? Dessin.VERT : Dessin.TEXTE);
        }
        int gx = bx + 80, gy = by + (int) pos + 6;
        g.fill(gx, by, gx + 1, gy - 6, 0xFFE9D8A8);
        g.fill(gx - 3, gy - 6, gx + 4, gy + 5, 0xFFE8743A);
        g.fill(gx - 2, gy - 10, gx + 3, gy - 6, 0xFFF2D2B0);
        g.fill(gx - 3, gy + 5, gx - 1, gy + 9, 0xFF3A4A5C);
        g.fill(gx + 2, gy + 5, gx + 4, gy + 9, 0xFF3A4A5C);
        int tx = ox + 172;
        texte(g, T.f("traces") + (fini && succes ? 3 : etape) + " / 3", tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("too_low") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("speed"), tx, oy + 40, vitesse > 1.6 ? Dessin.ALERTE : Dessin.JAUNE);
        jauge(g, tx, oy + 51, 100, vitesse / 2.4, vitesse > 1.6 ? Dessin.ALERTE : Dessin.JAUNE);
        Dessin.paragraphe(g, T.f("the_rope_takes_a_while"), tx, oy + 64, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_to_let");
    }
}
