package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

// Filtrage du cri (enquête dans la brume) : suivre la fréquence où le cri ressort du bruit
final class JeuFiltrage extends Jeu {
    static final int DUREE = 320, REQUIS = 70;
    final double p;
    final Random bruit = new Random(5);
    int temps = DUREE, tic;
    double acquis, clarte, cx;

    JeuFiltrage(Random h) {
        p = h.nextDouble() * 6;
    }

    double cible() {
        return 0.5 + 0.33 * Math.sin(tic * 0.019 + p) + 0.07 * Math.sin(tic * 0.083);
    }

    @Override
    void tick() {
        if (--temps <= 0) {
            terminer(false);
            return;
        }
        presser(temps, DUREE);
        tic++;
        double reglage = Math.max(0, Math.min(1, (cx - ox - 4) / L));
        clarte = Math.max(0, 1 - Math.abs(reglage - cible()) * 6);
        acquis = clarte > 0.8 ? acquis + 1 : Math.max(0, acquis - 0.3);
        // Le signal sonne d'autant plus aigu et fort que le cri est net.
        if (tic % 5 == 0) Dessin.bip(Sons.SCAN, (float) (0.5 + clarte * 1.4), (float) (0.2 + clarte * 0.4));
        if (acquis >= REQUIS) {
            parfait = true;
            terminer(true);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cx = mx;
        cadre(g, bx, by, false);
        g.fill(bx, by, bx + L, by + H, 0xFF0A1418);
        for (int k = 1; k < 4; k++) g.fill(bx, by + k * 24, bx + L, by + k * 24 + 1, 0xFF12303A);
        // L'onde : un cri régulier quand le filtre est bien réglé, du bruit sinon.
        bruit.setSeed(tic / 2);
        int avant = by + 48;
        for (int x = 0; x < L; x += 2) {
            double enveloppe = Math.sin(Math.PI * x / L);
            int y = by + 48 + (int) (Math.sin(x * 0.22 + tic * 0.35) * 26 * clarte * enveloppe + (bruit.nextDouble() * 2 - 1) * 26 * (1 - clarte));
            g.fill(bx + x, Math.min(avant, y), bx + x + 2, Math.max(avant, y) + 2, melange(0xFF5D6E80, 0xFF6CF0A0, clarte));
            avant = y;
        }
        // Le curseur du filtre, en bas.
        g.fill(bx, by + 100, bx + L, by + 104, 0xFF12303A);
        int rx = Math.max(bx + 2, Math.min(bx + L - 2, mx));
        g.fill(rx - 2, by + 96, rx + 2, by + 108, clarte > 0.8 ? Dessin.VERT : Dessin.JAUNE);
        int tx = ox + 172;
        texte(g, T.f("clarity"), tx, oy + 10, clarte > 0.8 ? Dessin.VERT : Dessin.JAUNE);
        jauge(g, tx, oy + 21, 100, clarte, clarte > 0.8 ? Dessin.VERT : Dessin.JAUNE);
        texte(g, T.f("cry_isolated_2"), tx, oy + 36, Dessin.TEXTE);
        jauge(g, tx, oy + 47, 100, acquis / REQUIS, Dessin.ACCENT);
        Dessin.paragraphe(g, T.f("trust_the_sound_the_higher"), tx, oy + 60, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("move_the_mouse_left_and");
    }
}
