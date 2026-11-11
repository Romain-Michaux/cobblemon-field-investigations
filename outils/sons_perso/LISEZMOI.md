# Sons faits à la main

Dépose ici un fichier `<nom>.wav` (ou `.ogg`, `.flac`, `.mp3`) pour remplacer le son synthétisé du même nom, puis lance
`python outils/gen_sons.py` et recompile. Le fichier est converti en OGG mono et mis au bon niveau automatiquement.

Sur https://www.bfxr.net/ : choisis une famille en haut à gauche, clique jusqu'à obtenir un son qui te plaît
(« Mutate » pour des variantes), puis « Export WAV » et renomme le fichier.

| Nom du fichier | Où on l'entend | Famille Bfxr conseillée |
| --- | --- | --- |
| `clic` | tous les boutons | Blip/Select |
| `tic` | le temps presse (dernier quart du chrono) | Blip/Select |
| `carillon` | une étape franchie, quelque chose à mémoriser | Pickup/Coin, Plucked |
| `reussite` | mini-jeu réussi | Jingles, Powerup |
| `echec` | mini-jeu raté | Hit/Hurt |
| `identifie` | cible identifiée au scanner | Jingles, Powerup |
| `grade` | montée de rang | Jingles |
| `scan` | bip du scanner, filtrage du cri, pipette | Blip/Select, Sonar |
| `papier` | une fiche qu'on retourne | Rustler, Whoosh |
| `tamis` | tamisage | Rustler |
| `platre` | moulage, plâtre qui coule | Squishy |
| `debordement` | moulage, ça déborde | Squishy |
| `pince` | la pince se referme | Blip/Select, Bonks |
| `pluck` | brin arraché, objet saisi | Plucked, Pickup/Coin |
| `obturateur` | photo | Cracker, Blip/Select |
| `frottement` | écouvillon, griffures | Rustler |
| `pinceau` | dégagement au pinceau | Rustler, Whoosh |
| `fiole` | la fiole se remplit | Squishy |
| `bulle` | apnée | Squishy |
| `coeur` | apnée, l'air manque | Bonks |
| `corde` | rappel, la corde file | Whoosh |
| `mousqueton` | rappel, arrêt sur une trace | Bonks, Tangs |
| `branche` | grimpe, branche saisie | Bonks |
| `craquement` | grimpe, branche ratée ; tamis, fragment perdu | Cracker |
| `choc` | tri, objets qui se cognent | Bonks |
| `eboulement` | éboulis, pierre encore chargée | Boomer, Explosion |
| `pioche` | éboulis, coup sur une pierre massive | Tangs, Bonks |
| `pierre` | éboulis, empreinte dentaire, moulage terminé | Bonks |
| `goutte` | dosage, chromatographie | Plucked, Squishy |
| `molette` | microscope | Blip/Select |
| `glace` | dégel, un palier franchi | Cracker |
| `flamme` | dégel, on chauffe | Breath, Whoosh |
| `sifflement` | dégel, surchauffe | Breath |
| `lampe` | lampe UV | Motors |
| `ambiance_labo` | boucle du séquenceur (doit boucler proprement) | - |
