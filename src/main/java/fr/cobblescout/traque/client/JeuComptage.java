package fr.cobblescout.traque.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Comptage de colonies : compter d'un coup d'œil, puis répondre
final class JeuComptage extends Jeu {
    static final ResourceLocation PETRI = Dessin.texture("petri");
    static final int[] TEINTES = {0xFFFFFFFF, 0xFFE07A5F, 0xFF7FB069, 0xFF6C8EBF};
    final Random h;
    int[] cx, cy, taille, teinte, choix = new int[4];
    int nombre, manche, erreurs, visible, pause;
    boolean dernierOk;

    JeuComptage(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        sonMontre();
        nombre = 7 + manche * 2 + h.nextInt(4);
        cx = new int[nombre];
        cy = new int[nombre];
        taille = new int[nombre];
        teinte = new int[nombre];
        for (int i = 0; i < nombre; i++) {
            taille[i] = 4 + h.nextInt(3);
            teinte[i] = h.nextInt(TEINTES.length);
            for (int essai = 0; essai < 80; essai++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 33;
                cx[i] = (int) Math.round(Math.cos(a) * r);
                cy[i] = (int) Math.round(Math.sin(a) * r);
                boolean libre = true;
                for (int k = 0; k < i; k++) libre &= Math.hypot(cx[k] - cx[i], cy[k] - cy[i]) >= 9;
                if (libre) break;
            }
        }
        List<Integer> options = new ArrayList<>(List.of(nombre - 2, nombre - 1, nombre + 1, nombre + 2));
        Collections.shuffle(options, h);
        List<Integer> retenues = new ArrayList<>(options.subList(0, 3));
        retenues.add(nombre);
        Collections.sort(retenues);
        for (int i = 0; i < 4; i++) choix[i] = retenues.get(i);
        visible = 56 - manche * 6;
    }

    @Override
    void tick() {
        if (visible > 0 && --visible == 0) sonCache();
        if (pause > 0 && --pause == 0 && !fini) nouvelle();
    }

    @Override
    void clic(double mx, double my) {
        if (visible > 0 || pause > 0) return;
        for (int i = 0; i < 4; i++) {
            if (!Dessin.dans(mx, my, ox + 126 + (i % 2) * 74, oy + 46 + (i / 2) * 28, 68, 22)) continue;
            dernierOk = choix[i] == nombre;
            pause = 24;
            if (dernierOk) {
                Dessin.note(1f + manche * 0.25f);
                if (++manche >= 3) {
                    parfait = erreurs == 0;
                    terminer(true);
                }
            } else {
                Dessin.note(0.5f);
                if (++erreurs >= 2) terminer(false);
            }
            return;
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int px = ox + 12, py = oy + 11;
        Dessin.image(g, PETRI, px, py, 96, 96);
        boolean montre = visible > 0 || pause > 0;
        if (montre) {
            for (int i = 0; i < nombre; i++) {
                int x = px + 48 + cx[i] - taille[i] / 2, y = py + 48 + cy[i] - taille[i] / 2, c = TEINTES[teinte[i]];
                g.fill(x - 1, y, x + taille[i] + 1, y + taille[i], Dessin.assombrir(c, 0.45));
                g.fill(x, y - 1, x + taille[i], y + taille[i] + 1, Dessin.assombrir(c, 0.45));
                g.fill(x, y, x + taille[i], y + taille[i], c);
            }
        } else {
            Dessin.centre(g, "?", px + 48, py + 44, Dessin.BORD);
        }
        int tx = ox + 126;
        Dessin.ecrire(g, T.f("dish") + Math.min(3, manche + 1) + T.f("3_mistakes") + erreurs + " / 2", tx, oy + 10, erreurs > 0 ? Dessin.ALERTE : Dessin.TEXTE, false);
        String etat = pause > 0 ? (dernierOk ? T.f("correct") + nombre + T.f("colonies") : T.f("wrong_there_were") + nombre) : visible > 0 ? T.f("count_the_colonies") : T.f("how_many_were_there");
        Dessin.ecrire(g, etat, tx, oy + 26, pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.JAUNE, false);
        boolean libre = visible == 0 && pause == 0 && !fini;
        for (int i = 0; i < 4; i++) {
            int x = tx + (i % 2) * 74, y = oy + 46 + (i / 2) * 28;
            Dessin.bouton(g, x, y, 68, 22, String.valueOf(choix[i]), libre && Dessin.dans(mx, my, x, y, 68, 22), libre, Dessin.ACCENT);
        }
        if (visible > 0) jaugeTemps(g, tx, oy + 106, 142, visible / (double) (56 - manche * 6));
    }

    @Override
    String consigne() {
        return T.f("the_dish_is_only_visible");
    }
}
