package fr.cobblescout.traque.client;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

// Les gestes de prélèvement sur le terrain : un par sorte de trace.
final class JeuxTerrain {
    private JeuxTerrain() {}

    // ---------- Cristaux résiduels (résidu élémentaire) : mémoriser le gisement, prélever les stables, éviter les instables ----------

    static final class Cristaux extends Jeu {
        static final ResourceLocation TERRE = Dessin.texture("terre"), CRISTAL = Dessin.texture("cristal");
        static final int COLS = 7, RANGS = 5, CASE = 20, TERNE = 0xFF566270, STABLE = 0xFF6FF0E0, INSTABLE = 0xFFF0604E;
        final Random h;
        final int[] sorte = new int[COLS * RANGS]; // 0 inerte, 1 stable (à prélever), 2 instable (à éviter)
        final int[] etat = new int[COLS * RANGS];  // 0 intact, 1 prélevé, 2 touché à tort
        int manche, montre, duree, restants, fautes, pause, flash, ticks;

        Cristaux(Random h) {
            this.h = h;
            nouveau();
        }

        void nouveau() {
            sonMontre();
            java.util.Arrays.fill(sorte, 0);
            java.util.Arrays.fill(etat, 0);
            restants = 5 + manche;
            for (int k = 0; k < restants + 1 + manche; ) {
                int i = h.nextInt(sorte.length);
                if (sorte[i] != 0) continue;
                sorte[i] = k < restants ? 1 : 2;
                k++;
            }
            duree = montre = 58 - manche * 6;
        }

        @Override
        void tick() {
            ticks++;
            if (flash > 0) flash--;
            if (montre > 0 && --montre == 0) sonCache();
            if (pause > 0 && --pause == 0 && !fini) nouveau();
        }

        int cx(int i) {
            return ox + 14 + (i % COLS) * CASE;
        }

        int cy(int i) {
            return oy + 9 + (i / COLS) * CASE;
        }

        @Override
        void clic(double mx, double my) {
            if (montre > 0 || pause > 0) return;
            for (int i = 0; i < sorte.length; i++) {
                if (etat[i] != 0 || !Dessin.dans(mx, my, cx(i), cy(i), CASE, CASE)) continue;
                if (sorte[i] == 1) {
                    etat[i] = 1;
                    Dessin.son(1.1f + (5 + manche - restants) * 0.08f);
                    if (--restants == 0) {
                        Dessin.note(1f + manche * 0.25f);
                        if (++manche >= 3) {
                            parfait = fautes == 0;
                            terminer(true);
                        } else pause = 26;
                    }
                } else {
                    etat[i] = 2;
                    flash = 8;
                    Dessin.note(0.5f);
                    if (++fautes >= 3) terminer(false);
                }
                return;
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
            Dessin.image(g, TERRE, bx, by, 160, 112);
            boolean revele = montre > 0 || pause > 0 || fini;
            boolean pulse = (ticks / 4) % 2 == 0;
            for (int i = 0; i < sorte.length; i++) {
                int x = cx(i), y = cy(i);
                int teinte = TERNE;
                boolean brille = false;
                if (etat[i] == 1 || (revele && sorte[i] == 1)) {
                    teinte = STABLE;
                    brille = true;
                } else if ((etat[i] == 2 && sorte[i] == 2) || (revele && sorte[i] == 2)) {
                    teinte = INSTABLE;
                    brille = true;
                } else if (etat[i] == 2) {
                    teinte = 0xFF2E3640; // cristal inerte abîmé
                } else if (montre == 0 && pause == 0 && !fini && Dessin.dans(mx, my, x, y, CASE, CASE)) {
                    teinte = 0xFF8FA0B2;
                }
                if (brille) {
                    int halo = (teinte & 0xFFFFFF) | (pulse ? 0x50000000 : 0x38000000);
                    g.fill(x + 1, y + 3, x + CASE - 1, y + CASE - 3, halo);
                    g.fill(x + 3, y + 1, x + CASE - 3, y + CASE - 1, halo);
                }
                Dessin.teinte(g, teinte);
                Dessin.image(g, CRISTAL, x + 2, y + 2, 16, 16);
                g.setColor(1f, 1f, 1f, 1f);
            }
            int tx = ox + 172;
            g.drawString(Dessin.police(), T.f("deposit") + Math.min(3, manche + 1) + " / 3", tx, oy + 8, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), montre > 0 ? T.f("watch_closely") : pause > 0 ? T.f("deposit_sampled") : T.f("your_turn_sample"), tx, oy + 20,
                    montre > 0 ? Dessin.JAUNE : pause > 0 ? Dessin.VERT : Dessin.ACCENT, false);
            if (montre > 0) jaugeTemps(g, tx, oy + 32, 100, montre / (double) duree);
            g.fill(tx, oy + 46, tx + 8, oy + 54, STABLE);
            g.drawString(Dessin.police(), T.f("stable") + restants, tx + 12, oy + 46, Dessin.TEXTE, false);
            g.fill(tx, oy + 58, tx + 8, oy + 66, INSTABLE);
            g.drawString(Dessin.police(), T.f("unstable_avoid"), tx + 12, oy + 58, Dessin.GRIS, false);
            g.drawString(Dessin.police(), T.f("faults") + fautes + " / 3", tx, oy + 76, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            Dessin.paragraphe(g, T.f("no_fault_one_extra_dose"), tx, oy + 92, 104, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("stable_teal_and_unstable_red");
        }
    }

    // ---------- Pipetage (dépôt étrange) : aspirer juste ce qu'il faut ----------

    static final class Pipette extends Jeu {
        final Random h;
        double niveau, cibleBas, cibleHaut, vitesse;
        int manche, fautes, pause, tic;
        boolean aspire, dernierOk, dansLeVert;

        Pipette(Random h) {
            this.h = h;
            nouvelle();
        }

        void nouvelle() {
            double largeur = 0.17 - manche * 0.035;
            cibleBas = 0.28 + h.nextDouble() * (0.62 - largeur);
            cibleHaut = cibleBas + largeur;
            niveau = 0;
            aspire = false;
            vitesse = 0.024 + manche * 0.009;
        }

        @Override
        void tick() {
            if (pause > 0) {
                if (--pause == 0 && !fini) nouvelle();
                return;
            }
            if (!aspire) return;
            niveau += vitesse;
            // L'aspiration s'entend : une note qui monte avec le niveau, et un tintement à l'entrée dans la zone verte.
            if (++tic % 2 == 0) Dessin.bip(SoundEvents.NOTE_BLOCK_HARP.value(), (float) (0.6 + Math.min(1, niveau) * 1.3), 0.35f);
            if (!dansLeVert && niveau >= cibleBas && niveau <= cibleHaut) {
                dansLeVert = true;
                Dessin.bip(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.45f);
            }
            if (niveau >= 1) {
                niveau = 1;
                juger();
            }
        }

        void juger() {
            aspire = false;
            dernierOk = niveau >= cibleBas && niveau <= cibleHaut;
            pause = 14;
            if (dernierOk) {
                Dessin.note(1f + manche * 0.25f);
                if (++manche >= 3) {
                    parfait = fautes == 0;
                    terminer(true);
                }
            } else {
                Dessin.note(0.5f);
                if (++fautes >= 3) terminer(false);
            }
        }

        @Override
        void clic(double mx, double my) {
            if (pause == 0 && !aspire) {
                aspire = true;
                dansLeVert = false;
                tic = 0;
                Dessin.bip(SoundEvents.BOTTLE_FILL, 1.3f, 0.5f);
            }
        }

        @Override
        void relache(double mx, double my) {
            if (aspire) juger();
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int tx = ox + 44, ty = oy + 9, tl = 26, th = 100;
            g.fill(tx - 2, ty - 2, tx + tl + 2, ty + th + 2, Dessin.BORD);
            g.fill(tx - 1, ty - 1, tx + tl + 1, ty + th + 1, 0xFF9FB0C0);
            g.fill(tx, ty, tx + tl, ty + th, 0xFF14202B);
            double n = Math.min(1, aspire ? niveau + vitesse * pt : niveau);
            int haut = (int) Math.round(th * n);
            int liquide = pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : 0xFF9B5FC0;
            g.fill(tx, ty + th - haut, tx + tl, ty + th, liquide);
            if (haut > 2) g.fill(tx, ty + th - haut, tx + tl, ty + th - haut + 2, Dessin.assombrir(liquide, 1.35));
            int yBas = ty + th - (int) Math.round(th * cibleBas), yHaut = ty + th - (int) Math.round(th * cibleHaut);
            g.fill(tx - 8, yHaut, tx - 3, yBas, Dessin.VERT);
            g.fill(tx + tl + 3, yHaut, tx + tl + 8, yBas, Dessin.VERT);
            g.fill(tx, yHaut, tx + tl, yHaut + 1, 0xC06CCB5F);
            g.fill(tx, yBas - 1, tx + tl, yBas, 0xC06CCB5F);
            for (int i = 1; i < 10; i++) g.fill(tx, ty + i * 10, tx + (i % 5 == 0 ? 8 : 4), ty + i * 10 + 1, 0xFF5D6E80);
            int px = ox + 110;
            g.drawString(Dessin.police(), T.f("dose") + Math.min(3, manche + 1) + " / 3", px, oy + 14, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("faults") + fautes + " / 3", px, oy + 26, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            g.drawString(Dessin.police(), pause > 0 ? (dernierOk ? T.f("right_dose") : niveau >= 1 ? T.f("overflow") : T.f("wrong_dose")) : aspire ? T.f("release_in_the_green") : T.f("hold_the_click"),
                    px, oy + 46, pause > 0 ? (dernierOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.JAUNE, false);
            Dessin.paragraphe(g, T.f("the_green_zone_shrinks_and"), px, oy + 66, 160, Dessin.SOMBRE);
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_to_draw");
        }
    }

    // ---------- Mesure de l'empreinte : poser les quatre mâchoires du pied à coulisse ----------

    static final class Mesure extends Jeu {
        static final ResourceLocation EMPREINTE = Dessin.texture("empreinte");
        static final int EL = 74, EH = 70;
        static final String[] BORDS = {T.f("left_edge"), T.f("right_edge"), T.f("top_edge"), T.f("bottom_edge")};
        final int ex, ey;
        final double[] poses = new double[4];
        double pos, sens = 1;
        int etape, fautes, flash;
        boolean flashOk;

        Mesure(Random h) {
            ex = 22 + h.nextInt(28);
            ey = 14 + h.nextInt(20);
            pos = h.nextInt(100);
        }

        double vitesse() {
            return 2.2 + etape * 0.35;
        }

        int longueur() {
            return etape < 2 ? 128 : 106;
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            pos += sens * vitesse();
            if (pos >= longueur()) {
                pos = longueur();
                sens = -1;
                Dessin.bip(SoundEvents.NOTE_BLOCK_HAT.value(), 1.1f, 0.45f);
            } else if (pos <= 0) {
                pos = 0;
                sens = 1;
                Dessin.bip(SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f, 0.45f);
            }
        }

        double cible() {
            return switch (etape) {
                case 0 -> ex;
                case 1 -> ex + EL;
                case 2 -> ey;
                default -> ey + EH;
            };
        }

        @Override
        void clic(double mx, double my) {
            // Position de la règle dans le repère de la zone (8 px de marge à gauche, 6 en haut).
            double ici = pos + (etape < 2 ? 8 : 6);
            flashOk = Math.abs(ici - cible()) <= 4.5;
            flash = 8;
            poses[etape] = ici;
            if (flashOk) Dessin.note(1f + etape * 0.2f);
            else {
                Dessin.note(0.5f);
                fautes++;
            }
            etape++;
            pos = etape == 2 ? 0 : pos;
            if (fautes >= 2) terminer(false);
            else if (etape >= 4) {
                parfait = fautes == 0;
                terminer(true);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            g.fill(ox + 6, oy + 4, ox + 140, oy + 114, Dessin.BORD);
            g.fill(ox + 7, oy + 5, ox + 139, oy + 113, 0xFF4A3828);
            g.fill(ox + 7, oy + 5, ox + 139, oy + 6, 0xFF5E4834);
            Dessin.image(g, EMPREINTE, ox + ex, oy + ey, EL, EH);
            for (int i = 0; i < Math.min(etape, 4); i++) regle(g, i, poses[i], Dessin.JAUNE);
            if (!fini) {
                double p = Math.max(0, Math.min(longueur(), pos + sens * vitesse() * pt)) + (etape < 2 ? 8 : 6);
                regle(g, etape, p, flash > 0 ? (flashOk ? Dessin.VERT : Dessin.ALERTE) : Dessin.TEXTE);
            }
            int px = ox + 150;
            g.drawString(Dessin.police(), T.f("jaw") + Math.min(4, etape + 1) + " / 4", px, oy + 12, Dessin.TEXTE, false);
            if (etape < 4) g.drawString(Dessin.police(), BORDS[etape], px, oy + 24, Dessin.JAUNE, false);
            g.drawString(Dessin.police(), T.f("faults") + fautes + " / 2", px, oy + 40, fautes > 0 ? Dessin.ALERTE : Dessin.GRIS, false);
            if (etape >= 2) g.drawString(Dessin.police(), T.f("width") + Math.round((poses[1] - poses[0]) / 2) + T.f("cm"), px, oy + 58, Dessin.ACCENT, false);
            if (etape >= 4) g.drawString(Dessin.police(), T.f("length") + Math.round((poses[3] - poses[2]) / 2) + T.f("cm"), px, oy + 70, Dessin.ACCENT, false);
            Dessin.paragraphe(g, T.f("click_when_the_ruler_touches"), px, oy + 88, 124, Dessin.SOMBRE);
        }

        void regle(GuiGraphics g, int i, double p, int couleur) {
            int q = (int) Math.round(p);
            if (i < 2) g.fill(ox + q, oy + 6, ox + q + 1, oy + 112, couleur);
            else g.fill(ox + 8, oy + q, ox + 138, oy + q + 1, couleur);
        }

        @Override
        String consigne() {
            return T.f("click_when_the_ruler_touches_2");
        }
    }

    // ---------- Tri des fragments (nid) : ramasser les coquilles, laisser le reste ----------

    static final class Tri extends Jeu {
        static final ResourceLocation[] DEBRIS = {Dessin.texture("debris_coquille"), Dessin.texture("debris_brindille"),
                Dessin.texture("debris_feuille"), Dessin.texture("debris_caillou")};
        static final int N = 18, COQUILLES = 6, DUREE = 300;
        final int[] x = new int[N], y = new int[N], sorte = new int[N];
        final boolean[] pris = new boolean[N];
        final double[] phase = new double[N];
        int trouvees, fautes, temps = DUREE, flash, ticks;

        Tri(Random h) {
            for (int i = 0; i < N; i++) {
                sorte[i] = i < COQUILLES ? 0 : 1 + h.nextInt(3);
                phase[i] = h.nextDouble() * 6.28;
                for (int essai = 0; essai < 60; essai++) {
                    x[i] = 10 + h.nextInt(168);
                    y[i] = 8 + h.nextInt(82);
                    boolean libre = true;
                    for (int k = 0; k < i; k++) libre &= Math.hypot(x[k] - x[i], y[k] - y[i]) >= 15;
                    if (libre) break;
                }
            }
        }

        @Override
        void tick() {
            ticks++;
            if (flash > 0) flash--;
            if (--temps <= 0) terminer(false);
            else presser(temps, DUREE);
        }

        int dx(int i, float pt) {
            return (int) Math.round(Math.sin((ticks + pt) * 0.12 + phase[i]) * 1.6);
        }

        @Override
        void clic(double mx, double my) {
            for (int i = N - 1; i >= 0; i--) {
                if (pris[i] || !Dessin.dans(mx, my, ox + x[i] + dx(i, 0), oy + y[i], 16, 16)) continue;
                if (sorte[i] == 0) {
                    pris[i] = true;
                    Dessin.son(1.1f + trouvees * 0.1f);
                    if (++trouvees >= COQUILLES) {
                        parfait = fautes == 0;
                        terminer(true);
                    }
                } else {
                    flash = 6;
                    Dessin.note(0.5f);
                    if (++fautes >= 3) terminer(false);
                }
                return;
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            g.fill(ox + 6, oy + 4, ox + 198, oy + 110, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
            g.fill(ox + 7, oy + 5, ox + 197, oy + 109, 0xFF3A2D21);
            for (int i = 0; i < N; i++) {
                if (!pris[i]) Dessin.image(g, DEBRIS[sorte[i]], ox + x[i] + dx(i, pt), oy + y[i], 16, 16);
            }
            int px = ox + 206;
            g.drawString(Dessin.police(), T.f("shells"), px, oy + 10, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), trouvees + " / " + COQUILLES, px, oy + 21, Dessin.JAUNE, false);
            g.drawString(Dessin.police(), T.f("faults_2"), px, oy + 40, Dessin.GRIS, false);
            g.drawString(Dessin.police(), fautes + " / 3", px, oy + 51, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            Dessin.image(g, DEBRIS[0], px + 44, oy + 14, 16, 16);
            jaugeTemps(g, ox + 6, oy + 111, 192, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("pick_up_the_six_eggshell");
        }
    }

    // ---------- Relevé des griffures : suivre chaque entaille sans en sortir ----------

    static final class Trace extends Jeu {
        static final ResourceLocation ECORCE = Dessin.texture("ecorce");
        static final int N = 44;
        final double[][] px = new double[3][N], py = new double[3][N];
        int ligne, avancee, fautes, flash;
        boolean enCours, attendRelache;

        Trace(Random h) {
            for (int k = 0; k < 3; k++) {
                double x0 = 26 + k * 40 + h.nextInt(8), penche = 22 + h.nextInt(14), courbe = 3 + h.nextDouble() * 4, dephasage = h.nextDouble() * 6.28;
                for (int i = 0; i < N; i++) {
                    double t = i / (double) (N - 1);
                    px[k][i] = x0 + t * penche + Math.sin(t * Math.PI * 2 + dephasage) * courbe;
                    py[k][i] = 14 + t * 88;
                }
            }
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
        }

        void faute() {
            enCours = false;
            attendRelache = true;
            avancee = 0;
            flash = 8;
            Dessin.note(0.5f);
            if (++fautes >= 3) terminer(false);
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            if (!fini) suivre(mx - bx, my - by);
            g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
            Dessin.image(g, ECORCE, bx, by, 160, 112);
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < N - 1; i++) {
                    Dessin.segment(g, bx + px[k][i] - 2, by + py[k][i] - 1, bx + px[k][i + 1] - 2, by + py[k][i + 1] - 1, 5, 0xFF1B120C);
                }
                for (int i = 0; i < N - 1; i++) {
                    boolean fait = k < ligne || (k == ligne && i < avancee);
                    Dessin.segment(g, bx + px[k][i] - 1, by + py[k][i] - 1, bx + px[k][i + 1] - 1, by + py[k][i + 1] - 1, 3, fait ? Dessin.JAUNE : 0xFFE3C993);
                }
            }
            if (ligne < 3 && !enCours) {
                boolean pulse = (System.currentTimeMillis() / 300) % 2 == 0;
                g.fill(bx + (int) px[ligne][0] - 4, by + (int) py[ligne][0] - 4, bx + (int) px[ligne][0] + 4, by + (int) py[ligne][0] + 4, Dessin.BORD);
                g.fill(bx + (int) px[ligne][0] - 3, by + (int) py[ligne][0] - 3, bx + (int) px[ligne][0] + 3, by + (int) py[ligne][0] + 3, pulse ? Dessin.ACCENT : Dessin.TEXTE);
            }
            int tx = ox + 172;
            g.drawString(Dessin.police(), T.f("scratch") + Math.min(3, ligne + 1) + " / 3", tx, oy + 12, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("slips") + fautes + " / 3", tx, oy + 24, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            g.drawString(Dessin.police(), enCours ? T.f("follow_the_groove") : attendRelache ? T.f("release_and_retry") : T.f("start_from_the_dot"), tx, oy + 44,
                    enCours ? Dessin.ACCENT : Dessin.JAUNE, false);
            Dessin.paragraphe(g, T.f("hold_the_click_from_top"), tx, oy + 64, 104, Dessin.SOMBRE);
        }

        void suivre(double mx, double my) {
            if (!appui) {
                enCours = false;
                attendRelache = false;
                avancee = 0;
                return;
            }
            if (attendRelache || ligne >= 3) return;
            if (!enCours) {
                if (Math.hypot(mx - px[ligne][0], my - py[ligne][0]) < 8) {
                    enCours = true;
                    avancee = 0;
                    Dessin.son(1.3f);
                }
                return;
            }
            while (avancee < N - 1 && Math.hypot(mx - px[ligne][avancee + 1], my - py[ligne][avancee + 1]) < 8) {
                if (++avancee % 5 == 0) Dessin.bip(SoundEvents.AXE_SCRAPE, 1.1f + avancee * 0.012f, 0.5f);
            }
            if (avancee >= N - 1) {
                Dessin.note(1f + ligne * 0.25f);
                enCours = false;
                attendRelache = true;
                avancee = 0;
                if (++ligne >= 3) {
                    parfait = fautes == 0;
                    terminer(true);
                }
            } else if (Math.hypot(mx - px[ligne][avancee], my - py[ligne][avancee]) > 13) {
                faute();
            }
        }

        @Override
        String consigne() {
            return T.f("hold_the_click_on_the");
        }
    }

    // ---------- Dégagement au pinceau (indice enfoui) : trouver les fragments cachés sous la terre, en brossant doucement ----------

    static final class Degagement extends Jeu {
        static final ResourceLocation TERRE = Dessin.texture("terre");
        // La terre fait 40 x 28 cases de 4 px. Chaque fragment (le dessin de l'indice, agrandi deux fois) en couvre 8 x 8.
        static final int COLS = 40, RANGS = 28, CASE = 4, DUREE = 700, FRAGMENTS = 3, TAILLE = 8, RAYON = 9;
        static final double VITESSE_MAX = 17;
        final ResourceLocation indice;
        // Épaisseur de terre restante sur chaque case : 1 = intacte, 0 = dégagée. Elle part peu à peu sous le pinceau.
        final float[] terre = new float[COLS * RANGS];
        final int[] fc = new int[FRAGMENTS], fr = new int[FRAGMENTS];
        final boolean[] trouve = new boolean[FRAGMENTS];
        int temps = DUREE, fautes, flash, repos, trouves, tic;
        double sx = -1, sy = -1, cx, cy;

        Degagement(Random h, String genre) {
            indice = Dessin.texture("enfoui_" + genre);
            java.util.Arrays.fill(terre, 1f);
            // Les fragments sont enfouis n'importe où, sans se chevaucher.
            for (int i = 0; i < FRAGMENTS; i++) {
                for (int essai = 0; essai < 200; essai++) {
                    fc[i] = h.nextInt(COLS - TAILLE + 1);
                    fr[i] = h.nextInt(RANGS - TAILLE + 1);
                    boolean libre = true;
                    for (int k = 0; k < i; k++) libre &= Math.abs(fc[k] - fc[i]) > TAILLE || Math.abs(fr[k] - fr[i]) > TAILLE;
                    if (libre) break;
                }
            }
        }

        // Fragment enfoui sous cette case, ou -1.
        int fragment(int c, int r) {
            for (int i = 0; i < FRAGMENTS; i++) {
                if (c >= fc[i] && c < fc[i] + TAILLE && r >= fr[i] && r < fr[i] + TAILLE) return i;
            }
            return -1;
        }

        int part(int i) {
            int n = 0;
            for (int r = fr[i]; r < fr[i] + TAILLE; r++) {
                for (int c = fc[i]; c < fc[i] + TAILLE; c++) {
                    if (terre[r * COLS + c] <= 0) n++;
                }
            }
            return n * 100 / (TAILLE * TAILLE);
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            if (repos > 0) repos--;
            if (--temps <= 0) {
                terminer(false);
                return;
            }
            presser(temps, DUREE);
            int bx = ox + 4, by = oy + 3;
            boolean surLaTerre = Dessin.dans(cx, cy, bx - RAYON, by - RAYON, 160 + 2 * RAYON, 112 + 2 * RAYON);
            if (appui && surLaTerre && sx >= 0) {
                double vitesse = Math.hypot(cx - sx, cy - sy);
                if (repos == 0 && vitesse > VITESSE_MAX) {
                    // Un geste trop brusque abîme ce qui est enfoui.
                    flash = 8;
                    repos = 12;
                    Dessin.note(0.5f);
                    if (++fautes >= 3) terminer(false);
                } else if (repos == 0) {
                    // Le pinceau enlève la terre peu à peu : plus au centre que sur les bords, et il faut s'attarder.
                    for (int i = 0; i < terre.length; i++) {
                        if (terre[i] <= 0) continue;
                        double d = Math.hypot(cx - (bx + (i % COLS) * CASE + 2), cy - (by + (i / COLS) * CASE + 2));
                        if (d < RAYON) terre[i] -= (float) (0.3 * (1 - d / RAYON) + 0.03);
                    }
                    if (++tic % 5 == 0) Dessin.bip(SoundEvents.BRUSH_GENERIC, (float) (0.9 + Math.min(0.5, vitesse / 30)), 0.7f);
                    for (int i = 0; i < FRAGMENTS; i++) {
                        if (trouve[i] || part(i) < 80) continue;
                        trouve[i] = true;
                        Dessin.note(1f + trouves * 0.25f);
                        if (++trouves >= FRAGMENTS) {
                            parfait = fautes == 0;
                            terminer(true);
                        }
                    }
                }
            }
            sx = cx;
            sy = cy;
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int bx = ox + 4, by = oy + 3;
            cx = mx;
            cy = my;
            g.fill(bx - 1, by - 1, bx + 161, by + 113, flash > 0 ? Dessin.ALERTE : Dessin.BORD);
            Dessin.image(g, TERRE, bx, by, 160, 112);
            for (int i = 0; i < terre.length; i++) {
                float e = terre[i];
                if (e >= 1f) continue;
                int c = i % COLS, r = i / COLS, x = bx + c * CASE, y = by + r * CASE, f = fragment(c, r);
                if (e <= 0) {
                    g.fill(x, y, x + CASE, y + CASE, 0xFF2B2018);
                    // Le morceau du fragment sous cette case (2 x 2 pixels du dessin, agrandis deux fois).
                    if (f >= 0) Dessin.morceauAgrandi(g, indice, x, y, CASE, CASE, (c - fc[f]) * 2, (r - fr[f]) * 2, 2, 2, 16, 16);
                } else {
                    // Terre entamée : elle fonce ; au-dessus d'un fragment, une pâleur le laisse deviner.
                    g.fill(x, y, x + CASE, y + CASE, ((int) ((1 - e) * 150) << 24) | 0x1E1610);
                    if (f >= 0 && e < 0.75f) g.fill(x, y, x + CASE, y + CASE, ((int) ((0.75f - e) * 110) << 24) | 0xE6D8B4);
                }
            }
            for (int i = 0; i < FRAGMENTS; i++) {
                if (!trouve[i]) continue;
                int x = bx + fc[i] * CASE, y = by + fr[i] * CASE, l = TAILLE * CASE;
                g.fill(x - 1, y - 1, x + l + 1, y, Dessin.VERT);
                g.fill(x - 1, y + l, x + l + 1, y + l + 1, Dessin.VERT);
                g.fill(x - 1, y, x, y + l, Dessin.VERT);
                g.fill(x + l, y, x + l + 1, y + l, Dessin.VERT);
            }
            if (!fini && Dessin.dans(mx, my, bx, by, 160, 112)) Dessin.cercle(g, mx - 1, my - 1, RAYON, 2, appui ? Dessin.JAUNE : Dessin.TEXTE);
            int tx = ox + 172;
            g.drawString(Dessin.police(), T.f("fragments") + trouves + " / " + FRAGMENTS, tx, oy + 12, trouves > 0 ? Dessin.VERT : Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("snags") + fautes + " / 3", tx, oy + 24, fautes > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            g.drawString(Dessin.police(), flash > 0 ? T.f("too_rough") : T.f("brush_gently"), tx, oy + 44, flash > 0 ? Dessin.ALERTE : Dessin.JAUNE, false);
            Dessin.paragraphe(g, T.f("go_slowly_a_pale_patch"), tx, oy + 60, 104, Dessin.SOMBRE);
            jaugeTemps(g, tx, oy + 104, 100, temps / (double) DUREE);
        }

        @Override
        String consigne() {
            return T.f("brush_the_soil_hold_the");
        }
    }
}
