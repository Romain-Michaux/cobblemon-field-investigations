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

// Shared investigation state (settings, case files, profiles) and common rules: stars, rank, per-type parameters.
public final class Engine {
    // XP required for each rank (0 = trainee ... 6 = legend). Each rank unlocks a kit upgrade:
    //  1 sharper radar (traces sparkle from further away), 2 spare vial (the first sample of each case does not use up a
    //  vial), 3 quick scan (2 s instead of 3), 4 composure (one more forgiven fault per case), 5 keen ear (more accurate cry
    //  bearings), 6 informant network (cooldown between cases halved).
    static final int[] THRESHOLDS = {0, 60, 160, 320, 560, 900, 1400};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final Random RANDOM = new Random();

    static Settings settings = new Settings();

    static State state = new State();

    private static Path stateFile;

    static final Map<UUID, PokemonEntity> ENTITIES = new HashMap<>();

    static final Map<UUID, Boolean> PENDING_ANALYSES = new HashMap<>();

    static final Map<UUID, BlockPos> PENDING_SAMPLINGS = new HashMap<>();

    static final Map<UUID, Boolean> PENDING_FILTERS = new HashMap<>();

    // Scan progress per player: [half-seconds spent on the target, "not the target" message still showing].
    static final Map<UUID, int[]> SCANS = new HashMap<>();

    private Engine() {}

    static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        settings = read(configDir.resolve("cobblemon_field_investigations.json"), Settings.class, new Settings());
        write(configDir.resolve("cobblemon_field_investigations.json"), settings);
    }

    // State belongs to the world, not the instance: in singleplayer each save has its own cases and ranks.
    // Up to 1.1.0 it lived in config/; a dedicated server has a single world, so it takes over that file once.
    // In singleplayer there is no way to tell which world the old file belonged to: every world starts fresh.
    static void loadWorld(MinecraftServer server) {
        state = new State();
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblemon_field_investigations_state.json");
        Path old = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_state.json");
        if (!Files.exists(stateFile) && server.isDedicatedServer() && Files.exists(old)) {
            try {
                Files.copy(old, stateFile);
                Files.move(old, old.resolveSibling("cobblemon_field_investigations_state.json.migrated"));
            } catch (Exception e) {
                System.err.println("[Field Investigations] Could not migrate " + old + ": " + e);
            }
        }
        State loaded = read(stateFile, State.class, null);
        if (loaded != null) {
            state = loaded;
            if (state.caseFiles == null) state.caseFiles = new HashMap<>();
            if (state.nextInvestigation == null) state.nextInvestigation = new HashMap<>();
            if (state.succeeded == null) state.succeeded = new HashMap<>();
            if (state.offers == null) state.offers = new HashMap<>();
            if (state.offersEnd == null) state.offersEnd = new HashMap<>();
            if (state.profiles == null) state.profiles = new HashMap<>();
            for (Map.Entry<UUID, CaseFile> entry : state.caseFiles.entrySet()) {
                if (entry.getValue().leader == null) entry.getValue().leader = entry.getKey();
                if (entry.getValue().team == null) entry.getValue().team = new ArrayList<>();
            }
        }
    }

    static void stop() {
        save();
        state = new State();
        stateFile = null;
        ENTITIES.clear();
        PENDING_ANALYSES.clear();
        PENDING_SAMPLINGS.clear();
        PENDING_FILTERS.clear();
        SCANS.clear();
        Team.INVITATIONS.clear();
        Team.INVITATION_EXPIRY.clear();
    }

    // The case file this player works on: their own, or that of the team they joined.
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

    static List<ServerPlayer> online(CaseFile d, net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID u : members(d)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
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

    static String name(net.minecraft.server.MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        var profileCache = server.getProfileCache();
        return profileCache == null ? "?" : profileCache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse("?");
    }

    static void resetScan(CaseFile d) {
        for (UUID u : members(d)) SCANS.remove(u);
    }

    // The player crosses out (or restores) a suspect on their board, based on what they deduce from analysed markers.
    static void ruleOut(ServerPlayer player, String species) {
        CaseFile d = caseFile(player);
        if (d == null || !"SAMPLING".equals(d.phase) || !d.suspects.contains(species)) return;
        if (d.ruledOut == null) d.ruledOut = new ArrayList<>();
        if (!d.ruledOut.remove(species)) d.ruledOut.add(species);
        save();
        Messages.sync(player);
    }

    // Case quality, 1 to 5 stars. Starts at 5; a mini-game fault costs half a star (so the first one is free),
    // the target fleeing one star, a wrong guess two stars.
    static int stars(CaseFile d, boolean withGuess) {
        int halves = 10 - Math.max(0, d.faults - d.forgiven) - 2 * d.flees;
        if (withGuess && d.guess != null && !d.species.equals(d.guess)) halves -= 4;
        return Math.max(1, Math.min(5, (halves + 1) / 2));
    }

    static int stars(CaseFile d) {
        return stars(d, true);
    }

    // Perfect IVs given to the Pokémon: none at 1 star, up to 4 at 5 stars.
    static int perfectIvs(int stars) {
        return Math.max(0, Math.min(4, stars - 1));
    }

    // Players who had solved cases before ranks existed start with 30 XP per solved case.
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

    // True the first time this player opens this mini-game: the screen then explains its rules before starting.
    static boolean firstTime(ServerPlayer player, String game) {
        boolean isNew = profile(player.getUUID()).gamesSeen.add(game);
        if (isNew) save();
        return isNew;
    }

    // Scan duration in half-seconds: 6 normally, 4 with quick scan (rank 3).
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

    // When the target relocates during the hunt (0 = never).
    static long nextMove(CaseFile d) {
        if (settings.minutesBeforeTargetMoves <= 0) return 0;
        return System.currentTimeMillis() + settings.minutesBeforeTargetMoves * 60_000L / (is(d, "restless") ? 2 : 1);
    }

    // A nocturnal target cannot be seen or heard in daylight.
    static boolean asleep(CaseFile d, ServerLevel level) {
        return is(d, "nocturnal") && !level.isNight();
    }

    // Grants one of the mod's advancements (data/cobblemon_field_investigations/advancement/<name>.json, criterion "done").
    static void grantAdvancement(ServerPlayer player, String name) {
        try {
            var p = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", name));
            if (p != null) player.getAdvancements().award(p, "done");
        } catch (Exception e) {
            System.err.println("[Field Investigations] Could not grant advancement " + name + ": " + e);
        }
    }

    static void fault(ServerPlayer player, CaseFile d) {
        d.faults++;
        save();
    }

    // Opens a mini-game as a dry run: nothing is sampled, analysed or counted, whatever the outcome.
    static boolean tryGame(ServerPlayer player, String game, int variant) {
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
        StringBuilder sb = new StringBuilder("[test] " + d.species + (d.special == null ? "" : " [" + d.special + "]") + " lvl " + d.level + " · phase " + d.phase + " · stars " + stars(d) + " (faults " + d.faults
                + ", flees " + d.flees + ") · zone X " + Math.round(d.zoneX) + " Z " + Math.round(d.zoneZ) + " · lair X " + Math.round(d.lairX)
                + " Z " + Math.round(d.lairZ) + " · traces:");
        for (Trace t : d.traces) sb.append(" ").append(t.kind).append("(").append(Math.round(t.x)).append(", ").append(Math.round(t.z)).append(t.sampled ? ", ok)" : t.lost ? ", lost)" : t.buried ? (t.uncovered ? ", uncovered)" : ", buried)") : ")");
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
            System.err.println("[Field Investigations] Could not read " + file + ": " + e);
            return fallback;
        }
    }

    private static void write(Path file, Object value) {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(value, w);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Could not write " + file + ": " + e);
        }
    }
}
