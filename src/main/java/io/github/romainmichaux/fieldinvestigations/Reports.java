package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

// Opening an investigation: offered leads, investigation kind, target species and suspects.
final class Reports {
    private Reports() {}

    static final List<String> CATEGORIES = List.of("common", "uncommon", "rare", "ultra-rare");

    // Special investigations: each has its own rule and reward (all give the "special" loot, plus "type_<name>" if it exists).
    // Possible anywhere:
    //  - nocturnal: traces, calls and target only at night (double shiny chance);
    //  - skittish: it spots the player from much further away (double special loot);
    //  - exceptional: one more analysis result, all suspects look alike (guaranteed hidden ability);
    //  - restless: it moves twice as often and its lair is further away (double special loot).
    // Tied to the biome of the trace zone:
    //  - aquatic (ocean, river): water species, traces on the bottom, target in the water;
    //  - dig_site (desert, badlands, beach): all remains are buried;
    //  - canopy (forest, jungle, taiga): traces on trunks and tree tops;
    //  - altitude (mountain, hills): traces on cliff faces, much larger search zone;
    //  - frost (snowy biomes): fragile samples, one dose less;
    //  - mist (swamp): calls carry poorly, less precise bearings.
    // The "underground" kind (1.1.0) is no longer offered: searching deep caves for traces was too tedious.
    // An underground investigation already open finishes normally; other investigations now have one trace in a cave.
    static final List<String> GENERIC_KINDS = List.of("nocturnal", "skittish", "exceptional", "restless");

    // Biome kind: applies if the biome has one of these tags, or if its name contains one of these words
    // (for modded biomes that are not tagged).
    private record Habitat(String type, List<String> tags, List<String> words) {}

    private static final List<Habitat> HABITATS = List.of(
            new Habitat("dig_site", List.of("c:is_desert", "minecraft:is_badlands", "minecraft:is_beach", "c:is_sandy"), List.of("desert", "badlands", "beach", "dune")),
            new Habitat("canopy", List.of("minecraft:is_forest", "minecraft:is_jungle", "minecraft:is_taiga"), List.of("forest", "jungle", "taiga", "woods")),
            new Habitat("altitude", List.of("minecraft:is_mountain", "minecraft:is_hill", "c:is_mountain"), List.of("mountain", "peak", "hills", "highland", "cliff")),
            new Habitat("frost", List.of("c:is_snowy", "c:is_icy"), List.of("snow", "frozen", "ice", "glacier", "tundra")),
            new Habitat("mist", List.of("c:is_swamp"), List.of("swamp", "marsh", "bog", "bayou", "mangrove")));

    private static List<String> biomeKinds(Holder<Biome> biome) {
        String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        List<String> types = new ArrayList<>();
        for (Habitat m : HABITATS) {
            boolean ok = false;
            for (String e : m.tags) ok |= biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(e)));
            for (String word : m.words) ok |= name.contains(word);
            if (ok) types.add(m.type);
        }
        return types;
    }

    private record Candidate(String species, int rank, int levelMin, int levelMax) {}

    static void openInvestigation(ServerPlayer player) {
        UUID id = player.getUUID();
        if (Engine.caseFile(player) != null) return;
        String dim = Engine.dimension(player);
        if (!Engine.settings.dimensions.contains(dim)) {
            Messages.announce(player, "dimension", 0xE8543A);
            return;
        }
        if (Engine.state.nextInvestigation.getOrDefault(id, 0L) > System.currentTimeMillis() && !player.isCreative()) {
            Messages.sync(player);
            return;
        }
        if (leads(player) != null) {
            Messages.sync(player); // leads are already waiting on their board
            return;
        }
        ServerLevel level = player.serverLevel();
        PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(player);
        int wanted = Math.max(1, Math.min(3, Engine.settings.leadCount));
        List<CaseFile> leads = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < wanted; i++) {
            // The first lead is always ordinary, so a classic investigation is always on offer.
            boolean special = i > 0 && Engine.RANDOM.nextDouble() < Engine.settings.specialCaseChance;
            CaseFile d = report(player, level, dim, pokedex, taken, special);
            if (d == null && special) d = report(player, level, dim, pokedex, taken, false);
            if (d == null) break;
            taken.add(d.species);
            leads.add(d);
        }
        if (leads.isEmpty()) {
            Messages.announce(player, "no_report", 0xF2C93A);
            return;
        }
        if (wanted == 1) {
            engage(player, leads.get(0));
            return;
        }
        Collections.shuffle(leads, Engine.RANDOM);
        Engine.state.offers.put(id, leads);
        Engine.state.offersEnd.put(id, System.currentTimeMillis() + Math.max(1, Engine.settings.leadValidityMinutes) * 60_000L);
        Engine.save();
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        Messages.sync(player);
    }

    // Leads awaiting a choice: dropped if they expired or the player changed dimension.
    static List<CaseFile> leads(ServerPlayer player) {
        UUID id = player.getUUID();
        List<CaseFile> leads = Engine.state.offers.get(id);
        if (leads == null) return null;
        boolean valid = !leads.isEmpty() && Engine.state.offersEnd.getOrDefault(id, 0L) > System.currentTimeMillis();
        for (CaseFile d : leads) valid &= d.dimension != null && d.dimension.equals(Engine.dimension(player));
        if (valid) return leads;
        Engine.state.offers.remove(id);
        Engine.state.offersEnd.remove(id);
        Engine.save();
        return null;
    }

    static void choose(ServerPlayer player, int index) {
        List<CaseFile> leads = Engine.caseFile(player) == null ? leads(player) : null;
        if (leads == null || index < 0 || index >= leads.size()) {
            Messages.sync(player);
            return;
        }
        engage(player, leads.get(index));
    }

    private static void engage(ServerPlayer player, CaseFile d) {
        UUID id = player.getUUID();
        Engine.state.offers.remove(id);
        Engine.state.offersEnd.remove(id);
        d.leader = id;
        d.forgiven = Engine.rank(player) >= 4 ? 1 : 0; // rank 4 perk: one forgiven fault
        Engine.state.caseFiles.put(id, d);
        Engine.save();
        player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
        Messages.announce(player, d.special == null ? "new_investigation" : "investigation_" + d.special, 0xF2C93A);
        Messages.sync(player);
    }

    // Rolls a lead around the player: a trace zone, a lair, a species of the biome and its suspects.
    // With wantSpecial, the kind depends on the rolled spot: aquatic if the zone falls in water, otherwise a kind
    // possible anywhere or (three times as often) a kind specific to the zone's biome.
    private static CaseFile report(ServerPlayer player, ServerLevel level, String dim, PokedexManager pokedex, Set<String> taken, boolean wantSpecial) {
        for (int attempt = 0; attempt < 60; attempt++) {
            double a1 = Engine.RANDOM.nextDouble() * Math.PI * 2;
            double d1 = Engine.settings.zoneMinDistance + Engine.RANDOM.nextDouble() * (Engine.settings.zoneMaxDistance - Engine.settings.zoneMinDistance);
            double zx = player.getX() + Math.cos(a1) * d1, zz = player.getZ() + Math.sin(a1) * d1;
            boolean water = aquatic(level, zx, zz);
            if (water && !wantSpecial) continue;
            String special = null;
            if (water) {
                special = "aquatic";
            } else if (wantSpecial) {
                List<String> possible = new ArrayList<>(GENERIC_KINDS);
                for (String t : biomeKinds(level.getBiome(BlockPos.containing(zx, 100, zz)))) possible.addAll(List.of(t, t, t));
                special = possible.get(Engine.RANDOM.nextInt(possible.size()));
            }
            double a2 = Engine.RANDOM.nextDouble() * Math.PI * 2;
            double d2 = (Engine.settings.lairMinDistance + Engine.RANDOM.nextDouble() * (Engine.settings.lairMaxDistance - Engine.settings.lairMinDistance))
                    * ("restless".equals(special) ? 1.5 : 1);
            double tx = zx + Math.cos(a2) * d2, tz = zz + Math.sin(a2) * d2;
            if (aquatic(level, tx, tz) != water) continue; // the lair is in the same medium as the traces
            Holder<Biome> biome = level.getBiome(BlockPos.containing(tx, 100, tz));
            boolean cave = "underground".equals(special);
            List<Candidate> basin = basin(biome, level.dimension().location(), water, cave);
            basin.removeIf(c -> taken.contains(c.species)); // never two leads for the same species
            if (basin.size() < 3) continue;
            double total = 0;
            double[] weight = new double[basin.size()];
            for (int i = 0; i < basin.size(); i++) {
                Candidate c = basin.get(i);
                weight[i] = Engine.settings.rarityWeights.getOrDefault(CATEGORIES.get(c.rank), 1.0) * (owns(pokedex, c.species) ? 1.0 : Engine.settings.newSpeciesWeight);
                total += weight[i];
            }
            double roll = Engine.RANDOM.nextDouble() * total;
            Candidate target = basin.get(basin.size() - 1);
            for (int i = 0; i < basin.size(); i++) {
                roll -= weight[i];
                if (roll <= 0) {
                    target = basin.get(i);
                    break;
                }
            }
            CaseFile d = new CaseFile();
            d.id = Engine.RANDOM.nextLong();
            d.dimension = dim;
            d.special = special;
            d.species = target.species;
            d.category = CATEGORIES.get(target.rank);
            d.newSpecies = !owns(pokedex, target.species);
            d.seed = Engine.RANDOM.nextLong();
            d.level = target.levelMin + Engine.RANDOM.nextInt(Math.max(1, target.levelMax - target.levelMin + 1));
            d.zoneX = zx;
            d.zoneZ = zz;
            d.lairX = tx;
            d.lairZ = tz;
            for (String m : Genetics.MARKERS) {
                Trace t = new Trace();
                t.kind = m;
                for (int k = 0; k < 20; k++) {
                    double a = Engine.RANDOM.nextDouble() * Math.PI * 2, r = 6 + Engine.RANDOM.nextDouble() * (Engine.radius(d) - 6);
                    t.x = Math.floor(zx + Math.cos(a) * r) + 0.5;
                    t.z = Math.floor(zz + Math.sin(a) * r) + 0.5;
                    boolean free = true;
                    for (Trace other : d.traces) free &= Math.hypot(other.x - t.x, other.z - t.z) >= 12;
                    if (free) break;
                }
                d.traces.add(t);
            }
            // Only one clue is buried per investigation (brushing stays an exception), and what is dug up varies.
            List<Trace> buriable = new ArrayList<>();
            for (Trace t : d.traces) {
                if (Genetics.BURIED_NAMES.containsKey(t.kind)) buriable.add(t);
            }
            Collections.shuffle(buriable, Engine.RANDOM);
            // Dig site: everything is buried. Underwater: nothing is (no brushing at the bottom of a lake).
            for (int i = 0; i < buriable.size(); i++) {
                Trace t = buriable.get(i);
                t.buried = !water && !cave && (i == 0 || "dig_site".equals(special));
                t.variant = Engine.RANDOM.nextInt(Genetics.VARIANTS.getOrDefault(t.kind, 1));
            }
            // One unburied trace goes into a cave (except underwater, and on dig sites where everything is buried).
            List<Trace> onSurface = new ArrayList<>();
            for (Trace t : d.traces) {
                if (!t.buried) onSurface.add(t);
            }
            if (!water && !cave && !onSurface.isEmpty()) onSurface.get(Engine.RANDOM.nextInt(onSurface.size())).cave = true;
            d.suspects = suspects(target.species, basin, "exceptional".equals(special), water, cave);
            return d;
        }
        return null;
    }

    static boolean aquatic(ServerLevel level, double x, double z) {
        Holder<Biome> biome = level.getBiome(BlockPos.containing(x, 100, z));
        String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        return name.contains("ocean") || name.contains("river") || biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
    }

    static boolean owns(PokedexManager pokedex, String species) {
        Species s = PokemonSpecies.getByName(species);
        return s != null && pokedex.getHighestKnowledgeForSpecies(s.getResourceIdentifier()) == PokedexEntryProgress.OWNED;
    }

    // Surface species Cobblemon spawns in this biome (null = anywhere): land-based, or living in water.
    // cave: only species Cobblemon spawns underground (out of sight of the sky, or deep down).
    private static List<Candidate> basin(Holder<Biome> biome, ResourceLocation dimension, boolean water, boolean cave) {
        Map<String, Candidate> result = new HashMap<>();
        for (SpawnDetail detail : CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails()) {
            if (!(detail instanceof PokemonSpawnDetail pokemon)) continue;
            int rank = CATEGORIES.indexOf(detail.getBucket());
            String position = detail.getSpawnablePositionType().getName();
            if (rank < 0 || !(water ? "submerged".equals(position) || "surface".equals(position) : "grounded".equals(position))) continue;
            if (pokemon.getPokemon().getForm() != null || (biome != null && !fits(detail, biome, dimension, cave))) continue;
            if (biome == null && cave && !underground(detail)) continue;
            String name = pokemon.getPokemon().getSpecies();
            if (name == null) continue;
            name = name.substring(name.indexOf(':') + 1).toLowerCase();
            Species species = PokemonSpecies.getByName(name);
            if (species == null || !species.getImplemented()) continue;
            if (species.getLabels().stream().anyMatch(Engine.settings.excludedLabels::contains)) continue;
            int min = 10, max = 30;
            if (pokemon.getLevelRange() != null) {
                min = pokemon.getLevelRange().getFirst();
                max = pokemon.getLevelRange().getLast();
            }
            Candidate old = result.get(name);
            if (old == null || rank < old.rank) result.put(name, new Candidate(name, rank, min, Math.max(min, max)));
        }
        return new ArrayList<>(result.values());
    }

    private static boolean underground(SpawningCondition<?> c) {
        return Boolean.FALSE.equals(c.getCanSeeSky()) || (c.getMaxY() != null && c.getMaxY() < 62);
    }

    private static boolean underground(SpawnDetail detail) {
        for (SpawningCondition<?> c : detail.getConditions()) {
            if (underground(c)) return true;
        }
        return false;
    }

    private static boolean fits(SpawnDetail detail, Holder<Biome> biome, ResourceLocation dimension, boolean cave) {
        boolean ok = false;
        for (SpawningCondition<?> c : detail.getConditions()) {
            Set<RegistryLikeCondition<Biome>> biomes = c.getBiomes();
            if (biomes == null || biomes.isEmpty()) continue;
            if (underground(c) != cave) continue; // surface investigations ignore underground spawns, and vice versa
            List<ResourceLocation> dims = c.getDimensions();
            if (dims != null && !dims.isEmpty() && !dims.contains(dimension)) continue;
            for (RegistryLikeCondition<Biome> b : biomes) {
                if (b.fits(biome)) {
                    ok = true;
                    break;
                }
            }
            if (ok) break;
        }
        if (!ok) return false;
        for (SpawningCondition<?> c : detail.getAnticonditions()) {
            Set<RegistryLikeCondition<Biome>> biomes = c.getBiomes();
            if (biomes == null) continue;
            for (RegistryLikeCondition<Biome> b : biomes) {
                if (b.fits(biome)) return false;
            }
        }
        return true;
    }

    // The target + decoys with a close profile (never identical: the 5 markers always point to a single species).
    // closeOnly (exceptional investigation): all decoys are taken from the closest profiles.
    private static List<String> suspects(String target, List<Candidate> local, boolean closeOnly, boolean water, boolean cave) {
        Species s = PokemonSpecies.getByName(target);
        Set<String> nearby = new HashSet<>();
        for (Candidate c : local) nearby.add(c.species);
        Map<String, Double> scores = new HashMap<>();
        for (Candidate c : basin(null, null, water, cave)) {
            if (c.species.equals(target)) continue;
            Species other = PokemonSpecies.getByName(c.species);
            int common = Genetics.common(s, other);
            if (common >= Genetics.MARKERS.size()) continue;
            scores.put(c.species, common + (nearby.contains(c.species) ? 0.6 : 0) + Engine.RANDOM.nextDouble() * 1.2);
        }
        List<String> ranked = new ArrayList<>(scores.keySet());
        ranked.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        int closeCount = closeOnly ? Math.max(0, Engine.settings.suspectCount - 1) : Math.max(0, Engine.settings.suspectCount - 1) * 2 / 3;
        List<String> list = new ArrayList<>(ranked.subList(0, Math.min(closeCount, ranked.size())));
        List<String> rest = new ArrayList<>(ranked.subList(list.size(), ranked.size()));
        Collections.shuffle(rest, Engine.RANDOM);
        while (list.size() < Engine.settings.suspectCount - 1 && !rest.isEmpty()) list.add(rest.remove(0));
        list.add(target);
        Collections.shuffle(list, Engine.RANDOM);
        return list;
    }
}
