# Changelog

## 1.1.0 (2026-11-12)

### Choosing and running a case
- **Pick your case**: the kit now reports up to three leads. Each shows the rarity of the species, where the zone is,
  whether you already caught that species, and what it rewards.
- **Eleven kinds of special investigations**, each with its own rule and reward:
  - Anywhere: nocturnal (night only, shiny odds ×2), skittish (senses you from farther, double special loot),
    exceptional (one more result required, look-alike suspects, hidden ability), restless (moves twice as often,
    farther lair, double special loot), underground (cave species, traces and lair in caves, depth shown on the
    radar, ores and stones as special loot).
  - By biome: aquatic (water species, traces on the bottom, target in the water), dig site (every remain buried),
    canopy (traces in the trees), highlands (traces on rock faces, wider zone), frost (one dose fewer per sample),
    mist (less precise bearings).
  - New loot tables: `traque/speciale` for every special case, `traque/type_<kind>` for each kind.
  - Traces can now sit under water.
- **Team investigations**: the lead investigator invites players within 64 blocks from the kit's Team tab (search by
  name, usual partners listed first, up to `tailleEquipe` members, 4 by default). One shared case, shared stars,
  loot and experience for every member.
- **Buried clues**: a single one per regular case (dig sites keep three), dug up with the brush or the sieve, and what
  you find varies: bone or tooth, eggshells, feather or moult, fang, claw or scale.

### Mini-games
- **17 new mini-games** (32 in total), all with sound feedback and new pixel art:
  - Sampling: sieving, footprint cast, tweezers, trace photo, swabbing.
  - Analysis: microscope, chromatography, family tree, drop titration, bite cast.
  - Tied to the kind of case: free dive (aquatic), tree climb (canopy), abseil (highlands), UV lamp (nocturnal),
    thawing (frost), cry filter (mist, played before each bearing during the hunt), rockfall (underground).
  - Each trace and each marker now picks among several games.
- Several games reworked after playtests: tree climb (scrolling trunk, dead branches to let go by), plaster cast
  (tooth sockets, validated once the spout is lifted), bite cast, field photo (a random suspect as subject),
  sorting (drag and throw, objects push each other).
- **First-time help**: the first time a player meets a mini-game, its rules are shown and nothing starts before Start.
- `/hunt game [name]` (operators): open any mini-game as a dry run; without a name, lists them.

### Progression
- **Investigator rank**: experience for every solved case, seven ranks, and one kit upgrade per rank (sharper radar,
  spare vial, quick scan, one more forgiven failure, keener ear, shorter wait between cases).
- **Logbook and archives** in the Field Kit: rank and upgrades, closed cases, kinds of investigation already solved.
  Players who solved cases before this update start with 30 points per case.
- **Advancements**: nine of them, from the first sample to ten solved cases.

### Sound, interface, accessibility
- **Own sound effects**: 34 effects made for the mod (sieve, plaster, shutter, bubbles, heartbeat, rope, branches,
  stones, drops, scanner, success and failure jingles, rank-up fanfare) replace the borrowed Minecraft sounds.
  No music or ambience loop.
- Texts never get cut: long lines scroll inside their box.
- Case board: the tooltip is drawn above the item icons.
- **Accessibility**: per-player colour aid (colorimetry shows the match percentage while you mix); server setting
  `difficulteMiniJeux` (`easy`, `normal`, `hard`) for the pace of every mini-game.

### Server settings
- New: `nombrePistes` (1 to 3; 1 = no choice, as in 1.0), `minutesValiditePistes`, `chanceEnqueteSpeciale`,
  `tailleEquipe`, `difficulteMiniJeux`.

## 1.0.0 (2026-11-10)

First public release.

- Case board: suspects are crossed out by the player (right-click), never automatically; a small Poké Ball marks
  species you already own.
- Photo pins are drawn above neighbouring photos on the case board.
