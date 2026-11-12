package fr.cobblescout.traque;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Carrière d'un enquêteur : son expérience (dont découle son rang), ses archives, et les mini-jeux qu'il a déjà vus.
public class Profil {
    public int xp;
    public List<Fiche> historique = new ArrayList<>();
    public Set<String> jeuxVus = new HashSet<>();
    // Nombre d'enquêtes résolues avec chaque autre joueur : les partenaires habituels passent en tête des invitations.
    public Map<UUID, Integer> partenaires = new HashMap<>();
}
