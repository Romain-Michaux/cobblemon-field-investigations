package fr.cobblescout.traque;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// Profil génétique d'une espèce : 5 marqueurs tirés de ses vraies données (types, taille, groupe d'œuf, stat dominante).
// Chaque marqueur a une valeur (une clé courte), un libellé et une couleur ; le client ne reçoit que les clés.
public final class Genetique {
    public static final List<String> MARQUEURS = List.of("type1", "type2", "taille", "oeuf", "stat");
    // Tous les noms sont des clés de traduction : l'anglais par défaut, le français fourni avec le mod.
    public static final Map<String, String> NOMS_MARQUEURS = Map.of(
            "type1", "cobblemon_field_investigations.marqueur.type1", "type2", "cobblemon_field_investigations.marqueur.type2", "taille", "cobblemon_field_investigations.marqueur.taille",
            "oeuf", "cobblemon_field_investigations.marqueur.oeuf", "stat", "cobblemon_field_investigations.marqueur.stat");
    public static final Map<String, String> NOMS_TRACES = Map.of(
            "type1", "cobblemon_field_investigations.trace.type1", "type2", "cobblemon_field_investigations.trace.type2", "taille", "cobblemon_field_investigations.trace.taille",
            "oeuf", "cobblemon_field_investigations.trace.oeuf", "stat", "cobblemon_field_investigations.trace.stat");
    public static final Map<String, String> NOMS_ENFOUIS = Map.of(
            "taille", "cobblemon_field_investigations.enfoui.taille", "oeuf", "cobblemon_field_investigations.enfoui.oeuf", "stat", "cobblemon_field_investigations.enfoui.stat");
// Ce qu'on déterre varie : nombre d'objets possibles pour chaque marqueur enfouissable (os ou dent ; coquilles,
    // plume ou mue ; croc, griffe ou écaille).
    public static final Map<String, Integer> VARIANTES = Map.of("taille", 2, "oeuf", 3, "stat", 3);
    // Mini-jeux d'analyse possibles pour chaque marqueur (tiré au sort à la création de l'échantillon).
    public static final Map<String, List<String>> JEUX_ANALYSE = Map.of(
            "type1", List.of("sequencage", "gel", "chromatographie"), "type2", List.of("appariement", "pcr", "microscope"),
            "taille", List.of("centrifugation", "comptage", "morsure"), "oeuf", List.of("fragments", "gel", "parente"),
            "stat", List.of("spectre", "titrage", "dosage"));
    public static final Map<String, List<String>> JEUX_PRELEVEMENT = Map.of(
            "type1", List.of("memoire", "photo"), "type2", List.of("pipette", "ecouvillon"), "taille", List.of("mesure", "moulage"),
            "oeuf", List.of("tri", "pince"), "stat", List.of("trace", "photo"));
    // Tous les mini-jeux, pour la commande d'essai /hunt game : prélèvements, puis analyses.
    public static final List<String> JEUX_TERRAIN = List.of("memoire", "pipette", "mesure", "tri", "trace", "degagement", "tamisage", "moulage",
            "pince", "photo", "ecouvillon", "apnee", "grimpe", "rappel", "lampe", "eboulis", "filtrage");
    public static final List<String> JEUX_LABO = List.of("sequencage", "appariement", "centrifugation", "fragments", "spectre", "gel", "titrage",
            "pcr", "comptage", "microscope", "chromatographie", "parente", "dosage", "morsure", "degel");

    // Noms anglais des mini-jeux pour la commande /hunt game (les identifiants internes restent ceux du code, en français).
    public static final Map<String, String> NOMS_COMMANDE = new java.util.LinkedHashMap<>();
    static {
        String[][] noms = {{"crystals", "memoire"}, {"pipette", "pipette"}, {"measure", "mesure"}, {"sorting", "tri"}, {"tracing", "trace"},
                {"brush", "degagement"}, {"sieve", "tamisage"}, {"cast", "moulage"}, {"tweezers", "pince"}, {"photo", "photo"}, {"swab", "ecouvillon"},
                {"dive", "apnee"}, {"climb", "grimpe"}, {"abseil", "rappel"}, {"uvlamp", "lampe"}, {"rockfall", "eboulis"}, {"filter", "filtrage"},
                {"sequencing", "sequencage"}, {"pairing", "appariement"}, {"centrifuge", "centrifugation"}, {"fragments", "fragments"},
                {"spectrum", "spectre"}, {"gel", "gel"}, {"colour", "titrage"}, {"pcr", "pcr"}, {"count", "comptage"}, {"microscope", "microscope"},
                {"chromatography", "chromatographie"}, {"tree", "parente"}, {"titration", "dosage"}, {"bite", "morsure"}, {"thaw", "degel"}};
        for (String[] n : noms) NOMS_COMMANDE.put(n[0], n[1]);
    }

    public static String nomCommande(String jeu) {
        for (Map.Entry<String, String> e : NOMS_COMMANDE.entrySet()) {
            if (e.getValue().equals(jeu)) return e.getKey();
        }
        return jeu;
    }

    public static String nomJeu(String jeu) {
        return "cobblemon_field_investigations.jeu." + jeu;
    }

    public static String nomTrace(String genre, boolean enfoui, int variante) {
        if (!enfoui) return NOMS_TRACES.getOrDefault(genre, "cobblemon_field_investigations.trace.type1");
        String cle = NOMS_ENFOUIS.getOrDefault(genre, "cobblemon_field_investigations.enfoui.taille");
        return variante > 0 && variante < VARIANTES.getOrDefault(genre, 1) ? cle + "_" + variante : cle;
    }

    // Numéro de bloc (propriété « genre » de la trace) de l'objet dégagé du sol.
    public static int blocEnfoui(String genre, int variante) {
        int[] blocs = switch (genre) {
            case "taille" -> new int[] {5, 8};
            case "oeuf" -> new int[] {6, 9, 10};
            default -> new int[] {7, 11, 12};
        };
        return blocs[Math.max(0, Math.min(blocs.length - 1, variante))];
    }

    // Le geste de prélèvement dépend de ce qu'on prélève, et parfois du type d'enquête : en apnée pour une enquête
    // aquatique, en grimpant dans les cimes, en rappel en altitude, à la lampe UV de nuit. Le tirage est fixe pour une trace.
    public static String jeuPrelevement(String genre, boolean enfoui, boolean grotte, String special, java.util.Random h) {
        if (enfoui) return h.nextBoolean() ? "degagement" : "tamisage";
        if (grotte && h.nextInt(3) != 0) return "eboulis";
        if (special != null && h.nextInt(3) != 0) {
            switch (special) {
                case "aquatique": return "apnee";
                case "cimes": return "grimpe";
                case "altitude": return "rappel";
                case "nocturne": return "lampe";
                case "souterraine": return "eboulis";
                default:
            }
        }
        List<String> possibles = JEUX_PRELEVEMENT.getOrDefault(genre, List.of("memoire"));
        return possibles.get(h.nextInt(possibles.size()));
    }

    public static String jeuAnalyse(String genre, String jeu) {
        List<String> possibles = JEUX_ANALYSE.getOrDefault(genre, List.of("sequencage"));
        return possibles.contains(jeu) || "degel".equals(jeu) ? jeu : possibles.get(0);
    }

    private static final Map<String, Integer> COULEURS_TYPES = Map.ofEntries(
            Map.entry("fire", 0xE8543A), Map.entry("water", 0x4A90D9), Map.entry("grass", 0x5DBB5D), Map.entry("electric", 0xF2C93A),
            Map.entry("ice", 0x7FD4E0), Map.entry("fighting", 0xB5503C), Map.entry("poison", 0x9B5FC0), Map.entry("ground", 0xC9A45A),
            Map.entry("flying", 0x9DB7F0), Map.entry("psychic", 0xEE6A9A), Map.entry("bug", 0xA2B833), Map.entry("rock", 0xB0A070),
            Map.entry("ghost", 0x6B5B9A), Map.entry("dragon", 0x6A5AE0), Map.entry("dark", 0x5A4A44), Map.entry("steel", 0xA8A8C0),
            Map.entry("fairy", 0xF0A0D0), Map.entry("normal", 0xB8B8A0), Map.entry("aucun", 0x55606C));
    private static final int[] COULEURS_TAILLES = {0xF4F1DE, 0xF2CC8F, 0xE9A15B, 0xE07A5F, 0x8D4E3A};
    private static final List<String> OEUFS = List.of("MONSTER", "WATER_1", "BUG", "FLYING", "FIELD", "FAIRY", "GRASS", "HUMAN_LIKE",
            "WATER_3", "MINERAL", "AMORPHOUS", "WATER_2", "DITTO", "DRAGON", "UNDISCOVERED");
    private static final int[] COULEURS_OEUFS = {0xC25B56, 0x5B9BD5, 0x9BBB59, 0xA6C8F0, 0xC8A165, 0xF4A6C8, 0x6DBE6D, 0xE0B080,
            0x3F6FB5, 0x9AA0A6, 0x8E7CC3, 0x76B7E8, 0xD98CD9, 0x7461D1, 0x707070};
    private static final List<String> STATS = List.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");
    private static final int[] COULEURS_STATS = {0x6CCB5F, 0xE8543A, 0xE9A15B, 0x9B5FC0, 0x4FC1C5, 0xF2C93A};

    private Genetique() {}

    public static String valeur(String marqueur, Species s) {
        switch (marqueur) {
            case "type1":
                return s.getPrimaryType().getName().toLowerCase();
            case "type2":
                return s.getSecondaryType() == null ? "aucun" : s.getSecondaryType().getName().toLowerCase();
            case "taille": {
                float m = s.getHeight() / 10f;
                return String.valueOf(m < 0.4f ? 0 : m < 1f ? 1 : m < 1.6f ? 2 : m <= 2.5f ? 3 : 4);
            }
            case "oeuf":
                return s.getEggGroups().stream().min(Comparator.comparingInt(Enum::ordinal)).map(Enum::name).orElse("UNDISCOVERED");
            default: {
                String meilleure = "hp";
                int max = -1;
                for (Map.Entry<Stat, Integer> e : s.getBaseStats().entrySet()) {
                    String id = e.getKey().getIdentifier().getPath();
                    if (!STATS.contains(id)) continue;
                    if (e.getValue() > max || (e.getValue() == max && STATS.indexOf(id) < STATS.indexOf(meilleure))) {
                        max = e.getValue();
                        meilleure = id;
                    }
                }
                return meilleure;
            }
        }
    }

    public static MutableComponent libelle(String marqueur, String v) {
        switch (marqueur) {
            case "type1":
            case "type2":
                return v.equals("aucun") ? Component.translatable("cobblemon_field_investigations.aucun") : Component.translatable("cobblemon.type." + v);
            case "taille":
                return Component.translatable("cobblemon_field_investigations.taille." + entier(v, 5));
            case "oeuf":
                return Component.translatable("cobblemon_field_investigations.oeuf." + OEUFS.get(Math.max(0, OEUFS.indexOf(v))).toLowerCase());
            default:
                return Component.translatable("cobblemon.stat." + v + ".name");
        }
    }

    public static int couleur(String marqueur, String v) {
        switch (marqueur) {
            case "type1":
            case "type2":
                return COULEURS_TYPES.getOrDefault(v, 0xB8B8A0);
            case "taille":
                return COULEURS_TAILLES[entier(v, 5)];
            case "oeuf":
                return COULEURS_OEUFS[Math.max(0, OEUFS.indexOf(v))];
            default:
                return COULEURS_STATS[Math.max(0, STATS.indexOf(v))];
        }
    }

    private static int entier(String v, int borne) {
        try {
            return Math.max(0, Math.min(borne - 1, Integer.parseInt(v)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int communs(Species a, Species b) {
        int n = 0;
        for (String m : MARQUEURS) {
            if (valeur(m, a).equals(valeur(m, b))) n++;
        }
        return n;
    }
}
