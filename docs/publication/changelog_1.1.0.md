## 1.1.0

### Choosing and running a case
- **Pick your case**: the Field Kit now reports up to three leads, each with the rarity of the species, where the zone is, whether you already own it and what it rewards.
- **Ten kinds of special investigations**, each with its own rule and loot:
  - Anywhere: *nocturnal* (night only, shiny odds ×2), *skittish*, *exceptional* (hidden ability), *restless*.
  - By biome: *aquatic* (traces on the bottom, target in the water), *dig site*, *canopy*, *highlands*, *frost*, *mist*.
- **Team investigations**: invite players within 64 blocks from the kit's Team tab. One shared case, shared stars, loot and experience for every member.
- **Cave traces**: one trace per case can sit in a shallow cave reachable on foot. The radar shows the height difference ("In a cave: 23 m ▼8").
- **Buried clues** now vary: bone or tooth, eggshells, feather or moult, fang, claw or scale.

### Mini-games
- **17 new mini-games (32 in total)**: sieving, footprint cast, tweezers, trace photo, swabbing, microscope, chromatography, family tree, drop titration, bite cast, plus games tied to the kind of case: free dive, tree climb, abseil, UV lamp, thawing, cry filter, rockfall.
- Each trace and each genetic marker now picks among several games.
- **First-time help**: the rules of a mini-game are shown the first time you meet it.
- `/hunt game [name]` (operators): try any mini-game as a dry run.

### Progression
- **Investigator rank**: seven ranks earned by solving cases, each unlocking a kit upgrade (sharper radar, spare vial, quick scan, one more forgiven failure…).
- **Logbook and archives** in the Field Kit. Cases solved before the update count retroactively.
- **Nine advancements**, from the first sample to ten solved cases.

### Sound, interface, accessibility
- **34 sound effects made for the mod** replace the borrowed Minecraft sounds. No music or ambience loop.
- Long texts scroll inside their box instead of being cut.
- Colour aid for the colorimetry game (per player) and a server-side mini-game pace setting (`easy`, `normal`, `hard`).

### Fixes
- Investigation state is now saved **per world**. Before, every single-player world shared the same file, so a new world inherited the cases and rank of the others. A dedicated server takes over its old file automatically.

### Server settings
- Config keys are now in English (`leadCount`, `maxTeamSize`…). An existing `config/cobblemon_field_investigations.json` is read as before and rewritten with the new names on first start.
- New: `leadCount` (1 = no choice, as in 1.0), `leadValidityMinutes`, `specialCaseChance`, `maxTeamSize`, `miniGameDifficulty`.
