package fr.cobblescout.traque.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

// Les analyses ajoutées au séquenceur : électrophorèse, titrage, cycles PCR, comptage de colonies.
final class JeuxLabo {
    private JeuxLabo() {}

    // ---------- Électrophorèse : retrouver, parmi des pistes presque identiques, celle de l'échantillon ----------

    static final class Gel extends Jeu {
        static final ResourceLocation BAC = Dessin.texture("gel");
        // Une bande = sa position (FENTES crans de 5 px), son épaisseur (fine ou large) et son intensité (pâle ou vive).
        static final int FENTES = 14, PAS = 30, LARGEUR = 22;
        final Random h;
        int[][] pistes;          // pistes[0] = l'échantillon ; chaque piste : FENTES valeurs (0 = rien, sinon bit 0 = large, bit 1 = vive, +4)
        int bonne, manche, erreurs, temps, duree, pause, choisie = -1;
        boolean dernierOk;

        Gel(Random h) {
            this.h = h;
            nouvelle();
        }

        int bande(boolean large, boolean vive) {
            return 4 | (large ? 1 : 0) | (vive ? 2 : 0);
        }

        // Une piste voisine : une seule différence discrète avec le modèle (bande décalée d'un cran, épaisseur ou
        // intensité changée, bande pâle en plus ou en moins).
        int[] variante(int[] modele) {
            for (int essai = 0; essai < 200; essai++) {
                int[] p = modele.clone();
                int i = h.nextInt(FENTES);
                switch (h.nextInt(manche == 0 ? 3 : 5)) {
                    case 0 -> {
                        int vers = i + (h.nextBoolean() ? 1 : -1);
                        if (p[i] == 0 || vers < 0 || vers >= FENTES || p[vers] != 0) continue;
                        p[vers] = p[i];
                        p[i] = 0;
                    }
                    case 1 -> {
                        if (p[i] == 0) continue;
                        p[i] ^= 1;
                    }
                    case 2 -> {
                        if (p[i] == 0) continue;
                        p[i] ^= 2;
                    }
                    case 3 -> {
                        if (p[i] != 0) continue;
                        p[i] = bande(false, false);
                    }
                    default -> {
                        if (p[i] == 0 || (p[i] & 2) != 0) continue;
                        p[i] = 0;
                    }
                }
                return p;
            }
            return modele.clone();
        }

        void nouvelle() {
            int nombre = manche == 0 ? 5 : 6;
            pistes = new int[nombre + 1][];
            int[] modele = new int[FENTES];
            for (int n = 0; n < 6 + manche; ) {
                int i = h.nextInt(FENTES);
                if (modele[i] != 0) continue;
                modele[i] = bande(h.nextInt(3) == 0, h.nextBoolean());
                n++;
            }
            pistes[0] = modele;
            bonne = 1 + h.nextInt(nombre);
            for (int k = 1; k <= nombre; k++) {
                if (k == bonne) {
                    pistes[k] = modele.clone();
                    continue;
                }
                for (int essai = 0; essai < 100; essai++) {
                    int[] p = variante(modele);
                    boolean nouveau = !java.util.Arrays.equals(p, modele);
                    for (int j = 1; j < k; j++) nouveau &= pistes[j] == null || !java.util.Arrays.equals(p, pistes[j]);
                    pistes[k] = p;
                    if (nouveau) break;
                }
            }
            duree = temps = 320 - manche * 30;
            choisie = -1;
        }

        void juger(int piste) {
            choisie = piste;
            dernierOk = piste == bonne;
            pause = dernierOk ? 16 : 40;
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
        }

        @Override
        void tick() {
            if (pause > 0) {
                if (--pause == 0 && !fini) nouvelle();
                return;
            }
            if (--temps <= 0) juger(-1);
            else presser(temps, duree);
        }

        int px(int k) {
            return ox + 16 + k * PAS + (k > 0 ? 6 : 0);
        }

        @Override
        void clic(double mx, double my) {
            if (pause > 0) return;
            for (int k = 1; k < pistes.length; k++) {
                if (Dessin.dans(mx, my, px(k), oy + 20, LARGEUR, 78)) {
                    juger(k);
                    return;
                }
            }
        }

        void piste(GuiGraphics g, int k, int cadre) {
            int x = px(k);
            if (cadre != 0) {
                g.fill(x - 2, oy + 18, x + LARGEUR + 2, oy + 19, cadre);
                g.fill(x - 2, oy + 99, x + LARGEUR + 2, oy + 100, cadre);
                g.fill(x - 2, oy + 18, x - 1, oy + 100, cadre);
                g.fill(x + LARGEUR + 1, oy + 18, x + LARGEUR + 2, oy + 100, cadre);
            }
            g.fill(x, oy + 20, x + LARGEUR, oy + 98, 0x50081420);
            g.fill(x + 4, oy + 21, x + LARGEUR - 4, oy + 24, 0xFF0A1622);
            for (int i = 0; i < FENTES; i++) {
                int b = pistes[k][i];
                if (b == 0) continue;
                boolean large = (b & 1) != 0, vive = (b & 2) != 0;
                int y = oy + 27 + i * 5, ep = large ? 4 : 2, couleur = vive ? 0xFF8CFAEC : 0xFF3C9C98;
                if (vive) g.fill(x + 2, y - 1, x + LARGEUR - 2, y + ep + 1, 0x7030C8C0);
                g.fill(x + 3, y, x + LARGEUR - 3, y + ep, couleur);
                // Traînée de migration sous la bande.
                g.fill(x + 5, y + ep, x + LARGEUR - 5, y + ep + 1, 0x4030C8C0);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            jaugeTemps(g, ox + 6, oy + 3, 232, pause > 0 ? 0 : temps / (double) duree);
            Dessin.image(g, BAC, ox + 2, oy + 11, 240, 104);
            piste(g, 0, Dessin.JAUNE);
            Dessin.centre(g, T.f("smp"), px(0) + LARGEUR / 2, oy + 103, Dessin.JAUNE);
            for (int k = 1; k < pistes.length; k++) {
                boolean survol = pause == 0 && !fini && Dessin.dans(mx, my, px(k), oy + 20, LARGEUR, 78);
                int cadre = pause > 0 || fini ? (k == bonne ? Dessin.VERT : k == choisie ? Dessin.ALERTE : 0) : survol ? Dessin.TEXTE : 0;
                piste(g, k, cadre);
                Dessin.centre(g, String.valueOf(k), px(k) + LARGEUR / 2, oy + 103, cadre == 0 ? Dessin.GRIS : cadre);
            }
            int tx = ox + 246;
            g.drawString(Dessin.police(), T.f("gel"), tx, oy + 16, Dessin.GRIS, false);
            g.drawString(Dessin.police(), Math.min(3, manche + (pause > 0 || fini ? 0 : 1)) + " / 3", tx, oy + 27, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("err"), tx, oy + 46, Dessin.GRIS, false);
            g.drawString(Dessin.police(), erreurs + " / 2", tx, oy + 57, erreurs > 0 ? Dessin.ALERTE : Dessin.TEXTE, false);
            if (pause > 0 || fini) g.drawString(Dessin.police(), dernierOk ? T.f("yes") : T.f("no"), tx, oy + 78, dernierOk ? Dessin.VERT : Dessin.ALERTE, false);
        }

        @Override
        String consigne() {
            return T.f("click_the_lane_identical_to");
        }
    }

    // ---------- Colorimétrie : mémoriser une teinte, puis la recréer en mélangeant les réactifs ----------

    static final class Titrage extends Jeu {
        static final ResourceLocation FIOLE = Dessin.texture("erlenmeyer"), LIQUIDE = Dessin.texture("erlenmeyer_liquide");
        static final String[] NOMS = {T.f("red"), T.f("yellow"), T.f("blue"), T.f("white")};
        static final int[] REACTIFS = {0xFFE8433A, 0xFFF2C93A, 0xFF3A78D8, 0xFFF4F4F0};
        static final int VIDE = 0xFFB9C6D2, MAX = 10, SEUIL = 85, MONTRE = 90;
        final Random h;
        final int[] gouttes = new int[4];
        int cible, manche, echecs, visible, pause, chute, dernier = -1, score, total;

        Titrage(Random h) {
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
                g.drawString(Dessin.police(), T.f("memorise"), cx, oy + 8, Dessin.JAUNE, false);
                pastille(g, cx, oy + 20, 64, 52, cible);
                jaugeTemps(g, cx, oy + 78, 64, visible / (double) MONTRE);
            } else if (pause > 0 || fini) {
                boolean ok = score >= SEUIL;
                g.drawString(Dessin.police(), T.f("target"), cx, oy + 8, Dessin.GRIS, false);
                pastille(g, cx, oy + 19, 64, 22, cible);
                g.drawString(Dessin.police(), T.f("your_mix"), cx, oy + 46, Dessin.GRIS, false);
                pastille(g, cx, oy + 57, 64, 22, melange(gouttes));
                g.drawString(Dessin.police(), score + " % " + (ok ? T.f("passed") : T.f("failed")), cx, oy + 86, ok ? Dessin.VERT : Dessin.ALERTE, false);
                g.drawString(Dessin.police(), T.f("needed") + SEUIL + " %", cx, oy + 97, Dessin.SOMBRE, false);
            } else {
                g.drawString(Dessin.police(), T.f("target"), cx, oy + 8, Dessin.GRIS, false);
                pastille(g, cx, oy + 19, 64, 22, 0xFF1A2633);
                // Aide aux couleurs : la ressemblance du mélange en cours s'affiche à la place du point d'interrogation.
                boolean aide = TraqueClient.aideCouleurs && nombre() > 0;
                Dessin.centre(g, aide ? ressemblance(melange(gouttes), cible) + " %" : "?", cx + 32, oy + 26, aide ? Dessin.TEXTE : Dessin.SOMBRE);
                g.drawString(Dessin.police(), T.f("your_mix"), cx, oy + 46, Dessin.GRIS, false);
                pastille(g, cx, oy + 57, 64, 22, melange(gouttes));
                // La recette en cours, goutte par goutte.
                int k = 0;
                for (int i = 0; i < 4; i++) {
                    for (int n = 0; n < gouttes[i]; n++, k++) g.fill(cx + k * 6, oy + 86, cx + k * 6 + 5, oy + 93, REACTIFS[i]);
                }
                g.drawString(Dessin.police(), nombre() + " / " + MAX + T.f("drops"), cx, oy + 97, Dessin.SOMBRE, false);
            }

            int tx = ox + 152;
            g.drawString(Dessin.police(), T.f("flask") + Math.min(3, manche + (pause > 0 || fini ? 0 : 1)) + " / 3", tx, oy + 8, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("fails") + echecs + " / 2", tx, oy + 20, echecs > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
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

    // ---------- Cycles PCR : répéter la suite de températures ----------

    static final class Pcr extends Jeu {
        static final String[] PALIERS = {"95°", "72°", "55°", "4°"};
        static final int[] COULEURS = {0xFFE8543A, 0xFFF2C93A, 0xFF4A90D9, 0xFF6CCB5F};
        // Une note par palier, de la plus aiguë (95°) à la plus grave (4°) : la suite se retient aussi à l'oreille.
        static final float[] NOTES = {1.5f, 1.19f, 0.94f, 0.75f};

        static void sonner(int palier) {
            Dessin.bip(SoundEvents.NOTE_BLOCK_PLING.value(), NOTES[palier], 0.8f);
        }
        final Random h;
        int[] suite;
        int manche, erreurs, montre, saisie, allume = -1, duree;

        Pcr(Random h) {
            this.h = h;
            nouvelle();
        }

        void nouvelle() {
            suite = new int[3 + manche];
            for (int i = 0; i < suite.length; i++) suite[i] = h.nextInt(4);
            montre = -16;
            saisie = -1;
        }

        @Override
        void tick() {
            if (duree > 0 && --duree == 0) allume = -1;
            if (saisie >= 0) return;
            montre++;
            if (montre >= 0 && montre % 13 == 0) {
                int i = montre / 13;
                if (i >= suite.length) {
                    saisie = 0;
                    return;
                }
                allume = suite[i];
                duree = 9;
                sonner(suite[i]);
            }
        }

        @Override
        void clic(double mx, double my) {
            if (saisie < 0) return;
            for (int b = 0; b < 4; b++) {
                if (!Dessin.dans(mx, my, ox + 12 + (b % 2) * 70, oy + 8 + (b / 2) * 52, 64, 46)) continue;
                allume = b;
                duree = 5;
                if (b != suite[saisie]) {
                    Dessin.bip(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.9f);
                    if (++erreurs >= 2) terminer(false);
                    else nouvelle();
                    return;
                }
                sonner(b);
                if (++saisie >= suite.length) {
                    Dessin.bip(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.6f); // cycle réussi
                    if (++manche >= 3) {
                        parfait = erreurs == 0;
                        terminer(true);
                    } else nouvelle();
                }
                return;
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            for (int b = 0; b < 4; b++) {
                int x = ox + 12 + (b % 2) * 70, y = oy + 8 + (b / 2) * 52;
                boolean vif = allume == b;
                boolean survol = saisie >= 0 && !fini && Dessin.dans(mx, my, x, y, 64, 46);
                Dessin.bouton(g, x, y, 64, 46, PALIERS[b], false, true, vif ? Dessin.assombrir(COULEURS[b], 1.25) : Dessin.assombrir(COULEURS[b], survol ? 0.75 : 0.5));
            }
            int tx = ox + 160;
            g.drawString(Dessin.police(), T.f("cycle") + Math.min(3, manche + 1) + " / 3", tx, oy + 12, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("mistakes") + erreurs + " / 2", tx, oy + 24, erreurs > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
            g.drawString(Dessin.police(), saisie < 0 ? T.f("watch_the_program") : T.f("your_turn_repeat_it"), tx, oy + 44, saisie < 0 ? Dessin.JAUNE : Dessin.ACCENT, false);
            for (int i = 0; i < suite.length; i++) {
                g.fill(tx + i * 12, oy + 60, tx + i * 12 + 9, oy + 69, Dessin.BORD);
                g.fill(tx + i * 12 + 1, oy + 61, tx + i * 12 + 8, oy + 68, saisie > i ? COULEURS[suite[i]] : Dessin.ENCART_CLAIR);
            }
            Dessin.paragraphe(g, T.f("the_program_gets_one_step"), tx, oy + 80, 112, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("watch_the_sequence_of_temperature");
        }
    }

    // ---------- Comptage de colonies : compter d'un coup d'œil, puis répondre ----------

    static final class Comptage extends Jeu {
        static final ResourceLocation PETRI = Dessin.texture("petri");
        static final int[] TEINTES = {0xFFFFFFFF, 0xFFE07A5F, 0xFF7FB069, 0xFF6C8EBF};
        final Random h;
        int[] cx, cy, taille, teinte, choix = new int[4];
        int nombre, manche, erreurs, visible, pause;
        boolean dernierOk;

        Comptage(Random h) {
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
            g.drawString(Dessin.police(), T.f("dish") + Math.min(3, manche + 1) + T.f("3_mistakes") + erreurs + " / 2", tx, oy + 10, erreurs > 0 ? Dessin.ALERTE : Dessin.TEXTE, false);
            String etat = pause > 0 ? (dernierOk ? T.f("correct") + nombre + T.f("colonies") : T.f("wrong_there_were") + nombre) : visible > 0 ? T.f("count_the_colonies") : T.f("how_many_were_there");
            g.drawString(Dessin.police(), etat, tx, oy + 26, pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.JAUNE, false);
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
}
