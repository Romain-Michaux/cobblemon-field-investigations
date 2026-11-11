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
- **16 new mini-games** (31 in total), all with sound feedback and new pixel art:
  - Sampling: sieving, footprint cast, tweezers, trace photo, swabbing.
  - Analysis: microscope, chromatography, family tree, drop titration, bite cast.
  - Tied to the kind of case: free dive (aquatic), tree climb (canopy), abseil (highlands), UV lamp (nocturnal),
    thawing (frost), cry filter (mist, played before each bearing during the hunt).
  - Each trace and each marker now picks among several games.
- **Buried clues**: a single one per regular case (dig sites keep three), dug up with the brush or the sieve, and what
  you find varies: bone or tooth, eggshells, feather or moult, fang, claw or scale.
- **Investigator rank**: experience for every solved case, seven ranks, and one kit upgrade per rank (sharper radar,
  spare vial, quick scan, one more forgiven failure, keener ear, shorter wait between cases).
- **Logbook and archives** in the Field Kit: rank and upgrades, closed cases, kinds of investigation already solved.
  Players who solved cases before this update start with 30 points per case.
- **First-time help**: the first time a player meets a mini-game, its rules are shown and nothing starts before Start.
- **Advancements**: nine of them, from the first sample to ten solved cases.
- **Accessibility**: per-player colour aid (colorimetry shows the match percentage while you mix); server setting
  `difficulteMiniJeux` (`easy`, `normal`, `hard`) for the pace of every mini-game.
- New server settings: `nombrePistes` (1 to 3; 1 = no choice, as in 1.0), `minutesValiditePistes`,
  `chanceEnqueteSpeciale`.

## 1.0.0

First public release.
