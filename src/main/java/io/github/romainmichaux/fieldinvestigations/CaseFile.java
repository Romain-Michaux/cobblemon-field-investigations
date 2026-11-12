package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CaseFile {
    public long id;
    public String phase = "SAMPLING"; // PRELEVEMENTS -> TRAQUE -> APPROCHE -> DEBUSQUE
    public String level1, species, category, guess;
    // Type d'enquête spéciale (voir Signalement), ou null pour une enquête ordinaire.
    public String special;
    // Améliorations du kit acquises à l'ouverture de l'enquête : ratés pardonnés en plus, et fiole de secours déjà utilisée.
    public int forgiven;
    public boolean vialGiven;
    public boolean newB;
    public long seed;
    public int level2, leaks;
    // Ratés des mini-jeux (prélèvement abîmé, dose perdue, indice détruit) : chacun coûte une demi-étoile.
    public int faults;
    // Le chef d'enquête (celui qui a ouvert le dossier) et les coéquipiers qu'il a invités : tous travaillent sur le
    // même dossier, partagent ses étoiles et reçoivent chacun le butin et l'expérience.
    public UUID leader;
    public List<UUID> team = new ArrayList<>();
    // Dernier instant où la cible a été vue en combat près du joueur (pour savoir qui l'a mise K.O.).
    public transient long lastBattle;
    public double zoneX, zoneZ, lairX, lairZ;
    public List<Trace> traces = new ArrayList<>();
    public List<String> suspects = new ArrayList<>();
    public List<String> pins = new ArrayList<>();
    // Suspects que le joueur a lui-même barrés sur son tableau (le mod n'écarte personne à sa place).
    public List<String> ruledOut = new ArrayList<>();
    public List<Bearing> bearings = new ArrayList<>();
    public long nextBearing, nextMove, glowEnd;
    public UUID pokemon, entity;
    public boolean announceApproach;
}
