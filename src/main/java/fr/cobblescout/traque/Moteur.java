package fr.cobblescout.traque;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.properties.HiddenAbilityProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

// Le déroulé de l'enquête, côté serveur.
//  1. Tableau d'enquête : ouvrir une enquête -> une zone de traces est signalée.
//  2. Terrain : prélever les traces avec le kit (une fiole par trace) -> échantillons.
//  3. Séquenceur : analyser chaque échantillon (un mini-jeu par type de marqueur).
//  4. Tableau : épingler les échantillons analysés ; les suspects incompatibles s'éliminent. Assez d'épingles -> lancer la traque.
//  5. Terrain : relevés de cris au kit, recoupement, approche discrète, capture.
public final class Moteur {
    static final List<String> CATEGORIES = List.of("common", "uncommon", "rare", "ultra-rare");

    public static class Reglages {
        public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
        public int zoneDistanceMin = 100;
        public int zoneDistanceMax = 180;
        public int rayonZone = 45;
        public int taniereDistanceMin = 150;
        public int taniereDistanceMax = 280;
        public int dosesParEchantillon = 2;
        public int nombreSuspects = 12;
        public int epinglesPourTraquer = 3;
        public int minutesEntreEnquetes = 10;
        public int secondesEntreReleves = 6;
        public int distanceEntreReleves = 20;
        public double erreurMinDegres = 4;
        public double erreurMaxDegres = 14;
        public int minutesAvantDeplacement = 8;
        public int rayonApproche = 48;
        public int rayonAlerte = 18;
        public int rayonDebusque = 7;
        public double multChromaParEtoile = 3.0;
        public double bonusNouvelleEspece = 2.0;
        public Map<String, Double> poidsCategories = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 2.5, "rare", 3.0, "ultra-rare", 2.0));
        public List<String> etiquettesInterdites = new ArrayList<>(List.of("legendary", "mythical", "ultra_beast", "paradox"));
        // Le butin d'une enquête résolue vient des tables de butin du mod (data/cobblescout/loot_table/traque) :
        // etoiles_1 à etoiles_5 selon la qualité de l'enquête, rarete_<catégorie> selon l'espèce, palier tous les N dossiers.
        public int palierBonus = 5;
        // À l'ouverture d'une enquête, le joueur choisit parmi plusieurs signalements (1 = pas de choix, comme en 1.0).
        public int nombrePistes = 3;
        public int minutesValiditePistes = 10;
        // Chance qu'un signalement (autre que le premier) soit une enquête spéciale : nocturne, craintive ou exceptionnelle.
        public double chanceEnqueteSpeciale = 0.3;
        // Rythme des mini-jeux : "easy" (un tiers plus lent), "normal" ou "hard" (un quart plus rapide).
        public String difficulteMiniJeux = "normal";
    }

    // Enquêtes spéciales. Nocturne : traces, cris et cible seulement la nuit (chance de chromatique doublée).
    // Craintive : elle repère le joueur de bien plus loin (butin spécial doublé). Exceptionnelle : un résultat d'analyse
    // de plus est exigé et les suspects se ressemblent tous (talent caché garanti). Toutes donnent le butin « speciale ».
    static final List<String> SPECIAUX = List.of("nocturne", "craintive", "exceptionnel");

    public static class Trace {
        public String genre;
        public double x, z;
        public boolean recolte, posee;
        public int px, py, pz;
        // Indice enfoui : d'abord un bloc de sol remué (qui remplace le bloc d'origine), puis l'indice une fois dégagé au pinceau.
        public boolean enfoui, degage, perdue;
        public String origine;
        public int brossage;

        boolean finie() {
            return recolte || perdue;
        }
    }

    public static class Releve {
        public double x, z, cap, erreur;
        public boolean perime;
    }

    public static class Dossier {
        public long id;
        public String phase = "PRELEVEMENTS"; // PRELEVEMENTS -> TRAQUE -> APPROCHE -> DEBUSQUE
        public String monde, espece, categorie, pronostic;
        // Type d'enquête spéciale (voir SPECIAUX), ou null pour une enquête ordinaire.
        public String special;
        public boolean nouvelle;
        public long graine;
        public int niveau, fuites;
        // Ratés des mini-jeux (prélèvement abîmé, dose perdue, indice détruit) : chacun coûte une demi-étoile.
        public int fautes;
        public transient int scan, scanAutre;
        // Dernier instant où la cible a été vue en combat près du joueur (pour savoir qui l'a mise K.O.).
        public transient long dernierCombat;
        public double zoneX, zoneZ, taniereX, taniereZ;
        public List<Trace> traces = new ArrayList<>();
        public List<String> suspects = new ArrayList<>();
        public List<String> epingles = new ArrayList<>();
        // Suspects que le joueur a lui-même barrés sur son tableau (le mod n'écarte personne à sa place).
        public List<String> ecartes = new ArrayList<>();
        public List<Releve> releves = new ArrayList<>();
        public long prochainReleve, prochainDeplacement, finLueur;
        public UUID pokemon, entite;
        public boolean annonceApproche;
    }

    public static class Etat {
        public Map<UUID, Dossier> dossiers = new HashMap<>();
        public Map<UUID, Long> prochaineEnquete = new HashMap<>();
        public Map<UUID, Integer> reussies = new HashMap<>();
        // Signalements proposés au joueur tant qu'il n'en a pas choisi un, et leur date d'expiration.
        public Map<UUID, List<Dossier>> offres = new HashMap<>();
        public Map<UUID, Long> finOffres = new HashMap<>();
    }

    private record Candidat(String espece, int rang, int niveauMin, int niveauMax) {}

    private record Recoupement(boolean valide, double x, double z, double incertitude, String message) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random HASARD = new Random();
    static Reglages reglages = new Reglages();
    static Etat etat = new Etat();
    private static Path fichierEtat;
    private static final Map<UUID, PokemonEntity> ENTITES = new HashMap<>();
    private static final Map<UUID, Boolean> ANALYSES = new HashMap<>();
    private static final Map<UUID, BlockPos> PRELEVEMENTS = new HashMap<>();
    // Vrai pendant que le mod remplace lui-même un sol remué (dégagement, nettoyage) : ce n'est pas une destruction.
    private static boolean remplacement;

    private Moteur() {}

    static void charger() {
        Path dossier = FabricLoader.getInstance().getConfigDir();
        reglages = lire(dossier.resolve("cobblemon_field_investigations.json"), Reglages.class, new Reglages());
        ecrire(dossier.resolve("cobblemon_field_investigations.json"), reglages);
        fichierEtat = dossier.resolve("cobblemon_field_investigations_state.json");
        Etat lu = lire(fichierEtat, Etat.class, null);
        if (lu != null) {
            etat = lu;
            if (etat.dossiers == null) etat.dossiers = new HashMap<>();
            if (etat.prochaineEnquete == null) etat.prochaineEnquete = new HashMap<>();
            if (etat.reussies == null) etat.reussies = new HashMap<>();
            if (etat.offres == null) etat.offres = new HashMap<>();
            if (etat.finOffres == null) etat.finOffres = new HashMap<>();
        }
    }

    static void arret() {
        ENTITES.clear();
        ANALYSES.clear();
        PRELEVEMENTS.clear();
    }

    static Dossier dossier(ServerPlayer joueur) {
        return etat.dossiers.get(joueur.getUUID());
    }

    // ---------- Messages vers le client ----------

    static void envoyer(ServerPlayer joueur, Paquet p) {
        if (ServerPlayNetworking.canSend(joueur, Paquet.TYPE)) ServerPlayNetworking.send(joueur, p);
    }

    static MutableComponent tr(String cle, Object... args) {
        return Component.translatable("cobblemon_field_investigations." + cle, args);
    }

    // Les annonces partent sous forme de textes traduisibles : chaque joueur les lit dans la langue de son jeu.
    public static void annonce(ServerPlayer joueur, Component titre, Component texte, int couleur) {
        Paquet p = Paquet.de("annonce");
        p.donnees().putString("titre", Component.Serializer.toJson(titre, joueur.registryAccess()));
        p.donnees().putString("texte", Component.Serializer.toJson(texte, joueur.registryAccess()));
        p.donnees().putInt("couleur", couleur);
        envoyer(joueur, p);
    }

    public static void annonce(ServerPlayer joueur, String cle, int couleur, Object... args) {
        annonce(joueur, tr("annonce." + cle + ".titre", args), tr("annonce." + cle + ".texte", args), couleur);
    }

    static String etoilesTexte(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
    }

    private static void ouvrir(ServerPlayer joueur, String ecran) {
        synchroniser(joueur);
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", ecran);
        envoyer(joueur, p);
    }

    // Tout ce que le client a le droit de savoir sur l'enquête du joueur (jamais l'espèce avant qu'elle soit débusquée).
    static void synchroniser(ServerPlayer joueur) {
        Paquet p = Paquet.de("etat");
        CompoundTag t = p.donnees();
        Dossier d = dossier(joueur);
        t.putBoolean("actif", d != null);
        t.putInt("reussies", etat.reussies.getOrDefault(joueur.getUUID(), 0));
        t.putLong("attente", Math.max(0, etat.prochaineEnquete.getOrDefault(joueur.getUUID(), 0L) - System.currentTimeMillis()));
        t.putInt("difficulte", "easy".equalsIgnoreCase(reglages.difficulteMiniJeux) ? -1 : "hard".equalsIgnoreCase(reglages.difficulteMiniJeux) ? 1 : 0);
        List<Dossier> pistes = d == null ? pistes(joueur) : null;
        if (pistes != null) {
            // Ce que le joueur sait d'un signalement avant de le choisir : sa rareté, son type, où se trouve la zone.
            ListTag liste = new ListTag();
            for (Dossier o : pistes) {
                CompoundTag c = new CompoundTag();
                c.putInt("rang", Math.max(0, CATEGORIES.indexOf(o.categorie)));
                c.putString("special", o.special == null ? "" : o.special);
                c.putBoolean("nouvelle", o.nouvelle);
                c.putDouble("zoneX", o.zoneX);
                c.putDouble("zoneZ", o.zoneZ);
                liste.add(c);
            }
            t.put("pistes", liste);
            t.putLong("pistesFin", Math.max(0, etat.finOffres.getOrDefault(joueur.getUUID(), 0L) - System.currentTimeMillis()));
        }
        if (d != null) {
            Species cible = PokemonSpecies.getByName(d.espece);
            t.putString("phase", d.phase);
            t.putInt("rang", Math.max(0, CATEGORIES.indexOf(d.categorie)));
            t.putBoolean("nouvelle", d.nouvelle);
            t.putDouble("zoneX", d.zoneX);
            t.putDouble("zoneZ", d.zoneZ);
            t.putInt("rayonZone", reglages.rayonZone);
            t.putInt("requis", requis(d));
            t.putString("special", d.special == null ? "" : d.special);
            t.putString("pronostic", d.pronostic == null ? "" : d.pronostic);
            ListTag traces = new ListTag();
            for (Trace tr : d.traces) {
                CompoundTag c = new CompoundTag();
                c.putString("genre", tr.genre);
                c.putBoolean("recolte", tr.recolte);
                c.putBoolean("enfoui", tr.enfoui);
                c.putBoolean("degage", tr.degage);
                c.putBoolean("perdue", tr.perdue);
                c.putBoolean("epingle", d.epingles.contains(tr.genre));
                if (d.epingles.contains(tr.genre) && cible != null) c.putString("valeur", Genetique.valeur(tr.genre, cible));
                traces.add(c);
            }
            t.put("traces", traces);
            ListTag suspects = new ListTag();
            PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(joueur);
            for (String s : d.suspects) {
                Species esp = PokemonSpecies.getByName(s);
                if (esp == null) continue;
                CompoundTag c = new CompoundTag();
                c.putString("espece", s);
                ListTag valeurs = new ListTag();
                for (String m : Genetique.MARQUEURS) valeurs.add(StringTag.valueOf(Genetique.valeur(m, esp)));
                c.put("valeurs", valeurs);
                c.putBoolean("elimine", d.ecartes != null && d.ecartes.contains(s));
                // Une petite Poké Ball signale au joueur les espèces qu'il possède déjà.
                c.putBoolean("possede", possede(pokedex, s));
                suspects.add(c);
            }
            t.put("suspects", suspects);
            ListTag releves = new ListTag();
            for (Releve r : d.releves) {
                CompoundTag c = new CompoundTag();
                c.putDouble("x", r.x);
                c.putDouble("z", r.z);
                c.putDouble("cap", r.cap);
                c.putDouble("erreur", r.erreur);
                c.putBoolean("perime", r.perime);
                releves.add(c);
            }
            t.put("releves", releves);
            Recoupement rec = recouper(d);
            t.putBoolean("recValide", rec.valide);
            t.putString("recMessage", rec.message);
            if (rec.valide) {
                t.putDouble("recX", rec.x);
                t.putDouble("recZ", rec.z);
                t.putDouble("recIncertitude", rec.incertitude);
            }
            boolean identifie = "APPROCHE".equals(d.phase) || "DEBUSQUE".equals(d.phase);
            if (identifie) t.putString("espece", d.espece);
            // Tant que la cible n'est pas identifiée, le client ne doit pas pouvoir deviner si le pronostic est juste.
            t.putInt("etoiles", etoiles(d, identifie));
            t.putInt("fautes", d.fautes);
            t.putInt("fuites", d.fuites);
        }
        envoyer(joueur, p);
    }

    // Le joueur barre (ou rétablit) un suspect sur son tableau, d'après ce qu'il déduit des marqueurs analysés.
    static void ecarter(ServerPlayer joueur, String espece) {
        Dossier d = dossier(joueur);
        if (d == null || !"PRELEVEMENTS".equals(d.phase) || !d.suspects.contains(espece)) return;
        if (d.ecartes == null) d.ecartes = new ArrayList<>();
        if (!d.ecartes.remove(espece)) d.ecartes.add(espece);
        sauver();
        synchroniser(joueur);
    }

    // Qualité de l'enquête, de 1 à 5 étoiles. On part de 5 ; un raté de mini-jeu coûte une demi-étoile (le premier est
    // donc gratuit), une fuite de la cible une étoile, un mauvais pronostic deux étoiles.
    private static int etoiles(Dossier d, boolean avecPronostic) {
        int demis = 10 - d.fautes - 2 * d.fuites;
        if (avecPronostic && d.pronostic != null && !d.espece.equals(d.pronostic)) demis -= 4;
        return Math.max(1, Math.min(5, (demis + 1) / 2));
    }

    private static int etoiles(Dossier d) {
        return etoiles(d, true);
    }

    // IV parfaits accordés au Pokémon : aucun à 1 étoile, 4 au mieux à 5 étoiles.
    static int ivParfaits(int etoiles) {
        return Math.max(0, Math.min(4, etoiles - 1));
    }

    private static boolean est(Dossier d, String special) {
        return special.equals(d.special);
    }

    // Résultats d'analyse à réunir avant de pouvoir désigner un suspect.
    static int requis(Dossier d) {
        return Math.min(Genetique.MARQUEURS.size(), reglages.epinglesPourTraquer + (est(d, "exceptionnel") ? 1 : 0));
    }

    // Distance en dessous de laquelle la cible repère le joueur.
    static int alerte(Dossier d) {
        return est(d, "craintive") ? (int) Math.round(reglages.rayonAlerte * 1.6) : reglages.rayonAlerte;
    }

    // Une cible nocturne ne laisse rien voir ni entendre en plein jour.
    private static boolean dort(Dossier d, ServerLevel monde) {
        return est(d, "nocturne") && !monde.isNight();
    }

    // Débloque un progrès du mod (data/cobblemon_field_investigations/advancement/<nom>.json, critère « fait »).
    static void progres(ServerPlayer joueur, String nom) {
        try {
            var p = joueur.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", nom));
            if (p != null) joueur.getAdvancements().award(p, "fait");
        } catch (Exception e) {
            System.err.println("[Field Investigations] Progrès impossible à accorder : " + nom + " : " + e);
        }
    }

    private static void faute(ServerPlayer joueur, Dossier d) {
        d.fautes++;
        sauver();
    }

    // ---------- Analyse ----------

    // Lance l'analyse de l'échantillon tenu en main, avec le séquenceur de poche du kit.
    public static void analyser(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        ItemStack pile = joueur.getMainHandItem();
        if (!pile.is(Contenu.ECHANTILLON)) return;
        if (!joueur.getInventory().contains(new ItemStack(Contenu.KIT))) {
            annonce(joueur, "echantillon_kit", 0xF2C93A);
            return;
        }
        CompoundTag donnees = Contenu.donnees(pile);
        if (d == null || d.id != donnees.getLong("enquete") || donnees.getBoolean("analyse")) {
            annonce(joueur, "trop_ancien", 0xE8543A);
            return;
        }
        ANALYSES.put(joueur.getUUID(), Boolean.TRUE);
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", "analyse");
        p.donnees().putString("genre", donnees.getString("genre"));
        p.donnees().putString("jeu", Genetique.jeuAnalyse(donnees.getString("genre"), donnees.getString("jeu")));
        p.donnees().putBoolean("enfoui", donnees.getBoolean("enfoui"));
        p.donnees().putLong("graine", HASARD.nextLong());
        p.donnees().putInt("doses", donnees.getInt("doses"));
        envoyer(joueur, p);
    }

    // Résultat d'un mini-jeu d'analyse, envoyé par le client.
    static void resultatAnalyse(ServerPlayer joueur, boolean succes) {
        if (ANALYSES.remove(joueur.getUUID()) == null) return;
        Dossier d = dossier(joueur);
        ItemStack pile = joueur.getMainHandItem();
        if (d == null || !pile.is(Contenu.ECHANTILLON)) return;
        CompoundTag donnees = Contenu.donnees(pile);
        if (donnees.getBoolean("analyse") || donnees.getLong("enquete") != d.id) return;
        String genre = donnees.getString("genre");
        Species cible = PokemonSpecies.getByName(d.espece);
        if (succes && cible != null) {
            // Le résultat part directement dans le dossier (consultable depuis le kit).
            pile.shrink(1);
            if (!d.epingles.contains(genre)) d.epingles.add(genre);
            sauver();
            joueur.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
            annonce(joueur, "analyse_reussie", 0x6CCB5F, Component.translatable(Genetique.NOMS_MARQUEURS.get(genre)),
                    Genetique.libelle(genre, Genetique.valeur(genre, cible)), d.epingles.size(), requis(d));
            progres(joueur, "premiere_analyse");
            synchroniser(joueur);
            return;
        }
        int doses = donnees.getInt("doses") - 1;
        faute(joueur, d);
        joueur.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (doses <= 0) {
            pile.shrink(1);
            annonce(joueur, "echantillon_detruit", 0xE8543A, etoilesTexte(etoiles(d, false)));
            synchroniser(joueur);
        } else {
            donnees.putInt("doses", doses);
            pile.set(DataComponents.CUSTOM_DATA, CustomData.of(donnees));
            annonce(joueur, "dose_perdue", 0xE8543A, doses, etoilesTexte(etoiles(d, false)));
            synchroniser(joueur);
        }
    }

    // ---------- 1. Ouvrir une enquête ----------

    static void ouvrirEnquete(ServerPlayer joueur) {
        UUID id = joueur.getUUID();
        if (dossier(joueur) != null) return;
        String dim = dimension(joueur);
        if (!reglages.dimensions.contains(dim)) {
            annonce(joueur, "dimension", 0xE8543A);
            return;
        }
        if (etat.prochaineEnquete.getOrDefault(id, 0L) > System.currentTimeMillis() && !joueur.isCreative()) {
            synchroniser(joueur);
            return;
        }
        if (pistes(joueur) != null) {
            synchroniser(joueur); // des signalements l'attendent déjà sur son tableau
            return;
        }
        ServerLevel monde = joueur.serverLevel();
        PokedexManager pokedex = Cobblemon.playerDataManager.getPokedexData(joueur);
        int voulu = Math.max(1, Math.min(3, reglages.nombrePistes));
        List<Dossier> pistes = new ArrayList<>();
        Set<String> prises = new HashSet<>();
        for (int i = 0; i < voulu; i++) {
            // Le premier signalement est toujours ordinaire : il y a toujours une enquête classique au choix.
            String special = i > 0 && HASARD.nextDouble() < reglages.chanceEnqueteSpeciale ? SPECIAUX.get(HASARD.nextInt(SPECIAUX.size())) : null;
            Dossier d = signalement(joueur, monde, dim, pokedex, prises, special);
            if (d == null) break;
            prises.add(d.espece);
            pistes.add(d);
        }
        if (pistes.isEmpty()) {
            annonce(joueur, "aucun_signalement", 0xF2C93A);
            return;
        }
        if (voulu == 1) {
            engager(joueur, pistes.get(0));
            return;
        }
        Collections.shuffle(pistes, HASARD);
        etat.offres.put(id, pistes);
        etat.finOffres.put(id, System.currentTimeMillis() + Math.max(1, reglages.minutesValiditePistes) * 60_000L);
        sauver();
        joueur.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        synchroniser(joueur);
    }

    // Signalements en attente de choix : ils tombent s'ils ont expiré ou si le joueur a changé de dimension.
    private static List<Dossier> pistes(ServerPlayer joueur) {
        UUID id = joueur.getUUID();
        List<Dossier> pistes = etat.offres.get(id);
        if (pistes == null) return null;
        boolean valides = !pistes.isEmpty() && etat.finOffres.getOrDefault(id, 0L) > System.currentTimeMillis();
        for (Dossier d : pistes) valides &= d.monde != null && d.monde.equals(dimension(joueur));
        if (valides) return pistes;
        etat.offres.remove(id);
        etat.finOffres.remove(id);
        sauver();
        return null;
    }

    // Le joueur retient l'un des signalements proposés : c'est son enquête.
    static void choisir(ServerPlayer joueur, int index) {
        List<Dossier> pistes = dossier(joueur) == null ? pistes(joueur) : null;
        if (pistes == null || index < 0 || index >= pistes.size()) {
            synchroniser(joueur);
            return;
        }
        engager(joueur, pistes.get(index));
    }

    private static void engager(ServerPlayer joueur, Dossier d) {
        UUID id = joueur.getUUID();
        etat.offres.remove(id);
        etat.finOffres.remove(id);
        etat.dossiers.put(id, d);
        sauver();
        joueur.playNotifySound(SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1f, 0.8f);
        annonce(joueur, d.special == null ? "nouvelle_enquete" : "enquete_" + d.special, 0xF2C93A);
        synchroniser(joueur);
    }

    // Tire un signalement autour du joueur : une zone de traces, une tanière, une espèce du biome et ses suspects.
    private static Dossier signalement(ServerPlayer joueur, ServerLevel monde, String dim, PokedexManager pokedex, Set<String> prises, String special) {
        for (int essai = 0; essai < 60; essai++) {
            double a1 = HASARD.nextDouble() * Math.PI * 2;
            double d1 = reglages.zoneDistanceMin + HASARD.nextDouble() * (reglages.zoneDistanceMax - reglages.zoneDistanceMin);
            double zx = joueur.getX() + Math.cos(a1) * d1, zz = joueur.getZ() + Math.sin(a1) * d1;
            if (aquatique(monde, zx, zz)) continue;
            double a2 = HASARD.nextDouble() * Math.PI * 2;
            double d2 = reglages.taniereDistanceMin + HASARD.nextDouble() * (reglages.taniereDistanceMax - reglages.taniereDistanceMin);
            double tx = zx + Math.cos(a2) * d2, tz = zz + Math.sin(a2) * d2;
            if (aquatique(monde, tx, tz)) continue;
            Holder<Biome> biome = monde.getBiome(BlockPos.containing(tx, 100, tz));
            List<Candidat> bassin = bassin(biome, monde.dimension().location());
            bassin.removeIf(c -> prises.contains(c.espece)); // jamais deux signalements pour la même espèce
            if (bassin.size() < 3) continue;
            double total = 0;
            double[] poids = new double[bassin.size()];
            for (int i = 0; i < bassin.size(); i++) {
                Candidat c = bassin.get(i);
                poids[i] = reglages.poidsCategories.getOrDefault(CATEGORIES.get(c.rang), 1.0) * (possede(pokedex, c.espece) ? 1.0 : reglages.bonusNouvelleEspece);
                total += poids[i];
            }
            double tirage = HASARD.nextDouble() * total;
            Candidat cible = bassin.get(bassin.size() - 1);
            for (int i = 0; i < bassin.size(); i++) {
                tirage -= poids[i];
                if (tirage <= 0) {
                    cible = bassin.get(i);
                    break;
                }
            }
            Dossier d = new Dossier();
            d.id = HASARD.nextLong();
            d.monde = dim;
            d.special = special;
            d.espece = cible.espece;
            d.categorie = CATEGORIES.get(cible.rang);
            d.nouvelle = !possede(pokedex, cible.espece);
            d.graine = HASARD.nextLong();
            d.niveau = cible.niveauMin + HASARD.nextInt(Math.max(1, cible.niveauMax - cible.niveauMin + 1));
            d.zoneX = zx;
            d.zoneZ = zz;
            d.taniereX = tx;
            d.taniereZ = tz;
            for (String m : Genetique.MARQUEURS) {
                Trace t = new Trace();
                t.genre = m;
                for (int k = 0; k < 20; k++) {
                    double a = HASARD.nextDouble() * Math.PI * 2, r = 6 + HASARD.nextDouble() * (reglages.rayonZone - 6);
                    t.x = Math.floor(zx + Math.cos(a) * r) + 0.5;
                    t.z = Math.floor(zz + Math.sin(a) * r) + 0.5;
                    boolean libre = true;
                    for (Trace autre : d.traces) libre &= Math.hypot(autre.x - t.x, autre.z - t.z) >= 12;
                    if (libre) break;
                }
                d.traces.add(t);
            }
            // Un à trois indices sont enfouis (os, coquilles, croc) : au moins un par enquête.
            List<Trace> enfouissables = new ArrayList<>();
            for (Trace t : d.traces) {
                if (Genetique.NOMS_ENFOUIS.containsKey(t.genre)) enfouissables.add(t);
            }
            Collections.shuffle(enfouissables, HASARD);
            for (int i = 0; i < enfouissables.size(); i++) enfouissables.get(i).enfoui = i == 0 || HASARD.nextInt(3) == 0;
            d.suspects = suspects(cible.espece, bassin, "exceptionnel".equals(special));
            return d;
        }
        return null;
    }

    private static boolean aquatique(ServerLevel monde, double x, double z) {
        String nom = monde.getBiome(BlockPos.containing(x, 100, z)).unwrapKey().map(k -> k.location().getPath()).orElse("");
        return nom.contains("ocean") || nom.contains("river");
    }

    private static boolean possede(PokedexManager pokedex, String espece) {
        Species s = PokemonSpecies.getByName(espece);
        return s != null && pokedex.getHighestKnowledgeForSpecies(s.getResourceIdentifier()) == PokedexEntryProgress.OWNED;
    }

    // Espèces terrestres de surface que Cobblemon fait apparaître dans ce biome (null = partout).
    private static List<Candidat> bassin(Holder<Biome> biome, ResourceLocation dimension) {
        Map<String, Candidat> resultat = new HashMap<>();
        for (SpawnDetail detail : CobblemonSpawnPools.WORLD_SPAWN_POOL.getDetails()) {
            if (!(detail instanceof PokemonSpawnDetail pokemon)) continue;
            int rang = CATEGORIES.indexOf(detail.getBucket());
            if (rang < 0 || !"grounded".equals(detail.getSpawnablePositionType().getName())) continue;
            if (pokemon.getPokemon().getForm() != null || (biome != null && !convient(detail, biome, dimension))) continue;
            String nom = pokemon.getPokemon().getSpecies();
            if (nom == null) continue;
            nom = nom.substring(nom.indexOf(':') + 1).toLowerCase();
            Species espece = PokemonSpecies.getByName(nom);
            if (espece == null || !espece.getImplemented()) continue;
            if (espece.getLabels().stream().anyMatch(reglages.etiquettesInterdites::contains)) continue;
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

    private static boolean convient(SpawnDetail detail, Holder<Biome> biome, ResourceLocation dimension) {
        boolean ok = false;
        for (SpawningCondition<?> c : detail.getConditions()) {
            Set<RegistryLikeCondition<Biome>> biomes = c.getBiomes();
            if (biomes == null || biomes.isEmpty()) continue;
            if (Boolean.FALSE.equals(c.getCanSeeSky()) || (c.getMaxY() != null && c.getMaxY() < 62)) continue;
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
    private static List<String> suspects(String cible, List<Candidat> locaux, boolean serre) {
        Species s = PokemonSpecies.getByName(cible);
        Set<String> duCoin = new HashSet<>();
        for (Candidat c : locaux) duCoin.add(c.espece);
        Map<String, Double> notes = new HashMap<>();
        for (Candidat c : bassin(null, null)) {
            if (c.espece.equals(cible)) continue;
            Species autre = PokemonSpecies.getByName(c.espece);
            int communs = Genetique.communs(s, autre);
            if (communs >= Genetique.MARQUEURS.size()) continue;
            notes.put(c.espece, communs + (duCoin.contains(c.espece) ? 0.6 : 0) + HASARD.nextDouble() * 1.2);
        }
        List<String> tries = new ArrayList<>(notes.keySet());
        tries.sort((a, b) -> Double.compare(notes.get(b), notes.get(a)));
        int proches = serre ? Math.max(0, reglages.nombreSuspects - 1) : Math.max(0, reglages.nombreSuspects - 1) * 2 / 3;
        List<String> liste = new ArrayList<>(tries.subList(0, Math.min(proches, tries.size())));
        List<String> reste = new ArrayList<>(tries.subList(liste.size(), tries.size()));
        Collections.shuffle(reste, HASARD);
        while (liste.size() < reglages.nombreSuspects - 1 && !reste.isEmpty()) liste.add(reste.remove(0));
        liste.add(cible);
        Collections.shuffle(liste, HASARD);
        return liste;
    }

    // ---------- 2. Prélever ----------

    public static void prelever(ServerPlayer joueur, BlockPos pos) {
        Dossier d = dossier(joueur);
        ServerLevel monde = joueur.serverLevel();
        Trace trace = null;
        if (d != null && "PRELEVEMENTS".equals(d.phase)) {
            for (Trace t : d.traces) {
                if (t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
            }
        }
        if (trace == null) {
            boolean aQuelquun = false;
            for (Dossier autre : etat.dossiers.values()) {
                for (Trace t : autre.traces) aQuelquun |= t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ();
            }
            if (aQuelquun) {
                annonce(joueur, "trace_autre", 0xF2C93A);
            } else {
                monde.removeBlock(pos, false);
                annonce(joueur, "trace_ancienne", 0x9AA0A6);
            }
            return;
        }
        if (dort(d, monde)) {
            annonce(joueur, "nocturne_trace", 0xF2C93A);
            return;
        }
        if (joueur.getInventory().findSlotMatchingItem(new ItemStack(Contenu.FIOLE)) < 0 && !joueur.isCreative()) {
            annonce(joueur, "fiole_requise", 0xE8543A);
            return;
        }
        PRELEVEMENTS.put(joueur.getUUID(), pos.immutable());
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", "prelevement");
        p.donnees().putString("genre", trace.genre);
        p.donnees().putString("jeu", Genetique.jeuPrelevement(trace.genre, trace.enfoui));
        p.donnees().putBoolean("enfoui", trace.enfoui);
        p.donnees().putLong("graine", HASARD.nextLong());
        envoyer(joueur, p);
    }

    // Résultat du mini-jeu de prélèvement : réussi = échantillon complet (une dose de plus si sans faute), raté = une seule dose.
    static void resultatPrelevement(ServerPlayer joueur, boolean succes, boolean parfait) {
        BlockPos pos = PRELEVEMENTS.remove(joueur.getUUID());
        Dossier d = dossier(joueur);
        if (pos == null || d == null || !"PRELEVEMENTS".equals(d.phase)) return;
        ServerLevel monde = joueur.serverLevel();
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.posee && !t.recolte && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null || joueur.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        int fiole = joueur.getInventory().findSlotMatchingItem(new ItemStack(Contenu.FIOLE));
        if (fiole < 0 && !joueur.isCreative()) return;
        if (fiole >= 0 && !joueur.isCreative()) joueur.getInventory().getItem(fiole).shrink(1);
        int doses = succes ? reglages.dosesParEchantillon + (parfait ? 1 : 0) : 1;
        ItemStack echantillon = new ItemStack(Contenu.ECHANTILLON);
        CompoundTag donnees = new CompoundTag();
        donnees.putLong("enquete", d.id);
        donnees.putString("genre", trace.genre);
        donnees.putInt("doses", doses);
        donnees.putBoolean("enfoui", trace.enfoui);
        List<String> jeux = Genetique.JEUX_ANALYSE.getOrDefault(trace.genre, List.of("sequencage"));
        donnees.putString("jeu", jeux.get(HASARD.nextInt(jeux.size())));
        echantillon.set(DataComponents.CUSTOM_DATA, CustomData.of(donnees));
        joueur.getInventory().placeItemBackInInventory(echantillon);
        trace.recolte = true;
        if (!succes) d.fautes++;
        monde.removeBlock(pos, false);
        if (trace.enfoui) reboucher(monde, trace);
        sauver();
        joueur.playNotifySound(SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 1.1f);
        progres(joueur, "premier_prelevement");
        long reste = d.traces.stream().filter(t -> !t.finie()).count();
        annonce(joueur, tr("annonce." + (succes ? (parfait ? "prelevement_parfait" : "prelevement_ok") : "prelevement_abime") + ".titre"),
                tr("annonce.prelevement.texte", Component.translatable(Genetique.nomTrace(trace.genre, trace.enfoui)), doses,
                        reste > 0 ? tr("annonce.prelevement.reste", reste) : tr("annonce.prelevement.fini")), succes ? 0x6CCB5F : 0xF2C93A);
        synchroniser(joueur);
    }

    // Où poser la trace : au sol par défaut, mais les griffures vont sur les troncs (ou une paroi), certains dépôts sur les
    // parois rocheuses, et un nid sur deux se trouve en haut d'un arbre. Le choix est fixe pour une trace donnée.
    private static void poser(ServerLevel monde, Dossier d, Trace t) {
        int bx = (int) Math.floor(t.x), bz = (int) Math.floor(t.z);
        if (!monde.hasChunk(bx >> 4, bz >> 4)) return;
        if (t.posee) {
            BlockState la = monde.getBlockState(new BlockPos(t.px, t.py, t.pz));
            if (la.is(Contenu.TRACE) || (t.enfoui && !t.degage && la.is(Contenu.SOL))) return;
        }
        if (t.enfoui && !t.degage) {
            if (enfouir(monde, t, bx, bz)) return;
            t.enfoui = false; // aucun sol meuble par ici : l'indice affleure, comme une trace ordinaire
        }
        if (t.enfoui) {
            // Déjà dégagé mais disparu (eau, chute de sable...) : on le repose dans son trou.
            BlockPos trou = new BlockPos(t.px, t.py, t.pz);
            if (monde.getBlockState(trou).canBeReplaced()) fixer(monde, t, trou, Direction.UP);
            return;
        }
        Random h = new Random(d.graine + t.genre.hashCode());
        boolean griffures = t.genre.equals("stat");
        boolean paroi = griffures || (t.genre.equals("type2") && h.nextBoolean()) || (t.genre.equals("type1") && h.nextInt(3) == 0);
        boolean cime = t.genre.equals("oeuf") && h.nextBoolean();
        if (griffures && surParoi(monde, t, bx, bz, true)) return;
        if (paroi && surParoi(monde, t, bx, bz, false)) return;
        if (cime && surCime(monde, t, bx, bz)) return;
        // Premier emplacement libre posé sur un bloc plein, en s'écartant en spirale si besoin (eau, feuillage...).
        for (int rayon = 0; rayon <= 20; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int y = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState ici = monde.getBlockState(pos), dessous = monde.getBlockState(pos.below());
                    if (!ici.canBeReplaced() || !ici.getFluidState().isEmpty() || !dessous.isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                    fixer(monde, t, pos, Direction.UP);
                    return;
                }
            }
        }
        // Rien de praticable à vingt blocs à la ronde (lac, marais...) : la trace est déplacée ailleurs dans la zone
        // et sera posée au prochain passage. Sans cela, le kit guiderait vers un endroit où il n'y a rien.
        double a = HASARD.nextDouble() * Math.PI * 2, r = 6 + HASARD.nextDouble() * (reglages.rayonZone - 6);
        t.x = Math.floor(d.zoneX + Math.cos(a) * r) + 0.5;
        t.z = Math.floor(d.zoneZ + Math.sin(a) * r) + 0.5;
        t.posee = false;
        sauver();
    }

    private static int matiere(BlockState sol) {
        return sol.is(BlockTags.DIRT) ? 0 : sol.is(BlockTags.SAND) ? 1 : sol.is(Blocks.GRAVEL) ? 2 : -1;
    }

    // Cache l'indice sous la surface : un bloc de sol meuble (terre, sable, gravier), recouvert d'un ou deux blocs,
    // est remplacé par un sol remué. Il faut creuser pour le trouver, puis le dégager au pinceau.
    private static boolean enfouir(ServerLevel monde, Trace t, int bx, int bz) {
        for (int rayon = 0; rayon <= 16; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int surface = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockState dessus = monde.getBlockState(new BlockPos(x, surface + 1, z));
                    if (!dessus.canBeReplaced() || !dessus.getFluidState().isEmpty()) continue;
                    for (int profondeur = 2; profondeur >= 1; profondeur--) {
                        BlockPos pos = new BlockPos(x, surface - profondeur, z);
                        BlockState sol = monde.getBlockState(pos);
                        boolean meuble = matiere(sol) >= 0;
                        for (int y = pos.getY() + 1; y <= surface && meuble; y++) meuble = matiere(monde.getBlockState(new BlockPos(x, y, z))) >= 0;
                        if (!meuble || !monde.getBlockState(pos.below()).isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                        t.origine = BuiltInRegistries.BLOCK.getKey(sol.getBlock()).toString();
                        monde.setBlock(pos, Contenu.SOL.defaultBlockState().setValue(Contenu.BlocSolRemue.MATIERE, matiere(sol)), 3);
                        t.px = x;
                        t.py = pos.getY();
                        t.pz = z;
                        t.x = x + 0.5;
                        t.z = z + 0.5;
                        t.posee = true;
                        t.brossage = 0;
                        sauver();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Le joueur brosse (pinceau maintenu) un sol remué de son enquête : quatre étapes, puis l'indice apparaît dans le trou.
    private static void brosser(ServerPlayer joueur, Dossier d, ServerLevel monde) {
        HitResult vise = joueur.pick(5.0, 0f, false);
        if (!(vise instanceof BlockHitResult coup) || vise.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = coup.getBlockPos();
        BlockState etat = monde.getBlockState(pos);
        if (!etat.is(Contenu.SOL)) return;
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.enfoui && !t.degage && !t.finie() && t.posee && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null) return;
        trace.brossage++;
        int etape = Math.min(3, trace.brossage / 2);
        monde.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.0);
        if (trace.brossage < 8) {
            if (etat.getValue(Contenu.BlocSolRemue.DEGAGE) != etape) {
                remplacement = true;
                monde.setBlock(pos, etat.setValue(Contenu.BlocSolRemue.DEGAGE, etape), 3);
                remplacement = false;
            }
            return;
        }
        remplacement = true;
        monde.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        remplacement = false;
        trace.degage = true;
        fixer(monde, trace, pos, Direction.UP);
        joueur.playNotifySound(SoundEvents.BRUSH_SAND_COMPLETED, SoundSource.PLAYERS, 1f, 1f);
        progres(joueur, "indice_degage");
        annonce(joueur, "indice_degage", 0x6CCB5F, Component.translatable(Genetique.nomTrace(trace.genre, true)));
        synchroniser(joueur);
    }

    // Un sol remué a été cassé (pioche, pelle, explosion, piston...) : l'indice qu'il cachait est perdu.
    public static void solDetruit(Level monde, BlockPos pos) {
        if (remplacement || !(monde instanceof ServerLevel serveur)) return;
        String dim = monde.dimension().location().toString();
        for (Map.Entry<UUID, Dossier> en : etat.dossiers.entrySet()) {
            Dossier d = en.getValue();
            if (!dim.equals(d.monde)) continue;
            for (Trace t : d.traces) {
                if (!t.enfoui || t.degage || t.finie() || !t.posee || t.px != pos.getX() || t.py != pos.getY() || t.pz != pos.getZ()) continue;
                t.perdue = true;
                t.posee = false;
                d.fautes++;
                sauver();
                ServerPlayer proprietaire = serveur.getServer().getPlayerList().getPlayer(en.getKey());
                if (proprietaire != null) {
                    proprietaire.playNotifySound(SoundEvents.TURTLE_EGG_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
                    annonce(proprietaire, "indice_detruit", 0xE8543A, Component.translatable(Genetique.nomTrace(t.genre, true)));
                    synchroniser(proprietaire);
                }
                return;
            }
        }
    }

    // Remet le bloc d'origine à la place d'un indice enfoui (une fois prélevé, ou quand l'enquête se termine).
    private static void reboucher(ServerLevel monde, Trace t) {
        BlockPos pos = new BlockPos(t.px, t.py, t.pz);
        BlockState la = monde.getBlockState(pos);
        if (!la.is(Contenu.SOL) && !la.is(Contenu.TRACE) && !la.isAir()) return;
        var bloc = t.origine == null ? java.util.Optional.<net.minecraft.world.level.block.Block>empty()
                : BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(t.origine));
        remplacement = true;
        monde.setBlock(pos, bloc.map(b -> b.defaultBlockState()).orElse(Blocks.DIRT.defaultBlockState()), 3);
        remplacement = false;
    }

    private static void fixer(ServerLevel monde, Trace t, BlockPos pos, Direction appui) {
        int genre = Genetique.MARQUEURS.indexOf(t.genre);
        if (t.enfoui) genre = t.genre.equals("taille") ? 5 : t.genre.equals("oeuf") ? 6 : 7;
        monde.setBlock(pos, Contenu.TRACE.defaultBlockState().setValue(Contenu.BlocTrace.GENRE, genre)
                .setValue(Contenu.BlocTrace.APPUI, appui), 3);
        t.px = pos.getX();
        t.py = pos.getY();
        t.pz = pos.getZ();
        t.x = pos.getX() + 0.5;
        t.z = pos.getZ() + 0.5;
        t.posee = true;
        sauver();
    }

    // Plaquée à hauteur de regard contre un tronc (troncs = true) ou contre une paroi pleine d'au moins deux blocs de haut.
    private static boolean surParoi(ServerLevel monde, Trace t, int bx, int bz, boolean troncs) {
        int[] hauteurs = {1, 0, 2};
        for (int rayon = 0; rayon <= 9; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int sol = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    if (!monde.getBlockState(new BlockPos(x, sol - 1, z)).isFaceSturdy(monde, new BlockPos(x, sol - 1, z), Direction.UP)) continue;
                    for (int dy : hauteurs) {
                        BlockPos pos = new BlockPos(x, sol + dy, z);
                        BlockState ici = monde.getBlockState(pos);
                        if (!ici.canBeReplaced() || !ici.getFluidState().isEmpty()) continue;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos derriere = pos.relative(dir);
                            if (!monde.hasChunk(derriere.getX() >> 4, derriere.getZ() >> 4)) continue;
                            BlockState appui = monde.getBlockState(derriere);
                            boolean ok;
                            if (troncs) {
                                ok = appui.is(BlockTags.LOGS);
                            } else {
                                ok = !appui.is(BlockTags.LOGS) && !appui.is(BlockTags.LEAVES) && appui.isFaceSturdy(monde, derriere, dir.getOpposite())
                                        && monde.getBlockState(derriere.above()).isFaceSturdy(monde, derriere.above(), dir.getOpposite());
                            }
                            if (!ok) continue;
                            fixer(monde, t, pos, dir.getOpposite());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Posée sur le feuillage, au sommet d'un arbre.
    private static boolean surCime(ServerLevel monde, Trace t, int bx, int bz) {
        for (int rayon = 0; rayon <= 9; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = monde.getChunk(x >> 4, z >> 4);
                    int haut = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
                    int sol = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos pos = new BlockPos(x, haut + 1, z);
                    if (haut - sol < 3 || !monde.getBlockState(pos.below()).is(BlockTags.LEAVES) || !monde.getBlockState(pos).canBeReplaced()) continue;
                    fixer(monde, t, pos, Direction.UP);
                    return true;
                }
            }
        }
        return false;
    }

    private static void retirerTraces(ServerLevel monde, Dossier d) {
        for (Trace t : d.traces) {
            if (!t.posee || t.finie()) continue;
            BlockPos pos = new BlockPos(t.px, t.py, t.pz);
            if (monde.hasChunk(t.px >> 4, t.pz >> 4)) {
                if (t.enfoui) reboucher(monde, t);
                else if (monde.getBlockState(pos).is(Contenu.TRACE)) monde.removeBlock(pos, false);
            }
            t.posee = false;
        }
    }

    // ---------- 4. Lancer la traque ----------

    static void lancer(ServerPlayer joueur, String pronostic) {
        Dossier d = dossier(joueur);
        if (d == null || !"PRELEVEMENTS".equals(d.phase) || d.epingles.size() < requis(d)) return;
        Species cible = PokemonSpecies.getByName(d.espece), choix = PokemonSpecies.getByName(pronostic);
        // Le joueur peut désigner n'importe quel suspect qu'il n'a pas barré, même si les analyses le contredisent :
        // se tromper lui coûtera des étoiles.
        if (cible == null || choix == null || !d.suspects.contains(pronostic) || (d.ecartes != null && d.ecartes.contains(pronostic))) return;
        d.pronostic = pronostic;
        d.phase = "TRAQUE";
        d.prochainDeplacement = reglages.minutesAvantDeplacement > 0 ? System.currentTimeMillis() + reglages.minutesAvantDeplacement * 60_000L : 0;
        if (d.monde.equals(dimension(joueur))) retirerTraces(joueur.serverLevel(), d);
        sauver();
        joueur.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.3f);
        annonce(joueur, "kit_calibre", 0x4FD1C5);
        synchroniser(joueur);
    }

    static void abandonner(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d == null) return;
        PokemonEntity e = ENTITES.remove(joueur.getUUID());
        if (e != null && !e.isBattling() && ("APPROCHE".equals(d.phase) || "REPERAGE".equals(d.phase))) e.discard();
        finir(joueur, d, tr("annonce.classee.titre"), tr("annonce.classee.texte"), 0x9AA0A6);
    }

    // ---------- 5. Kit de terrain ----------

    public static void utiliserKit(ServerPlayer joueur, boolean accroupi) {
        Dossier d = dossier(joueur);
        if (d == null) {
            ouvrir(joueur, "tableau");
            return;
        }
        if ("TRAQUE".equals(d.phase) && !accroupi && d.monde.equals(dimension(joueur))) releve(joueur, d);
        else ouvrir(joueur, "kit");
    }

    // La cible est apparue mais n'est pas encore identifiée : le kit sert alors de scanner (clic droit maintenu).
    public static boolean enReperage(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        return d != null && "REPERAGE".equals(d.phase) && d.monde.equals(dimension(joueur));
    }

    private static void releve(ServerPlayer joueur, Dossier d) {
        long maintenant = System.currentTimeMillis();
        if (maintenant < d.prochainReleve) return;
        if (dort(d, joueur.serverLevel())) {
            annonce(joueur, "nocturne_silence", 0xF2C93A);
            return;
        }
        Releve dernier = d.releves.isEmpty() ? null : d.releves.get(d.releves.size() - 1);
        if (dernier != null && !dernier.perime && distance(joueur, dernier.x, dernier.z) < reglages.distanceEntreReleves) {
            annonce(joueur, "meme_ecoute", 0xF2C93A, reglages.distanceEntreReleves);
            return;
        }
        d.prochainReleve = maintenant + reglages.secondesEntreReleves * 1000L;
        joueur.getCooldowns().addCooldown(Contenu.KIT, reglages.secondesEntreReleves * 20);
        double dx = d.taniereX - joueur.getX(), dz = d.taniereZ - joueur.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double vrai = Math.toDegrees(Math.atan2(dx, -dz));
        double erreur = Math.max(reglages.erreurMinDegres, Math.min(reglages.erreurMaxDegres, dist / 22));
        // Le bruit dépend de l'endroit (et pas du moment) : refaire le relevé au même endroit ne l'améliore pas.
        Random bruit = new Random(d.graine ^ (((long) joueur.getBlockX() >> 3) * 73428767L) ^ (((long) joueur.getBlockZ() >> 3) * 912931L) ^ d.fuites);
        Releve r = new Releve();
        r.x = joueur.getX();
        r.z = joueur.getZ();
        r.cap = ((vrai + (bruit.nextDouble() * 2 - 1) * erreur) % 360 + 360) % 360;
        r.erreur = erreur;
        d.releves.add(r);
        sauver();

        double rad = Math.toRadians(r.cap);
        double ux = Math.sin(rad), uz = -Math.cos(rad);
        float volume = (float) Math.max(0.25, Math.min(1.0, 1.25 - dist / 320));
        SoundEvent cri = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon." + d.espece + ".cry"));
        joueur.connection.send(new ClientboundSoundPacket(Holder.direct(cri), SoundSource.NEUTRAL,
                joueur.getX() + ux * 14, joueur.getEyeY(), joueur.getZ() + uz * 14, volume * 2.5f, 1f, HASARD.nextLong()));
        Paquet p = Paquet.de("releve");
        p.donnees().putDouble("cap", r.cap);
        p.donnees().putDouble("erreur", erreur);
        p.donnees().putInt("force", dist > 260 ? 0 : dist > 170 ? 1 : dist > 100 ? 2 : 3);
        envoyer(joueur, p);
        synchroniser(joueur);
    }

    // Recoupement des relevés valides (moindres carrés pondérés) : position estimée et incertitude.
    private static Recoupement recouper(Dossier d) {
        List<Releve> valides = new ArrayList<>();
        for (Releve r : d.releves) {
            if (!r.perime) valides.add(r);
        }
        if (valides.size() < 2) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.deux");
        double ex = 0, ez = 0, incertitude = 0;
        for (int passe = 0; passe < 2; passe++) {
            double a11 = 0, a12 = 0, a22 = 0, b1 = 0, b2 = 0;
            for (Releve r : valides) {
                double rad = Math.toRadians(r.cap);
                double nx = Math.cos(rad), nz = Math.sin(rad);
                double w = 1;
                if (passe == 1) {
                    double sigma = Math.max(8, Math.hypot(ex - r.x, ez - r.z) * Math.toRadians(r.erreur) * 0.6);
                    w = 1 / (sigma * sigma);
                }
                double np = nx * r.x + nz * r.z;
                a11 += w * nx * nx;
                a12 += w * nx * nz;
                a22 += w * nz * nz;
                b1 += w * nx * np;
                b2 += w * nz * np;
            }
            double det = a11 * a22 - a12 * a12;
            if (passe == 0 && det < 0.03) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.alignes");
            if (Math.abs(det) < 1e-12) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.impossible");
            ex = (a22 * b1 - a12 * b2) / det;
            ez = (a11 * b2 - a12 * b1) / det;
            if (passe == 1) incertitude = 2.5 * Math.sqrt((a11 + a22) / det);
        }
        int devant = 0;
        for (Releve r : valides) {
            double rad = Math.toRadians(r.cap);
            if ((ex - r.x) * Math.sin(rad) - (ez - r.z) * Math.cos(rad) > 0) devant++;
        }
        if (devant * 2 <= valides.size()) return new Recoupement(false, 0, 0, 0, "cobblemon_field_investigations.rec.contredisent");
        return new Recoupement(true, ex, ez, Math.max(10, incertitude), "cobblemon_field_investigations.rec.ok");
    }

    private static void deplacer(Dossier d, double min, double max) {
        double a = HASARD.nextDouble() * Math.PI * 2, dist = min + HASARD.nextDouble() * (max - min);
        d.taniereX += Math.cos(a) * dist;
        d.taniereZ += Math.sin(a) * dist;
        for (Releve r : d.releves) r.perime = true;
        d.prochainDeplacement = reglages.minutesAvantDeplacement > 0 ? System.currentTimeMillis() + reglages.minutesAvantDeplacement * 60_000L : 0;
        d.annonceApproche = false;
    }

    // ---------- Suivi (deux fois par seconde) ----------

    static void suivre(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d == null || !d.monde.equals(dimension(joueur))) return;
        boolean kit = joueur.isHolding(Contenu.KIT);
        long maintenant = System.currentTimeMillis();
        ServerLevel monde = joueur.serverLevel();
        Paquet signal = Paquet.de("signal");
        signal.donnees().putString("phase", d.phase);
        switch (d.phase) {
            case "PRELEVEMENTS": {
                boolean jour = dort(d, monde);
                double min = Double.MAX_VALUE;
                Trace proche = null;
                for (Trace t : d.traces) {
                    if (t.finie()) continue;
                    double dist = distance(joueur, t.x, t.z);
                    if (dist < 56 && !jour) poser(monde, d, t);
                    if (dist < min) {
                        min = dist;
                        proche = t;
                    }
                    // Les traces sont discrètes : kit en main, celles qui sont à portée scintillent (pour ce joueur seulement).
                    boolean cache = t.enfoui && !t.degage;
                    if (kit && !jour && t.posee && dist < 9 && (!cache || monde.getBlockState(new BlockPos(t.px, t.py + 1, t.pz)).isAir())) {
                        monde.sendParticles(joueur, ParticleTypes.WAX_OFF, false, t.px + 0.5, t.py + (cache ? 1.15 : 0.4), t.pz + 0.5,
                                2, 0.25, 0.1, 0.25, 0.0);
                    }
                }
                if (joueur.isUsingItem() && joueur.getUseItem().is(Items.BRUSH)) brosser(joueur, d, monde);
                if (!kit) return;
                signal.donnees().putBoolean("jour", jour);
                signal.donnees().putInt("niveau", min == Double.MAX_VALUE ? -1 : min > 90 ? 0 : min > 60 ? 1 : min > 35 ? 2 : min > 14 ? 3 : min > 5 ? 4 : 5);
                boolean sousTerre = proche != null && proche.enfoui && !proche.degage;
                signal.donnees().putBoolean("enfoui", sousTerre);
                // Pour un indice enfoui, le kit donne la distance exacte (en trois dimensions) : on creuse au plus près.
                if (sousTerre && proche.posee) {
                    signal.donnees().putDouble("metres", Math.sqrt(joueur.distanceToSqr(proche.px + 0.5, proche.py + 0.5, proche.pz + 0.5)));
                    signal.donnees().putInt("dessous", (int) Math.round(joueur.getY() - (proche.py + 1)));
                }
                signal.donnees().putInt("zone", (int) distance(joueur, d.zoneX, d.zoneZ));
                envoyer(joueur, signal);
                return;
            }
            case "TRAQUE": {
                if (d.prochainDeplacement > 0 && maintenant > d.prochainDeplacement) {
                    deplacer(d, 70, 120);
                    sauver();
                    annonce(joueur, "cible_deplacee", 0xF2C93A);
                    synchroniser(joueur);
                }
                boolean jour = dort(d, monde);
                if (!jour && distance(joueur, d.taniereX, d.taniereZ) < reglages.rayonApproche) {
                    apparaitre(joueur, d, monde);
                } else if (kit) {
                    signal.donnees().putBoolean("jour", jour);
                    signal.donnees().putBoolean("pret", maintenant >= d.prochainReleve);
                    envoyer(joueur, signal);
                }
                return;
            }
            case "REPERAGE":
            case "APPROCHE":
            case "DEBUSQUE": {
                PokemonEntity e = ENTITES.get(joueur.getUUID());
                if (e == null && d.entite != null && monde.getEntity(d.entite) instanceof PokemonEntity retrouve) {
                    e = retrouve; // serveur relancé : le spécimen est toujours là
                    ENTITES.put(joueur.getUUID(), e);
                }
                if (e == null || e.isRemoved() || !e.isAlive()) {
                    ENTITES.remove(joueur.getUUID());
                    boolean morte = e != null && e.getHealth() <= 0f;
                    if (morte && vaincueParLeJoueur(joueur, d, e, maintenant)) {
                        resoudre(joueur, d, false);
                    } else if (dossier(joueur) == d) {
                        // Disparue sans que le joueur l'ait vaincue ni capturée (trop loin, serveur relancé, accident, autre
                        // créature...) : l'enquête continue, sans pénalité ni butin. Si elle est morte, elle est repérée ailleurs.
                        d.phase = "TRAQUE";
                        d.annonceApproche = false;
                        d.scan = 0;
                        if (morte) deplacer(d, 60, 110);
                        sauver();
                        annonce(joueur, morte ? "cible_disparue" : "contact_perdu", 0xF2C93A);
                        synchroniser(joueur);
                    }
                    return;
                }
                double dist = Math.sqrt(joueur.distanceToSqr(e));
                boolean reperage = "REPERAGE".equals(d.phase);
                if (e.isBattling() && dist < 64) d.dernierCombat = maintenant;
                if (reperage || "APPROCHE".equals(d.phase)) {
                    // La cible file si on l'approche avant de l'avoir identifiée, ou en courant une fois identifiée.
                    boolean senti = reperage && dist < alerte(d);
                    boolean entendu = !reperage && dist < alerte(d) && joueur.isSprinting();
                    if ((senti || entendu) && !e.isBattling()) {
                        e.discard();
                        ENTITES.remove(joueur.getUUID());
                        d.phase = "TRAQUE";
                        d.fuites++;
                        d.scan = 0;
                        deplacer(d, 80, 130);
                        sauver();
                        joueur.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1.4f);
                        annonce(joueur, senti ? "senti" : "entendu", 0xE8543A, alerte(d), etoilesTexte(etoiles(d, false)));
                        synchroniser(joueur);
                        return;
                    }
                    if (reperage) {
                        if (e.isBattling()) d.scan = 6; // combat engagé de loin : la cible est de fait identifiée
                        else if (joueur.isUsingItem() && joueur.getUseItem().is(Contenu.KIT)) scanner(joueur, d, monde, e);
                        else d.scan = Math.max(0, d.scan - 1);
                        if (d.scan >= 6) {
                            d.phase = "APPROCHE";
                            d.scan = 0;
                            e.setInvulnerable(false);
                            sauver();
                            joueur.stopUsingItem();
                            // Validation du scan : deux notes claires.
                            joueur.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.5f);
                            joueur.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.3f);
                            boolean juste = d.espece.equals(d.pronostic);
                            if (juste) progres(joueur, "bon_flair");
                            annonce(joueur, tr("annonce.identifie.titre", etoilesTexte(etoiles(d))),
                                    tr("annonce.identifie.texte", nomEspece(d.espece), tr(juste ? "annonce.identifie.juste" : "annonce.identifie.faux"), ivParfaits(etoiles(d))), 0xF2C93A);
                            synchroniser(joueur);
                            return;
                        }
                        signal.donnees().putInt("distance", (int) Math.round(dist));
                        signal.donnees().putInt("scan", d.scan);
                        signal.donnees().putBoolean("autre", d.scanAutre > 0);
                        signal.donnees().putInt("alerte", alerte(d));
                        envoyer(joueur, signal);
                        return;
                    }
                    if (dist < reglages.rayonDebusque || e.isBattling()) {
                        d.phase = "DEBUSQUE";
                        d.finLueur = maintenant + 12_000;
                        e.setGlowingTag(true);
                        sauver();
                        joueur.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1f);
                        annonce(joueur, "debusque", 0xF2C93A, nomEspece(d.espece), ivParfaits(etoiles(d)));
                        synchroniser(joueur);
                        return;
                    }
                    signal.donnees().putInt("distance", (int) Math.round(dist));
                    signal.donnees().putBoolean("course", joueur.isSprinting());
                    envoyer(joueur, signal);
                } else if (d.finLueur > 0 && maintenant > d.finLueur) {
                    d.finLueur = 0;
                    e.setGlowingTag(false);
                }
                return;
            }
            default:
        }
    }

    // Une cible K.O. ne vaut victoire que si elle était identifiée et que c'est bien le joueur qui l'a vaincue :
    // en combat Cobblemon juste avant, ou frappée par lui (ou par un de ses Pokémon).
    private static boolean vaincueParLeJoueur(ServerPlayer joueur, Dossier d, PokemonEntity e, long maintenant) {
        if ("REPERAGE".equals(d.phase)) return false;
        if (maintenant - d.dernierCombat < 5000) return true;
        var auteur = e.getKillCredit();
        if (auteur == joueur) return true;
        return auteur instanceof PokemonEntity autre && joueur.getUUID().equals(autre.getOwnerUUID());
    }

    private static boolean vise(ServerPlayer joueur, PokemonEntity e) {
        Vec3 oeil = joueur.getEyePosition(), vers = e.getBoundingBox().getCenter().subtract(oeil);
        double dist = vers.length();
        if (dist < 0.5 || dist > 64) return false;
        double angle = Math.acos(Math.max(-1, Math.min(1, joueur.getLookAngle().dot(vers.normalize()))));
        double tolerance = Math.atan((Math.max(e.getBbWidth(), e.getBbHeight()) / 2 + 0.6) / dist) + Math.toRadians(2.5);
        return angle <= tolerance && joueur.hasLineOfSight(e);
    }

    // Scan au kit (clic droit maintenu, viseur sur le Pokémon) : trois secondes sur la cible pour l'identifier.
    // Un autre Pokémon dans le viseur est simplement signalé comme n'étant pas la cible.
    private static void scanner(ServerPlayer joueur, Dossier d, ServerLevel monde, PokemonEntity cible) {
        if (vise(joueur, cible)) {
            d.scanAutre = 0;
            d.scan++; // les bips sont joués par le client, qui les accélère et les rend plus aigus au fil de l'analyse
            return;
        }
        d.scan = Math.max(0, d.scan - 1);
        boolean autre = false;
        for (PokemonEntity p : monde.getEntitiesOfClass(PokemonEntity.class, joueur.getBoundingBox().inflate(48), x -> x != cible)) {
            if (vise(joueur, p)) {
                autre = true;
                break;
            }
        }
        d.scanAutre = autre ? 3 : Math.max(0, d.scanAutre - 1);
    }

    private static void apparaitre(ServerPlayer joueur, Dossier d, ServerLevel monde) {
        Species s = PokemonSpecies.getByName(d.espece);
        if (s == null) {
            finir(joueur, d, tr("annonce.annulee.titre"), tr("annonce.annulee.texte"), 0xE8543A);
            return;
        }
        Pokemon p = new Pokemon();
        p.setSpecies(s);
        p.setLevel(Math.max(1, d.niveau));
        p.initialize();
        int etoiles = etoiles(d);
        List<Stat> stats = new ArrayList<>(List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));
        Collections.shuffle(stats, HASARD);
        // Mieux l'enquête est menée, meilleur est le Pokémon : une statistique parfaite (IV 31) par étoile au-delà de la première.
        for (int i = 0; i < Math.min(stats.size(), ivParfaits(etoiles)); i++) p.setIV(stats.get(i), 31);
        float taux = Cobblemon.INSTANCE.getConfig().getShinyRate();
        double chroma = (1 + reglages.multChromaParEtoile * etoiles) * (est(d, "nocturne") ? 2 : 1);
        if (taux > 0 && HASARD.nextDouble() < chroma / taux) p.setShiny(true);
        if (est(d, "exceptionnel")) {
            try {
                new HiddenAbilityProperty(true).apply(p); // sans effet si l'espèce n'a pas de talent caché
            } catch (Exception e) {
                System.err.println("[Field Investigations] Talent caché impossible à donner : " + e);
            }
        }
        // Elle apparaît sur la terre ferme : jamais dans l'eau, la lave ou au-dessus du vide.
        BlockPos sol = solFerme(monde, (int) Math.floor(d.taniereX), (int) Math.floor(d.taniereZ));
        if (sol == null) {
            deplacer(d, 40, 80); // rien de praticable ici : elle est repérée un peu plus loin
            sauver();
            synchroniser(joueur);
            return;
        }
        PokemonEntity e = new PokemonEntity(monde, p, CobblemonEntities.POKEMON);
        e.moveTo(sol.getX() + 0.5, sol.getY(), sol.getZ() + 0.5, HASARD.nextFloat() * 360f, 0f);
        // Tant qu'elle n'est pas identifiée, rien ne peut la blesser (chute, noyade, autre créature).
        e.setInvulnerable(true);
        monde.addFreshEntity(e);
        ENTITES.put(joueur.getUUID(), e);
        d.pokemon = p.getUuid();
        d.entite = e.getUUID();
        d.phase = "REPERAGE";
        d.scan = 0;
        sauver();
        if (!d.annonceApproche) {
            d.annonceApproche = true;
            joueur.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.6f);
            annonce(joueur, "tout_pres", 0xF2C93A, alerte(d));
        }
        synchroniser(joueur);
    }

    private static BlockPos solFerme(ServerLevel monde, int bx, int bz) {
        for (int rayon = 0; rayon <= 10; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int y = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!monde.getBlockState(pos.below()).isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                    if (!monde.getBlockState(pos).getFluidState().isEmpty() || !monde.getBlockState(pos.above()).getFluidState().isEmpty()) continue;
                    if (!monde.getBlockState(pos).getCollisionShape(monde, pos).isEmpty()) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    // ---------- Fin ----------

    static void capture(PokemonCapturedEvent evt) {
        UUID capture = evt.getPokemon().getUuid();
        for (Map.Entry<UUID, Dossier> en : new ArrayList<>(etat.dossiers.entrySet())) {
            Dossier d = en.getValue();
            if (!capture.equals(d.pokemon)) continue;
            ServerPlayer chasseur = evt.getPlayer().server.getPlayerList().getPlayer(en.getKey());
            ENTITES.remove(en.getKey());
            if (chasseur == null) {
                etat.dossiers.remove(en.getKey());
                sauver();
            } else if (chasseur == evt.getPlayer()) {
                resoudre(chasseur, d, true);
            } else {
                finir(chasseur, d, tr("annonce.trop_tard.titre"), tr("annonce.trop_tard.texte"), 0xE8543A);
            }
            return;
        }
    }

    // Enquête menée à son terme (cible capturée ou mise K.O.) : le butin est tiré dans les tables de butin du mod.
    // Plus l'enquête a d'étoiles, plus la table est fournie ; la rareté de l'espèce et les paliers ajoutent leur part.
    private static void resoudre(ServerPlayer joueur, Dossier d, boolean capture) {
        int total = etat.reussies.merge(joueur.getUUID(), 1, Integer::sum);
        int etoiles = etoiles(d);
        boolean palier = reglages.palierBonus > 0 && total % reglages.palierBonus == 0;
        List<ItemStack> gains = new ArrayList<>(butin(joueur, "traque/etoiles_" + etoiles));
        gains.addAll(butin(joueur, "traque/rarete_" + d.categorie.replace('-', '_')));
        if (palier) gains.addAll(butin(joueur, "traque/palier"));
        if (d.special != null) {
            gains.addAll(butin(joueur, "traque/speciale"));
            if (est(d, "craintive")) gains.addAll(butin(joueur, "traque/speciale"));
            progres(joueur, "enquete_speciale");
        }
        progres(joueur, "premiere_enquete");
        if (etoiles >= 5) progres(joueur, "cinq_etoiles");
        if (total >= 10) progres(joueur, "dix_enquetes");
        MutableComponent liste = Component.empty();
        boolean premier = true;
        for (ItemStack pile : gains) {
            if (pile.isEmpty()) continue;
            if (!premier) liste.append(", ");
            premier = false;
            liste.append(pile.getCount() + "× ").append(pile.getHoverName());
            joueur.getInventory().placeItemBackInInventory(pile);
        }
        joueur.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        finir(joueur, d, tr("annonce.resolue.titre", etoilesTexte(etoiles)),
                tr("annonce.resolue.texte", nomEspece(d.espece), tr(capture ? "annonce.resolue.capture" : "annonce.resolue.vaincu"), total,
                        palier ? tr("annonce.resolue.palier") : Component.empty(), premier ? Component.empty() : tr("annonce.resolue.butin", liste)), 0x6CCB5F);
    }

    private static List<ItemStack> butin(ServerPlayer joueur, String table) {
        try {
            ResourceKey<LootTable> cle = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", table));
            LootTable t = joueur.server.reloadableRegistries().getLootTable(cle);
            LootParams params = new LootParams.Builder(joueur.serverLevel()).withParameter(LootContextParams.ORIGIN, joueur.position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, joueur).create(LootContextParamSets.CHEST);
            return t.getRandomItems(params);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Table de butin illisible : " + table + " : " + e);
            return List.of();
        }
    }

    private static void finir(ServerPlayer joueur, Dossier d, Component titre, Component texte, int couleur) {
        if (d.monde.equals(dimension(joueur))) retirerTraces(joueur.serverLevel(), d);
        etat.dossiers.remove(joueur.getUUID());
        etat.prochaineEnquete.put(joueur.getUUID(), System.currentTimeMillis() + reglages.minutesEntreEnquetes * 60_000L);
        sauver();
        annonce(joueur, titre, texte, couleur);
        synchroniser(joueur);
    }

    // ---------- Outils de test (op) ----------

    static void reinitialiser(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d != null && d.monde.equals(dimension(joueur))) retirerTraces(joueur.serverLevel(), d);
        etat.dossiers.remove(joueur.getUUID());
        etat.prochaineEnquete.remove(joueur.getUUID());
        etat.offres.remove(joueur.getUUID());
        etat.finOffres.remove(joueur.getUUID());
        ENTITES.remove(joueur.getUUID());
        sauver();
        synchroniser(joueur);
    }

    static String reveler(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d == null) return "No investigation in progress.";
        StringBuilder sb = new StringBuilder("[test] " + d.espece + (d.special == null ? "" : " [" + d.special + "]") + " lvl " + d.niveau + " · phase " + d.phase + " · stars " + etoiles(d) + " (faults " + d.fautes
                + ", flees " + d.fuites + ") · zone X " + Math.round(d.zoneX) + " Z " + Math.round(d.zoneZ) + " · lair X " + Math.round(d.taniereX)
                + " Z " + Math.round(d.taniereZ) + " · traces:");
        for (Trace t : d.traces) sb.append(" ").append(t.genre).append("(").append(Math.round(t.x)).append(", ").append(Math.round(t.z)).append(t.recolte ? ", ok)" : t.perdue ? ", lost)" : t.enfoui ? (t.degage ? ", uncovered)" : ", buried)") : ")");
        return sb.toString();
    }

    // ---------- Utilitaires ----------

    private static String dimension(ServerPlayer joueur) {
        return joueur.level().dimension().location().toString();
    }

    private static double distance(ServerPlayer joueur, double x, double z) {
        return Math.hypot(joueur.getX() - x, joueur.getZ() - z);
    }

    private static Component nomEspece(String espece) {
        Species s = PokemonSpecies.getByName(espece);
        return s == null ? Component.literal(espece) : s.getTranslatedName();
    }

    static void sauver() {
        ecrire(fichierEtat, etat);
    }

    private static <T> T lire(Path fichier, java.lang.reflect.Type type, T defaut) {
        if (!Files.exists(fichier)) return defaut;
        try (Reader r = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            T valeur = GSON.fromJson(r, type);
            return valeur == null ? defaut : valeur;
        } catch (Exception e) {
            System.err.println("[Field Investigations] Lecture impossible de " + fichier + " : " + e);
            return defaut;
        }
    }

    private static void ecrire(Path fichier, Object valeur) {
        try (Writer w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            GSON.toJson(valeur, w);
        } catch (Exception e) {
            System.err.println("[Field Investigations] Écriture impossible de " + fichier + " : " + e);
        }
    }
}
