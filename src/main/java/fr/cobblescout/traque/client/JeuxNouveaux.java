package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

// Mini-jeux ajoutés en 1.1 : cinq prélèvements, cinq analyses, et six gestes propres à un type d'enquête.
// Tous se jouent dans une aire de 160 x 112 à gauche, avec les compteurs et la consigne à droite.
final class JeuxNouveaux {
    static final int L = 160, H = 112;

    private JeuxNouveaux() {}

    static void cadre(GuiGraphics g, int bx, int by, boolean alerte) {
        g.fill(bx - 1, by - 1, bx + L + 1, by + H + 1, alerte ? Dessin.ALERTE : Dessin.BORD);
    }

    static void texte(GuiGraphics g, String s, int x, int y, int couleur) {
        g.drawString(Dessin.police(), s, x, y, couleur, false);
    }

    // Jauge horizontale (part de 0 à 1).
    static void jauge(GuiGraphics g, int x, int y, int l, double part, int couleur) {
        g.fill(x, y, x + l, y + 6, Dessin.BORD);
        g.fill(x + 1, y + 1, x + 1 + (int) ((l - 2) * Math.max(0, Math.min(1, part))), y + 5, couleur);
    }

    static void disque(GuiGraphics g, int cx, int cy, int rayon, int couleur) {
        for (int dy = -rayon; dy <= rayon; dy++) {
            int dx = (int) Math.round(Math.sqrt(rayon * rayon - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, couleur);
        }
    }

    static int melange(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t), v = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (v << 8) | bl;
    }

    // =====================================================================================================
    // PRÉLÈVEMENTS
    // =====================================================================================================

    // ---------- Tamisage (indice enfoui) : secouer par à-coups pour faire tomber le sable sans perdre les fragments ----------

    static final class Tamis extends Jeu {
        static final ResourceLocation TAMIS = Dessin.texture("tamis");
        static final int DUREE = 620, GRAINS = 110;
        final ResourceLocation indice;
        final int[] gx = new int[GRAINS], gy = new int[GRAINS], fx = new int[3], fy = new int[3];
        double sable = 100, secousse;
        int temps = DUREE, fautes, flash, tic, visibles;

        Tamis(Random h, String genre, int variante) {
            indice = Dessin.iconeEnfoui(genre, variante);
            for (int i = 0; i < GRAINS; i++) {
                double a = h.nextDouble() * Math.PI * 2, r = Math.sqrt(h.nextDouble()) * 31;
                gx[i] = (int) (48 + Math.cos(a) * r);
                gy[i] = (int) (48 + Math.sin(a) * r);
            }
            for (int i = 0; i < 3; i++) {
                double a = h.nextDouble() * Math.PI * 2 + i * 2.1, r = 8 + h.nextDouble() * 12;
                fx[i] = (int) (40 + Math.cos(a) * r);
                fy[i] = (int) (40 + Math.sin(a) * r);
            }
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
            if (appui) {
                sable -= 0.55;
                secousse += 2.2;
                if (tic % 4 == 0) Dessin.bip(SoundEvents.SAND_BREAK, (float) (0.8 + secousse / 160), 0.5f);
            } else {
                secousse = Math.max(0, secousse - 3);
            }
            if (secousse >= 100) {
                // Secoué trop longtemps d'affilée : un fragment passe à travers les mailles.
                secousse = 35;
                flash = 8;
                Dessin.bip(SoundEvents.GLASS_BREAK, 1.5f, 0.5f);
                if (++fautes >= 3) terminer(false);
            }
            int v = sable <= 0 ? 3 : sable < 34 ? 2 : sable < 67 ? 1 : 0;
            if (v > visibles) {
                visibles = v;
                Dessin.note(1f + v * 0.2f);
            }
            if (sable <= 0 && !fini) {
                parfait = fautes == 0;
                terminer(true);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cadre(g, bx, by, flash > 0);
            g.fill(bx, by, bx + L, by + H, 0xFF2A2019);
            int dx = appui && !fini ? (tic % 2) * 4 - 2 : 0, sx = bx + 32 + dx, sy = by + 8;
            Dessin.image(g, TAMIS, sx, sy, 96, 96);
            for (int i = 0; i < 3; i++) {
                if (i < visibles) Dessin.image(g, indice, sx + fx[i], sy + fy[i], 16, 16);
            }
            int n = (int) (Math.max(0, sable) / 100 * GRAINS);
            for (int i = 0; i < n; i++) g.fill(sx + gx[i], sy + gy[i], sx + gx[i] + 3, sy + gy[i] + 3, i % 3 == 0 ? 0xFFB98F4E : 0xFFD9B36A);
            // Le sable qui tombe sous le tamis.
            if (appui && !fini) {
                for (int i = 0; i < 6; i++) g.fill(sx + 20 + (tic * 7 + i * 13) % 56, by + 100 + (tic + i * 5) % 10, sx + 22 + (tic * 7 + i * 13) % 56, by + 102 + (tic + i * 5) % 10, 0xFFD9B36A);
            }
            int tx = ox + 172;
            texte(g, T.f("sand") + (int) Math.ceil(Math.max(0, sable)) + " %", tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("lost") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            texte(g, T.f("shaking"), tx, oy + 40, secousse > 70 ? Dessin.ALERTE : Dessin.JAUNE);
            jauge(g, tx, oy + 51, 100, secousse / 100, secousse > 70 ? Dessin.ALERTE : Dessin.JAUNE);
            Dessin.paragraphe(g, T.f("let_go_before_the_gauge"), tx, oy + 64, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_to_shake");
        }
    }

    // ---------- Moulage : couler le plâtre dans chaque alvéole de la mâchoire, à ras bord, sans déborder ----------

    static final class Moulage extends Jeu {
        static final int N = 5, DUREE = 560, HAUT_BLOC = 24, PAS = 30;
        // Chaque alvéole a sa largeur et sa profondeur (une canine est étroite et profonde, une molaire large et courte).
        final int[] largeur = new int[N], cible = new int[N];
        final double[] niveau = new double[N];
        int temps = DUREE, fautes, flash, tic;
        double cx;

        Moulage(Random h) {
            for (int i = 0; i < N; i++) {
                largeur[i] = 16 + h.nextInt(3) * 4;
                cible[i] = 34 + h.nextInt(44);
            }
        }

        int gauche(int i) {
            return ox + 4 + 6 + i * PAS + (PAS - largeur[i]) / 2;
        }

        int colonne(double mx) {
            for (int i = 0; i < N; i++) {
                if (mx >= gauche(i) && mx < gauche(i) + largeur[i]) return i;
            }
            return -1;
        }

        boolean plein(int i) {
            return niveau[i] >= cible[i] - 6;
        }

        // Largeur de l'alvéole à cette hauteur au-dessus du fond : la racine est plus étroite que la couronne.
        int retrait(int i, int hauteur) {
            return hauteur < cible[i] * 0.4 ? 4 : hauteur < cible[i] * 0.55 ? 2 : 0;
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
            int i = appui ? colonne(cx) : -1;
            if (i >= 0) {
                niveau[i] += 1.1;
                if (tic % 5 == 0) Dessin.bip(SoundEvents.BOTTLE_EMPTY, (float) (0.6 + niveau[i] / cible[i]), 0.5f);
                if (niveau[i] > cible[i] + 1.5) {
                    // Débordement : il faut gratter et recommencer cette alvéole presque à zéro.
                    niveau[i] = cible[i] * 0.3;
                    flash = 8;
                    Dessin.bip(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.6f, 0.9f);
                    if (++fautes >= 3) terminer(false);
                }
            }
            // Le moulage n'est validé qu'une fois le bec relevé : c'est au joueur de s'arrêter à temps.
            boolean tous = !appui;
            for (int k = 0; k < N; k++) tous &= plein(k);
            if (tous && !fini) {
                parfait = fautes == 0;
                Dessin.bip(SoundEvents.STONE_PLACE, 1.2f, 0.8f);
                terminer(true);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3, haut = by + HAUT_BLOC;
            cx = mx;
            cadre(g, bx, by, flash > 0);
            g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
            // Le bloc de gencive, vu en coupe.
            g.fill(bx, haut, bx + L, by + H, 0xFFB5746A);
            g.fill(bx, haut, bx + L, haut + 3, 0xFFD69A8C);
            g.fill(bx, by + H - 10, bx + L, by + H, 0xFF8F564E);
            int survol = fini ? -1 : colonne(mx);
            for (int i = 0; i < N; i++) {
                int x = gauche(i), fond = haut + cible[i];
                for (int k = 0; k < cible[i]; k++) {
                    int r = retrait(i, k), y = fond - 1 - k;
                    g.fill(x + r, y, x + largeur[i] - r, y + 1, k < niveau[i] ? (k >= (int) niveau[i] - 1 ? 0xFFFFFFFF : 0xFFE9E4D8) : 0xFF3A1A1E);
                }
                // La bonne hauteur : les six derniers pixels sous le bord.
                g.fill(x, haut, x + 2, haut + 6, Dessin.VERT);
                g.fill(x + largeur[i] - 2, haut, x + largeur[i], haut + 6, Dessin.VERT);
                if (i == survol) g.fill(x, haut - 2, x + largeur[i], haut - 1, Dessin.JAUNE);
                if (plein(i)) texte(g, "✔", x + largeur[i] / 2 - 3, haut - 11, Dessin.VERT);
            }
            // Le bec verseur suit la souris.
            if (!fini && Dessin.dans(mx, my, bx, by, L, H)) {
                g.fill(mx - 3, by + 1, mx + 3, by + 9, 0xFF9FB0C0);
                if (appui && survol >= 0) g.fill(mx - 1, by + 9, mx + 1, haut + cible[survol] - (int) niveau[survol], 0xFFF4F0E6);
            }
            int tx = ox + 172, faits = 0;
            for (int i = 0; i < N; i++) faits += plein(i) ? 1 : 0;
            texte(g, T.f("sockets_full") + faits + " / " + N, tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("overflows") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            Dessin.paragraphe(g, T.f("fill_up_to_the_green"), tx, oy + 40, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("put_the_spout_over_a_2");
        }
    }

    // ---------- Pince à épiler : saisir chaque fibre quand la pince se referme, bien alignée ----------

    static final class Pince extends Jeu {
        static final ResourceLocation NID = Dessin.texture("nid"), PINCE = Dessin.texture("pince");
        static final int N = 5, DUREE = 560;
        final int[] fx = new int[N];
        final boolean[] pris = new boolean[N];
        int temps = DUREE, fautes, flash, tic, restants = N;
        double phase;

        Pince(Random h) {
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
            if (avant >= 0.3 && ouverture() < 0.3) Dessin.bip(SoundEvents.TRIPWIRE_CLICK_ON, 1.6f, 0.4f);
        }

        @Override
        void clic(double mx, double my) {
            int bx = ox + 4, by = oy + 3;
            if (!Dessin.dans(mx, my, bx, by, L, H)) return;
            for (int i = 0; i < N; i++) {
                if (pris[i] || Math.abs(mx - (bx + fx[i])) > 5) continue;
                if (ouverture() < 0.3) {
                    pris[i] = true;
                    Dessin.bip(SoundEvents.ITEM_PICKUP, 1.2f + (N - restants) * 0.12f, 0.7f);
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

    // ---------- Photo de terrain : cadrer un Pokémon du coin qui passe, et déclencher quand l'image est nette ----------

    static final class Photo extends Jeu {
        static final ResourceLocation FOND = Dessin.texture("sous_bois");
        static final String[] PAR_DEFAUT = {"rattata", "pidgey", "oddish", "zubat", "eevee", "pikachu"};
        static final int DUREE = 560;
        final String espece;
        final double p1, p2, p3;
        int temps = DUREE, fautes, flash, eclair, prises, tic;
        String message = "";

        Photo(Random h) {
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
            return 80 + 52 * Math.sin(tic * 0.021 * vitesse() + p1);
        }

        double sy() {
            return 60 + 26 * Math.sin(tic * 0.033 * vitesse() + p2);
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
            Dessin.bip(SoundEvents.LEVER_CLICK, 1.6f, 0.7f);
            boolean cadre = Math.abs(mx - bx - sx()) < 10 && Math.abs(my - by - sy()) < 10, net = flou() < 1.3;
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
            int x = bx + (int) sx() - 16, y = by + (int) sy() - 16, f = (int) Math.round(flou());
            // Image floue : le sujet se dédouble, d'autant plus que la mise au point est loin.
            if (f >= 1) {
                Dessin.portrait(g, espece, Math.max(bx, x - f * 2), y, 2f);
                Dessin.portrait(g, espece, Math.min(bx + L - 32, x + f * 2), y, 2f);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(Math.max(bx, x - f * 2), y, Math.min(bx + L, x + f * 2 + 32), y + 32, (Math.min(5, f) * 22 << 24) | 0xC8D8C0);
                g.pose().popPose();
            } else {
                Dessin.portrait(g, espece, Math.max(bx, Math.min(bx + L - 32, x)), y, 2f);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            // Le viseur : quatre équerres autour de la souris.
            if (!fini && Dessin.dans(mx, my, bx, by, L, H)) {
                int c = flou() < 1.3 ? Dessin.VERT : Dessin.TEXTE;
                for (int cx = -1; cx <= 1; cx += 2) {
                    for (int cy = -1; cy <= 1; cy += 2) {
                        int ex = mx + cx * 20, ey = my + cy * 20;
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

    // ---------- Écouvillon : frotter toute la tache, sans repasser sur une zone déjà prélevée ----------

    static final class Ecouvillon extends Jeu {
        static final ResourceLocation ECORCE = Dessin.texture("ecorce"), COTON = Dessin.texture("ecouvillon");
        static final int COLS = 20, RANGS = 14, CASE = 8, DUREE = 600;
        final boolean[] tache = new boolean[COLS * RANGS];
        final int[] propre = new int[COLS * RANGS];
        int temps = DUREE, fautes, flash, tic, total, faits, derniere = -1;
        double cx, cy;

        Ecouvillon(Random h) {
            for (int k = 0; k < 3; k++) {
                double ccx = 5 + h.nextInt(10), ccy = 4 + h.nextInt(6), r = 2.6 + h.nextDouble() * 1.4;
                for (int i = 0; i < tache.length; i++) {
                    if (Math.hypot(i % COLS - ccx, i / COLS - ccy) <= r) tache[i] = true;
                }
            }
            for (boolean t : tache) total += t ? 1 : 0;
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
            int c = (int) Math.floor((cx - ox - 4) / CASE), r = (int) Math.floor((cy - oy - 3) / CASE);
            int i = c >= 0 && c < COLS && r >= 0 && r < RANGS ? r * COLS + c : -1;
            if (!appui || i < 0 || !tache[i]) {
                derniere = -1;
                return;
            }
            if (propre[i] == 0) {
                propre[i] = tic;
                if (++faits % 2 == 0) Dessin.bip(SoundEvents.BRUSH_GENERIC, 1.3f + faits * 0.6f / total, 0.6f);
                if (faits >= total) {
                    parfait = fautes == 0;
                    Dessin.note(1.5f);
                    terminer(true);
                }
            } else if (i != derniere && tic - propre[i] > 14) {
                // Repasser sur une zone déjà frottée contamine l'écouvillon.
                flash = 8;
                Dessin.note(0.5f);
                if (++fautes >= 3) terminer(false);
            }
            if (propre[i] != 0) propre[i] = tic;
            derniere = i;
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cx = mx;
            cy = my;
            cadre(g, bx, by, flash > 0);
            Dessin.image(g, ECORCE, bx, by, L, H);
            for (int i = 0; i < tache.length; i++) {
                if (!tache[i]) continue;
                int x = bx + (i % COLS) * CASE, y = by + (i / COLS) * CASE;
                if (propre[i] == 0) {
                    g.fill(x, y, x + CASE, y + CASE, 0xD07A3FA8);
                    g.fill(x + 1, y + 1, x + 4, y + 3, 0x60E0B0FF);
                } else {
                    g.fill(x, y, x + CASE, y + CASE, 0x50F4F0E0);
                }
            }
            if (!fini && Dessin.dans(mx, my, bx, by, L, H)) Dessin.image(g, COTON, mx - 16, my - 62, 32, 64);
            int tx = ox + 172;
            texte(g, T.f("stain_swabbed") + (faits * 100 / Math.max(1, total)) + " %", tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("soiled") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            Dessin.paragraphe(g, T.f("you_may_lift_the_swab"), tx, oy + 42, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_and_rub");
        }
    }

    // ---------- Tri des fragments (nid) : glisser chaque objet dans son compartiment, sans pousser les autres au mauvais endroit ----------

    static final class Tri extends Jeu {
        static final ResourceLocation PLATEAU = Dessin.texture("plateau");
        static final ResourceLocation[] DEBRIS = {Dessin.texture("debris_coquille"), Dessin.texture("debris_brindille"),
                Dessin.texture("debris_feuille"), Dessin.texture("debris_caillou")};
        static final int N = 13, COQUILLES = 5, DUREE = 560, RAYON = 7, REBUT = 26, BOITE = L - 26;
        final double[] x = new double[N], y = new double[N];
        final int[] sorte = new int[N];
        final boolean[] sorti = new boolean[N];
        int tenu = -1, rangees, restantes = COQUILLES, fautes, temps = DUREE, flash, tic, dernierChoc;
        double cx, cy;

        Tri(Random h) {
            for (int i = 0; i < N; i++) {
                sorte[i] = i < COQUILLES ? 0 : 1 + h.nextInt(3);
                for (int essai = 0; essai < 200; essai++) {
                    x[i] = REBUT + 14 + h.nextInt(BOITE - REBUT - 28);
                    y[i] = 12 + h.nextInt(H - 24);
                    boolean libre = true;
                    for (int k = 0; k < i; k++) libre &= Math.hypot(x[k] - x[i], y[k] - y[i]) >= RAYON * 2 + 2;
                    if (libre) break;
                }
            }
        }

        void faute() {
            flash = 8;
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
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
            if (!appui) tenu = -1;
            if (tenu >= 0) {
                // L'objet tenu suit la souris, sans jamais aller plus vite que trois pixels par tic : il pousse, il ne saute pas.
                double dx = cx - ox - 4 - x[tenu], dy = cy - oy - 3 - y[tenu], d = Math.hypot(dx, dy);
                if (d > 0.5) {
                    double pas = Math.min(3, d);
                    x[tenu] += dx / d * pas;
                    y[tenu] += dy / d * pas;
                }
            }
            // Les objets se repoussent : celui qu'on tient ne cède pas, les autres s'écartent (et poussent à leur tour).
            for (int passe = 0; passe < 3; passe++) {
                for (int i = 0; i < N; i++) {
                    for (int k = i + 1; k < N; k++) {
                        if (sorti[i] || sorti[k]) continue;
                        double dx = x[k] - x[i], dy = y[k] - y[i], d = Math.hypot(dx, dy), manque = RAYON * 2 - d;
                        if (manque <= 0) continue;
                        if (d < 0.01) {
                            dx = 1;
                            dy = 0;
                            d = 1;
                        }
                        double pi = i == tenu ? 0 : k == tenu ? 1 : 0.5;
                        x[i] -= dx / d * manque * pi;
                        y[i] -= dy / d * manque * pi;
                        x[k] += dx / d * manque * (1 - pi);
                        y[k] += dy / d * manque * (1 - pi);
                        if (tenu >= 0 && tic - dernierChoc > 5) {
                            dernierChoc = tic;
                            Dessin.bip(SoundEvents.STONE_HIT, 1.5f, 0.3f);
                        }
                    }
                }
            }
            for (int i = 0; i < N; i++) {
                if (sorti[i]) continue;
                x[i] = Math.max(RAYON, Math.min(L - RAYON, x[i]));
                y[i] = Math.max(RAYON, Math.min(H - RAYON, y[i]));
                boolean rebut = x[i] < REBUT - 2, boite = x[i] > BOITE + 2;
                if (!rebut && !boite) continue;
                // Tombé dans un compartiment : il n'en ressort plus.
                sorti[i] = true;
                if (i == tenu) tenu = -1;
                if (sorte[i] == 0) {
                    restantes--;
                    if (boite) {
                        rangees++;
                        Dessin.note(1f + rangees * 0.15f);
                    } else {
                        faute(); // une coquille au rebut
                    }
                } else if (boite) {
                    faute(); // un débris dans la boîte d'échantillons
                } else {
                    Dessin.bip(SoundEvents.ITEM_PICKUP, 0.7f, 0.4f);
                }
            }
            if (restantes <= 0 && !fini) {
                parfait = fautes == 0;
                terminer(rangees > 0);
            }
        }

        @Override
        void clic(double mx, double my) {
            double lx = mx - ox - 4, ly = my - oy - 3, proche = RAYON + 3;
            tenu = -1;
            for (int i = 0; i < N; i++) {
                double d = Math.hypot(lx - x[i], ly - y[i]);
                if (!sorti[i] && d < proche) {
                    proche = d;
                    tenu = i;
                }
            }
            if (tenu >= 0) Dessin.bip(SoundEvents.ITEM_PICKUP, 1.3f, 0.4f);
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cx = mx;
            cy = my;
            cadre(g, bx, by, flash > 0);
            Dessin.image(g, PLATEAU, bx, by, L, H);
            // À gauche le rebut, à droite la boîte d'échantillons.
            g.fill(bx, by, bx + REBUT, by + H, 0xB0241A12);
            g.fill(bx + REBUT - 1, by, bx + REBUT, by + H, Dessin.ALERTE);
            g.fill(bx + BOITE, by, bx + L, by + H, 0xB0102A22);
            g.fill(bx + BOITE, by, bx + BOITE + 1, by + H, Dessin.VERT);
            g.setColor(1f, 1f, 1f, 0.45f);
            Dessin.image(g, DEBRIS[1], bx + 5, by + 6, 16, 16);
            Dessin.image(g, DEBRIS[3], bx + 5, by + 26, 16, 16);
            Dessin.image(g, DEBRIS[0], bx + BOITE + 5, by + 6, 16, 16);
            g.setColor(1f, 1f, 1f, 1f);
            for (int i = 0; i < N; i++) {
                if (sorti[i]) continue;
                int x0 = bx + (int) Math.round(x[i]), y0 = by + (int) Math.round(y[i]);
                if (i == tenu) Dessin.cercle(g, x0 - 0.5, y0 - 0.5, RAYON + 2, 1, Dessin.JAUNE);
                Dessin.image(g, DEBRIS[sorte[i]], x0 - 8, y0 - 8, 16, 16);
            }
            int tx = ox + 172;
            texte(g, T.f("shells_2") + rangees + " / " + COQUILLES, tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("mistakes") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            Dessin.paragraphe(g, T.f("objects_push_each_other_clear"), tx, oy + 42, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("drag_the_shells_into_the");
        }
    }

    // =====================================================================================================
    // ANALYSES
    // =====================================================================================================

    // ---------- Microscope : faire la mise au point, puis désigner la cellule anormale ----------

    static final class Microscope extends Jeu {
        static final int N = 10;
        final Random h;
        final int[] px = new int[N], py = new int[N];
        int mise, cible, anormale, manche, fautes, temps, duree, flash, pause;
        boolean net;

        Microscope(Random h) {
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
                Dessin.bip(SoundEvents.COMPARATOR_CLICK, 0.8f + (float) Math.max(0, 30 - flou()) / 30f, 0.6f);
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

    // ---------- Chromatographie : arrêter chaque bandelette quand ses trois bandes sont bien séparées ----------

    static final class Chromato extends Jeu {
        static final int N = 3, HAUT = 96, ECART = 13;
        static final int[][] TEINTES = {{0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9}, {0xFF9B5FC0, 0xFF5DBB5D, 0xFFE9A15B}, {0xFF4FC1C5, 0xFFEE6A9A, 0xFFB8B8A0}};
        final double[] front = new double[N], vitesse = new double[N];
        final double[][] rapport = new double[N][3];
        // 0 = en cours, 1 = arrêtée à temps, 2 = ratée.
        final int[] etat = new int[N];
        int fautes, attente = 24, tic, flash;

        Chromato(Random h) {
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
                if (--attente == 0) Dessin.bip(SoundEvents.BOTTLE_EMPTY, 1.4f, 0.7f);
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
            if (encore && tic % 8 == 0) Dessin.bip(SoundEvents.NOTE_BLOCK_HAT.value(), (float) (0.7 + max / HAUT), 0.35f);
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

    // ---------- Arbre de parenté : suivre les branches d'après les trois caractères de l'échantillon ----------

    static final class Parente extends Jeu {
        final Random h;
        final int[] ordre = new int[3], gauche = new int[3], valeurs = new int[3];
        int manche, fautes, temps, duree, flash, pause, bonne;

        Parente(Random h) {
            this.h = h;
            nouvelle();
        }

        void nouvelle() {
            int[][] permutations = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
            System.arraycopy(permutations[h.nextInt(6)], 0, ordre, 0, 3);
            bonne = 0;
            for (int l = 0; l < 3; l++) {
                gauche[l] = h.nextInt(2);
                valeurs[l] = h.nextInt(2);
            }
            for (int l = 0; l < 3; l++) bonne = bonne * 2 + (valeurs[ordre[l]] == gauche[l] ? 0 : 1);
            duree = temps = 260 - manche * 50;
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

        // Caractères : 0 = forme (rond / carré), 1 = couleur (rouge / bleu), 2 = taille (grand / petit).
        static void icone(GuiGraphics g, int x, int y, int caractere, int valeur) {
            g.fill(x - 5, y - 5, x + 5, y + 5, Dessin.BORD);
            if (caractere == 0) {
                if (valeur == 0) disque(g, x, y, 3, Dessin.TEXTE);
                else g.fill(x - 3, y - 3, x + 3, y + 3, Dessin.TEXTE);
            } else if (caractere == 1) {
                g.fill(x - 3, y - 3, x + 3, y + 3, valeur == 0 ? 0xFFE8543A : 0xFF4A90D9);
            } else {
                int r = valeur == 0 ? 4 : 2;
                g.fill(x - r, y - r, x + r, y + r, 0xFFB8C4D0);
            }
        }

        int fx(int feuille) {
            return ox + 4 + 10 + feuille * 20;
        }

        @Override
        void clic(double mx, double my) {
            if (pause > 0) return;
            for (int f = 0; f < 8; f++) {
                if (!Dessin.dans(mx, my, fx(f) - 8, oy + 3 + 92, 16, 14)) continue;
                if (f == bonne) {
                    Dessin.note(1f + manche * 0.25f);
                    Dessin.bip(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.5f);
                    if (++manche >= 3) {
                        parfait = fautes == 0;
                        terminer(true);
                    } else {
                        pause = 20;
                    }
                } else {
                    faute();
                }
                return;
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int ax = ox + 4, ay = oy + 3;
            cadre(g, ax, ay, flash > 0);
            g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
            int[] ys = {ay + 8, ay + 36, ay + 64, ay + 92};
            for (int l = 0; l < 3; l++) {
                int noeuds = 1 << l;
                for (int n = 0; n < noeuds; n++) {
                    double x = ax + L * (n + 0.5) / noeuds;
                    for (int cote = 0; cote < 2; cote++) {
                        double xf = ax + L * (n * 2 + cote + 0.5) / (noeuds * 2);
                        Dessin.segment(g, x, ys[l], xf, ys[l + 1], 2, 0xFF6B7C8C);
                        icone(g, (int) ((x + xf) / 2), (ys[l] + ys[l + 1]) / 2, ordre[l], cote == 0 ? gauche[l] : 1 - gauche[l]);
                    }
                    g.fill((int) x - 2, ys[l] - 2, (int) x + 2, ys[l] + 2, Dessin.TEXTE);
                }
            }
            for (int f = 0; f < 8; f++) {
                boolean survol = pause == 0 && !fini && Dessin.dans(mx, my, fx(f) - 8, ay + 92, 16, 14);
                boolean montre = (pause > 0 || fini) && f == bonne;
                g.fill(fx(f) - 8, ay + 92, fx(f) + 8, ay + 106, montre ? Dessin.VERT : survol ? Dessin.JAUNE : Dessin.BORD);
                g.fill(fx(f) - 7, ay + 93, fx(f) + 7, ay + 105, 0xFF34475C);
                Dessin.centre(g, String.valueOf(f + 1), fx(f), ay + 95, Dessin.TEXTE);
            }
            // L'échantillon : une forme, une couleur, une taille.
            int tx = ox + 172, r = valeurs[2] == 0 ? 11 : 6, c = valeurs[1] == 0 ? 0xFFE8543A : 0xFF4A90D9, sx = tx + 80, sy = oy + 42;
            texte(g, T.f("lineage") + Math.min(3, manche + 1) + " / 3", tx, oy + 8, Dessin.TEXTE);
            texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 20, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
            texte(g, T.f("sample"), tx, oy + 38, Dessin.JAUNE);
            g.fill(sx - 14, sy - 14, sx + 14, sy + 14, Dessin.BORD);
            if (valeurs[0] == 0) disque(g, sx, sy, r, c);
            else g.fill(sx - r, sy - r, sx + r, sy + r, c);
            Dessin.paragraphe(g, T.f("at_each_node_take_the"), tx, oy + 60, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
        }

        @Override
        String consigne() {
            return T.f("start_at_the_top_at");
        }
    }

    // ---------- Dosage : ajouter le réactif jusqu'au virage, sans le dépasser ----------

    static final class Dosage extends Jeu {
        static final ResourceLocation BURETTE = Dessin.texture("burette"), FIOLE = Dessin.texture("erlenmeyer"), LIQUIDE = Dessin.texture("erlenmeyer_liquide");
        final Random h;
        int virage, gouttes, attente, manche, fautes, temps, duree, pause, flash;
        boolean vire;
        String message = "";

        Dosage(Random h) {
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
            Dessin.bip(SoundEvents.NOTE_BLOCK_PLING.value(), (float) (0.6 + 1.2 * Math.max(0, 1 - (virage - gouttes) / 30.0)), 0.5f);
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

    // ---------- Empreinte dentaire : glisser la réglette de moulage sous la morsure, puis la presser pour voir si elle s'emboîte ----------

    static final class Morsure extends Jeu {
        // La morsure a 7 dents ; la réglette en a 13 et peut prendre 7 positions. Une seule épouse la morsure.
        static final int N = 7, M = 13, MAX = 6, U = 7, COL = 12, REPOS = 30;
        final Random h;
        final int[] morsure = new int[N], reglette = new int[M];
        int position, manche, fautes, temps, duree, flash, pause, montee = -1;
        boolean emboite;

        Morsure(Random h) {
            this.h = h;
            nouvelle();
        }

        void nouvelle() {
            int bonne;
            do {
                for (int i = 0; i < N; i++) morsure[i] = 1 + h.nextInt(5);
                for (int j = 0; j < M; j++) reglette[j] = 1 + h.nextInt(5);
                bonne = h.nextInt(M - N + 1);
                for (int i = 0; i < N; i++) reglette[bonne + i] = MAX - morsure[i];
            } while (solutions() != 1);
            do {
                position = h.nextInt(M - N + 1);
            } while (position == bonne);
            duree = temps = 420 - manche * 80;
            montee = -1;
            emboite = false;
        }

        // Espace entre la dent i de la morsure et la dent de la réglette placée dessous (0 = elles se touchent déjà au repos).
        int jeu(int i, int pos) {
            return REPOS + (MAX - morsure[i] - reglette[pos + i]) * U;
        }

        // De combien la réglette peut monter avant de buter.
        int course(int pos) {
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < N; i++) min = Math.min(min, jeu(i, pos));
            return min;
        }

        boolean epouse(int pos) {
            for (int i = 0; i < N; i++) {
                if (jeu(i, pos) != REPOS) return false;
            }
            return true;
        }

        int solutions() {
            int n = 0;
            for (int pos = 0; pos <= M - N; pos++) n += epouse(pos) ? 1 : 0;
            return n;
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            if (montee >= 0) {
                // La réglette monte jusqu'à buter, puis on regarde si toutes les dents sont en contact.
                montee++;
                if (montee == 8) {
                    if (epouse(position)) {
                        emboite = true;
                        Dessin.bip(SoundEvents.STONE_PLACE, 0.6f, 1f);
                        Dessin.note(1f + manche * 0.3f);
                        if (++manche >= 2) {
                            parfait = fautes == 0;
                            terminer(true);
                        } else {
                            pause = 34;
                        }
                    } else {
                        flash = 10;
                        Dessin.bip(SoundEvents.STONE_HIT, 0.6f, 1f);
                        Dessin.note(0.5f);
                        if (++fautes >= 2) terminer(false);
                    }
                } else if (montee >= 22 && !emboite) {
                    montee = -1; // elle retombe : on peut réessayer ailleurs
                }
                return;
            }
            if (pause > 0) {
                if (--pause == 0 && !fini) nouvelle();
                return;
            }
            if (--temps <= 0) {
                flash = 8;
                Dessin.note(0.5f);
                if (++fautes >= 2) terminer(false);
                else pause = 24;
            } else {
                presser(temps, duree);
            }
        }

        void glisser(int sens) {
            int nouvelle = Math.max(0, Math.min(M - N, position + sens));
            if (nouvelle == position) return;
            position = nouvelle;
            Dessin.bip(SoundEvents.STONE_HIT, 0.9f + position * 0.05f, 0.6f);
        }

        boolean libre() {
            return pause == 0 && montee < 0 && !fini;
        }

        @Override
        void clic(double mx, double my) {
            if (!libre()) return;
            int tx = ox + 172;
            if (Dessin.dans(mx, my, tx, oy + 40, 50, 18)) glisser(1);
            else if (Dessin.dans(mx, my, tx + 54, oy + 40, 50, 18)) glisser(-1);
            else if (Dessin.dans(mx, my, tx, oy + 66, 104, 22)) montee = 0;
        }

        @Override
        boolean touche(int touche) {
            if (!libre()) return false;
            if (touche == 263) glisser(1);
            else if (touche == 262) glisser(-1);
            else if (touche == 32 || touche == 257) montee = 0;
            else return false;
            return true;
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int ax = ox + 4, ay = oy + 3;
            cadre(g, ax, ay, flash > 0);
            g.fill(ax, ay, ax + L, ay + H, 0xFF1D2733);
            int course = course(position);
            int leve = montee < 0 ? 0 : Math.min(course, montee * 5);
            boolean bute = montee >= 8 && !emboite;
            int x0 = ax + 2 + 3 * COL, haut = ay + 6, base = haut + MAX * U + REPOS + 6 - leve;
            // La morsure : une gencive en haut, des dents qui pendent.
            g.fill(x0 - 4, haut - 4, x0 + N * COL + 3, haut, 0xFFB5746A);
            for (int i = 0; i < N; i++) {
                int x = x0 + i * COL, bas = haut + morsure[i] * U;
                boolean contact = bute && jeu(i, position) == course;
                g.fill(x, haut, x + COL - 1, bas, contact ? Dessin.ALERTE : emboite ? 0xFFBFE8C8 : 0xFFEDE6D2);
                g.fill(x, bas - 2, x + COL - 1, bas, contact ? 0xFF8E2B26 : 0xFFC9BFA4);
            }
            // La réglette de moulage : treize dents, dont sept seulement se trouvent sous la morsure.
            g.fill(ax + 2, base, ax + 2 + M * COL - 1, base + 6, 0xFF7C8FA3);
            for (int j = 0; j < M; j++) {
                int ecran = j - position + 3;
                if (ecran < 0 || ecran >= M) continue;
                int x = ax + 2 + ecran * COL, sommet = base - reglette[j] * U;
                boolean dessous = ecran >= 3 && ecran < 3 + N;
                boolean contact = bute && dessous && jeu(ecran - 3, position) == course;
                g.fill(x, sommet, x + COL - 1, base, contact ? Dessin.ALERTE : !dessous ? 0xFF55606C : emboite ? 0xFF8FD8A0 : 0xFFE9A15B);
                g.fill(x, sommet, x + COL - 1, sommet + 2, contact ? 0xFFFFB0A0 : dessous ? 0xFFFFD9A8 : 0xFF6E7A88);
            }
            // Repères : la partie de la réglette qui sera pressée contre la morsure.
            g.fill(x0 - 2, ay + H - 6, x0 + N * COL + 1, ay + H - 4, Dessin.JAUNE);
            int tx = ox + 172;
            texte(g, T.f("bite") + Math.min(2, manche + 1) + " / 2", tx, oy + 5, Dessin.TEXTE);
            texte(g, T.f("mistakes") + fautes + " / 2", tx, oy + 16, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS);
            texte(g, emboite ? T.f("it_fits") : bute ? T.f("red_teeth_are_blocking") : T.f("slide_the_strip"), tx, oy + 29, emboite ? Dessin.VERT : bute ? Dessin.ALERTE : Dessin.JAUNE);
            boolean libre = libre();
            Dessin.bouton(g, tx, oy + 40, 50, 18, "<", libre && Dessin.dans(mx, my, tx, oy + 40, 50, 18), libre && position < M - N, 0xFF7C8FA3);
            Dessin.bouton(g, tx + 54, oy + 40, 50, 18, ">", libre && Dessin.dans(mx, my, tx + 54, oy + 40, 50, 18), libre && position > 0, 0xFF7C8FA3);
            Dessin.bouton(g, tx, oy + 66, 104, 22, T.f("press"), libre && Dessin.dans(mx, my, tx, oy + 66, 104, 22), libre, Dessin.VERT);
            jaugeTemps(g, tx, oy + 104, 100, pause > 0 ? 0 : temps / (double) duree);
        }

        @Override
        String consigne() {
            return T.f("slide_the_orange_strip_buttons");
        }
    }

    // =====================================================================================================
    // GESTES PROPRES À UN TYPE D'ENQUÊTE
    // =====================================================================================================

    // ---------- Apnée (enquête aquatique) : remplir la fiole sur la trace que le courant déplace, avant de manquer d'air ----------

    static final class Apnee extends Jeu {
        static final ResourceLocation FOND = Dessin.texture("fond_eau");
        final double p, q;
        double air = 100, fiole;
        int tic;
        boolean dessus;
        double cx, cy;

        Apnee(Random h) {
            p = h.nextDouble() * 6;
            q = h.nextDouble() * 6;
        }

        double tx() {
            double v = 1 + fiole / 120;
            return 80 + 52 * Math.sin(tic * 0.03 * v + p) + 8 * Math.sin(tic * 0.11);
        }

        double ty() {
            double v = 1 + fiole / 120;
            return 62 + 30 * Math.sin(tic * 0.047 * v + q);
        }

        @Override
        void tick() {
            tic++;
            air -= 0.22;
            dessus = appui && Math.hypot(cx - ox - 4 - tx(), cy - oy - 3 - ty()) < 12;
            if (dessus) {
                fiole += 1;
                if (tic % 12 == 0) Dessin.bip(SoundEvents.BOTTLE_FILL, (float) (0.8 + fiole / 120), 0.6f);
            } else {
                fiole = Math.max(0, fiole - 0.15);
            }
            if (tic % 14 == 0) Dessin.bip(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.8f + (tic % 5) * 0.1f, 0.35f);
            // Le cœur s'emballe quand l'air vient à manquer.
            if (air < 35 && tic % (air < 15 ? 8 : 14) == 0) Dessin.bip(SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.9f);
            if (fiole >= 100) {
                parfait = air >= 40;
                terminer(true);
            } else if (air <= 0) {
                terminer(false);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cx = mx;
            cy = my;
            cadre(g, bx, by, air < 15 && (tic / 4) % 2 == 0);
            Dessin.image(g, FOND, bx, by, L, H);
            for (int i = 0; i < 9; i++) {
                int x = bx + 8 + (i * 37) % 146, y = by + H - 4 - (tic * (1 + i % 3) + i * 29) % (H - 8);
                g.fill(x, y, x + 2, y + 2, 0x90DDF4FF);
            }
            int x = bx + (int) tx(), y = by + (int) ty();
            Dessin.cercle(g, x, y, 12, 1, dessus ? Dessin.VERT : 0xC0FFFFFF);
            disque(g, x, y, 4, dessus ? 0xFFB8FFD0 : 0xFF7FE0D0);
            if (!fini && Dessin.dans(mx, my, bx, by, L, H)) Dessin.cercle(g, mx, my, 5, 2, appui ? Dessin.JAUNE : Dessin.TEXTE);
            int tx = ox + 172;
            texte(g, T.f("air"), tx, oy + 10, air < 35 ? Dessin.ALERTE : 0xFF8FC8F0);
            jauge(g, tx, oy + 21, 100, air / 100, air < 35 ? Dessin.ALERTE : 0xFF5BB8F0);
            texte(g, T.f("vial"), tx, oy + 36, Dessin.JAUNE);
            jauge(g, tx, oy + 47, 100, fiole / 100, Dessin.VERT);
            Dessin.paragraphe(g, T.f("stay_on_the_trace_40"), tx, oy + 60, 104, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_on_the_2");
        }
    }

    // ---------- Dégel (enquête givre) : chauffer l'échantillon gelé en restant dans la bonne plage de température ----------

    static final class Degel extends Jeu {
        static final ResourceLocation GLACON = Dessin.texture("glacon");
        static final int DUREE = 580;
        final double p;
        double temperature = 20, chauffe, degel;
        int temps = DUREE, fautes, flash, tic, paliers;

        Degel(Random h) {
            p = h.nextDouble() * 6;
        }

        double bas() {
            return 44 + 13 * Math.sin(tic * 0.022 + p);
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
            if (appui) {
                chauffe = Math.min(30, chauffe + 1);
                temperature += 0.5 + chauffe * 0.05;
                if (tic % 9 == 0) Dessin.bip(SoundEvents.FIRE_AMBIENT, 1.4f, 0.5f);
            } else {
                chauffe = 0;
                temperature = Math.max(0, temperature - 0.8);
            }
            if (temperature >= 90) {
                // Trop chaud : l'échantillon cuit.
                temperature = 35;
                chauffe = 0;
                flash = 8;
                Dessin.bip(SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
                if (++fautes >= 3) terminer(false);
            }
            if (temperature >= bas() && temperature <= bas() + 18) degel += 0.55;
            int palier = (int) (degel / 34);
            if (palier > paliers) {
                paliers = palier;
                Dessin.bip(SoundEvents.GLASS_BREAK, 1.7f, 0.4f);
            }
            if (degel >= 100 && !fini) {
                parfait = fautes == 0;
                terminer(true);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cadre(g, bx, by, flash > 0);
            g.fill(bx, by, bx + L, by + H, 0xFF1D2733);
            // Le glaçon rétrécit à mesure qu'il fond, sur sa flaque.
            int taille = (int) (64 - degel * 0.34), gx = bx + 52 - taille / 2, gy = by + 82 - taille;
            g.fill(bx + 52 - 20 - (int) (degel * 0.14), by + 82, bx + 52 + 20 + (int) (degel * 0.14), by + 85, 0xFF5BB8F0);
            Dessin.morceauAgrandi(g, GLACON, gx, gy, taille, taille, 0, 0, 48, 48, 48, 48);
            if (appui && !fini) {
                for (int i = 0; i < 5; i++) {
                    int fx = bx + 34 + i * 9, fh = 4 + (tic * 3 + i * 5) % 7;
                    g.fill(fx, by + 100 - fh, fx + 5, by + 100, i % 2 == 0 ? 0xFFF2C93A : 0xFFE8543A);
                }
            }
            g.fill(bx + 26, by + 100, bx + 80, by + 104, 0xFF46525E);
            // Le thermomètre : la plage verte se déplace lentement, la zone rouge fait cuire l'échantillon.
            int tx0 = bx + 118, haut = by + 6, h = 100;
            g.fill(tx0 - 1, haut - 1, tx0 + 15, haut + h + 1, Dessin.BORD);
            g.fill(tx0, haut, tx0 + 14, haut + h, 0xFF0F1820);
            g.fill(tx0, haut, tx0 + 14, haut + 10, 0xFF6B2420);
            int yb = haut + h - (int) bas(), yh = yb - 18;
            g.fill(tx0, yh, tx0 + 14, yb, 0xFF245C3A);
            int yt = haut + h - (int) temperature;
            g.fill(tx0 + 4, yt, tx0 + 10, haut + h, temperature >= 80 ? Dessin.ALERTE : 0xFFE9A15B);
            g.fill(tx0 - 3, yt - 1, tx0 + 17, yt + 1, Dessin.TEXTE);
            int tx = ox + 172;
            texte(g, T.f("thawed") + (int) degel + " %", tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("overheats") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            Dessin.paragraphe(g, T.f("the_ice_only_melts_in"), tx, oy + 42, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_to_heat");
        }
    }

    // ---------- Filtrage du cri (enquête dans la brume) : suivre la fréquence où le cri ressort du bruit ----------

    static final class Filtrage extends Jeu {
        static final int DUREE = 320, REQUIS = 70;
        final double p;
        final Random bruit = new Random(5);
        int temps = DUREE, tic;
        double acquis, clarte, cx;

        Filtrage(Random h) {
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
            if (tic % 5 == 0) Dessin.bip(SoundEvents.NOTE_BLOCK_BIT.value(), (float) (0.5 + clarte * 1.4), (float) (0.2 + clarte * 0.4));
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

    // ---------- Grimpe (enquête des cimes) : attraper chaque prise du bon côté, au bon moment, sans toucher aux branches mortes ----------

    static final class Grimpe extends Jeu {
        static final ResourceLocation ECORCE = Dessin.texture("ecorce");
        static final int N = 20, LIGNE = 84, MARGE = 7;
        final int[] cote = new int[N], hauteur = new int[N];
        final boolean[] morte = new boolean[N];
        // 0 = à venir, 1 = passée (attrapée, ou branche morte évitée), 2 = manquée.
        final int[] etat = new int[N];
        double avancee;
        int prochaine, fautes, flash, prises, bonnes;

        Grimpe(Random h) {
            int y = 0;
            for (int i = 0; i < N; i++) {
                cote[i] = h.nextInt(2);
                // Des écarts irréguliers : parfois deux prises coup sur coup, parfois un long vide.
                y += 20 + h.nextInt(26);
                hauteur[i] = y;
                morte[i] = i > 1 && h.nextInt(5) == 0;
                if (!morte[i]) bonnes++;
            }
        }

        double y(int i) {
            return avancee - 20 - hauteur[i];
        }

        void rate() {
            etat[prochaine++] = 2;
            flash = 8;
            Dessin.bip(SoundEvents.WOOD_BREAK, 0.9f, 0.8f);
            if (++fautes >= 3) terminer(false);
        }

        void suite() {
            if (!fini && prochaine >= N) {
                parfait = fautes == 0;
                terminer(true);
            }
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            avancee += 1.7 + avancee / 330;
            if (prochaine < N && y(prochaine) > LIGNE + MARGE) {
                if (morte[prochaine]) etat[prochaine++] = 1; // une branche morte qu'on a laissée passer : c'est ce qu'il fallait
                else rate();
                suite();
            }
        }

        @Override
        void clic(double mx, double my) {
            int bx = ox + 4, by = oy + 3;
            if (prochaine >= N || !Dessin.dans(mx, my, bx, by, L, H)) return;
            int c = mx < bx + 80 ? 0 : 1;
            boolean aPortee = Math.abs(y(prochaine) - LIGNE) <= MARGE;
            if (aPortee && !morte[prochaine] && c == cote[prochaine]) {
                etat[prochaine++] = 1;
                Dessin.bip(SoundEvents.WOOD_HIT, 0.8f + prises * 0.05f, 0.9f);
                prises++;
            } else {
                rate(); // trop tôt, trop tard, du mauvais côté, ou une branche morte qui casse
            }
            suite();
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cadre(g, bx, by, flash > 0);
            Dessin.image(g, ECORCE, bx, by, L, H);
            g.fill(bx + 79, by, bx + 81, by + H, 0x60000000);
            // La zone d'atteinte : c'est là qu'il faut saisir la prise.
            g.fill(bx, by + LIGNE - MARGE, bx + L, by + LIGNE + MARGE, 0x30FFFFFF);
            g.fill(bx, by + LIGNE - MARGE, bx + L, by + LIGNE - MARGE + 1, 0xA0FFFFFF);
            g.fill(bx, by + LIGNE + MARGE - 1, bx + L, by + LIGNE + MARGE, 0xA0FFFFFF);
            for (int i = 0; i < N; i++) {
                double y = y(i);
                if (etat[i] != 0 || y < -6 || y > H + 6) continue;
                int x = bx + (cote[i] == 0 ? 40 : 120), py = by + (int) y;
                boolean pret = i == prochaine && Math.abs(y - LIGNE) <= MARGE && !morte[i];
                g.fill(x - 11, Math.max(by, py - 5), x + 11, Math.min(by + H, py + 5), pret ? Dessin.VERT : 0xFF2A1C10);
                g.fill(x - 9, Math.max(by, py - 3), x + 9, Math.min(by + H, py + 3), morte[i] ? 0xFF6B4A4A : 0xFFC89A62);
                // Une branche morte est grise et fendue.
                if (morte[i]) g.fill(x - 1, Math.max(by, py - 3), x + 1, Math.min(by + H, py + 3), 0xFF2A1C10);
            }
            int tx = ox + 172;
            texte(g, T.f("holds") + prises + " / " + bonnes, tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("slips_2") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            Dessin.paragraphe(g, T.f("grey_split_branches_are_dead"), tx, oy + 42, 104, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("click_left_or_right_on");
        }
    }

    // ---------- Rappel (enquête d'altitude) : laisser filer la corde et s'arrêter à hauteur de chaque trace ----------

    static final class Rappel extends Jeu {
        static final ResourceLocation PAROI = Dessin.texture("paroi");
        static final int DUREE = 540;
        final int[] cibles = new int[3];
        double pos = 6, vitesse, depart = 6;
        int etape, temps = DUREE, fautes, flash, tic;
        boolean attendRelache;

        Rappel(Random h) {
            for (int i = 0; i < 3; i++) cibles[i] = 30 + i * 30 + h.nextInt(9);
        }

        void faute() {
            pos = depart;
            vitesse = 0;
            attendRelache = true;
            flash = 8;
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
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
            if (!appui) attendRelache = false;
            boolean file = appui && !attendRelache;
            vitesse = file ? Math.min(2.4, vitesse + 0.05) : Math.max(0, vitesse - 0.1);
            pos += vitesse;
            if (vitesse > 0 && tic % 4 == 0) Dessin.bip(SoundEvents.CHAIN_STEP, (float) (0.7 + vitesse * 0.4), 0.6f);
            int cible = cibles[etape];
            if (pos > cible + 4) {
                faute(); // descendu trop bas : il faut remonter et recommencer ce tronçon
            } else if (vitesse == 0 && !file && Math.abs(pos - cible) <= 4) {
                depart = pos;
                Dessin.bip(SoundEvents.CHAIN_PLACE, 1.2f, 0.8f);
                Dessin.note(1f + etape * 0.25f);
                attendRelache = true;
                if (++etape >= 3) {
                    etape = 2;
                    parfait = fautes == 0;
                    terminer(true);
                }
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cadre(g, bx, by, flash > 0);
            Dessin.image(g, PAROI, bx, by, L, H);
            for (int i = 0; i < 3; i++) {
                int y = by + cibles[i] + 6;
                boolean fait = i < etape || (fini && succes);
                g.fill(bx + 92, y - 4, bx + 150, y + 4, fait ? 0x6040C060 : i == etape ? 0x60F2C93A : 0x30FFFFFF);
                g.fill(bx + 92, y, bx + 150, y + 1, fait ? Dessin.VERT : i == etape ? Dessin.JAUNE : Dessin.GRIS);
                texte(g, fait ? "✔" : String.valueOf(i + 1), bx + 152 - 8, y - 4, fait ? Dessin.VERT : Dessin.TEXTE);
            }
            int gx = bx + 80, gy = by + (int) pos + 6;
            g.fill(gx, by, gx + 1, gy - 6, 0xFFE9D8A8);
            g.fill(gx - 3, gy - 6, gx + 4, gy + 5, 0xFFE8743A);
            g.fill(gx - 2, gy - 10, gx + 3, gy - 6, 0xFFF2D2B0);
            g.fill(gx - 3, gy + 5, gx - 1, gy + 9, 0xFF3A4A5C);
            g.fill(gx + 2, gy + 5, gx + 4, gy + 9, 0xFF3A4A5C);
            int tx = ox + 172;
            texte(g, T.f("traces_reached") + (fini && succes ? 3 : etape) + " / 3", tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("too_low") + fautes + " / 3", tx, oy + 22, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS);
            texte(g, T.f("speed"), tx, oy + 40, vitesse > 1.6 ? Dessin.ALERTE : Dessin.JAUNE);
            jauge(g, tx, oy + 51, 100, vitesse / 2.4, vitesse > 1.6 ? Dessin.ALERTE : Dessin.JAUNE);
            Dessin.paragraphe(g, T.f("the_rope_takes_a_while"), tx, oy + 64, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_to_let");
        }
    }

    // ---------- Lampe UV (enquête nocturne) : balayer le sol dans le noir pour révéler les taches fluorescentes ----------

    static final class LampeUV extends Jeu {
        static final ResourceLocation NUIT = Dessin.texture("nuit");
        static final int N = 5, PORTEE = 22;
        final int[] sx = new int[N], sy = new int[N];
        final boolean[] trouve = new boolean[N];
        double batterie = 100, cx, cy;
        int trouves, flash, tic;

        LampeUV(Random h) {
            for (int i = 0; i < N; i++) {
                for (int essai = 0; essai < 100; essai++) {
                    sx[i] = 12 + h.nextInt(L - 24);
                    sy[i] = 12 + h.nextInt(H - 24);
                    boolean libre = true;
                    for (int k = 0; k < i; k++) libre &= Math.hypot(sx[k] - sx[i], sy[k] - sy[i]) > 30;
                    if (libre) break;
                }
            }
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            tic++;
            batterie -= 0.17;
            if (tic % 24 == 0) Dessin.bip(SoundEvents.BEACON_AMBIENT, 1.8f, 0.25f);
            if (batterie <= 0) terminer(false);
        }

        @Override
        void clic(double mx, double my) {
            int bx = ox + 4, by = oy + 3;
            if (!Dessin.dans(mx, my, bx, by, L, H)) return;
            for (int i = 0; i < N; i++) {
                if (trouve[i] || Math.hypot(mx - bx - sx[i], my - by - sy[i]) > 9) continue;
                trouve[i] = true;
                Dessin.note(1f + trouves * 0.2f);
                if (++trouves >= N) {
                    parfait = batterie >= 35;
                    terminer(true);
                }
                return;
            }
            // Un clic dans le vide vide la batterie.
            batterie -= 8;
            flash = 6;
            Dessin.bip(SoundEvents.DISPENSER_FAIL, 1.2f, 0.6f);
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cx = mx;
            cy = my;
            cadre(g, bx, by, flash > 0);
            g.setColor(0.45f, 0.45f, 0.6f, 1f);
            Dessin.image(g, NUIT, bx, by, L, H);
            g.setColor(1f, 1f, 1f, 1f);
            boolean dedans = !fini && Dessin.dans(mx, my, bx, by, L, H);
            if (dedans) {
                // Le faisceau : un halo violet, découpé au bord de l'aire.
                for (int dy = -PORTEE; dy <= PORTEE; dy++) {
                    int dx = (int) Math.sqrt(PORTEE * PORTEE - dy * dy), y = my + dy;
                    if (y < by || y >= by + H) continue;
                    g.fill(Math.max(bx, mx - dx), y, Math.min(bx + L, mx + dx), y + 1, 0x389060FF);
                }
            }
            for (int i = 0; i < N; i++) {
                int x = bx + sx[i], y = by + sy[i];
                if (trouve[i]) {
                    disque(g, x, y, 4, 0xFF6CCB5F);
                    Dessin.cercle(g, x, y, 7, 1, Dessin.VERT);
                } else if (dedans && Math.hypot(mx - x, my - y) < PORTEE) {
                    boolean pulse = (tic / 3) % 2 == 0;
                    disque(g, x, y, pulse ? 4 : 3, 0xFFB8FF5A);
                    g.fill(x - 6, y + 3, x - 4, y + 5, 0xFFE0FF90);
                    g.fill(x + 4, y - 5, x + 6, y - 3, 0xFFE0FF90);
                }
            }
            int tx = ox + 172;
            texte(g, T.f("stains_found") + trouves + " / " + N, tx, oy + 10, Dessin.TEXTE);
            texte(g, T.f("battery"), tx, oy + 28, batterie < 30 ? Dessin.ALERTE : 0xFFB79CFF);
            jauge(g, tx, oy + 39, 100, batterie / 100, batterie < 30 ? Dessin.ALERTE : 0xFF9060FF);
            Dessin.paragraphe(g, T.f("stains_only_glow_under_the"), tx, oy + 54, 104, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("sweep_the_ground_with_the");
        }
    }
}
