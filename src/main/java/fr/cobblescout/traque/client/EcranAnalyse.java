package fr.cobblescout.traque.client;

import java.util.Random;

import com.mojang.math.Axis;

import fr.cobblescout.traque.Genetique;
import fr.cobblescout.traque.Paquet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// Séquenceur ADN : chaque type d'échantillon s'analyse par un mini-jeu différent.
// Quitter l'écran avant la fin gâche une dose, comme un échec.
public class EcranAnalyse extends Screen {
    private static final int ZL = 280, ZH = 118;
    private final String genre, idJeu;
    private final boolean prelevement, filet, enfoui;
    private final int doses;
    private final Jeu jeu;
    private boolean envoye;
    private int finDepuis = -1;
    private int ticks;

    public EcranAnalyse(String genre, String idJeu, long graine, int doses, boolean filet, boolean enfoui, boolean prelevement) {
        super(Component.literal(T.f("dna_sequencer")));
        this.genre = genre;
        this.idJeu = idJeu;
        this.prelevement = prelevement;
        this.enfoui = enfoui;
        this.doses = doses;
        this.filet = filet;
        Random h = new Random(graine);
        this.jeu = switch (idJeu) {
            case "memoire" -> new JeuxTerrain.Cristaux(h);
            case "pipette" -> new JeuxTerrain.Pipette(h);
            case "mesure" -> new JeuxTerrain.Mesure(h);
            case "tri" -> new JeuxTerrain.Tri(h);
            case "trace" -> new JeuxTerrain.Trace(h);
            case "degagement" -> new JeuxTerrain.Degagement(h, genre);
            case "appariement" -> new Appariement(h);
            case "centrifugation" -> new Centrifugeuse(h);
            case "fragments" -> new Fragments(h);
            case "spectre" -> new Spectre(h);
            case "gel" -> new JeuxLabo.Gel(h);
            case "titrage" -> new JeuxLabo.Titrage(h);
            case "pcr" -> new JeuxLabo.Pcr(h);
            case "comptage" -> new JeuxLabo.Comptage(h);
            default -> new Sequencage(h);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xA0070B10);
    }

    @Override
    public void tick() {
        ticks++;
        // Rythme réglé par le serveur : en facile, le jeu saute un tic sur trois ; en difficile, il en joue un de plus sur quatre.
        int rythme = TraqueClient.etat.getInt("difficulte");
        if (!jeu.fini && !(rythme < 0 && ticks % 3 == 0)) {
            jeu.tick();
            if (rythme > 0 && ticks % 4 == 0 && !jeu.fini) jeu.tick();
        }
        if (jeu.fini && finDepuis < 0) {
            finDepuis = ticks;
            // Fin de partie : carillon et petite fanfare si c'est réussi, note grave sinon.
            Dessin.note(jeu.succes ? 1.6f : 0.6f);
            if (jeu.succes) Dessin.bip(SoundEvents.EXPERIENCE_ORB_PICKUP, jeu.parfait ? 1.4f : 1f, 0.6f);
            else Dessin.bip(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.9f);
        }
        if (finDepuis >= 0 && ticks - finDepuis > 32) onClose();
    }

    @Override
    public void removed() {
        if (!envoye) {
            envoye = true;
            Paquet p = Paquet.de(prelevement ? "prelevement" : "analyse");
            p.donnees().putBoolean("succes", jeu.fini && jeu.succes);
            p.donnees().putBoolean("parfait", jeu.fini && jeu.succes && jeu.parfait);
            TraqueClient.envoyer(p);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        // Un prélèvement se fait sur le terrain, dans l'écran du kit ; une analyse, dans celui du séquenceur.
        Dessin.Fond f = prelevement ? Dessin.KIT : Dessin.SEQUENCEUR;
        int x = f.x(width), y = f.y(height), l = f.il();
        Dessin.fond(g, f, width, height);
        int zx = x + (l - ZL) / 2, zy;
        if (prelevement) {
            Dessin.bandeau(g, x, y, l, T.c(Genetique.nomJeu(idJeu)).toUpperCase(), T.c(Genetique.nomTrace(genre, enfoui)));
            zy = y + 18;
        } else {
            // Le titre s'affiche sur le bandeau turquoise de la machine, sous l'écran.
            String nomJeu = T.c(Genetique.nomJeu(idJeu)).toUpperCase();
            String titre = T.f("dna_sequencer_2") + nomJeu;
            if (Dessin.police().width(titre) > 220) titre = nomJeu;
            Dessin.centre(g, titre, x + l / 2, y + 176, 0xFF0E3B3A);
            g.drawString(Dessin.police(), T.c(Genetique.nomTrace(genre, enfoui)) + T.f("doses") + doses + (filet ? T.f("first_mistake_is_free") : ""),
                    zx, y + 3, Dessin.GRIS, false);
            zy = y + 15;
        }
        Dessin.encart(g, zx, zy, ZL, ZH);
        jeu.ox = zx;
        jeu.oy = zy;
        jeu.dessiner(g, mx, my, pt);
        Dessin.paragraphe(g, jeu.consigne(), zx, zy + ZH + 3, ZL, Dessin.GRIS);
        if (finDepuis >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(zx + 40, zy + 40, zx + ZL - 40, zy + 78, Dessin.BORD);
            g.fill(zx + 42, zy + 42, zx + ZL - 42, zy + 76, jeu.succes ? 0xFF245C3A : 0xFF6B2420);
            if (prelevement) {
                Dessin.centre(g, jeu.succes ? (jeu.parfait ? T.f("perfect_sample") : T.f("sample_taken")) : T.f("sample_damaged"), zx + ZL / 2, zy + 49, Dessin.TEXTE);
                Dessin.centre(g, jeu.succes ? (jeu.parfait ? T.f("one_extra_dose") : T.f("full_sample")) : T.f("a_single_dose_half_star"), zx + ZL / 2, zy + 62, Dessin.GRIS);
            } else {
                Dessin.centre(g, jeu.succes ? T.f("analysis_complete") : T.f("analysis_failed"), zx + ZL / 2, zy + 49, Dessin.TEXTE);
                Dessin.centre(g, jeu.succes ? T.f("marker") + T.c(Genetique.NOMS_MARQUEURS.get(genre)) + T.f("added_to_the_case_file")
                        : filet ? T.f("the_sequencer_saved_the_dose") : T.f("one_dose_lost_half_star"), zx + ZL / 2, zy + 62, Dessin.GRIS);
            }
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        jeu.appui = true;
        if (!jeu.fini) jeu.clic(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int bouton) {
        jeu.appui = false;
        if (!jeu.fini) jeu.relache(mx, my);
        return true;
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        if (!jeu.fini && jeu.touche(touche)) return true;
        return super.keyPressed(touche, scan, mods);
    }

    // ---------- 1. Séquençage : retrouver 4 bases en 6 lectures ----------

    private static final class Sequencage extends Jeu {
        final int[] code = new int[4];
        final int[][] essais = new int[6][4];
        final int[][] retours = new int[6][2];
        int ligne;

        Sequencage(Random h) {
            for (int k = 0; k < 4; k++) code[k] = h.nextInt(4);
            for (int[] e : essais) java.util.Arrays.fill(e, -1);
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            for (int l = 0; l < 6; l++) {
                int y = oy + 6 + l * 18;
                g.drawString(Dessin.police(), String.valueOf(l + 1), ox + 8, y + 4, l == ligne ? Dessin.JAUNE : Dessin.SOMBRE, false);
                for (int k = 0; k < 4; k++) Dessin.base(g, ox + 22 + k * 20, y, 15, essais[l][k], l == ligne && Dessin.dans(mx, my, ox + 22 + k * 20, y, 15, 15));
                if (l < ligne) {
                    for (int k = 0; k < 4; k++) {
                        int c = k < retours[l][0] ? Dessin.VERT : k < retours[l][0] + retours[l][1] ? Dessin.JAUNE : Dessin.ENCART_CLAIR;
                        g.fill(ox + 110 + k * 11, y + 4, ox + 118 + k * 11, y + 12, Dessin.BORD);
                        g.fill(ox + 111 + k * 11, y + 5, ox + 117 + k * 11, y + 11, c);
                    }
                } else if (l == ligne) {
                    Dessin.bouton(g, ox + 110, y - 2, 50, 18, T.f("read"), Dessin.dans(mx, my, ox + 110, y - 2, 50, 18), complet(), Dessin.ACCENT);
                }
            }
            g.fill(ox + 190, oy + 10, ox + 198, oy + 18, Dessin.VERT);
            g.drawString(Dessin.police(), T.f("right_place"), ox + 202, oy + 10, Dessin.GRIS, false);
            g.fill(ox + 190, oy + 24, ox + 198, oy + 32, Dessin.JAUNE);
            g.drawString(Dessin.police(), T.f("wrong_place"), ox + 202, oy + 24, Dessin.GRIS, false);
            g.drawString(Dessin.police(), T.f("reads_left") + (6 - ligne), ox + 190, oy + 46, Dessin.TEXTE, false);
        }

        boolean complet() {
            for (int v : essais[ligne]) {
                if (v < 0) return false;
            }
            return true;
        }

        @Override
        void clic(double mx, double my) {
            int y = oy + 6 + ligne * 18;
            for (int k = 0; k < 4; k++) {
                if (Dessin.dans(mx, my, ox + 22 + k * 20, y, 15, 15)) {
                    essais[ligne][k] = (essais[ligne][k] + 1) % 4;
                    Dessin.son(1f + essais[ligne][k] * 0.15f);
                    return;
                }
            }
            if (!Dessin.dans(mx, my, ox + 110, y - 2, 50, 18) || !complet()) return;
            int exacts = 0, presents = 0;
            int[] resteCode = new int[4], resteEssai = new int[4];
            for (int k = 0; k < 4; k++) {
                if (essais[ligne][k] == code[k]) exacts++;
                else {
                    resteCode[code[k]]++;
                    resteEssai[essais[ligne][k]]++;
                }
            }
            for (int b = 0; b < 4; b++) presents += Math.min(resteCode[b], resteEssai[b]);
            retours[ligne][0] = exacts;
            retours[ligne][1] = presents;
            Dessin.note(0.8f + exacts * 0.2f);
            ligne++;
            if (exacts == 4) terminer(true);
            else if (ligne >= 6) terminer(false);
            else essais[ligne] = essais[ligne - 1].clone();
        }

        @Override
        String consigne() {
            return T.f("click_the_slots_to_pick");
        }
    }

    // ---------- 2. Appariement des brins : donner la base complémentaire avant la fin du temps ----------

    private static final class Appariement extends Jeu {
        static final int[] COMPLEMENT = {1, 0, 3, 2};
        static final int[] TOUCHES = {65, 84, 67, 71};
        final int[] brin = new int[12];
        int position, erreurs, temps;
        int flash, flashOk;

        Appariement(Random h) {
            for (int k = 0; k < brin.length; k++) brin[k] = h.nextInt(4);
        }

        int duree() {
            return Math.max(20, 38 - position * 2);
        }

        @Override
        void tick() {
            if (flash > 0) flash--;
            if (++temps > duree()) faute();
            else if (temps == duree() * 2 / 3) Dessin.bip(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.5f);
        }

        void faute() {
            erreurs++;
            flash = 6;
            flashOk = 0;
            Dessin.note(0.5f);
            suivant();
            if (erreurs >= 3) terminer(false);
        }

        void suivant() {
            position++;
            temps = 0;
            if (position >= brin.length && !fini) terminer(erreurs < 3);
        }

        void repondre(int b) {
            if (fini || position >= brin.length) return;
            if (b == COMPLEMENT[brin[position]]) {
                flash = 6;
                flashOk = 1;
                Dessin.son(1.2f + position * 0.05f);
                suivant();
            } else faute();
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            g.drawString(Dessin.police(), T.f("strand_to_complete"), ox + 10, oy + 8, Dessin.GRIS, false);
            for (int k = position; k < Math.min(brin.length, position + 6); k++) {
                int i = k - position;
                int taille = i == 0 ? 28 : 16;
                int bx = i == 0 ? ox + 20 : ox + 62 + (i - 1) * 22, by = i == 0 ? oy + 22 : oy + 28;
                Dessin.base(g, bx, by, taille, brin[k], i == 0);
            }
            int bandeau = flash > 0 ? (flashOk == 1 ? Dessin.VERT : Dessin.ALERTE) : Dessin.ENCART_CLAIR;
            g.fill(ox + 20, oy + 56, ox + ZL - 20, oy + 62, Dessin.BORD);
            double part = 1 - Math.min(1.0, (temps + pt) / duree());
            g.fill(ox + 21, oy + 57, ox + 21 + (int) ((ZL - 42) * part), oy + 61, part > 0.35 ? Dessin.ACCENT : Dessin.ALERTE);
            g.fill(ox + 20, oy + 64, ox + ZL - 20, oy + 65, bandeau);
            for (int b = 0; b < 4; b++) {
                int bx = ox + 30 + b * 56;
                boolean survol = Dessin.dans(mx, my, bx, oy + 74, 48, 24);
                Dessin.bouton(g, bx, oy + 74, 48, 24, Dessin.BASES[b], survol, true, Dessin.COULEURS_BASES[b]);
            }
            g.drawString(Dessin.police(), (position) + " / " + brin.length, ox + ZL - 60, oy + 8, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("mistakes") + erreurs + " / 3", ox + ZL - 84, oy + 104, erreurs > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            g.drawString(Dessin.police(), "A↔T   C↔G", ox + 10, oy + 104, Dessin.JAUNE, false);
        }

        @Override
        void clic(double mx, double my) {
            for (int b = 0; b < 4; b++) {
                if (Dessin.dans(mx, my, ox + 30 + b * 56, oy + 74, 48, 24)) repondre(b);
            }
        }

        @Override
        boolean touche(int touche) {
            for (int b = 0; b < 4; b++) {
                if (touche == TOUCHES[b]) {
                    repondre(b);
                    return true;
                }
            }
            return false;
        }

        @Override
        String consigne() {
            return T.f("give_the_base_that_pairs");
        }
    }

    // ---------- 3. Centrifugation : arrêter l'aiguille dans la zone verte, trois fois ----------

    private static final class Centrifugeuse extends Jeu {
        final Random h;
        double angle, vitesse = 7, debut, largeur = 56;
        int reussites, rates, flash;

        Centrifugeuse(Random h) {
            this.h = h;
            debut = h.nextInt(360);
        }

        @Override
        void tick() {
            double avant = angle;
            angle = (angle + vitesse) % 360;
            if (angle < avant) Dessin.bip(SoundEvents.NOTE_BLOCK_HAT.value(), (float) (0.7 + vitesse * 0.07), 0.5f);
            if (flash > 0) flash--;
        }

        void arreter(float pt) {
            double a = (angle + vitesse * pt) % 360;
            double ecart = ((a - debut) % 360 + 360) % 360;
            if (ecart <= largeur) {
                reussites++;
                Dessin.note(1f + reussites * 0.25f);
                if (reussites >= 3) {
                    terminer(true);
                    return;
                }
                vitesse += 2.5;
                largeur -= 13;
                debut = h.nextInt(360);
            } else {
                rates++;
                flash = 6;
                Dessin.note(0.5f);
                if (rates >= 3) terminer(false);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int cx = ox + 70, cy = oy + ZH / 2, rayon = 51;
            double degres = fini ? angle : angle + vitesse * pt;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            // Le rotor tourne à contresens de l'aiguille.
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) -degres));
            Dessin.image(g, Dessin.ROTOR, -48, -48, 96, 96);
            g.pose().popPose();
            Dessin.cercle(g, cx - 1, cy - 1, rayon, 2, flash > 0 ? Dessin.ALERTE : Dessin.BORD_CLAIR);
            for (double a = debut; a <= debut + largeur; a += 2) {
                double r = Math.toRadians(a);
                Dessin.point(g, cx + Math.sin(r) * rayon - 2, cy - Math.cos(r) * rayon - 2, 4, Dessin.VERT);
            }
            double r = Math.toRadians(degres);
            Dessin.segment(g, cx - 1 + Math.sin(r) * 14, cy - 1 - Math.cos(r) * 14, cx - 1 + Math.sin(r) * (rayon + 2), cy - 1 - Math.cos(r) * (rayon + 2), 2, Dessin.JAUNE);
            for (int i = 0; i < 3; i++) {
                g.fill(ox + 150 + i * 30, oy + 20, ox + 172 + i * 30, oy + 42, Dessin.BORD);
                g.fill(ox + 152 + i * 30, oy + 22, ox + 170 + i * 30, oy + 40, i < reussites ? Dessin.VERT : Dessin.ENCART_CLAIR);
            }
            g.drawString(Dessin.police(), T.f("phases_separated") + reussites + " / 3", ox + 150, oy + 50, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("misses") + rates + " / 3", ox + 150, oy + 62, rates > 1 ? Dessin.ALERTE : Dessin.GRIS, false);
            Dessin.bouton(g, ox + 150, oy + 82, 110, 24, T.f("stop_space"), Dessin.dans(mx, my, ox + 150, oy + 82, 110, 24), true, Dessin.ACCENT);
        }

        @Override
        void clic(double mx, double my) {
            arreter(0f);
        }

        @Override
        boolean touche(int touche) {
            if (touche == 32) {
                arreter(0f);
                return true;
            }
            return false;
        }

        @Override
        String consigne() {
            return T.f("click_or_space_when_the");
        }
    }

    // ---------- 4. Assemblage : remettre 5 fragments dans l'ordre grâce à leurs chevauchements ----------

    private static final class Fragments extends Jeu {
        final int[][] fragments = new int[5][4];
        final int[] ordre = {0, 1, 2, 3, 4};
        int selection = -1, essais = 3, jonctions = -1;

        Fragments(Random h) {
            int[] seq = new int[12];
            while (true) {
                for (int k = 0; k < 12; k++) seq[k] = h.nextInt(4);
                java.util.Set<Integer> paires = new java.util.HashSet<>();
                for (int i = 0; i < 6; i++) paires.add(seq[2 * i] * 4 + seq[2 * i + 1]);
                if (paires.size() == 6) break;
            }
            for (int i = 0; i < 5; i++) System.arraycopy(seq, 2 * i, fragments[i], 0, 4);
            do {
                for (int i = 4; i > 0; i--) {
                    int j = h.nextInt(i + 1);
                    int t = ordre[i];
                    ordre[i] = ordre[j];
                    ordre[j] = t;
                }
            } while (bonnes() == 4);
        }

        int bonnes() {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int[] a = fragments[ordre[i]], b = fragments[ordre[i + 1]];
                if (a[2] == b[0] && a[3] == b[1]) n++;
            }
            return n;
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            for (int l = 0; l < 5; l++) {
                int y = oy + 8 + l * 20;
                boolean survol = Dessin.dans(mx, my, ox + 16, y - 1, 100, 18);
                if (l == selection || survol) g.fill(ox + 14, y - 2, ox + 118, y + 17, l == selection ? Dessin.JAUNE : Dessin.BORD_CLAIR);
                g.fill(ox + 15, y - 1, ox + 117, y + 16, Dessin.ENCART);
                g.drawString(Dessin.police(), String.valueOf(l + 1), ox + 20, y + 4, Dessin.SOMBRE, false);
                for (int k = 0; k < 4; k++) Dessin.base(g, ox + 34 + k * 20, y, 15, fragments[ordre[l]][k], false);
            }
            g.drawString(Dessin.police(), T.f("the_end_of_a_fragment"), ox + 132, oy + 10, Dessin.GRIS, false);
            g.drawString(Dessin.police(), T.f("the_start_of_the_next"), ox + 132, oy + 20, Dessin.GRIS, false);
            for (int k = 0; k < 4; k++) Dessin.base(g, ox + 132 + k * 13, oy + 36, 11, k < 2 ? -1 : k - 2, false);
            for (int k = 0; k < 4; k++) Dessin.base(g, ox + 158 + k * 13, oy + 50, 11, k < 2 ? k : -1, false);
            if (jonctions >= 0) g.drawString(Dessin.police(), T.f("correct_joints") + jonctions + " / 4", ox + 132, oy + 68, Dessin.JAUNE, false);
            g.drawString(Dessin.police(), T.f("tries") + essais, ox + 132, oy + 80, essais == 1 ? Dessin.ALERTE : Dessin.TEXTE, false);
            Dessin.bouton(g, ox + 132, oy + 92, 110, 20, T.f("assemble"), Dessin.dans(mx, my, ox + 132, oy + 92, 110, 20), true, Dessin.ACCENT);
        }

        @Override
        void clic(double mx, double my) {
            for (int l = 0; l < 5; l++) {
                if (!Dessin.dans(mx, my, ox + 16, oy + 7 + l * 20, 100, 18)) continue;
                if (selection < 0) selection = l;
                else {
                    int t = ordre[l];
                    ordre[l] = ordre[selection];
                    ordre[selection] = t;
                    selection = -1;
                }
                Dessin.son(1.2f);
                return;
            }
            if (Dessin.dans(mx, my, ox + 132, oy + 92, 110, 20)) {
                jonctions = bonnes();
                if (jonctions == 4) terminer(true);
                else {
                    essais--;
                    Dessin.note(0.6f);
                    if (essais <= 0) terminer(false);
                }
            }
        }

        @Override
        String consigne() {
            return T.f("click_two_fragments_to_swap");
        }
    }

    // ---------- 5. Accord du spectre : régler trois paramètres pour superposer sa courbe à celle de l'échantillon ----------

    private static final class Spectre extends Jeu {
        static final String[] NOMS = {T.f("frequency"), T.f("amplitude"), T.f("phase")};
        final int[] cible = new int[3], reglage = new int[3];
        int temps = 900, accord;

        Spectre(Random h) {
            do {
                for (int k = 0; k < 3; k++) {
                    cible[k] = h.nextInt(7);
                    reglage[k] = h.nextInt(7);
                }
            } while (ecart() < 5);
        }

        int ecart() {
            int n = 0;
            for (int k = 0; k < 3; k++) n += Math.abs(cible[k] - reglage[k]);
            return n;
        }

        static double courbe(int[] p, double t) {
            return Math.sin(t * (1 + p[0] * 0.5) * Math.PI * 2 + p[2] * Math.PI * 2 / 7) * (5 + p[1] * 2.5);
        }

        double ressemblance() {
            double somme = 0;
            for (int i = 0; i < 64; i++) somme += Math.abs(courbe(cible, i / 64.0) - courbe(reglage, i / 64.0));
            return Math.max(0, 1 - somme / 64 / 20);
        }

        @Override
        void tick() {
            if (--temps <= 0) terminer(false);
            presser(temps, 900);
            if (ecart() == 0) {
                if (accord == 0) Dessin.bip(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, 0.6f);
                if (++accord > 6) terminer(true);
            }
        }

        @Override
        void dessiner(GuiGraphics g, int mx, int my, float pt) {
            int px = ox + 8, py = oy + 6, pl = 150, ph = 54;
            g.fill(px, py, px + pl, py + ph, Dessin.BORD);
            g.fill(px + 1, py + 1, px + pl - 1, py + ph - 1, 0xFF10231F);
            g.fill(px + 1, py + ph / 2, px + pl - 1, py + ph / 2 + 1, 0xFF1D3B34);
            for (int i = 2; i < pl - 3; i++) {
                double t = i / (double) pl;
                Dessin.point(g, px + i, py + ph / 2 - courbe(cible, t), 2, 0xFF7A8A99);
                Dessin.point(g, px + i, py + ph / 2 - courbe(reglage, t), 2, ecart() == 0 ? Dessin.VERT : Dessin.ACCENT);
            }
            g.drawString(Dessin.police(), T.f("grey_sample"), px, py + ph + 3, Dessin.SOMBRE, false);
            int pourcent = (int) Math.round(ressemblance() * 100);
            g.drawString(Dessin.police(), T.f("match") + pourcent + " %", px, py + ph + 14, pourcent > 85 ? Dessin.VERT : Dessin.JAUNE, false);
            g.fill(px, oy + ZH - 12, px + pl, oy + ZH - 6, Dessin.BORD);
            g.fill(px + 1, oy + ZH - 11, px + 1 + (int) ((pl - 2) * temps / 900.0), oy + ZH - 7, temps > 200 ? Dessin.ACCENT : Dessin.ALERTE);
            for (int k = 0; k < 3; k++) {
                int y = oy + 8 + k * 34;
                g.drawString(Dessin.police(), NOMS[k], ox + 170, y, Dessin.TEXTE, false);
                Dessin.bouton(g, ox + 168, y + 10, 22, 18, "-", Dessin.dans(mx, my, ox + 168, y + 10, 22, 18), reglage[k] > 0, 0xFF5E7890);
                for (int i = 0; i < 7; i++) g.fill(ox + 194 + i * 8, y + 15, ox + 200 + i * 8, y + 23, i == reglage[k] ? Dessin.ACCENT : Dessin.BORD);
                Dessin.bouton(g, ox + 252, y + 10, 22, 18, "+", Dessin.dans(mx, my, ox + 252, y + 10, 22, 18), reglage[k] < 6, 0xFF5E7890);
            }
        }

        @Override
        void clic(double mx, double my) {
            for (int k = 0; k < 3; k++) {
                int y = oy + 8 + k * 34;
                if (Dessin.dans(mx, my, ox + 168, y + 10, 22, 18) && reglage[k] > 0) {
                    reglage[k]--;
                    accord = 0;
                    Dessin.son(0.9f + reglage[k] * 0.08f);
                } else if (Dessin.dans(mx, my, ox + 252, y + 10, 22, 18) && reglage[k] < 6) {
                    reglage[k]++;
                    accord = 0;
                    Dessin.son(0.9f + reglage[k] * 0.08f);
                }
            }
        }

        @Override
        String consigne() {
            return T.f("tune_frequency_amplitude_and_phase");
        }
    }
}
