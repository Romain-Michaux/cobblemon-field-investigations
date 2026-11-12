package fr.cobblescout.traque;

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
public final class Moteur {
    // Expérience requise pour chaque rang (0 = stagiaire ... 6 = légende). Chaque rang débloque une amélioration du kit :
    //  1 radar affiné (les traces scintillent de plus loin), 2 fiole de secours (le premier prélèvement de chaque enquête
    //  ne consomme pas de fiole), 3 scan rapide (2 s au lieu de 3), 4 sang-froid (un raté de plus pardonné par enquête),
    //  5 oreille fine (relevés de cri plus précis), 6 réseau d'informateurs (attente entre deux enquêtes divisée par deux).
    static final int[] SEUILS = {0, 60, 160, 320, 560, 900, 1400};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final Random HASARD = new Random();

    static Reglages reglages = new Reglages();

    static Etat etat = new Etat();

    private static Path fichierEtat;

    static final Map<UUID, PokemonEntity> ENTITES = new HashMap<>();

    static final Map<UUID, Boolean> ANALYSES = new HashMap<>();

    static final Map<UUID, BlockPos> PRELEVEMENTS = new HashMap<>();

    static final Map<UUID, Boolean> FILTRAGES = new HashMap<>();

    // Avancée du scan de chaque joueur : [demi-secondes passées sur la cible, « ce n'est pas la cible » encore affiché].
    static final Map<UUID, int[]> SCANS = new HashMap<>();

    private Moteur() {}

    static void charger() {
        Path dossier = FabricLoader.getInstance().getConfigDir();
        reglages = lire(dossier.resolve("cobblemon_field_investigations.json"), Reglages.class, new Reglages());
        ecrire(dossier.resolve("cobblemon_field_investigations.json"), reglages);
    }

    // L'état appartient au monde, pas à l'instance : en solo, chaque partie a ses propres enquêtes et ses propres rangs.
    // Jusqu'en 1.1.0 il était rangé dans config/ ; un serveur dédié n'a qu'un monde, il reprend donc ce fichier une fois.
    // En solo, impossible de savoir à quel monde l'ancien fichier appartenait : chaque monde repart de zéro.
    static void chargerMonde(MinecraftServer serveur) {
        etat = new Etat();
        fichierEtat = serveur.getWorldPath(LevelResource.ROOT).resolve("cobblemon_field_investigations_state.json");
        Path ancien = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_field_investigations_state.json");
        if (!Files.exists(fichierEtat) && serveur.isDedicatedServer() && Files.exists(ancien)) {
            try {
                Files.copy(ancien, fichierEtat);
                Files.move(ancien, ancien.resolveSibling("cobblemon_field_investigations_state.json.migre"));
            } catch (Exception e) {
                System.err.println("[Field Investigations] Reprise impossible de " + ancien + " : " + e);
            }
        }
        Etat lu = lire(fichierEtat, Etat.class, null);
        if (lu != null) {
            etat = lu;
            if (etat.dossiers == null) etat.dossiers = new HashMap<>();
            if (etat.prochaineEnquete == null) etat.prochaineEnquete = new HashMap<>();
            if (etat.reussies == null) etat.reussies = new HashMap<>();
            if (etat.offres == null) etat.offres = new HashMap<>();
            if (etat.finOffres == null) etat.finOffres = new HashMap<>();
            if (etat.profils == null) etat.profils = new HashMap<>();
            for (Map.Entry<UUID, Dossier> en : etat.dossiers.entrySet()) {
                if (en.getValue().chef == null) en.getValue().chef = en.getKey();
                if (en.getValue().equipe == null) en.getValue().equipe = new ArrayList<>();
            }
        }
    }

    static void arret() {
        sauver();
        etat = new Etat();
        fichierEtat = null;
        ENTITES.clear();
        ANALYSES.clear();
        PRELEVEMENTS.clear();
        FILTRAGES.clear();
        SCANS.clear();
        Equipe.INVITATIONS.clear();
        Equipe.FIN_INVITATIONS.clear();
    }

    // Le dossier sur lequel travaille ce joueur : le sien, ou celui de l'équipe qu'il a rejointe.
    static Dossier dossier(ServerPlayer joueur) {
        UUID id = joueur.getUUID();
        Dossier d = etat.dossiers.get(id);
        if (d != null) return d;
        for (Dossier autre : etat.dossiers.values()) {
            if (autre.equipe != null && autre.equipe.contains(id)) return autre;
        }
        return null;
    }

    static List<UUID> membres(Dossier d) {
        List<UUID> tous = new ArrayList<>();
        tous.add(d.chef);
        if (d.equipe != null) tous.addAll(d.equipe);
        return tous;
    }

    static List<ServerPlayer> enLigne(Dossier d, net.minecraft.server.MinecraftServer serveur) {
        List<ServerPlayer> presents = new ArrayList<>();
        for (UUID u : membres(d)) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) presents.add(p);
        }
        return presents;
    }

    static List<ServerPlayer> coequipiers(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d == null || d.equipe == null || d.equipe.isEmpty()) return List.of();
        List<ServerPlayer> autres = enLigne(d, joueur.server);
        autres.remove(joueur);
        return autres;
    }

    static String nom(net.minecraft.server.MinecraftServer serveur, UUID id) {
        ServerPlayer p = serveur.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        var cache = serveur.getProfileCache();
        return cache == null ? "?" : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse("?");
    }

    static void razScan(Dossier d) {
        for (UUID u : membres(d)) SCANS.remove(u);
    }

    // Le joueur barre (ou rétablit) un suspect sur son tableau, d'après ce qu'il déduit des marqueurs analysés.
    static void ecarter(ServerPlayer joueur, String espece) {
        Dossier d = dossier(joueur);
        if (d == null || !"PRELEVEMENTS".equals(d.phase) || !d.suspects.contains(espece)) return;
        if (d.ecartes == null) d.ecartes = new ArrayList<>();
        if (!d.ecartes.remove(espece)) d.ecartes.add(espece);
        sauver();
        Messages.synchroniser(joueur);
    }

    // Qualité de l'enquête, de 1 à 5 étoiles. On part de 5 ; un raté de mini-jeu coûte une demi-étoile (le premier est
    // donc gratuit), une fuite de la cible une étoile, un mauvais pronostic deux étoiles.
    static int etoiles(Dossier d, boolean avecPronostic) {
        int demis = 10 - Math.max(0, d.fautes - d.pardon) - 2 * d.fuites;
        if (avecPronostic && d.pronostic != null && !d.espece.equals(d.pronostic)) demis -= 4;
        return Math.max(1, Math.min(5, (demis + 1) / 2));
    }

    static int etoiles(Dossier d) {
        return etoiles(d, true);
    }

    // IV parfaits accordés au Pokémon : aucun à 1 étoile, 4 au mieux à 5 étoiles.
    static int ivParfaits(int etoiles) {
        return Math.max(0, Math.min(4, etoiles - 1));
    }

    // Le profil d'un joueur. Ceux qui avaient déjà résolu des enquêtes avant l'arrivée des rangs partent avec 30 points par dossier.
    static Profil profil(UUID id) {
        Profil p = etat.profils.get(id);
        if (p == null) {
            p = new Profil();
            p.xp = etat.reussies.getOrDefault(id, 0) * 30;
            etat.profils.put(id, p);
        }
        if (p.historique == null) p.historique = new ArrayList<>();
        if (p.jeuxVus == null) p.jeuxVus = new HashSet<>();
        if (p.partenaires == null) p.partenaires = new HashMap<>();
        return p;
    }

    static int rang(int xp) {
        int r = 0;
        while (r + 1 < SEUILS.length && xp >= SEUILS[r + 1]) r++;
        return r;
    }

    static int rang(ServerPlayer joueur) {
        return rang(profil(joueur.getUUID()).xp);
    }

    // Vrai la première fois que ce joueur ouvre ce mini-jeu : l'écran lui en explique alors les règles avant de démarrer.
    static boolean premiereFois(ServerPlayer joueur, String jeu) {
        boolean nouveau = profil(joueur.getUUID()).jeuxVus.add(jeu);
        if (nouveau) sauver();
        return nouveau;
    }

    // Durée du scan, en demi-secondes : 6 d'ordinaire, 4 avec le scan rapide (rang 3).
    static int dureeScan(ServerPlayer joueur) {
        return rang(joueur) >= 3 ? 4 : 6;
    }

    static boolean est(Dossier d, String special) {
        return special.equals(d.special);
    }

    static int requis(Dossier d) {
        return Math.min(Genetique.MARQUEURS.size(), reglages.epinglesPourTraquer + (est(d, "exceptionnel") ? 1 : 0));
    }

    static int alerte(Dossier d) {
        return est(d, "craintive") ? (int) Math.round(reglages.rayonAlerte * 1.6) : reglages.rayonAlerte;
    }

    static int rayon(Dossier d) {
        return est(d, "altitude") ? (int) Math.round(reglages.rayonZone * 1.5) : reglages.rayonZone;
    }

    // Délai avant que la cible change d'endroit pendant la traque (0 = jamais).
    static long prochainDeplacement(Dossier d) {
        if (reglages.minutesAvantDeplacement <= 0) return 0;
        return System.currentTimeMillis() + reglages.minutesAvantDeplacement * 60_000L / (est(d, "migratrice") ? 2 : 1);
    }

    // Une cible nocturne ne laisse rien voir ni entendre en plein jour.
    static boolean dort(Dossier d, ServerLevel monde) {
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

    static void faute(ServerPlayer joueur, Dossier d) {
        d.fautes++;
        sauver();
    }

    // Ouvre un mini-jeu à blanc : rien n'est prélevé, analysé ni compté, quelle que soit l'issue.
    static boolean essayer(ServerPlayer joueur, String jeu, int variante) {
        boolean terrain = Genetique.JEUX_TERRAIN.contains(jeu);
        if (!terrain && !Genetique.JEUX_LABO.contains(jeu)) return false;
        String genre = "type1";
        for (Map.Entry<String, List<String>> e : (terrain ? Genetique.JEUX_PRELEVEMENT : Genetique.JEUX_ANALYSE).entrySet()) {
            if (e.getValue().contains(jeu)) genre = e.getKey();
        }
        boolean enfoui = jeu.equals("degagement") || jeu.equals("tamisage");
        if (enfoui) genre = List.of("taille", "oeuf", "stat").get(HASARD.nextInt(3));
        Paquet p = Paquet.de("ouvrir");
        p.donnees().putString("ecran", "essai");
        p.donnees().putBoolean("premiere", premiereFois(joueur, jeu));
        p.donnees().putString("jeu", jeu);
        p.donnees().putString("genre", genre);
        p.donnees().putBoolean("terrain", terrain);
        p.donnees().putBoolean("enfoui", enfoui);
        p.donnees().putInt("variante", variante >= 0 ? variante : HASARD.nextInt(Genetique.VARIANTES.getOrDefault(genre, 1)));
        p.donnees().putLong("graine", HASARD.nextLong());
        Messages.envoyer(joueur, p);
        return true;
    }

    static void reinitialiser(ServerPlayer joueur) {
        Dossier d = dossier(joueur);
        if (d != null && joueur.getUUID().equals(d.chef)) Cloture.cloturer(joueur, d);
        else if (d != null) d.equipe.remove(joueur.getUUID());
        etat.prochaineEnquete.remove(joueur.getUUID());
        etat.offres.remove(joueur.getUUID());
        etat.finOffres.remove(joueur.getUUID());
        sauver();
        Messages.synchroniser(joueur);
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

    static String dimension(ServerPlayer joueur) {
        return joueur.level().dimension().location().toString();
    }

    static double distance(ServerPlayer joueur, double x, double z) {
        return Math.hypot(joueur.getX() - x, joueur.getZ() - z);
    }

    static Component nomEspece(String espece) {
        Species s = PokemonSpecies.getByName(espece);
        return s == null ? Component.literal(espece) : s.getTranslatedName();
    }

    static void sauver() {
        if (fichierEtat != null) ecrire(fichierEtat, etat);
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
