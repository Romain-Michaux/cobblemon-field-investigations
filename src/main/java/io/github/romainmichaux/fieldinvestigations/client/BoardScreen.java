package io.github.romainmichaux.fieldinvestigations.client;

import java.util.ArrayList;
import java.util.List;

import io.github.romainmichaux.fieldinvestigations.Genetics;
import io.github.romainmichaux.fieldinvestigations.Packet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

// Investigation board: a cork board. On the left, the sheet of the reconstructed genetic profile; on the right, the suspects
// as pinned photos. Each pinned result rules out incompatible suspects; with enough results, the player names a suspect
// and starts the hunt.
public class BoardScreen extends Screen {
    private static final Draw.Backdrop BG = Draw.BOARD;
    private static final int L = BG.il();
    private static final int PAPER_X = 3, PAPER_Y = 3, PAPER_W = 128, PAPER_H = 183;
    private static final int GRID_X = 134, STEP_X = 50, STEP_Y = 55, BUTTONS_Y = 171;
    private static final String[] RANKS = {T.f("common"), T.f("uncommon"), T.f("rare"), T.f("very_rare")};
    private String choice = "";

    public BoardScreen() {
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
        if (Demo.mouse != null) {
            mx = Demo.mouse[0];
            my = Demo.mouse[1];
        }
        renderBackground(g, mx, my, pt);
        CompoundTag e = FieldInvestigationsClient.state;
        int x = BG.x(width), y = BG.y(height);
        Draw.background(g, BG, width, height);
        Draw.centre(g, T.f("case_board"), x + L / 2 + 1, y - 14, Draw.INK);

        if (!e.getBoolean("active") && !e.getList("leads", Tag.TAG_COMPOUND).isEmpty()) {
            leads(g, e, x, y, mx, my);
            return;
        }
        if (!e.getBoolean("active")) {
            long waiting = e.getLong("waiting") - (System.currentTimeMillis() - FieldInvestigationsClient.stateReceived);
            Draw.plate(g, x + 38, y + 26, L - 76, 110);
            Draw.zone(x + 46, x + L - 46);
            Draw.centre(g, T.f("no_investigation_in_progress"), x + L / 2, y + 38, Draw.TEXT);
            int ly0 = Draw.paragraph(g, T.f("open_an_investigation_to_get"),
                    x + 50, y + 52, L - 100, Draw.GREY);
            Draw.paragraph(g, T.f("each_star_you_keep_5"), x + 50, ly0 + 2, L - 100, Draw.YELLOW);
            Draw.zone(x + 2, x + L - 2);
            boolean ready = waiting <= 0;
            String label = ready ? T.f("check_the_reports") : T.f("next_report_in") + (waiting / 60000 + 1) + T.f("min");
            Draw.button(g, x + L / 2 - 90, y + 144, 180, 22, label, Draw.in(mx, my, x + L / 2 - 90, y + 144, 180, 22), ready, Draw.ACCENT);
            Draw.centre(g, T.f("cases_solved") + e.getInt("passed"), x + L / 2, y + 173, Draw.INK);
            setting(g, x, y, mx, my);
            return;
        }

        String phase = e.getString("phase");
        boolean sampling = phase.equals("SAMPLING");
        ListTag traces = e.getList("traces", Tag.TAG_COMPOUND);
        ListTag suspects = e.getList("suspects", Tag.TAG_COMPOUND);
        int pins = 0;

        int px = x + PAPER_X, py = y + PAPER_Y, tx = px + 15, tl = 98;
        Draw.image(g, Draw.PAPER, px, py, PAPER_W, PAPER_H);
        Draw.zone(tx, tx + tl + 2);
        // Investigation quality: it sets the IVs of the caught Pokémon and the loot.
        Draw.write(g, T.stars(e.getInt("stars")), tx, py + 22, 0xFFB07A10, false);
        String special = e.getString("special");
        if (!special.isEmpty()) Draw.write(g, "✦", tx + 50, py + 22, specialColour(special), false);
        boolean hoverStars = Draw.in(mx, my, tx - 2, py + 20, 62, 12);
        Draw.write(g, T.f("species") + RANKS[Math.min(3, e.getInt("rank"))], tx, py + 37, Draw.INK_RED, false);
        for (int i = 0; i < Genetics.MARKERS.size(); i++) {
            String m = Genetics.MARKERS.get(i);
            int ly = py + 49 + i * 20;
            Draw.write(g, T.c(Genetics.MARKER_NAMES.get(m)), tx, ly, Draw.INK_LIGHT, false);
            CompoundTag trace = null;
            for (int k = 0; k < traces.size(); k++) {
                if (traces.getCompound(k).getString("kind").equals(m)) trace = traces.getCompound(k);
            }
            if (trace != null && trace.getBoolean("pin")) {
                pins++;
                String v = trace.getString("value");
                Draw.chip(g, tx, ly + 9, tl, 10, Genetics.colour(m, v), Genetics.label(m, v).getString());
            } else {
                boolean harvest = trace != null && trace.getBoolean("harvest");
                g.fill(tx, ly + 9, tx + tl, ly + 19, 0x30402A14);
                g.fill(tx, ly + 18, tx + tl, ly + 19, 0x60402A14);
                boolean lost = trace != null && trace.getBoolean("lost"), toDigUp = trace != null && trace.getBoolean("buried") && !trace.getBoolean("uncovered");
                Draw.write(g, lost ? T.f("clue_destroyed") : harvest ? T.f("to_analyse") : toDigUp ? T.f("to_dig_up") : T.f("to_sample"), tx + 3, ly + 10,
                        lost ? Draw.INK_RED : harvest ? Draw.INK : Draw.INK_LIGHT, false);
            }
        }
        if (sampling && minecraft != null && minecraft.player != null) {
            double zx = e.getDouble("zoneX"), zz = e.getDouble("zoneZ");
            double dist = Math.hypot(zx - minecraft.player.getX(), zz - minecraft.player.getZ());
            Draw.write(g, T.f("zone") + Math.round(dist) + T.f("blocks_2"), tx, py + 151, Draw.INK, false);
            Draw.write(g, T.f("compass") + Draw.wind(Draw.bearing(minecraft.player.getX(), minecraft.player.getZ(), zx, zz)), tx, py + 162, Draw.INK_RED, false);
        } else {
            Draw.write(g, T.f("hunt_in_progress"), tx, py + 151, Draw.INK, false);
            Draw.write(g, T.f("kit_map"), tx, py + 162, Draw.INK_RED, false);
        }

        Draw.zone(x + GRID_X - 2, x + L - 2);
        int remaining = 0;
        String lastRemaining = "";
        CompoundTag hovered = null;
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            CompoundTag s = suspects.getCompound(i);
            int cx = x + GRID_X + (i % 4) * STEP_X, cy = y + (i / 4) * STEP_Y;
            boolean ruledOut = s.getBoolean("ruled_out");
            String species = s.getString("species");
            boolean chosen = species.equals(sampling ? choice : e.getString("pronostic"));
            if (!ruledOut) {
                remaining++;
                lastRemaining = species;
            }
            if (chosen && !ruledOut) g.fill(cx - 2, cy + 2, cx + 50, cy + 62, Draw.YELLOW);
            Draw.image(g, Draw.POLAROID, cx, cy, 48, 60);
            Draw.portrait(g, species, cx + 8, cy + 12, 2f);
            ListTag values = s.getList("values", Tag.TAG_STRING);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            for (int m = 0; m < values.size() && m < 5; m++) {
                int c = Draw.opaque(Genetics.colour(Genetics.MARKERS.get(m), values.getString(m)));
                g.fill(cx + 4 + m * 8, cy + 50, cx + 11 + m * 8, cy + 56, Draw.INK);
                g.fill(cx + 5 + m * 8, cy + 51, cx + 10 + m * 8, cy + 55, c);
            }
            if (s.getBoolean("owned")) Draw.pokeBall(g, cx + 32, cy + 9);
            if (ruledOut) {
                g.fill(cx + 1, cy + 5, cx + 47, cy + 59, 0x9C241A12);
                Draw.image(g, Draw.CROSS, cx + 10, cy + 14, 28, 28);
            }
            g.pose().popPose();
            if (Draw.in(mx, my, cx, cy + 4, 48, 56)) hovered = s;
        }
        // Pins go on top of everything: otherwise the photo in the row above (and its overlay) hides their top.
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            Draw.image(g, Draw.PIN, x + GRID_X + (i % 4) * STEP_X + 18, y + (i / 4) * STEP_Y, 12, 10);
        }
        g.pose().popPose();
        if (remaining == 1 && sampling) choice = lastRemaining;

        int bx = x + GRID_X, by = y + BUTTONS_Y;
        if (sampling) {
            int required = e.getInt("required");
            boolean ready = pins >= required && !choice.isEmpty();
            String label = pins < required ? T.f("results") + pins + " / " + required : choice.isEmpty() ? T.f("pick_a_suspect") : T.f("track") + Draw.speciesName(choice);
            if (Draw.textFont().width(label) > 112) label = T.f("start_the_hunt");
            Draw.button(g, bx, by, 122, 18, label, Draw.in(mx, my, bx, by, 122, 18), ready, Draw.GREEN);
        } else {
            Draw.write(g, T.f("guess") + Draw.speciesName(e.getString("pronostic")), bx, by, Draw.INK, false);
            Draw.write(g, phase.equals("FLUSHED") ? T.f("target_flushed_out") : T.f("in_the_field_with_the"), bx, by + 10, Draw.INK_RED, false);
        }
        Draw.button(g, bx + 126, by, 72, 18, T.f("give_up"), Draw.in(mx, my, bx + 126, by, 72, 18), true, Draw.DARK_RED);

        if (hoverStars) {
            int stars = e.getInt("stars");
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(T.f("investigation_quality") + stars + " / 5"));
            lines.add(Component.literal("§b" + T.iv(stars) + T.f("perfect_ivs_7on_the_caught")));
            lines.add(Component.literal(T.f("7and_richer_loot_with_each")));
            lines.add(Component.literal(T.f("8_one_star_no_perfect")));
            lines.add(Component.literal(T.f("8mini_game_failure_half") + e.getInt("fautes") + ")"));
            lines.add(Component.literal(T.f("8target_ran_away_1") + e.getInt("leaks") + ")"));
            lines.add(Component.literal(T.f("8wrong_guess_2")));
            if (!special.isEmpty()) {
                lines.add(Component.literal("§6✦ " + T.f("investigation") + specialName(special).toLowerCase()));
                for (String line : specialRules(special)) lines.add(Component.literal(line));
                lines.add(Component.literal("§e" + specialGain(special)));
            }
            Draw.tooltip(g, lines, mx, my, width);
        } else if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(Draw.speciesName(hovered.getString("species")) + (hovered.getBoolean("ruled_out") ? T.f("ruled_out") : "")));
            ListTag values = hovered.getList("values", Tag.TAG_STRING);
            for (int m = 0; m < values.size() && m < 5; m++) {
                String marker = Genetics.MARKERS.get(m);
                lines.add(Component.literal("§7" + T.c(Genetics.MARKER_NAMES.get(marker)) + " : §f").append(Genetics.label(marker, values.getString(m))));
            }
            lines.add(Component.literal(hovered.getBoolean("owned") ? T.f("aalready_caught") : T.f("7not_caught_yet")));
            if (sampling) {
                lines.add(Component.literal(T.f("8left_click_name_this_suspect")));
                lines.add(Component.literal(T.f("8right_click_rule_out_restore")));
            }
            Draw.tooltip(g, lines, mx, my, width);
        }
    }

    // ---------- Leads to choose from ----------

    private static final int CARD_W = 104, CARD_H = 130, CARD_Y = 21, CARD_STEP = 110;

static String specialName(String special) {
        return switch (special) {
            case "nocturnal" -> T.f("nocturnal");
            case "skittish" -> T.f("skittish");
            case "exceptional" -> T.f("exceptional");
            case "restless" -> T.f("restless");
            case "aquatic" -> T.f("aquatic");
            case "dig_site" -> T.f("dig_site");
            case "canopy" -> T.f("canopy");
            case "altitude" -> T.f("highlands");
            case "frost" -> T.f("frost");
            case "mist" -> T.f("mist");
            case "underground" -> T.f("underground");
            default -> T.f("standard");
        };
    }

    static int specialColour(String special) {
        return switch (special) {
            case "nocturnal" -> 0xFF8FA8FF;
            case "skittish" -> Draw.YELLOW;
            case "exceptional" -> 0xFFE9A15B;
            case "restless" -> 0xFFF0906A;
            case "aquatic" -> 0xFF5BB8F0;
            case "dig_site" -> 0xFFD9B36A;
            case "canopy" -> 0xFF7CC66A;
            case "altitude" -> 0xFFC9B8AA;
            case "frost" -> 0xFFA8E4F0;
            case "mist" -> 0xFFB7A8DC;
            case "underground" -> 0xFFD0A070;
            default -> Draw.GREY;
        };
    }

    static String[] specialRules(String special) {
        return switch (special) {
            case "nocturnal" -> new String[] {T.f("7traces_and_cries_only_at"), T.f("7the_target_only_comes_out")};
            case "skittish" -> new String[] {T.f("7it_senses_you_from_much"), T.f("7scan_it_from_a_long")};
            case "exceptional" -> new String[] {T.f("7one_more_analysis_result_required"), T.f("7and_suspects_that_all_look")};
            case "restless" -> new String[] {T.f("7it_moves_twice_as_often"), T.f("7and_its_lair_is_farther")};
            case "aquatic" -> new String[] {T.f("7traces_on_the_bottom_target"), T.f("7the_water_be_ready_to")};
            case "dig_site" -> new String[] {T.f("7every_remain_is_buried"), T.f("7dig_and_brush_with_care")};
            case "canopy" -> new String[] {T.f("7traces_on_trunks_and_up"), T.f("7treetops_look_up")};
            case "altitude" -> new String[] {T.f("7traces_on_rock_faces_across"), T.f("7a_much_wider_zone")};
            case "frost" -> new String[] {T.f("7frozen_fragile_samples"), T.f("7one_dose_fewer_per_sample")};
            case "mist" -> new String[] {T.f("7cries_carry_poorly_bearings"), T.f("7are_much_less_precise")};
            case "underground" -> new String[] {T.f("7cave_species_traces_and_target"), T.f("7underground_bring_torches_and_a")};
            default -> new String[] {T.f("7a_regular_investigation")};
        };
    }

    static String specialGain(String special) {
        return switch (special) {
            case "nocturnal" -> T.f("shiny_odds_2_special_loot");
            case "skittish", "restless" -> T.f("double_special_loot");
            case "exceptional" -> T.f("hidden_ability_special_loot");
            case "aquatic" -> T.f("special_loot_water_items");
            case "dig_site" -> T.f("special_loot_fossils");
            case "canopy" -> T.f("special_loot_forest_items");
            case "altitude" -> T.f("special_loot_rare_stones");
            case "frost" -> T.f("special_loot_ice_items");
            case "mist" -> T.f("special_loot_swamp_items");
            case "underground" -> T.f("special_loot_ores_and_stones");
            default -> T.f("ivs_and_loot_by_your");
        };
    }

    private static int cardX(int x, int n, int i) {
        return x + (L - (n * CARD_W + (n - 1) * (CARD_STEP - CARD_W))) / 2 + i * CARD_STEP;
    }

    // Three leads pinned to the board: the player picks their investigation (rarity, kind, distance).
    private void leads(GuiGraphics g, CompoundTag e, int x, int y, int mx, int my) {
        ListTag leads = e.getList("leads", Tag.TAG_COMPOUND);
        int n = Math.min(3, leads.size());
        long timeLeft = e.getLong("leadsEnd") - (System.currentTimeMillis() - FieldInvestigationsClient.stateReceived);
        Draw.centre(g, T.f("pick_your_investigation"), x + L / 2, y + 8, Draw.INK);
        CompoundTag hovered = null;
        for (int i = 0; i < n; i++) {
            CompoundTag p = leads.getCompound(i);
            int cx = cardX(x, n, i), cy = y + CARD_Y, tx = cx + 9;
            boolean hover = Draw.in(mx, my, cx, cy, CARD_W, CARD_H);
            String special = p.getString("special");
            if (hover) {
                g.fill(cx - 2, cy - 2, cx + CARD_W + 2, cy + CARD_H + 2, Draw.YELLOW);
                hovered = p;
            }
            Draw.plate(g, cx, cy, CARD_W, CARD_H);
            Draw.zone(cx + 7, cx + CARD_W - 7);
            Draw.write(g, T.f("lead") + (i + 1), tx, cy + 9, Draw.ACCENT, false);
            Draw.write(g, (special.isEmpty() ? "" : "✦ ") + specialName(special), tx, cy + 21, specialColour(special), false);
            g.fill(tx, cy + 32, cx + CARD_W - 9, cy + 33, 0x40FFFFFF);
            Draw.write(g, T.f("species_2"), tx, cy + 37, Draw.GREY, false);
            Draw.write(g, RANKS[Math.min(3, p.getInt("rank"))], tx, cy + 47, Draw.TEXT, false);
            Draw.write(g, T.f("zone_2"), tx, cy + 60, Draw.GREY, false);
            String whereabouts = "?";
            if (minecraft != null && minecraft.player != null) {
                double zx = p.getDouble("zoneX"), zz = p.getDouble("zoneZ"), jx = minecraft.player.getX(), jz = minecraft.player.getZ();
                whereabouts = Math.round(Math.hypot(zx - jx, zz - jz)) + " m · " + Draw.wind(Draw.bearing(jx, jz, zx, zz));
            }
            Draw.write(g, whereabouts, tx, cy + 70, Draw.TEXT, false);
            Draw.write(g, p.getBoolean("new") ? T.f("never_caught") : T.f("already_caught"), tx, cy + 84,
                    p.getBoolean("new") ? Draw.GREEN : Draw.DARK, false);
            Draw.paragraph(g, specialGain(special), tx, cy + 96, CARD_W - 18, Draw.YELLOW);
        }
        Draw.zone(x + 2, x + L - 2);
        Draw.centre(g, T.f("reports_valid_for_another") + Math.max(1, timeLeft / 60000 + 1) + T.f("min"), x + L / 2, y + 157, Draw.INK);
        Draw.centre(g, T.f("cases_solved") + e.getInt("passed"), x + L / 2, y + 173, Draw.INK);
        setting(g, x, y, mx, my);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            String special = hovered.getString("special");
            lines.add(Component.literal(T.f("investigation") + specialName(special).toLowerCase()));
            for (String line : specialRules(special)) lines.add(Component.literal(line));
            lines.add(Component.literal("§e" + specialGain(special)));
            lines.add(Component.literal(T.f("8click_open_this_investigation")));
            Draw.tooltip(g, lines, mx, my, width);
        }
    }

    // Accessibility setting, bottom left of the board: the colour aid shows mini-game hues as numbers.
private void setting(GuiGraphics g, int x, int y, int mx, int my) {
        String text = T.f("colour_aid") + (FieldInvestigationsClient.colourAid ? T.f("on") : T.f("off"));
        boolean hover = Draw.in(mx, my, x + 4, y + 171, Draw.textFont().width(text) + 4, 12);
        Draw.write(g, text, x + 6, y + 173, hover ? Draw.INK_RED : Draw.INK, false);
        // An invitation to join a team: it is handled in the kit, Team tab.
        String invitation = FieldInvestigationsClient.state.getString("invitation");
        if (!invitation.isEmpty()) {
            Draw.zone(x + 2, x + L - 2);
            Draw.button(g, x + L / 2 - 110, y + 2, 220, 18, invitation + T.f("invites_you_see_the_team"), Draw.in(mx, my, x + L / 2 - 110, y + 2, 220, 18), true, Draw.GREEN);
        }
        // Bottom right: the investigator's logbook (rank, upgrades, archives), which lives in the kit.
        Draw.button(g, x + L - 78, y + 168, 74, 17, T.f("logbook"), Draw.in(mx, my, x + L - 78, y + 168, 74, 17), true, Draw.ACCENT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CompoundTag e = FieldInvestigationsClient.state;
        int x = BG.x(width), y = BG.y(height);
        if (!e.getBoolean("active") && !e.getString("invitation").isEmpty() && Draw.in(mx, my, x + L / 2 - 110, y + 2, 220, 18) && minecraft != null) {
            Draw.click(1f);
            minecraft.setScreen(new KitScreen(3));
            return true;
        }
        if (!e.getBoolean("active") && Draw.in(mx, my, x + L - 78, y + 168, 74, 17) && minecraft != null) {
            Draw.click(1f);
            minecraft.setScreen(new KitScreen(1));
            return true;
        }
        if (!e.getBoolean("active") && Draw.in(mx, my, x + 4, y + 171, 96, 12)) {
            FieldInvestigationsClient.toggleColourAid();
            Draw.click(1.2f);
            return true;
        }
        ListTag leads = e.getList("leads", Tag.TAG_COMPOUND);
        if (!e.getBoolean("active") && !leads.isEmpty()) {
            int n = Math.min(3, leads.size());
            for (int i = 0; i < n; i++) {
                if (!Draw.in(mx, my, cardX(x, n, i), y + CARD_Y, CARD_W, CARD_H)) continue;
                Packet p = Packet.from("choose");
                p.data().putInt("index", i);
                FieldInvestigationsClient.send(p);
                Draw.click(1.3f);
            }
            return true;
        }
        if (!e.getBoolean("active")) {
            long waiting = e.getLong("waiting") - (System.currentTimeMillis() - FieldInvestigationsClient.stateReceived);
            if (waiting <= 0 && Draw.in(mx, my, x + L / 2 - 90, y + 144, 180, 22)) {
                Draw.click(1f);
                FieldInvestigationsClient.send(Packet.from("open_investigation"));
            }
            return true;
        }
        int bx = x + GRID_X, by = y + BUTTONS_Y;
        if (Draw.in(mx, my, bx + 126, by, 72, 18)) {
            Draw.click(0.7f);
            FieldInvestigationsClient.send(Packet.from("abandon"));
            return true;
        }
        if (Draw.in(mx, my, x + PAPER_X, y + PAPER_Y + 146, PAPER_W, 30) && minecraft != null) {
            Draw.click(1f);
            minecraft.setScreen(new KitScreen());
            return true;
        }
        if (!e.getString("phase").equals("SAMPLING")) return true;
        ListTag suspects = e.getList("suspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < suspects.size() && i < 12; i++) {
            CompoundTag s = suspects.getCompound(i);
            int cx = x + GRID_X + (i % 4) * STEP_X, cy = y + (i / 4) * STEP_Y;
            if (!Draw.in(mx, my, cx, cy + 4, 48, 56)) continue;
            if (button == 1) {
                // Right click: the player rules out (or restores) this suspect themselves.
                boolean ruleOut = !s.getBoolean("ruled_out");
                s.putBoolean("ruled_out", ruleOut);
                if (ruleOut && choice.equals(s.getString("species"))) choice = "";
                Packet p = Packet.from("rule_out");
                p.data().putString("species", s.getString("species"));
                FieldInvestigationsClient.send(p);
                Draw.click(ruleOut ? 0.8f : 1.1f);
            } else if (!s.getBoolean("ruled_out")) {
                choice = s.getString("species");
                Draw.click(1.3f);
            }
            return true;
        }
        if (Draw.in(mx, my, bx, by, 122, 18) && !choice.isEmpty()) {
            Packet p = Packet.from("start");
            p.data().putString("species", choice);
            FieldInvestigationsClient.send(p);
            Draw.click(1f);
        }
        return true;
    }
}
