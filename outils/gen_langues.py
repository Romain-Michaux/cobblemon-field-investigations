# Fichiers de langue du mod (anglais par défaut, français fourni) et passage des textes de l'interface par ces fichiers.
#  - UI : (texte français tel qu'il était écrit dans le code, texte anglais). Chaque texte reçoit une clé
#    cobblemon_field_investigations.ui.<nom> et le littéral du code est remplacé par T.f("<nom>").
#  - CLES : le reste (annonces du serveur, noms des traces, marqueurs, mini-jeux, objets, blocs).
# Usage : python outils/gen_langues.py   (à relancer après avoir ajouté un texte ; outils/extraire_textes.py liste les oublis)
import io
import json
import os
import re

ICI = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ICI, "..", "src", "main", "resources", "assets", "cobblemon_field_investigations", "lang")
CLIENT = os.path.join(ICI, "..", "src", "main", "java", "io", "github", "romainmichaux", "fieldinvestigations", "client")
FICHIERS = ["Dessin.java", "EcranAnalyse.java", "EcranKit.java", "EcranTableau.java", "TraqueClient.java"]
FICHIERS += sorted(f for f in os.listdir(CLIENT) if f.startswith("Jeu") and f != "Jeu.java")

UI = [
    # --- commun ---
    ("Séquenceur ADN", "DNA Sequencer"),
    ("SÉQUENCEUR ADN · ", "DNA SEQUENCER · "),
    (" · doses : ", " · doses: "),
    (" · première erreur sans frais", " · first mistake is free"),
    ("PRÉLÈVEMENT PARFAIT", "PERFECT SAMPLE"),
    ("PRÉLÈVEMENT RÉUSSI", "SAMPLE TAKEN"),
    ("PRÉLÈVEMENT ABÎMÉ", "SAMPLE DAMAGED"),
    ("une dose de plus", "one extra dose"),
    ("échantillon complet", "full sample"),
    ("une seule dose · −½ étoile", "a single dose · −½ star"),
    ("ANALYSE RÉUSSIE", "ANALYSIS COMPLETE"),
    ("ANALYSE RATÉE", "ANALYSIS FAILED"),
    ("marqueur ", "marker "),
    (" ajouté au dossier", " added to the case file"),
    ("le séquenceur a sauvé la dose", "the sequencer saved the dose"),
    ("une dose perdue · −½ étoile", "one dose lost · −½ star"),
    # --- séquençage ---
    ("Lire", "Read"),
    ("bien placée", "right place"),
    ("mal placée", "wrong place"),
    ("Lectures : ", "Reads left: "),
    ("Clique les cases pour choisir les 4 bases, puis Lire. Tu as 6 lectures pour retrouver la séquence.",
     "Click the slots to pick the 4 bases, then Read. You have 6 reads to find the sequence."),
    # --- appariement ---
    ("Brin à compléter", "Strand to complete"),
    ("Erreurs : ", "Mistakes: "),
    ("Donne la base complémentaire de la grande case (A avec T, C avec G) avant la fin de la barre. Boutons ou touches A, T, C, G.",
     "Give the base that pairs with the big slot (A with T, C with G) before the bar runs out. Buttons or keys A, T, C, G."),
    # --- centrifugation ---
    ("Phases séparées : ", "Phases separated: "),
    ("Ratés : ", "Misses: "),
    ("Arrêter (Espace)", "Stop (Space)"),
    ("Clique (ou Espace) quand l'aiguille passe dans la zone verte. Trois arrêts réussis ; la zone rétrécit et le rotor accélère.",
     "Click (or Space) when the needle crosses the green zone. Three good stops; the zone shrinks and the rotor speeds up."),
    # --- fragments ---
    ("La fin d'un fragment", "The end of a fragment"),
    ("= le début du suivant", "= the start of the next"),
    ("Jonctions justes : ", "Correct joints: "),
    ("Essais : ", "Tries: "),
    ("Assembler", "Assemble"),
    ("Clique deux fragments pour les échanger. Dans le bon ordre, les 2 dernières bases de chaque fragment sont les 2 premières du suivant.",
     "Click two fragments to swap them. In the right order, the last 2 bases of each fragment are the first 2 of the next."),
    # --- spectre ---
    ("Fréquence", "Frequency"),
    ("Amplitude", "Amplitude"),
    ("Phase", "Phase"),
    ("gris : échantillon", "grey: sample"),
    ("Accord : ", "Match: "),
    ("Règle fréquence, amplitude et phase jusqu'à superposer ta courbe (bleue) à celle de l'échantillon (grise), avant la fin de la barre.",
     "Tune frequency, amplitude and phase until your curve (blue) overlaps the sample's (grey), before the bar runs out."),
    # --- kit ---
    ("Kit de terrain", "Field Kit"),
    ("en veille", "standby"),
    ("prélèvements", "sampling"),
    ("hunt", "tracking"),
    ("repérage", "spotting"),
    ("approach", "approach"),
    ("débusqué", "flushed out"),
    ("KIT DE TERRAIN", "FIELD KIT"),
    ("Aucune enquête en cours", "No investigation in progress"),
    ("ÉCHANTILLONS", "SAMPLES"),
    ("indice détruit", "clue destroyed"),
    ("analysé · au dossier", "analysed · in the file"),
    ("prélevé · à analyser", "sampled · to analyse"),
    ("dégagé · à prélever", "uncovered · to sample"),
    ("enfoui · pinceau requis", "buried · brush needed"),
    ("à prélever dans la zone", "to sample in the zone"),
    ("ZONE DES TRACES", "TRACE ZONE"),
    (" blocs · ", " blocks · "),
    ("Dossier", "Case file"),
    ("1 carreau = 50 blocs", "1 square = 50 blocks"),
    ("RELEVÉS (", "BEARINGS ("),
    ("CIBLE EN VUE", "TARGET IN SIGHT"),
    ("DÉBUSQUÉ", "FLUSHED OUT"),
    ("La cible est en vue. Reste à distance et scanne-la : clic droit maintenu avec le kit, viseur sur le Pokémon, pendant trois secondes.",
     "The target is in sight. Keep your distance and scan it: hold right-click with the kit, crosshair on the Pokémon, for three seconds."),
    ("Si tu t'approches avant de l'avoir identifiée, elle file.", "If you get close before identifying it, it runs away."),
    (" IV parfaits à la capture", " perfect IVs on capture"),
    ("Il est là : combats-le ou capture-le.", "There it is: battle it or catch it."),
    ("Avance sans courir : à quelques blocs, elle sera débusquée.", "Walk, don't run: a few blocks away, it will be flushed out."),
    ("Recoupement", "Fix"),
    (" blocs", " blocks"),
    ("Blanc : tes relevés. Trait clair : cap entendu, traits sombres : marge d'erreur. Cercle jaune : zone probable. ",
     "White: your bearings. Bright line: bearing heard, dark lines: margin of error. Yellow circle: likely area. "),
    ("Plus tu es près, plus le cap est précis.", "The closer you are, the more accurate the bearing."),
    # --- tableau ---
    ("commune", "common"),
    ("peu commune", "uncommon"),
    ("rare", "rare"),
    ("très rare", "very rare"),
    ("Tableau d'enquête", "Investigation Board"),
    ("TABLEAU D'ENQUÊTE", "CASE BOARD"),
    ("Ouvre une enquête : des traces à prélever (kit, fioles vides, pinceau), puis à toi de barrer les suspects qui ne collent pas (clic droit).",
     "Open an investigation to get traces to sample (kit, empty vials, brush), then cross out the suspects that do not fit yourself (right-click)."),
    ("Chaque étoile au-delà de la première donne un IV parfait au Pokémon capturé (4 au mieux) et un meilleur butin.",
     "Each star you keep (5 at best) past the first gives the caught Pokémon a perfect IV, and better loot."),
    ("Ouvrir une enquête", "Open an investigation"),
    ("Prochain signalement dans ", "Next report in "),
    (" min", " min"),
    ("Enquêtes résolues : ", "Cases solved: "),
    ("Espèce ", "Species: "),
    ("à analyser", "to analyse"),
    ("à déterrer", "to dig up"),
    ("à prélever", "to sample"),
    ("Zone : ", "Zone: "),
    ("> boussole · ", "> compass · "),
    ("Traque en cours", "Hunt in progress"),
    ("> carte du kit", "> kit map"),
    ("Résultats : ", "Results: "),
    ("Désigne un suspect", "Pick a suspect"),
    ("Traquer ", "Track "),
    ("Lancer la traque", "Start the hunt"),
    ("Pronostic : ", "Guess: "),
    ("Cible débusquée !", "Target flushed out!"),
    ("Sur le terrain avec le kit", "In the field with the kit"),
    ("Abandonner", "Give up"),
    ("Qualité de l'enquête : ", "Investigation quality: "),
    (" IV parfaits §7sur le Pokémon capturé", " perfect IVs §7on the caught Pokémon"),
    ("§7et un butin plus riche à chaque étoile", "§7and richer loot with each star"),
    ("§8(une étoile = aucun IV parfait, cinq = quatre)", "§8(one star = no perfect IV, five = four)"),
    ("§8Raté de mini-jeu : −½ (", "§8Mini-game failure: −½ ("),
    ("§8Fuite de la cible : −1 (", "§8Target ran away: −1 ("),
    ("§8Mauvais pronostic : −2", "§8Wrong guess: −2"),
    (" (écarté)", " (ruled out)"),
    # --- électrophorèse ---
    ("éch.", "smp."),
    ("Gel", "Gel"),
    ("Err.", "Err."),
    ("Oui !", "Yes!"),
    ("Non", "No"),
    ("Clique la piste identique à l'échantillon (éch.) : position, épaisseur et éclat de chaque bande comptent. Trois gels ; deux erreurs au plus.",
     "Click the lane identical to the sample (smp.): position, thickness and brightness of every band matter. Three gels; two mistakes at most."),
    # --- colorimétrie ---
    ("Rouge", "Red"),
    ("Jaune", "Yellow"),
    ("Bleu", "Blue"),
    ("Blanc", "White"),
    ("Mémorise !", "Memorise!"),
    ("Cible", "Target"),
    ("Ton mélange", "Your mix"),
    ("· réussi", "· passed"),
    ("· raté", "· failed"),
    ("seuil : ", "needed: "),
    (" gouttes", " drops"),
    ("Fiole ", "Flask "),
    ("Échecs : ", "Fails: "),
    ("Vider", "Empty"),
    ("Valider", "Confirm"),
    ("Mémorise la teinte, puis recrée-la de tête en mélangeant les réactifs. Il faut au moins 85 % de ressemblance. Trois fioles ; deux échecs au plus.",
     "Memorise the colour, then recreate it from memory by mixing the reagents. You need at least 85 % likeness. Three flasks; two fails at most."),
    # --- PCR ---
    ("Cycle ", "Cycle "),
    ("Observe le programme…", "Watch the program…"),
    ("À toi : reproduis-le", "Your turn: repeat it"),
    ("Le programme s'allonge d'un palier à chaque cycle.", "The program gets one step longer each cycle."),
    ("Observe la suite de paliers de température, puis reproduis-la en cliquant les boutons dans le même ordre. Trois cycles ; deux erreurs au plus.",
     "Watch the sequence of temperature steps, then repeat it by clicking the buttons in the same order. Three cycles; two mistakes at most."),
    # --- comptage ---
    ("Boîte ", "Dish "),
    (" / 3 · erreurs ", " / 3 · mistakes "),
    ("Exact : ", "Correct: "),
    (" colonies", " colonies"),
    ("Raté : il y en avait ", "Wrong: there were "),
    ("Compte les colonies…", "Count the colonies…"),
    ("Combien y en avait-il ?", "How many were there?"),
    ("La boîte n'est visible qu'un instant : compte les colonies, puis choisis le bon nombre. Trois boîtes ; deux erreurs au plus.",
     "The dish is only visible for a moment: count the colonies, then pick the right number. Three dishes; two mistakes at most."),
    # --- cristaux ---
    ("Gisement ", "Deposit "),
    ("Observe bien…", "Watch closely…"),
    ("Gisement prélevé", "Deposit sampled"),
    ("À toi : prélève", "Your turn: sample"),
    ("stable : ", "stable: "),
    ("instable : évite", "unstable: avoid"),
    ("Fautes : ", "Faults: "),
    ("Sans faute : une dose de plus.", "No fault: one extra dose."),
    ("Cristaux stables (turquoise) et instables (rouges) brillent un instant : mémorise, puis prélève les stables sans toucher aux rouges. Trois fautes au plus.",
     "Stable (teal) and unstable (red) crystals glow for a moment: memorise them, then sample the stable ones without touching the red ones. Three faults at most."),
    # --- pipetage ---
    ("Dose ", "Dose "),
    ("Dose juste !", "Right dose!"),
    ("Débordement…", "Overflow…"),
    ("Dose à côté", "Wrong dose"),
    ("Relâche dans le vert", "Release in the green"),
    ("Maintiens le clic", "Hold the click"),
    ("La zone verte rétrécit et l'aspiration accélère à chaque dose.", "The green zone shrinks and the suction speeds up with each dose."),
    ("Maintiens le clic pour aspirer, relâche quand le niveau est dans la zone verte. Trois doses justes à réussir ; trois fautes abîment l'échantillon.",
     "Hold the click to draw liquid, release when the level is in the green zone. Three right doses to get; three faults damage the sample."),
    # --- mesure ---
    ("bord gauche", "left edge"),
    ("bord droit", "right edge"),
    ("bord haut", "top edge"),
    ("bord bas", "bottom edge"),
    ("Mâchoire ", "Jaw "),
    ("Largeur : ", "Width: "),
    (" cm", " cm"),
    ("Longueur : ", "Length: "),
    ("Clique quand la règle touche le bord demandé.", "Click when the ruler touches the requested edge."),
    ("Clique quand la règle touche le bord gauche de l'empreinte, puis le droit, le haut et le bas. Deux mesures ratées faussent le relevé.",
     "Click when the ruler touches the left edge of the footprint, then the right, top and bottom. Two bad measurements spoil the reading."),
    # --- tri ---
    ("Coquilles", "Shells"),
    ("Fautes", "Faults"),
    ("Ramasse les six éclats de coquille parmi les brindilles, feuilles et cailloux avant la fin de la barre. Trois erreurs au plus.",
     "Pick up the six eggshell shards among the twigs, leaves and pebbles before the bar runs out. Three mistakes at most."),
    # --- griffures ---
    ("Griffure ", "Scratch "),
    ("Écarts : ", "Slips: "),
    ("Suis l'entaille…", "Follow the groove…"),
    ("Relâche et reprends", "Release and retry"),
    ("Pars du point", "Start from the dot"),
    ("Clic maintenu, du haut vers le bas.", "Hold the click, from top to bottom."),
    ("Clic maintenu sur le point de départ, puis suis l'entaille jusqu'en bas sans t'en écarter. Trois griffures ; trois écarts abîment le relevé.",
     "Hold the click on the starting dot, then follow the groove to the bottom without straying. Three scratches; three slips damage the reading."),
    # --- dégagement ---
    ("Dégagé : ", "Uncovered: "),
    ("Accrocs : ", "Snags: "),
    ("Trop brusque !", "Too rough!"),
    ("Brosse doucement", "Brush gently"),
    ("Clic maintenu : balaie la terre au centre.", "Hold the click: sweep the soil in the middle."),
    ("Clic maintenu : balaie la terre pour dégager l'indice à 90 % avant la fin de la barre. Un geste trop brusque l'abîme (trois accrocs au plus).",
     "Hold the click: sweep the soil to uncover 90 % of the clue before the bar runs out. A rough stroke damages it (three snags at most)."),
    # --- radar ---
    ("aucun signe", "no sign"),
    ("signes ténus", "faint signs"),
    ("signes nets", "clear signs"),
    ("signes frais", "fresh signs"),
    ("trace toute proche", "trace very close"),
    ("juste là", "right here"),
    ("Tout est prélevé", "All sampled"),
    ("enfoui · à ", "buried · "),
    ("indice enfoui", "buried clue"),
    ("zone à ", "zone at "),
    ("très lointain", "very far"),
    ("far", "far"),
    ("net", "clear"),
    ("close", "close"),
    ("Cri ", "Cry: "),
    ("Clic droit : écoute", "Right-click: listen"),
    ("Recalibrage…", "Recalibrating…"),
    ("Accroupi : carte", "Sneak: map"),
    ("Analyse… ", "Scanning… "),
    ("Scanne la cible", "Scan the target"),
    ("pas ta cible", "not your target"),
    ("trop près : recule !", "too close: back off!"),
    ("clic droit · à ", "right-click · "),
    ("Cible à ", "Target at "),
    ("NE COURS PAS !", "DON'T RUN!"),
    ("Approche en douceur", "Approach quietly"),
    ("Débusqué !", "Flushed out!"),
    ("capture-le", "catch it"),
    # --- scanner ---
    ("SCANNER", "SCANNER"),
    ("Ce n'est pas ta cible", "This is not your target"),
    ("Verrouillage…", "Locking on…"),
    ("Vise le Pokémon", "Aim at the Pokémon"),
    # --- suspects ---
    ("§8Clic gauche : désigner ce suspect", "§8Left-click: name this suspect"),
    ("§8Clic droit : écarter / rétablir", "§8Right-click: rule out / restore"),
    ("§aDéjà capturé", "§aAlready caught"),
    ("§7Pas encore capturé", "§7Not caught yet"),
    # --- petits mots ---
    ("à ", "at "),
    # --- fouille au pinceau ---
    ("Fragments : ", "Fragments: "),
    ("Ils sont enfouis n'importe où : cherche en brossant.", "They are buried anywhere: brush around to find them."),
    ("Passe lentement : une pâleur trahit ce qui est enfoui.", "Go slowly: a pale patch gives away what is buried."),
    ("Brosse lentement (clic maintenu) : la terre part peu à peu. Dégage les trois fragments avant la fin de la barre ; trois gestes brusques les gâchent.",
     "Brush the soil (hold the click) slowly: it comes off bit by bit. Uncover the three fragments before the bar runs out; three rough strokes ruin them."),
]

UI += [
    # --- 1.1 : signalements ---
    ("Consulter les signalements", "Check the reports"),
    ("Choisis ton enquête", "Pick your investigation"),
    ("Piste ", "Lead "),
    ("Nocturne", "Nocturnal"),
    ("Craintive", "Skittish"),
    ("Exceptionnelle", "Exceptional"),
    ("Ordinaire", "Standard"),
    ("Espèce", "Species"),
    ("Zone", "Zone"),
    ("Jamais capturée", "Never caught"),
    ("Déjà capturée", "Already caught"),
    ("Signalements valables encore ", "Reports valid for another "),
    ("Enquête ", "Investigation: "),
    ("§7Traces et cris seulement la nuit.", "§7Traces and cries only at night."),
    ("§7La cible ne se montre que de nuit.", "§7The target only comes out after dark."),
    ("§7Elle te repère de bien plus loin :", "§7It senses you from much farther:"),
    ("§7scanne-la à grande distance.", "§7scan it from a long distance."),
    ("§7Un résultat d'analyse de plus exigé,", "§7One more analysis result required,"),
    ("§7et des suspects très ressemblants.", "§7and suspects that all look alike."),
    ("§7Une enquête classique.", "§7A regular investigation."),
    ("Chromatique : chance ×2 · butin spécial", "Shiny odds ×2 · special loot"),
    ("Butin spécial doublé", "Double special loot"),
    ("Talent caché garanti · butin spécial", "Hidden ability · special loot"),
    ("IV et butin selon tes étoiles", "IVs and loot by your stars"),
    ("§8Clic : ouvrir cette enquête", "§8Click: open this investigation"),
    ("Aide couleurs : ", "Colour aid: "),
    ("oui", "on"),
    ("non", "off"),
    ("Attends la nuit", "Wait for nightfall"),
    ("traces invisibles de jour", "no traces by day"),
    ("la cible dort : aucun cri", "target asleep: no cry"),
    # --- 1.1 : types liés au biome ---
    ("Migratrice", "Restless"),
    ("Aquatique", "Aquatic"),
    ("Fouille", "Dig site"),
    ("Cimes", "Canopy"),
    ("Altitude", "Highlands"),
    ("Givre", "Frost"),
    ("Brume", "Mist"),
    ("§7Elle change d'endroit deux fois", "§7It moves twice as often,"),
    ("§7plus souvent, et part plus loin.", "§7and its lair is farther away."),
    ("§7Traces au fond de l'eau, cible", "§7Traces on the bottom, target in"),
    ("§7dans l'eau : prévois de quoi plonger.", "§7the water: be ready to dive."),
    ("§7Tous les restes sont enfouis :", "§7Every remain is buried:"),
    ("§7creuse et brosse avec soin.", "§7dig and brush with care."),
    ("§7Traces sur les troncs et à la", "§7Traces on trunks and up in the"),
    ("§7cime des arbres : lève les yeux.", "§7treetops: look up."),
    ("§7Traces sur les parois rocheuses,", "§7Traces on rock faces, across"),
    ("§7dans une zone bien plus vaste.", "§7a much wider zone."),
    ("§7Échantillons gelés et fragiles :", "§7Frozen, fragile samples:"),
    ("§7une dose de moins par prélèvement.", "§7one dose fewer per sample."),
    ("§7Les cris portent mal : relevés", "§7Cries carry poorly: bearings"),
    ("§7nettement moins précis.", "§7are much less precise."),
    ("Butin spécial · objets aquatiques", "Special loot · water items"),
    ("Butin spécial · fossiles", "Special loot · fossils"),
    ("Butin spécial · objets de forêt", "Special loot · forest items"),
    ("Butin spécial · pierres rares", "Special loot · rare stones"),
    ("Butin spécial · objets de glace", "Special loot · ice items"),
    ("Butin spécial · objets des marais", "Special loot · swamp items"),
]

# --- 1.1 : nouveaux mini-jeux (les textes déjà connus plus haut ne sont pas répétés) ---
NOUVEAUX = [
    ("Relevé de cri", "Cry bearing"), ("CRI ISOLÉ", "CRY ISOLATED"), ("CRI BROUILLÉ", "CRY LOST IN NOISE"),
    ("relevé précis", "precise bearing"), ("relevé moins précis", "less precise bearing"),
    # tamisage
    ("Sable : ", "Sand: "), ("Perdus : ", "Lost: "), ("Secousse", "Shaking"),
    ("Relâche avant que la jauge soit pleine.", "Let go before the gauge is full."),
    ("Maintiens le clic pour secouer le tamis, par à-coups : si la jauge de secousse se remplit, un fragment passe à travers.",
     "Hold the click to shake the sieve, in bursts: if the shaking gauge fills up, a fragment falls through."),
    # moulage
    ("Creux remplis : ", "Hollows filled: "), ("Débordés : ", "Spilled: "),
    ("Arrête-toi dans la bande verte, juste sous le trait.", "Stop in the green band, just under the line."),
    ("Place le bec sur un creux et maintiens le clic pour couler le plâtre. Remplis chaque creux jusqu'à la bande verte, sans dépasser le trait.",
     "Put the spout over a hollow and hold the click to pour plaster. Fill each hollow up to the green band without going over the line."),
    # pince
    ("Fibres : ", "Fibres: "), ("Ratés : ", "Misses: "), ("Pince fermée", "Tweezers closed"), ("Pince ouverte", "Tweezers open"),
    ("Les pointes passent au vert quand elles serrent.", "The tips turn green when they grip."),
    ("Aligne la pince sur une fibre et clique à l'instant où elle se referme. Elle bat de plus en plus vite.",
     "Line the tweezers up with a fibre and click the moment they close. They beat faster and faster."),
    # photo
    ("Photo nette", "Sharp photo"), ("Photo floue", "Blurry photo"), ("Mal cadrée", "Badly framed"), ("Photos : ", "Photos: "),
    ("Ratées : ", "Failed: "), ("Netteté", "Sharpness"), ("Lumière", "Light"),
    ("Suis la trace avec le viseur et clique quand elle est nette (viseur vert). Trois photos avant que la lumière baisse.",
     "Follow the trace with the viewfinder and click when it is sharp (green viewfinder). Three photos before the light fades."),
    # écouvillon
    ("Prélevé : ", "Swabbed: "), ("Souillé : ", "Soiled: "),
    ("Tu peux lever l'écouvillon pour reprendre ailleurs.", "You may lift the swab and start again elsewhere."),
    ("Maintiens le clic et frotte toute la tache violette. Ne repasse pas sur une zone déjà claire : l'écouvillon serait contaminé.",
     "Hold the click and rub the whole purple stain. Do not go back over a spot that is already clear: the swab would be contaminated."),
    # microscope
    ("Lame ", "Slide "), ("Erreurs : ", "Mistakes: "),
    ("Règle la mise au point avec les molettes (<< >> grossière, < > fine). Une fois l'image nette, clique la cellule à deux noyaux.",
     "Focus with the knobs (<< >> coarse, < > fine). Once the image is sharp, click the cell that has two nuclei."),
    # chromatographie
    ("Bandelettes : ", "Strips: "),
    ("Le petit repère gris donne l'écart minimal entre deux bandes.", "The small grey mark shows the minimum gap between two bands."),
    ("Clique une bandelette pour l'arrêter quand ses trois bandes sont bien séparées, mais avant que le solvant atteigne le haut.",
     "Click a strip to stop it once its three bands are well apart, but before the solvent reaches the top."),
    # parenté
    ("Lignée ", "Lineage "), ("Échantillon", "Sample"),
    ("À chaque nœud, prends la branche qui ressemble à l'échantillon.", "At each node, take the branch that matches the sample."),
    ("Pars du haut : à chaque embranchement, suis la branche dont le symbole correspond à l'échantillon, puis clique la case où tu arrives.",
     "Start at the top: at each fork, follow the branch whose symbol matches the sample, then click the box you end up on."),
    # dosage
    ("Trop lent", "Too slow"), ("Virage net", "Clean endpoint"), ("Virage dépassé", "Endpoint overshot"), ("Dosage ", "Titration "),
    ("Gouttes : ", "Drops: "), ("Goutte", "Drop"), ("Jet ×3", "Squirt ×3"),
    ("Verse jusqu'au virage au rose vif. Va vite au jet, puis goutte à goutte dès que le liquide rosit : dépasser de deux gouttes rate le dosage.",
     "Pour until the liquid turns bright pink. Squirt at first, then go drop by drop as soon as it blushes: two drops too many and it fails."),
    # morsure
    ("Morsure ", "Bite "), ("Moulage ", "Cast "), ("Glisser le moulage", "Slide the cast"), ("Emboîter", "Fit"),
    ("Un seul des trois moulages épouse la morsure. Choisis-le, fais-le glisser pour mettre chaque dent face à son creux, puis emboîte.",
     "Only one of the three casts matches the bite. Pick it, slide it until every tooth faces its hollow, then fit it."),
    # apnée
    ("Air", "Air"), ("Fiole", "Vial"),
    ("Suis la trace sans la lâcher. 40 % d'air restant : une dose de plus.", "Stay on the trace. 40% air left at the end: one extra dose."),
    ("Maintiens le clic sur la trace lumineuse pour remplir la fiole. Le courant la déplace, et ta réserve d'air baisse.",
     "Hold the click on the glowing trace to fill the vial. The current moves it, and your air is running out."),
    # dégel
    ("Dégel : ", "Thawed: "), ("Surchauffes : ", "Overheats: "),
    ("La glace ne fond que dans la plage verte. Elle se déplace : accompagne-la.", "The ice only melts in the green range. It drifts: follow it."),
    ("Maintiens le clic pour chauffer, relâche pour laisser refroidir. Garde la température dans la plage verte sans atteindre le rouge.",
     "Hold the click to heat, let go to cool down. Keep the temperature in the green range without reaching the red."),
    # filtrage
    ("Clarté", "Clarity"), ("Cri isolé", "Cry isolated"),
    ("Fie-toi au son : plus il est aigu, plus tu es près.", "Trust the sound: the higher it gets, the closer you are."),
    ("Déplace la souris de gauche à droite pour régler le filtre. Garde le cri net assez longtemps : la bonne fréquence dérive.",
     "Move the mouse left and right to tune the filter. Keep the cry clear long enough: the right frequency drifts."),
    # grimpe
    ("Prises : ", "Holds: "), ("Glissades : ", "Slips: "),
    ("Clique du côté de la prise quand elle traverse la bande claire. Le rythme s'accélère.",
     "Click on the side of the hold as it crosses the light band. The pace picks up."),
    ("Les prises défilent le long du tronc. Clique à gauche ou à droite, du côté de la prise, quand elle passe dans la bande claire.",
     "Holds scroll down the trunk. Click left or right, on the side of the hold, when it passes through the light band."),
    # rappel
    ("Traces : ", "Traces: "), ("Trop bas : ", "Too low: "), ("Vitesse", "Speed"),
    ("La corde met du temps à freiner : relâche avant la bande.", "The rope takes a while to brake: let go before the band."),
    ("Maintiens le clic pour laisser filer la corde, relâche pour freiner. Arrête-toi dans la bande jaune de chaque trace, sans la dépasser.",
     "Hold the click to let the rope run, let go to brake. Stop in the yellow band of each trace without going past it."),
    # lampe UV
    ("Taches : ", "Stains: "), ("Batterie", "Battery"),
    ("Les taches ne brillent que sous le faisceau. Un clic dans le vide use la batterie.",
     "Stains only glow under the beam. A click on nothing drains the battery."),
    ("Balaie le sol avec la lampe : les taches n'apparaissent que dans le faisceau. Clique chacune avant que la batterie soit vide.",
     "Sweep the ground with the lamp: stains only show inside the beam. Click each one before the battery runs out."),
]
NOUVEAUX += [
    # moulage (mâchoire)
    ("Alvéoles : ", "Sockets: "),
    ("Remplis jusqu'aux repères verts, puis relève le bec. Les alvéoles n'ont pas toutes la même profondeur.",
     "Fill up to the green marks, then lift the spout. The sockets are not all the same depth."),
    ("Place le bec sur une alvéole et maintiens le clic pour couler le plâtre. Remplis-les toutes à ras bord sans déborder, puis relâche.",
     "Put the spout over a socket and hold the click to pour plaster. Fill them all to the brim without overflowing, then let go."),
    # photo
    ("Un Pokémon du coin passe près de la trace. Suis-le avec le viseur et clique quand il est net (viseur vert). Trois photos avant la nuit.",
     "A local Pokémon walks past the trace. Follow it with the viewfinder and click when it is sharp (green viewfinder). Three photos before dark."),
    # empreinte dentaire
    ("Emboîté !", "It fits!"), ("Ça bute en rouge", "Red teeth are blocking"), ("Glisser la réglette", "Slide the strip"), ("Presser", "Press"),
    ("Fais glisser la réglette orange (boutons ou flèches) pour que chaque dent arrive sous son creux, puis presse-la : elle doit toucher partout à la fois.",
     "Slide the orange strip (buttons or arrow keys) until every tooth sits under its hollow, then press it: it must touch everywhere at once."),
    # grimpe
    ("Les branches grises et fendues sont mortes : laisse-les passer sans cliquer.", "Grey, split branches are dead: let them go by without clicking."),
    ("Clique à gauche ou à droite, du côté de la prise, quand elle passe dans la bande claire. Ne touche pas aux branches mortes. Ça accélère.",
     "Click left or right, on the side of the hold, when it crosses the light band. Leave the dead branches alone. It speeds up."),
    # tri
    ("Coquilles : ", "Shells: "),
    ("Les objets se poussent : dégage le passage avant d'amener une coquille.", "Objects push each other: clear the way before bringing a shell over."),
    ("Fais glisser les coquilles dans la boîte de droite. Un débris qui y tombe, ou une coquille poussée au rebut à gauche, compte comme une erreur.",
     "Drag the shells into the box on the right. Debris falling into it, or a shell pushed into the bin on the left, counts as a mistake."),
]
NOUVEAUX += [
    ("Lance les coquilles : elles gardent leur élan, et cognent ce qu'elles croisent.",
     "Throw the shells: they keep their momentum and knock into whatever is in the way."),
    ("Attrape une coquille et lance-la dans la boîte de droite. Le temps presse, mais un débris cogné dans la boîte ou une coquille au rebut est une erreur.",
     "Grab a shell and throw it into the box on the right. Time is short, but debris knocked into the box or a shell in the bin is a mistake."),
]
NOUVEAUX += [
    ("Plumes", "Feathers"), ("Écailles", "Scales"), ("Ailes", "Wings"), ("Nageoires", "Fins"), ("Griffes", "Claws"), ("Crocs", "Fangs"),
    ("L'échantillon a :", "The sample has:"),
    ("Descends l'arbre : à chaque embranchement, clique la branche qui porte un caractère de l'échantillon (sa fiche est à droite).",
     "Go down the tree: at each fork, click the branch that carries one of the sample's traits (its card is on the right)."),
]
NOUVEAUX += [
    # carnet et archives du kit
    ("Enquête", "Case"), ("Carnet", "Logbook"), ("Archives", "Archives"), ("rang maximal", "top rank"),
    ("Enquêtes : ", "Cases: "), ("Cinq étoiles : ", "Five stars: "), ("Espèces : ", "Species: "),
    ("AMÉLIORATIONS DU KIT", "KIT UPGRADES"),
    ("Radar affiné", "Sharper radar"), ("Fiole de secours", "Spare vial"), ("Scan rapide", "Quick scan"), ("Sang-froid", "Cool head"),
    ("Oreille fine", "Keen ear"), ("Réseau d'informateurs", "Informant network"),
    ("détecte de plus loin", "detects from farther away"), ("§aDébloqué au rang : ", "§aUnlocked at rank: "), ("§7Se débloque au rang : ", "§7Unlocks at rank: "), ("1er prélèvement sans fiole", "first sample needs no vial"),
    ("cible identifiée en 2 s", "target identified in 2 s"), ("un raté de plus pardonné", "one more failure forgiven"),
    ("relevés de cri plus précis", "more precise cry bearings"), ("attente entre enquêtes ÷ 2", "wait between cases ÷ 2"),
    ("DOSSIERS CLASSÉS", "CLOSED CASES"),
    ("Aucun dossier classé pour l'instant. Chaque enquête résolue sera consignée ici.", "No closed case yet. Every solved investigation will be filed here."),
    ("capturé", "caught"), ("vaincu", "defeated"), ("TYPES  ", "KINDS  "),
    # première partie d'un mini-jeu
    ("PREMIÈRE FOIS · ", "FIRST TIME · "), ("Commencer", "Start"),
    ("Un raté ne coûte pas d'étoile : le relevé est seulement moins précis.", "A failure costs no star: the bearing is only less precise."),
    ("Un raté abîme le prélèvement : une seule dose, et une demi-étoile en moins.", "A failure damages the sample: a single dose, and half a star lost."),
    ("Un raté fait perdre une dose de l'échantillon, et une demi-étoile.", "A failure loses one dose of the sample, and half a star."),
]
NOUVEAUX += [
    # équipe
    ("Équipe", "Team"), ("ÉQUIPE", "TEAM"), ("INVITER", "INVITE"), ("Rejoindre", "Join"), ("Refuser", "Decline"), ("Quitter l'équipe", "Leave the team"),
    ("Actualiser", "Refresh"),
    (" t'invite à rejoindre son enquête.", " invites you to join their investigation."),
    ("Tu travailleras sur son dossier, et tu recevras ton butin et ton expérience.", "You will work on their case, and get your own loot and experience."),
    ("Ouvre une enquête pour inviter des coéquipiers, ou attends qu'un chef d'enquête t'invite.", "Open a case to invite teammates, or wait for a lead investigator to invite you."),
    ("Seul le chef d'enquête (★) peut inviter.", "Only the lead investigator (★) can invite."),
    ("Chacun prélève et analyse pour le même dossier. Les étoiles sont communes ; le butin et l'expérience vont à chacun.",
     "Everyone samples and analyses for the same case. Stars are shared; loot and experience go to each player."),
    ("L'équipe est au complet.", "The team is full."),
    ("Chercher un joueur…", "Search for a player…"),
    ("Aucun joueur sans enquête à moins de 64 blocs.", "No player without a case within 64 blocks."),
    ("Aucun joueur libre ne correspond.", "No free player matches."),
    (" t'invite : voir l'onglet Équipe", " invites you: see the Team tab"),
]
NOUVEAUX += [
    # enquête souterraine et son mini-jeu
    ("Souterraine", "Underground"),
    ("§7Espèces des grottes : traces et cible", "§7Cave species: traces and target are"),
    ("§7sous terre. Prévois torches et pioche.", "§7underground. Bring torches and a pickaxe."),
    ("Butin spécial · minerais et pierres", "Special loot · ores and stones"),
    ("En grotte : ", "In a cave: "),
    ("Pierres retirées : ", "Stones removed: "), ("Éboulements : ", "Rockslides: "),
    ("Les pierres assombries portent encore du poids. Les sombres à entailles demandent trois coups.",
     "Darkened stones still carry weight. Dark notched ones take three hits."),
    ("Dégage la pierre qui luit, tout en bas. On ne peut retirer qu'une pierre sur laquelle rien ne repose : commence par le haut.",
     "Free the glowing stone at the very bottom. You can only pull a stone nothing rests on: start from the top."),
]
_deja = {f for f, _ in UI}
UI += [t for t in NOUVEAUX if t[0] not in _deja]

# Clés qui ne viennent pas d'un littéral du code : (clé sans le préfixe cobblemon_field_investigations., anglais, français)
CLES = [
    ("ui.vents", "N,NNE,NE,ENE,E,ESE,SE,SSE,S,SSW,SW,WSW,W,WNW,NW,NNW", "N,NNE,NE,ENE,E,ESE,SE,SSE,S,SSO,SO,OSO,O,ONO,NO,NNO"),
    ("none", "none", "aucun"),
    ("marker.type1", "Element α", "Élément α"), ("marqueur.type2", "Element β", "Élément β"), ("marqueur.taille", "Growth", "Croissance"),
    ("marker.egg", "Lineage", "Lignée"), ("marqueur.stat", "Aptitude", "Aptitude"),
    ("trace.type1", "Elemental residue", "Résidu élémentaire"), ("trace.type2", "Strange deposit", "Dépôt étrange"), ("trace.taille", "Footprint", "Empreinte"),
    ("trace.egg", "Abandoned nest", "Nid abandonné"), ("trace.stat", "Signs of a struggle", "Traces de lutte"),
    ("buried.size", "Buried bone", "Ossement enfoui"), ("enfoui.oeuf", "Buried eggshells", "Coquilles enfouies"), ("enfoui.stat", "Broken fang", "Croc brisé"),
    ("buried.size_1", "Buried tooth", "Dent enfouie"), ("enfoui.oeuf_1", "Buried feather", "Plume enfouie"), ("enfoui.oeuf_2", "Buried moult", "Mue enfouie"),
    ("buried.stat_1", "Broken claw", "Griffe brisée"), ("enfoui.stat_2", "Torn-off scale", "Écaille arrachée"),
    ("game.rockfall", "Rockfall", "Éboulis"),
    ("game.sieving", "Sieving", "Tamisage"), ("jeu.moulage", "Plaster cast", "Moulage au plâtre"), ("jeu.pince", "Tweezers", "Pince à épiler"),
    ("game.photo", "Field photo", "Photo de terrain"), ("jeu.ecouvillon", "Swabbing", "Écouvillonnage"), ("jeu.apnee", "Free dive", "Apnée"),
    ("game.climb", "Tree climb", "Grimpe"), ("jeu.rappel", "Abseil", "Rappel"), ("jeu.lampe", "UV lamp", "Lampe UV"),
    ("game.filter", "Cry filter", "Filtrage du cri"), ("jeu.microscope", "Microscope", "Microscope"),
    ("game.chromatography", "Chromatography", "Chromatographie"), ("jeu.parente", "Family tree", "Arbre de parenté"),
    ("game.titration", "Drop titration", "Dosage au goutte-à-goutte"), ("jeu.morsure", "Bite cast", "Empreinte dentaire"), ("jeu.degel", "Thawing", "Dégel"),
    ("game.sequencing", "Sequencing", "Séquençage"), ("jeu.appariement", "Strand pairing", "Appariement des brins"), ("jeu.centrifugation", "Centrifugation", "Centrifugation"),
    ("game.fragments", "Fragment assembly", "Assemblage de fragments"), ("jeu.spectre", "Spectrum tuning", "Accord du spectre"),
    ("game.gel", "Electrophoresis", "Électrophorèse"), ("jeu.titrage", "Colorimetry", "Colorimétrie"), ("jeu.pcr", "PCR cycles", "Cycles PCR"),
    ("game.count", "Colony count", "Comptage de colonies"), ("jeu.memoire", "Residual crystals", "Cristaux résiduels"), ("jeu.pipette", "Pipetting", "Pipetage"),
    ("game.measure", "Footprint measurement", "Mesure de l'empreinte"), ("jeu.tri", "Fragment sorting", "Tri des fragments"),
    ("game.trace", "Scratch tracing", "Relevé des griffures"), ("jeu.degagement", "Brush excavation", "Dégagement au pinceau"),
    ("size.0", "tiny", "minuscule"), ("taille.1", "small", "petit"), ("taille.2", "medium", "moyen"), ("taille.3", "large", "grand"), ("taille.4", "huge", "énorme"),
    ("egg.monster", "Monster", "Monstrueux"), ("oeuf.water_1", "Water 1", "Aquatique 1"), ("oeuf.bug", "Bug", "Insectoïde"), ("oeuf.flying", "Flying", "Aérien"),
    ("egg.field", "Field", "Terrestre"), ("oeuf.fairy", "Fairy", "Féerique"), ("oeuf.grass", "Grass", "Végétal"), ("oeuf.human_like", "Human-like", "Humanoïde"),
    ("egg.water_3", "Water 3", "Aquatique 3"), ("oeuf.mineral", "Mineral", "Minéral"), ("oeuf.amorphous", "Amorphous", "Amorphe"), ("oeuf.water_2", "Water 2", "Aquatique 2"),
    ("egg.ditto", "Ditto", "Métamorph"), ("oeuf.dragon", "Dragon", "Draconique"), ("oeuf.undiscovered", "Unknown", "Inconnu"),
    ("sample.name", "Sample · %s", "Échantillon · %s"), ("echantillon.nom_analyse", "Analysed sample · %s", "Échantillon analysé · %s"),
    ("sample.marker", "Marker %s: %s", "Marqueur %s : %s"), ("echantillon.analyse", "Analysis: %s", "Analyse : %s"),
    ("sample.help1", "Right-click with a field kit on you to analyse it", "Clic droit avec un kit de terrain sur soi pour l'analyser"),
    ("sample.doses", "Doses left: %s", "Doses restantes : %s"),
    ("command.reset", "Investigation and cooldown reset.", "Enquête et délai remis à zéro."),
    ("fix.two", "You need at least two bearings taken far apart to get a fix", "Il faut au moins deux relevés éloignés pour recouper"),
    ("fix.aligned", "Bearings too aligned: move well off to the side", "Relevés trop alignés : décale-toi nettement sur le côté"),
    ("fix.impossible", "No fix possible", "Recoupement impossible"),
    ("fix.contradict", "The bearings contradict each other: take another one further away", "Les relevés se contredisent : prends-en un autre plus loin"),
    ("fix.ok", "Fix computed", "Recoupement établi"),
    # --- annonces ---
    ("announce.kit_required.title", "Trace", "Trace"), ("annonce.kit_requis.texte", "You need a field kit to sample it", "Il faut un kit de terrain pour la prélever"),
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
    ("announce.dimension.title", "Investigation Board", "Tableau d'enquête"), ("annonce.dimension.texte", "No reports in this dimension", "Aucun signalement dans cette dimension"),
    ("announce.new_investigation.title", "New investigation", "Nouvelle enquête"),
    ("announce.new_investigation.text", "Traces have been reported: head to the zone with your kit, vials and a brush", "Des traces ont été signalées : rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.no_report.title", "Investigation Board", "Tableau d'enquête"),
    ("announce.no_report.text", "No usable report around here, try somewhere else", "Aucun signalement exploitable par ici, essaie ailleurs"),
    ("announce.other_trace.title", "Trace", "Trace"), ("annonce.trace_autre.texte", "It does not belong to your investigation", "Elle ne correspond pas à ton enquête"),
    ("announce.old_trace.title", "Trace", "Trace"), ("annonce.trace_ancienne.texte", "Too old: it crumbles under your fingers", "Trop ancienne : elle s'effrite sous tes doigts"),
    ("announce.vial_required.title", "Field Kit", "Kit de terrain"), ("annonce.fiole_requise.texte", "You need an empty vial to take a sample", "Il te faut une fiole vide pour prélever"),
    ("announce.sample_perfect.title", "Perfect sample", "Prélèvement parfait"), ("annonce.prelevement_ok.titre", "Sample taken", "Échantillon prélevé"),
    ("announce.sample_damaged.title", "Sample damaged (−½ star)", "Prélèvement abîmé (−½ étoile)"),
    ("announce.sampling.text", "%s · %s dose(s)%s", "%s · %s dose(s)%s"),
    ("announce.sampling.left", " · %s more trace(s) in the zone", " · encore %s trace(s) dans la zone"),
    ("announce.sampling.finished", " · every trace is sampled, off to the sequencer", " · toutes les traces sont prélevées, direction le séquenceur"),
    ("announce.clue_uncovered.title", "Clue uncovered", "Indice dégagé"), ("annonce.indice_degage.texte", "%s: sample it now with the kit", "%s : prélève-le maintenant avec le kit"),
    ("announce.clue_destroyed.title", "Clue destroyed (−½ star)", "Indice détruit (−½ étoile)"),
    ("announce.clue_destroyed.text", "The disturbed soil was broken: %s is lost, this marker will stay unknown", "Le sol remué a été cassé : %s est perdu, ce marqueur restera inconnu"),
    ("announce.kit_calibrated.title", "Kit calibrated", "Kit calibré"),
    ("announce.kit_calibrated.text", "In the field, right-click with the kit to pick up its cry and get a fix on its position", "Sur le terrain, clic droit avec le kit pour capter son cri et recouper sa position"),
    ("announce.closed.title", "Case closed", "Enquête classée"), ("annonce.classee.texte", "You give up the trail", "Tu abandonnes la piste"),
    ("announce.same_spot.title", "Same spot", "Même écoute"),
    ("announce.same_spot.text", "Move at least %s blocks before taking another bearing", "Déplace-toi d'au moins %s blocs avant un nouveau relevé"),
    ("announce.target_moved.title", "The target has moved", "La cible s'est déplacée"),
    ("announce.target_moved.text", "Your earlier bearings are no longer reliable", "Tes anciens relevés ne sont plus fiables"),
    ("announce.contact_lost.title", "Contact lost", "Contact perdu"), ("annonce.contact_perdu.texte", "Take new bearings with the kit", "Reprends tes relevés avec le kit"),
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
    ("announce.cancelled.title", "Investigation cancelled", "Enquête annulée"), ("annonce.annulee.texte", "This species no longer exists", "Cette espèce n'existe plus"),
    ("announce.too_late.title", "Too late", "Trop tard"), ("annonce.trop_tard.texte", "Someone else caught your target", "Quelqu'un d'autre a capturé ta cible"),
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
    ("rank.0", "Trainee", "Stagiaire"), ("rang.1", "Junior Investigator", "Enquêteur adjoint"), ("rang.2", "Investigator", "Enquêteur"),
    ("rank.3", "Inspector", "Inspecteur"), ("rang.4", "Chief Inspector", "Inspecteur principal"), ("rang.5", "Commissioner", "Commissaire"),
    ("rank.6", "Legend", "Légende"),
    ("advancement.rank_inspector.title", "Inspector", "Inspecteur"),
    ("advancement.rank_inspector.text", "Reach the rank of Inspector", "Atteins le rang d'inspecteur"),
    ("announce.solved.capture", "caught", "capturé"), ("annonce.resolue.vaincu", "defeated", "vaincu"),
    ("announce.solved.milestone", " (milestone!)", " (palier !)"), ("annonce.resolue.butin", " · Loot: %s", " · Butin : %s"),
    # --- 1.1 : signalements, enquêtes spéciales ---
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
    ("announce.investigation_altitude.title", "Highland investigation", "Enquête d'altitude"),
    ("announce.investigation_altitude.text", "The traces are on rock faces, scattered across a much wider zone. Head to the zone with your kit, vials and a brush", "Les traces sont sur les parois rocheuses, éparpillées dans une zone bien plus vaste. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_frost.title", "Frozen trail", "Piste gelée"),
    ("announce.investigation_frost.text", "The samples are frozen and fragile: each one holds one dose fewer. Head to the zone with your kit, vials and a brush", "Les échantillons sont gelés et fragiles : chacun contient une dose de moins. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_mist.title", "Misty investigation", "Enquête dans la brume"),
    ("announce.investigation_mist.text", "Cries carry poorly here: your bearings will be much less precise, take more of them. Head to the zone with your kit, vials and a brush", "Les cris portent mal ici : tes relevés seront nettement moins précis, multiplie-les. Rends-toi dans la zone avec ton kit, des fioles et un pinceau"),
    ("announce.investigation_underground.title", "Underground investigation", "Enquête souterraine"),
    ("announce.investigation_underground.text", "A cave species: its traces and its lair are underground. The kit radar gives the depth; bring torches and a pickaxe. Head to the zone with your kit and vials",
     "Une espèce des grottes : ses traces et sa tanière sont sous terre. Le radar du kit donne la profondeur ; prévois torches et pioche. Rends-toi dans la zone avec ton kit et des fioles"),
    # --- 1.1 : progrès ---
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

# Objets et blocs (clés complètes).
OBJETS = [
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


def nom(anglais, pris):
    mots = re.findall(r"[a-z0-9]+", anglais.lower().replace("½", "half"))[:5] or ["texte"]
    base = "_".join(mots)[:40]
    cle, n = base, 2
    while cle in pris:
        cle = base + "_" + str(n)
        n += 1
    pris.add(cle)
    return cle


def java(texte):
    return texte.replace("\\", "\\\\").replace('"', '\\"')


if __name__ == "__main__":
    en, fr, pris, cles = {}, {}, set(), {}
    for francais, anglais in UI:
        cle = nom(anglais, pris)
        cles[francais] = cle
        en["cobblemon_field_investigations.ui." + cle] = anglais
        fr["cobblemon_field_investigations.ui." + cle] = francais
    for cle, anglais, francais in CLES:
        en["cobblemon_field_investigations." + cle] = anglais
        fr["cobblemon_field_investigations." + cle] = francais
    for cle, anglais, francais in OBJETS:
        en[cle] = anglais
        fr[cle] = francais
    for langue, textes in (("en_us", en), ("fr_fr", fr)):
        with open(os.path.join(RES, langue + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(textes, f, ensure_ascii=False, indent=2, sort_keys=True)
    print(len(en), "clés écrites")

    # Remplacement des littéraux français du code par T.f("<clé>"), du plus long au plus court.
    for fichier in FICHIERS:
        chemin = os.path.join(CLIENT, fichier)
        source = io.open(chemin, encoding="utf-8").read()
        lignes = source.split("\n")
        for i, ligne in enumerate(lignes):
            if ligne.strip().startswith("//"):
                continue
            for francais in sorted(cles, key=len, reverse=True):
                litteral = '"' + java(francais) + '"'
                if litteral in ligne:
                    # jamais à l'intérieur d'un T.f("...") déjà posé (la clé « rare » est aussi un mot français)
                    ligne = re.sub(r'(?<!T\.f\()' + re.escape(litteral), lambda m: 'T.f("' + cles[francais] + '")', ligne)
            lignes[i] = ligne
        nouveau = "\n".join(lignes)
        if nouveau != source:
            io.open(chemin, "w", encoding="utf-8", newline="\n").write(nouveau)
            print("mis à jour :", fichier)
