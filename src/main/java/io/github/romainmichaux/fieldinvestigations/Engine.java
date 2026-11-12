package io.github.romainmichaux.fieldinvestigations;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

// État partagé des enquêtes (réglages, dossiers, profils) et règles communes : étoiles, rang, paramètres par type d'enquête.
public final class Engine {
    // Expérience requise pour chaque rang (0 = stagiaire ... 6 = légende). Chaque rang débloque une amélioration du kit :
    //  1 radar affiné (les traces scintillent de plus loin), 2 fiole de secours (le premier prélèvement de chaque enquête
    //  ne consomme pas de fiole), 3 scan rapide (2 s au lieu de 3), 4 sang-froid (un raté de plus pardonné par enquête),
    //  5 oreille fine (relevés de cri plus précis), 6 réseau d'informateurs (attente entre deux enquêtes divisée par deux).
    static final int[] THRESHOLDS = {0, 60, 160, 320, 560, 900, 1400};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final Random RANDOM = new Random();

    static Settings settings = new Settings();

    static State state = new State();

    private static Path stateFile;

    static final Map<UUID, PokemonEntity> ENTITIES = new HashMap<>();

    static final Map<UUID, Boolean> ANALYSES_LIST = new HashMap<>();

    static final Map<UUID, BlockPos> SAMPLINGS = new HashMap<>();

    static final Map<UUID, Boolean> FILTERINGS = new HashMap<>();

    // Avancée du scan de chaque joueur : [demi-secondes passées sur la cible, « ce n'est pas la cible » encore affiché].
    static final Map<UUID, int[]> SCANS = new HashMap<>();

    private Engine() {}

    static void load() {
        Path caseFile = FabricLoader.getInstance().getConfigDir();
        settings = read(caseFile.resolve("cobblemon_field_investigations.json"), Settings.class, new Settings());
        write(caseFile.resolve("cobblemon_field_investigations.json"), settings);
    }

    // L'état appartient au monde, pas à l'instance : en solo, chaque partie a ses propres enquêtes et ses propres rangs.
    // Jusqu'en 1.1.0 il était rangé dans config/ ; un serveur dédié n'a qu'un monde, il reprend donc ce fichier une fois.
    // En solo, impossible de savoir à quel monde l'ancien fichier appartenait : chaque monde repart de zéro.
    static void loadWorld(MinecraftServer server1) {
        state = new State();
        stateFile = server1.getWorldPath(LevelResource.ROOT).resolve("cobblemon_field_investigations_state.json");
        Path old = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_state.json");
        if (!Files.exists(stateFile) && server1.isDedicatedServer() && Files.exists(old)) {
            try {
                Files.copy(old, stateFile);
                Files.move(old, old.resolveSibling("cobblemon_field_investigations_state.json.migrated"));
            } catch (Exception e) {
                System.err.println("[Field Investigations] Reprise impossible de " + old + " : " + e);
            }
        }
        State read1 = read(stateFile, State.class, null);
        if (read1 != null) {
            state = read1;
            if (state.caseFiles == null) state.caseFiles = new HashMap<>();
            if (state.nextInvestigation == null) state.nextInvestigation = new HashMap<>();
            if (state.succeeded == null) state.succeeded = new HashMap<>();
            if (state.offers == null) state.offers = new HashMap<>();
            if (state.offersEnd == null) state.offersEnd = new HashMap<>();
            if (state.profiles == null) state.profiles = new HashMap<>();
            for (Map.Entry<UUID, CaseFile> en : state.caseFiles.entrySet()) {
                if (en.getValue().leader == null) en.getValue().leader = en.getKey();
                if (en.getValue().team == null) en.getValue().team = new ArrayList<>();
            }
        }
    }

    static void stop() {
        save();
        state = new State();
        stateFile = null;
        ENTITIES.clear();
        ANALYSES_LIST.clear();
        SAMPLINGS.clear();
        FILTERINGS.clear();
        SCANS.clear();
        Team.INVITATIONS.clear();
        Team.INVITATION_EXPIRY.clear();
    }

    // Le dossier sur lequel travaille ce joueur : le sien, ou celui de l'équipe qu'il a rejointe.
    static CaseFile caseFile(ServerPlayer player) {
        UUID id = player.getUUID();
        CaseFile d = state.caseFiles.get(id);
        if (d != null) return d;
        for (CaseFile other : state.caseFiles.values()) {
            if (other.team != null && other.team.contains(id)) return other;
        }
        return null;
    }

    static List<UUID> members(CaseFile d) {
        List<UUID> all = new ArrayList<>();
        all.add(d.leader);
        if (d.team != null) all.addAll(d.team);
        return all;
    }

    static List<ServerPlayer> online(CaseFile d, net.minecraft.server.MinecraftServer server1) {
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID u : members(d)) {
            ServerPlayer p = server1.getPlayerList().getPlayer(u);
            if (p != null) present.add(p);
        }
        return present;
    }

    static List<ServerPlayer> teammates(ServerPlayer player) {
        CaseFile d = caseFile(player);
        if (d == null || d.team == null || d.team.isEmpty()) return List.of();
        List<ServerPlayer> others = online(d, player.server);
        others.remove(player);
        return others;
    }

    static String name(net.minecraft.server.MinecraftServer server1, UUID id) {
        ServerPlayer p = server1.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        var hidden = server1.getProfileCache();
        return hidden == null ? "?" : hidden.get(id).map(com.mojang.authlib.GameProfile::getName).orElse("?");
    }

    static void resetScan(CaseFile d) {
        for (UUID u : members(d)) SCANS.remove(u);
    }

    // Le joueur barre (ou rétablit) un suspect sur son tableau, d'après ce qu'il déduit des marqueurs analysés.
    static void ruleOut(ServerPlayer player, String species) {
        CaseFile d = caseFile(player);
        if (d == null || !"SAMPLING".equals(d.phase) || !d.suspects.contains(species)) return;
        if (d.ruledOut == null) d.ruledOut = new ArrayList<>();
        if (!d.ruledOut.remove(species)) d.ruledOut.add(species);
        save();
        Messages.sync(player);
    }

    // Qualité de l'enquête, de 1 à 5 étoiles. On part de 5 ; un raté de mini-jeu coûte une demi-étoile (le premier est
    // donc gratuit), une fuite de la cible une étoile, un mauvais pronostic deux étoiles.
    static int stars(CaseFile d, boolean withGuess) {
        int halves = 10 - Math.max(0, d.faults - d.forgiven) - 2 * d.leaks;
        if (withGuess && d.guess != null && !d.species.equals(d.guess)) halves -= 4;
        return Math.max(1, Math.min(5, (halves + 1) / 2));
    }

    static int stars(CaseFile d) {
        return stars(d, true);
    }

    // IV parfaits accordés au Pokémon : aucun à 1 étoile, 4 au mieux à 5 étoiles.
    static int perfectIvs(int stars) {
        return Math.max(0, Math.min(4, stars - 1));
    }

    // Le profil d'un joueur. Ceux qui avaient déjà résolu des enquêtes avant l'arrivée des rangs partent avec 30 points par dossier.
    static Profile profile(UUID id) {
        Profile p = state.profiles.get(id);
        if (p == null) {
            p = new Profile();
            p.xp = state.succeeded.getOrDefault(id, 0) * 30;
            state.profiles.put(id, p);
        }
        if (p.history == null) p.history = new ArrayList<>();
        if (p.gamesSeen == null) p.gamesSeen = new HashSet<>();
        if (p.partners == null) p.partners = new HashMap<>();
        return p;
    }

    static int rank(int xp) {
        int r = 0;
        while (r + 1 < THRESHOLDS.length && xp >= THRESHOLDS[r + 1]) r++;
        return r;
    }

    static int rank(ServerPlayer player) {
        return rank(profile(player.getUUID()).xp);
    }

    // Vrai la première fois que ce joueur ouvre ce mini-jeu : l'écran lui en explique alors les règles avant de démarrer.
    static boolean firstTime(ServerPlayer player, String game) {
        boolean newOne = profile(player.getUUID()).gamesSeen.add(game);
        if (newOne) save();
        return newOne;
    }

    // Durée du scan, en demi-secondes : 6 d'ordinaire, 4 avec le scan rapide (rang 3).
    static int scanDuration(ServerPlayer player) {
        return rank(player) >= 3 ? 4 : 6;
    }

    static boolean is(CaseFile d, String special) {
        return special.equals(d.special);
    }

    static int required(CaseFile d) {
        return Math.min(Genetics.MARKERS.size(), settings.pinsToStartHunt + (is(d, "exceptional") ? 1 : 0));
    }

    static int alert(CaseFile d) {
        return is(d, "skittish") ? (int) Math.round(settings.alertRadius * 1.6) : settings.alertRadius;
    }

    static int radius(CaseFile d) {
        return is(d, "altitude") ? (int) Math.round(settings.zoneRadius * 1.5) : settings.zoneRadius;
    }

    // Délai avant que la cible change d'endroit pendant la traque (0 = jamais).
    static long nextMove(CaseFile d) {
        if (settings.minutesAvantDeplacement <= 0) return 0;
        return System.currentTimeMillis() + settings.minutesAvantDeplacement * 60_000L / (is(d, "restless") ? 2 : 1);
    }

    // Une cible nocturne ne laisse rien voir ni entendre en plein jour.
    static boolean asleep(CaseFile d, ServerLevel level1) {
        return is(d, "nocturnal") && !level1.isNight();
    }

    // Débloque un progrès du mod (data/cobblemon_field_investigations/advancement/<nom>.json, critère « fait »).
    static void progress1(ServerPlayer player, String name) {
        try {
            var p = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", name));
            if (p != null) player.getAdvancements().award(p, "done");
        } catch (Exception e) {
            System.err.println("[Field Investigations] Progrès impossible à accorder : " + name + " : " + e);
        }
    }

    static void fault(ServerPlayer player, CaseFile d) {
        d.faults++;
        save();
    }

    // Ouvre un mini-jeu à blanc : rien n'est prélevé, analysé ni compté, quelle que soit l'issue.
    static boolean tryIt(ServerPlayer player, String game, int variant) {
        boolean field = Genetics.FIELD_GAMES.contains(game);
        if (!field && !Genetics.LAB_GAMES.contains(game)) return false;
        String kind = "type1";
        for (Map.Entry<String, List<String>> e : (field ? Genetics.SAMPLING_GAMES : Genetics.ANALYSIS_GAMES).entrySet()) {
            if (e.getValue().contains(game)) kind = e.getKey();
        }
        boolean buried = game.equals("brush") || game.equals("sieving");
        if (buried) kind = List.of("size", "egg", "stat").get(RANDOM.nextInt(3));
        Packet p = Packet.from("open");
        p.data().putString("screen", "attempt");
        p.data().putBoolean("first", firstTime(player, game));
        p.data().putString("game", game);
        p.data().putString("kind", kind);
        p.data().putBoolean("field", field);
        p.data().putBoolean("buried", buried);
        p.data().putInt("variant", variant >= 0 ? variant : RANDOM.nextInt(Genetics.VARIANTS.getOrDefault(kind, 1)));
        p.data().putLong("seed", RANDOM.nextLong());
        Messages.send(player, p);
        return true;
    }

    static void reset(ServerPlayer player) {
        CaseFile d = caseFile(player);
        if (d != null && player.getUUID().equals(d.leader)) CaseClosing.closeCase(player, d);
        else if (d != null) d.team.remove(player.getUUID());
        state.nextInvestigation.remove(player.getUUID());
        state.offers.remove(player.getUUID());
        state.offersEnd.remove(player.getUUID());
        save();
        Messages.sync(player);
    }

    static String reveal(ServerPlayer player) {
        CaseFile d = caseFile(player);
        if (d == null) return "No investigation in progress.";
        StringBuilder sb = new StringBuilder("[test] " + d.species + (d.special == null ? "" : " [" + d.special + "]") + " lvl " + d.level2 + " · phase " + d.phase + " · stars " + stars(d) + " (faults " + d.faults
                + ", flees " + d.leaks + ") · zone X " + Math.round(d.zoneX) + " Z " + Math.round(d.zoneZ) + " · lair X " + Math.round(d.lairX)
                + " Z " + Math.round(d.lairZ) + " · traces:");
        for (Trace t : d.traces) sb.append(" ").append(t.kind).append("(").append(Math.round(t.x)).append(", ").append(Math.round(t.z)).append(t.harvest ? ", ok)" : t.lost ? ", lost)" : t.buried ? (t.uncovered ? ", uncovered)" : ", buried)") : ")");
        return sb.toString();
    }

    static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    static double distance(ServerPlayer player, double x, double z) {
        return Math.hypot(player.getX() - x, player.getZ() - z);
    }

    static Component speciesName(String species) {
        Species s = PokemonSpecies.getByName(species);
        return s == null ? Component.literal(species) : s.getTranslatedName();
    }

    static void save() {
        if (stateFile != null) write(stateFile, state);
    }

    private static <T> T read(Path file, java.lang.reflect.Type type, T fallback) {
        if (!Files.exists(file)) return fallback;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(r, type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            System.err.println("[Field Investigations] Lecture impossible de " + file + " : " + e);
            return fallback;
        }
    }

    private static void write(Path file, Object value) {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(value, w);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Écriture impossible de " + file + " : " + e);
        }
    }
}
