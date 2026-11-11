package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Microscope : faire la mise au point, puis désigner la cellule anormale
final class JeuMicroscope extends Jeu {
    static final int N = 10;
    final Random h;
    final int[] px = new int[N], py = new int[N];
    int mise, cible, anormale, manche, fautes, temps, duree, flash, pause;
    boolean net;

    JeuMicroscope(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        cible = 15 + h.nextInt(70);
        int ecart = 18 + h.nextInt(20);
        mise = cible + (cible > 50 ? -ecart : ecart);
        anormale = h.nextInt(N);
        for (int i = 0; i < N; i++) {
            for (int essai = 0; essai < 80; essai++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 40;
                px[i] = (int) (80 + Math.cos(a) * r);
                py[i] = (int) (56 + Math.sin(a) * r);
                boolean libre = true;
                for (int k = 0; k < i; k++) libre &= Math.hypot(px[k] - px[i], py[k] - py[i]) > 19;
                if (libre) break;
            }
        }
        duree = temps = 300 - manche * 40;
        net = false;
    }

    int flou() {
        return Math.abs(mise - cible);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (--temps <= 0) faute();
        else presser(temps, duree);
    }

    void faute() {
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 2) terminer(false);
        else pause = 24;
    }

    int bx(int i) {
        return ox + 172 + (i % 2) * 52;
    }

    int by(int i) {
        return oy + 44 + (i / 2) * 22;
    }

    @Override
    void clic(double mx, double my) {
        if (pause > 0) return;
        int[] pas = {-5, 5, -1, 1};
        for (int i = 0; i < 4; i++) {
            if (!Dessin.dans(mx, my, bx(i), by(i), 48, 18)) continue;
            mise = Math.max(0, Math.min(100, mise + pas[i]));
            Dessin.bip(Sons.MOLETTE, 0.8f + (float) Math.max(0, 30 - flou()) / 30f, 0.6f);
            if (flou() <= 2 && !net) {
                net = true;
                Dessin.note(1.6f);
            } else if (flou() > 2) {
                net = false;
            }
            return;
        }
        if (flou() > 2) return;
        int ax = ox + 4, ay = oy + 3;
        for (int i = 0; i < N; i++) {
            if (Math.hypot(mx - ax - px[i], my - ay - py[i]) > 8) continue;
            if (i == anormale) {
                Dessin.note(1f + manche * 0.25f);
                if (++manche >= 3) {
                    parfait = fautes == 0;
                    terminer(true);
                } else {
                    pause = 24;
                }
            } else {
                faute();
            }
            return;
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3, f = flou();
        cadre(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF0A0E12);
        disque(g, ax + 80, ay + 56, 54, 0xFF1B3A40);
        disque(g, ax + 80, ay + 56, 52, 0xFFCFE8DC);
        for (int i = 0; i < N; i++) {
            int x = ax + px[i], y = ay + py[i];
            if (f > 2) {
                // Flou : la cellule n'est qu'une tache pâle, d'autant plus large que la mise au point est loin.
                disque(g, x, y, 7 + Math.min(6, f / 4), 0x3060A080);
                disque(g, x, y, 5, 0x4060A080);
            } else {
                disque(g, x, y, 7, 0xFF3F8F70);
                disque(g, x, y, 6, 0xFF9FD8B8);
                if (i == anormale) {
                    g.fill(x - 4, y - 2, x - 1, y + 1, 0xFF2A5C48);
                    g.fill(x + 1, y, x + 4, y + 3, 0xFF2A5C48);
                } else {
                    g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF2A5C48);
                }
            }
        }
        Dessin.cercle(g, ax + 80, ay + 56, 53, 2, 0xFF0A0E12);
        int tx = ox + 172;
        texte(g, T.f("slide") + Math.min(3, manche + 1) + " / 3", tx, oy + 6, Dessin.TEXTE);
        texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 90, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("sharpness"), tx, oy + 20, f <= 2 ? Dessin.VERT : Dessin.JAUNE);
        jauge(g, tx, oy + 31, 100, 1 - f / 40.0, f <= 2 ? Dessin.VERT : Dessin.JAUNE);
        String[] noms = {"<<", ">>", "<", ">"};
        for (int i = 0; i < 4; i++) Dessin.bouton(g, bx(i), by(i), 48, 18, noms[i], pause == 0 && Dessin.dans(mx, my, bx(i), by(i), 48, 18), pause == 0 && !fini, 0xFF7C8FA3);
        jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
    }

    @Override
    String consigne() {
        return T.f("focus_with_the_knobs_coarse");
    }
}
