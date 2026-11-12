# Prépare les textures d'interface à partir des images générées avec PixelLab (outils/pixellab/*.png).
# Les fonds sont détourés puis agrandis sans les déformer : on recopie en miroir une bande neutre du cadre,
# ce qui garde les coins, les vis et les détails à leur taille d'origine.
# Usage : python outils/gen_interfaces.py   (nécessite Pillow)
import os
import sys

from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ICI, "pixellab")
DST = os.path.join(ICI, "..", "src", "main", "resources", "assets", "cobblemon_field_investigations", "textures", "gui")
os.makedirs(DST, exist_ok=True)


def ouvrir(nom):
    return Image.open(os.path.join(SRC, nom + ".png")).convert("RGBA")


def sauver(im, nom):
    im.save(os.path.join(DST, nom + ".png"))
    print(f"{nom}.png {im.size}")


def detourer(im, seuil=24):
    w, h = im.size
    for coin in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        if im.getpixel(coin)[3] != 0:
            ImageDraw.floodfill(im, coin, (0, 0, 0, 0), thresh=seuil)
    return im


def lignes(im):
    w, h = im.size
    d = im.tobytes()
    return [d[y * w * 4:(y + 1) * w * 4] for y in range(h)]


def ecart(a, b):
    return sum(abs(x - y) for x, y in zip(a, b))


def asymetrie(rows, p, k=5):
    return sum(ecart(rows[p - 1 - i], rows[p + i]) for i in range(k))


def etirer_lignes(im, n, lo, hi, copies=2):
    """Ajoute n lignes en recopiant en miroir la bande [a, a+n/copies) choisie dans [lo, hi) là où ça se voit le moins."""
    rows = lignes(im)
    longueur = n // copies
    assert longueur * copies == n and copies % 2 == 0, (n, copies)
    meilleur = None
    for a in range(lo, hi - longueur + 1):
        s = asymetrie(rows, a) + asymetrie(rows, a + longueur)
        if meilleur is None or s < meilleur[0]:
            meilleur = (s, a)
    a = meilleur[1]
    bande = rows[a:a + longueur]
    bloc = []
    for c in range(copies):
        bloc += bande[::-1] if c % 2 == 0 else bande
    out = rows[:a + longueur] + bloc + rows[a + longueur:]
    return Image.frombytes("RGBA", (im.width, len(out)), b"".join(out))


def etirer_colonnes(im, n, lo, hi, copies=2):
    return etirer_lignes(im.transpose(Image.TRANSPOSE), n, lo, hi, copies).transpose(Image.TRANSPOSE)


def interieur(im, seuil):
    """Rectangle de la zone unie au centre (l'écran, le liège)."""
    t = im.copy()
    ImageDraw.floodfill(t, (t.width // 2, t.height // 2), (255, 0, 255, 255), thresh=seuil)
    px = t.load()
    xs, ys = [], []
    for y in range(t.height):
        for x in range(t.width):
            if px[x, y] == (255, 0, 255, 255):
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def fond(nom, colonnes, rangees, seuil):
    im = detourer(ouvrir(nom))
    # de droite à gauche et de bas en haut : les coordonnées restent celles de l'image d'origine
    for n, lo, hi in sorted(colonnes, key=lambda c: -c[1]):
        im = etirer_colonnes(im, n, lo, hi)
    for n, lo, hi in sorted(rangees, key=lambda c: -c[1]):
        im = etirer_lignes(im, n, lo, hi)
    im = im.crop(im.getbbox())
    x0, y0, x1, y1 = interieur(im, seuil)
    sauver(im, "bg_" + nom)
    print(f"   interieur : x {x0} y {y0} l {x1 - x0} h {y1 - y0}  (marges d {im.width - x1} b {im.height - y1})")
    return im


# ---------- Les trois fonds ----------
fond("kit", colonnes=[(48, 82, 124), (48, 212, 254)], rangees=[(50, 56, 96)], seuil=12)
fond("board", colonnes=[(30, 62, 118), (24, 150, 200), (30, 234, 290)], rangees=[(30, 60, 180)], seuil=40)
fond("sequencer", colonnes=[(66, 106, 212)], rangees=[(58, 54, 94)], seuil=12)

# ---------- Polaroïd, cadran, rotor, fiole : tels quels ----------
polaroid = ouvrir("polaroid")
sauver(polaroid, "polaroid")
# L'épingle seule (12 x 10), redessinée par-dessus les photos voisines : sans cela, la photo de la rangée du dessus la coupe.
epingle = Image.new("RGBA", (12, 10))
for y in range(10):
    for x in range(12):
        r, g, b, a = polaroid.getpixel((18 + x, y))
        if a and (y < 4 or r > g + 50):
            epingle.putpixel((x, y), (r, g, b, a))
sauver(epingle, "pin")
cadran = ouvrir("dial")
sauver(cadran, "dial")
sauver(ouvrir("rotor"), "rotor")
fiole = ouvrir("vial")
sauver(fiole.crop(fiole.getbbox()), "vial")

# ---------- Feuille de papier : grain adouci pour que le texte reste lisible ----------
papier = ouvrir("paper2")
papier = papier.crop(papier.getbbox())
px = papier.load()
clairs = sorted(px[x, y][:3] for y in range(papier.height) for x in range(papier.width)
                if px[x, y][3] == 255 and sum(px[x, y][:3]) > 480)
base = clairs[len(clairs) // 2]
for y in range(papier.height):
    for x in range(papier.width):
        r, g, b, a = px[x, y]
        if a < 255 or r - g > 60 or not (sum(base) - 150 < r + g + b < sum(base) + 60):
            continue
        px[x, y] = tuple(int(c * 0.45 + base[i] * 0.55) for i, c in enumerate((r, g, b))) + (255,)
sauver(papier, "paper")

# ---------- Bouton : embouts d'origine, face aplanie et éclaircie pour être teintée en jeu ----------
b = ouvrir("button")
b = b.crop(b.getbbox())  # 90 x 28
EMBOUT, MILIEU = 9, 14
bouton = Image.new("RGBA", (EMBOUT * 2 + MILIEU, b.height))
bouton.paste(b.crop((0, 0, EMBOUT, b.height)), (0, 0))
bouton.paste(b.crop((b.width - EMBOUT, 0, b.width, b.height)), (EMBOUT + MILIEU, 0))
colonne = b.crop((27, 0, 28, b.height))
for x in range(EMBOUT, EMBOUT + MILIEU):
    bouton.paste(colonne, (x, 0))
px = bouton.load()
face = b.getpixel((27, 7))
for y in range(6, 19):
    for x in range(7, bouton.width - 7):
        px[x, y] = face
facteur = 238 / max(face[:3])
for y in range(bouton.height):
    for x in range(bouton.width):
        r, g, bb, a = px[x, y]
        if a:
            ombre = 0.86 if 16 <= y < 19 and (r, g, bb, a) == face else 1
            px[x, y] = tuple(min(255, int(c * facteur * ombre)) for c in (r, g, bb)) + (a,)
# Bords amincis : contour, biseau, filet en haut ; ombre et contour en bas. Le libellé a ainsi toute la face.
rows = lignes(bouton)
rows = [rows[i] for i in [0, 1, 4] + list(range(6, 19)) + [20, 23, 24, 25]]
bouton = Image.frombytes("RGBA", (bouton.width, len(rows)), b"".join(rows))
sauver(bouton, "button")

# ---------- Tuile (bases A/T/C/G) : touche de clavier dont on efface la lettre, réduite à 16 px, teintée en jeu ----------
t = ouvrir("tile")
xs = [0, 1, 2] + [18] * 10 + [28, 30, 31]
ys = [0, 1, 2] + [12] * 9 + [26, 29, 30, 31]
tuile = Image.new("RGBA", (16, 16))
for j, sy in enumerate(ys):
    for i, sx in enumerate(xs):
        tuile.putpixel((i, j), t.getpixel((sx, sy)))
facteur = 240 / max(t.getpixel((18, 12))[:3])
px = tuile.load()
for y in range(16):
    for x in range(16):
        r, g, bb, a = px[x, y]
        if a:
            px[x, y] = tuple(min(255, int(c * facteur)) for c in (r, g, bb)) + (255,)
for coin in [(0, 0), (15, 0), (0, 15), (15, 15)]:
    tuile.putpixel(coin, (0, 0, 0, 0))
sauver(tuile, "tile")

# ---------- Encart, croix, icônes des traces ----------
e = ouvrir("inset")
e = e.crop(e.getbbox())
px = e.load()
for cx, cy in [(0, 0), (e.width - 2, 0), (0, e.height - 2), (e.width - 2, e.height - 2)]:
    for dy in range(2):
        for dx in range(2):
            r, g, bb, a = px[cx + dx, cy + dy]
            if a < 255 or r * 3 + g * 6 + bb > 1200:
                px[cx + dx, cy + dy] = (0, 0, 0, 0)
sauver(e, "inset")
c = ouvrir("cross")
sauver(c.crop(c.getbbox()), "cross")
for genre in ["type1", "type2", "size", "egg", "stat"]:
    sauver(ouvrir("trace_" + genre), "trace_" + genre)


# ---------- Plaque : la face a été détourée par PixelLab, on la remet en bleu nuit ----------
plaque = ouvrir("plaque")
ImageDraw.floodfill(plaque, (plaque.width // 2, plaque.height // 2), (29, 39, 51, 255), thresh=0)
sauver(plaque, "plaque")

# ---------- Planche de contrôle ----------
if len(sys.argv) > 1:
    noms = ["bg_kit", "bg_board", "bg_sequencer", "paper", "polaroid", "button", "plaque", "dial", "rotor", "vial", "tile", "inset", "cross"]
    ims = [Image.open(os.path.join(DST, n + ".png")) for n in noms]
    for n, im in zip(noms, ims):
        k = 2 if im.width > 150 else 4 if im.width > 60 else 8
        fondc = Image.new("RGBA", (im.width * k, im.height * k), (255, 0, 255, 255))
        g = im.resize((im.width * k, im.height * k), Image.NEAREST)
        fondc.paste(g, (0, 0), g)
        fondc.save(os.path.join(sys.argv[1], "ctrl_" + n + ".png"))
