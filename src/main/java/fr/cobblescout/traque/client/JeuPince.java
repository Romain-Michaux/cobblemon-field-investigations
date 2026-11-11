package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Pince à épiler : saisir chaque fibre quand la pince se referme, bien alignée
final class JeuPince extends Jeu {
    static final ResourceLocation NID = Dessin.texture("nid"), PINCE = Dessin.texture("pince");
    static final int N = 5, DUREE = 560;
    final int[] fx = new int[N];
    final boolean[] pris = new boolean[N];
    int temps = DUREE, fautes, flash, tic, restants = N;
    double phase;

    JeuPince(Random h) {
        for (int i = 0; i < N; i++) fx[i] = 16 + i * 28 + h.nextInt(14);
    }

    // 0 = pince fermée, 1 = grande ouverte. Elle bat de plus en plus vite.
    double ouverture() {
        return (Math.sin(phase) + 1) / 2;
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
        double avant = ouverture();
        phase += 0.15 + 0.035 * (N - restants);
        if (avant >= 0.3 && ouverture() < 0.3) Dessin.bip(Sons.PINCE, 1.6f, 0.4f);
    }

    @Override
    void clic(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Dessin.dans(mx, my, bx, by, L, H)) return;
        for (int i = 0; i < N; i++) {
            if (pris[i] || Math.abs(mx - (bx + fx[i])) > 5) continue;
            if (ouverture() < 0.3) {
                pris[i] = true;
                Dessin.bip(Sons.PLUCK, 1.2f + (N - restants) * 0.12f, 0.7f);
                if (--restants == 0) {
                    parfait = fautes == 0;
                    terminer(true);
                }
                return;
            }
        }
        // Pince ouverte ou à côté d'une fibre : elle ripe sur l'écorce.
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 3) terminer(false);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        Dessin.image(g, NID, bx, by, L, H);
        for (int i = 0; i < N; i++) {
            if (pris[i]) continue;
            int x = bx + fx[i];
            // Un brin de duvet planté dans le nid : une tige claire et son plumet.
            g.fill(x - 2, by + 76, x + 3, by + 98, 0xA0140C06);
            g.fill(x, by + 80, x + 1, by + 97, 0xFFFFFFFF);
            g.fill(x - 2, by + 76, x + 3, by + 81, 0xFFF4F0E0);
            g.fill(x - 1, by + 74, x + 2, by + 76, 0xFFFFFFFF);
        }
        int px = Math.max(bx + 16, Math.min(bx + L - 16, mx));
        double o = ouverture();
        if (!fini) {
            Dessin.image(g, PINCE, px - 16, by + 8, 32, 64);
            int e = 2 + (int) Math.round(o * 6);
            int c = o < 0.3 ? Dessin.VERT : 0xFFC8D4E0;
            g.fill(px - e - 1, by + 72, px - e + 1, by + 80, c);
            g.fill(px + e - 1, by + 72, px + e + 1, by + 80, c);
        }
        int tx = ox + 172;
        texte(g, T.f("fibres") + (N - restants) + " / " + N, tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("misses") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, o < 0.3 ? T.f("tweezers_closed") : T.f("tweezers_open"), tx, oy + 42, o < 0.3 ? Dessin.VERT : Dessin.GRIS);
        jauge(g, tx, oy + 53, 100, 1 - o, o < 0.3 ? Dessin.VERT : Dessin.SOMBRE);
        Dessin.paragraphe(g, T.f("the_tips_turn_green_when"), tx, oy + 66, 104, Dessin.SOMBRE);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("line_the_tweezers_up_with");
    }
}
