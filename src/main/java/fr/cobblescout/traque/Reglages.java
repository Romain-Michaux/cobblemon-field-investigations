package fr.cobblescout.traque;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Clés du fichier de config en anglais ; "alternate" garde les anciens noms français lisibles, et le fichier est
// réécrit en anglais au premier démarrage (Moteur relit puis réécrit la config).
public class Reglages {
    public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
    @SerializedName(value = "zoneMinDistance", alternate = "zoneDistanceMin")
    public int zoneDistanceMin = 100;
    @SerializedName(value = "zoneMaxDistance", alternate = "zoneDistanceMax")
    public int zoneDistanceMax = 180;
    @SerializedName(value = "zoneRadius", alternate = "rayonZone")
    public int rayonZone = 45;
    @SerializedName(value = "lairMinDistance", alternate = "taniereDistanceMin")
    public int taniereDistanceMin = 150;
    @SerializedName(value = "lairMaxDistance", alternate = "taniereDistanceMax")
    public int taniereDistanceMax = 280;
    @SerializedName(value = "dosesPerSample", alternate = "dosesParEchantillon")
    public int dosesParEchantillon = 2;
    @SerializedName(value = "suspectCount", alternate = "nombreSuspects")
    public int nombreSuspects = 12;
    @SerializedName(value = "pinsToStartHunt", alternate = "epinglesPourTraquer")
    public int epinglesPourTraquer = 3;
    @SerializedName(value = "minutesBetweenCases", alternate = "minutesEntreEnquetes")
    public int minutesEntreEnquetes = 10;
    @SerializedName(value = "secondsBetweenBearings", alternate = "secondesEntreReleves")
    public int secondesEntreReleves = 6;
    @SerializedName(value = "distanceBetweenBearings", alternate = "distanceEntreReleves")
    public int distanceEntreReleves = 20;
    @SerializedName(value = "bearingErrorMinDegrees", alternate = "erreurMinDegres")
    public double erreurMinDegres = 4;
    @SerializedName(value = "bearingErrorMaxDegrees", alternate = "erreurMaxDegres")
    public double erreurMaxDegres = 14;
    @SerializedName(value = "minutesBeforeTargetMoves", alternate = "minutesAvantDeplacement")
    public int minutesAvantDeplacement = 8;
    @SerializedName(value = "approachRadius", alternate = "rayonApproche")
    public int rayonApproche = 48;
    @SerializedName(value = "alertRadius", alternate = "rayonAlerte")
    public int rayonAlerte = 18;
    @SerializedName(value = "flushOutRadius", alternate = "rayonDebusque")
    public int rayonDebusque = 7;
    @SerializedName(value = "shinyMultiplierPerStar", alternate = "multChromaParEtoile")
    public double multChromaParEtoile = 3.0;
    @SerializedName(value = "newSpeciesWeight", alternate = "bonusNouvelleEspece")
    public double bonusNouvelleEspece = 2.0;
    @SerializedName(value = "rarityWeights", alternate = "poidsCategories")
    public Map<String, Double> poidsCategories = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 2.5, "rare", 3.0, "ultra-rare", 2.0));
    @SerializedName(value = "excludedLabels", alternate = "etiquettesInterdites")
    public List<String> etiquettesInterdites = new ArrayList<>(List.of("legendary", "mythical", "ultra_beast", "paradox"));
    // Le butin d'une enquête résolue vient des tables de butin du mod (data/cobblemon_field_investigations/loot_table/traque) :
    // etoiles_1 à etoiles_5 selon la qualité de l'enquête, rarete_<catégorie> selon l'espèce, palier tous les N dossiers.
    @SerializedName(value = "milestoneEveryCases", alternate = "palierBonus")
    public int palierBonus = 5;
    // À l'ouverture d'une enquête, le joueur choisit parmi plusieurs signalements (1 = pas de choix, comme en 1.0).
    @SerializedName(value = "leadCount", alternate = "nombrePistes")
    public int nombrePistes = 3;
    @SerializedName(value = "leadValidityMinutes", alternate = "minutesValiditePistes")
    public int minutesValiditePistes = 10;
    // Chance qu'un signalement (autre que le premier) soit une enquête spéciale (voir Signalement).
    @SerializedName(value = "specialCaseChance", alternate = "chanceEnqueteSpeciale")
    public double chanceEnqueteSpeciale = 0.5;
    // Rythme des mini-jeux : "easy" (un tiers plus lent), "normal" ou "hard" (un quart plus rapide).
    @SerializedName(value = "miniGameDifficulty", alternate = "difficulteMiniJeux")
    public String difficulteMiniJeux = "normal";
    // Nombre maximal d'enquêteurs sur un même dossier (1 = pas d'enquête en équipe).
    @SerializedName(value = "maxTeamSize", alternate = "tailleEquipe")
    public int tailleEquipe = 4;
}
