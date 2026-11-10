# Fiche de publication — Cobblemon: Field Investigations

Tout ce qu'il faut saisir sur Modrinth et CurseForge. Les textes à coller sont en anglais (langue des deux plateformes).

## Fichiers à téléverser

| Quoi | Fichier |
| --- | --- |
| Le mod | `build/libs/cobblemon-field-investigations-1.0.0.jar` |
| Icône (carrée, 768 px) | `icon.png` |
| Description longue | `docs/publication/description.md` (Markdown, accepté par les deux sites) |
| Galerie | `docs/gallery/*.png` : 24 captures en anglais, numérotées dans l'ordre du jeu. Image vedette conseillée : `02_case_board.png` ou `23_scanner.png` |

## Champs communs

| Champ | Valeur |
| --- | --- |
| Nom | `Cobblemon: Field Investigations` |
| Identifiant / slug | `cobblemon-field-investigations` |
| Résumé (une ligne) | `Investigate traces, analyse DNA in mini-games, rule out suspects, then track, scan and catch an unknown Pokémon. A detective add-on for Cobblemon.` |
| Version du mod | `1.0.0` |
| Type de version | Release |
| Minecraft | `1.21.1` |
| Chargeur | Fabric |
| Côté | Client **et** serveur (requis des deux côtés) |
| Licence | MIT |
| Code source | https://github.com/Romain-Michaux/cobblemon-field-investigations |
| Suivi des bugs | https://github.com/Romain-Michaux/cobblemon-field-investigations/issues |
| Java | 21 |

## Dépendances à déclarer

| Mod | Type |
| --- | --- |
| Fabric API | Requise |
| Cobblemon (1.8 ou plus récent) | Requise |

Fabric Language Kotlin arrive avec Cobblemon : inutile de la déclarer.

## Modrinth

- Type de projet : Mod.
- Catégories conseillées : Adventure, Game Mechanics, Minigame (trois au plus en catégories principales).
- Environnement : « Client and server », requis des deux côtés.
- Dans « Links », renseigner Source et Issues.
- Dans la version : chargeur Fabric, jeu 1.21.1, dépendances ci-dessus.
- Monétisation : rien à activer par projet, c'est au niveau du compte (Revenue → informations de paiement).

## CurseForge

- Jeu : Minecraft ; classe : Mods.
- Catégories conseillées : Adventure and RPG (principale), Miscellaneous ; cocher Fabric.
- Licence : MIT License.
- Dans le fichier : version du jeu 1.21.1, Fabric, Java 21, environnement Client + Server, relations « Required Dependency » pour Fabric API et Cobblemon.
- Laisser « Allow 3rd party launchers / API downloads » activé : c'est ce qui permet aux lanceurs (Prism, Modrinth App…) de télécharger le mod, et ces téléchargements comptent pour le programme de récompenses.
- Le premier projet passe par une modération manuelle (souvent un à deux jours).

## Notes de version (à coller dans le changelog de la 1.0.0)

```
First public release.

- Full investigation loop: trace sampling, buried clues, DNA analysis, case board, cry tracking, scanner, capture.
- 15 mini-games (6 in the field, 9 in the lab).
- 5-star case rating driving perfect IVs, shiny odds and loot.
- Data-driven loot tables.
- English and French.
```

## À vérifier avant de cliquer sur « Publier »

- [x] Une enquête complète jouée de bout en bout (testé sur le serveur CobbleScout le 10/11/2026).
- [x] Le mod testé sur un serveur dédié.
- [x] Conditions de PixelLab relues (version du 30/10/2026) : l'article 1.3 autorise l'usage commercial des créations
      et l'article 3.3 leur libre distribution ; seule interdiction, entraîner d'autres modèles avec.
- [x] Icône composée avec le gabarit officiel `Cobblemon_Addon_Frames` (couches bg, border et mod) partagé sur le Discord de Cobblemon.
