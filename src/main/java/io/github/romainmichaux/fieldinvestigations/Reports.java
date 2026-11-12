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

// Ouverture d'une enquête : pistes proposées, type d'enquête, espèce cible et suspects.
final class Reports {
    private Reports() {}

    static final List<String> CATEGORIES = List.of("common", "uncommon", "rare", "ultra-rare");

    // Enquêtes spéciales : chacune a sa règle et sa récompense (toutes donnent le butin « speciale », et « type_<nom> » s'il existe).
    // Possibles partout :
    //  - nocturne : traces, cris et cible seulement la nuit (chance de chromatique doublée) ;
    //  - craintive : elle repère le joueur de bien plus loin (butin spécial doublé) ;
    //  - exceptionnel : un résultat d'analyse de plus, suspects tous ressemblants (talent caché garanti) ;
    //  - migratrice : elle se déplace deux fois plus souvent et sa tanière est plus loin (butin spécial doublé).
    // Liées au biome de la zone de traces :
    //  - aquatique (océan, rivière) : espèces d'eau, traces au fond de l'eau, cible dans l'eau ;
    //  - fouille (désert, badlands, plage) : tous les restes sont enfouis ;
    //  - cimes (forêt, jungle, taïga) : traces sur les troncs et au sommet des arbres ;
    //  - altitude (montagne, collines) : traces sur les parois, zone de recherche bien plus vaste ;
    //  - givre (biomes enneigés) : échantillons fragiles, une dose de moins ;
    //  - brume (marais) : les cris portent mal, relevés moins précis.
    // Le type « souterraine » (1.1.0) n'est plus proposé : chercher des traces dans les grottes profondes était trop pénible.
    // Une enquête souterraine déjà ouverte se termine normalement ; les autres enquêtes ont désormais une trace en grotte.
    static final List<String> GENERIC_KINDS = List.of("nocturnal", "skittish", "exceptional", "restless");

    // Type lié au biome : il s'applique si le biome porte l'une de ces étiquettes, ou si son nom contient l'un de ces mots
    // (pour les biomes de mods qui ne sont pas étiquetés).
    private record Habitat(String type, List<String> labels, List<String> words) {}

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
            for (String e : m.labels) ok |= biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(e)));
            for (String word1 : m.words) ok |= name.contains(word1);
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
            Messages.sync(player); // des signalements l'attendent déjà sur son tableau
            return;
        }
        ServerLevel level1 = player.serverLevel();
        PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(player);
        int wanted = Math.max(1, Math.min(3, Engine.settings.leadCount));
        List<CaseFile> leads = new ArrayList<>();
        Set<String> takes = new HashSet<>();
        for (int i = 0; i < wanted; i++) {
            // Le premier signalement est toujours ordinaire : il y a toujours une enquête classique au choix.
            boolean special = i > 0 && Engine.RANDOM.nextDouble() < Engine.settings.chanceSpecialCase;
            CaseFile d = report(player, level1, dim, pokedex, takes, special);
            if (d == null && special) d = report(player, level1, dim, pokedex, takes, false);
            if (d == null) break;
            takes.add(d.species);
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
        Engine.state.offersEnd.put(id, System.currentTimeMillis() + Math.max(1, Engine.settings.minutesValiditePistes) * 60_000L);
        Engine.save();
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        Messages.sync(player);
    }

    // Signalements en attente de choix : ils tombent s'ils ont expiré ou si le joueur a changé de dimension.
    static List<CaseFile> leads(ServerPlayer player) {
        UUID id = player.getUUID();
        List<CaseFile> leads = Engine.state.offers.get(id);
        if (leads == null) return null;
        boolean validOnes = !leads.isEmpty() && Engine.state.offersEnd.getOrDefault(id, 0L) > System.currentTimeMillis();
        for (CaseFile d : leads) validOnes &= d.level1 != null && d.level1.equals(Engine.dimension(player));
        if (validOnes) return leads;
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
        d.forgiven = Engine.rank(player) >= 4 ? 1 : 0; // sang-froid
        Engine.state.caseFiles.put(id, d);
        Engine.save();
        player.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
        Messages.announce(player, d.special == null ? "new_investigation" : "investigation_" + d.special, 0xF2C93A);
        Messages.sync(player);
    }

    // Tire un signalement autour du joueur : une zone de traces, une tanière, une espèce du biome et ses suspects.
    // Avec « specialVoulu », le type dépend de l'endroit tiré : aquatique si la zone tombe dans l'eau, sinon un type
    // possible partout ou (trois fois plus souvent) un type propre au biome de la zone.
    private static CaseFile report(ServerPlayer player, ServerLevel level1, String dim, PokedexManager pokedex, Set<String> takes, boolean wantedSpecial) {
        for (int attempt = 0; attempt < 60; attempt++) {
            double a1 = Engine.RANDOM.nextDouble() * Math.PI * 2;
            double d1 = Engine.settings.zoneMinDistance + Engine.RANDOM.nextDouble() * (Engine.settings.zoneMaxDistance - Engine.settings.zoneMinDistance);
            double zx = player.getX() + Math.cos(a1) * d1, zz = player.getZ() + Math.sin(a1) * d1;
            boolean water = aquatic(level1, zx, zz);
            if (water && !wantedSpecial) continue;
            String special = null;
            if (water) {
                special = "aquatic";
            } else if (wantedSpecial) {
                List<String> possible = new ArrayList<>(GENERIC_KINDS);
                for (String t : biomeKinds(level1.getBiome(BlockPos.containing(zx, 100, zz)))) possible.addAll(List.of(t, t, t));
                special = possible.get(Engine.RANDOM.nextInt(possible.size()));
            }
            double a2 = Engine.RANDOM.nextDouble() * Math.PI * 2;
            double d2 = (Engine.settings.lairMinDistance + Engine.RANDOM.nextDouble() * (Engine.settings.lairMaxDistance - Engine.settings.lairMinDistance))
                    * ("restless".equals(special) ? 1.5 : 1);
            double tx = zx + Math.cos(a2) * d2, tz = zz + Math.sin(a2) * d2;
            if (aquatic(level1, tx, tz) != water) continue; // la tanière est dans le même milieu que les traces
            Holder<Biome> biome = level1.getBiome(BlockPos.containing(tx, 100, tz));
            boolean cave = "underground".equals(special);
            List<Candidate> basin = basin(biome, level1.dimension().location(), water, cave);
            basin.removeIf(c -> takes.contains(c.species)); // jamais deux signalements pour la même espèce
            if (basin.size() < 3) continue;
            double total = 0;
            double[] weight = new double[basin.size()];
            for (int i = 0; i < basin.size(); i++) {
                Candidate c = basin.get(i);
                weight[i] = Engine.settings.rarityWeights.getOrDefault(CATEGORIES.get(c.rank), 1.0) * (owns(pokedex, c.species) ? 1.0 : Engine.settings.bonusNewSpecies);
                total += weight[i];
            }
            double draw1 = Engine.RANDOM.nextDouble() * total;
            Candidate target = basin.get(basin.size() - 1);
            for (int i = 0; i < basin.size(); i++) {
                draw1 -= weight[i];
                if (draw1 <= 0) {
                    target = basin.get(i);
                    break;
                }
            }
            CaseFile d = new CaseFile();
            d.id = Engine.RANDOM.nextLong();
            d.level1 = dim;
            d.special = special;
            d.species = target.species;
            d.category = CATEGORIES.get(target.rank);
            d.newB = !owns(pokedex, target.species);
            d.seed = Engine.RANDOM.nextLong();
            d.level2 = target.levelMin + Engine.RANDOM.nextInt(Math.max(1, target.levelMax - target.levelMin + 1));
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
            // Un seul indice est enfoui par enquête (le brossage reste une exception), et ce qu'on déterre varie.
            List<Trace> buriable = new ArrayList<>();
            for (Trace t : d.traces) {
                if (Genetics.BURIED_NAMES.containsKey(t.kind)) buriable.add(t);
            }
            Collections.shuffle(buriable, Engine.RANDOM);
            // Fouille : tout est enfoui. Sous l'eau : rien ne l'est (on ne brosse pas au fond d'un lac).
            for (int i = 0; i < buriable.size(); i++) {
                Trace t = buriable.get(i);
                t.buried = !water && !cave && (i == 0 || "dig_site".equals(special));
                t.variant = Engine.RANDOM.nextInt(Genetics.VARIANTS.getOrDefault(t.kind, 1));
            }
            // Une trace non enfouie part dans une grotte (sauf sous l'eau, et en fouille où tout est enfoui).
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

    static boolean aquatic(ServerLevel level1, double x, double z) {
        Holder<Biome> biome = level1.getBiome(BlockPos.containing(x, 100, z));
        String name = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        return name.contains("ocean") || name.contains("river") || biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
    }

    static boolean owns(PokedexManager pokedex, String species) {
        Species s = PokemonSpecies.getByName(species);
        return s != null && pokedex.getHighestKnowledgeForSpecies(s.getResourceIdentifier()) == PokedexEntryProgress.OWNED;
    }

    // Espèces de surface que Cobblemon fait apparaître dans ce biome (null = partout) : terrestres, ou vivant dans l'eau.
    // « grotte » : seulement les espèces que Cobblemon fait apparaître sous terre (à l'abri du ciel, ou en profondeur).
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
            if (underground(c) != cave) continue; // les enquêtes de surface ignorent les apparitions souterraines, et inversement
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

    // La cible + des leurres au profil proche (mais jamais identique : les 5 marqueurs désignent toujours une seule espèce).
    // « serre » (enquête exceptionnelle) : tous les leurres sont pris parmi les profils les plus proches.
    private static List<String> suspects(String target, List<Candidate> local, boolean clamp, boolean water, boolean cave) {
        Species s = PokemonSpecies.getByName(target);
        Set<String> nearby = new HashSet<>();
        for (Candidate c : local) nearby.add(c.species);
        Map<String, Double> notes = new HashMap<>();
        for (Candidate c : basin(null, null, water, cave)) {
            if (c.species.equals(target)) continue;
            Species other = PokemonSpecies.getByName(c.species);
            int common = Genetics.common(s, other);
            if (common >= Genetics.MARKERS.size()) continue;
            notes.put(c.species, common + (nearby.contains(c.species) ? 0.6 : 0) + Engine.RANDOM.nextDouble() * 1.2);
        }
        List<String> tries = new ArrayList<>(notes.keySet());
        tries.sort((a, b) -> Double.compare(notes.get(b), notes.get(a)));
        int closeOnes = clamp ? Math.max(0, Engine.settings.suspectCount - 1) : Math.max(0, Engine.settings.suspectCount - 1) * 2 / 3;
        List<String> list = new ArrayList<>(tries.subList(0, Math.min(closeOnes, tries.size())));
        List<String> left1 = new ArrayList<>(tries.subList(list.size(), tries.size()));
        Collections.shuffle(left1, Engine.RANDOM);
        while (list.size() < Engine.settings.suspectCount - 1 && !left1.isEmpty()) list.add(left1.remove(0));
        list.add(target);
        Collections.shuffle(list, Engine.RANDOM);
        return list;
    }
}
