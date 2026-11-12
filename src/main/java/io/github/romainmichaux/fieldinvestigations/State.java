package io.github.romainmichaux.fieldinvestigations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class State {
    public Map<UUID, CaseFile> caseFiles = new HashMap<>();
    public Map<UUID, Long> nextInvestigation = new HashMap<>();
    public Map<UUID, Integer> succeeded = new HashMap<>();
    // Reports offered to the player until they pick one, and when the offer expires.
    public Map<UUID, List<CaseFile>> offers = new HashMap<>();
    public Map<UUID, Long> offersEnd = new HashMap<>();
    public Map<UUID, Profile> profiles = new HashMap<>();
}
