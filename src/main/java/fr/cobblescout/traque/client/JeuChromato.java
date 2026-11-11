package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Chromatographie : arrêter chaque bandelette quand ses trois bandes sont bien séparées
final class JeuChromato extends Jeu {
    static final int N = 3, HAUT = 96, ECART = 13;
    static final int[][] TEINTES = {{0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9}, {0xFF9B5FC0, 0xFF5DBB5D, 0xFFE9A15B}, {0xFF4FC1C5, 0xFFEE6A9A, 0xFFB8B8A0}};
    final double[] front = new double[N], vitesse = new double[N];
    final double[][] rapport = new double[N][3];
    // 0 = en cours, 1 = arrêtée à temps, 2 = ratée.
    final int[] etat = new int[N];
    int fautes, attente = 24, tic, flash;

    JeuChromato(Random h) {
        for (int i = 0; i < N; i++) {
            vitesse[i] = 0.22 + h.nextDouble() * 0.2;
            double e1 = 0.15 + h.nextDouble() * 0.1, e2 = 0.15 + h.nextDouble() * 0.1;
            rapport[i][0] = 0.3 + h.nextDouble() * 0.08;
            rapport[i][1] = rapport[i][0] + e1;
            rapport[i][2] = rapport[i][1] + e2;
        }
    }

    boolean separe(int i) {
        return (rapport[i][1] - rapport[i][0]) * front[i] >= ECART && (rapport[i][2] - rapport[i][1]) * front[i] >= ECART;
    }

    void rate(int i) {
        etat[i] = 2;
        flash = 8;
        fautes++;
        Dessin.note(0.5f);
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (attente > 0) {
            if (--attente == 0) Dessin.bip(Sons.GOUTTE, 1.0f, 0.7f);
            return;
        }
        tic++;
        boolean encore = false;
        double max = 0;
        for (int i = 0; i < N; i++) {
            if (etat[i] != 0) continue;
            front[i] += vitesse[i];
            max = Math.max(max, front[i]);
            if (front[i] >= HAUT) rate(i); // le solvant a atteint le haut : la bandelette est perdue
            else encore = true;
        }
        if (encore && tic % 8 == 0) Dessin.bip(Sons.TIC, (float) (0.7 + max / HAUT), 0.35f);
        if (!encore && !fini) {
            parfait = fautes == 0;
            terminer(fautes <= 1);
        }
    }

    int bx(int i) {
        return ox + 4 + 18 + i * 48;
    }

    @Override
    void clic(double mx, double my) {
        if (attente > 0) return;
        for (int i = 0; i < N; i++) {
            if (etat[i] != 0 || !Dessin.dans(mx, my, bx(i), oy + 3 + 8, 30, HAUT)) continue;
            if (separe(i)) {
                etat[i] = 1;
                Dessin.note(1.2f + i * 0.15f);
            } else {
                rate(i); // arrêtée trop tôt : les bandes se chevauchent encore
            }
            return;
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        cadre(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        for (int i = 0; i < N; i++) {
            int x = bx(i), haut = ay + 8, bas = haut + HAUT, f = (int) front[i];
            boolean survol = etat[i] == 0 && attente == 0 && Dessin.dans(mx, my, x, haut, 30, HAUT);
            g.fill(x - 1, haut - 1, x + 31, bas + 1, etat[i] == 1 ? Dessin.VERT : etat[i] == 2 ? Dessin.ALERTE : survol ? Dessin.JAUNE : Dessin.BORD);
            g.fill(x, haut, x + 30, bas, 0xFFF1EBDD);
            g.fill(x, bas - f, x + 30, bas, 0xFFD8E2E8); // papier mouillé par le solvant
            g.fill(x, bas - f, x + 30, bas - f + 1, 0xFF8FB0C8);
            for (int k = 0; k < 3; k++) {
                int y = bas - (int) (rapport[i][k] * front[i]);
                g.fill(x + 3, y - 3, x + 27, y + 3, TEINTES[i][k]);
            }
            g.fill(x, bas - 2, x + 30, bas - 1, 0xFF7D6248);
            // Repère d'écart minimal, à côté de la bandelette.
            g.fill(x + 33, haut + 2, x + 35, haut + 2 + ECART, Dessin.GRIS);
            g.fill(x + 32, haut + 2, x + 36, haut + 3, Dessin.GRIS);
            g.fill(x + 32, haut + 1 + ECART, x + 36, haut + 2 + ECART, Dessin.GRIS);
        }
        int tx = ox + 172, ok = 0;
        for (int e : etat) ok += e == 1 ? 1 : 0;
        texte(g, T.f("strips") + ok + " / " + N, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("failed_2") + fautes + " / 2", tx, oy + 22, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
        Dessin.paragraphe(g, T.f("the_small_grey_mark_shows"), tx, oy + 42, 104, Dessin.SOMBRE);
    }

    @Override
    String consigne() {
        return T.f("click_a_strip_to_stop");
    }
}
