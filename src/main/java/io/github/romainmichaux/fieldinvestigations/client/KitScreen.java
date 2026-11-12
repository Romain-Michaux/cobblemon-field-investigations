package io.github.romainmichaux.fieldinvestigations.client;

import io.github.romainmichaux.fieldinvestigations.Genetics;
import io.github.romainmichaux.fieldinvestigations.Packet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Field kit screen: during sampling, the case status and the direction of the zone;
// during tracking, the bearings map (bearing lines, their error margin and the cross-fix).
// Other tabs show the investigator's career: the logbook (rank, experience, kit upgrades) and the
// archives (closed cases, investigation kinds already solved). Everything fits in the device screen (texture bg_kit).
public class KitScreen extends Screen {
    private static final Draw.Backdrop BG = Draw.KIT;
    private static final int L = BG.il(), MAP = 148;
    static final ResourceLocation BADGE = Draw.texture("badge");
    static final int[] RANK_COLOURS = {0xFFB08D6A, 0xFFE0A060, 0xFFE8EEF2, 0xFFF2C93A, 0xFF6CCB5F, 0xFF4FD1C5, 0xFFC79CFF};
    static final String[] TYPES = {"", "nocturnal", "skittish", "exceptional", "restless", "aquatic", "dig_site", "canopy", "altitude", "frost", "mist", "underground"};
    // 0 = current case, 1 = investigator's logbook, 2 = archives, 3 = team.
    private int tab;
    private int scrolling;
    // Team tab: text typed to search for a player, and scroll position in the list of players to invite.
    private String searchText = "";
    private int inviteScroll;

    // Players who can be invited, filtered by the search (the server sets the order: usual partners first).
    private java.util.List<CompoundTag> invitable() {
        ListTag nearby = FieldInvestigationsClient.state.getList("close", Tag.TAG_COMPOUND);
        java.util.List<CompoundTag> list = new java.util.ArrayList<>();
        for (int i = 0; i < nearby.size(); i++) {
            CompoundTag c = nearby.getCompound(i);
            if (searchText.isEmpty() || c.getString("name").toLowerCase().contains(searchText.toLowerCase())) list.add(c);
        }
        return list;
    }

    public KitScreen() {
        this(0);
    }

    public KitScreen(int tab) {
        super(Component.literal(T.f("field_kit")));
        this.tab = tab;
    }

    private static String[] tabs() {
        return new String[] {T.f("case"), T.f("logbook"), T.f("archives"), T.f("team")};
    }

    private int[] placeTab(int x, int i) {
        String[] names = tabs();
        int px = x + 15;
        for (int k = 0; k < i; k++) px += Draw.textFont().width(names[k]) + 12;
        return new int[] {px, Draw.textFont().width(names[i])};
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
        CompoundTag e = FieldInvestigationsClient.state;
        int x = BG.x(width), y = BG.y(height);
        Draw.background(g, BG, width, height);
        boolean active = e.getBoolean("active") && minecraft != null && minecraft.player != null;
        String phase = e.getString("phase");
        String step = !active ? T.f("standby") : phase.equals("SAMPLING") ? T.f("sampling") : phase.equals("TRACKING") ? T.f("tracking")
                : phase.equals("SPOTTING") ? T.f("spotting") : phase.equals("APPROACH") ? T.f("approach") : T.f("flushed_out");
        Draw.banner(g, x, y, L, "", active ? T.stars(e.getInt("stars")) : "");
        String[] names = tabs();
        for (int i = 0; i < names.length; i++) {
            int[] place = placeTab(x, i);
            boolean hover = Draw.in(mx, my, place[0] - 3, y, place[1] + 6, 14);
            Draw.write(g, names[i], place[0], y + 3, i == tab ? Draw.TEXT : hover ? Draw.YELLOW : Draw.GREY, false);
            if (i == tab) g.fill(place[0], y + 12, place[0] + place[1], y + 14, Draw.ACCENT);
        }
        if (tab == 1) {
            logbook(g, e.getCompound("career"), e.getInt("passed"), x, y, mx, my);
            return;
        }
        if (tab == 2) {
            archives(g, e.getCompound("career"), x, y);
            return;
        }
        if (tab == 3) {
            team(g, e, x, y, mx, my);
            return;
        }
        if (!active) {
            Draw.centre(g, T.f("no_investigation_in_progress"), x + L / 2, y + 70, Draw.GREY);
            Draw.button(g, x + L / 2 - 80, y + 90, 160, 22, T.f("check_the_reports"), Draw.in(mx, my, x + L / 2 - 80, y + 90, 160, 22), true, Draw.ACCENT);
            return;
        }
        double jx = minecraft.player.getX(), jz = minecraft.player.getZ();
        if (phase.equals("SAMPLING")) {
            samplings(g, e, x, y, jx, jz, mx, my);
        } else {
            map(g, e, x, y, jx, jz);
        }
    }

    private void samplings(GuiGraphics g, CompoundTag e, int x, int y, double jx, double jz, int mx, int my) {
        ListTag traces = e.getList("traces", Tag.TAG_COMPOUND);
        Draw.inset(g, x + 4, y + 18, 188, 148);
        Draw.write(g, T.f("samples"), x + 10, y + 23, Draw.YELLOW, false);
        for (int i = 0; i < traces.size() && i < 5; i++) {
            CompoundTag t = traces.getCompound(i);
            String kind = t.getString("kind");
            int ly = y + 37 + i * 25;
            boolean pin = t.getBoolean("pin"), harvest = t.getBoolean("harvest");
            boolean buried = t.getBoolean("buried"), uncovered = t.getBoolean("uncovered"), lost = t.getBoolean("lost");
            int colour = lost ? Draw.ALERT : pin ? Draw.GREEN : harvest ? Draw.YELLOW : buried && uncovered ? Draw.ACCENT : Draw.DARK;
            if (buried) Draw.image(g, Draw.buriedIcon(kind, t.getInt("variant")), x + 12, ly + 2, 16, 16);
            else Draw.image(g, Draw.traceIcon(kind, false), x + 8, ly - 2, 24, 24);
            // The vial: greyed out until the trace is sampled, full afterwards, green dot once analysed.
            if (lost || (!pin && !harvest)) Draw.tint(g, 0xFF46525E);
            Draw.image(g, Draw.VIAL, x + 172, ly, 10, 20);
            g.setColor(1f, 1f, 1f, 1f);
            if (pin) {
                g.fill(x + 179, ly + 13, x + 185, ly + 19, Draw.EDGE);
                g.fill(x + 180, ly + 14, x + 184, ly + 18, Draw.GREEN);
            }
            Draw.write(g, T.c(Genetics.traceName(kind, buried, t.getInt("variant"))), x + 36, ly + 1, lost ? Draw.DARK : Draw.TEXT, false);
            Draw.write(g, lost ? T.f("clue_destroyed") : pin ? T.f("analysed_in_the_file") : harvest ? T.f("sampled_to_analyse")
                    : buried ? (uncovered ? T.f("uncovered_to_sample") : T.f("buried_brush_needed")) : T.f("to_sample_in_the_zone"), x + 36, ly + 11, colour, false);
        }
        // Compass towards the trace zone (the top of the dial is where the player looks).
        int cx = x + 256, cy = y + 72;
        Draw.inset(g, x + 196, y + 18, 120, 148);
        Draw.write(g, T.f("trace_zone"), x + 202, y + 23, Draw.YELLOW, false);
        double zx = e.getDouble("zoneX"), zz = e.getDouble("zoneZ");
        double bearing = Draw.bearing(jx, jz, zx, zz);
        double relative = Math.toRadians(bearing - (minecraft.player.getYRot() + 180));
        Draw.image(g, Draw.DIAL, cx - 36, cy - 36, 72, 72);
        Draw.segment(g, cx - 1, cy - 1, cx - 1 + Math.sin(relative) * 22, cy - 1 - Math.cos(relative) * 22, 2, Draw.ACCENT);
        Draw.point(g, cx + Math.sin(relative) * 22 - 2, cy - Math.cos(relative) * 22 - 2, 4, Draw.YELLOW);
        Draw.centre(g, Math.round(Math.hypot(zx - jx, zz - jz)) + T.f("blocks") + Draw.wind(bearing), cx, y + 114, Draw.TEXT);
        Draw.centre(g, "X " + Math.round(zx) + "  Z " + Math.round(zz), cx, y + 125, Draw.GREY);
        Draw.button(g, x + 202, y + 140, 108, 22, T.f("case_file"), Draw.in(mx, my, x + 202, y + 140, 108, 22), true, Draw.ACCENT);
    }

    // ---------- Logbook: rank, experience, kit upgrades ----------

    private void logbook(GuiGraphics g, CompoundTag c, int succeeded, int x, int y, int mx, int my) {
        int rank = Math.max(0, Math.min(6, c.getInt("rank"))), xp = c.getInt("xp"), threshold = c.getInt("threshold"), next = c.getInt("next");
        int colour = RANK_COLOURS[rank];
        Draw.inset(g, x + 4, y + 18, 124, 148);
        Draw.tint(g, colour);
        Draw.pieceEnlarged(g, BADGE, x + 34, y + 23, 64, 64, 0, 0, 32, 32, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        Draw.centre(g, T.c("cobblemon_field_investigations.rank." + rank), x + 66, y + 90, colour);
        g.fill(x + 14, y + 103, x + 118, y + 110, Draw.EDGE);
        double part = next > threshold ? (xp - threshold) / (double) (next - threshold) : 1;
        g.fill(x + 15, y + 104, x + 15 + (int) (102 * Math.max(0, Math.min(1, part))), y + 109, colour);
        Draw.centre(g, next > 0 ? xp + " / " + next + " XP" : xp + " XP · " + T.f("top_rank"), x + 66, y + 113, Draw.GREY);
        Draw.write(g, T.f("cases") + succeeded, x + 12, y + 128, Draw.TEXT, false);
        Draw.write(g, T.f("five_stars") + c.getInt("perfect"), x + 12, y + 139, Draw.YELLOW, false);
        Draw.write(g, T.f("species_3") + c.getInt("species_count"), x + 12, y + 150, Draw.ACCENT, false);

        Draw.inset(g, x + 132, y + 18, 184, 148);
        Draw.write(g, T.f("kit_upgrades"), x + 138, y + 23, Draw.YELLOW, false);
        String[] titles = {T.f("sharper_radar"), T.f("spare_vial"), T.f("quick_scan"), T.f("cool_head"), T.f("keen_ear"), T.f("informant_network")};
        String[] effects = {T.f("detects_from_farther_away"), T.f("first_sample_needs_no_vial"), T.f("target_identified_in_2_s"), T.f("one_more_failure_forgiven"),
                T.f("more_precise_cry_bearings"), T.f("wait_between_cases_2")};
        int hovered = -1;
        for (int i = 0; i < 6; i++) {
            int ly = y + 36 + i * 21;
            boolean acquired = rank >= i + 1;
            g.fill(x + 138, ly, x + 154, ly + 18, Draw.EDGE);
            g.fill(x + 139, ly + 1, x + 153, ly + 17, acquired ? 0xFF245C3A : 0xFF1A2633);
            Draw.centre(g, acquired ? "✔" : String.valueOf(i + 1), x + 146, ly + 5, acquired ? Draw.GREEN : RANK_COLOURS[i + 1]);
            Draw.write(g, titles[i], x + 158, ly, acquired ? Draw.TEXT : Draw.GREY, false);
            Draw.write(g, effects[i], x + 158, ly + 10, acquired ? Draw.GREEN : Draw.DARK, false);
            if (Draw.in(mx, my, x + 136, ly - 1, 176, 20)) hovered = i;
        }
        if (hovered >= 0) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.literal(titles[hovered]));
            lines.add(Component.literal((rank >= hovered + 1 ? T.f("aunlocked_at_rank") : T.f("7unlocks_at_rank")) + T.c("cobblemon_field_investigations.rank." + (hovered + 1))));
            Draw.tooltip(g, lines, mx, my, width);
        }
    }

    // ---------- Archives: closed cases, investigation kinds already solved ----------

    private void archives(GuiGraphics g, CompoundTag c, int x, int y) {
        ListTag pastCases = c.getList("archives", Tag.TAG_COMPOUND);
        Draw.inset(g, x + 4, y + 18, 196, 148);
        Draw.write(g, T.f("closed_cases"), x + 10, y + 23, Draw.YELLOW, false);
        scrolling = Math.max(0, Math.min(Math.max(0, pastCases.size() - 6), scrolling));
        if (pastCases.isEmpty()) Draw.paragraph(g, T.f("no_closed_case_yet_every"), x + 12, y + 44, 180, Draw.GREY);
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("dd/MM");
        for (int i = 0; i < 6 && i + scrolling < pastCases.size(); i++) {
            CompoundTag f = pastCases.getCompound(i + scrolling);
            int ly = y + 36 + i * 21;
            String special = f.getString("special");
            Draw.portrait(g, f.getString("species"), x + 9, ly + 1, 1f);
            Draw.scroll(g, Draw.speciesName(f.getString("species")), x + 29, ly, 104, Draw.TEXT);
            String stars = T.stars(f.getInt("stars"));
            Draw.write(g, stars, x + 194 - Draw.textFont().width(stars), ly, Draw.YELLOW, false);
            String type = BoardScreen.specialName(special);
            Draw.write(g, type, x + 29, ly + 10, BoardScreen.specialColour(special), false);
            String details = " · " + (f.getBoolean("capture") ? T.f("caught") : T.f("defeated")) + " · " + day.format(new java.util.Date(f.getLong("date")));
            Draw.scroll(g, details, x + 29 + Draw.textFont().width(type), ly + 10, 164 - Draw.textFont().width(type), Draw.DARK);
        }
        if (pastCases.size() > 6) {
            Draw.write(g, (scrolling + 1) + "-" + Math.min(pastCases.size(), scrolling + 6) + " / " + pastCases.size() + "  ▲▼", x + 118, y + 23, Draw.GREY, false);
        }
        Draw.inset(g, x + 204, y + 18, 112, 148);
        CompoundTag types = c.getCompound("types");
        int solved = 0;
        for (String t : TYPES) solved += types.getInt(t.isEmpty() ? "standard" : t) > 0 ? 1 : 0;
        Draw.write(g, T.f("kinds") + solved + " / " + TYPES.length, x + 210, y + 23, Draw.YELLOW, false);
        for (int i = 0; i < TYPES.length; i++) {
            int n = types.getInt(TYPES[i].isEmpty() ? "standard" : TYPES[i]), ly = y + 35 + i * 10;
            Draw.write(g, n > 0 ? "✔" : "·", x + 210, ly, n > 0 ? Draw.GREEN : Draw.DARK, false);
            Draw.write(g, BoardScreen.specialName(TYPES[i]), x + 221, ly, n > 0 ? BoardScreen.specialColour(TYPES[i]) : Draw.DARK, false);
            if (n > 0) Draw.write(g, "×" + n, x + 310 - Draw.textFont().width("×" + n), ly, Draw.GREY, false);
        }
    }

    // ---------- Team: teammates, invitations ----------

    private void team(GuiGraphics g, CompoundTag e, int x, int y, int mx, int my) {
        ListTag members = e.getList("team", Tag.TAG_STRING);
        String invitation = e.getString("invitation");
        boolean active = e.getBoolean("active"), leader = e.getBoolean("chef");
        Draw.inset(g, x + 4, y + 18, 152, 148);
        g.drawString(Draw.textFont(), T.f("team_2"), x + 10, y + 23, Draw.YELLOW, false);
        if (!invitation.isEmpty()) {
            int ly = Draw.paragraph(g, invitation + T.f("invites_you_to_join_their"), x + 12, y + 40, 136, Draw.TEXT);
            Draw.paragraph(g, T.f("you_will_work_on_their"), x + 12, ly + 4, 136, Draw.GREY);
            Draw.button(g, x + 12, y + 118, 136, 20, T.f("join"), Draw.in(mx, my, x + 12, y + 118, 136, 20), true, Draw.GREEN);
            Draw.button(g, x + 12, y + 141, 136, 18, T.f("decline"), Draw.in(mx, my, x + 12, y + 141, 136, 18), true, Draw.DARK_RED);
        } else if (!active) {
            Draw.paragraph(g, T.f("open_a_case_to_invite"), x + 12, y + 40, 136, Draw.GREY);
        } else {
            for (int i = 0; i < members.size() && i < 6; i++) {
                int ly = y + 38 + i * 13;
                g.drawString(Draw.textFont(), i == 0 ? "★" : "•", x + 12, ly, i == 0 ? Draw.YELLOW : Draw.ACCENT, false);
                g.drawString(Draw.textFont(), members.getString(i), x + 24, ly, Draw.TEXT, false);
            }
            g.drawString(Draw.textFont(), members.size() + " / " + e.getInt("teamMax"), x + 150 - Draw.textFont().width(members.size() + " / " + e.getInt("teamMax")), y + 23, Draw.GREY, false);
            if (!leader) Draw.button(g, x + 12, y + 141, 136, 18, T.f("leave_the_team"), Draw.in(mx, my, x + 12, y + 141, 136, 18), true, Draw.DARK_RED);
        }
        Draw.inset(g, x + 160, y + 18, 156, 148);
        g.drawString(Draw.textFont(), T.f("invite"), x + 166, y + 23, Draw.YELLOW, false);
        if (!active || !leader) {
            Draw.paragraph(g, active ? T.f("only_the_lead_investigator_can") : T.f("everyone_samples_and_analyses_for"),
                    x + 168, y + 40, 140, Draw.GREY);
            return;
        }
        boolean full = members.size() >= e.getInt("teamMax");
        if (full) {
            Draw.paragraph(g, T.f("the_team_is_full"), x + 168, y + 40, 140, Draw.GREY);
            return;
        }
        g.fill(x + 166, y + 34, x + 310, y + 48, Draw.EDGE);
        g.fill(x + 167, y + 35, x + 309, y + 47, 0xFF101A24);
        boolean cursor = (System.currentTimeMillis() / 400) % 2 == 0;
        if (searchText.isEmpty()) g.drawString(Draw.textFont(), T.f("search_for_a_player") , x + 171, y + 37, Draw.DARK, false);
        Draw.scroll(g, searchText + (cursor ? "_" : ""), x + 171, y + 37, 134, Draw.TEXT);
        java.util.List<CompoundTag> list = invitable();
        inviteScroll = Math.max(0, Math.min(Math.max(0, list.size() - 5), inviteScroll));
        if (list.isEmpty()) {
            Draw.paragraph(g, searchText.isEmpty() ? T.f("no_player_without_a_case") : T.f("no_free_player_matches"), x + 168, y + 56, 140, Draw.GREY);
        }
        for (int i = 0; i < 5 && i + inviteScroll < list.size(); i++) {
            CompoundTag c = list.get(i + inviteScroll);
            int ly = y + 51 + i * 17, together = c.getInt("together");
            Draw.button(g, x + 166, ly, 144, 16, c.getString("name") + (together > 0 ? "  ×" + together : ""), Draw.in(mx, my, x + 166, ly, 144, 16), true,
                    together > 0 ? Draw.ACCENT : 0xFF7C8FA3);
        }
        String count = list.size() > 5 ? (inviteScroll + 1) + "-" + Math.min(list.size(), inviteScroll + 5) + " / " + list.size() + " ▲▼" : list.size() + "";
        g.drawString(Draw.textFont(), count, x + 310 - Draw.textFont().width(count), y + 23, Draw.GREY, false);
        Draw.button(g, x + 166, y + 146, 144, 16, T.f("refresh"), Draw.in(mx, my, x + 166, y + 146, 144, 16), true, 0xFF7C8FA3);
    }

    // The search is typed on the keyboard as soon as the Team tab is open.
    @Override
    public boolean charTyped(char c, int mods) {
        if (tab == 3 && FieldInvestigationsClient.state.getBoolean("chef") && searchText.length() < 16 && (Character.isLetterOrDigit(c) || c == '_')) {
            searchText += c;
            inviteScroll = 0;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == 3 && key == 259 && !searchText.isEmpty()) { // Backspace
            searchText = searchText.substring(0, searchText.length() - 1);
            inviteScroll = 0;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private boolean clickTeam(double mx, double my, int x, int y) {
        CompoundTag e = FieldInvestigationsClient.state;
        if (!e.getString("invitation").isEmpty()) {
            boolean yes = Draw.in(mx, my, x + 12, y + 118, 136, 20), no = Draw.in(mx, my, x + 12, y + 141, 136, 18);
            if (yes || no) {
                Packet p = Packet.from("answer");
                p.data().putBoolean("accepted", yes);
                FieldInvestigationsClient.send(p);
                Draw.click(yes ? 1.3f : 0.7f);
                if (yes) tab = 0;
            }
            return true;
        }
        if (!e.getBoolean("active")) return true;
        if (!e.getBoolean("chef")) {
            if (Draw.in(mx, my, x + 12, y + 141, 136, 18)) {
                FieldInvestigationsClient.send(Packet.from("abandon"));
                Draw.click(0.7f);
            }
            return true;
        }
        if (e.getList("team", Tag.TAG_STRING).size() < e.getInt("teamMax")) {
            java.util.List<CompoundTag> list = invitable();
            for (int i = 0; i < 5 && i + inviteScroll < list.size(); i++) {
                if (!Draw.in(mx, my, x + 166, y + 51 + i * 17, 144, 16)) continue;
                Packet p = Packet.from("invite");
                p.data().putString("name", list.get(i + inviteScroll).getString("name"));
                FieldInvestigationsClient.send(p);
                Draw.click(1.2f);
                return true;
            }
            if (Draw.in(mx, my, x + 166, y + 146, 144, 16)) {
                FieldInvestigationsClient.send(Packet.from("team"));
                Draw.click(1f);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (tab == 2) scrolling -= (int) Math.signum(dy);
        if (tab == 3) inviteScroll -= (int) Math.signum(dy);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = BG.x(width), y = BG.y(height);
        for (int i = 0; i < 4; i++) {
            int[] place = placeTab(x, i);
            if (Draw.in(mx, my, place[0] - 3, y, place[1] + 6, 14)) {
                tab = i;
                Draw.click(1.2f);
                // The Team tab asks the server for an up-to-date list of nearby players.
                if (i == 3) FieldInvestigationsClient.send(Packet.from("team"));
                return true;
            }
        }
        if (tab == 3) return clickTeam(mx, my, x, y);
        if (tab != 0) return true;
        if (!FieldInvestigationsClient.state.getBoolean("active")) {
            if (Draw.in(mx, my, x + L / 2 - 80, y + 90, 160, 22) && minecraft != null) {
                Draw.click(1f);
                minecraft.setScreen(new BoardScreen());
            }
            return true;
        }
        if (FieldInvestigationsClient.state.getString("phase").equals("SAMPLING") && Draw.in(mx, my, x + 202, y + 140, 108, 22) && minecraft != null) {
            Draw.click(1f);
            minecraft.setScreen(new BoardScreen());
        }
        return true;
    }

    private void map(GuiGraphics g, CompoundTag e, int x, int y, double jx, double jz) {
        ListTag bearings = e.getList("bearings", Tag.TAG_COMPOUND);
        boolean hasFix = e.getBoolean("fixValid");
        double rx = e.getDouble("fixX"), rz = e.getDouble("fixZ"), uncertainty = e.getDouble("fixUncertainty");
        // Framing: everything that matters must fit in the map.
        double minX = jx, maxX = jx, minZ = jz, maxZ = jz;
        for (int i = 0; i < bearings.size(); i++) {
            CompoundTag r = bearings.getCompound(i);
            if (r.getBoolean("expired")) continue;
            minX = Math.min(minX, r.getDouble("x"));
            maxX = Math.max(maxX, r.getDouble("x"));
            minZ = Math.min(minZ, r.getDouble("z"));
            maxZ = Math.max(maxZ, r.getDouble("z"));
        }
        if (hasFix) {
            minX = Math.min(minX, rx - uncertainty);
            maxX = Math.max(maxX, rx + uncertainty);
            minZ = Math.min(minZ, rz - uncertainty);
            maxZ = Math.max(maxZ, rz + uncertainty);
        }
        double mx0 = (minX + maxX) / 2, mz0 = (minZ + maxZ) / 2;
        double half = Math.max(80, Math.max(maxX - minX, maxZ - minZ) / 2 * 1.2);
        double scale = (MAP / 2.0) / half;
        int ox = x + 4, oy = y + 18;
        Draw.inset(g, ox, oy, MAP + 2, MAP + 2);
        g.fill(ox + 1, oy + 1, ox + MAP + 1, oy + MAP + 1, 0xFF101A24);
        g.enableScissor(ox + 1, oy + 1, ox + MAP + 1, oy + MAP + 1);
        int ccx = ox + 1 + MAP / 2, ccy = oy + 1 + MAP / 2;
        for (double q = Math.ceil((mx0 - half) / 50) * 50; q < mx0 + half; q += 50) {
            int px = (int) (ccx + (q - mx0) * scale);
            g.fill(px, oy + 1, px + 1, oy + MAP + 1, 0xFF1C2C3B);
        }
        for (double q = Math.ceil((mz0 - half) / 50) * 50; q < mz0 + half; q += 50) {
            int py = (int) (ccy + (q - mz0) * scale);
            g.fill(ox + 1, py, ox + MAP + 1, py + 1, 0xFF1C2C3B);
        }
        int n = 0;
        for (int i = 0; i < bearings.size(); i++) {
            CompoundTag r = bearings.getCompound(i);
            if (r.getBoolean("expired")) continue;
            n++;
            double px = ccx + (r.getDouble("x") - mx0) * scale, py = ccy + (r.getDouble("z") - mz0) * scale;
            double length = MAP * 2;
            for (int side = -1; side <= 1; side++) {
                double a = Math.toRadians(r.getDouble("cap") + side * r.getDouble("error"));
                Draw.segment(g, px, py, px + Math.sin(a) * length, py - Math.cos(a) * length, side == 0 ? 2 : 1, side == 0 ? Draw.ACCENT : 0xFF2C6F73);
            }
            g.fill((int) px - 2, (int) py - 2, (int) px + 3, (int) py + 3, Draw.TEXT);
            Draw.write(g, String.valueOf(i + 1), (int) px + 4, (int) py - 9, Draw.TEXT, false);
        }
        if (hasFix) {
            double px = ccx + (rx - mx0) * scale, py = ccy + (rz - mz0) * scale;
            Draw.circle(g, px, py, uncertainty * scale, 2, Draw.YELLOW);
            Draw.segment(g, px - 4, py, px + 4, py, 2, Draw.YELLOW);
            Draw.segment(g, px, py - 4, px, py + 4, 2, Draw.YELLOW);
        }
        double pjx = ccx + (jx - mx0) * scale, pjy = ccy + (jz - mz0) * scale;
        g.fill((int) pjx - 2, (int) pjy - 2, (int) pjx + 3, (int) pjy + 3, Draw.GREEN);
        g.disableScissor();
        Draw.write(g, "N", ccx - 2, oy + 4, Draw.GREY, false);
        Draw.write(g, T.f("1_square_50_blocks"), ox + 4, oy + MAP - 8, Draw.DARK, false);

        int tx = ox + MAP + 10, lw = L - MAP - 22;
        Draw.inset(g, tx - 4, oy, lw + 8, MAP + 2);
        String phase = e.getString("phase");
        Draw.write(g, phase.equals("TRACKING") ? T.f("bearings") + n + ")" : phase.equals("SPOTTING") ? T.f("target_in_sight")
                : phase.equals("APPROACH") ? T.f("approach").toUpperCase() : T.f("flushed_out_2"), tx, oy + 6, Draw.YELLOW, false);
        int ly = oy + 20;
        int stars = e.getInt("stars");
        if (phase.equals("SPOTTING")) {
            ly = Draw.paragraph(g, T.f("the_target_is_in_sight"),
                    tx, ly, lw, Draw.TEXT) + 6;
            Draw.paragraph(g, T.f("if_you_get_close_before"), tx, ly, lw, Draw.GREY);
            return;
        }
        if (phase.equals("APPROACH") || phase.equals("FLUSHED")) {
            // Target identified: its portrait, the investigation quality and what it is worth.
            Draw.portrait(g, e.getString("species"), tx + (lw - 48) / 2, ly, 3f);
            Draw.centre(g, Draw.speciesName(e.getString("species")), tx + lw / 2, ly + 54, Draw.TEXT);
            Draw.centre(g, T.stars(stars), tx + lw / 2, ly + 66, Draw.YELLOW);
            Draw.centre(g, T.iv(stars) + T.f("perfect_ivs_on_capture"), tx + lw / 2, ly + 78, Draw.ACCENT);
            Draw.paragraph(g, phase.equals("FLUSHED") ? T.f("there_it_is_battle_it") : T.f("walk_don_t_run_a"),
                    tx, ly + 94, lw, Draw.GREY);
            return;
        }
        if (hasFix) {
            Draw.write(g, T.f("fix"), tx, ly, Draw.ACCENT, false);
            Draw.write(g, "X " + Math.round(rx) + "  Z " + Math.round(rz), tx, ly + 11, Draw.TEXT, false);
            Draw.write(g, "± " + Math.round(uncertainty / 5) * 5 + T.f("blocks_2"), tx, ly + 22, Draw.YELLOW, false);
            Draw.write(g, T.f("at") + Math.round(Math.hypot(rx - jx, rz - jz)) + T.f("blocks") + Draw.wind(Draw.bearing(jx, jz, rx, rz)), tx, ly + 33, Draw.GREY, false);
            ly += 48;
        } else {
            ly = Draw.paragraph(g, T.c(e.getString("fixMessage")), tx, ly, lw, Draw.GREY) + 6;
        }
        Draw.paragraph(g, T.f("white_your_bearings_bright_line")
                + T.f("the_closer_you_are_the"), tx, ly, lw, Draw.DARK);
    }
}
