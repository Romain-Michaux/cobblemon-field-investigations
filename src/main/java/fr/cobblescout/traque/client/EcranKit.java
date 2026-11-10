package fr.cobblescout.traque.client;

import fr.cobblescout.traque.Genetique;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

// Écran du kit de terrain : pendant les prélèvements, l'état du dossier et la direction de la zone ;
// pendant la traque, la carte des relevés (les droites de cap, leur marge d'erreur et le recoupement).
// Tout tient dans l'écran du boîtier (texture fond_kit).
public class EcranKit extends Screen {
    private static final Dessin.Fond F = Dessin.KIT;
    private static final int L = F.il(), CARTE = 148;

    public EcranKit() {
        super(Component.literal(T.f("field_kit")));
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
        Dessin.bandeau(g, x, y, L, T.f("field_kit_2"), actif ? etape + " · " + T.etoiles(e.getInt("etoiles")) : etape);
        if (!actif) {
            Dessin.centre(g, T.f("no_investigation_in_progress"), x + L / 2, y + 80, Dessin.GRIS);
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
        g.drawString(Dessin.police(), T.f("samples"), x + 10, y + 23, Dessin.JAUNE, false);
        for (int i = 0; i < traces.size() && i < 5; i++) {
            CompoundTag t = traces.getCompound(i);
            String genre = t.getString("genre");
            int ly = y + 37 + i * 25;
            boolean epingle = t.getBoolean("epingle"), recolte = t.getBoolean("recolte");
            boolean enfoui = t.getBoolean("enfoui"), degage = t.getBoolean("degage"), perdue = t.getBoolean("perdue");
            int couleur = perdue ? Dessin.ALERTE : epingle ? Dessin.VERT : recolte ? Dessin.JAUNE : enfoui && degage ? Dessin.ACCENT : Dessin.SOMBRE;
            if (enfoui) Dessin.image(g, Dessin.iconeTrace(genre, true), x + 12, ly + 2, 16, 16);
            else Dessin.image(g, Dessin.iconeTrace(genre, false), x + 8, ly - 2, 24, 24);
            // La fiole : éteinte tant que la trace n'est pas prélevée, pleine ensuite, pastille verte une fois analysée.
            if (perdue || (!epingle && !recolte)) Dessin.teinte(g, 0xFF46525E);
            Dessin.image(g, Dessin.FIOLE, x + 172, ly, 10, 20);
            g.setColor(1f, 1f, 1f, 1f);
            if (epingle) {
                g.fill(x + 179, ly + 13, x + 185, ly + 19, Dessin.BORD);
                g.fill(x + 180, ly + 14, x + 184, ly + 18, Dessin.VERT);
            }
            g.drawString(Dessin.police(), T.c(Genetique.nomTrace(genre, enfoui)), x + 36, ly + 1, perdue ? Dessin.SOMBRE : Dessin.TEXTE, false);
            g.drawString(Dessin.police(), perdue ? T.f("clue_destroyed") : epingle ? T.f("analysed_in_the_file") : recolte ? T.f("sampled_to_analyse")
                    : enfoui ? (degage ? T.f("uncovered_to_sample") : T.f("buried_brush_needed")) : T.f("to_sample_in_the_zone"), x + 36, ly + 11, couleur, false);
        }
        // Boussole vers la zone des traces (le haut du cadran est la direction du regard).
        int cx = x + 256, cy = y + 72;
        Dessin.encart(g, x + 196, y + 18, 120, 148);
        g.drawString(Dessin.police(), T.f("trace_zone"), x + 202, y + 23, Dessin.JAUNE, false);
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

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int x = F.x(width), y = F.y(height);
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
            g.drawString(Dessin.police(), String.valueOf(i + 1), (int) px + 4, (int) py - 9, Dessin.TEXTE, false);
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
        g.drawString(Dessin.police(), "N", ccx - 2, oy + 4, Dessin.GRIS, false);
        g.drawString(Dessin.police(), T.f("1_square_50_blocks"), ox + 4, oy + CARTE - 8, Dessin.SOMBRE, false);

        // Colonne de droite : lecture.
        int tx = ox + CARTE + 10, lw = L - CARTE - 22;
        Dessin.encart(g, tx - 4, oy, lw + 8, CARTE + 2);
        String phase = e.getString("phase");
        g.drawString(Dessin.police(), phase.equals("TRAQUE") ? T.f("bearings") + n + ")" : phase.equals("REPERAGE") ? T.f("target_in_sight")
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
            g.drawString(Dessin.police(), T.f("fix"), tx, ly, Dessin.ACCENT, false);
            g.drawString(Dessin.police(), "X " + Math.round(rx) + "  Z " + Math.round(rz), tx, ly + 11, Dessin.TEXTE, false);
            g.drawString(Dessin.police(), "± " + Math.round(inc / 5) * 5 + T.f("blocks_2"), tx, ly + 22, Dessin.JAUNE, false);
            g.drawString(Dessin.police(), T.f("at") + Math.round(Math.hypot(rx - jx, rz - jz)) + T.f("blocks") + Dessin.vent(Dessin.cap(jx, jz, rx, rz)), tx, ly + 33, Dessin.GRIS, false);
            ly += 48;
        } else {
            ly = Dessin.paragraphe(g, T.c(e.getString("recMessage")), tx, ly, lw, Dessin.GRIS) + 6;
        }
        Dessin.paragraphe(g, T.f("white_your_bearings_bright_line")
                + T.f("the_closer_you_are_the"), tx, ly, lw, Dessin.SOMBRE);
    }
}
