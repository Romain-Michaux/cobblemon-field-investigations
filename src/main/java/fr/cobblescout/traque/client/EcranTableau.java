package fr.cobblescout.traque.client;

import java.util.ArrayList;
import java.util.List;

import fr.cobblescout.traque.Genetique;
import fr.cobblescout.traque.Paquet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

// Tableau d'enquête : un panneau de liège. À gauche, la feuille du profil génétique reconstitué ; à droite, les suspects
// en photos épinglées. Chaque résultat épinglé écarte les suspects incompatibles ; avec assez de résultats, on désigne
// un suspect et on lance la traque.
public class EcranTableau extends Screen {
    private static final Dessin.Fond F = Dessin.TABLEAU;
    private static final int L = F.il();
    private static final int PAPIER_X = 3, PAPIER_Y = 3, PAPIER_L = 128, PAPIER_H = 183;
    private static final int GRILLE_X = 134, PAS_X = 50, PAS_Y = 55, BOUTONS_Y = 171;
    private static final String[] RANGS = {T.f("common"), T.f("uncommon"), T.f("rare"), T.f("very_rare")};
    private String choix = "";

    public EcranTableau() {
        super(Component.literal(T.f("investigation_board")));
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
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        CompoundTag e = TraqueClient.etat;
        int x = F.x(width), y = F.y(height);
        Dessin.fond(g, F, width, height);
        // Le titre est gravé sur la plaque de laiton du cadre.
        Dessin.centre(g, T.f("case_board"), x + L / 2 + 1, y - 14, Dessin.ENCRE);

        if (!e.getBoolean("actif") && !e.getList("pistes", Tag.TAG_COMPOUND).isEmpty()) {
            pistes(g, e, x, y, mx, my);
            return;
        }
        if (!e.getBoolean("actif")) {
            long attente = e.getLong("attente") - (System.currentTimeMillis() - TraqueClient.etatRecu);
            Dessin.plaque(g, x + 38, y + 26, L - 76, 110);
            Dessin.centre(g, T.f("no_investigation_in_progress"), x + L / 2, y + 38, Dessin.TEXTE);
            int ly0 = Dessin.paragraphe(g, T.f("open_an_investigation_to_get"),
                    x + 50, y + 52, L - 100, Dessin.GRIS);
            Dessin.paragraphe(g, T.f("each_star_you_keep_5"), x + 50, ly0 + 2, L - 100, Dessin.JAUNE);
            boolean pret = attente <= 0;
            String libelle = pret ? T.f("check_the_reports") : T.f("next_report_in") + (attente / 60000 + 1) + T.f("min");
            Dessin.bouton(g, x + L / 2 - 90, y + 144, 180, 22, libelle, Dessin.dans(mx, my, x + L / 2 - 90, y + 144, 180, 22), pret, Dessin.ACCENT);
            Dessin.centre(g, T.f("cases_solved") + e.getInt("reussies"), x + L / 2, y + 173, Dessin.ENCRE);
            reglage(g, x, y, mx, my);
            return;
        }

        String phase = e.getString("phase");
        boolean prelevements = phase.equals("PRELEVEMENTS");
        ListTag traces = e.getList("traces", Tag.TAG_COMPOUND);
        ListTag suspects = e.getList("suspects", Tag.TAG_COMPOUND);
        int epingles = 0;

        // La feuille du profil : une bande surlignée par marqueur.
        int px = x + PAPIER_X, py = y + PAPIER_Y, tx = px + 15, tl = 98;
        Dessin.image(g, Dessin.PAPIER, px, py, PAPIER_L, PAPIER_H);
        // Qualité de l'enquête : c'est elle qui fixe les IV du Pokémon capturé et le butin.
        g.drawString(Dessin.police(), T.etoiles(e.getInt("etoiles")), tx, py + 22, 0xFFB07A10, false);
        String special = e.getString("special");
        if (!special.isEmpty()) g.drawString(Dessin.police(), "✦", tx + 50, py + 22, couleurSpecial(special), false);
        boolean survolEtoiles = Dessin.dans(mx, my, tx - 2, py + 20, 62, 12);
        g.drawString(Dessin.police(), T.f("species") + RANGS[Math.min(3, e.getInt("rang"))], tx, py + 37, Dessin.ENCRE_ROUGE, false);
        for (int i = 0; i < Genetique.MARQUEURS.size(); i++) {
            String m = Genetique.MARQUEURS.get(i);
            int ly = py + 49 + i * 20;
            g.drawString(Dessin.police(), T.c(Genetique.NOMS_MARQUEURS.get(m)), tx, ly, Dessin.ENCRE_CLAIRE, false);
            CompoundTag trace = null;
            for (int k = 0; k < traces.size(); k++) {
                if (traces.getCompound(k).getString("genre").equals(m)) trace = traces.getCompound(k);
            }
            if (trace != null && trace.getBoolean("epingle")) {
                epingles++;
                String v = trace.getString("valeur");
                Dessin.puce(g, tx, ly + 9, tl, 10, Genetique.couleur(m, v), Genetique.libelle(m, v).getString());
            } else {
                boolean recolte = trace != null && trace.getBoolean("recolte");
                g.fill(tx, ly + 9, tx + tl, ly + 19, 0x30402A14);
                g.fill(tx, ly + 18, tx + tl, ly + 19, 0x60402A14);
                boolean perdue = trace != null && trace.getBoolean("perdue"), aDeterrer = trace != null && trace.getBoolean("enfoui") && !trace.getBoolean("degage");
                g.drawString(Dessin.police(), perdue ? T.f("clue_destroyed") : recolte ? T.f("to_analyse") : aDeterrer ? T.f("to_dig_up") : T.f("to_sample"), tx + 3, ly + 10,
                        perdue ? Dessin.ENCRE_ROUGE : recolte ? Dessin.ENCRE : Dessin.ENCRE_CLAIRE, false);
            }
        }
        if (prelevements && minecraft != null && minecraft.player != null) {
            double zx = e.getDouble("zoneX"), zz = e.getDouble("zoneZ");
            double dist = Math.hypot(zx - minecraft.player.getX(), zz - minecraft.player.getZ());
            g.drawString(Dessin.police(), T.f("zone") + Math.round(dist) + T.f("blocks_2"), tx, py + 151, Dessin.ENCRE, false);
            g.drawString(Dessin.police(), T.f("compass") + Dessin.vent(Dessin.cap(minecraft.player.getX(), minecraft.player.getZ(), zx, zz)), tx, py + 162, Dessin.ENCRE_ROUGE, false);
        } else {
            g.drawString(Dessin.police(), T.f("hunt_in_progress"), tx, py + 151, Dessin.ENCRE, false);
            g.drawString(Dessin.police(), T.f("kit_map"), tx, py + 162, Dessin.ENCRE_ROUGE, false);
        }

        // Les suspects : des photos épinglées, barrées quand un résultat les écarte.
        int restants = 0;
        String seul = "";
        CompoundTag survole = null;
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            CompoundTag s = suspects.getCompound(i);
            int cx = x + GRILLE_X + (i % 4) * PAS_X, cy = y + (i / 4) * PAS_Y;
            boolean elimine = s.getBoolean("elimine");
            String espece = s.getString("espece");
            boolean choisi = espece.equals(prelevements ? choix : e.getString("pronostic"));
            if (!elimine) {
                restants++;
                seul = espece;
            }
            if (choisi && !elimine) g.fill(cx - 2, cy + 2, cx + 50, cy + 62, Dessin.JAUNE);
            Dessin.image(g, Dessin.POLAROID, cx, cy, 48, 60);
            Dessin.portrait(g, espece, cx + 8, cy + 12, 2f);
            ListTag valeurs = s.getList("valeurs", Tag.TAG_STRING);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            for (int m = 0; m < valeurs.size() && m < 5; m++) {
                int c = Dessin.opaque(Genetique.couleur(Genetique.MARQUEURS.get(m), valeurs.getString(m)));
                g.fill(cx + 4 + m * 8, cy + 50, cx + 11 + m * 8, cy + 56, Dessin.ENCRE);
                g.fill(cx + 5 + m * 8, cy + 51, cx + 10 + m * 8, cy + 55, c);
            }
            if (s.getBoolean("possede")) Dessin.pokeBall(g, cx + 32, cy + 9);
            if (elimine) {
                g.fill(cx + 1, cy + 5, cx + 47, cy + 59, 0x9C241A12);
                Dessin.image(g, Dessin.CROIX, cx + 10, cy + 14, 28, 28);
            }
            g.pose().popPose();
            if (Dessin.dans(mx, my, cx, cy + 4, 48, 56)) survole = s;
        }
        // Les épingles passent par-dessus tout : sinon la photo de la rangée du dessus (et son voile) en cache le haut.
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            Dessin.image(g, Dessin.EPINGLE, x + GRILLE_X + (i % 4) * PAS_X + 18, y + (i / 4) * PAS_Y, 12, 10);
        }
        g.pose().popPose();
        if (restants == 1 && prelevements) choix = seul;

        // Barre du bas : le bouton de traque dit lui-même ce qu'il attend.
        int bx = x + GRILLE_X, by = y + BOUTONS_Y;
        if (prelevements) {
            int requis = e.getInt("requis");
            boolean pret = epingles >= requis && !choix.isEmpty();
            String libelle = epingles < requis ? T.f("results") + epingles + " / " + requis : choix.isEmpty() ? T.f("pick_a_suspect") : T.f("track") + Dessin.nomEspece(choix);
            if (Dessin.police().width(libelle) > 112) libelle = T.f("start_the_hunt");
            Dessin.bouton(g, bx, by, 122, 18, libelle, Dessin.dans(mx, my, bx, by, 122, 18), pret, Dessin.VERT);
        } else {
            g.drawString(Dessin.police(), T.f("guess") + Dessin.nomEspece(e.getString("pronostic")), bx, by, Dessin.ENCRE, false);
            g.drawString(Dessin.police(), phase.equals("DEBUSQUE") ? T.f("target_flushed_out") : T.f("in_the_field_with_the"), bx, by + 10, Dessin.ENCRE_ROUGE, false);
        }
        Dessin.bouton(g, bx + 126, by, 72, 18, T.f("give_up"), Dessin.dans(mx, my, bx + 126, by, 72, 18), true, Dessin.ROUGE_SOMBRE);

        if (survolEtoiles) {
            int et = e.getInt("etoiles");
            List<Component> lignes = new ArrayList<>();
            lignes.add(Component.literal(T.f("investigation_quality") + et + " / 5"));
            lignes.add(Component.literal("§b" + T.iv(et) + T.f("perfect_ivs_7on_the_caught")));
            lignes.add(Component.literal(T.f("7and_richer_loot_with_each")));
            lignes.add(Component.literal(T.f("8_one_star_no_perfect")));
            lignes.add(Component.literal(T.f("8mini_game_failure_half") + e.getInt("fautes") + ")"));
            lignes.add(Component.literal(T.f("8target_ran_away_1") + e.getInt("fuites") + ")"));
            lignes.add(Component.literal(T.f("8wrong_guess_2")));
            if (!special.isEmpty()) {
                lignes.add(Component.literal("§6✦ " + T.f("investigation") + nomSpecial(special).toLowerCase()));
                for (String ligne : reglesSpecial(special)) lignes.add(Component.literal(ligne));
                lignes.add(Component.literal("§e" + gainSpecial(special)));
            }
            Dessin.infobulle(g, lignes, mx, my, width);
        } else if (survole != null) {
            List<Component> lignes = new ArrayList<>();
            lignes.add(Component.literal(Dessin.nomEspece(survole.getString("espece")) + (survole.getBoolean("elimine") ? T.f("ruled_out") : "")));
            ListTag valeurs = survole.getList("valeurs", Tag.TAG_STRING);
            for (int m = 0; m < valeurs.size() && m < 5; m++) {
                String marqueur = Genetique.MARQUEURS.get(m);
                lignes.add(Component.literal("§7" + T.c(Genetique.NOMS_MARQUEURS.get(marqueur)) + " : §f").append(Genetique.libelle(marqueur, valeurs.getString(m))));
            }
            lignes.add(Component.literal(survole.getBoolean("possede") ? T.f("aalready_caught") : T.f("7not_caught_yet")));
            if (prelevements) {
                lignes.add(Component.literal(T.f("8left_click_name_this_suspect")));
                lignes.add(Component.literal(T.f("8right_click_rule_out_restore")));
            }
            Dessin.infobulle(g, lignes, mx, my, width);
        }
    }

    // ---------- Signalements à choisir ----------

    private static final int CARTE_L = 104, CARTE_H = 130, CARTE_Y = 21, CARTE_PAS = 110;

    static String nomSpecial(String special) {
        return switch (special) {
            case "nocturne" -> T.f("nocturnal");
            case "craintive" -> T.f("skittish");
            case "exceptionnel" -> T.f("exceptional");
            default -> T.f("standard");
        };
    }

    static int couleurSpecial(String special) {
        return switch (special) {
            case "nocturne" -> 0xFF8FA8FF;
            case "craintive" -> Dessin.JAUNE;
            case "exceptionnel" -> 0xFFE9A15B;
            default -> Dessin.GRIS;
        };
    }

    static String[] reglesSpecial(String special) {
        return switch (special) {
            case "nocturne" -> new String[] {T.f("7traces_and_cries_only_at"), T.f("7the_target_only_comes_out")};
            case "craintive" -> new String[] {T.f("7it_senses_you_from_much"), T.f("7scan_it_from_a_long")};
            case "exceptionnel" -> new String[] {T.f("7one_more_analysis_result_required"), T.f("7and_suspects_that_all_look")};
            default -> new String[] {T.f("7a_regular_investigation")};
        };
    }

    static String gainSpecial(String special) {
        return switch (special) {
            case "nocturne" -> T.f("shiny_odds_2_special_loot");
            case "craintive" -> T.f("double_special_loot");
            case "exceptionnel" -> T.f("hidden_ability_special_loot");
            default -> T.f("ivs_and_loot_by_your");
        };
    }

    private static int carteX(int x, int n, int i) {
        return x + (L - (n * CARTE_L + (n - 1) * (CARTE_PAS - CARTE_L))) / 2 + i * CARTE_PAS;
    }

    // Trois signalements épinglés au tableau : le joueur choisit son enquête (rareté, type, distance).
    private void pistes(GuiGraphics g, CompoundTag e, int x, int y, int mx, int my) {
        ListTag pistes = e.getList("pistes", Tag.TAG_COMPOUND);
        int n = Math.min(3, pistes.size());
        long reste = e.getLong("pistesFin") - (System.currentTimeMillis() - TraqueClient.etatRecu);
        Dessin.centre(g, T.f("pick_your_investigation"), x + L / 2, y + 8, Dessin.ENCRE);
        CompoundTag survole = null;
        for (int i = 0; i < n; i++) {
            CompoundTag p = pistes.getCompound(i);
            int cx = carteX(x, n, i), cy = y + CARTE_Y, tx = cx + 9;
            boolean survol = Dessin.dans(mx, my, cx, cy, CARTE_L, CARTE_H);
            String special = p.getString("special");
            if (survol) {
                g.fill(cx - 2, cy - 2, cx + CARTE_L + 2, cy + CARTE_H + 2, Dessin.JAUNE);
                survole = p;
            }
            Dessin.plaque(g, cx, cy, CARTE_L, CARTE_H);
            g.drawString(Dessin.police(), T.f("lead") + (i + 1), tx, cy + 9, Dessin.ACCENT, false);
            g.drawString(Dessin.police(), (special.isEmpty() ? "" : "✦ ") + nomSpecial(special), tx, cy + 21, couleurSpecial(special), false);
            g.fill(tx, cy + 32, cx + CARTE_L - 9, cy + 33, 0x40FFFFFF);
            g.drawString(Dessin.police(), T.f("species_2"), tx, cy + 37, Dessin.GRIS, false);
            g.drawString(Dessin.police(), RANGS[Math.min(3, p.getInt("rang"))], tx, cy + 47, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), T.f("zone_2"), tx, cy + 60, Dessin.GRIS, false);
            String ou = "?";
            if (minecraft != null && minecraft.player != null) {
                double zx = p.getDouble("zoneX"), zz = p.getDouble("zoneZ"), jx = minecraft.player.getX(), jz = minecraft.player.getZ();
                ou = Math.round(Math.hypot(zx - jx, zz - jz)) + " m · " + Dessin.vent(Dessin.cap(jx, jz, zx, zz));
            }
            g.drawString(Dessin.police(), ou, tx, cy + 70, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), p.getBoolean("nouvelle") ? T.f("never_caught") : T.f("already_caught"), tx, cy + 84,
                    p.getBoolean("nouvelle") ? Dessin.VERT : Dessin.SOMBRE, false);
            Dessin.paragraphe(g, gainSpecial(special), tx, cy + 96, CARTE_L - 18, Dessin.JAUNE);
        }
        Dessin.centre(g, T.f("reports_valid_for_another") + Math.max(1, reste / 60000 + 1) + T.f("min"), x + L / 2, y + 157, Dessin.ENCRE);
        Dessin.centre(g, T.f("cases_solved") + e.getInt("reussies"), x + L / 2, y + 173, Dessin.ENCRE);
        reglage(g, x, y, mx, my);
        if (survole != null) {
            List<Component> lignes = new ArrayList<>();
            String special = survole.getString("special");
            lignes.add(Component.literal(T.f("investigation") + nomSpecial(special).toLowerCase()));
            for (String ligne : reglesSpecial(special)) lignes.add(Component.literal(ligne));
            lignes.add(Component.literal("§e" + gainSpecial(special)));
            lignes.add(Component.literal(T.f("8click_open_this_investigation")));
            Dessin.infobulle(g, lignes, mx, my, width);
        }
    }

    // Réglage d'accessibilité, en bas à gauche du tableau : l'aide aux couleurs chiffre les teintes des mini-jeux.
    private void reglage(GuiGraphics g, int x, int y, int mx, int my) {
        String texte = T.f("colour_aid") + (TraqueClient.aideCouleurs ? T.f("on") : T.f("off"));
        boolean survol = Dessin.dans(mx, my, x + 4, y + 171, Dessin.police().width(texte) + 4, 12);
        g.drawString(Dessin.police(), texte, x + 6, y + 173, survol ? Dessin.ENCRE_ROUGE : Dessin.ENCRE, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        CompoundTag e = TraqueClient.etat;
        int x = F.x(width), y = F.y(height);
        if (!e.getBoolean("actif") && Dessin.dans(mx, my, x + 4, y + 171, 96, 12)) {
            TraqueClient.basculerAideCouleurs();
            Dessin.son(1.2f);
            return true;
        }
        ListTag pistes = e.getList("pistes", Tag.TAG_COMPOUND);
        if (!e.getBoolean("actif") && !pistes.isEmpty()) {
            int n = Math.min(3, pistes.size());
            for (int i = 0; i < n; i++) {
                if (!Dessin.dans(mx, my, carteX(x, n, i), y + CARTE_Y, CARTE_L, CARTE_H)) continue;
                Paquet p = Paquet.de("choisir");
                p.donnees().putInt("index", i);
                TraqueClient.envoyer(p);
                Dessin.son(1.3f);
            }
            return true;
        }
        if (!e.getBoolean("actif")) {
            long attente = e.getLong("attente") - (System.currentTimeMillis() - TraqueClient.etatRecu);
            if (attente <= 0 && Dessin.dans(mx, my, x + L / 2 - 90, y + 144, 180, 22)) {
                Dessin.son(1f);
                TraqueClient.envoyer(Paquet.de("ouvrir_enquete"));
            }
            return true;
        }
        int bx = x + GRILLE_X, by = y + BOUTONS_Y;
        if (Dessin.dans(mx, my, bx + 126, by, 72, 18)) {
            Dessin.son(0.7f);
            TraqueClient.envoyer(Paquet.de("abandonner"));
            return true;
        }
        // Le bas de la feuille mène à la boussole (ou à la carte) du kit.
        if (Dessin.dans(mx, my, x + PAPIER_X, y + PAPIER_Y + 146, PAPIER_L, 30) && minecraft != null) {
            Dessin.son(1f);
            minecraft.setScreen(new EcranKit());
            return true;
        }
        if (!e.getString("phase").equals("PRELEVEMENTS")) return true;
        ListTag suspects = e.getList("suspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            CompoundTag s = suspects.getCompound(i);
            int cx = x + GRILLE_X + (i % 4) * PAS_X, cy = y + (i / 4) * PAS_Y;
            if (!Dessin.dans(mx, my, cx, cy + 4, 48, 56)) continue;
            if (bouton == 1) {
                // Clic droit : le joueur barre (ou rétablit) lui-même ce suspect.
                boolean barre = !s.getBoolean("elimine");
                s.putBoolean("elimine", barre);
                if (barre && choix.equals(s.getString("espece"))) choix = "";
                Paquet p = Paquet.de("ecarter");
                p.donnees().putString("espece", s.getString("espece"));
                TraqueClient.envoyer(p);
                Dessin.son(barre ? 0.8f : 1.1f);
            } else if (!s.getBoolean("elimine")) {
                choix = s.getString("espece");
                Dessin.son(1.3f);
            }
            return true;
        }
        if (Dessin.dans(mx, my, bx, by, 122, 18) && !choix.isEmpty()) {
            Paquet p = Paquet.de("lancer");
            p.donnees().putString("espece", choix);
            TraqueClient.envoyer(p);
            Dessin.son(1f);
        }
        return true;
    }
}
