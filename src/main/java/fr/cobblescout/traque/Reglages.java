package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Reglages {
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
    public double chanceEnqueteSpeciale = 0.5;
    // Rythme des mini-jeux : "easy" (un tiers plus lent), "normal" ou "hard" (un quart plus rapide).
    public String difficulteMiniJeux = "normal";
    // Nombre maximal d'enquêteurs sur un même dossier (1 = pas d'enquête en équipe).
    public int tailleEquipe = 4;
}
