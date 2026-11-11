package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Dossier {
    public long id;
    public String phase = "PRELEVEMENTS"; // PRELEVEMENTS -> TRAQUE -> APPROCHE -> DEBUSQUE
    public String monde, espece, categorie, pronostic;
    // Type d'enquête spéciale (voir SPECIAUX), ou null pour une enquête ordinaire.
    public String special;
    // Améliorations du kit acquises à l'ouverture de l'enquête : ratés pardonnés en plus, et fiole de secours déjà utilisée.
    public int pardon;
    public boolean fioleOfferte;
    public boolean nouvelle;
    public long graine;
    public int niveau, fuites;
    // Ratés des mini-jeux (prélèvement abîmé, dose perdue, indice détruit) : chacun coûte une demi-étoile.
    public int fautes;
    // Le chef d'enquête (celui qui a ouvert le dossier) et les coéquipiers qu'il a invités : tous travaillent sur le
    // même dossier, partagent ses étoiles et reçoivent chacun le butin et l'expérience.
    public UUID chef;
    public List<UUID> equipe = new ArrayList<>();
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
