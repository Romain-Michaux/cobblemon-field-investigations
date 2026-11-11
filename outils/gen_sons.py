# Bruitages et ambiance du mod, fabriqués par synthèse (numpy) et écrits en OGG Vorbis, le seul format que lit Minecraft.
# Chaque son est un fichier assets/<mod>/sounds/<nom>.ogg déclaré dans sounds.json : pour remplacer un son par un
# enregistrement ou une génération, il suffit de déposer un autre .ogg du même nom (et de retirer le son de ce script).
# Usage : python outils/gen_sons.py   (nécessite numpy et soundfile)
import json
import os

import numpy as np
import soundfile as sf

ICI = os.path.dirname(os.path.abspath(__file__))
NS = "cobblemon_field_investigations"
SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", NS, "sounds")
SR = 44100
PI2 = 2 * np.pi


def temps(duree):
    return np.arange(int(SR * duree)) / SR


def decroit(duree, vitesse, attaque=0.004):
    """Enveloppe : montée très courte, puis décroissance exponentielle (vitesse = nombre de « demi-vies » par seconde)."""
    t = temps(duree)
    return np.minimum(1, t / attaque) * np.exp(-t * vitesse)


def cloche_env(duree):
    """Enveloppe en cloche : monte puis redescend, pour les frottements et les souffles."""
    return np.sin(np.pi * np.linspace(0, 1, int(SR * duree))) ** 1.5


def bruit(duree, bas, haut, graine=1):
    """Bruit blanc limité à une bande de fréquences (filtre par transformée de Fourier, bords adoucis)."""
    n = int(SR * duree)
    x = np.random.default_rng(graine).standard_normal(n)
    spectre = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    masque = 1 / (1 + (bas / np.maximum(f, 1)) ** 6) / (1 + (f / haut) ** 6)
    y = np.fft.irfft(spectre * masque, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def sinus(freq, duree):
    return np.sin(PI2 * freq * temps(duree))


def glisse(f0, f1, duree, courbe=1.0):
    """Sinus dont la fréquence glisse de f0 à f1."""
    t = np.linspace(0, 1, int(SR * duree)) ** courbe
    freq = f0 + (f1 - f0) * t
    return np.sin(PI2 * np.cumsum(freq) / SR)


def cloche(freq, duree, vitesse=7.0):
    """Timbre de clochette : quelques partiels inharmoniques qui s'éteignent d'autant plus vite qu'ils sont aigus."""
    t = temps(duree)
    y = np.zeros_like(t)
    for rapport, poids, amorti in ((1, 1.0, 1.0), (2.76, 0.45, 1.8), (5.4, 0.22, 3.0), (8.9, 0.1, 4.5)):
        y += poids * np.sin(PI2 * freq * rapport * t) * np.exp(-t * vitesse * amorti)
    return y * np.minimum(1, t / 0.003)


def carre(freq, duree):
    """Onde carrée adoucie (harmoniques impairs), pour les jingles façon console."""
    t = temps(duree)
    return sum(np.sin(PI2 * freq * k * t) / k for k in (1, 3, 5, 7)) * 0.7


def poser(piste, son, debut):
    """Ajoute un son dans une piste à l'instant voulu (en secondes)."""
    i = int(SR * debut)
    n = min(len(son), len(piste) - i)
    if n > 0:
        piste[i:i + n] += son[:n]


def piste(duree):
    return np.zeros(int(SR * duree))


def finir(x, pic=0.8, fondu=0.008):
    """Fondus d'entrée et de sortie (pas de clic), puis mise au niveau."""
    x = np.array(x, dtype=np.float64)
    n = int(SR * fondu)
    if len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return (x / (np.max(np.abs(x)) + 1e-9) * pic).astype(np.float32)


SONS = {}


def son(nom):
    def enregistrer(fabrique):
        SONS[nom] = fabrique
        return fabrique
    return enregistrer


# ---------- Prélèvements ----------

@son("tamis")  # sable secoué dans un tamis
def _():
    d = 0.32
    return bruit(d, 2500, 9000, 3) * decroit(d, 9) * (0.6 + 0.4 * np.sin(PI2 * 28 * temps(d)) ** 2)


@son("platre")  # plâtre épais qui coule
def _():
    d = 0.4
    p = bruit(d, 150, 900, 5) * cloche_env(d) * 0.8
    for i, debut in enumerate((0.04, 0.15, 0.26)):
        poser(p, glisse(180 + 40 * i, 320 + 60 * i, 0.07) * decroit(0.07, 25), debut)
    return p


@son("debordement")  # ça déborde
def _():
    d = 0.45
    p = bruit(d, 200, 2200, 7) * decroit(d, 11)
    poser(p, glisse(500, 110, 0.18) * decroit(0.18, 14) * 1.2, 0)
    return p


@son("pince")  # la pince se referme
def _():
    d = 0.09
    return (sinus(4200, d) + 0.6 * sinus(6300, d) + 0.5 * bruit(d, 3000, 12000, 2)) * decroit(d, 70, 0.001)


@son("pluck")  # un brin, un objet qu'on saisit
def _():
    d = 0.11
    return glisse(320, 950, d, 0.6) * decroit(d, 26, 0.002)


@son("obturateur")  # déclencheur d'appareil photo
def _():
    p = piste(0.2)
    clic = (bruit(0.03, 1500, 9000, 4) + 0.5 * sinus(2400, 0.03)) * decroit(0.03, 110, 0.001)
    poser(p, clic, 0)
    poser(p, clic * 0.8, 0.075)
    poser(p, bruit(0.06, 300, 1500, 9) * decroit(0.06, 40) * 0.5, 0.01)
    return p


@son("frottement")  # écouvillon, griffure qu'on suit
def _():
    d = 0.24
    return bruit(d, 1200, 5000, 6) * cloche_env(d) * (0.55 + 0.45 * np.sin(PI2 * 45 * temps(d)))


@son("pinceau")  # pinceau sur la terre
def _():
    d = 0.3
    return bruit(d, 3000, 11000, 8) * cloche_env(d)


@son("fiole")  # la fiole se remplit
def _():
    p = piste(0.36)
    for i in range(5):
        poser(p, glisse(420 + 110 * i, 640 + 140 * i, 0.06) * decroit(0.06, 30), i * 0.062)
    return p


@son("bulle")  # une bulle sous l'eau
def _():
    d = 0.13
    return glisse(380, 1150, d, 1.6) * decroit(d, 20, 0.006)


@son("coeur")  # un battement de cœur
def _():
    p = piste(0.42)
    poser(p, glisse(75, 48, 0.14) * decroit(0.14, 20, 0.006), 0)
    poser(p, glisse(68, 44, 0.16) * decroit(0.16, 18, 0.006) * 0.8, 0.17)
    return p


@son("corde")  # la corde file
def _():
    d = 0.2
    n = int(SR * d)
    porteuse = glisse(900, 2600, d)
    return (bruit(d, 800, 6000, 11) * 0.8 + porteuse * 0.25) * cloche_env(d)[:n]


@son("mousqueton")  # un mousqueton qui se ferme
def _():
    d = 0.22
    t = temps(d)
    y = sum(p * np.sin(PI2 * f * t) * np.exp(-t * a) for f, p, a in ((1850, 1.0, 28), (2930, 0.7, 36), (4710, 0.5, 48), (7300, 0.3, 60)))
    return y + bruit(d, 2000, 10000, 12) * decroit(d, 90, 0.001) * 0.6


@son("branche")  # la main agrippe une branche
def _():
    d = 0.16
    return glisse(150, 95, d) * decroit(d, 26) + bruit(d, 300, 2500, 13) * decroit(d, 60, 0.001) * 0.6


@son("craquement")  # une branche qui casse, un fragment perdu
def _():
    p = piste(0.3)
    rng = np.random.default_rng(14)
    for debut in (0.0, 0.03, 0.07, 0.12, 0.19):
        f = rng.uniform(900, 3200)
        poser(p, (bruit(0.03, f * 0.6, f * 2.2, int(f)) + 0.3 * sinus(f, 0.03)) * decroit(0.03, 120, 0.0008) * rng.uniform(0.5, 1), debut)
    return p


@son("choc")  # deux petits objets qui se cognent
def _():
    d = 0.07
    return (sinus(1900, d) + 0.6 * sinus(3100, d) + 0.4 * bruit(d, 1500, 8000, 15)) * decroit(d, 75, 0.0008)


@son("eboulement")  # des pierres qui roulent
def _():
    p = bruit(0.9, 60, 500, 16) * decroit(0.9, 4) * 0.5
    rng = np.random.default_rng(17)
    for debut in np.sort(rng.uniform(0, 0.55, 9)):
        f = rng.uniform(90, 220)
        poser(p, (glisse(f, f * 0.6, 0.09) + bruit(0.09, 200, 1800, int(f)) * 0.7) * decroit(0.09, 35), debut)
    return p


@son("pioche")  # un coup de pioche sur la pierre
def _():
    d = 0.2
    t = temps(d)
    return (np.sin(PI2 * 1250 * t) + 0.6 * np.sin(PI2 * 2470 * t) + 0.3 * np.sin(PI2 * 3900 * t)) * np.exp(-t * 30) + bruit(d, 800, 7000, 18) * decroit(d, 80, 0.001)


@son("pierre")  # une pierre qu'on pose, qu'on fait glisser
def _():
    d = 0.2
    return glisse(130, 70, d) * decroit(d, 22) + bruit(d, 150, 1400, 19) * decroit(d, 26) * 0.9


# ---------- Laboratoire ----------

@son("goutte")  # une goutte tombe dans le liquide
def _():
    d = 0.2
    return glisse(1500, 720, d, 0.4) * decroit(d, 20, 0.002)


@son("molette")  # un cran de molette
def _():
    d = 0.035
    return (sinus(3100, d) + 0.5 * bruit(d, 2500, 9000, 20)) * decroit(d, 160, 0.0006)


@son("glace")  # la glace se fend
def _():
    d = 0.3
    t = temps(d)
    tinte = sum(p * np.sin(PI2 * f * t) * np.exp(-t * a) for f, p, a in ((3300, 1.0, 22), (5200, 0.6, 30), (7100, 0.4, 40)))
    return tinte * 0.6 + bruit(d, 2500, 13000, 21) * decroit(d, 55, 0.0008)


@son("flamme")  # la flamme du brûleur
def _():
    d = 0.45
    return bruit(d, 90, 700, 22) * cloche_env(d) * (0.75 + 0.25 * np.sin(PI2 * 13 * temps(d)))


@son("sifflement")  # ça grésille : trop chaud
def _():
    d = 0.5
    return bruit(d, 4500, 15000, 23) * decroit(d, 7, 0.01)


@son("lampe")  # bourdonnement d'une lampe UV
def _():
    d = 1.1
    t = temps(d)
    y = np.sin(PI2 * 100 * t) + 0.5 * np.sin(PI2 * 200 * t) + 0.3 * np.sin(PI2 * 300 * t) + 0.12 * np.sin(PI2 * 2400 * t) * (0.5 + 0.5 * np.sin(PI2 * 100 * t))
    return y * cloche_env(d) ** 0.4


# ---------- Interface ----------

@son("tic")  # le temps presse
def _():
    d = 0.04
    return (sinus(1500, d) + 0.4 * bruit(d, 2000, 9000, 24)) * decroit(d, 130, 0.0006)


@son("clic")  # bouton de l'interface
def _():
    d = 0.05
    return (sinus(880, d) + 0.5 * sinus(1760, d)) * decroit(d, 95, 0.001)


@son("papier")  # une fiche qu'on retourne
def _():
    d = 0.2
    return bruit(d, 1800, 9000, 25) * cloche_env(d) ** 2 * (0.6 + 0.4 * np.sin(PI2 * 22 * temps(d)))


@son("scan")  # bip du scanner et des instruments
def _():
    d = 0.09
    return (sinus(1000, d) + 0.25 * sinus(2000, d)) * np.minimum(1, temps(d) / 0.004) * np.exp(-temps(d) * 22)


@son("carillon")  # note claire : quelque chose apparaît, une étape est franchie
def _():
    return cloche(880, 0.7, 6.0)


@son("reussite")  # mini-jeu réussi
def _():
    p = piste(0.75)
    for i, f in enumerate((523.25, 659.25, 783.99, 1046.5)):
        poser(p, (carre(f, 0.3) * 0.5 + cloche(f, 0.3, 9) * 0.8) * decroit(0.3, 9), i * 0.085)
    return p


@son("echec")  # mini-jeu raté, clic dans le vide
def _():
    p = piste(0.42)
    poser(p, carre(196, 0.17) * decroit(0.17, 12), 0)
    poser(p, carre(146.83, 0.24) * decroit(0.24, 9), 0.15)
    return p


@son("identifie")  # la cible est identifiée
def _():
    p = piste(0.8)
    poser(p, cloche(880, 0.5, 7), 0)
    poser(p, cloche(1318.5, 0.65, 6), 0.14)
    return p


@son("grade")  # montée de rang
def _():
    p = piste(1.5)
    for i, f in enumerate((392, 523.25, 659.25, 783.99)):
        poser(p, carre(f, 0.22) * decroit(0.22, 8) * 0.7, i * 0.11)
    for f in (523.25, 659.25, 783.99, 1046.5):
        poser(p, (carre(f, 0.9) * 0.4 + cloche(f, 0.9, 3) * 0.5) * decroit(0.9, 3.2), 0.46)
    return p


# ---------- Ambiance du laboratoire : une nappe douce qui boucle sans couture ----------

def ambiance_labo():
    duree = 16.0
    t = temps(duree)
    y = np.zeros_like(t)
    # Accord tenu : chaque note respire à son rythme, sur un nombre entier de cycles pour que la boucle se referme.
    for freq, poids, cycles in ((110.0, 0.9, 2), (164.81, 0.6, 3), (220.0, 0.5, 4), (261.63, 0.4, 5), (329.63, 0.3, 3)):
        f = round(freq * duree) / duree  # fréquence ajustée : un nombre entier de périodes dans la boucle
        souffle = 0.55 + 0.45 * np.sin(PI2 * cycles * t / duree + freq)
        y += poids * (np.sin(PI2 * f * t) + 0.3 * np.sin(PI2 * 2 * f * t + 1)) * souffle
    y *= 0.22
    # Quelques notes de clochette, posées sur une boucle circulaire : la queue d'une note de fin retombe au début.
    for debut, freq in ((0.5, 880.0), (3.0, 659.25), (5.5, 987.77), (8.0, 783.99), (10.5, 1318.5), (13.0, 880.0), (14.5, 659.25)):
        note = cloche(freq, 3.0, 2.2) * 0.16
        i = int(SR * debut)
        for k in range(len(note)):
            y[(i + k) % len(y)] += note[k]
    return (y / np.max(np.abs(y)) * 0.5).astype(np.float32)


if __name__ == "__main__":
    os.makedirs(SORTIE, exist_ok=True)
    declarations = {}
    for nom, fabrique in SONS.items():
        sf.write(os.path.join(SORTIE, nom + ".ogg"), finir(fabrique()), SR, format="OGG", subtype="VORBIS")
        declarations[nom] = {"sounds": [NS + ":" + nom]}
    sf.write(os.path.join(SORTIE, "ambiance_labo.ogg"), ambiance_labo(), SR, format="OGG", subtype="VORBIS")
    declarations["ambiance_labo"] = {"sounds": [{"name": NS + ":ambiance_labo", "stream": True}]}
    with open(os.path.join(SORTIE, "..", "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(declarations, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    total = sum(os.path.getsize(os.path.join(SORTIE, x)) for x in os.listdir(SORTIE))
    print(len(declarations), "sons écrits,", total // 1024, "Ko")
