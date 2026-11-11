package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Mesure de l'empreinte : poser les quatre mâchoires du pied à coulisse
final class JeuMesure extends Jeu {
    static final ResourceLocation EMPREINTE = Dessin.texture("empreinte");
    static final int EL = 74, EH = 70;
    static final String[] BORDS = {T.f("left_edge"), T.f("right_edge"), T.f("top_edge"), T.f("bottom_edge")};
    final int ex, ey;
    final double[] poses = new double[4];
    double pos, sens = 1;
    int etape, fautes, flash;
    boolean flashOk;

    JeuMesure(Random h) {
        ex = 22 + h.nextInt(28);
        ey = 14 + h.nextInt(20);
        pos = h.nextInt(100);
    }

    double vitesse() {
        return 2.2 + etape * 0.35;
    }

    int longueur() {
        return etape < 2 ? 128 : 106;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        pos += sens * vitesse();
        if (pos >= longueur()) {
            pos = longueur();
            sens = -1;
            Dessin.bip(Sons.TIC, 1.1f, 0.45f);
        } else if (pos <= 0) {
            pos = 0;
            sens = 1;
            Dessin.bip(Sons.TIC, 0.9f, 0.45f);
        }
    }

    double cible() {
        return switch (etape) {
            case 0 -> ex;
            case 1 -> ex + EL;
            case 2 -> ey;
            default -> ey + EH;
        };
    }

    @Override
    void clic(double mx, double my) {
        // Position de la règle dans le repère de la zone (8 px de marge à gauche, 6 en haut).
        double ici = pos + (etape < 2 ? 8 : 6);
        flashOk = Math.abs(ici - cible()) <= 4.5;
        flash = 8;
        poses[etape] = ici;
        if (flashOk) Dessin.note(1f + etape * 0.2f);
        else {
            Dessin.note(0.5f);
            fautes++;
        }
        etape++;
        pos = etape == 2 ? 0 : pos;
        if (fautes >= 2) terminer(false);
        else if (etape >= 4) {
            parfait = fautes == 0;
            terminer(true);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        g.fill(ox + 6, oy + 4, ox + 140, oy + 114, Dessin.BORD);
        g.fill(ox + 7, oy + 5, ox + 139, oy + 113, 0xFF4A3828);
        g.fill(ox + 7, oy + 5, ox + 139, oy + 6, 0xFF5E4834);
        Dessin.image(g, EMPREINTE, ox + ex, oy + ey, EL, EH);
        for (int i = 0; i < Math.min(etape, 4); i++) regle(g, i, poses[i], Dessin.JAUNE);
        if (!fini) {
            double p = Math.max(0, Math.min(longueur(), pos + sens * vitesse() * pt)) + (etape < 2 ? 8 : 6);
            regle(g, etape, p, flash > 0 ? (flashOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.TEXTE);
        }
        int px = ox + 150;
        Dessin.ecrire(g, T.f("jaw") + Math.min(4, etape + 1) + " / 4", px, oy + 12, Dessin.TEXTE, false);
        if (etape < 4) Dessin.ecrire(g, BORDS[etape], px, oy + 24, Dessin.JAUNE, false);
        Dessin.ecrire(g, T.f("faults") + fautes + " / 2", px, oy + 40, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
        if (etape >= 2) Dessin.ecrire(g, T.f("width") + Math.round((poses[1] - poses[0]) / 2) + T.f("cm"), px, oy + 58, Dessin.ACCENT, false);
        if (etape >= 4) Dessin.ecrire(g, T.f("length") + Math.round((poses[3] - poses[2]) / 2) + T.f("cm"), px, oy + 70, Dessin.ACCENT, false);
        Dessin.paragraphe(g, T.f("click_when_the_ruler_touches"), px, oy + 88, 124, Dessin.SOMBRE);
    }

    void regle(GuiGraphics g, int i, double p, int couleur) {
        int q = (int) Math.round(p);
        if (i < 2) g.fill(ox + q, oy + 6, ox + q + 1, oy + 112, couleur);
        else g.fill(ox + 8, oy + q, ox + 138, oy + q + 1, couleur);
    }

    @Override
    String consigne() {
        return T.f("click_when_the_ruler_touches_2");
    }
}
