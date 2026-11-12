package io.github.romainmichaux.fieldinvestigations;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Config keys are English; "alternate" keeps the old French names readable, and the file is rewritten in English on
// first start (Engine.load reads then rewrites the config).
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
    public int minutesBetweenCases = 10;
    @SerializedName(value = "secondsBetweenBearings", alternate = "secondesEntreReleves")
    public int secondsBetweenBearings = 6;
    @SerializedName(value = "distanceBetweenBearings", alternate = "distanceEntreReleves")
    public int distanceBetweenBearings = 20;
    @SerializedName(value = "bearingErrorMinDegrees", alternate = "erreurMinDegres")
    public double bearingErrorMinDegrees = 4;
    @SerializedName(value = "bearingErrorMaxDegrees", alternate = "erreurMaxDegres")
    public double bearingErrorMaxDegrees = 14;
    @SerializedName(value = "minutesBeforeTargetMoves", alternate = "minutesAvantDeplacement")
    public int minutesBeforeTargetMoves = 8;
    @SerializedName(value = "approachRadius", alternate = "rayonApproche")
    public int approachRadius = 48;
    @SerializedName(value = "alertRadius", alternate = "rayonAlerte")
    public int alertRadius = 18;
    @SerializedName(value = "flushOutRadius", alternate = "rayonDebusque")
    public int flushOutRadius = 7;
    @SerializedName(value = "shinyMultiplierPerStar", alternate = "multChromaParEtoile")
    public double shinyMultiplierPerStar = 3.0;
    @SerializedName(value = "newSpeciesWeight", alternate = "bonusNouvelleEspece")
    public double newSpeciesWeight = 2.0;
    @SerializedName(value = "rarityWeights", alternate = "poidsCategories")
    public Map<String, Double> rarityWeights = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 2.5, "rare", 3.0, "ultra-rare", 2.0));
    @SerializedName(value = "excludedLabels", alternate = "etiquettesInterdites")
    public List<String> excludedLabels = new ArrayList<>(List.of("legendary", "mythical", "ultra_beast", "paradox"));
    // Loot for a solved case comes from the mod's loot tables (data/cobblemon_field_investigations/loot_table/investigation):
    // stars_1 to stars_5 by case quality, rarity_<category> by species, milestone every N cases.
    @SerializedName(value = "milestoneEveryCases", alternate = "palierBonus")
    public int milestoneEveryCases = 5;
    // When opening a case, the player picks from several reports (1 = no choice, as in 1.0).
    @SerializedName(value = "leadCount", alternate = "nombrePistes")
    public int leadCount = 3;
    @SerializedName(value = "leadValidityMinutes", alternate = "minutesValiditePistes")
    public int leadValidityMinutes = 10;
    // Chance that a report (other than the first) is a special case (see Reports).
    @SerializedName(value = "specialCaseChance", alternate = "chanceEnqueteSpeciale")
    public double specialCaseChance = 0.5;
    // Mini-game pace: "easy" (a third slower), "normal" or "hard" (a quarter faster).
    @SerializedName(value = "miniGameDifficulty", alternate = "difficulteMiniJeux")
    public String miniGameDifficulty = "normal";
    // Maximum investigators on one case (1 = no team cases).
    @SerializedName(value = "maxTeamSize", alternate = "tailleEquipe")
    public int maxTeamSize = 4;
}
