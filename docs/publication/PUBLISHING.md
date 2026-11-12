# Publishing checklist — Cobblemon: Field Investigations

Everything to fill in on Modrinth and CurseForge. The texts to paste are in English (the language of both platforms).

## Files to upload

| What | File |
| --- | --- |
| The mod | `build/libs/cobblemon-field-investigations-1.1.0.jar` |
| Icon (square, 768 px) | `icon.png` |
| Long description | `docs/publication/description.md` (Markdown, accepted by both sites) |
| Gallery | `docs/gallery/*.png`: 24 screenshots in English, numbered in game order. Suggested featured image: `02_case_board.png` or `23_scanner.png` |

## Common fields

| Field | Value |
| --- | --- |
| Name | `Cobblemon: Field Investigations` |
| Identifier / slug | `cobblemon-field-investigations` |
| Summary (one line) | `Investigate traces, analyse DNA in mini-games, rule out suspects, then track, scan and catch an unknown Pokémon. A detective add-on for Cobblemon.` |
| Mod version | `1.1.0` |
| Release type | Release |
| Minecraft | `1.21.1` |
| Loader | Fabric |
| Side | Client **and** server (required on both) |
| License | MIT |
| Source code | https://github.com/Romain-Michaux/cobblemon-field-investigations |
| Issue tracker | https://github.com/Romain-Michaux/cobblemon-field-investigations/issues |
| Java | 21 |

## Dependencies to declare

| Mod | Type |
| --- | --- |
| Fabric API | Required |
| Cobblemon (1.8 or newer) | Required |

Fabric Language Kotlin comes with Cobblemon: no need to declare it.

## Modrinth

- Project type: Mod.
- Suggested categories: Adventure, Game Mechanics, Minigame (three at most as main categories).
- Environment: "Client and server", required on both sides.
- Under "Links", fill in Source and Issues.
- In the version: Fabric loader, game 1.21.1, dependencies above.
- Monetisation: nothing to enable per project, it is set at account level (Revenue → payment details).

## CurseForge

- Game: Minecraft; class: Mods.
- Suggested categories: Adventure and RPG (main), Miscellaneous; tick Fabric.
- License: MIT License.
- In the file: game version 1.21.1, Fabric, Java 21, environment Client + Server, "Required Dependency" relations for Fabric API and Cobblemon.
- Leave "Allow 3rd party launchers / API downloads" enabled: it lets launchers (Prism, Modrinth App…) download the mod, and those downloads count for the rewards programme.
- The first project goes through manual moderation (often one or two days).

## Release notes

- 1.1.0: paste `docs/publication/changelog_1.1.0.md` (pick the Markdown format in CurseForge's changelog field).
- 1.0.0 (history): "First public release" + full loop, 15 mini-games, 5-star rating, data-pack loot, English and French.

## Check before clicking "Publish"

- [x] One full investigation played from start to finish (tested on the CobbleScout server on 2026-11-10).
- [x] The mod tested on a dedicated server.
- [x] PixelLab terms reread (version of 2026-10-30): section 1.3 allows commercial use of the creations and section 3.3
      their free distribution; the only restriction is training other models with them.
- [x] Icon made with the official `Cobblemon_Addon_Frames` template (bg, border and mod layers) shared on the Cobblemon Discord.
