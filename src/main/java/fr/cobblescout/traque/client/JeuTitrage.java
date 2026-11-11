package fr.cobblescout.traque.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Colorimétrie : mémoriser une teinte, puis la recréer en mélangeant les réactifs
final class JeuTitrage extends Jeu {
    static final ResourceLocation FIOLE = Dessin.texture("erlenmeyer"), LIQUIDE = Dessin.texture("erlenmeyer_liquide");
    static final String[] NOMS = {T.f("red"), T.f("yellow"), T.f("blue"), T.f("white")};
    static final int[] REACTIFS = {0xFFE8433A, 0xFFF2C93A, 0xFF3A78D8, 0xFFF4F4F0};
    static final int VIDE = 0xFFB9C6D2, MAX = 10, SEUIL = 85, MONTRE = 90;
    final Random h;
    final int[] gouttes = new int[4];
    int cible, manche, echecs, visible, pause, chute, dernier = -1, score, total;

    JeuTitrage(Random h) {
        this.h = h;
        nouvelle();
    }

    // La teinte cible est toujours un mélange réalisable : 4 à 7 gouttes de deux ou trois réactifs.
    void nouvelle() {
        sonMontre();
        int[] recette = new int[4];
        List<Integer> ordre = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(ordre, h);
        int sortes = 2 + h.nextInt(2), n = 4 + h.nextInt(4);
        for (int i = 0; i < sortes; i++) recette[ordre.get(i)] = 1;
        for (int i = sortes; i < n; i++) recette[ordre.get(h.nextInt(sortes))]++;
        cible = melange(recette);
        java.util.Arrays.fill(gouttes, 0);
        visible = MONTRE;
        dernier = -1;
    }

    static int melange(int[] quantites) {
        int n = 0, r = 0, v = 0, b = 0;
        for (int i = 0; i < 4; i++) {
            n += quantites[i];
            r += ((REACTIFS[i] >> 16) & 0xFF) * quantites[i];
            v += ((REACTIFS[i] >> 8) & 0xFF) * quantites[i];
            b += (REACTIFS[i] & 0xFF) * quantites[i];
        }
        return n == 0 ? VIDE : 0xFF000000 | ((r / n) << 16) | ((v / n) << 8) | (b / n);
    }

    // Ressemblance en pourcentage : 100 = teinte identique. Réglé pour qu'une teinte visiblement proche passe le seuil :
    // une goutte d'écart coûte 5 à 15 points selon les réactifs, deux gouttes font en général échouer.
    static int ressemblance(int a, int b) {
        double dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dv = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.round(Math.max(0, 1 - Math.sqrt(dr * dr + dv * dv + db * db) / 330) * 100);
    }

    int nombre() {
        return gouttes[0] + gouttes[1] + gouttes[2] + gouttes[3];
    }

    @Override
    void tick() {
        if (chute > 0) chute--;
        if (visible > 0 && --visible == 0) sonCache();
        if (pause > 0 && --pause == 0 && !fini) nouvelle();
    }

    int bx(int i) {
        return ox + 152 + (i % 2) * 62;
    }

    int by(int i) {
        return oy + 34 + (i / 2) * 24;
    }

    @Override
    void clic(double mx, double my) {
        if (visible > 0 || pause > 0) return;
        for (int i = 0; i < 4; i++) {
            if (!Dessin.dans(mx, my, bx(i), by(i), 58, 20) || nombre() >= MAX) continue;
            gouttes[i]++;
            dernier = i;
            chute = 4;
            Dessin.son(0.9f + i * 0.12f);
            return;
        }
        if (Dessin.dans(mx, my, ox + 152, oy + 88, 58, 22) && nombre() > 0) {
            java.util.Arrays.fill(gouttes, 0);
            Dessin.son(0.7f);
        } else if (Dessin.dans(mx, my, ox + 214, oy + 88, 58, 22) && nombre() > 0) {
            score = ressemblance(melange(gouttes), cible);
            total += score;
            pause = 44;
            if (score >= SEUIL) {
                Dessin.note(1f + manche * 0.25f);
            } else {
                Dessin.note(0.5f);
                echecs++;
            }
            manche++;
            if (echecs >= 2) terminer(false);
            else if (manche >= 3) {
                parfait = echecs == 0;
                terminer(true);
            }
        }
    }

    void pastille(GuiGraphics g, int x, int y, int l, int ht, int couleur) {
        g.fill(x, y, x + l, y + ht, Dessin.BORD);
        g.fill(x + 1, y + 1, x + l - 1, y + ht - 1, couleur);
        g.fill(x + 1, y + 1, x + l - 1, y + 2, Dessin.assombrir(couleur, 1.2));
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        // La fiole : elle prend la teinte du mélange en cours (ou la teinte à mémoriser, tant qu'elle est montrée).
        int fx = ox + 14, fy = oy + 34;
        int actuelle = visible > 0 ? cible : melange(gouttes);
        g.fill(fx + 22, oy + 10, fx + 26, oy + 26, 0xFF9FB0C0);
        g.fill(fx + 23, oy + 26, fx + 25, oy + 30, 0xFF6B7C8C);
        if (chute > 0 && dernier >= 0) g.fill(fx + 23, oy + 30 + (4 - chute) * 3, fx + 25, oy + 34 + (4 - chute) * 3, REACTIFS[dernier]);
        // Seul le liquide prend la teinte ; le verre de la fiole garde ses couleurs.
        Dessin.image(g, FIOLE, fx, fy, 48, 64);
        Dessin.teinte(g, actuelle);
        Dessin.image(g, LIQUIDE, fx, fy, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);

        // Colonne du milieu : la teinte à mémoriser, puis cachée, puis la comparaison chiffrée.
        int cx = ox + 74;
        if (visible > 0) {
            Dessin.ecrire(g, T.f("memorise"), cx, oy + 8, Dessin.JAUNE, false);
            pastille(g, cx, oy + 20, 64, 52, cible);
            jaugeTemps(g, cx, oy + 78, 64, visible / (double) MONTRE);
        } else if (pause > 0 || fini) {
            boolean ok = score >= SEUIL;
            Dessin.ecrire(g, T.f("target"), cx, oy + 8, Dessin.GRIS, false);
            pastille(g, cx, oy + 19, 64, 22, cible);
            Dessin.ecrire(g, T.f("your_mix"), cx, oy + 46, Dessin.GRIS, false);
            pastille(g, cx, oy + 57, 64, 22, melange(gouttes));
            Dessin.ecrire(g, score + " % " + (ok ? T.f("passed") : T.f("failed")), cx, oy + 86, ok ? Dessin.VERT : Dessin.ALERTE, false);
            Dessin.ecrire(g, T.f("needed") + SEUIL + " %", cx, oy + 97, Dessin.SOMBRE, false);
        } else {
            Dessin.ecrire(g, T.f("target"), cx, oy + 8, Dessin.GRIS, false);
            pastille(g, cx, oy + 19, 64, 22, 0xFF1A2633);
            // Aide aux couleurs : la ressemblance du mélange en cours s'affiche à la place du point d'interrogation.
            boolean aide = TraqueClient.aideCouleurs && nombre() > 0;
            Dessin.centre(g, aide ? ressemblance(melange(gouttes), cible) + " %" : "?", cx + 32, oy + 26, aide ? Dessin.TEXTE : Dessin.SOMBRE);
            Dessin.ecrire(g, T.f("your_mix"), cx, oy + 46, Dessin.GRIS, false);
            pastille(g, cx, oy + 57, 64, 22, melange(gouttes));
            int k = 0;
            for (int i = 0; i < 4; i++) {
                for (int n = 0; n < gouttes[i]; n++, k++) g.fill(cx + k * 6, oy + 86, cx + k * 6 + 5, oy + 93, REACTIFS[i]);
            }
            Dessin.ecrire(g, nombre() + " / " + MAX + T.f("drops"), cx, oy + 97, Dessin.SOMBRE, false);
        }

        int tx = ox + 152;
        Dessin.ecrire(g, T.f("flask") + Math.min(3, manche + (pause > 0 || fini ? 0 : 1)) + " / 3", tx, oy + 8, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("fails") + echecs + " / 2", tx, oy + 20, echecs > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
        boolean libre = visible == 0 && pause == 0 && !fini;
        for (int i = 0; i < 4; i++) {
            boolean actif = libre && nombre() < MAX;
            Dessin.bouton(g, bx(i), by(i), 58, 20, NOMS[i], actif && Dessin.dans(mx, my, bx(i), by(i), 58, 20), actif, REACTIFS[i]);
        }
        boolean rempli = libre && nombre() > 0;
        Dessin.bouton(g, tx, oy + 88, 58, 22, T.f("empty"), rempli && Dessin.dans(mx, my, tx, oy + 88, 58, 22), rempli, 0xFF7C8FA3);
        Dessin.bouton(g, tx + 62, oy + 88, 58, 22, T.f("confirm"), rempli && Dessin.dans(mx, my, tx + 62, oy + 88, 58, 22), rempli, Dessin.VERT);
    }

    @Override
    String consigne() {
        return T.f("memorise_the_colour_then_recreate");
    }
}
