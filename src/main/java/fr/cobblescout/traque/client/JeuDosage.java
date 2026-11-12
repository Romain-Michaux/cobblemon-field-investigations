package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Dosage : ajouter le réactif jusqu'au virage, sans le dépasser
final class JeuDosage extends Jeu {
    static final ResourceLocation BURETTE = Dessin.texture("burette"), FIOLE = Dessin.texture("erlenmeyer"), LIQUIDE = Dessin.texture("erlenmeyer_liquide");
    final Random h;
    int virage, gouttes, attente, manche, fautes, temps, duree, pause, flash;
    boolean vire;
    String message = "";

    JeuDosage(Random h) {
        this.h = h;
        nouvelle();
    }

    void nouvelle() {
        virage = 16 + h.nextInt(17);
        gouttes = 0;
        vire = false;
        duree = temps = 230 - manche * 15;
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (attente > 0) attente--;
        if (pause > 0) {
            if (--pause == 0 && !fini) nouvelle();
            return;
        }
        if (--temps <= 0) faute(T.f("too_slow"));
        else presser(temps, duree);
    }

    void faute(String raison) {
        message = raison;
        flash = 8;
        Dessin.note(0.5f);
        if (++fautes >= 2) terminer(false);
        else pause = 34;
    }

    void verser(int n) {
        gouttes += n;
        attente = 6;
        // Plus on approche du virage, plus la goutte sonne aigu.
        Dessin.bip(Sons.GOUTTE, (float) (0.6 + 1.2 * Math.max(0, 1 - (virage - gouttes) / 30.0)), 0.5f);
        if (gouttes < virage) return;
        vire = true;
        if (gouttes - virage <= 1) {
            message = T.f("clean_endpoint");
            Dessin.note(1f + manche * 0.25f);
            if (++manche >= 3) {
                parfait = fautes == 0;
                terminer(true);
            } else {
                pause = 34;
            }
        } else {
            faute(T.f("endpoint_overshot"));
        }
    }

    @Override
    void clic(double mx, double my) {
        if (pause > 0 || attente > 0) return;
        int tx = ox + 172;
        if (Dessin.dans(mx, my, tx, oy + 60, 50, 20)) verser(1);
        else if (Dessin.dans(mx, my, tx + 54, oy + 60, 50, 20)) verser(3);
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int ax = ox + 4, ay = oy + 3;
        cadre(g, ax, ay, flash > 0);
        g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
        Dessin.image(g, BURETTE, ax + 40, ay + 2, 32, 96);
        if (attente > 0) g.fill(ax + 55, ay + 96 + (6 - attente), ax + 57, ay + 100 + (6 - attente), 0xFFD0407A);
        // Le liquide rosit à l'approche du virage, puis vire franchement.
        int teinte = vire ? 0xFFD0407A : melange(0xFFE8EEF2, 0xFFF6C4D6, (gouttes - (virage - 5)) / 5.0);
        Dessin.image(g, FIOLE, ax + 84, ay + 46, 48, 64);
        Dessin.teinte(g, teinte);
        Dessin.image(g, LIQUIDE, ax + 84, ay + 46, 48, 64);
        g.setColor(1f, 1f, 1f, 1f);
        int tx = ox + 172;
        texte(g, T.f("titration") + Math.min(3, manche + 1) + " / 3", tx, oy + 8, Dessin.TEXTE);
        texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 20, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("drops_2") + gouttes, tx, oy + 34, Dessin.JAUNE);
        texte(g, pause > 0 || fini ? message : "", tx, oy + 46, message.equals(T.f("clean_endpoint")) ? Dessin.VERT : Dessin.ALERTE);
        boolean libre = pause == 0 && attente == 0 && !fini;
        Dessin.bouton(g, tx, oy + 60, 50, 20, T.f("drop"), libre && Dessin.dans(mx, my, tx, oy + 60, 50, 20), libre, Dessin.ACCENT);
        Dessin.bouton(g, tx + 54, oy + 60, 50, 20, T.f("squirt_3"), libre && Dessin.dans(mx, my, tx + 54, oy + 60, 50, 20), libre, 0xFFE9A15B);
        jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
    }

    @Override
    String consigne() {
        return T.f("pour_until_the_liquid_turns");
    }
}
