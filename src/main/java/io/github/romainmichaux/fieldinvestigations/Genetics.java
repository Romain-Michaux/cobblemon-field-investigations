package io.github.romainmichaux.fieldinvestigations;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// Genetic profile of a species: 5 markers taken from its real data (types, size, egg group, highest stat).
// Each marker has a value (a short key), a label and a colour; the client only receives the keys.
public final class Genetics {
    public static final List<String> MARKERS = List.of("type1", "type2", "size", "egg", "stat");
    public static final Map<String, String> MARKER_NAMES = Map.of(
            "type1", "cobblemon_field_investigations.marker.type1", "type2", "cobblemon_field_investigations.marker.type2", "size", "cobblemon_field_investigations.marker.size",
            "egg", "cobblemon_field_investigations.marker.egg", "stat", "cobblemon_field_investigations.marker.stat");
    public static final Map<String, String> TRACE_NAMES = Map.of(
            "type1", "cobblemon_field_investigations.trace.type1", "type2", "cobblemon_field_investigations.trace.type2", "size", "cobblemon_field_investigations.trace.size",
            "egg", "cobblemon_field_investigations.trace.egg", "stat", "cobblemon_field_investigations.trace.stat");
    public static final Map<String, String> BURIED_NAMES = Map.of(
            "size", "cobblemon_field_investigations.buried.size", "egg", "cobblemon_field_investigations.buried.egg", "stat", "cobblemon_field_investigations.buried.stat");
    // Number of possible dug-up objects per buriable marker (bone or tooth; shells, feather or shed skin; fang, claw or
    // scale).
    public static final Map<String, Integer> VARIANTS = Map.of("size", 2, "egg", 3, "stat", 3);
    // Possible analysis mini-games per marker (drawn when the sample is created).
    public static final Map<String, List<String>> ANALYSIS_GAMES = Map.of(
            "type1", List.of("sequencing", "gel", "chromatography"), "type2", List.of("pairing", "pcr", "microscope"),
            "size", List.of("centrifugation", "count", "bite"), "egg", List.of("fragments", "gel", "family_tree"),
            "stat", List.of("spectrum", "colorimetry", "titration"));
    public static final Map<String, List<String>> SAMPLING_GAMES = Map.of(
            "type1", List.of("crystals", "photo"), "type2", List.of("pipette", "swab"), "size", List.of("measure", "cast"),
            "egg", List.of("sorting", "tweezers"), "stat", List.of("trace", "photo"));
    public static final List<String> FIELD_GAMES = List.of("crystals", "pipette", "measure", "sorting", "trace", "brush", "sieving", "cast",
            "tweezers", "photo", "swab", "dive", "climb", "abseil", "lamp", "rockfall", "filter");
    public static final List<String> LAB_GAMES = List.of("sequencing", "pairing", "centrifugation", "fragments", "spectrum", "gel", "colorimetry",
            "pcr", "count", "microscope", "chromatography", "family_tree", "titration", "bite", "thaw");

    // Mini-game names for the /hunt game command, which can differ from the internal game ids.
    public static final Map<String, String> COMMAND_NAMES = new java.util.LinkedHashMap<>();
    static {
        String[][] names = {{"crystals", "crystals"}, {"pipette", "pipette"}, {"measure", "measure"}, {"sorting", "sorting"}, {"tracing", "trace"},
                {"brush", "brush"}, {"sieve", "sieving"}, {"cast", "cast"}, {"tweezers", "tweezers"}, {"photo", "photo"}, {"swab", "swab"},
                {"dive", "dive"}, {"climb", "climb"}, {"abseil", "abseil"}, {"uvlamp", "lamp"}, {"rockfall", "rockfall"}, {"filter", "filter"},
                {"sequencing", "sequencing"}, {"pairing", "pairing"}, {"centrifuge", "centrifugation"}, {"fragments", "fragments"},
                {"spectrum", "spectrum"}, {"gel", "gel"}, {"colour", "colorimetry"}, {"pcr", "pcr"}, {"count", "count"}, {"microscope", "microscope"},
                {"chromatography", "chromatography"}, {"tree", "family_tree"}, {"titration", "titration"}, {"bite", "bite"}, {"thaw", "thaw"}};
        for (String[] n : names) COMMAND_NAMES.put(n[0], n[1]);
    }

    public static String commandName(String game) {
        for (Map.Entry<String, String> e : COMMAND_NAMES.entrySet()) {
            if (e.getValue().equals(game)) return e.getKey();
        }
        return game;
    }

    public static String gameName(String game) {
        return "cobblemon_field_investigations.game." + game;
    }

    public static String traceName(String kind, boolean buried, int variant) {
        if (!buried) return TRACE_NAMES.getOrDefault(kind, "cobblemon_field_investigations.trace.type1");
        String key = BURIED_NAMES.getOrDefault(kind, "cobblemon_field_investigations.buried.size");
        return variant > 0 && variant < VARIANTS.getOrDefault(kind, 1) ? key + "_" + variant : key;
    }

    // Trace block "kind" property value for the object dug out of the ground.
    public static int buriedBlock(String kind, int variant) {
        int[] blocks = switch (kind) {
            case "size" -> new int[] {5, 8};
            case "egg" -> new int[] {6, 9, 10};
            default -> new int[] {7, 11, 12};
        };
        return blocks[Math.max(0, Math.min(blocks.length - 1, variant))];
    }

    // The sampling gesture depends on what is sampled, and sometimes on the case type: free-diving for an aquatic case,
    // climbing in the canopy, abseiling at altitude, UV lamp at night. The draw is fixed for a given trace.
    public static String samplingGame(String kind, boolean buried, boolean cave, String special, java.util.Random h) {
        if (buried) return h.nextBoolean() ? "brush" : "sieving";
        if (cave && h.nextInt(3) != 0) return "rockfall";
        if (special != null && h.nextInt(3) != 0) {
            switch (special) {
                case "aquatic": return "dive";
                case "canopy": return "climb";
                case "altitude": return "abseil";
                case "nocturnal": return "lamp";
                case "underground": return "rockfall";
                default:
            }
        }
        List<String> possible = SAMPLING_GAMES.getOrDefault(kind, List.of("crystals"));
        return possible.get(h.nextInt(possible.size()));
    }

    public static String analysisGame(String kind, String game) {
        List<String> possible = ANALYSIS_GAMES.getOrDefault(kind, List.of("sequencing"));
        return possible.contains(game) || "thaw".equals(game) ? game : possible.get(0);
    }

    private static final Map<String, Integer> TYPE_COLOURS = Map.ofEntries(
            Map.entry("fire", 0xE8543A), Map.entry("water", 0x4A90D9), Map.entry("grass", 0x5DBB5D), Map.entry("electric", 0xF2C93A),
            Map.entry("ice", 0x7FD4E0), Map.entry("fighting", 0xB5503C), Map.entry("poison", 0x9B5FC0), Map.entry("ground", 0xC9A45A),
            Map.entry("flying", 0x9DB7F0), Map.entry("psychic", 0xEE6A9A), Map.entry("bug", 0xA2B833), Map.entry("rock", 0xB0A070),
            Map.entry("ghost", 0x6B5B9A), Map.entry("dragon", 0x6A5AE0), Map.entry("dark", 0x5A4A44), Map.entry("steel", 0xA8A8C0),
            Map.entry("fairy", 0xF0A0D0), Map.entry("normal", 0xB8B8A0), Map.entry("none", 0x55606C));
    private static final int[] SIZE_COLOURS = {0xF4F1DE, 0xF2CC8F, 0xE9A15B, 0xE07A5F, 0x8D4E3A};
    private static final List<String> EGGS = List.of("MONSTER", "WATER_1", "BUG", "FLYING", "FIELD", "FAIRY", "GRASS", "HUMAN_LIKE",
            "WATER_3", "MINERAL", "AMORPHOUS", "WATER_2", "DITTO", "DRAGON", "UNDISCOVERED");
    private static final int[] EGG_COLOURS = {0xC25B56, 0x5B9BD5, 0x9BBB59, 0xA6C8F0, 0xC8A165, 0xF4A6C8, 0x6DBE6D, 0xE0B080,
            0x3F6FB5, 0x9AA0A6, 0x8E7CC3, 0x76B7E8, 0xD98CD9, 0x7461D1, 0x707070};
    private static final List<String> STATS = List.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");
    private static final int[] STAT_COLOURS = {0x6CCB5F, 0xE8543A, 0xE9A15B, 0x9B5FC0, 0x4FC1C5, 0xF2C93A};

    private Genetics() {}

    public static String value(String marker, Species s) {
        switch (marker) {
            case "type1":
                return s.getPrimaryType().getName().toLowerCase();
            case "type2":
                return s.getSecondaryType() == null ? "none" : s.getSecondaryType().getName().toLowerCase();
            case "size": {
                float m = s.getHeight() / 10f;
                return String.valueOf(m < 0.4f ? 0 : m < 1f ? 1 : m < 1.6f ? 2 : m <= 2.5f ? 3 : 4);
            }
            case "egg":
                return s.getEggGroups().stream().min(Comparator.comparingInt(Enum::ordinal)).map(Enum::name).orElse("UNDISCOVERED");
            default: {
                String best = "hp";
                int max = -1;
                for (Map.Entry<Stat, Integer> e : s.getBaseStats().entrySet()) {
                    String id = e.getKey().getIdentifier().getPath();
                    if (!STATS.contains(id)) continue;
                    if (e.getValue() > max || (e.getValue() == max && STATS.indexOf(id) < STATS.indexOf(best))) {
                        max = e.getValue();
                        best = id;
                    }
                }
                return best;
            }
        }
    }

    public static MutableComponent label(String marker, String v) {
        switch (marker) {
            case "type1":
            case "type2":
                return v.equals("none") ? Component.translatable("cobblemon_field_investigations.none") : Component.translatable("cobblemon.type." + v);
            case "size":
                return Component.translatable("cobblemon_field_investigations.size." + clampedIndex(v, 5));
            case "egg":
                return Component.translatable("cobblemon_field_investigations.egg." + EGGS.get(Math.max(0, EGGS.indexOf(v))).toLowerCase());
            default:
                return Component.translatable("cobblemon.stat." + v + ".name");
        }
    }

    public static int colour(String marker, String v) {
        switch (marker) {
            case "type1":
            case "type2":
                return TYPE_COLOURS.getOrDefault(v, 0xB8B8A0);
            case "size":
                return SIZE_COLOURS[clampedIndex(v, 5)];
            case "egg":
                return EGG_COLOURS[Math.max(0, EGGS.indexOf(v))];
            default:
                return STAT_COLOURS[Math.max(0, STATS.indexOf(v))];
        }
    }

    private static int clampedIndex(String v, int size) {
        try {
            return Math.max(0, Math.min(size - 1, Integer.parseInt(v)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int common(Species a, Species b) {
        int n = 0;
        for (String m : MARKERS) {
            if (value(m, a).equals(value(m, b))) n++;
        }
        return n;
    }
}
