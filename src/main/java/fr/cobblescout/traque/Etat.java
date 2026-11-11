package fr.cobblescout.traque;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Etat {
    public Map<UUID, Dossier> dossiers = new HashMap<>();
    public Map<UUID, Long> prochaineEnquete = new HashMap<>();
    public Map<UUID, Integer> reussies = new HashMap<>();
    // Signalements proposés au joueur tant qu'il n'en a pas choisi un, et leur date d'expiration.
    public Map<UUID, List<Dossier>> offres = new HashMap<>();
    public Map<UUID, Long> finOffres = new HashMap<>();
    public Map<UUID, Profil> profils = new HashMap<>();
}
