<p align="center"><img src="icon.png" width="160" alt="Cobblemon: Field Investigations icon"></p>

# Cobblemon: Field Investigations

A field investigation add-on for [Cobblemon](https://cobblemon.com/) (Fabric, Minecraft 1.21.1).

An unknown Pokémon has been reported nearby. Sample the traces it left behind, dig up buried clues, analyse their DNA in
mini-games, rule out suspects on your case board, then track the target by its cries, scan it from afar and catch it.
The cleaner your investigation, the better the Pokémon and the loot.

Everything fits in one item: the **Field Kit**. No workstation to carry around.

![Case board](docs/gallery/02_case_board.png)

![Scanner](docs/gallery/23_scanner.png)

## How an investigation goes

1. **Open a case.** Right-click with the Field Kit. A trace zone is reported 100–180 blocks away, with a line-up of suspects
   taken from the species that really spawn in that biome.
2. **Sample the traces.** Five traces are hidden in the zone (elemental residue, strange deposit, footprint, abandoned
   nest, signs of a struggle). The kit's proximity radar leads you to them. Each kind of trace has its own sampling
   mini-game, and you need an empty vial for each.
3. **Dig up buried clues.** Some clues (bone, eggshells, fang) are buried one or two blocks underground. The radar gives
   you the exact distance; dig carefully, then uncover the disturbed soil with a vanilla **brush**. Breaking that block
   destroys the clue.
4. **Analyse the samples.** Right-click a sample with the kit on you. Each of the five genetic markers (two elements,
   growth, lineage, aptitude) has two possible lab mini-games.
5. **Rule out suspects.** Every suspect shows its five markers. Compare them with your results and cross out the ones
   that do not fit yourself (right-click); nothing is done for you. With three markers analysed you can name your guess
   (left-click) and start the hunt.
6. **Track it.** Right-click with the kit to pick up the target's cry and get a bearing. Take several bearings from
   different spots: the kit map computes a fix.
7. **Scan it.** Once the target is in sight, hold right-click with the kit, crosshair on the Pokémon, for three seconds.
   Get too close before it is identified and it runs away.
8. **Catch it.** Approach without running, then battle or catch it.

## Stars, IVs and loot

Every investigation is rated from 1 to 5 stars. You start at 5:

| Event | Cost |
| --- | --- |
| Mini-game failure (damaged sample, lost dose, destroyed clue) | −½ star (the first one is free) |
| The target runs away | −1 star |
| Wrong guess | −2 stars |

- **Perfect IVs:** stars − 1 (none at 1 star, four at 5 stars). Shiny odds also rise with the stars.
- **Loot:** rolled from loot tables, one per star level, plus a small table for the rarity of the species and one for
  every fifth solved case.

## Mini-games

| Sampling (field) | Analysis (lab) |
| --- | --- |
| Residual crystals (memory) | Sequencing (code breaking) |
| Pipetting (hold and release) | Strand pairing (reflexes) |
| Footprint measurement (timing) | Centrifugation (timing) |
| Fragment sorting (spot the shells) | Fragment assembly (ordering) |
| Scratch tracing (steady hand) | Spectrum tuning |
| Brush excavation (find the buried fragments) | Electrophoresis (find the identical lane) |
| | Colorimetry (recreate a colour from memory) |
| | PCR cycles (repeat the sequence) |
| | Colony count |

## Requirements

- Minecraft 1.21.1, Fabric Loader 0.16+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cobblemon](https://modrinth.com/mod/cobblemon) 1.8 or newer

Required on **both** the client and the server.

## Recipes

- **Field Kit:** redstone on top, iron ingot / compass / iron ingot in the middle, glass pane at the bottom.
- **Empty Vial (×4):** three glass panes in a V.
- A vanilla **brush** for buried clues.

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `/hunt abandon` | everyone | Drop the current investigation |
| `/hunt gear` | operators | Give a kit, vials and a brush |
| `/hunt reset` | operators | Clear your investigation and its cooldown |
| `/hunt reveal` | operators | Show the hidden details of your investigation (testing) |

## Configuration

`config/cobblemon_field_investigations.json` is created on first launch: distances, number of suspects, markers needed
before the hunt, cooldown between cases, bearing accuracy, flee and scan distances, shiny multiplier, rarity weights,
excluded labels (legendary, mythical, ultra beast and paradox by default).

Loot is data-driven. Override these loot tables with a data pack:

```
data/cobblemon_field_investigations/loot_table/traque/etoiles_1.json … etoiles_5.json   (one per star level)
data/cobblemon_field_investigations/loot_table/traque/rarete_common.json, rarete_uncommon.json, rarete_rare.json, rarete_ultra_rare.json
data/cobblemon_field_investigations/loot_table/traque/palier.json                        (every fifth solved case)
```

## Languages

English and French are included. Everything goes through the language files
(`assets/cobblemon_field_investigations/lang`), so other translations only need a new JSON file.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Java 21 is required.

The `outils/` folder holds the Python scripts (Pillow required) that turn the source images in `outils/pixellab/` into
the textures of the mod, and that write the language files:

- `gen_interfaces.py` – screens, buttons and frames
- `gen_monde.py` – world textures, block models, mini-game art
- `gen_langues.py` – `en_us.json` and `fr_fr.json` (every text of the mod lives in this script)

The source code and its comments are written in French.

## Credits

- Pixel art generated with [PixelLab](https://www.pixellab.ai/) and processed by the scripts in `outils/`.
- The icon follows the frame commonly used by Cobblemon add-ons.
- Not affiliated with Cobblemon, Mojang, Nintendo, Game Freak or The Pokémon Company.

## License

[MIT](LICENSE)
