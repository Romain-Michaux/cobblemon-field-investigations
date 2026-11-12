package io.github.romainmichaux.fieldinvestigations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CaseFile {
    public long id;
    public String phase = "SAMPLING"; // SAMPLING -> TRACKING -> SPOTTING -> APPROACH -> FLUSHED
    public String dimension, species, category, guess;
    // Special case type (see Reports), or null for an ordinary case.
    public String special;
    // Kit upgrades fixed when the case opens: extra forgiven faults, and whether the spare vial was already used.
    public int forgiven;
    public boolean spareVialUsed;
    public boolean newSpecies;
    public long seed;
    public int level, flees;
    // Mini-game faults (damaged sample, lost dose, destroyed clue): each costs half a star.
    public int faults;
    // The lead investigator (who opened the case) and the teammates they invited: all work on the same case, share its
    // stars and each receive the loot and XP.
    public UUID leader;
    public List<UUID> team = new ArrayList<>();
    // Last time the target was seen in battle near the player (to know who knocked it out).
    public transient long lastBattle;
    public double zoneX, zoneZ, lairX, lairZ;
    public List<Trace> traces = new ArrayList<>();
    public List<String> suspects = new ArrayList<>();
    public List<String> pins = new ArrayList<>();
    // Suspects the player crossed out on their board themselves (the mod never rules anyone out for them).
    public List<String> ruledOut = new ArrayList<>();
    public List<Bearing> bearings = new ArrayList<>();
    public long nextBearing, nextMove, glowEnd;
    public UUID pokemon, entity;
    public boolean announceApproach;
}
