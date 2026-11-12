# Builds the GUI textures from the images generated with PixelLab (tools/pixellab/*.png).
# Backgrounds are cut out, then enlarged without distortion: a neutral strip of the frame is copied mirrored, which
# keeps the corners, screws and details at their original size.
# Usage: python tools/gen_interfaces.py   (needs Pillow)
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "pixellab")
DST = os.path.join(HERE, "..", "src", "main", "resources", "assets", "cobblemon_field_investigations", "textures", "gui")
os.makedirs(DST, exist_ok=True)


def load(name):
    return Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")


def save(im, name):
    im.save(os.path.join(DST, name + ".png"))
    print(f"{name}.png {im.size}")


def cut_out(im, threshold=24):
    w, h = im.size
    for corner in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        if im.getpixel(corner)[3] != 0:
            ImageDraw.floodfill(im, corner, (0, 0, 0, 0), thresh=threshold)
    return im


def rows_of(im):
    w, h = im.size
    d = im.tobytes()
    return [d[y * w * 4:(y + 1) * w * 4] for y in range(h)]


def difference(a, b):
    return sum(abs(x - y) for x, y in zip(a, b))


def asymmetry(rows, p, k=5):
    return sum(difference(rows[p - 1 - i], rows[p + i]) for i in range(k))


def stretch_rows(im, n, lo, hi, copies=2):
    """Adds n rows by mirroring the strip [a, a+n/copies), picked in [lo, hi) where it shows the least."""
    rows = rows_of(im)
    length = n // copies
    assert length * copies == n and copies % 2 == 0, (n, copies)
    best = None
    for a in range(lo, hi - length + 1):
        s = asymmetry(rows, a) + asymmetry(rows, a + length)
        if best is None or s < best[0]:
            best = (s, a)
    a = best[1]
    strip = rows[a:a + length]
    block = []
    for c in range(copies):
        block += strip[::-1] if c % 2 == 0 else strip
    out = rows[:a + length] + block + rows[a + length:]
    return Image.frombytes("RGBA", (im.width, len(out)), b"".join(out))


def stretch_columns(im, n, lo, hi, copies=2):
    return stretch_rows(im.transpose(Image.TRANSPOSE), n, lo, hi, copies).transpose(Image.TRANSPOSE)


def inner_area(im, threshold):
    """Rectangle of the plain area in the middle (the screen, the cork)."""
    t = im.copy()
    ImageDraw.floodfill(t, (t.width // 2, t.height // 2), (255, 0, 255, 255), thresh=threshold)
    px = t.load()
    xs, ys = [], []
    for y in range(t.height):
        for x in range(t.width):
            if px[x, y] == (255, 0, 255, 255):
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def background(name, columns, rows, threshold):
    im = cut_out(load(name))
    # right to left and bottom to top, so the coordinates stay those of the original image
    for n, lo, hi in sorted(columns, key=lambda c: -c[1]):
        im = stretch_columns(im, n, lo, hi)
    for n, lo, hi in sorted(rows, key=lambda c: -c[1]):
        im = stretch_rows(im, n, lo, hi)
    im = im.crop(im.getbbox())
    x0, y0, x1, y1 = inner_area(im, threshold)
    save(im, "bg_" + name)
    print(f"   inner area: x {x0} y {y0} w {x1 - x0} h {y1 - y0}  (margins r {im.width - x1} b {im.height - y1})")
    return im


# ---------- The three backgrounds ----------
background("kit", columns=[(48, 82, 124), (48, 212, 254)], rows=[(50, 56, 96)], threshold=12)
background("board", columns=[(30, 62, 118), (24, 150, 200), (30, 234, 290)], rows=[(30, 60, 180)], threshold=40)
background("sequencer", columns=[(66, 106, 212)], rows=[(58, 54, 94)], threshold=12)

# ---------- Polaroid, dial, rotor, vial: as they are ----------
polaroid = load("polaroid")
save(polaroid, "polaroid")
# The pin alone (12 x 10), drawn again over the neighbouring photos: otherwise the photo of the row above cuts it.
pin = Image.new("RGBA", (12, 10))
for y in range(10):
    for x in range(12):
        r, g, b, a = polaroid.getpixel((18 + x, y))
        if a and (y < 4 or r > g + 50):
            pin.putpixel((x, y), (r, g, b, a))
save(pin, "pin")
dial = load("dial")
save(dial, "dial")
save(load("rotor"), "rotor")
vial = load("vial")
save(vial.crop(vial.getbbox()), "vial")

# ---------- Sheet of paper: grain softened so the text stays readable ----------
paper = load("paper2")
paper = paper.crop(paper.getbbox())
px = paper.load()
light = sorted(px[x, y][:3] for y in range(paper.height) for x in range(paper.width)
               if px[x, y][3] == 255 and sum(px[x, y][:3]) > 480)
base = light[len(light) // 2]
for y in range(paper.height):
    for x in range(paper.width):
        r, g, b, a = px[x, y]
        if a < 255 or r - g > 60 or not (sum(base) - 150 < r + g + b < sum(base) + 60):
            continue
        px[x, y] = tuple(int(c * 0.45 + base[i] * 0.55) for i, c in enumerate((r, g, b))) + (255,)
save(paper, "paper")

# ---------- Button: original end caps, face flattened and lightened so it can be tinted in game ----------
b = load("button")
b = b.crop(b.getbbox())  # 90 x 28
END_CAP, MIDDLE = 9, 14
button = Image.new("RGBA", (END_CAP * 2 + MIDDLE, b.height))
button.paste(b.crop((0, 0, END_CAP, b.height)), (0, 0))
button.paste(b.crop((b.width - END_CAP, 0, b.width, b.height)), (END_CAP + MIDDLE, 0))
column = b.crop((27, 0, 28, b.height))
for x in range(END_CAP, END_CAP + MIDDLE):
    button.paste(column, (x, 0))
px = button.load()
face = b.getpixel((27, 7))
for y in range(6, 19):
    for x in range(7, button.width - 7):
        px[x, y] = face
factor = 238 / max(face[:3])
for y in range(button.height):
    for x in range(button.width):
        r, g, bb, a = px[x, y]
        if a:
            shade = 0.86 if 16 <= y < 19 and (r, g, bb, a) == face else 1
            px[x, y] = tuple(min(255, int(c * factor * shade)) for c in (r, g, bb)) + (a,)
# Thinner edges: outline, bevel and highlight at the top; shadow and outline at the bottom. The label gets the whole face.
rows = rows_of(button)
rows = [rows[i] for i in [0, 1, 4] + list(range(6, 19)) + [20, 23, 24, 25]]
button = Image.frombytes("RGBA", (button.width, len(rows)), b"".join(rows))
save(button, "button")

# ---------- Tile (A/T/C/G bases): a keyboard key with its letter erased, shrunk to 16 px, tinted in game ----------
t = load("tile")
xs = [0, 1, 2] + [18] * 10 + [28, 30, 31]
ys = [0, 1, 2] + [12] * 9 + [26, 29, 30, 31]
tile = Image.new("RGBA", (16, 16))
for j, sy in enumerate(ys):
    for i, sx in enumerate(xs):
        tile.putpixel((i, j), t.getpixel((sx, sy)))
factor = 240 / max(t.getpixel((18, 12))[:3])
px = tile.load()
for y in range(16):
    for x in range(16):
        r, g, bb, a = px[x, y]
        if a:
            px[x, y] = tuple(min(255, int(c * factor)) for c in (r, g, bb)) + (255,)
for corner in [(0, 0), (15, 0), (0, 15), (15, 15)]:
    tile.putpixel(corner, (0, 0, 0, 0))
save(tile, "tile")

# ---------- Inset, cross, trace icons ----------
e = load("inset")
e = e.crop(e.getbbox())
px = e.load()
for cx, cy in [(0, 0), (e.width - 2, 0), (0, e.height - 2), (e.width - 2, e.height - 2)]:
    for dy in range(2):
        for dx in range(2):
            r, g, bb, a = px[cx + dx, cy + dy]
            if a < 255 or r * 3 + g * 6 + bb > 1200:
                px[cx + dx, cy + dy] = (0, 0, 0, 0)
save(e, "inset")
c = load("cross")
save(c.crop(c.getbbox()), "cross")
for kind in ["type1", "type2", "size", "egg", "stat"]:
    save(load("trace_" + kind), "trace_" + kind)


# ---------- Plaque: PixelLab cut out its face, paint it midnight blue again ----------
plate = load("plate")
ImageDraw.floodfill(plate, (plate.width // 2, plate.height // 2), (29, 39, 51, 255), thresh=0)
save(plate, "plate")

# ---------- Contact sheet (optional argument: output folder) ----------
if len(sys.argv) > 1:
    names = ["bg_kit", "bg_board", "bg_sequencer", "paper", "polaroid", "button", "plate", "dial", "rotor", "vial", "tile", "inset", "cross"]
    ims = [Image.open(os.path.join(DST, n + ".png")) for n in names]
    for n, im in zip(names, ims):
        k = 2 if im.width > 150 else 4 if im.width > 60 else 8
        sheet = Image.new("RGBA", (im.width * k, im.height * k), (255, 0, 255, 255))
        g = im.resize((im.width * k, im.height * k), Image.NEAREST)
        sheet.paste(g, (0, 0), g)
        sheet.save(os.path.join(sys.argv[1], "ctrl_" + n + ".png"))
