# Recense les textes français encore écrits en dur dans les écrans du client (pour les passer dans les fichiers de langue).
# Usage : python outils/extraire_textes.py   -> liste "fichier<TAB>texte"
import io
import os
import re
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
CLIENT = os.path.join(ICI, "..", "src", "main", "java", "fr", "cobblescout", "traque", "client")
FICHIERS = ["Dessin.java", "EcranAnalyse.java", "EcranKit.java", "EcranTableau.java", "TraqueClient.java"]
FICHIERS += sorted(f for f in os.listdir(CLIENT) if f.startswith("Jeu") and f != "Jeu.java")
LITTERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
PHASES = {"PRELEVEMENTS", "TRAQUE", "APPROCHE", "DEBUSQUE", "REPERAGE"}


def humain(t):
    if t in PHASES or len(t) == 0:
        return False
    if re.fullmatch(r"[a-z0-9_:./%-]*", t):          # identifiants, clés NBT, noms de textures
        return False
    if not re.search(r"[A-Za-zÀ-ÿ]{2,}", t):           # symboles, chiffres, lettres isolées (A, T, C, G, N, 95°...)
        return False
    if t.startswith("cobblemon_field_investigations.") or t.startswith("§") and len(t) <= 3:
        return False
    return True


def textes(fichier):
    source = io.open(os.path.join(CLIENT, fichier), encoding="utf-8").read()
    trouves = []
    for ligne in source.split("\n"):
        code = ligne.split("//")[0] if '"' not in ligne.split("//")[0][-1:] else ligne
        if ligne.strip().startswith("//"):
            continue
        for m in LITTERAL.finditer(ligne):
            # un littéral placé après un // de commentaire n'est pas du code
            avant = ligne[:m.start()]
            if "//" in LITTERAL.sub('""', avant):
                continue
            if humain(m.group(1)) and "T.f(" not in avant[-5:] and "T.t(" not in avant[-5:] and "T.c(" not in avant[-5:]:
                trouves.append(m.group(1))
    return trouves


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    vus = set()
    for f in FICHIERS:
        for t in textes(f):
            if t not in vus:
                vus.add(t)
                print(f + "\t" + t)
