# Language files of the mod (English by default, French included), and routing of the client's UI texts through them.
#  - UI: (English text as written in the client code, French text). Each text gets the key
#    cobblemon_field_investigations.ui.<name> (built from its first English words), and the literal in the code is
#    replaced by T.f("<name>").
#  - KEYS: everything else (server announcements, trace names, markers, mini-games, advancements).
#  - ITEMS: items and blocks (full keys).
# Usage: python tools/gen_lang.py   (run it again after adding a text; tools/extract_texts.py lists the ones left behind)
import io
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
LANG_DIR = os.path.join(HERE, "..", "src", "main", "resources", "assets", "cobblemon_field_investigations", "lang")
CLIENT = os.path.join(HERE, "..", "src", "main", "java", "io", "github", "romainmichaux", "fieldinvestigations", "client")

UI = [
    # --- shared ---
    ("DNA Sequencer", "Séquenceur ADN"),
    ("DNA SEQUENCER · ", "SÉQUENCEUR ADN · "),
    (" · doses: ", " · doses : "),
    (" · first mistake is free", " · première erreur sans frais"),
    ("PERFECT SAMPLE", "PRÉLÈVEMENT PARFAIT"),
    ("SAMPLE TAKEN", "PRÉLÈVEMENT RÉUSSI"),
    ("SAMPLE DAMAGED", "PRÉLÈVEMENT ABÎMÉ"),
    ("one extra dose", "une dose de plus"),
    ("full sample", "échantillon complet"),
    ("a single dose · −½ star", "une seule dose · −½ étoile"),
    ("ANALYSIS COMPLETE", "ANALYSE RÉUSSIE"),
    ("ANALYSIS FAILED", "ANALYSE RATÉE"),
    ("marker ", "marqueur "),
    (" added to the case file", " ajouté au dossier"),
    ("the sequencer saved the dose", "le séquenceur a sauvé la dose"),
    ("one dose lost · −½ star", "une dose perdue · −½ étoile"),
    # --- sequencing ---
    ("Read", "Lire"),
    ("right place", "bien placée"),
    ("wrong place", "mal placée"),
    ("Reads left: ", "Lectures : "),
    ("Click the slots to pick the 4 bases, then Read. You have 6 reads to find the sequence.",
     "Clique les cases pour choisir les 4 bases, puis Lire. Tu as 6 lectures pour retrouver la séquence."),
    # --- strand pairing ---
    ("Strand to complete", "Brin à compléter"),
    ("Mistakes: ", "Erreurs : "),
    ("Give the base that pairs with the big slot (A with T, C with G) before the bar runs out. Buttons or keys A, T, C, G.",
     "Donne la base complémentaire de la grande case (A avec T, C avec G) avant la fin de la barre. Boutons ou touches A, T, C, G."),
    # --- centrifugation ---
    ("Phases separated: ", "Phases séparées : "),
    ("Misses: ", "Ratés : "),
    ("Stop (Space)", "Arrêter (Espace)"),
    ("Click (or Space) when the needle crosses the green zone. Three good stops; the zone shrinks and the rotor speeds up.",
     "Clique (ou Espace) quand l'aiguille passe dans la zone verte. Trois arrêts réussis ; la zone rétrécit et le rotor accélère."),
    # --- fragments ---
    ("The end of a fragment", "La fin d'un fragment"),
    ("= the start of the next", "= le début du suivant"),
    ("Correct joints: ", "Jonctions justes : "),
    ("Tries: ", "Essais : "),
    ("Assemble", "Assembler"),
    ("Click two fragments to swap them. In the right order, the last 2 bases of each fragment are the first 2 of the next.",
     "Clique deux fragments pour les échanger. Dans le bon ordre, les 2 dernières bases de chaque fragment sont les 2 premières du suivant."),
    # --- spectrum ---
    ("Frequency", "Fréquence"),
    ("Amplitude", "Amplitude"),
    ("Phase", "Phase"),
    ("grey: sample", "gris : échantillon"),
    ("Match: ", "Accord : "),
    ("Tune frequency, amplitude and phase until your curve (blue) overlaps the sample's (grey), before the bar runs out.",
     "Règle fréquence, amplitude et phase jusqu'à superposer ta courbe (bleue) à celle de l'échantillon (grise), avant la fin de la barre."),
    # --- kit ---
    ("Field Kit", "Kit de terrain"),
    ("standby", "en veille"),
    ("sampling", "prélèvements"),
    ("tracking", "traque"),
    ("spotting", "repérage"),
    ("approach", "approche"),
    ("flushed out", "débusqué"),
    ("FIELD KIT", "KIT DE TERRAIN"),
    ("No investigation in progress", "Aucune enquête en cours"),
    ("SAMPLES", "ÉCHANTILLONS"),
    ("clue destroyed", "indice détruit"),
    ("analysed · in the file", "analysé · au dossier"),
    ("sampled · to analyse", "prélevé · à analyser"),
    ("uncovered · to sample", "dégagé · à prélever"),
    ("buried · brush needed", "enfoui · pinceau requis"),
    ("to sample in the zone", "à prélever dans la zone"),
    ("TRACE ZONE", "ZONE DES TRACES"),
    (" blocks · ", " blocs · "),
    ("Case file", "Dossier"),
    ("1 square = 50 blocks", "1 carreau = 50 blocs"),
    ("BEARINGS (", "RELEVÉS ("),
    ("TARGET IN SIGHT", "CIBLE EN VUE"),
    ("FLUSHED OUT", "DÉBUSQUÉ"),
    ("The target is in sight. Keep your distance and scan it: hold right-click with the kit, crosshair on the Pokémon, for three seconds.",
     "La cible est en vue. Reste à distance et scanne-la : clic droit maintenu avec le kit, viseur sur le Pokémon, pendant trois secondes."),
    ("If you get close before identifying it, it runs away.", "Si tu t'approches avant de l'avoir identifiée, elle file."),
    (" perfect IVs on capture", " IV parfaits à la capture"),
    ("There it is: battle it or catch it.", "Il est là : combats-le ou capture-le."),
    ("Walk, don't run: a few blocks away, it will be flushed out.", "Avance sans courir : à quelques blocs, elle sera débusquée."),
    ("Fix", "Recoupement"),
    (" blocks", " blocs"),
    ("White: your bearings. Bright line: bearing heard, dark lines: margin of error. Yellow circle: likely area. ",
     "Blanc : tes relevés. Trait clair : cap entendu, traits sombres : marge d'erreur. Cercle jaune : zone probable. "),
    ("The closer you are, the more accurate the bearing.", "Plus tu es près, plus le cap est précis."),
    # --- case board ---
    ("common", "commune"),
    ("uncommon", "peu commune"),
    ("rare", "rare"),
    ("very rare", "très rare"),
    ("Investigation Board", "Tableau d'enquête"),
    ("CASE BOARD", "TABLEAU D'ENQUÊTE"),
    ("Open an investigation to get traces to sample (kit, empty vials, brush), then cross out the suspects that do not fit yourself (right-click).",
     "Ouvre une enquête : des traces à prélever (kit, fioles vides, pinceau), puis à toi de barrer les suspects qui ne collent pas (clic droit)."),
    ("Each star you keep (5 at best) past the first gives the caught Pokémon a perfect IV, and better loot.",
     "Chaque étoile au-delà de la première donne un IV parfait au Pokémon capturé (4 au mieux) et un meilleur butin."),
    ("Open an investigation", "Ouvrir une enquête"),
    ("Next report in ", "Prochain signalement dans "),
    (" min", " min"),
    ("Cases solved: ", "Enquêtes résolues : "),
    ("Species: ", "Espèce "),
    ("to analyse", "à analyser"),
    ("to dig up", "à déterrer"),
    ("to sample", "à prélever"),
    ("Zone: ", "Zone : "),
    ("> compass · ", "> boussole · "),
    ("Hunt in progress", "Traque en cours"),
    ("> kit map", "> carte du kit"),
    ("Results: ", "Résultats : "),
    ("Pick a suspect", "Désigne un suspect"),
    ("Track ", "Traquer "),
    ("Start the hunt", "Lancer la traque"),
    ("Guess: ", "Pronostic : "),
    ("Target flushed out!", "Cible débusquée !"),
    ("In the field with the kit", "Sur le terrain avec le kit"),
    ("Give up", "Abandonner"),
    ("Investigation quality: ", "Qualité de l'enquête : "),
    (" perfect IVs §7on the caught Pokémon", " IV parfaits §7sur le Pokémon capturé"),
    ("§7and richer loot with each star", "§7et un butin plus riche à chaque étoile"),
    ("§8(one star = no perfect IV, five = four)", "§8(une étoile = aucun IV parfait, cinq = quatre)"),
    ("§8Mini-game failure: −½ (", "§8Raté de mini-jeu : −½ ("),
    ("§8Target ran away: −1 (", "§8Fuite de la cible : −1 ("),
    ("§8Wrong guess: −2", "§8Mauvais pronostic : −2"),
    (" (ruled out)", " (écarté)"),
    # --- electrophoresis ---
    ("smp.", "éch."),
    ("Gel", "Gel"),
    ("Err.", "Err."),
    ("Yes!", "Oui !"),
    ("No", "Non"),
    ("Click the lane identical to the sample (smp.): position, thickness and brightness of every band matter. Three gels; two mistakes at most.",
     "Clique la piste identique à l'échantillon (éch.) : position, épaisseur et éclat de chaque bande comptent. Trois gels ; deux erreurs au plus."),
    # --- colorimetry ---
    ("Red", "Rouge"),
    ("Yellow", "Jaune"),
    ("Blue", "Bleu"),
    ("White", "Blanc"),
    ("Memorise!", "Mémorise !"),
    ("Target", "Cible"),
    ("Your mix", "Ton mélange"),
    ("· passed", "· réussi"),
    ("· failed", "· raté"),
    ("needed: ", "seuil : "),
    (" drops", " gouttes"),
    ("Flask ", "Fiole "),
    ("Fails: ", "Échecs : "),
    ("Empty", "Vider"),
    ("Confirm", "Valider"),
    ("Memorise the colour, then recreate it from memory by mixing the reagents. You need at least 85 % likeness. Three flasks; two fails at most.",
     "Mémorise la teinte, puis recrée-la de tête en mélangeant les réactifs. Il faut au moins 85 % de ressemblance. Trois fioles ; deux échecs au plus."),
    # --- PCR ---
    ("Cycle ", "Cycle "),
    ("Watch the program…", "Observe le programme…"),
    ("Your turn: repeat it", "À toi : reproduis-le"),
    ("The program gets one step longer each cycle.", "Le programme s'allonge d'un palier à chaque cycle."),
    ("Watch the sequence of temperature steps, then repeat it by clicking the buttons in the same order. Three cycles; two mistakes at most.",
     "Observe la suite de paliers de température, puis reproduis-la en cliquant les boutons dans le même ordre. Trois cycles ; deux erreurs au plus."),
    # --- colony count ---
    ("Dish ", "Boîte "),
    (" / 3 · mistakes ", " / 3 · erreurs "),
    ("Correct: ", "Exact : "),
    (" colonies", " colonies"),
    ("Wrong: there were ", "Raté : il y en avait "),
    ("Count the colonies…", "Compte les colonies…"),
    ("How many were there?", "Combien y en avait-il ?"),
    ("The dish is only visible for a moment: count the colonies, then pick the right number. Three dishes; two mistakes at most.",
     "La boîte n'est visible qu'un instant : compte les colonies, puis choisis le bon nombre. Trois boîtes ; deux erreurs au plus."),
    # --- crystals ---
    ("Deposit ", "Gisement "),
    ("Watch closely…", "Observe bien…"),
    ("Deposit sampled", "Gisement prélevé"),
    ("Your turn: sample", "À toi : prélève"),
    ("stable: ", "stable : "),
    ("unstable: avoid", "instable : évite"),
    ("Faults: ", "Fautes : "),
    ("No fault: one extra dose.", "Sans faute : une dose de plus."),
    ("Stable (teal) and unstable (red) crystals glow for a moment: memorise them, then sample the stable ones without touching the red ones. Three faults at most.",
     "Cristaux stables (turquoise) et instables (rouges) brillent un instant : mémorise, puis prélève les stables sans toucher aux rouges. Trois fautes au plus."),
    # --- pipetting ---
    ("Dose ", "Dose "),
    ("Right dose!", "Dose juste !"),
    ("Overflow…", "Débordement…"),
    ("Wrong dose", "Dose à côté"),
    ("Release in the green", "Relâche dans le vert"),
    ("Hold the click", "Maintiens le clic"),
    ("The green zone shrinks and the suction speeds up with each dose.", "La zone verte rétrécit et l'aspiration accélère à chaque dose."),
    ("Hold the click to draw liquid, release when the level is in the green zone. Three right doses to get; three faults damage the sample.",
     "Maintiens le clic pour aspirer, relâche quand le niveau est dans la zone verte. Trois doses justes à réussir ; trois fautes abîment l'échantillon."),
    # --- measurement ---
    ("left edge", "bord gauche"),
    ("right edge", "bord droit"),
    ("top edge", "bord haut"),
    ("bottom edge", "bord bas"),
    ("Jaw ", "Mâchoire "),
    ("Width: ", "Largeur : "),
    (" cm", " cm"),
    ("Length: ", "Longueur : "),
    ("Click when the ruler touches the requested edge.", "Clique quand la règle touche le bord demandé."),
    ("Click when the ruler touches the left edge of the footprint, then the right, top and bottom. Two bad measurements spoil the reading.",
     "Clique quand la règle touche le bord gauche de l'empreinte, puis le droit, le haut et le bas. Deux mesures ratées faussent le relevé."),
    # --- sorting ---
    ("Shells", "Coquilles"),
    ("Faults", "Fautes"),
    ("Pick up the six eggshell shards among the twigs, leaves and pebbles before the bar runs out. Three mistakes at most.",
     "Ramasse les six éclats de coquille parmi les brindilles, feuilles et cailloux avant la fin de la barre. Trois erreurs au plus."),
    # --- scratches ---
    ("Scratch ", "Griffure "),
    ("Slips: ", "Écarts : "),
    ("Follow the groove…", "Suis l'entaille…"),
    ("Release and retry", "Relâche et reprends"),
    ("Start from the dot", "Pars du point"),
    ("Hold the click, from top to bottom.", "Clic maintenu, du haut vers le bas."),
    ("Hold the click on the starting dot, then follow the groove to the bottom without straying. Three scratches; three slips damage the reading.",
     "Clic maintenu sur le point de départ, puis suis l'entaille jusqu'en bas sans t'en écarter. Trois griffures ; trois écarts abîment le relevé."),
    # --- excavation ---
    ("Uncovered: ", "Dégagé : "),
    ("Snags: ", "Accrocs : "),
    ("Too rough!", "Trop brusque !"),
    ("Brush gently", "Brosse doucement"),
    ("Hold the click: sweep the soil in the middle.", "Clic maintenu : balaie la terre au centre."),
    ("Hold the click: sweep the soil to uncover 90 % of the clue before the bar runs out. A rough stroke damages it (three snags at most).",
     "Clic maintenu : balaie la terre pour dégager l'indice à 90 % avant la fin de la barre. Un geste trop brusque l'abîme (trois accrocs au plus)."),
    # --- radar ---
    ("no sign", "aucun signe"),
    ("faint signs", "signes ténus"),
    ("clear signs", "signes nets"),
    ("fresh signs", "signes frais"),
    ("trace very close", "trace toute proche"),
    ("right here", "juste là"),
    ("All sampled", "Tout est prélevé"),
    ("buried · ", "enfoui · à "),
    ("buried clue", "indice enfoui"),
    ("zone at ", "zone à "),
    ("very far", "très lointain"),
    ("far", "lointain"),
    ("clear", "net"),
    ("close", "proche"),
    ("Cry: ", "Cri "),
    ("Right-click: listen", "Clic droit : écoute"),
    ("Recalibrating…", "Recalibrage…"),
    ("Sneak: map", "Accroupi : carte"),
    ("Scanning… ", "Analyse… "),
    ("Scan the target", "Scanne la cible"),
    ("not your target", "pas ta cible"),
    ("too close: back off!", "trop près : recule !"),
    ("right-click · ", "clic droit · à "),
    ("Target at ", "Cible à "),
    ("DON'T RUN!", "NE COURS PAS !"),
    ("Approach quietly", "Approche en douceur"),
    ("Flushed out!", "Débusqué !"),
    ("catch it", "capture-le"),
    # --- scanner ---
    ("SCANNER", "SCANNER"),
    ("This is not your target", "Ce n'est pas ta cible"),
    ("Locking on…", "Verrouillage…"),
    ("Aim at the Pokémon", "Vise le Pokémon"),
    # --- suspects ---
    ("§8Left-click: name this suspect", "§8Clic gauche : désigner ce suspect"),
    ("§8Right-click: rule out / restore", "§8Clic droit : écarter / rétablir"),
    ("§aAlready caught", "§aDéjà capturé"),
    ("§7Not caught yet", "§7Pas encore capturé"),
    # --- small words ---
    ("at ", "à "),
    # --- brush dig ---
    ("Shards: ", "Éclats : "),
    ("They are buried anywhere: brush around to find them.", "Ils sont enfouis n'importe où : cherche en brossant."),
    ("Brush (hold)", "Brosser (maintenu)"),
    ("The earth keeps what it buried. Brush it away gently, listen for what sounds hollow, and bring the three shards back to the light.",
     "La terre garde ce qu'elle a enfoui. Brosse-la avec douceur, écoute ce qui sonne creux, et ramène les trois éclats à la lumière."),
]

UI += [
    # --- 1.1: reports ---
    ("Check the reports", "Consulter les signalements"),
    ("Pick your investigation", "Choisis ton enquête"),
    ("Lead ", "Piste "),
    ("Nocturnal", "Nocturne"),
    ("Skittish", "Craintive"),
    ("Exceptional", "Exceptionnelle"),
    ("Standard", "Ordinaire"),
    ("Species", "Espèce"),
    ("Zone", "Zone"),
    ("Never caught", "Jamais capturée"),
    ("Already caught", "Déjà capturée"),
    ("Reports valid for another ", "Signalements valables encore "),
    ("Investigation: ", "Enquête "),
    ("§7Traces and cries only at night.", "§7Traces et cris seulement la nuit."),
    ("§7The target only comes out after dark.", "§7La cible ne se montre que de nuit."),
    ("§7It senses you from much farther:", "§7Elle te repère de bien plus loin :"),
    ("§7scan it from a long distance.", "§7scanne-la à grande distance."),
    ("§7One more analysis result required,", "§7Un résultat d'analyse de plus exigé,"),
    ("§7and suspects that all look alike.", "§7et des suspects très ressemblants."),
    ("§7A regular investigation.", "§7Une enquête classique."),
    ("Shiny odds ×2 · special loot", "Chromatique : chance ×2 · butin spécial"),
    ("Double special loot", "Butin spécial doublé"),
    ("Hidden ability · special loot", "Talent caché garanti · butin spécial"),
    ("IVs and loot by your stars", "IV et butin selon tes étoiles"),
    ("§8Click: open this investigation", "§8Clic : ouvrir cette enquête"),
    ("Colour aid: ", "Aide couleurs : "),
    ("on", "oui"),
    ("off", "non"),
    ("Wait for nightfall", "Attends la nuit"),
    ("no traces by day", "traces invisibles de jour"),
    ("target asleep: no cry", "la cible dort : aucun cri"),
    # --- 1.1: biome kinds ---
    ("Restless", "Migratrice"),
    ("Aquatic", "Aquatique"),
    ("Dig site", "Fouille"),
    ("Canopy", "Cimes"),
    ("Highlands", "Altitude"),
    ("Frost", "Givre"),
    ("Mist", "Brume"),
    ("§7It moves twice as often,", "§7Elle change d'endroit deux fois"),
    ("§7and its lair is farther away.", "§7plus souvent, et part plus loin."),
    ("§7Traces on the bottom, target in", "§7Traces au fond de l'eau, cible"),
    ("§7the water: be ready to dive.", "§7dans l'eau : prévois de quoi plonger."),
    ("§7Every remain is buried:", "§7Tous les restes sont enfouis :"),
    ("§7dig and brush with care.", "§7creuse et brosse avec soin."),
    ("§7Traces on trunks and up in the", "§7Traces sur les troncs et à la"),
    ("§7treetops: look up.", "§7cime des arbres : lève les yeux."),
    ("§7Traces on rock faces, across", "§7Traces sur les parois rocheuses,"),
    ("§7a much wider zone.", "§7dans une zone bien plus vaste."),
    ("§7Frozen, fragile samples:", "§7Échantillons gelés et fragiles :"),
    ("§7one dose fewer per sample.", "§7une dose de moins par prélèvement."),
    ("§7Cries carry poorly: bearings", "§7Les cris portent mal : relevés"),
    ("§7are much less precise.", "§7nettement moins précis."),
    ("Special loot · water items", "Butin spécial · objets aquatiques"),
    ("Special loot · fossils", "Butin spécial · fossiles"),
    ("Special loot · forest items", "Butin spécial · objets de forêt"),
    ("Special loot · rare stones", "Butin spécial · pierres rares"),
    ("Special loot · ice items", "Butin spécial · objets de glace"),
    ("Special loot · swamp items", "Butin spécial · objets des marais"),
]

# --- 1.1: new mini-games and kit tabs (texts already listed above are skipped) ---
NEW_TEXTS = [
    ("Cry bearing", "Relevé de cri"), ("CRY ISOLATED", "CRI ISOLÉ"), ("CRY LOST IN NOISE", "CRI BROUILLÉ"),
    ("precise bearing", "relevé précis"), ("less precise bearing", "relevé moins précis"),
    # sieving
    ("Sand: ", "Sable : "), ("Lost: ", "Perdus : "), ("Shaking", "Secousse"),
    ("Let go before the gauge is full.", "Relâche avant que la jauge soit pleine."),
    ("Hold the click to shake the sieve, in bursts: if the shaking gauge fills up, a fragment falls through.",
     "Maintiens le clic pour secouer le tamis, par à-coups : si la jauge de secousse se remplit, un fragment passe à travers."),
    # plaster cast
    ("Hollows filled: ", "Creux remplis : "), ("Spilled: ", "Débordés : "),
    ("Stop in the green band, just under the line.", "Arrête-toi dans la bande verte, juste sous le trait."),
    ("Put the spout over a hollow and hold the click to pour plaster. Fill each hollow up to the green band without going over the line.",
     "Place le bec sur un creux et maintiens le clic pour couler le plâtre. Remplis chaque creux jusqu'à la bande verte, sans dépasser le trait."),
    # tweezers
    ("Fibres: ", "Fibres : "), ("Misses: ", "Ratés : "), ("Tweezers closed", "Pince fermée"), ("Tweezers open", "Pince ouverte"),
    ("The tips turn green when they grip.", "Les pointes passent au vert quand elles serrent."),
    ("Line the tweezers up with a fibre and click the moment they close. They beat faster and faster.",
     "Aligne la pince sur une fibre et clique à l'instant où elle se referme. Elle bat de plus en plus vite."),
    # photo
    ("Sharp photo", "Photo nette"), ("Blurry photo", "Photo floue"), ("Badly framed", "Mal cadrée"), ("Photos: ", "Photos : "),
    ("Failed: ", "Ratées : "), ("Sharpness", "Netteté"), ("Light", "Lumière"),
    ("Follow the trace with the viewfinder and click when it is sharp (green viewfinder). Three photos before the light fades.",
     "Suis la trace avec le viseur et clique quand elle est nette (viseur vert). Trois photos avant que la lumière baisse."),
    # swabbing
    ("Swabbed: ", "Prélevé : "), ("Soiled: ", "Souillé : "),
    ("You may lift the swab and start again elsewhere.", "Tu peux lever l'écouvillon pour reprendre ailleurs."),
    ("Hold the click and rub the whole purple stain. Do not go back over a spot that is already clear: the swab would be contaminated.",
     "Maintiens le clic et frotte toute la tache violette. Ne repasse pas sur une zone déjà claire : l'écouvillon serait contaminé."),
    # microscope
    ("Slide ", "Lame "), ("Mistakes: ", "Erreurs : "),
    ("Focus with the knobs (<< >> coarse, < > fine). Once the image is sharp, click the cell that has two nuclei.",
     "Règle la mise au point avec les molettes (<< >> grossière, < > fine). Une fois l'image nette, clique la cellule à deux noyaux."),
    # chromatography
    ("Strips: ", "Bandelettes : "),
    ("The small grey mark shows the minimum gap between two bands.", "Le petit repère gris donne l'écart minimal entre deux bandes."),
    ("Click a strip to stop it once its three bands are well apart, but before the solvent reaches the top.",
     "Clique une bandelette pour l'arrêter quand ses trois bandes sont bien séparées, mais avant que le solvant atteigne le haut."),
    # family tree
    ("Lineage ", "Lignée "), ("Sample", "Échantillon"),
    ("At each node, take the branch that matches the sample.", "À chaque nœud, prends la branche qui ressemble à l'échantillon."),
    ("Start at the top: at each fork, follow the branch whose symbol matches the sample, then click the box you end up on.",
     "Pars du haut : à chaque embranchement, suis la branche dont le symbole correspond à l'échantillon, puis clique la case où tu arrives."),
    # titration
    ("Too slow", "Trop lent"), ("Clean endpoint", "Virage net"), ("Endpoint overshot", "Virage dépassé"), ("Titration ", "Dosage "),
    ("Drops: ", "Gouttes : "), ("Drop", "Goutte"), ("Squirt ×3", "Jet ×3"),
    ("Pour until the liquid turns bright pink. Squirt at first, then go drop by drop as soon as it blushes: two drops too many and it fails.",
     "Verse jusqu'au virage au rose vif. Va vite au jet, puis goutte à goutte dès que le liquide rosit : dépasser de deux gouttes rate le dosage."),
    # bite cast
    ("Bite ", "Morsure "), ("Cast ", "Moulage "), ("Slide the cast", "Glisser le moulage"), ("Fit", "Emboîter"),
    ("Only one of the three casts matches the bite. Pick it, slide it until every tooth faces its hollow, then fit it.",
     "Un seul des trois moulages épouse la morsure. Choisis-le, fais-le glisser pour mettre chaque dent face à son creux, puis emboîte."),
    # free dive
    ("Air", "Air"), ("Vial", "Fiole"),
    ("Stay on the trace. 40% air left at the end: one extra dose.", "Suis la trace sans la lâcher. 40 % d'air restant : une dose de plus."),
    ("Hold the click on the glowing trace to fill the vial. The current moves it, and your air is running out.",
     "Maintiens le clic sur la trace lumineuse pour remplir la fiole. Le courant la déplace, et ta réserve d'air baisse."),
    # thawing
    ("Thawed: ", "Dégel : "), ("Overheats: ", "Surchauffes : "),
    ("The ice only melts in the green range. It drifts: follow it.", "La glace ne fond que dans la plage verte. Elle se déplace : accompagne-la."),
    ("Hold the click to heat, let go to cool down. Keep the temperature in the green range without reaching the red.",
     "Maintiens le clic pour chauffer, relâche pour laisser refroidir. Garde la température dans la plage verte sans atteindre le rouge."),
    # cry filter
    ("Clarity", "Clarté"), ("Cry isolated", "Cri isolé"),
    ("Trust the sound: the higher it gets, the closer you are.", "Fie-toi au son : plus il est aigu, plus tu es près."),
    ("Move the mouse left and right to tune the filter. Keep the cry clear long enough: the right frequency drifts.",
     "Déplace la souris de gauche à droite pour régler le filtre. Garde le cri net assez longtemps : la bonne fréquence dérive."),
    # tree climb
    ("Holds: ", "Prises : "), ("Slips: ", "Glissades : "),
    ("Click on the side of the hold as it crosses the light band. The pace picks up.",
     "Clique du côté de la prise quand elle traverse la bande claire. Le rythme s'accélère."),
    ("Holds scroll down the trunk. Click left or right, on the side of the hold, when it passes through the light band.",
     "Les prises défilent le long du tronc. Clique à gauche ou à droite, du côté de la prise, quand elle passe dans la bande claire."),
    # abseil
    ("Traces: ", "Traces : "), ("Impacts: ", "Chocs : "), ("Rope ", "Corde "), ("Trace taken", "Trace prélevée"), ("Too fast!", "Trop vite !"),
    ("Swing", "Se balancer"), ("Haul in", "Remonter"), ("Pay out", "Filer"),
    ("Swing from boulder to boulder and slip down to the traces without ever grazing the rock.",
     "Balance-toi d'un rocher à l'autre et glisse jusqu'aux traces sans jamais effleurer la roche."),
    # UV lamp
    ("Stains: ", "Taches : "), ("Battery", "Batterie"),
    ("Stains only glow under the beam. A click on nothing drains the battery.",
     "Les taches ne brillent que sous le faisceau. Un clic dans le vide use la batterie."),
    ("Sweep the ground with the lamp: stains only show inside the beam. Click each one before the battery runs out.",
     "Balaie le sol avec la lampe : les taches n'apparaissent que dans le faisceau. Clique chacune avant que la batterie soit vide."),
]
NEW_TEXTS += [
    # chromatography
    ("Too far", "Trop tard"), ("Blends: ", "Mélanges : "), ("Blend identified!", "Mélange identifié !"), ("Not this blend", "Pas ce mélange"),
    ("Lift the rack", "Retirer le portoir"), ("Name two references", "Désigner deux références"),
    ("Every trace is a blend of known inks. Let the solvent climb, lift the rack once you can read the sample, then name the two references it is made of.",
     "Chaque trace mêle des encres connues. Laisse le solvant monter, retire le portoir quand tu sais lire l'échantillon, puis désigne les deux références qui le composent."),
]

NEW_TEXTS += [
    # centrifugation
    ("Brake", "Freiner"), ("Separated!", "Séparé !"), ("Missed", "Manqué"), ("Spinning up…", "Mise en rotation…"),
    ("Spinning, the sample sorts itself into layers. Brake so that the marked tube comes to rest under the window: the rotor coasts a while before it stops.",
     "En tournant, l'échantillon se range en couches. Freine pour que le tube marqué s'immobilise sous la fenêtre : le rotor file encore un moment avant de s'arrêter."),
]

NEW_TEXTS += [
    # plaster cast (paw print)
    ("Pour (hold)", "Couler (maintenu)"), ("Scrape a spill", "Gratter un débordement"), ("Scrape", "Gratter"),
    ("The mud remembers a passing paw. Pour the plaster into each hollow, right to the brim: it keeps falling after you stop.",
     "La boue se souvient d'une patte passée par là. Coule le plâtre dans chaque creux, à ras bord : il tombe encore quand tu t'arrêtes."),
    # photo
    ("A local Pokémon walks past the trace. Follow it with the viewfinder and click when it is sharp (green viewfinder). Three photos before dark.",
     "Un Pokémon du coin passe près de la trace. Suis-le avec le viseur et clique quand il est net (viseur vert). Trois photos avant la nuit."),
    # bite cast
    ("It fits!", "Emboîté !"), ("It jams", "Ça bute"), ("Slide", "Glisser"), ("Press", "Presser"),
    ("Every bite leaves its signature. Slide the cast beneath the jaw until each fang finds its hollow, then press it home.",
     "Chaque morsure signe son auteur. Glisse le moulage sous la mâchoire jusqu'à ce que chaque croc trouve son creux, puis presse."),
    # tree climb
    ("Perfect!", "Parfait !"), ("Good", "Bien"), ("Slipped!", "Glissade !"), ("Grab", "Attraper"), ("Vine: hold", "Liane : maintenir"),
    ("The canopy keeps its secrets high. Catch each hold as it reaches your hands, let dead wood go by, hang on to the vines, and climb to the trace.",
     "La canopée garde ses secrets en hauteur. Attrape chaque prise quand elle arrive à tes mains, laisse passer le bois mort, tiens bon sur les lianes, et grimpe jusqu'à la trace."),
    # sorting
    ("Shells: ", "Coquilles : "),
    ("Objects push each other: clear the way before bringing a shell over.", "Les objets se poussent : dégage le passage avant d'amener une coquille."),
    ("Drag the shells into the box on the right. Debris falling into it, or a shell pushed into the bin on the left, counts as a mistake.",
     "Fais glisser les coquilles dans la boîte de droite. Un débris qui y tombe, ou une coquille poussée au rebut à gauche, compte comme une erreur."),
]
NEW_TEXTS += [
    ("Throw the shells: they keep their momentum and knock into whatever is in the way.",
     "Lance les coquilles : elles gardent leur élan, et cognent ce qu'elles croisent."),
    ("Grab a shell and throw it into the box on the right. Time is short, but debris knocked into the box or a shell in the bin is a mistake.",
     "Attrape une coquille et lance-la dans la boîte de droite. Le temps presse, mais un débris cogné dans la boîte ou une coquille au rebut est une erreur."),
]
NEW_TEXTS += [
    ("Feathers", "Plumes"), ("Scales", "Écailles"), ("Wings", "Ailes"), ("Fins", "Nageoires"), ("Claws", "Griffes"), ("Fangs", "Crocs"),
    ("The sample has:", "L'échantillon a :"),
    ("Go down the tree: at each fork, click the branch that carries one of the sample's traits (its card is on the right).",
     "Descends l'arbre : à chaque embranchement, clique la branche qui porte un caractère de l'échantillon (sa fiche est à droite)."),
]
NEW_TEXTS += [
    # kit logbook and archives
    ("Case", "Enquête"), ("Logbook", "Carnet"), ("Archives", "Archives"), ("top rank", "rang maximal"),
    ("Cases: ", "Enquêtes : "), ("Five stars: ", "Cinq étoiles : "), ("Species: ", "Espèces : "),
    ("KIT UPGRADES", "AMÉLIORATIONS DU KIT"),
    ("Sharper radar", "Radar affiné"), ("Spare vial", "Fiole de secours"), ("Quick scan", "Scan rapide"), ("Cool head", "Sang-froid"),
    ("Keen ear", "Oreille fine"), ("Informant network", "Réseau d'informateurs"),
    ("detects from farther away", "détecte de plus loin"), ("§aUnlocked at rank: ", "§aDébloqué au rang : "), ("§7Unlocks at rank: ", "§7Se débloque au rang : "), ("first sample needs no vial", "1er prélèvement sans fiole"),
    ("target identified in 2 s", "cible identifiée en 2 s"), ("one more failure forgiven", "un raté de plus pardonné"),
    ("more precise cry bearings", "relevés de cri plus précis"), ("wait between cases ÷ 2", "attente entre enquêtes ÷ 2"),
    ("CLOSED CASES", "DOSSIERS CLASSÉS"),
    ("No closed case yet. Every solved investigation will be filed here.", "Aucun dossier classé pour l'instant. Chaque enquête résolue sera consignée ici."),
    ("caught", "capturé"), ("defeated", "vaincu"), ("KINDS  ", "TYPES  "),
    # first time a mini-game is played
    ("FIRST TIME · ", "PREMIÈRE FOIS · "), ("Start", "Commencer"),
    ("A failure costs no star: the bearing is only less precise.", "Un raté ne coûte pas d'étoile : le relevé est seulement moins précis."),
    ("A failure damages the sample: a single dose, and half a star lost.", "Un raté abîme le prélèvement : une seule dose, et une demi-étoile en moins."),
    ("A failure loses one dose of the sample, and half a star.", "Un raté fait perdre une dose de l'échantillon, et une demi-étoile."),
]
NEW_TEXTS += [
    # team
    ("Team", "Équipe"), ("TEAM", "ÉQUIPE"), ("INVITE", "INVITER"), ("Join", "Rejoindre"), ("Decline", "Refuser"), ("Leave the team", "Quitter l'équipe"),
    ("Refresh", "Actualiser"),
    (" invites you to join their investigation.", " t'invite à rejoindre son enquête."),
    ("You will work on their case, and get your own loot and experience.", "Tu travailleras sur son dossier, et tu recevras ton butin et ton expérience."),
    ("Open a case to invite teammates, or wait for a lead investigator to invite you.", "Ouvre une enquête pour inviter des coéquipiers, ou attends qu'un chef d'enquête t'invite."),
    ("Only the lead investigator (★) can invite.", "Seul le chef d'enquête (★) peut inviter."),
    ("Everyone samples and analyses for the same case. Stars are shared; loot and experience go to each player.",
     "Chacun prélève et analyse pour le même dossier. Les étoiles sont communes ; le butin et l'expérience vont à chacun."),
    ("The team is full.", "L'équipe est au complet."),
    ("Search for a player…", "Chercher un joueur…"),
    ("No player without a case within 64 blocks.", "Aucun joueur sans enquête à moins de 64 blocs."),
    ("No free player matches.", "Aucun joueur libre ne correspond."),
    (" invites you: see the Team tab", " t'invite : voir l'onglet Équipe"),
]
NEW_TEXTS += [
    # underground investigation and its mini-game
    ("Underground", "Souterraine"),
    ("§7Cave species: traces and target are", "§7Espèces des grottes : traces et cible"),
    ("§7underground. Bring torches and a pickaxe.", "§7sous terre. Prévois torches et pioche."),
    ("Special loot · ores and stones", "Butin spécial · minerais et pierres"),
    ("In a cave: ", "En grotte : "),
    ("Stones removed: ", "Pierres retirées : "), ("Rockslides: ", "Éboulements : "),
    ("Darkened stones still carry weight. Dark notched ones take three hits.",
     "Les pierres assombries portent encore du poids. Les sombres à entailles demandent trois coups."),
    ("Free the glowing stone at the very bottom. You can only pull a stone nothing rests on: start from the top.",
     "Dégage la pierre qui luit, tout en bas. On ne peut retirer qu'une pierre sur laquelle rien ne repose : commence par le haut."),
]
_listed = set(UI)
UI += [t for t in NEW_TEXTS if t not in _listed]

# Keys that do not come from a literal of the client code: (key without the cobblemon_field_investigations. prefix,
# English, French)
KEYS = [
    ("ui.winds", "N,NNE,NE,ENE,E,ESE,SE,SSE,S,SSW,SW,WSW,W,WNW,NW,NNW", "N,NNE,NE,ENE,E,ESE,SE,SSE,S,SSO,SO,OSO,O,ONO,NO,NNO"),
    ("none", "none", "aucun"),
    ("marker.type1", "Element α", "Élément α"), ("marker.type2", "Element β", "Élément β"), ("marker.size", "Growth", "Croissance"),
    ("marker.egg", "Lineage", "Lignée"), ("marker.stat", "Aptitude", "Aptitude"),
    ("trace.type1", "Elemental residue", "Résidu élémentaire"), ("trace.type2", "Strange deposit", "Dépôt étrange"), ("trace.size", "Footprint", "Empreinte"),
    ("trace.egg", "Abandoned nest", "Nid abandonné"), ("trace.stat", "Signs of a struggle", "Traces de lutte"),
    ("buried.size", "Buried bone", "Ossement enfoui"), ("buried.egg", "Buried eggshells", "Coquilles enfouies"), ("buried.stat", "Broken fang", "Croc brisé"),
    ("buried.size_1", "Buried tooth", "Dent enfouie"), ("buried.egg_1", "Buried feather", "Plume enfouie"), ("buried.egg_2", "Buried moult", "Mue enfouie"),
    ("buried.stat_1", "Broken claw", "Griffe brisée"), ("buried.stat_2", "Torn-off scale", "Écaille arrachée"),
    ("game.rockfall", "Rockfall", "Éboulis"),
    ("game.sieving", "Sieving", "Tamisage"), ("game.cast", "Plaster cast", "Moulage au plâtre"), ("game.tweezers", "Tweezers", "Pince à épiler"),
    ("game.photo", "Field photo", "Photo de terrain"), ("game.swab", "Swabbing", "Écouvillonnage"), ("game.dive", "Free dive", "Apnée"),
    ("game.climb", "Tree climb", "Grimpe"), ("game.abseil", "Abseil", "Rappel"), ("game.lamp", "UV lamp", "Lampe UV"),
    ("game.filter", "Cry filter", "Filtrage du cri"), ("game.microscope", "Microscope", "Microscope"),
    ("game.chromatography", "Chromatography", "Chromatographie"), ("game.family_tree", "Family tree", "Arbre de parenté"),
    ("game.titration", "Drop titration", "Dosage au goutte-à-goutte"), ("game.bite", "Bite cast", "Empreinte dentaire"), ("game.thaw", "Thawing", "Dégel"),
    ("game.sequencing", "Sequencing", "Séquençage"), ("game.pairing", "Strand pairing", "Appariement des brins"), ("game.centrifugation", "Centrifugation", "Centrifugation"),
    ("game.fragments", "Fragment assembly", "Assemblage de fragments"), ("game.spectrum", "Spectrum tuning", "Accord du spectre"),
    ("game.gel", "Electrophoresis", "Électrophorèse"), ("game.colorimetry", "Colorimetry", "Colorimétrie"), ("game.pcr", "PCR cycles", "Cycles PCR"),
    ("game.count", "Colony count", "Comptage de colonies"), ("game.crystals", "Residual crystals", "Cristaux résiduels"), ("game.pipette", "Pipetting", "Pipetage"),
    ("game.measure", "Footprint measurement", "Mesure de l'empreinte"), ("game.sorting", "Fragment sorting", "Tri des fragments"),
    ("game.trace", "Scratch tracing", "Relevé des griffures"), ("game.brush", "Brush excavation", "Dégagement au pinceau"),
    ("size.0", "tiny", "minuscule"), ("size.1", "small", "petit"), ("size.2", "medium", "moyen"), ("size.3", "large", "grand"), ("size.4", "huge", "énorme"),
    ("egg.monster", "Monster", "Monstrueux"), ("egg.water_1", "Water 1", "Aquatique 1"), ("egg.bug", "Bug", "Insectoïde"), ("egg.flying", "Flying", "Aérien"),
    ("egg.field", "Field", "Terrestre"), ("egg.fairy", "Fairy", "Féerique"), ("egg.grass", "Grass", "Végétal"), ("egg.human_like", "Human-like", "Humanoïde"),
    ("egg.water_3", "Water 3", "Aquatique 3"), ("egg.mineral", "Mineral", "Minéral"), ("egg.amorphous", "Amorphous", "Amorphe"), ("egg.water_2", "Water 2", "Aquatique 2"),
    ("egg.ditto", "Ditto", "Métamorph"), ("egg.dragon", "Dragon", "Draconique"), ("egg.undiscovered", "Unknown", "Inconnu"),
    ("sample.name", "Sample · %s", "Échantillon · %s"), ("sample.analysed_name", "Analysed sample · %s", "Échantillon analysé · %s"),
    ("sample.marker", "Marker %s: %s", "Marqueur %s : %s"), ("sample.analyse", "Analysis: %s", "Analyse : %s"),
    ("sample.help1", "Right-click with a field kit on you to analyse it", "Clic droit avec un kit de terrain sur soi pour l'analyser"),
    ("sample.doses", "Doses left: %s", "Doses restantes : %s"),
    ("command.reset", "Investigation and cooldown reset.", "Enquête et délai remis à zéro."),
    ("fix.two", "You need at least two bearings taken far apart to get a fix", "Il faut au moins deux relevés éloignés pour recouper"),
    ("fix.aligned", "Bearings too aligned: move well off to the side", "Relevés trop alignés : décale-toi nettement sur le côté"),
    ("fix.impossible", "No fix possible", "Recoupement impossible"),
    ("fix.contradict", "The bearings contradict each other: take another one further away", "Les relevés se contredisent : prends-en un autre plus loin"),
    ("fix.ok", "Fix computed", "Recoupement établi"),
    # --- announcements ---
    ("announce.kit_required.title", "Trace", "Trace"), ("announce.kit_required.text", "You need a field kit to sample it", "Il faut un kit de terrain pour la prélever"),
    ("announce.disturbed_soil.title", "Disturbed soil", "Sol remué"),
    ("announce.disturbed_soil.text", "Something is buried here: uncover it with a brush, without breaking the block", "Quelque chose est enfoui ici : dégage-le au pinceau, sans casser le bloc"),
    ("announce.sample_needs_kit.title", "Sample", "Échantillon"),
    ("announce.sample_needs_kit.text", "You need a field kit on you (pocket sequencer) to analyse it", "Il faut un kit de terrain sur soi (séquenceur de poche) pour l'analyser"),
    ("announce.too_old.title", "DNA Sequencer", "Séquenceur ADN"),
    ("announce.too_old.text", "This sample matches no investigation in progress", "Cet échantillon ne correspond à aucune enquête en cours"),
    ("announce.analysis_done.title", "Analysis complete", "Analyse réussie"),
    ("announce.analysis_done.text", "Marker %s: %s · added to the case file (%s / %s needed)", "Marqueur %s : %s · ajouté au dossier (%s / %s requis)"),
    ("announce.sample_destroyed.title", "Analysis failed", "Analyse ratée"),
    ("announce.sample_destroyed.text", "The sample is destroyed: this marker will stay unknown · quality %s", "L'échantillon est détruit : ce marqueur restera inconnu · qualité %s"),
    ("announce.dose_lost.title", "Analysis failed", "Analyse ratée"),
    ("announce.dose_lost.text", "One dose lost (%s left) · quality %s", "Une dose est perdue (reste %s) · qualité %s"),
    ("announce.dimension.title", "Investigation Board", "Tableau d'enquête"), ("announce.dimension.text", "No reports in this dimension", "Aucun signalement dans cette dimension"),
    ("announce.new_investigation.title", "New investigation", "Nouvelle enquête"),
    ("announce.new_investigation.text", "Traces have been reported: head to the zone with your kit, vials and a brush", "Des traces ont été signalées : rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.no_report.title", "Investigation Board", "Tableau d'enquête"),
    ("announce.no_report.text", "No usable report around here, try somewhere else", "Aucun signalement exploitable par ici, essaie ailleurs"),
    ("announce.other_trace.title", "Trace", "Trace"), ("announce.other_trace.text", "It does not belong to your investigation", "Elle ne correspond pas à ton enquête"),
    ("announce.old_trace.title", "Trace", "Trace"), ("announce.old_trace.text", "Too old: it crumbles under your fingers", "Trop ancienne : elle s'effrite sous tes doigts"),
    ("announce.vial_required.title", "Field Kit", "Kit de terrain"), ("announce.vial_required.text", "You need an empty vial to take a sample", "Il te faut une fiole vide pour prélever"),
    ("announce.sample_perfect.title", "Perfect sample", "Prélèvement parfait"), ("announce.sample_ok.title", "Sample taken", "Échantillon prélevé"),
    ("announce.sample_damaged.title", "Sample damaged (−½ star)", "Prélèvement abîmé (−½ étoile)"),
    ("announce.sampling.text", "%s · %s dose(s)%s", "%s · %s dose(s)%s"),
    ("announce.sampling.left", " · %s more trace(s) in the zone", " · encore %s trace(s) dans la zone"),
    ("announce.sampling.finished", " · every trace is sampled, off to the sequencer", " · toutes les traces sont prélevées, direction le séquenceur"),
    ("announce.clue_uncovered.title", "Clue uncovered", "Indice dégagé"), ("announce.clue_uncovered.text", "%s: sample it now with the kit", "%s : prélève-le maintenant avec le kit"),
    ("announce.clue_destroyed.title", "Clue destroyed (−½ star)", "Indice détruit (−½ étoile)"),
    ("announce.clue_destroyed.text", "The disturbed soil was broken: %s is lost, this marker will stay unknown", "Le sol remué a été cassé : %s est perdu, ce marqueur restera inconnu"),
    ("announce.kit_calibrated.title", "Kit calibrated", "Kit calibré"),
    ("announce.kit_calibrated.text", "In the field, right-click with the kit to pick up its cry and get a fix on its position", "Sur le terrain, clic droit avec le kit pour capter son cri et recouper sa position"),
    ("announce.closed.title", "Case closed", "Enquête classée"), ("announce.closed.text", "You give up the trail", "Tu abandonnes la piste"),
    ("announce.same_spot.title", "Same spot", "Même écoute"),
    ("announce.same_spot.text", "Move at least %s blocks before taking another bearing", "Déplace-toi d'au moins %s blocs avant un nouveau relevé"),
    ("announce.target_moved.title", "The target has moved", "La cible s'est déplacée"),
    ("announce.target_moved.text", "Your earlier bearings are no longer reliable", "Tes anciens relevés ne sont plus fiables"),
    ("announce.contact_lost.title", "Contact lost", "Contact perdu"), ("announce.contact_lost.text", "Take new bearings with the kit", "Reprends tes relevés avec le kit"),
    ("announce.target_vanished.title", "Target lost", "Cible perdue"),
    ("announce.target_vanished.text", "The target died before you could defeat or catch it: it does not count. Another one has been reported nearby, take new bearings",
     "La cible est morte sans que tu l'aies vaincue ni capturée : ça ne compte pas. Une autre est signalée dans les environs, reprends tes relevés"),
    ("announce.sensed.title", "It sensed you!", "Il t'a senti !"),
    ("announce.sensed.text", "You came within %s blocks before scanning it: the target fled (−1 star, quality %s)", "Tu es passé à moins de %s blocs avant de l'avoir scanné : la cible a filé (−1 étoile, qualité %s)"),
    ("announce.heard.title", "It heard you!", "Il t'a entendu !"),
    ("announce.heard.text", "You ran within %s blocks of it: the target fled (−1 star, quality %s)", "Tu as couru à moins de %s blocs : la cible a filé (−1 étoile, qualité %s)"),
    ("announce.very_close.title", "Target in sight", "Cible en vue"),
    ("announce.very_close.text", "Spot it and scan it from afar (hold right-click with the kit, crosshair on it). Stay more than %s blocks away until it is identified",
     "Repère-la et scanne-la de loin (clic droit maintenu avec le kit, viseur dessus). Reste à plus de %s blocs tant qu'elle n'est pas identifiée"),
    ("announce.identified.title", "Target identified %s", "Cible identifiée %s"),
    ("announce.identified.text", "It is a %s%s. %s perfect IVs if you catch it: approach without running", "C'est un %s%s. %s IV parfaits si tu le captures : approche sans courir"),
    ("announce.identified.right", ": your guess was right", " : ton pronostic était le bon"),
    ("announce.identified.wrong", ": not what you guessed (−2 stars)", " : ce n'était pas ton pronostic (−2 étoiles)"),
    ("announce.flushed.title", "Flushed out!", "Débusqué !"),
    ("announce.flushed.text", "%s is right there: battle it or catch it (%s perfect IVs)", "%s est là : combats-le ou capture-le (%s IV parfaits)"),
    ("announce.cancelled.title", "Investigation cancelled", "Enquête annulée"), ("announce.cancelled.text", "This species no longer exists", "Cette espèce n'existe plus"),
    ("announce.too_late.title", "Too late", "Trop tard"), ("announce.too_late.text", "Someone else caught your target", "Quelqu'un d'autre a capturé ta cible"),
    ("announce.solved.title", "Case solved! %s", "Enquête résolue ! %s"),
    ("announce.solved.text", "%s %s · case no. %s%s%s%s", "%s %s · dossier n° %s%s%s%s"),
    ("announce.solved.xp", " · +%s XP", " · +%s XP"),
    ("announce.solved.rank", " · +%s XP · Promoted: %s!", " · +%s XP · Promotion : %s !"),
    ("announce.invitation.title", "Team invitation", "Invitation"),
    ("announce.invitation.text", "%s invites you to join their investigation: open your kit, Team tab", "%s t'invite à rejoindre son enquête : ouvre ton kit, onglet Équipe"),
    ("announce.invitation_sent.title", "Invitation sent", "Invitation envoyée"),
    ("announce.invitation_sent.text", "%s has two minutes to answer", "%s a deux minutes pour répondre"),
    ("announce.invitation_impossible.title", "Invitation impossible", "Invitation impossible"),
    ("announce.invitation_impossible.text", "That player is offline, already on a case, or the target is already flushed out", "Ce joueur est déconnecté, déjà sur une enquête, ou la cible est déjà débusquée"),
    ("announce.team_full.title", "Team is full", "Équipe au complet"),
    ("announce.team_full.text", "No more than %s investigators on one case", "Pas plus de %s enquêteurs sur un même dossier"),
    ("announce.team_joined.title", "New teammate", "Nouveau coéquipier"),
    ("announce.team_joined.text", "%s joins the investigation", "%s rejoint l'enquête"),
    ("announce.team_left.title", "Team left", "Équipe quittée"),
    ("announce.team_left.text", "You are off the case", "Tu ne travailles plus sur ce dossier"),
    ("announce.team_departure.title", "Teammate gone", "Départ"),
    ("announce.team_departure.text", "%s left the team", "%s a quitté l'équipe"),
    ("rank.0", "Trainee", "Stagiaire"), ("rank.1", "Junior Investigator", "Enquêteur adjoint"), ("rank.2", "Investigator", "Enquêteur"),
    ("rank.3", "Inspector", "Inspecteur"), ("rank.4", "Chief Inspector", "Inspecteur principal"), ("rank.5", "Commissioner", "Commissaire"),
    ("rank.6", "Legend", "Légende"),
    ("advancement.rank_inspector.title", "Inspector", "Inspecteur"),
    ("advancement.rank_inspector.text", "Reach the rank of Inspector", "Atteins le rang d'inspecteur"),
    ("announce.solved.capture", "caught", "capturé"), ("announce.solved.defeated", "defeated", "vaincu"),
    ("announce.solved.milestone", " (milestone!)", " (palier !)"), ("announce.solved.loot", " · Loot: %s", " · Butin : %s"),
    # --- 1.1: reports, special investigations ---
    ("announce.investigation_nocturnal.title", "Nocturnal investigation", "Enquête nocturne"),
    ("announce.investigation_nocturnal.text", "Its traces only show at night, and it only cries and comes out after dark. Head to the zone with your kit, vials and a brush",
     "Ses traces ne se voient que la nuit, et elle ne crie et ne sort qu'à la nuit tombée. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_skittish.title", "Skittish target", "Cible craintive"),
    ("announce.investigation_skittish.text", "This one senses you from much farther away: you will have to scan it from a long distance. Head to the zone with your kit, vials and a brush",
     "Celle-ci te repère de bien plus loin : il faudra la scanner à grande distance. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_exceptional.title", "Exceptional specimen", "Spécimen exceptionnel"),
    ("announce.investigation_exceptional.text", "The suspects all look alike and one more analysis result is required, but the specimen has its hidden ability. Head to the zone with your kit, vials and a brush",
     "Les suspects se ressemblent tous et il faut un résultat d'analyse de plus, mais le spécimen a son talent caché. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.nocturnal_trace.title", "Wait for nightfall", "Attends la nuit"),
    ("announce.nocturnal_trace.text", "The traces of a nocturnal target can only be sampled at night", "Les traces d'une cible nocturne ne se prélèvent que de nuit"),
    ("announce.nocturnal_silence.title", "Not a sound", "Aucun cri"),
    ("announce.nocturnal_silence.text", "The target sleeps during the day: take your bearings at night", "La cible dort le jour : fais tes relevés de nuit"),
    ("announce.investigation_restless.title", "Restless target", "Cible migratrice"),
    ("announce.investigation_restless.text", "This one never stays long: it moves twice as often during the hunt, and its lair is farther away. Head to the zone with your kit, vials and a brush", "Celle-ci ne tient pas en place : elle se déplace deux fois plus souvent pendant la traque, et sa tanière est plus loin. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_aquatic.title", "Aquatic investigation", "Enquête aquatique"),
    ("announce.investigation_aquatic.text", "The traces lie on the bottom of the water and the target lives in it: bring something to dive and to cross. Head to the zone with your kit, vials and a brush", "Les traces reposent au fond de l'eau et la cible y vit : prévois de quoi plonger et traverser. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_dig_site.title", "Dig site", "Chantier de fouille"),
    ("announce.investigation_dig_site.text", "Every remain is buried here: dig carefully and brush each one out. Head to the zone with your kit, vials and a brush", "Ici, tous les restes sont enfouis : creuse avec soin et dégage-les un par un au pinceau. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_canopy.title", "Canopy investigation", "Enquête des cimes"),
    ("announce.investigation_canopy.text", "The traces are on tree trunks and up in the treetops: look up. Head to the zone with your kit, vials and a brush", "Les traces sont sur les troncs et au sommet des arbres : lève les yeux. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_highlands.title", "Highland investigation", "Enquête d'altitude"),
    ("announce.investigation_highlands.text", "The traces are on rock faces, scattered across a much wider zone. Head to the zone with your kit, vials and a brush", "Les traces sont sur les parois rocheuses, éparpillées dans une zone bien plus vaste. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_frost.title", "Frozen trail", "Piste gelée"),
    ("announce.investigation_frost.text", "The samples are frozen and fragile: each one holds one dose fewer. Head to the zone with your kit, vials and a brush", "Les échantillons sont gelés et fragiles : chacun contient une dose de moins. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_mist.title", "Misty investigation", "Enquête dans la brume"),
    ("announce.investigation_mist.text", "Cries carry poorly here: your bearings will be much less precise, take more of them. Head to the zone with your kit, vials and a brush", "Les cris portent mal ici : tes relevés seront nettement moins précis, multiplie-les. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_underground.title", "Underground investigation", "Enquête souterraine"),
    ("announce.investigation_underground.text", "A cave species: its traces and its lair are underground. The kit radar gives the depth; bring torches and a pickaxe. Head to the zone with your kit and vials",
     "Une espèce des grottes : ses traces et sa tanière sont sous terre. Le radar du kit donne la profondeur ; prévois torches et pioche. Rends-toi dans la zone avec ton kit et des fioles"),
    # --- 1.1: advancements ---
    ("advancement.root.title", "Field Investigations", "Enquêtes de terrain"),
    ("advancement.root.text", "Get a Field Kit and open your first case", "Procure-toi un kit de terrain et ouvre ta première enquête"),
    ("advancement.first_sample.title", "Evidence Bag", "Pièce à conviction"),
    ("advancement.first_sample.text", "Sample a trace with the Field Kit", "Prélève une trace avec le kit de terrain"),
    ("advancement.clue_uncovered.title", "Careful Hands", "Doigts de fée"),
    ("advancement.clue_uncovered.text", "Dig up a buried clue and brush it out without breaking it", "Déterre un indice enfoui et dégage-le au pinceau sans le casser"),
    ("advancement.first_analysis.title", "Lab Work", "Travail de labo"),
    ("advancement.first_analysis.text", "Analyse a sample in the pocket sequencer", "Analyse un échantillon dans le séquenceur de poche"),
    ("advancement.keen_nose.title", "Good Nose", "Bon flair"),
    ("advancement.keen_nose.text", "Scan a target that turns out to be the suspect you named", "Scanne une cible qui se révèle être le suspect que tu avais désigné"),
    ("advancement.first_investigation.title", "Case Closed", "Affaire classée"),
    ("advancement.first_investigation.text", "Solve an investigation", "Résous une enquête"),
    ("advancement.five_stars.title", "By the Book", "Dans les règles de l'art"),
    ("advancement.five_stars.text", "Solve an investigation with five stars", "Résous une enquête avec cinq étoiles"),
    ("advancement.special_investigation.title", "Out of the Ordinary", "Hors du commun"),
    ("advancement.special_investigation.text", "Solve a special investigation", "Résous une enquête spéciale"),
    ("advancement.ten_investigations.title", "Seasoned Investigator", "Enquêteur chevronné"),
    ("advancement.ten_investigations.text", "Solve ten investigations", "Résous dix enquêtes"),
]

# Items and blocks (full keys).
ITEMS = [
    ("item.cobblemon_field_investigations.field_kit", "Field Kit", "Kit de terrain"),
    ("item.cobblemon_field_investigations.field_kit.help1", "All-in-one: case file, sampling, pocket sequencer, tracking, scanner",
     "Tout-en-un : dossier d'enquête, prélèvement, séquenceur de poche, pistage, scanner"),
    ("item.cobblemon_field_investigations.field_kit.help2", "Right-click: case file · on a trace: sample it (empty vial needed) · buried clue: brush",
     "Clic droit : dossier · sur une trace : prélever (fiole vide requise) · indice enfoui : pinceau"),
    ("item.cobblemon_field_investigations.vial", "Empty Vial", "Fiole vide"),
    ("item.cobblemon_field_investigations.sample", "Sample", "Échantillon"),
    ("block.cobblemon_field_investigations.trace", "Trace", "Trace"),
    ("block.cobblemon_field_investigations.disturbed_soil", "Disturbed Soil", "Sol remué"),
]


def key_name(english, taken):
    words = re.findall(r"[a-z0-9]+", english.lower().replace("½", "half"))[:5] or ["text"]
    base = "_".join(words)[:40]
    key, n = base, 2
    while key in taken:
        key = base + "_" + str(n)
        n += 1
    taken.add(key)
    return key


def java_literal(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def build():
    """Returns (en_us texts, fr_fr texts, {English UI text: key name})."""
    en, fr, taken, ui_keys = {}, {}, set(), {}
    for english, french in UI:
        key = key_name(english, taken)
        ui_keys[english] = key
        en["cobblemon_field_investigations.ui." + key] = english
        fr["cobblemon_field_investigations.ui." + key] = french
    for key, english, french in KEYS:
        en["cobblemon_field_investigations." + key] = english
        fr["cobblemon_field_investigations." + key] = french
    for key, english, french in ITEMS:
        en[key] = english
        fr[key] = french
    return en, fr, ui_keys


def replace_literals(ui_keys, write=True):
    """Replaces the English UI literals of the client code by T.f("<key>"), longest first. Returns the changed files.
    Texts shaped like an identifier ("sampling", "close", "rare") are left alone: the same literal is often an id, an NBT
    key or a phase name in the code. Wrap those in T.f by hand."""
    ui_keys = {text: key for text, key in ui_keys.items() if not re.fullmatch(r"[a-z0-9_]+", text)}
    changed = []
    for file in sorted(f for f in os.listdir(CLIENT) if f.endswith(".java")):
        path = os.path.join(CLIENT, file)
        source = io.open(path, encoding="utf-8").read()
        lines = source.split("\n")
        for i, line in enumerate(lines):
            if line.strip().startswith("//"):
                continue
            for english in sorted(ui_keys, key=len, reverse=True):
                literal = java_literal(english)
                if literal in line:
                    # Never inside a T.f("...") already in place: a key can be the same word as a text ("rare").
                    line = re.sub(r'(?<!T\.f\()' + re.escape(literal), lambda m: 'T.f("' + ui_keys[english] + '")', line)
            lines[i] = line
        new = "\n".join(lines)
        if new != source:
            changed.append(file)
            if write:
                io.open(path, "w", encoding="utf-8", newline="\n").write(new)
    return changed


if __name__ == "__main__":
    en, fr, ui_keys = build()
    for lang, texts in (("en_us", en), ("fr_fr", fr)):
        with open(os.path.join(LANG_DIR, lang + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(texts, f, ensure_ascii=False, indent=2, sort_keys=True)
    print(len(en), "keys written")
    for file in replace_literals(ui_keys):
        print("updated:", file)
