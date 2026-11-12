package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// An investigator's career: XP (which sets the rank), archive, and the mini-games already seen.
public class Profile {
    public int xp;
    public List<CaseRecord> history = new ArrayList<>();
    public Set<String> gamesSeen = new HashSet<>();
    // Cases solved with each other player: regular partners are listed first when inviting.
    public Map<UUID, Integer> partners = new HashMap<>();
}
