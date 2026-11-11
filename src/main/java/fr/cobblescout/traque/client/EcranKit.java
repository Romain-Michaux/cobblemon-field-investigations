package fr.cobblescout.traque.client;

import fr.cobblescout.traque.Genetique;
import fr.cobblescout.traque.Paquet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Écran du kit de terrain : pendant les prélèvements, l'état du dossier et la direction de la zone ;
// pendant la traque, la carte des relevés (les droites de cap, leur marge d'erreur et le recoupement).
// Deux autres onglets donnent la carrière de l'enquêteur : le carnet (rang, expérience, améliorations du kit) et les
// archives (dossiers classés, types d'enquête déjà résolus). Tout tient dans l'écran du boîtier (texture fond_kit).
public class EcranKit extends Screen {
    private static final Dessin.Fond F = Dessin.KIT;
    private static final int L = F.il(), CARTE = 148;
    static final ResourceLocation INSIGNE = Dessin.texture("insigne");
    // Couleur de l'insigne et du nom de chaque rang, du stagiaire à la légende.
    static final int[] COULEURS_RANGS = {0xFFB08D6A, 0xFFE0A060, 0xFFE8EEF2, 0xFFF2C93A, 0xFF6CCB5F, 0xFF4FD1C5, 0xFFC79CFF};
    static final String[] TYPES = {"", "nocturne", "craintive", "exceptionnel", "migratrice", "aquatique", "fouille", "cimes", "altitude", "givre", "brume", "souterraine"};
    // 0 = enquête en cours, 1 = carnet de l'enquêteur, 2 = archives, 3 = équipe.
    private int onglet;
    private int defilement;
    // Onglet Équipe : texte tapé pour chercher un joueur, et position dans la liste des joueurs à inviter.
    private String recherche = "";
    private int defilementInvites;

    // Les joueurs qu'on peut inviter, filtrés par la recherche (l'ordre vient du serveur : partenaires habituels d'abord).
    private java.util.List<CompoundTag> invitables() {
        ListTag proches = TraqueClient.etat.getList("proches", Tag.TAG_COMPOUND);
        java.util.List<CompoundTag> liste = new java.util.ArrayList<>();
        for (int i = 0; i < proches.size(); i++) {
            CompoundTag c = proches.getCompound(i);
            if (recherche.isEmpty() || c.getString("nom").toLowerCase().contains(recherche.toLowerCase())) liste.add(c);
        }
        return liste;
    }

    public EcranKit() {
        this(0);
    }

    public EcranKit(int onglet) {
        super(Component.literal(T.f("field_kit")));
        this.onglet = onglet;
    }

    private static String[] onglets() {
        return new String[] {T.f("case"), T.f("logbook"), T.f("archives"), T.f("team")};
    }

    // Abscisse et largeur de l'onglet i, dans le bandeau du haut.
    private int[] placeOnglet(int x, int i) {
        String[] noms = onglets();
        int px = x + 15;
        for (int k = 0; k < i; k++) px += Dessin.police().width(noms[k]) + 12;
        return new int[] {px, Dessin.police().width(noms[i])};
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
        boolean actif = e.getBoolean("actif") && minecraft != null && minecraft.player != null;
        String phase = e.getString("phase");
        String etape = !actif ? T.f("standby") : phase.equals("PRELEVEMENTS") ? T.f("sampling") : phase.equals("TRAQUE") ? T.f("tracking")
                : phase.equals("REPERAGE") ? T.f("spotting") : phase.equals("APPROCHE") ? T.f("approach") : T.f("flushed_out");
        Dessin.bandeau(g, x, y, L, "", actif ? T.etoiles(e.getInt("etoiles")) : "");
        String[] noms = onglets();
        for (int i = 0; i < noms.length; i++) {
            int[] place = placeOnglet(x, i);
            boolean survol = Dessin.dans(mx, my, place[0] - 3, y, place[1] + 6, 14);
            Dessin.ecrire(g, noms[i], place[0], y + 3, i == onglet ? Dessin.TEXTE : survol ? Dessin.JAUNE : Dessin.GRIS, false);
            if (i == onglet) g.fill(place[0], y + 12, place[0] + place[1], y + 14, Dessin.ACCENT);
        }
        if (onglet == 1) {
            carnet(g, e.getCompound("carriere"), e.getInt("reussies"), x, y, mx, my);
            return;
        }
        if (onglet == 2) {
            archives(g, e.getCompound("carriere"), x, y);
            return;
        }
        if (onglet == 3) {
            equipe(g, e, x, y, mx, my);
            return;
        }
        if (!actif) {
            Dessin.centre(g, T.f("no_investigation_in_progress"), x + L / 2, y + 70, Dessin.GRIS);
            Dessin.bouton(g, x + L / 2 - 80, y + 90, 160, 22, T.f("check_the_reports"), Dessin.dans(mx, my, x + L / 2 - 80, y + 90, 160, 22), true, Dessin.ACCENT);
            return;
        }
        double jx = minecraft.player.getX(), jz = minecraft.player.getZ();
        if (phase.equals("PRELEVEMENTS")) {
            prelevements(g, e, x, y, jx, jz, mx, my);
        } else {
            carte(g, e, x, y, jx, jz);
        }
    }

    private void prelevements(GuiGraphics g, CompoundTag e, int x, int y, double jx, double jz, int mx, int my) {
        ListTag traces = e.getList("traces", Tag.TAG_COMPOUND);
        Dessin.encart(g, x + 4, y + 18, 188, 148);
        Dessin.ecrire(g, T.f("samples"), x + 10, y + 23, Dessin.JAUNE, false);
        for (int i = 0; i < traces.size() && i < 5; i++) {
            CompoundTag t = traces.getCompound(i);
            String genre = t.getString("genre");
            int ly = y + 37 + i * 25;
            boolean epingle = t.getBoolean("epingle"), recolte = t.getBoolean("recolte");
            boolean enfoui = t.getBoolean("enfoui"), degage = t.getBoolean("degage"), perdue = t.getBoolean("perdue");
            int couleur = perdue ? Dessin.ALERTE : epingle ? Dessin.VERT : recolte ? Dessin.JAUNE : enfoui && degage ? Dessin.ACCENT : Dessin.SOMBRE;
            if (enfoui) Dessin.image(g, Dessin.iconeEnfoui(genre, t.getInt("variante")), x + 12, ly + 2, 16, 16);
            else Dessin.image(g, Dessin.iconeTrace(genre, false), x + 8, ly - 2, 24, 24);
            // La fiole : éteinte tant que la trace n'est pas prélevée, pleine ensuite, pastille verte une fois analysée.
            if (perdue || (!epingle && !recolte)) Dessin.teinte(g, 0xFF46525E);
            Dessin.image(g, Dessin.FIOLE, x + 172, ly, 10, 20);
            g.setColor(1f, 1f, 1f, 1f);
            if (epingle) {
                g.fill(x + 179, ly + 13, x + 185, ly + 19, Dessin.BORD);
                g.fill(x + 180, ly + 14, x + 184, ly + 18, Dessin.VERT);
            }
            Dessin.ecrire(g, T.c(Genetique.nomTrace(genre, enfoui, t.getInt("variante"))), x + 36, ly + 1, perdue ? Dessin.SOMBRE : Dessin.TEXTE, false);
            Dessin.ecrire(g, perdue ? T.f("clue_destroyed") : epingle ? T.f("analysed_in_the_file") : recolte ? T.f("sampled_to_analyse")
                    : enfoui ? (degage ? T.f("uncovered_to_sample") : T.f("buried_brush_needed")) : T.f("to_sample_in_the_zone"), x + 36, ly + 11, couleur, false);
        }
        // Boussole vers la zone des traces (le haut du cadran est la direction du regard).
        int cx = x + 256, cy = y + 72;
        Dessin.encart(g, x + 196, y + 18, 120, 148);
        Dessin.ecrire(g, T.f("trace_zone"), x + 202, y + 23, Dessin.JAUNE, false);
        double zx = e.getDouble("zoneX"), zz = e.getDouble("zoneZ");
        double cap = Dessin.cap(jx, jz, zx, zz);
        double relatif = Math.toRadians(cap - (minecraft.player.getYRot() + 180));
        Dessin.image(g, Dessin.CADRAN, cx - 36, cy - 36, 72, 72);
        Dessin.segment(g, cx - 1, cy - 1, cx - 1 + Math.sin(relatif) * 22, cy - 1 - Math.cos(relatif) * 22, 2, Dessin.ACCENT);
        Dessin.point(g, cx + Math.sin(relatif) * 22 - 2, cy - Math.cos(relatif) * 22 - 2, 4, Dessin.JAUNE);
        Dessin.centre(g, Math.round(Math.hypot(zx - jx, zz - jz)) + T.f("blocks") + Dessin.vent(cap), cx, y + 114, Dessin.TEXTE);
        Dessin.centre(g, "X " + Math.round(zx) + "  Z " + Math.round(zz), cx, y + 125, Dessin.GRIS);
        Dessin.bouton(g, x + 202, y + 140, 108, 22, T.f("case_file"), Dessin.dans(mx, my, x + 202, y + 140, 108, 22), true, Dessin.ACCENT);
    }

    // ---------- Carnet : rang, expérience, améliorations du kit ----------

    private void carnet(GuiGraphics g, CompoundTag c, int reussies, int x, int y, int mx, int my) {
        int rang = Math.max(0, Math.min(6, c.getInt("rang"))), xp = c.getInt("xp"), seuil = c.getInt("seuil"), suivant = c.getInt("suivant");
        int couleur = COULEURS_RANGS[rang];
        Dessin.encart(g, x + 4, y + 18, 124, 148);
        // L'insigne, teinté à la couleur du rang.
        Dessin.teinte(g, couleur);
        Dessin.morceauAgrandi(g, INSIGNE, x + 34, y + 23, 64, 64, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        Dessin.centre(g, T.c("cobblemon_field_investigations.rang." + rang), x + 66, y + 90, couleur);
        g.fill(x + 14, y + 103, x + 118, y + 110, Dessin.BORD);
        double part = suivant > seuil ? (xp - seuil) / (double) (suivant - seuil) : 1;
        g.fill(x + 15, y + 104, x + 15 + (int) (102 * Math.max(0, Math.min(1, part))), y + 109, couleur);
        Dessin.centre(g, suivant > 0 ? xp + " / " + suivant + " XP" : xp + " XP · " + T.f("top_rank"), x + 66, y + 113, Dessin.GRIS);
        Dessin.ecrire(g, T.f("cases") + reussies, x + 12, y + 128, Dessin.TEXTE, false);
        Dessin.ecrire(g, T.f("five_stars") + c.getInt("parfaites"), x + 12, y + 139, Dessin.JAUNE, false);
        Dessin.ecrire(g, T.f("species_3") + c.getInt("especes"), x + 12, y + 150, Dessin.ACCENT, false);

        Dessin.encart(g, x + 132, y + 18, 184, 148);
        Dessin.ecrire(g, T.f("kit_upgrades"), x + 138, y + 23, Dessin.JAUNE, false);
        String[] titres = {T.f("sharper_radar"), T.f("spare_vial"), T.f("quick_scan"), T.f("cool_head"), T.f("keen_ear"), T.f("informant_network")};
        String[] effets = {T.f("detects_from_farther_away"), T.f("first_sample_needs_no_vial"), T.f("target_identified_in_2_s"), T.f("one_more_failure_forgiven"),
                T.f("more_precise_cry_bearings"), T.f("wait_between_cases_2")};
        int survole = -1;
        for (int i = 0; i < 6; i++) {
            int ly = y + 36 + i * 21;
            boolean acquis = rang >= i + 1;
            g.fill(x + 138, ly, x + 154, ly + 18, Dessin.BORD);
            g.fill(x + 139, ly + 1, x + 153, ly + 17, acquis ? 0xFF245C3A : 0xFF1A2633);
            Dessin.centre(g, acquis ? "✔" : String.valueOf(i + 1), x + 146, ly + 5, acquis ? Dessin.VERT : COULEURS_RANGS[i + 1]);
            Dessin.ecrire(g, titres[i], x + 158, ly, acquis ? Dessin.TEXTE : Dessin.GRIS, false);
            Dessin.ecrire(g, effets[i], x + 158, ly + 10, acquis ? Dessin.VERT : Dessin.SOMBRE, false);
            if (Dessin.dans(mx, my, x + 136, ly - 1, 176, 20)) survole = i;
        }
        // Au survol : le rang qui débloque l'amélioration.
        if (survole >= 0) {
            java.util.List<Component> lignes = new java.util.ArrayList<>();
            lignes.add(Component.literal(titres[survole]));
            lignes.add(Component.literal((rang >= survole + 1 ? T.f("aunlocked_at_rank") : T.f("7unlocks_at_rank")) + T.c("cobblemon_field_investigations.rang." + (survole + 1))));
            Dessin.infobulle(g, lignes, mx, my, width);
        }
    }

    // ---------- Archives : dossiers classés, types d'enquête déjà résolus ----------

    private void archives(GuiGraphics g, CompoundTag c, int x, int y) {
        ListTag fiches = c.getList("archives", Tag.TAG_COMPOUND);
        Dessin.encart(g, x + 4, y + 18, 196, 148);
        Dessin.ecrire(g, T.f("closed_cases"), x + 10, y + 23, Dessin.JAUNE, false);
        defilement = Math.max(0, Math.min(Math.max(0, fiches.size() - 6), defilement));
        if (fiches.isEmpty()) Dessin.paragraphe(g, T.f("no_closed_case_yet_every"), x + 12, y + 44, 180, Dessin.GRIS);
        java.text.SimpleDateFormat jour = new java.text.SimpleDateFormat("dd/MM");
        for (int i = 0; i < 6 && i + defilement < fiches.size(); i++) {
            CompoundTag f = fiches.getCompound(i + defilement);
            int ly = y + 36 + i * 21;
            String special = f.getString("special");
            Dessin.portrait(g, f.getString("espece"), x + 9, ly + 1, 1f);
            Dessin.defile(g, Dessin.nomEspece(f.getString("espece")), x + 29, ly, 104, Dessin.TEXTE);
            String etoiles = T.etoiles(f.getInt("etoiles"));
            Dessin.ecrire(g, etoiles, x + 194 - Dessin.police().width(etoiles), ly, Dessin.JAUNE, false);
            String type = EcranTableau.nomSpecial(special);
            Dessin.ecrire(g, type, x + 29, ly + 10, EcranTableau.couleurSpecial(special), false);
            String suite = " · " + (f.getBoolean("capture") ? T.f("caught") : T.f("defeated")) + " · " + jour.format(new java.util.Date(f.getLong("date")));
            Dessin.defile(g, suite, x + 29 + Dessin.police().width(type), ly + 10, 164 - Dessin.police().width(type), Dessin.SOMBRE);
        }
        if (fiches.size() > 6) {
            Dessin.ecrire(g, (defilement + 1) + "-" + Math.min(fiches.size(), defilement + 6) + " / " + fiches.size() + "  ▲▼", x + 118, y + 23, Dessin.GRIS, false);
        }
        // À droite : la collection des types d'enquête.
        Dessin.encart(g, x + 204, y + 18, 112, 148);
        CompoundTag types = c.getCompound("types");
        int resolus = 0;
        for (String t : TYPES) resolus += types.getInt(t.isEmpty() ? "ordinaire" : t) > 0 ? 1 : 0;
        Dessin.ecrire(g, T.f("kinds") + resolus + " / " + TYPES.length, x + 210, y + 23, Dessin.JAUNE, false);
        for (int i = 0; i < TYPES.length; i++) {
            int n = types.getInt(TYPES[i].isEmpty() ? "ordinaire" : TYPES[i]), ly = y + 35 + i * 10;
            Dessin.ecrire(g, n > 0 ? "✔" : "·", x + 210, ly, n > 0 ? Dessin.VERT : Dessin.SOMBRE, false);
            Dessin.ecrire(g, EcranTableau.nomSpecial(TYPES[i]), x + 221, ly, n > 0 ? EcranTableau.couleurSpecial(TYPES[i]) : Dessin.SOMBRE, false);
            if (n > 0) Dessin.ecrire(g, "×" + n, x + 310 - Dessin.police().width("×" + n), ly, Dessin.GRIS, false);
        }
    }

    // ---------- Équipe : coéquipiers, invitations ----------

    private void equipe(GuiGraphics g, CompoundTag e, int x, int y, int mx, int my) {
        ListTag membres = e.getList("equipe", Tag.TAG_STRING);
        String invitation = e.getString("invitation");
        boolean actif = e.getBoolean("actif"), chef = e.getBoolean("chef");
        Dessin.encart(g, x + 4, y + 18, 152, 148);
        g.drawString(Dessin.police(), T.f("team_2"), x + 10, y + 23, Dessin.JAUNE, false);
        if (!invitation.isEmpty()) {
            // Une invitation attend une réponse.
            int ly = Dessin.paragraphe(g, invitation + T.f("invites_you_to_join_their"), x + 12, y + 40, 136, Dessin.TEXTE);
            Dessin.paragraphe(g, T.f("you_will_work_on_their"), x + 12, ly + 4, 136, Dessin.GRIS);
            Dessin.bouton(g, x + 12, y + 118, 136, 20, T.f("join"), Dessin.dans(mx, my, x + 12, y + 118, 136, 20), true, Dessin.VERT);
            Dessin.bouton(g, x + 12, y + 141, 136, 18, T.f("decline"), Dessin.dans(mx, my, x + 12, y + 141, 136, 18), true, Dessin.ROUGE_SOMBRE);
        } else if (!actif) {
            Dessin.paragraphe(g, T.f("open_a_case_to_invite"), x + 12, y + 40, 136, Dessin.GRIS);
        } else {
            for (int i = 0; i < membres.size() && i < 6; i++) {
                int ly = y + 38 + i * 13;
                g.drawString(Dessin.police(), i == 0 ? "★" : "•", x + 12, ly, i == 0 ? Dessin.JAUNE : Dessin.ACCENT, false);
                g.drawString(Dessin.police(), membres.getString(i), x + 24, ly, Dessin.TEXTE, false);
            }
            g.drawString(Dessin.police(), membres.size() + " / " + e.getInt("equipeMax"), x + 150 - Dessin.police().width(membres.size() + " / " + e.getInt("equipeMax")), y + 23, Dessin.GRIS, false);
            if (!chef) Dessin.bouton(g, x + 12, y + 141, 136, 18, T.f("leave_the_team"), Dessin.dans(mx, my, x + 12, y + 141, 136, 18), true, Dessin.ROUGE_SOMBRE);
        }
        Dessin.encart(g, x + 160, y + 18, 156, 148);
        g.drawString(Dessin.police(), T.f("invite"), x + 166, y + 23, Dessin.JAUNE, false);
        if (!actif || !chef) {
            Dessin.paragraphe(g, actif ? T.f("only_the_lead_investigator_can") : T.f("everyone_samples_and_analyses_for"),
                    x + 168, y + 40, 140, Dessin.GRIS);
            return;
        }
        boolean plein = membres.size() >= e.getInt("equipeMax");
        if (plein) {
            Dessin.paragraphe(g, T.f("the_team_is_full"), x + 168, y + 40, 140, Dessin.GRIS);
            return;
        }
        // Champ de recherche : on tape directement le nom du joueur.
        g.fill(x + 166, y + 34, x + 310, y + 48, Dessin.BORD);
        g.fill(x + 167, y + 35, x + 309, y + 47, 0xFF101A24);
        boolean curseur = (System.currentTimeMillis() / 400) % 2 == 0;
        if (recherche.isEmpty()) g.drawString(Dessin.police(), T.f("search_for_a_player") , x + 171, y + 37, Dessin.SOMBRE, false);
        Dessin.defile(g, recherche + (curseur ? "_" : ""), x + 171, y + 37, 134, Dessin.TEXTE);
        java.util.List<CompoundTag> liste = invitables();
        defilementInvites = Math.max(0, Math.min(Math.max(0, liste.size() - 5), defilementInvites));
        if (liste.isEmpty()) {
            Dessin.paragraphe(g, recherche.isEmpty() ? T.f("no_online_player_is_free") : T.f("no_free_player_matches"), x + 168, y + 56, 140, Dessin.GRIS);
        }
        for (int i = 0; i < 5 && i + defilementInvites < liste.size(); i++) {
            CompoundTag c = liste.get(i + defilementInvites);
            int ly = y + 51 + i * 17, ensemble = c.getInt("ensemble");
            Dessin.bouton(g, x + 166, ly, 144, 16, c.getString("nom") + (ensemble > 0 ? "  ×" + ensemble : ""), Dessin.dans(mx, my, x + 166, ly, 144, 16), true,
                    ensemble > 0 ? Dessin.ACCENT : 0xFF7C8FA3);
        }
        String compte = liste.size() > 5 ? (defilementInvites + 1) + "-" + Math.min(liste.size(), defilementInvites + 5) + " / " + liste.size() + " ▲▼" : liste.size() + "";
        g.drawString(Dessin.police(), compte, x + 310 - Dessin.police().width(compte), y + 23, Dessin.GRIS, false);
        Dessin.bouton(g, x + 166, y + 146, 144, 16, T.f("refresh"), Dessin.dans(mx, my, x + 166, y + 146, 144, 16), true, 0xFF7C8FA3);
    }

    // La recherche se tape au clavier dès que l'onglet Équipe est ouvert.
    @Override
    public boolean charTyped(char c, int mods) {
        if (onglet == 3 && TraqueClient.etat.getBoolean("chef") && recherche.length() < 16 && (Character.isLetterOrDigit(c) || c == '_')) {
            recherche += c;
            defilementInvites = 0;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        if (onglet == 3 && touche == 259 && !recherche.isEmpty()) { // Retour arrière
            recherche = recherche.substring(0, recherche.length() - 1);
            defilementInvites = 0;
            return true;
        }
        return super.keyPressed(touche, scan, mods);
    }

    private boolean clicEquipe(double mx, double my, int x, int y) {
        CompoundTag e = TraqueClient.etat;
        if (!e.getString("invitation").isEmpty()) {
            boolean oui = Dessin.dans(mx, my, x + 12, y + 118, 136, 20), non = Dessin.dans(mx, my, x + 12, y + 141, 136, 18);
            if (oui || non) {
                Paquet p = Paquet.de("repondre");
                p.donnees().putBoolean("accepte", oui);
                TraqueClient.envoyer(p);
                Dessin.son(oui ? 1.3f : 0.7f);
                if (oui) onglet = 0;
            }
            return true;
        }
        if (!e.getBoolean("actif")) return true;
        if (!e.getBoolean("chef")) {
            if (Dessin.dans(mx, my, x + 12, y + 141, 136, 18)) {
                TraqueClient.envoyer(Paquet.de("abandonner"));
                Dessin.son(0.7f);
            }
            return true;
        }
        if (e.getList("equipe", Tag.TAG_STRING).size() < e.getInt("equipeMax")) {
            java.util.List<CompoundTag> liste = invitables();
            for (int i = 0; i < 5 && i + defilementInvites < liste.size(); i++) {
                if (!Dessin.dans(mx, my, x + 166, y + 51 + i * 17, 144, 16)) continue;
                Paquet p = Paquet.de("inviter");
                p.donnees().putString("nom", liste.get(i + defilementInvites).getString("nom"));
                TraqueClient.envoyer(p);
                Dessin.son(1.2f);
                return true;
            }
            if (Dessin.dans(mx, my, x + 166, y + 146, 144, 16)) {
                TraqueClient.envoyer(Paquet.de("equipe"));
                Dessin.son(1f);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (onglet == 2) defilement -= (int) Math.signum(dy);
        if (onglet == 3) defilementInvites -= (int) Math.signum(dy);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int x = F.x(width), y = F.y(height);
        for (int i = 0; i < 4; i++) {
            int[] place = placeOnglet(x, i);
            if (Dessin.dans(mx, my, place[0] - 3, y, place[1] + 6, 14)) {
                onglet = i;
                Dessin.son(1.2f);
                // L'onglet Équipe demande au serveur la liste à jour des joueurs proches.
                if (i == 3) TraqueClient.envoyer(Paquet.de("equipe"));
                return true;
            }
        }
        if (onglet == 3) return clicEquipe(mx, my, x, y);
        if (onglet != 0) return true;
        if (!TraqueClient.etat.getBoolean("actif")) {
            if (Dessin.dans(mx, my, x + L / 2 - 80, y + 90, 160, 22) && minecraft != null) {
                Dessin.son(1f);
                minecraft.setScreen(new EcranTableau());
            }
            return true;
        }
        if (TraqueClient.etat.getString("phase").equals("PRELEVEMENTS") && Dessin.dans(mx, my, x + 202, y + 140, 108, 22) && minecraft != null) {
            Dessin.son(1f);
            minecraft.setScreen(new EcranTableau());
        }
        return true;
    }

    private void carte(GuiGraphics g, CompoundTag e, int x, int y, double jx, double jz) {
        ListTag releves = e.getList("releves", Tag.TAG_COMPOUND);
        boolean rec = e.getBoolean("recValide");
        double rx = e.getDouble("recX"), rz = e.getDouble("recZ"), inc = e.getDouble("recIncertitude");
        // Cadrage : tout ce qui compte doit tenir dans la carte.
        double minX = jx, maxX = jx, minZ = jz, maxZ = jz;
        for (int i = 0; i < releves.size(); i++) {
            CompoundTag r = releves.getCompound(i);
            if (r.getBoolean("perime")) continue;
            minX = Math.min(minX, r.getDouble("x"));
            maxX = Math.max(maxX, r.getDouble("x"));
            minZ = Math.min(minZ, r.getDouble("z"));
            maxZ = Math.max(maxZ, r.getDouble("z"));
        }
        if (rec) {
            minX = Math.min(minX, rx - inc);
            maxX = Math.max(maxX, rx + inc);
            minZ = Math.min(minZ, rz - inc);
            maxZ = Math.max(maxZ, rz + inc);
        }
        double mx0 = (minX + maxX) / 2, mz0 = (minZ + maxZ) / 2;
        double demi = Math.max(80, Math.max(maxX - minX, maxZ - minZ) / 2 * 1.2);
        double echelle = (CARTE / 2.0) / demi;
        int ox = x + 4, oy = y + 18;
        Dessin.encart(g, ox, oy, CARTE + 2, CARTE + 2);
        g.fill(ox + 1, oy + 1, ox + CARTE + 1, oy + CARTE + 1, 0xFF101A24);
        g.enableScissor(ox + 1, oy + 1, ox + CARTE + 1, oy + CARTE + 1);
        int ccx = ox + 1 + CARTE / 2, ccy = oy + 1 + CARTE / 2;
        // Quadrillage tous les 50 blocs.
        for (double q = Math.ceil((mx0 - demi) / 50) * 50; q < mx0 + demi; q += 50) {
            int px = (int) (ccx + (q - mx0) * echelle);
            g.fill(px, oy + 1, px + 1, oy + CARTE + 1, 0xFF1C2C3B);
        }
        for (double q = Math.ceil((mz0 - demi) / 50) * 50; q < mz0 + demi; q += 50) {
            int py = (int) (ccy + (q - mz0) * echelle);
            g.fill(ox + 1, py, ox + CARTE + 1, py + 1, 0xFF1C2C3B);
        }
        int n = 0;
        for (int i = 0; i < releves.size(); i++) {
            CompoundTag r = releves.getCompound(i);
            if (r.getBoolean("perime")) continue;
            n++;
            double px = ccx + (r.getDouble("x") - mx0) * echelle, py = ccy + (r.getDouble("z") - mz0) * echelle;
            double longueur = CARTE * 2;
            for (int cote = -1; cote <= 1; cote++) {
                double a = Math.toRadians(r.getDouble("cap") + cote * r.getDouble("erreur"));
                Dessin.segment(g, px, py, px + Math.sin(a) * longueur, py - Math.cos(a) * longueur, cote == 0 ? 2 : 1, cote == 0 ? Dessin.ACCENT : 0xFF2C6F73);
            }
            g.fill((int) px - 2, (int) py - 2, (int) px + 3, (int) py + 3, Dessin.TEXTE);
            Dessin.ecrire(g, String.valueOf(i + 1), (int) px + 4, (int) py - 9, Dessin.TEXTE, false);
        }
        if (rec) {
            double px = ccx + (rx - mx0) * echelle, py = ccy + (rz - mz0) * echelle;
            Dessin.cercle(g, px, py, inc * echelle, 2, Dessin.JAUNE);
            Dessin.segment(g, px - 4, py, px + 4, py, 2, Dessin.JAUNE);
            Dessin.segment(g, px, py - 4, px, py + 4, 2, Dessin.JAUNE);
        }
        double pjx = ccx + (jx - mx0) * echelle, pjy = ccy + (jz - mz0) * echelle;
        g.fill((int) pjx - 2, (int) pjy - 2, (int) pjx + 3, (int) pjy + 3, Dessin.VERT);
        g.disableScissor();
        Dessin.ecrire(g, "N", ccx - 2, oy + 4, Dessin.GRIS, false);
        Dessin.ecrire(g, T.f("1_square_50_blocks"), ox + 4, oy + CARTE - 8, Dessin.SOMBRE, false);

        // Colonne de droite : lecture.
        int tx = ox + CARTE + 10, lw = L - CARTE - 22;
        Dessin.encart(g, tx - 4, oy, lw + 8, CARTE + 2);
        String phase = e.getString("phase");
        Dessin.ecrire(g, phase.equals("TRAQUE") ? T.f("bearings") + n + ")" : phase.equals("REPERAGE") ? T.f("target_in_sight")
                : phase.equals("APPROCHE") ? T.f("approach").toUpperCase() : T.f("flushed_out_2"), tx, oy + 6, Dessin.JAUNE, false);
        int ly = oy + 20;
        int et = e.getInt("etoiles");
        if (phase.equals("REPERAGE")) {
            ly = Dessin.paragraphe(g, T.f("the_target_is_in_sight"),
                    tx, ly, lw, Dessin.TEXTE) + 6;
            Dessin.paragraphe(g, T.f("if_you_get_close_before"), tx, ly, lw, Dessin.GRIS);
            return;
        }
        if (phase.equals("APPROCHE") || phase.equals("DEBUSQUE")) {
            // Cible identifiée : son portrait, la qualité de l'enquête et ce qu'elle vaut.
            Dessin.portrait(g, e.getString("espece"), tx + (lw - 48) / 2, ly, 3f);
            Dessin.centre(g, Dessin.nomEspece(e.getString("espece")), tx + lw / 2, ly + 54, Dessin.TEXTE);
            Dessin.centre(g, T.etoiles(et), tx + lw / 2, ly + 66, Dessin.JAUNE);
            Dessin.centre(g, T.iv(et) + T.f("perfect_ivs_on_capture"), tx + lw / 2, ly + 78, Dessin.ACCENT);
            Dessin.paragraphe(g, phase.equals("DEBUSQUE") ? T.f("there_it_is_battle_it") : T.f("walk_don_t_run_a"),
                    tx, ly + 94, lw, Dessin.GRIS);
            return;
        }
        if (rec) {
            Dessin.ecrire(g, T.f("fix"), tx, ly, Dessin.ACCENT, false);
            Dessin.ecrire(g, "X " + Math.round(rx) + "  Z " + Math.round(rz), tx, ly + 11, Dessin.TEXTE, false);
            Dessin.ecrire(g, "± " + Math.round(inc / 5) * 5 + T.f("blocks_2"), tx, ly + 22, Dessin.JAUNE, false);
            Dessin.ecrire(g, T.f("at") + Math.round(Math.hypot(rx - jx, rz - jz)) + T.f("blocks") + Dessin.vent(Dessin.cap(jx, jz, rx, rz)), tx, ly + 33, Dessin.GRIS, false);
            ly += 48;
        } else {
            ly = Dessin.paragraphe(g, T.c(e.getString("recMessage")), tx, ly, lw, Dessin.GRIS) + 6;
        }
        Dessin.paragraphe(g, T.f("white_your_bearings_bright_line")
                + T.f("the_closer_you_are_the"), tx, ly, lw, Dessin.SOMBRE);
    }
}
