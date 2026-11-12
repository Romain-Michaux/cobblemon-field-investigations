# Sound effects of the mod, synthesised with numpy and written as OGG Vorbis, the only format Minecraft reads.
# Each sound is a file assets/<mod>/sounds/<name>.ogg declared in sounds.json.
# Hand-made sounds: any file tools/custom_sounds/<name>.wav (or .ogg, .mp3, .flac) replaces the synthesised sound of the
# same name. That is how sounds tuned by ear are used, for example exported from https://www.bfxr.net/.
# Usage: python tools/gen_sounds.py   (needs numpy and soundfile)
import json
import os

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "cobblemon_field_investigations"
OUT_DIR = os.path.join(HERE, "..", "src", "main", "resources", "assets", NS, "sounds")
SR = 44100
PI2 = 2 * np.pi


def times(duration):
    return np.arange(int(SR * duration)) / SR


def decay(duration, speed, attack=0.004):
    """Envelope: very short attack, then exponential decay (speed = decay rate per second)."""
    t = times(duration)
    return np.minimum(1, t / attack) * np.exp(-t * speed)


def bell_env(duration):
    """Bell-shaped envelope: rises then falls, for rubbing and breathy sounds."""
    return np.sin(np.pi * np.linspace(0, 1, int(SR * duration))) ** 1.5


def noise(duration, low, high, seed=1):
    """White noise limited to a frequency band (Fourier transform filter, soft edges)."""
    n = int(SR * duration)
    x = np.random.default_rng(seed).standard_normal(n)
    spectrum = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    mask = 1 / (1 + (low / np.maximum(f, 1)) ** 6) / (1 + (f / high) ** 6)
    y = np.fft.irfft(spectrum * mask, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def sine(freq, duration):
    return np.sin(PI2 * freq * times(duration))


def glide(f0, f1, duration, curve=1.0):
    """Sine wave whose frequency glides from f0 to f1."""
    t = np.linspace(0, 1, int(SR * duration)) ** curve
    freq = f0 + (f1 - f0) * t
    return np.sin(PI2 * np.cumsum(freq) / SR)


def bell(freq, duration, speed=7.0):
    """Small bell timbre: a few inharmonic partials, the higher ones dying out faster."""
    t = times(duration)
    y = np.zeros_like(t)
    for ratio, weight, damping in ((1, 1.0, 1.0), (2.76, 0.45, 1.8), (5.4, 0.22, 3.0), (8.9, 0.1, 4.5)):
        y += weight * np.sin(PI2 * freq * ratio * t) * np.exp(-t * speed * damping)
    return y * np.minimum(1, t / 0.003)


def square(freq, duration):
    """Softened square wave (odd harmonics), for console-style jingles."""
    t = times(duration)
    return sum(np.sin(PI2 * freq * k * t) / k for k in (1, 3, 5, 7)) * 0.7


def place(track, sound, start):
    """Adds a sound to a track at the given time (in seconds)."""
    i = int(SR * start)
    n = min(len(sound), len(track) - i)
    if n > 0:
        track[i:i + n] += sound[:n]


def track(duration):
    return np.zeros(int(SR * duration))


def finish(x, peak=0.8, fade=0.008):
    """Fade in and out (no click), then normalise."""
    x = np.array(x, dtype=np.float64)
    n = int(SR * fade)
    if len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return (x / (np.max(np.abs(x)) + 1e-9) * peak).astype(np.float32)


SOUNDS = {}


def sound(name):
    def register(maker):
        SOUNDS[name] = maker
        return maker
    return register


# ---------- Sampling ----------

@sound("sieve")  # sand shaken in a sieve
def _():
    d = 0.32
    return noise(d, 2500, 9000, 3) * decay(d, 9) * (0.6 + 0.4 * np.sin(PI2 * 28 * times(d)) ** 2)


@sound("plaster")  # thick plaster pouring
def _():
    d = 0.4
    p = noise(d, 150, 900, 5) * bell_env(d) * 0.8
    for i, start in enumerate((0.04, 0.15, 0.26)):
        place(p, glide(180 + 40 * i, 320 + 60 * i, 0.07) * decay(0.07, 25), start)
    return p


@sound("overflow")  # overflowing
def _():
    d = 0.45
    p = noise(d, 200, 2200, 7) * decay(d, 11)
    place(p, glide(500, 110, 0.18) * decay(0.18, 14) * 1.2, 0)
    return p


@sound("tweezers")  # tweezers closing
def _():
    d = 0.09
    return (sine(4200, d) + 0.6 * sine(6300, d) + 0.5 * noise(d, 3000, 12000, 2)) * decay(d, 70, 0.001)


@sound("pluck")  # a strand, an object picked up
def _():
    d = 0.11
    return glide(320, 950, d, 0.6) * decay(d, 26, 0.002)


@sound("shutter")  # camera shutter
def _():
    p = track(0.2)
    click = (noise(0.03, 1500, 9000, 4) + 0.5 * sine(2400, 0.03)) * decay(0.03, 110, 0.001)
    place(p, click, 0)
    place(p, click * 0.8, 0.075)
    place(p, noise(0.06, 300, 1500, 9) * decay(0.06, 40) * 0.5, 0.01)
    return p


@sound("rub")  # swab, scratch being traced
def _():
    d = 0.24
    return noise(d, 1200, 5000, 6) * bell_env(d) * (0.55 + 0.45 * np.sin(PI2 * 45 * times(d)))


@sound("brush")  # brush on soil
def _():
    d = 0.3
    return noise(d, 3000, 11000, 8) * bell_env(d)


@sound("vial")  # vial filling up
def _():
    p = track(0.36)
    for i in range(5):
        place(p, glide(420 + 110 * i, 640 + 140 * i, 0.06) * decay(0.06, 30), i * 0.062)
    return p


@sound("bubble")  # a bubble under water
def _():
    d = 0.13
    return glide(380, 1150, d, 1.6) * decay(d, 20, 0.006)


@sound("heartbeat")  # a heartbeat
def _():
    p = track(0.42)
    place(p, glide(75, 48, 0.14) * decay(0.14, 20, 0.006), 0)
    place(p, glide(68, 44, 0.16) * decay(0.16, 18, 0.006) * 0.8, 0.17)
    return p


@sound("rope")  # rope running
def _():
    d = 0.2
    n = int(SR * d)
    carrier = glide(900, 2600, d)
    return (noise(d, 800, 6000, 11) * 0.8 + carrier * 0.25) * bell_env(d)[:n]


@sound("carabiner")  # a carabiner snapping shut
def _():
    d = 0.22
    t = times(d)
    y = sum(p * np.sin(PI2 * f * t) * np.exp(-t * a) for f, p, a in ((1850, 1.0, 28), (2930, 0.7, 36), (4710, 0.5, 48), (7300, 0.3, 60)))
    return y + noise(d, 2000, 10000, 12) * decay(d, 90, 0.001) * 0.6


@sound("branch")  # a hand grabbing a branch
def _():
    d = 0.16
    return glide(150, 95, d) * decay(d, 26) + noise(d, 300, 2500, 13) * decay(d, 60, 0.001) * 0.6


@sound("crack")  # a branch snapping, a fragment lost
def _():
    p = track(0.3)
    rng = np.random.default_rng(14)
    for start in (0.0, 0.03, 0.07, 0.12, 0.19):
        f = rng.uniform(900, 3200)
        place(p, (noise(0.03, f * 0.6, f * 2.2, int(f)) + 0.3 * sine(f, 0.03)) * decay(0.03, 120, 0.0008) * rng.uniform(0.5, 1), start)
    return p


@sound("knock")  # two small objects knocking together
def _():
    d = 0.07
    return (sine(1900, d) + 0.6 * sine(3100, d) + 0.4 * noise(d, 1500, 8000, 15)) * decay(d, 75, 0.0008)


@sound("rockslide")  # rolling stones
def _():
    p = noise(0.9, 60, 500, 16) * decay(0.9, 4) * 0.5
    rng = np.random.default_rng(17)
    for start in np.sort(rng.uniform(0, 0.55, 9)):
        f = rng.uniform(90, 220)
        place(p, (glide(f, f * 0.6, 0.09) + noise(0.09, 200, 1800, int(f)) * 0.7) * decay(0.09, 35), start)
    return p


@sound("pickaxe")  # a pickaxe hitting stone
def _():
    d = 0.2
    t = times(d)
    return (np.sin(PI2 * 1250 * t) + 0.6 * np.sin(PI2 * 2470 * t) + 0.3 * np.sin(PI2 * 3900 * t)) * np.exp(-t * 30) + noise(d, 800, 7000, 18) * decay(d, 80, 0.001)


@sound("stone")  # a stone set down or slid
def _():
    d = 0.2
    return glide(130, 70, d) * decay(d, 22) + noise(d, 150, 1400, 19) * decay(d, 26) * 0.9


# ---------- Lab ----------

@sound("drop")  # a drop falling into liquid
def _():
    d = 0.2
    return glide(1500, 720, d, 0.4) * decay(d, 20, 0.002)


@sound("knob")  # one notch of a knob
def _():
    d = 0.035
    return (sine(3100, d) + 0.5 * noise(d, 2500, 9000, 20)) * decay(d, 160, 0.0006)


@sound("ice")  # ice cracking
def _():
    d = 0.3
    t = times(d)
    ring = sum(p * np.sin(PI2 * f * t) * np.exp(-t * a) for f, p, a in ((3300, 1.0, 22), (5200, 0.6, 30), (7100, 0.4, 40)))
    return ring * 0.6 + noise(d, 2500, 13000, 21) * decay(d, 55, 0.0008)


@sound("flame")  # burner flame
def _():
    d = 0.45
    return noise(d, 90, 700, 22) * bell_env(d) * (0.75 + 0.25 * np.sin(PI2 * 13 * times(d)))


@sound("whistle")  # sizzling: too hot
def _():
    d = 0.5
    return noise(d, 4500, 15000, 23) * decay(d, 7, 0.01)


@sound("lamp")  # hum of a UV lamp
def _():
    d = 1.1
    t = times(d)
    y = np.sin(PI2 * 100 * t) + 0.5 * np.sin(PI2 * 200 * t) + 0.3 * np.sin(PI2 * 300 * t) + 0.12 * np.sin(PI2 * 2400 * t) * (0.5 + 0.5 * np.sin(PI2 * 100 * t))
    return y * bell_env(d) ** 0.4


# ---------- Interface ----------

@sound("tick")  # time is running out
def _():
    d = 0.04
    return (sine(1500, d) + 0.4 * noise(d, 2000, 9000, 24)) * decay(d, 130, 0.0006)


@sound("click")  # UI button
def _():
    d = 0.05
    return (sine(880, d) + 0.5 * sine(1760, d)) * decay(d, 95, 0.001)


@sound("paper")  # a card being turned over
def _():
    d = 0.2
    return noise(d, 1800, 9000, 25) * bell_env(d) ** 2 * (0.6 + 0.4 * np.sin(PI2 * 22 * times(d)))


@sound("scan")  # beep of the scanner and instruments
def _():
    d = 0.09
    return (sine(1000, d) + 0.25 * sine(2000, d)) * np.minimum(1, times(d) / 0.004) * np.exp(-times(d) * 22)


@sound("chime")  # clear note: something appears, a step is done
def _():
    return bell(880, 0.7, 6.0)


@sound("success")  # mini-game passed
def _():
    p = track(0.75)
    for i, f in enumerate((523.25, 659.25, 783.99, 1046.5)):
        place(p, (square(f, 0.3) * 0.5 + bell(f, 0.3, 9) * 0.8) * decay(0.3, 9), i * 0.085)
    return p


@sound("failure")  # mini-game failed, click on nothing
def _():
    p = track(0.42)
    place(p, square(196, 0.17) * decay(0.17, 12), 0)
    place(p, square(146.83, 0.24) * decay(0.24, 9), 0.15)
    return p


@sound("identified")  # target identified
def _():
    p = track(0.8)
    place(p, bell(880, 0.5, 7), 0)
    place(p, bell(1318.5, 0.65, 6), 0.14)
    return p


@sound("rank_up")  # rank up
def _():
    p = track(1.5)
    for i, f in enumerate((392, 523.25, 659.25, 783.99)):
        place(p, square(f, 0.22) * decay(0.22, 8) * 0.7, i * 0.11)
    for f in (523.25, 659.25, 783.99, 1046.5):
        place(p, (square(f, 0.9) * 0.4 + bell(f, 0.9, 3) * 0.5) * decay(0.9, 3.2), 0.46)
    return p


CUSTOM = os.path.join(HERE, "custom_sounds")
# Maximum length of each hand-made sound, in seconds. Many are replayed several times per second (sieve, rope, scanner
# beep): too long, they would pile up into mush. Anything longer is cut with a fade-out.
MAX_DURATIONS = {"click": 0.12, "knob": 0.08, "tick": 0.1, "chime": 0.9, "carabiner": 0.6, "success": 1.6, "failure": 1.0, "identified": 1.6,
                 "rank_up": 2.1, "scan": 0.18, "paper": 0.5, "sieve": 0.35, "rub": 0.3, "brush": 0.35, "rope": 0.3, "plaster": 0.4,
                 "overflow": 0.47, "pluck": 0.25, "vial": 0.6, "bubble": 0.25, "drop": 0.24, "tweezers": 0.15, "heartbeat": 0.5, "branch": 0.35,
                 "knock": 0.2, "pickaxe": 0.45, "stone": 0.45, "crack": 0.28, "rockslide": 1.4, "ice": 0.42, "shutter": 0.22,
                 "lamp": 1.2, "flame": 0.5, "whistle": 0.6}


def trim(x, sr, name):
    """Removes the leading silence, then cuts to the sound's maximum length with a fade-out."""
    threshold = np.max(np.abs(x)) * 0.02
    start = int(np.argmax(np.abs(x) > threshold))
    x = x[max(0, start - int(sr * 0.002)):]
    n = int(sr * MAX_DURATIONS.get(name, 2.0))
    if len(x) > n:
        x = x[:n].copy()
        f = min(n, int(sr * 0.05))
        x[-f:] *= np.linspace(1, 0, f) ** 2
    return x


def custom(name):
    """The hand-made sound for this name, if any: (mono samples, sample rate), else None."""
    for ext in (".wav", ".ogg", ".flac", ".mp3"):
        path = os.path.join(CUSTOM, name + ext)
        if os.path.exists(path):
            x, sr = sf.read(path, dtype="float32", always_2d=True)
            return x.mean(axis=1), sr
    return None


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(CUSTOM, exist_ok=True)
    declarations, replaced = {}, []
    for name, maker in SOUNDS.items():
        custom_sound = custom(name)
        if custom_sound is not None:
            sf.write(os.path.join(OUT_DIR, name + ".ogg"), finish(trim(custom_sound[0], custom_sound[1], name), fade=0.003), custom_sound[1], format="OGG", subtype="VORBIS")
            replaced.append(name)
        else:
            sf.write(os.path.join(OUT_DIR, name + ".ogg"), finish(maker()), SR, format="OGG", subtype="VORBIS")
        declarations[name] = {"sounds": [NS + ":" + name]}
    print("hand-made sounds:", ", ".join(replaced) if replaced else "none")
    with open(os.path.join(OUT_DIR, "..", "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(declarations, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write("\n")
    total = sum(os.path.getsize(os.path.join(OUT_DIR, x)) for x in os.listdir(OUT_DIR))
    print(len(declarations), "sounds written,", total // 1024, "KB")
