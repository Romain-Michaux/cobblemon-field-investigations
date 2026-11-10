# Textures et modèles du monde (traces discrètes, indices enfouis, sol remué, objets) et décors des mini-jeux,
# à partir des images PixelLab de outils/pixellab. À lancer APRÈS gen_ressources.py (qui écrit les anciennes textures
# dessinées à la main) : ce script les remplace.
# Usage : python outils/gen_monde.py   (nécessite Pillow)
import json
import os
import random

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ICI, "pixellab")
RES = os.path.join(ICI, "..", "src", "main", "resources")
NS = "cobblemon_field_investigations"


def ouvrir(nom):
    return Image.open(os.path.join(SRC, nom + ".png")).convert("RGBA")


def sauver(im, dossier, nom):
    chemin = os.path.join(RES, "assets", NS, "textures", dossier)
    os.makedirs(chemin, exist_ok=True)
    im.save(os.path.join(chemin, nom + ".png"))
    print(f"{dossier}/{nom}.png {im.size}")


def ecrire_json(parts, objet):
    chemin = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8", newline="\n") as f:
        json.dump(objet, f, ensure_ascii=False, indent=2)


def voile(im, alpha):
    """Rend une trace plus discrète : même dessin, un peu transparent."""
    im = im.copy()
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, int(a * alpha))
    return im


# ---------- Traces de surface (blocs) : discrètes, sans lueur ----------
h = random.Random(11)
residu = Image.new("RGBA", (16, 16))
for _ in range(11):
    x, y = h.randint(2, 13), h.randint(2, 13)
    residu.putpixel((x, y), h.choice([(111, 175, 168, 210), (150, 196, 190, 190), (74, 120, 118, 200)]))
for x, y in [(5, 6), (10, 9), (7, 11)]:
    for dx, dy in [(0, 0), (1, 0), (0, 1)]:
        residu.putpixel((x + dx, y + dy), (132, 205, 196, 230))
    residu.putpixel((x, y), (214, 244, 238, 240))
sauver(residu, "block", "trace_0")
sauver(voile(ouvrir("monde_type2"), 0.85), "block", "trace_1")
sauver(voile(ouvrir("monde_taille"), 0.8), "block", "trace_2")
sauver(voile(ouvrir("monde_oeuf"), 0.92), "block", "trace_3")
sauver(voile(ouvrir("monde_stat"), 0.85), "block", "trace_4")
# Indices dégagés du sol : os, coquilles, croc.
# Les numéros suivent Genetique.blocEnfoui : os et dent (croissance), coquilles, plume et mue (lignée), croc, griffe et écaille (aptitude).
ENFOUIS = ((5, "enfoui_os", "taille", 0), (6, "enfoui_coquille", "oeuf", 0), (7, "enfoui_croc", "stat", 0), (8, "enfoui_dent", "taille", 1),
           (9, "enfoui_plume", "oeuf", 1), (10, "enfoui_mue", "oeuf", 2), (11, "enfoui_griffe", "stat", 1), (12, "enfoui_ecaille", "stat", 2))
for i, nom, _, _ in ENFOUIS:
    sauver(ouvrir(nom), "block", "trace_%d" % i)

# ---------- Sol remué : un voile posé sur le dessus du bloc d'origine, qui se creuse au fil du brossage ----------
for etape in range(4):
    h = random.Random(7)
    im = Image.new("RGBA", (16, 16))
    rayon = [0, 3.0, 4.6, 6.2][etape]
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 + h.uniform(-0.9, 0.9)
            if d <= rayon:
                im.putpixel((x, y), (38, 27, 19, 215) if d <= rayon - 1.3 else (58, 42, 28, 170))
            elif d <= rayon + 1.2 and h.random() < 0.45:
                im.putpixel((x, y), (120, 94, 62, 150))
    # Mottes et fissures autour : le signe que la terre a été retournée.
    for _ in range(12):
        a, r = h.uniform(0, 6.283), h.uniform(4.2, 7.2)
        x, y = int(7.5 + r * __import__("math").cos(a)), int(7.5 + r * __import__("math").sin(a))
        if 0 <= x < 16 and 0 <= y < 16 and im.getpixel((x, y))[3] == 0:
            im.putpixel((x, y), h.choice([(44, 31, 21, 150), (150, 120, 84, 130)]))
    if etape == 3:
        for x, y in [(6, 9), (7, 8), (8, 7), (9, 6), (9, 7), (6, 8)]:
            im.putpixel((x, y), (226, 214, 186, 255))
    sauver(im, "block", "sol_%d" % etape)

BASES = ["minecraft:block/dirt", "minecraft:block/sand", "minecraft:block/gravel"]
variantes = {}
for m, base in enumerate(BASES):
    for k in range(4):
        nom = "sol_remue_%d_%d" % (m, k)
        ecrire_json(("assets", NS, "models", "block", nom + ".json"), {
            "parent": "minecraft:block/block",
            "textures": {"base": base, "dessus": NS + ":block/sol_%d" % k, "particle": base},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": {d: {"texture": "#base", "cullface": d} for d in ("north", "south", "east", "west", "up", "down")}},
                {"from": [-0.02, -0.02, -0.02], "to": [16.02, 16.02, 16.02], "shade": False,
                 "faces": {d: {"texture": "#dessus"} for d in ("north", "south", "east", "west", "up", "down")}},
            ]})
        variantes["degage=%d,matiere=%d" % (k, m)] = {"model": NS + ":block/" + nom}
ecrire_json(("assets", NS, "blockstates", "sol_remue.json"), {"variants": variantes})

# ---------- Traces : treize genres (5 de surface + 8 indices dégagés) ----------
variantes = {}
for i in range(13):
    ecrire_json(("assets", NS, "models", "block", "trace_%d.json" % i), {
        "ambientocclusion": False,
        "textures": {"t": NS + ":block/trace_%d" % i, "particle": NS + ":block/trace_%d" % i},
        "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False,
                      "faces": {"up": {"texture": "#t", "uv": [0, 0, 16, 16]}, "down": {"texture": "#t", "uv": [0, 0, 16, 16]}}}]})
    ecrire_json(("assets", NS, "models", "block", "trace_%d_mur.json" % i), {
        "ambientocclusion": False,
        "textures": {"t": NS + ":block/trace_%d" % i, "particle": NS + ":block/trace_%d" % i},
        "elements": [{"from": [0, 0, 15.75], "to": [16, 16, 15.75], "shade": False,
                      "faces": {"north": {"texture": "#t", "uv": [0, 0, 16, 16]}, "south": {"texture": "#t", "uv": [0, 0, 16, 16]}}}]})
    for d in ("up", "down"):
        variantes["facing=%s,genre=%d" % (d, i)] = {"model": NS + ":block/trace_%d" % i}
    for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": NS + ":block/trace_%d_mur" % i}
        if r:
            v["y"] = r
        variantes["facing=%s,genre=%d" % (d, i)] = v
ecrire_json(("assets", NS, "blockstates", "trace.json"), {"variants": variantes})

# ---------- Objets ----------
sauver(ouvrir("item_kit2"), "item", "kit_terrain")
sauver(ouvrir("item_fiole"), "item", "fiole")
sauver(ouvrir("item_echantillon"), "item", "echantillon")

# ---------- Décors des mini-jeux (interface) ----------
for nom in ("ecorce", "terre", "petri", "grand_os", "grand_coquille", "grand_croc",
            "debris_coquille", "debris_brindille", "debris_feuille", "debris_caillou"):
    sauver(ouvrir(nom), "gui", nom)
for _, nom, genre, variante in ENFOUIS:
    sauver(ouvrir(nom), "gui", "enfoui_" + genre + ("_%d" % variante if variante else ""))
# Accessoires et décors des mini-jeux ajoutés en 1.1.
for nom in ("tamis", "pince", "ecouvillon", "burette", "glacon", "paroi", "fond_eau", "nuit", "sous_bois", "nid", "plateau"):
    sauver(ouvrir(nom), "gui", nom)
sauver(ouvrir("radar_b"), "gui", "radar")  # détecteur du HUD : sonar à gauche (centre 33,24), afficheur à droite (55..177 x 14..33)
sauver(ouvrir("cristal_a"), "gui", "cristal")  # teinté en jeu : terne, turquoise (stable) ou rouge (instable)
# Bac de gel de l'électrophorèse (240 x 104), dessiné ici : PixelLab le rend en perspective, il faut un fond bien à plat.
h = random.Random(3)
gel = Image.new("RGBA", (240, 104))
for y in range(104):
    for x in range(240):
        bord = min(x, y, 239 - x, 103 - y)
        coin = (x < 3 or x > 236) and (y < 3 or y > 100) and (min(x, 239 - x) + min(y, 103 - y) < 3)
        if coin:
            continue
        if bord == 0:
            c = (11, 17, 24)
        elif bord <= 3:
            c = (226, 232, 238) if bord < 3 else (150, 160, 176)
            if y > 99 and bord < 3:
                c = (176, 186, 200)
        elif bord == 4:
            c = (11, 17, 24)
        else:
            # gel bleu nuit, un peu plus violet et clair au centre (lampe UV), avec un grain léger
            d = abs(x - 120) / 120
            v = 1 - d * 0.45
            g = h.choice((0, 0, 0, 3, -3))
            c = (int(26 * v) + 10 + g, int(30 * v) + 14 + g, int(74 * v) + 30 + g)
        gel.putpixel((x, y), c + (255,))
sauver(gel, "gui", "gel")
sauver(ouvrir("viseur_a"), "gui", "viseur")  # viseur du scanner, teinté en jeu
empreinte = ouvrir("empreinte")
empreinte = empreinte.crop(empreinte.getbbox())
sauver(empreinte, "gui", "empreinte")
assert empreinte.size == (74, 70), "JeuxTerrain.Mesure.EL / EH sont à mettre à jour"
# Erlenmeyer de la colorimétrie : le verre garde ses couleurs ; le liquide (zone claire et chaude du bas) est séparé
# dans une seconde texture en gris clair, la seule à être teintée en jeu.
verre = ouvrir("erlenmeyer")
liquide = Image.new("RGBA", verre.size)
px = verre.load()
for y in range(37, verre.height):
    for x in range(verre.width):
        r, g, b, a = px[x, y]
        if a and r * 3 + g * 6 + b >= 450 and not b > r + 8:
            v = min(255, int(max(r, g, b) * 1.08) + 8)
            liquide.putpixel((x, y), (v, v, v, 255))
            px[x, y] = (0, 0, 0, 0)
sauver(verre, "gui", "erlenmeyer")
sauver(liquide, "gui", "erlenmeyer_liquide")

# Les fichiers de langue sont écrits par outils/gen_langues.py.
