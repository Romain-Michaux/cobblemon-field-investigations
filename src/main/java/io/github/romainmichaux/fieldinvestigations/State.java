package io.github.romainmichaux.fieldinvestigations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class State {
    public Map<UUID, CaseFile> caseFiles = new HashMap<>();
    public Map<UUID, Long> nextInvestigation = new HashMap<>();
    public Map<UUID, Integer> succeeded = new HashMap<>();
    // Signalements proposés au joueur tant qu'il n'en a pas choisi un, et leur date d'expiration.
    public Map<UUID, List<CaseFile>> offers = new HashMap<>();
    public Map<UUID, Long> offersEnd = new HashMap<>();
    public Map<UUID, Profile> profiles = new HashMap<>();
}
