# Changelog

## 1.2.0 (in progress)

### Mini-games
- **Abseil**: new art (climber, boulders, rock ledges growing out of the walls, gold-rimmed clues that glint), a short
  poetic intro instead of a key list, and the controls shown as key icons on one line under the game.
- **Bite**: a fossil jaw with real fangs over a putty cast in a steel tray; drag the cast with the mouse (a click
  presses it), the fangs sink into the putty when it fits, the jaw shakes and sparks fly where it jams. First round
  lights up the bumps that already match; the arrow buttons and the long text are gone.
- **Brush**: two soil layers (dry crust, then damp earth) over the whole width, a real brush that leans with the stroke
  and kicks up dust, and turns orange before a stroke gets too rough. The clue is broken into three jagged shards
  buried apart; covered shards sound hollow under the brush, two decoy pebbles sound dry. Freed shards lift with a
  gold rim, and the three fly back together into the whole clue at the end.
- **Cast**: a paw print in cross-section (four toes and the pad) filled from a jug that follows the pointer. The
  plaster falls for real, so it keeps landing after the click is released, and the stream thickens the longer it runs.
  One toe hides under a fold of mud and is filled by ear. An overflow spills over the rim and must be scraped; at the
  end the plaster sets and the whole cast comes out of the ground.
- **Centrifuge**: no more needle. The rotor sits in a steel housing with the sample tube in one bucket; braking makes
  it coast about a turn before it stops, so the tube must be stopped under the lid's window by anticipation. Each good
  stop separates one more layer in the test tube beside it (pellet, serum, pale band).
- **Chromatography**: now an identification. The sample strip climbs in a glass tank beside four, then five,
  reference strips; the sample is a blend of two references, to be named once the rack is lifted. Overlapping inks
  blend into a dull smear, each hue exists at two heights, and neighbouring inks only part late in the run: reading
  early is a guess, reading late risks the solvent reaching the top.

## 1.1.0 (2026-11-12)

### Choosing and running a case
- **Pick your case**: the kit now reports up to three leads. Each shows the rarity of the species, where the zone is,
  whether you already caught that species, and what it rewards.
- **Ten kinds of special investigations**, each with its own rule and reward:
  - Anywhere: nocturnal (night only, shiny odds ×2), skittish (senses you from farther, double special loot),
    exceptional (one more result required, look-alike suspects, hidden ability), restless (moves twice as often,
    farther lair, double special loot).
  - By biome: aquatic (water species, traces on the bottom, target in the water), dig site (every remain buried),
    canopy (traces in the trees), highlands (traces on rock faces, wider zone), frost (one dose fewer per sample),
    mist (less precise bearings).
  - New loot tables: `investigation/special` for every special case, `investigation/kind_<kind>` for each kind.
  - Traces can now sit under water.
- **Team investigations**: the lead investigator invites players within 64 blocks from the kit's Team tab (search by
  name, usual partners listed first, up to `maxTeamSize` members, 4 by default). One shared case, shared stars,
  loot and experience for every member.
- **Cave traces**: one trace per case can sit in a shallow cave, 3 to 15 blocks below the surface, always reachable
  on foot from the outside. The kit radar then counts height and shows the distance and height difference
  ("In a cave: 23 m ▼8"). Aquatic and dig-site cases keep all their traces outside.
- **Buried clues**: a single one per regular case (dig sites keep three), dug up with the brush or the sieve, and what
  you find varies: bone or tooth, eggshells, feather or moult, fang, claw or scale.

### Mini-games
- **17 new mini-games** (32 in total), all with sound feedback and new pixel art:
  - Sampling: sieving, footprint cast, tweezers, trace photo, swabbing.
  - Analysis: microscope, chromatography, family tree, drop titration, bite cast.
  - Tied to the kind of case: free dive (aquatic), tree climb (canopy), abseil (highlands), UV lamp (nocturnal),
    thawing (frost), cry filter (mist, played before each bearing during the hunt), rockfall (cave traces).
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
  `miniGameDifficulty` (`easy`, `normal`, `hard`) for the pace of every mini-game.

### Fixes
- Investigation state (cases, ranks, solved count) is now saved in each world. Before, every single-player world
  shared the same file in `config/`, so a new world started with the cases and rank of the others. A dedicated
  server takes over its old `config/cobblemon_field_investigations_state.json` once (kept as `.migrated`).

### Server settings
- Loot tables renamed in English: `traque/etoiles_N` → `investigation/stars_N`, `rarete_*` → `rarity_*`, `palier` → `milestone`.
  Data packs that overrode the 1.0 tables must rename their files.
- Config keys are now in English (`leadCount`, `maxTeamSize`…). An existing `config/cobblemon_field_investigations.json` is read as before and rewritten with the new names on first start.
- New: `leadCount` (1 to 3; 1 = no choice, as in 1.0), `leadValidityMinutes`, `specialCaseChance`,
  `maxTeamSize`, `miniGameDifficulty`.

## 1.0.0 (2026-11-10)

First public release.

- Case board: suspects are crossed out by the player (right-click), never automatically; a small Poké Ball marks
  species you already own.
- Photo pins are drawn above neighbouring photos on the case board.
