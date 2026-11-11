package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Photo de terrain : cadrer un Pokémon du coin qui passe, et déclencher quand l'image est nette
final class JeuPhoto extends Jeu {
    static final ResourceLocation FOND = Dessin.texture("sous_bois");
    static final String[] PAR_DEFAUT = {"rattata", "pidgey", "oddish", "zubat", "eevee", "pikachu"};
    static final int DUREE = 560;
    final String espece;
    final double p1, p2, p3;
    int temps = DUREE, fautes, flash, eclair, prises, tic;
    String message = "";

    JeuPhoto(Random h) {
        // Le sujet est l'un des suspects de l'enquête, pris au hasard (sans rapport avec la cible).
        net.minecraft.nbt.ListTag suspects = TraqueClient.etat.getList("suspects", net.minecraft.nbt.Tag.TAG_COMPOUND);
        espece = suspects.isEmpty() ? PAR_DEFAUT[h.nextInt(PAR_DEFAUT.length)] : suspects.getCompound(h.nextInt(suspects.size())).getString("espece");
        p1 = h.nextDouble() * 6;
        p2 = h.nextDouble() * 6;
        p3 = h.nextDouble() * 6;
    }

    double vitesse() {
        return 1 + prises * 0.35;
    }

    double sx() {
        return 80 + 46 * Math.sin(tic * 0.021 * vitesse() + p1);
    }

    double sy() {
        return 58 + 22 * Math.sin(tic * 0.033 * vitesse() + p2);
    }

    // Flou en pixels : la mise au point « pompe » toute seule, il faut déclencher au bon moment.
    double flou() {
        return 2.5 * (1 + Math.sin(tic * 0.085 * vitesse() + p3));
    }

    @Override
    void tick() {
        if (flash > 0) flash--;
        if (eclair > 0) eclair--;
        if (--temps <= 0) {
            terminer(false);
            return;
        }
        presser(temps, DUREE);
        tic++;
    }

    @Override
    void clic(double mx, double my) {
        int bx = ox + 4, by = oy + 3;
        if (!Dessin.dans(mx, my, bx, by, L, H)) return;
        Dessin.bip(Sons.OBTURATEUR, 1.6f, 0.7f);
        boolean cadre = Math.abs(mx - bx - sx()) < 13 && Math.abs(my - by - sy()) < 13, net = flou() < 1.3;
        if (cadre && net) {
            eclair = 4;
            message = T.f("sharp_photo");
            Dessin.note(1f + prises * 0.25f);
            if (++prises >= 3) {
                parfait = fautes == 0;
                terminer(true);
            }
        } else {
            flash = 8;
            message = cadre ? T.f("blurry_photo") : T.f("badly_framed");
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
        }
    }

    @Override
    void dessiner(GuiGraphics g, int mx, int my, float pt) {
        int bx = ox + 4, by = oy + 3;
        cadre(g, bx, by, flash > 0);
        Dessin.image(g, FOND, bx, by, L, H);
        int x = bx + (int) sx() - 24, y = by + (int) sy() - 24, f = (int) Math.round(flou());
        // Image floue : le sujet se dédouble, d'autant plus que la mise au point est loin.
        if (f >= 1) {
            Dessin.portrait(g, espece, Math.max(bx, x - f * 2), y, 3f);
            Dessin.portrait(g, espece, Math.min(bx + L - 48, x + f * 2), y, 3f);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(Math.max(bx, x - f * 2), y, Math.min(bx + L, x + f * 2 + 48), y + 48, (Math.min(5, f) * 22 << 24) | 0xC8D8C0);
            g.pose().popPose();
        } else {
            Dessin.portrait(g, espece, Math.max(bx, Math.min(bx + L - 48, x)), y, 3f);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        if (!fini && Dessin.dans(mx, my, bx, by, L, H)) {
            int c = flou() < 1.3 ? Dessin.VERT : Dessin.TEXTE;
            for (int cx = -1; cx <= 1; cx += 2) {
                for (int cy = -1; cy <= 1; cy += 2) {
                    int ex = mx + cx * 26, ey = my + cy * 26;
                    g.fill(Math.min(ex, ex - cx * 8), cy < 0 ? ey : ey - 1, Math.max(ex, ex - cx * 8), cy < 0 ? ey + 1 : ey, c);
                    g.fill(cx < 0 ? ex : ex - 1, Math.min(ey, ey - cy * 8), cx < 0 ? ex + 1 : ex, Math.max(ey, ey - cy * 8), c);
                }
            }
            g.fill(mx - 1, my - 1, mx + 1, my + 1, c);
        }
        if (eclair > 0) g.fill(bx, by, bx + L, by + H, 0xA0FFFFFF);
        g.pose().popPose();
        int tx = ox + 172;
        texte(g, T.f("photos") + prises + " / 3", tx, oy + 10, Dessin.TEXTE);
        texte(g, T.f("failed_2") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
        texte(g, T.f("sharpness"), tx, oy + 40, Dessin.JAUNE);
        jauge(g, tx, oy + 51, 100, 1 - flou() / 5.0, flou() < 1.3 ? Dessin.VERT : Dessin.SOMBRE);
        texte(g, message, tx, oy + 64, message.equals(T.f("sharp_photo")) ? Dessin.VERT : Dessin.ALERTE);
        texte(g, T.f("light"), tx, oy + 92, Dessin.GRIS);
        jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
    }

    @Override
    String consigne() {
        return T.f("a_local_pok_mon_walks");
    }
}
