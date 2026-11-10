# Changelog

## 1.1.0 (in development)

- **Pick your case**: the kit now reports up to three leads. Each shows the rarity of the species, where the zone is,
  whether you already caught that species, and what it rewards.
- **Ten kinds of special investigations**, each with its own rule and reward:
  - Anywhere: nocturnal (night only, shiny odds ×2), skittish (senses you from farther, double special loot),
    exceptional (one more result required, look-alike suspects, hidden ability), restless (moves twice as often,
    farther lair, double special loot).
  - By biome: aquatic (water species, traces on the bottom, target in the water), dig site (every remain buried),
    canopy (traces in the trees), highlands (traces on rock faces, wider zone), frost (one dose fewer per sample),
    mist (less precise bearings).
  - New loot tables: `traque/speciale` for every special case, `traque/type_<kind>` for the biome kinds.
  - Traces can now sit under water.
- **Advancements**: nine of them, from the first sample to ten solved cases.
- **Accessibility**: per-player colour aid (colorimetry shows the match percentage while you mix); server setting
  `difficulteMiniJeux` (`easy`, `normal`, `hard`) for the pace of every mini-game.
- New server settings: `nombrePistes` (1 to 3; 1 = no choice, as in 1.0), `minutesValiditePistes`,
  `chanceEnqueteSpeciale`.

## 1.0.0

First public release.
