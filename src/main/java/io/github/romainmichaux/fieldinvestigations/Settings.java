package io.github.romainmichaux.fieldinvestigations;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Clés du fichier de config en anglais ; "alternate" garde les anciens noms français lisibles, et le fichier est
// réécrit en anglais au premier démarrage (Moteur relit puis réécrit la config).
public class Settings {
    public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
    @SerializedName(value = "zoneMinDistance", alternate = "zoneDistanceMin")
    public int zoneMinDistance = 100;
    @SerializedName(value = "zoneMaxDistance", alternate = "zoneDistanceMax")
    public int zoneMaxDistance = 180;
    @SerializedName(value = "zoneRadius", alternate = "rayonZone")
    public int zoneRadius = 45;
    @SerializedName(value = "lairMinDistance", alternate = "taniereDistanceMin")
    public int lairMinDistance = 150;
    @SerializedName(value = "lairMaxDistance", alternate = "taniereDistanceMax")
    public int lairMaxDistance = 280;
    @SerializedName(value = "dosesPerSample", alternate = "dosesParEchantillon")
    public int dosesPerSample = 2;
    @SerializedName(value = "suspectCount", alternate = "nombreSuspects")
    public int suspectCount = 12;
    @SerializedName(value = "pinsToStartHunt", alternate = "epinglesPourTraquer")
    public int pinsToStartHunt = 3;
    @SerializedName(value = "minutesBetweenCases", alternate = "minutesEntreEnquetes")
    public int minutesEntreEnquetes = 10;
    @SerializedName(value = "secondsBetweenBearings", alternate = "secondesEntreReleves")
    public int secondsBetweenBearings = 6;
    @SerializedName(value = "distanceBetweenBearings", alternate = "distanceEntreReleves")
    public int distanceBetweenBearings = 20;
    @SerializedName(value = "bearingErrorMinDegrees", alternate = "erreurMinDegres")
    public double bearingErrorMinDegrees = 4;
    @SerializedName(value = "bearingErrorMaxDegrees", alternate = "erreurMaxDegres")
    public double bearingErrorMaxDegrees = 14;
    @SerializedName(value = "minutesBeforeTargetMoves", alternate = "minutesAvantDeplacement")
    public int minutesAvantDeplacement = 8;
    @SerializedName(value = "approachRadius", alternate = "rayonApproche")
    public int approachRadius = 48;
    @SerializedName(value = "alertRadius", alternate = "rayonAlerte")
    public int alertRadius = 18;
    @SerializedName(value = "flushOutRadius", alternate = "rayonDebusque")
    public int flushOutRadius = 7;
    @SerializedName(value = "shinyMultiplierPerStar", alternate = "multChromaParEtoile")
    public double shinyMultiplierPerStar = 3.0;
    @SerializedName(value = "newSpeciesWeight", alternate = "bonusNouvelleEspece")
    public double bonusNewSpecies = 2.0;
    @SerializedName(value = "rarityWeights", alternate = "poidsCategories")
    public Map<String, Double> rarityWeights = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 2.5, "rare", 3.0, "ultra-rare", 2.0));
    @SerializedName(value = "excludedLabels", alternate = "etiquettesInterdites")
    public List<String> excludedLabels = new ArrayList<>(List.of("legendary", "mythical", "ultra_beast", "paradox"));
    // Le butin d'une enquête résolue vient des tables de butin du mod (data/cobblemon_field_investigations/loot_table/investigation) :
    // stars_1 à stars_5 selon la qualité de l'enquête, rarity_<catégorie> selon l'espèce, milestone tous les N dossiers.
    @SerializedName(value = "milestoneEveryCases", alternate = "palierBonus")
    public int milestoneEveryCases = 5;
    // À l'ouverture d'une enquête, le joueur choisit parmi plusieurs signalements (1 = pas de choix, comme en 1.0).
    @SerializedName(value = "leadCount", alternate = "nombrePistes")
    public int leadCount = 3;
    @SerializedName(value = "leadValidityMinutes", alternate = "minutesValiditePistes")
    public int minutesValiditePistes = 10;
    // Chance qu'un signalement (autre que le premier) soit une enquête spéciale (voir Signalement).
    @SerializedName(value = "specialCaseChance", alternate = "chanceEnqueteSpeciale")
    public double chanceSpecialCase = 0.5;
    // Rythme des mini-jeux : "easy" (un tiers plus lent), "normal" ou "hard" (un quart plus rapide).
    @SerializedName(value = "miniGameDifficulty", alternate = "difficulteMiniJeux")
    public String miniGameDifficulty = "normal";
    // Nombre maximal d'enquêteurs sur un même dossier (1 = pas d'enquête en équipe).
    @SerializedName(value = "maxTeamSize", alternate = "tailleEquipe")
    public int maxTeamSize = 4;
}
