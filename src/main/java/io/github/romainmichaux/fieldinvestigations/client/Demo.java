package io.github.romainmichaux.fieldinvestigations.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import io.github.romainmichaux.fieldinvestigations.Genetics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

// Visual check tool, inactive normally: if the file config/cobblemon_field_investigations_demo exists, the client opens
// every screen with fake data as soon as it joins a world, saves screenshots/demo_*.png, then quits the game.
final class Demo {
    static boolean active, scan;
    // Simulated mouse position for a demo step (null = the real mouse).
    static int[] mouse;
    private static final List<String> NAMES = new ArrayList<>();
    private static final List<Runnable> STEPS = new ArrayList<>();
    private static int waiting = 120, step = -1, since, stepDuration = 20;

    private Demo() {}

    static void install() {
        if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_demo"))) return;
        Minecraft mc = Minecraft.getInstance();
        String content = "";
        try {
            content = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_demo")).trim();
        } catch (Exception e) {
            // unreadable file: regular demo
        }
        // "play <game>": no screenshots, the game opens once as a practice run after joining the world, then the
        // player keeps playing normally (replay with /hunt game).
        if (content.startsWith("play ")) {
            String game = Genetics.COMMAND_NAMES.getOrDefault(content.substring(5).trim(), content.substring(5).trim());
            int[] wait = {60};
            ClientTickEvents.END_CLIENT_TICK.register(c -> {
                if (c.player == null || wait[0] < 0 || --wait[0] > 0) return;
                wait[0] = -1;
                AnalysisScreen screen = new AnalysisScreen("stat", game, System.nanoTime(), 2, false, false, Genetics.FIELD_GAMES.contains(game), 0);
                screen.attempt = true;
                c.setScreen(screen);
            });
            return;
        }
        // "only <text>": regular demo restricted to the steps whose name contains that text.
        if (content.startsWith("only ")) only = content.substring(5).trim();
        active = true;
        // Sound endurance test: if the file contains "ambience", a single screen (the sequencer) stays open for two minutes,
        // reopened every two seconds on another game while UI sounds play, then the game closes.
        try {
            if (Files.readString(FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_demo")).contains("ambience")) {
                stepDuration = 2400;
                step("sound_endurance", () -> mc.setScreen(new AnalysisScreen("stat", "colorimetry", 42L, 2, false, false, false, 0)));
                ClientTickEvents.END_CLIENT_TICK.register(Demo::tick);
                ClientTickEvents.END_CLIENT_TICK.register(c -> {
                    if (step == 0 && since % 7 == 0) Draw.click(0.9f + (since % 5) * 0.12f);
                    if (step == 0 && since % 31 == 0) Draw.note(0.5f + (since % 4) * 0.25f);
                    if (step == 0 && since % 40 == 20) c.setScreen(null);
                    if (step == 0 && since % 40 == 0 && since > 0) {
                        String[] games = {"colorimetry", "gel", "microscope", "titration", "family_tree", "chromatography"};
                        c.setScreen(new AnalysisScreen("stat", games[(since / 40) % games.length], since, 2, false, false, false, 0));
                    }
                });
                return;
            }
        } catch (Exception e) {
            // unreadable file: regular demo
        }
        step("board_empty", () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("passed", 3);
            FieldInvestigationsClient.state = t;
            mc.setScreen(new BoardScreen());
        });
        step("board_leads", () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("passed", 3);
            ListTag leads = new ListTag();
            String[] specials = {"", "underground", "dig_site"};
            for (int i = 0; i < 3; i++) {
                CompoundTag c = new CompoundTag();
                c.putInt("rank", i);
                c.putString("special", specials[i]);
                c.putBoolean("new", i != 0);
                c.putDouble("zoneX", mc.player.getX() + 110 - i * 90);
                c.putDouble("zoneZ", mc.player.getZ() - 60 + i * 70);
                leads.add(c);
            }
            t.put("leads", leads);
            t.putLong("leadsEnd", 540_000);
            FieldInvestigationsClient.state = t;
            FieldInvestigationsClient.stateReceived = System.currentTimeMillis();
            mc.setScreen(new BoardScreen());
        });
        step("board", () -> {
            FieldInvestigationsClient.state = state("SAMPLING", 3);
            mc.setScreen(new BoardScreen());
        });
        step("board_tooltip", () -> {
            FieldInvestigationsClient.state = state("SAMPLING", 3);
            mc.setScreen(new BoardScreen());
            // Mouse simulated over a suspect surrounded by Poké Balls: the tooltip must render in front of them.
            mouse = new int[] {(mc.getWindow().getGuiScaledWidth() - 336) / 2 + 134 + 50 + 20, (mc.getWindow().getGuiScaledHeight() - 189) / 2 + 24};
        });
        step("board_hunt", () -> {
            mouse = null;
            FieldInvestigationsClient.state = state("TRACKING", 5);
            mc.setScreen(new BoardScreen());
        });
        String[][] analyses = {{"type1", "sequencing"}, {"type1", "gel"}, {"type2", "pairing"}, {"type2", "pcr"}, {"size", "centrifugation"},
                {"size", "count"}, {"egg", "fragments"}, {"stat", "spectrum"}, {"stat", "colorimetry"}, {"type1", "chromatography"},
                {"type2", "microscope"}, {"size", "bite"}, {"egg", "family_tree"}, {"stat", "titration"}, {"stat", "thaw"}};
        for (String[] a : analyses) {
            step("analyse_" + a[1], () -> mc.setScreen(new AnalysisScreen(a[0], a[1], 42L, 2, a[1].equals("sequencing"), false, false, 0)));
        }
        String[][] samplings = {{"type1", "crystals"}, {"type2", "pipette"}, {"size", "measure"}, {"egg", "sorting"}, {"stat", "trace"},
                {"size", "brush"}, {"egg", "brush"}, {"stat", "brush"}, {"egg", "sieving"}, {"type1", "photo"},
                {"type2", "swab"}, {"size", "cast"}, {"egg", "tweezers"}, {"type1", "dive"}, {"stat", "climb"}, {"stat", "abseil"},
                {"type1", "lamp"}, {"type2", "rockfall"}, {"", "filter"}};
        for (String[] a : samplings) {
            boolean buried = a[1].equals("brush") || a[1].equals("sieving");
            // The digs show the base objects; sieving shows an object added in 1.1 (the feather).
            step("sampling_" + a[1] + (buried ? "_" + a[0] : ""), () -> mc.setScreen(new AnalysisScreen(a[0], a[1], 7L, 0, false, buried, true,
                    a[1].equals("sieving") ? 1 : 0)));
        }
        step("kit_samples", () -> {
            FieldInvestigationsClient.state = state("SAMPLING", 2);
            mc.setScreen(new KitScreen());
        });
        step("kit_map", () -> {
            FieldInvestigationsClient.state = state("TRACKING", 5);
            mc.setScreen(new KitScreen());
        });
        step("kit_spotting", () -> {
            FieldInvestigationsClient.state = state("SPOTTING", 5);
            mc.setScreen(new KitScreen());
        });
        step("kit_flushed", () -> {
            FieldInvestigationsClient.state = state("FLUSHED", 5);
            mc.setScreen(new KitScreen());
        });
        step("kit_logbook", () -> {
            FieldInvestigationsClient.state = state("SAMPLING", 2);
            mc.setScreen(new KitScreen(1));
        });
        step("kit_archives", () -> {
            FieldInvestigationsClient.state = state("SAMPLING", 2);
            mc.setScreen(new KitScreen(2));
        });
        step("kit_team", () -> {
            CompoundTag t = state("SAMPLING", 2);
            ListTag members = new ListTag(), nearby = new ListTag();
            members.add(StringTag.valueOf("Romain"));
            members.add(StringTag.valueOf("Camille"));
            String[][] nearbyPlayers = {{"Sacha", "7"}, {"Alex", "3"}, {"Billie", "0"}, {"Charlie", "0"}, {"Dominique", "0"}, {"Eden", "0"}, {"Noa", "0"}};
            for (String[] l : nearbyPlayers) {
                CompoundTag c = new CompoundTag();
                c.putString("name", l[0]);
                c.putInt("together", Integer.parseInt(l[1]));
                nearby.add(c);
            }
            t.put("team", members);
            t.put("close", nearby);
            t.putBoolean("lead", true);
            t.putInt("teamMax", 4);
            FieldInvestigationsClient.state = t;
            mc.setScreen(new KitScreen(3));
        });
        step("first_time", () -> {
            AnalysisScreen screen = new AnalysisScreen("egg", "sorting", 7L, 0, false, false, true, 0);
            screen.intro = true;
            mc.setScreen(screen);
        });
        step("first_time_abseil", () -> {
            AnalysisScreen screen = new AnalysisScreen("stat", "abseil", 7L, 0, false, false, true, 0);
            screen.intro = true;
            mc.setScreen(screen);
        });
        step("hud", () -> {
            mc.setScreen(null);
            FieldInvestigationsClient.announceTitle = net.minecraft.network.chat.Component.translatable("cobblemon_field_investigations.announce.new_investigation.title");
            FieldInvestigationsClient.announceText = net.minecraft.network.chat.Component.translatable("cobblemon_field_investigations.announce.new_investigation.text");
            FieldInvestigationsClient.announceColour = 0x4FD1C5;
            FieldInvestigationsClient.announceReceived = System.currentTimeMillis();
        });
        step("scanner", () -> {
            mc.setScreen(null);
            scan = true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(Demo::tick);
    }

    // Texts too long for their frame, collected during the demo (they scroll in game): screen + text -> excess pixels.
    private static final java.util.Map<String, Integer> OVERFLOWS = new java.util.LinkedHashMap<>();

    static void overflow(String text, int pixels) {
        if (active && step >= 0 && step < NAMES.size()) OVERFLOWS.merge(NAMES.get(step) + "\t" + text, pixels, Math::max);
    }

    private static String only;

    private static void step(String name, Runnable action) {
        if (only != null && !name.contains(only)) return;
        NAMES.add(String.format("%02d_%s", NAMES.size() + 1, name));
        STEPS.add(action);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        if (step >= STEPS.size() - 2) {
            CompoundTag s = new CompoundTag();
            s.putString("phase", scan ? "SPOTTING" : "SAMPLING");
            s.putInt("distance", 31);
            s.putInt("alert", 18);
            s.putInt("level", 5);
            s.putBoolean("buried", true);
            s.putDouble("metres", 3.4);
            FieldInvestigationsClient.signal = s;
            FieldInvestigationsClient.signalReceived = System.currentTimeMillis();
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        since++;
        if (step < 0 || since == stepDuration) {
            step++;
            since = 0;
            if (step >= STEPS.size()) {
                try {
                    StringBuilder sb = new StringBuilder();
                    OVERFLOWS.forEach((key, px) -> sb.append(key).append("\t+").append(px).append(" px\n"));
                    Files.writeString(mc.gameDirectory.toPath().resolve("screenshots").resolve("demo_overflows.txt"), sb.toString());
                } catch (Exception e) {
                    // this report is only a debugging aid
                }
                mc.stop();
                return;
            }
            STEPS.get(step).run();
        } else if (since == 14) {
            Screenshot.grab(mc.gameDirectory, "demo_" + NAMES.get(step) + ".png", mc.getMainRenderTarget(), c -> {});
        }
    }

    private static CompoundTag state(String phase, int pins) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", true);
        t.putInt("passed", 3);
        t.putString("phase", phase);
        t.putInt("rank", 2);
        Minecraft mc = Minecraft.getInstance();
        double jx = mc.player.getX(), jz = mc.player.getZ();
        t.putDouble("zoneX", jx + 120);
        t.putDouble("zoneZ", jz - 75);
        t.putInt("zoneRadius", 45);
        t.putInt("required", 3);
        t.putString("guess", phase.equals("SAMPLING") ? "" : "vulpix");
        if (phase.equals("TRACKING")) t.putString("special", "nocturnal");
        String[] target = {"fire", "none", "1", "FIELD", "speed"};
        ListTag traces = new ListTag();
        for (int i = 0; i < 5; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("kind", Genetics.MARKERS.get(i));
            c.putBoolean("harvest", i <= pins);
            c.putBoolean("pin", i < pins);
            if (i < pins) c.putString("value", target[i]);
            if (i >= 3 && pins < 5) {
                c.putBoolean("buried", true);
                c.putInt("variant", 1);
                c.putBoolean("lost", i == 4);
            }
            traces.add(c);
        }
        t.put("traces", traces);
        String[][] suspectRows = {
                {"mankey", "fighting", "none", "1", "FIELD", "attack"}, {"growlithe", "fire", "none", "1", "FIELD", "attack"},
                {"rattata", "normal", "none", "0", "FIELD", "speed"}, {"pikachu", "electric", "none", "1", "FIELD", "speed"},
                {"sandshrew", "ground", "none", "1", "FIELD", "defence"}, {"ponyta", "fire", "none", "2", "FIELD", "speed"},
                {"zubat", "poison", "flying", "1", "FLYING", "speed"}, {"eevee", "normal", "none", "0", "FIELD", "special_defence"},
                {"vulpix", "fire", "none", "1", "FIELD", "speed"}, {"charmander", "fire", "none", "1", "MONSTER", "speed"},
                {"geodude", "rock", "ground", "1", "MINERAL", "defence"}, {"oddish", "grass", "poison", "1", "GRASS", "special_attack"}};
        ListTag suspects = new ListTag();
        for (String[] f : suspectRows) {
            CompoundTag c = new CompoundTag();
            c.putString("species", f[0]);
            ListTag values = new ListTag();
            boolean ruledOut = false;
            for (int m = 0; m < 5; m++) {
                values.add(StringTag.valueOf(f[m + 1]));
                if (m < pins && !f[m + 1].equals(target[m])) ruledOut = true;
            }
            c.put("values", values);
            c.putBoolean("ruled_out", ruledOut);
            c.putBoolean("owned", f[0].equals("charmander") || f[0].equals("pikachu") || f[0].equals("growlithe"));
            suspects.add(c);
        }
        t.put("suspects", suspects);
        ListTag bearings = new ListTag();
        double[][] points = {{-20, 30, 52, 9}, {-70, 55, 62, 12}, {25, 95, 22, 7}};
        for (double[] p : points) {
            CompoundTag c = new CompoundTag();
            c.putDouble("x", jx + p[0]);
            c.putDouble("z", jz + p[1]);
            c.putDouble("heading", p[2]);
            c.putDouble("error", p[3]);
            bearings.add(c);
        }
        t.put("bearings", bearings);
        t.putBoolean("fixValid", true);
        t.putString("fixMessage", "");
        t.putDouble("fixX", jx + 110);
        t.putDouble("fixZ", jz - 60);
        t.putDouble("fixUncertainty", 60);
        t.putString("species", "vulpix");
        t.putInt("stars", phase.equals("SAMPLING") ? 4 : 3);
        CompoundTag career = new CompoundTag();
        career.putInt("xp", 395);
        career.putInt("rank", 3);
        career.putInt("threshold", 320);
        career.putInt("next", 560);
        career.putInt("perfect", 2);
        career.putInt("specials", 3);
        career.putInt("species_count", 6);
        CompoundTag types = new CompoundTag();
        types.putInt("standard", 4);
        types.putInt("nocturnal", 1);
        types.putInt("aquatic", 1);
        types.putInt("canopy", 1);
        career.put("types", types);
        ListTag archives = new ListTag();
        String[][] pastCases = {{"vulpix", "", "5"}, {"magikarp", "aquatic", "4"}, {"zubat", "nocturnal", "3"}, {"oddish", "", "5"},
                {"pikachu", "canopy", "2"}, {"geodude", "", "4"}, {"eevee", "", "3"}};
        for (int i = 0; i < pastCases.length; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("species", pastCases[i][0]);
            c.putString("special", pastCases[i][1]);
            c.putInt("stars", Integer.parseInt(pastCases[i][2]));
            c.putBoolean("capture", i % 3 != 2);
            c.putLong("date", System.currentTimeMillis() - i * 86_400_000L);
            archives.add(c);
        }
        career.put("archives", archives);
        t.put("career", career);
        return t;
    }
}
