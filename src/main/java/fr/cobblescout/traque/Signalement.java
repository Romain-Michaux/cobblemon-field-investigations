package fr.cobblescout.traque;

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
final class Signalement {
    private Signalement() {}

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
    //  - souterraine : espèces des grottes, traces et cible sous terre (le radar donne la profondeur).
    static final List<String> GENERIQUES = List.of("nocturne", "craintive", "exceptionnel", "migratrice", "souterraine");

    // Type lié au biome : il s'applique si le biome porte l'une de ces étiquettes, ou si son nom contient l'un de ces mots
    // (pour les biomes de mods qui ne sont pas étiquetés).
    private record Milieu(String type, List<String> etiquettes, List<String> mots) {}

    private static final List<Milieu> MILIEUX = List.of(
            new Milieu("fouille", List.of("c:is_desert", "minecraft:is_badlands", "minecraft:is_beach", "c:is_sandy"), List.of("desert", "badlands", "beach", "dune")),
            new Milieu("cimes", List.of("minecraft:is_forest", "minecraft:is_jungle", "minecraft:is_taiga"), List.of("forest", "jungle", "taiga", "woods")),
            new Milieu("altitude", List.of("minecraft:is_mountain", "minecraft:is_hill", "c:is_mountain"), List.of("mountain", "peak", "hills", "highland", "cliff")),
            new Milieu("givre", List.of("c:is_snowy", "c:is_icy"), List.of("snow", "frozen", "ice", "glacier", "tundra")),
            new Milieu("brume", List.of("c:is_swamp"), List.of("swamp", "marsh", "bog", "bayou", "mangrove")));

    private static List<String> typesDuBiome(Holder<Biome> biome) {
        String nom = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        List<String> types = new ArrayList<>();
        for (Milieu m : MILIEUX) {
            boolean ok = false;
            for (String e : m.etiquettes) ok |= biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(e)));
            for (String mot : m.mots) ok |= nom.contains(mot);
            if (ok) types.add(m.type);
        }
        return types;
    }

    private record Candidat(String espece, int rang, int niveauMin, int niveauMax) {}

    static void ouvrirEnquete(ServerPlayer joueur) {
        UUID id = joueur.getUUID();
        if (Moteur.dossier(joueur) != null) return;
        String dim = Moteur.dimension(joueur);
        if (!Moteur.reglages.dimensions.contains(dim)) {
            Messages.annonce(joueur, "dimension", 0xE8543A);
            return;
        }
        if (Moteur.etat.prochaineEnquete.getOrDefault(id, 0L) > System.currentTimeMillis() && !joueur.isCreative()) {
            Messages.synchroniser(joueur);
            return;
        }
        if (pistes(joueur) != null) {
            Messages.synchroniser(joueur); // des signalements l'attendent déjà sur son tableau
            return;
        }
        ServerLevel monde = joueur.serverLevel();
        PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(joueur);
        int voulu = Math.max(1, Math.min(3, Moteur.reglages.nombrePistes));
        List<Dossier> pistes = new ArrayList<>();
        Set<String> prises = new HashSet<>();
        for (int i = 0; i < voulu; i++) {
            // Le premier signalement est toujours ordinaire : il y a toujours une enquête classique au choix.
            boolean special = i > 0 && Moteur.HASARD.nextDouble() < Moteur.reglages.chanceEnqueteSpeciale;
            Dossier d = signalement(joueur, monde, dim, pokedex, prises, special);
            if (d == null && special) d = signalement(joueur, monde, dim, pokedex, prises, false);
            if (d == null) break;
            prises.add(d.espece);
            pistes.add(d);
        }
        if (pistes.isEmpty()) {
            Messages.annonce(joueur, "aucun_signalement", 0xF2C93A);
            return;
        }
        if (voulu == 1) {
            engager(joueur, pistes.get(0));
            return;
        }
        Collections.shuffle(pistes, Moteur.HASARD);
        Moteur.etat.offres.put(id, pistes);
        Moteur.etat.finOffres.put(id, System.currentTimeMillis() + Math.max(1, Moteur.reglages.minutesValiditePistes) * 60_000L);
        Moteur.sauver();
        joueur.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        Messages.synchroniser(joueur);
    }

    // Signalements en attente de choix : ils tombent s'ils ont expiré ou si le joueur a changé de dimension.
    static List<Dossier> pistes(ServerPlayer joueur) {
        UUID id = joueur.getUUID();
        List<Dossier> pistes = Moteur.etat.offres.get(id);
        if (pistes == null) return null;
        boolean valides = !pistes.isEmpty() && Moteur.etat.finOffres.getOrDefault(id, 0L) > System.currentTimeMillis();
        for (Dossier d : pistes) valides &= d.monde != null && d.monde.equals(Moteur.dimension(joueur));
        if (valides) return pistes;
        Moteur.etat.offres.remove(id);
        Moteur.etat.finOffres.remove(id);
        Moteur.sauver();
        return null;
    }

    static void choisir(ServerPlayer joueur, int index) {
        List<Dossier> pistes = Moteur.dossier(joueur) == null ? pistes(joueur) : null;
        if (pistes == null || index < 0 || index >= pistes.size()) {
            Messages.synchroniser(joueur);
            return;
        }
        engager(joueur, pistes.get(index));
    }

    private static void engager(ServerPlayer joueur, Dossier d) {
        UUID id = joueur.getUUID();
        Moteur.etat.offres.remove(id);
        Moteur.etat.finOffres.remove(id);
        d.chef = id;
        d.pardon = Moteur.rang(joueur) >= 4 ? 1 : 0; // sang-froid
        Moteur.etat.dossiers.put(id, d);
        Moteur.sauver();
        joueur.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
        Messages.annonce(joueur, d.special == null ? "nouvelle_enquete" : "enquete_" + d.special, 0xF2C93A);
        Messages.synchroniser(joueur);
    }

    // Tire un signalement autour du joueur : une zone de traces, une tanière, une espèce du biome et ses suspects.
    // Avec « specialVoulu », le type dépend de l'endroit tiré : aquatique si la zone tombe dans l'eau, sinon un type
    // possible partout ou (trois fois plus souvent) un type propre au biome de la zone.
    private static Dossier signalement(ServerPlayer joueur, ServerLevel monde, String dim, PokedexManager pokedex, Set<String> prises, boolean specialVoulu) {
        for (int essai = 0; essai < 60; essai++) {
            double a1 = Moteur.HASARD.nextDouble() * Math.PI * 2;
            double d1 = Moteur.reglages.zoneDistanceMin + Moteur.HASARD.nextDouble() * (Moteur.reglages.zoneDistanceMax - Moteur.reglages.zoneDistanceMin);
            double zx = joueur.getX() + Math.cos(a1) * d1, zz = joueur.getZ() + Math.sin(a1) * d1;
            boolean eau = aquatique(monde, zx, zz);
            if (eau && !specialVoulu) continue;
            String special = null;
            if (eau) {
                special = "aquatique";
            } else if (specialVoulu) {
                List<String> possibles = new ArrayList<>(GENERIQUES);
                for (String t : typesDuBiome(monde.getBiome(BlockPos.containing(zx, 100, zz)))) possibles.addAll(List.of(t, t, t));
                special = possibles.get(Moteur.HASARD.nextInt(possibles.size()));
            }
            double a2 = Moteur.HASARD.nextDouble() * Math.PI * 2;
            double d2 = (Moteur.reglages.taniereDistanceMin + Moteur.HASARD.nextDouble() * (Moteur.reglages.taniereDistanceMax - Moteur.reglages.taniereDistanceMin))
                    * ("migratrice".equals(special) ? 1.5 : 1);
            double tx = zx + Math.cos(a2) * d2, tz = zz + Math.sin(a2) * d2;
            if (aquatique(monde, tx, tz) != eau) continue; // la tanière est dans le même milieu que les traces
            Holder<Biome> biome = monde.getBiome(BlockPos.containing(tx, 100, tz));
            boolean grotte = "souterraine".equals(special);
            List<Candidat> bassin = bassin(biome, monde.dimension().location(), eau, grotte);
            bassin.removeIf(c -> prises.contains(c.espece)); // jamais deux signalements pour la même espèce
            if (bassin.size() < 3) continue;
            double total = 0;
            double[] poids = new double[bassin.size()];
            for (int i = 0; i < bassin.size(); i++) {
                Candidat c = bassin.get(i);
                poids[i] = Moteur.reglages.poidsCategories.getOrDefault(CATEGORIES.get(c.rang), 1.0) * (possede(pokedex, c.espece) ? 1.0 : Moteur.reglages.bonusNouvelleEspece);
                total += poids[i];
            }
            double tirage = Moteur.HASARD.nextDouble() * total;
            Candidat cible = bassin.get(bassin.size() - 1);
            for (int i = 0; i < bassin.size(); i++) {
                tirage -= poids[i];
                if (tirage <= 0) {
                    cible = bassin.get(i);
                    break;
                }
            }
            Dossier d = new Dossier();
            d.id = Moteur.HASARD.nextLong();
            d.monde = dim;
            d.special = special;
            d.espece = cible.espece;
            d.categorie = CATEGORIES.get(cible.rang);
            d.nouvelle = !possede(pokedex, cible.espece);
            d.graine = Moteur.HASARD.nextLong();
            d.niveau = cible.niveauMin + Moteur.HASARD.nextInt(Math.max(1, cible.niveauMax - cible.niveauMin + 1));
            d.zoneX = zx;
            d.zoneZ = zz;
            d.taniereX = tx;
            d.taniereZ = tz;
            for (String m : Genetique.MARQUEURS) {
                Trace t = new Trace();
                t.genre = m;
                for (int k = 0; k < 20; k++) {
                    double a = Moteur.HASARD.nextDouble() * Math.PI * 2, r = 6 + Moteur.HASARD.nextDouble() * (Moteur.rayon(d) - 6);
                    t.x = Math.floor(zx + Math.cos(a) * r) + 0.5;
                    t.z = Math.floor(zz + Math.sin(a) * r) + 0.5;
                    boolean libre = true;
                    for (Trace autre : d.traces) libre &= Math.hypot(autre.x - t.x, autre.z - t.z) >= 12;
                    if (libre) break;
                }
                d.traces.add(t);
            }
            // Un seul indice est enfoui par enquête (le brossage reste une exception), et ce qu'on déterre varie.
            List<Trace> enfouissables = new ArrayList<>();
            for (Trace t : d.traces) {
                if (Genetique.NOMS_ENFOUIS.containsKey(t.genre)) enfouissables.add(t);
            }
            Collections.shuffle(enfouissables, Moteur.HASARD);
            // Fouille : tout est enfoui. Sous l'eau : rien ne l'est (on ne brosse pas au fond d'un lac).
            for (int i = 0; i < enfouissables.size(); i++) {
                Trace t = enfouissables.get(i);
                t.enfoui = !eau && !grotte && (i == 0 || "fouille".equals(special));
                t.variante = Moteur.HASARD.nextInt(Genetique.VARIANTES.getOrDefault(t.genre, 1));
            }
            d.suspects = suspects(cible.espece, bassin, "exceptionnel".equals(special), eau, grotte);
            return d;
        }
        return null;
    }

    static boolean aquatique(ServerLevel monde, double x, double z) {
        Holder<Biome> biome = monde.getBiome(BlockPos.containing(x, 100, z));
        String nom = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        return nom.contains("ocean") || nom.contains("river") || biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
    }

    static boolean possede(PokedexManager pokedex, String espece) {
        Species s = PokemonSpecies.getByName(espece);
        return s != null && pokedex.getHighestKnowledgeForSpecies(s.getResourceIdentifier()) == PokedexEntryProgress.OWNED;
    }

    // Espèces de surface que Cobblemon fait apparaître dans ce biome (null = partout) : terrestres, ou vivant dans l'eau.
    // « grotte » : seulement les espèces que Cobblemon fait apparaître sous terre (à l'abri du ciel, ou en profondeur).
    private static List<Candidat> bassin(Holder<Biome> biome, ResourceLocation dimension, boolean eau, boolean grotte) {
        Map<String, Candidat> resultat = new HashMap<>();
        for (SpawnDetail detail : CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails()) {
            if (!(detail instanceof PokemonSpawnDetail pokemon)) continue;
            int rang = CATEGORIES.indexOf(detail.getBucket());
            String position = detail.getSpawnablePositionType().getName();
            if (rang < 0 || !(eau ? "submerged".equals(position) || "surface".equals(position) : "grounded".equals(position))) continue;
            if (pokemon.getPokemon().getForm() != null || (biome != null && !convient(detail, biome, dimension, grotte))) continue;
            if (biome == null && grotte && !sousTerre(detail)) continue;
            String nom = pokemon.getPokemon().getSpecies();
            if (nom == null) continue;
            nom = nom.substring(nom.indexOf(':') + 1).toLowerCase();
            Species espece = PokemonSpecies.getByName(nom);
            if (espece == null || !espece.getImplemented()) continue;
            if (espece.getLabels().stream().anyMatch(Moteur.reglages.etiquettesInterdites::contains)) continue;
            int min = 10, max = 30;
            if (pokemon.getLevelRange() != null) {
                min = pokemon.getLevelRange().getFirst();
                max = pokemon.getLevelRange().getLast();
            }
            Candidat ancien = resultat.get(nom);
            if (ancien == null || rang < ancien.rang) resultat.put(nom, new Candidat(nom, rang, min, Math.max(min, max)));
        }
        return new ArrayList<>(resultat.values());
    }

    private static boolean sousTerre(SpawningCondition<?> c) {
        return Boolean.FALSE.equals(c.getCanSeeSky()) || (c.getMaxY() != null && c.getMaxY() < 62);
    }

    private static boolean sousTerre(SpawnDetail detail) {
        for (SpawningCondition<?> c : detail.getConditions()) {
            if (sousTerre(c)) return true;
        }
        return false;
    }

    private static boolean convient(SpawnDetail detail, Holder<Biome> biome, ResourceLocation dimension, boolean grotte) {
        boolean ok = false;
        for (SpawningCondition<?> c : detail.getConditions()) {
            Set<RegistryLikeCondition<Biome>> biomes = c.getBiomes();
            if (biomes == null || biomes.isEmpty()) continue;
            if (sousTerre(c) != grotte) continue; // les enquêtes de surface ignorent les apparitions souterraines, et inversement
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
    private static List<String> suspects(String cible, List<Candidat> locaux, boolean serre, boolean eau, boolean grotte) {
        Species s = PokemonSpecies.getByName(cible);
        Set<String> duCoin = new HashSet<>();
        for (Candidat c : locaux) duCoin.add(c.espece);
        Map<String, Double> notes = new HashMap<>();
        for (Candidat c : bassin(null, null, eau, grotte)) {
            if (c.espece.equals(cible)) continue;
            Species autre = PokemonSpecies.getByName(c.espece);
            int communs = Genetique.communs(s, autre);
            if (communs >= Genetique.MARQUEURS.size()) continue;
            notes.put(c.espece, communs + (duCoin.contains(c.espece) ? 0.6 : 0) + Moteur.HASARD.nextDouble() * 1.2);
        }
        List<String> tries = new ArrayList<>(notes.keySet());
        tries.sort((a, b) -> Double.compare(notes.get(b), notes.get(a)));
        int proches = serre ? Math.max(0, Moteur.reglages.nombreSuspects - 1) : Math.max(0, Moteur.reglages.nombreSuspects - 1) * 2 / 3;
        List<String> liste = new ArrayList<>(tries.subList(0, Math.min(proches, tries.size())));
        List<String> reste = new ArrayList<>(tries.subList(liste.size(), tries.size()));
        Collections.shuffle(reste, Moteur.HASARD);
        while (liste.size() < Moteur.reglages.nombreSuspects - 1 && !reste.isEmpty()) liste.add(reste.remove(0));
        liste.add(cible);
        Collections.shuffle(liste, Moteur.HASARD);
        return liste;
    }
}
