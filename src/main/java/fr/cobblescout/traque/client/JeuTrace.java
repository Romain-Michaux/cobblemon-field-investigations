package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Relevé des griffures : suivre chaque entaille sans en sortir
final class JeuTrace extends Jeu {
    static final ResourceLocation ECORCE = Dessin.texture("ecorce");
    static final int N = 44;
    final double[][] px = new double[3][N], py = new double[3][N];
    int ligne, avancee, fautes, flash;
    boolean enCours, attendRelache;

    JeuTrace(Random h) {
        for (int k = 0; k < 3; k++) {
            double x0 = 26 + k * 40 + h.nextInt(8), penche = 22 + h.nextInt(14), courbe = 3 + h.nextDouble() * 4, dephasage = h.nextDouble() * 6.28;
            for (int i = 0; i < N; i++) {
                double t = i / (double) (N - 1);
                px[k][i] = x0 + t * penche + Math.sin(t * Math.PI * 2 + dephasage) * courbe;
                py[k][i] = 14 + t * 88;
            }
        }
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
    }

    void faute() {
        enCours = false;
        attendRelache = true;
        avancee = 0;
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 3) terminer(false);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        if (!fini) suivre(mx - bx, my - by);
        g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
        Dessin.image(g, ECORCE, bx, by, 160, 112);
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < N - 1; i++) {
                Dessin.segment(g, bx + px[k][i] - 2, by + py[k][i] - 1, bx + px[k][i + 1] - 2, by + py[k][i + 1] - 1, 5, 0xFF1B120C);
            }
            for (int i = 0; i < N - 1; i++) {
                boolean fait = k < ligne || (k == ligne && i < avancee);
                Dessin.segment(g, bx + px[k][i] - 1, by + py[k][i] - 1, bx + px[k][i + 1] - 1, by + py[k][i + 1] - 1, 3, fait ? Dessin.JAUNE : 0xFFE3C993);
            }
        }
        if (ligne < 3 && !enCours) {
            boolean pulse = (System.currentTimeMillis() / 300) % 2 == 0;
            g.fill(bx + (int) px[ligne][0] - 4, by + (int) py[ligne][0] - 4, bx + (int) px[ligne][0] + 4, by + (int) py[ligne][0] + 4, Dessin.BORD);
            g.fill(bx + (int) px[ligne][0] - 3, by + (int) py[ligne][0] - 3, bx + (int) px[ligne][0] + 3, by + (int) py[ligne][0] + 3, pulse ? Dessin.ACCENT : Dessin.TEXTE);
        }
        int tx = ox + 172;
        Dessin.ecrire(g, T.f("scratch") + Math.min(3, ligne + 1) + " / 3", tx, oy + 12, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("slips") + fautes + " / 3", tx, oy + 24, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
        Dessin.ecrire(g, enCours ? T.f("follow_the_groove") : attendRelache ? T.f("release_and_retry") : T.f("start_from_the_dot"), tx, oy + 44,
                enCours ? Dessin.ACCENT : Dessin.JAUNE, false);
        Dessin.paragraphe(g, T.f("hold_the_click_from_top"), tx, oy + 64, 104, Dessin.SOMBRE);
    }

    void suivre(double mx, double my) {
        if (!appui) {
            enCours = false;
            attendRelache = false;
            avancee = 0;
            return;
        }
        if (attendRelache || ligne >= 3) return;
        if (!enCours) {
            if (Math.hypot(mx - px[ligne][0], my - py[ligne][0]) < 8) {
                enCours = true;
                avancee = 0;
                Dessin.son(1.3f);
            }
            return;
        }
        while (avancee < N - 1 && Math.hypot(mx - px[ligne][avancee + 1], my - py[ligne][avancee + 1]) < 8) {
            if (++avancee % 5 == 0) Dessin.bip(Sons.FROTTEMENT, 1.1f + avancee * 0.012f, 0.5f);
        }
        if (avancee >= N - 1) {
            Dessin.note(1f + ligne * 0.25f);
            enCours = false;
            attendRelache = true;
            avancee = 0;
            if (++ligne >= 3) {
                parfait = fautes == 0;
                terminer(true);
            }
        } else if (Math.hypot(mx - px[ligne][avancee], my - py[ligne][avancee]) > 13) {
            faute();
        }
    }

    @Override
    String consigne() {
        return T.f("hold_the_click_on_the");
    }
}
