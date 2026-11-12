# Hand-made sounds

Drop a `<name>.wav` file here (or `.ogg`, `.flac`, `.mp3`) to replace the synthesised sound of the same name, then run
`python tools/gen_sounds.py` and rebuild. The file is converted to mono OGG and its level is set automatically.

On https://www.bfxr.net/: pick a family at the top left, click until you get a sound you like ("Mutate" for
variations), then "Export WAV" and rename the file.

| File name | Where it is heard | Suggested Bfxr family |
| --- | --- | --- |
| `click` | every button | Blip/Select |
| `tick` | time is running out (last quarter of the timer) | Blip/Select |
| `chime` | a step is done, something to memorise | Pickup/Coin, Plucked |
| `success` | mini-game passed | Jingles, Powerup |
| `failure` | mini-game failed | Jingles (Failure), Hit/Hurt |
| `identified` | target identified by the scanner | Jingles, Powerup |
| `rank_up` | rank up | Jingles |
| `scan` | scanner beep, cry filter, pipette | Blip/Select, Sonar |
| `paper` | a card being turned over | Rustler, Whoosh |
| `sieve` | sieving | Rustler |
| `plaster` | plaster cast, plaster pouring | Squishy |
| `overflow` | plaster cast, overflowing | Squishy |
| `tweezers` | the tweezers close | Blip/Select, Bonks |
| `pluck` | strand pulled out, object picked up | Plucked, Pickup/Coin |
| `shutter` | photo | Cracker, Blip/Select |
| `rub` | swab, scratches | Rustler |
| `brush` | brush excavation | Rustler, Whoosh |
| `vial` | the vial fills up | Squishy |
| `bubble` | free dive | Squishy |
| `heartbeat` | free dive, running out of air | Bonks |
| `rope` | abseil, the rope runs | Whoosh |
| `carabiner` | abseil, stop on a trace | Bonks, Tangs |
| `branch` | tree climb, branch grabbed | Bonks |
| `crack` | tree climb, branch missed; sieve, fragment lost | Cracker |
| `knock` | sorting, objects knocking together | Bonks |
| `rockslide` | rockfall, stone still carrying weight | Boomer, Explosion |
| `pickaxe` | rockfall, hit on a solid stone | Tangs, Bonks |
| `stone` | rockfall, bite cast, plaster cast done | Bonks |
| `drop` | titration, chromatography | Plucked, Squishy |
| `knob` | microscope | Blip/Select |
| `ice` | thawing, a step done | Cracker |
| `flame` | thawing, heating | Breath, Whoosh |
| `whistle` | thawing, overheating | Breath |
| `lamp` | UV lamp | Motors |
