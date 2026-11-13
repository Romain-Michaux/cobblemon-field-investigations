# World textures and models (discreet traces, buried clues, disturbed soil, items) and mini-game art, built from the
# PixelLab images in tools/pixellab.
# Usage: python tools/gen_world.py   (needs Pillow)
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "pixellab")
RES = os.path.join(HERE, "..", "src", "main", "resources")
NS = "cobblemon_field_investigations"


def load(name):
    return Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")


def save(im, folder, name):
    path = os.path.join(RES, "assets", NS, "textures", folder)
    os.makedirs(path, exist_ok=True)
    im.save(os.path.join(path, name + ".png"))
    print(f"{folder}/{name}.png {im.size}")


def write_json(parts, obj):
    path = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def fade(im, alpha):
    """Makes a trace more discreet: same drawing, slightly transparent."""
    im = im.copy()
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, int(a * alpha))
    return im


# ---------- Surface traces (blocks): discreet, no glow ----------
h = random.Random(11)
residue = Image.new("RGBA", (16, 16))
for _ in range(11):
    x, y = h.randint(2, 13), h.randint(2, 13)
    residue.putpixel((x, y), h.choice([(111, 175, 168, 210), (150, 196, 190, 190), (74, 120, 118, 200)]))
for x, y in [(5, 6), (10, 9), (7, 11)]:
    for dx, dy in [(0, 0), (1, 0), (0, 1)]:
        residue.putpixel((x + dx, y + dy), (132, 205, 196, 230))
    residue.putpixel((x, y), (214, 244, 238, 240))
save(residue, "block", "trace_0")
save(fade(load("world_type2"), 0.85), "block", "trace_1")
save(fade(load("world_size"), 0.8), "block", "trace_2")
save(fade(load("world_egg"), 0.92), "block", "trace_3")
save(fade(load("world_stat"), 0.85), "block", "trace_4")
# Clues uncovered from the ground: bones, shells, fang.
# The numbers follow Genetics.buriedBlock: bone and tooth (growth), eggshells, feather and moult (lineage), fang, claw
# and scale (aptitude).
BURIED = ((5, "buried_bone", "size", 0), (6, "buried_shell", "egg", 0), (7, "buried_fang", "stat", 0), (8, "buried_tooth", "size", 1),
          (9, "buried_feather", "egg", 1), (10, "buried_moult", "egg", 2), (11, "buried_claw", "stat", 1), (12, "buried_scale", "stat", 2))
for i, name, _, _ in BURIED:
    save(load(name), "block", "trace_%d" % i)

# ---------- Disturbed soil: an overlay on top of the original block, dug deeper as it is brushed ----------
for step in range(4):
    h = random.Random(7)
    im = Image.new("RGBA", (16, 16))
    radius = [0, 3.0, 4.6, 6.2][step]
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 + h.uniform(-0.9, 0.9)
            if d <= radius:
                im.putpixel((x, y), (38, 27, 19, 215) if d <= radius - 1.3 else (58, 42, 28, 170))
            elif d <= radius + 1.2 and h.random() < 0.45:
                im.putpixel((x, y), (120, 94, 62, 150))
    # Clods and cracks around it: the sign that the soil was turned over.
    for _ in range(12):
        a, r = h.uniform(0, 6.283), h.uniform(4.2, 7.2)
        x, y = int(7.5 + r * __import__("math").cos(a)), int(7.5 + r * __import__("math").sin(a))
        if 0 <= x < 16 and 0 <= y < 16 and im.getpixel((x, y))[3] == 0:
            im.putpixel((x, y), h.choice([(44, 31, 21, 150), (150, 120, 84, 130)]))
    if step == 3:
        for x, y in [(6, 9), (7, 8), (8, 7), (9, 6), (9, 7), (6, 8)]:
            im.putpixel((x, y), (226, 214, 186, 255))
    save(im, "block", "soil_%d" % step)

BASES = ["minecraft:block/dirt", "minecraft:block/sand", "minecraft:block/gravel"]
variants = {}
for m, base in enumerate(BASES):
    for k in range(4):
        name = "disturbed_soil_%d_%d" % (m, k)
        write_json(("assets", NS, "models", "block", name + ".json"), {
            "parent": "minecraft:block/block",
            "textures": {"base": base, "top": NS + ":block/soil_%d" % k, "particle": base},
            "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16],
                 "faces": {d: {"texture": "#base", "cullface": d} for d in ("north", "south", "east", "west", "up", "down")}},
                {"from": [-0.02, -0.02, -0.02], "to": [16.02, 16.02, 16.02], "shade": False,
                 "faces": {d: {"texture": "#top"} for d in ("north", "south", "east", "west", "up", "down")}},
            ]})
        # Property names as declared in ModContent (UNCOVERED, MATERIAL).
        variants["uncovered=%d,material=%d" % (k, m)] = {"model": NS + ":block/" + name}
write_json(("assets", NS, "blockstates", "disturbed_soil.json"), {"variants": variants})

# ---------- Traces: thirteen kinds (5 on the surface + 8 uncovered clues) ----------
variants = {}
for i in range(13):
    write_json(("assets", NS, "models", "block", "trace_%d.json" % i), {
        "ambientocclusion": False,
        "textures": {"t": NS + ":block/trace_%d" % i, "particle": NS + ":block/trace_%d" % i},
        "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False,
                      "faces": {"up": {"texture": "#t", "uv": [0, 0, 16, 16]}, "down": {"texture": "#t", "uv": [0, 0, 16, 16]}}}]})
    write_json(("assets", NS, "models", "block", "trace_%d_wall.json" % i), {
        "ambientocclusion": False,
        "textures": {"t": NS + ":block/trace_%d" % i, "particle": NS + ":block/trace_%d" % i},
        "elements": [{"from": [0, 0, 15.75], "to": [16, 16, 15.75], "shade": False,
                      "faces": {"north": {"texture": "#t", "uv": [0, 0, 16, 16]}, "south": {"texture": "#t", "uv": [0, 0, 16, 16]}}}]})
    # Property names as declared in ModContent (FACING, KIND).
    for d in ("up", "down"):
        variants["facing=%s,kind=%d" % (d, i)] = {"model": NS + ":block/trace_%d" % i}
    for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": NS + ":block/trace_%d_wall" % i}
        if r:
            v["y"] = r
        variants["facing=%s,kind=%d" % (d, i)] = v
write_json(("assets", NS, "blockstates", "trace.json"), {"variants": variants})

# ---------- Items ----------
save(load("item_kit2"), "item", "field_kit")
save(load("item_vial"), "item", "vial")
save(load("item_sample"), "item", "sample")

# ---------- Mini-game art (GUI) ----------
for name in ("bark", "earth", "petri", "large_bone", "large_shell", "large_fang",
             "debris_shell", "debris_twig", "debris_leaf", "debris_pebble"):
    save(load(name), "gui", name)
for _, name, kind, variant in BURIED:
    save(load(name), "gui", "buried_" + kind + ("_%d" % variant if variant else ""))
# Props and backgrounds of the mini-games added in 1.1.
for name in ("sieve", "tweezers", "swab", "burette", "ice_cube", "rock_face", "bg_water", "night", "undergrowth", "nest", "tray"):
    save(load(name), "gui", name)
save(load("radar_b"), "gui", "radar")  # HUD detector: sonar on the left (centre 33,24), display on the right (55..177 x 14..33)
save(load("crystal_a"), "gui", "crystal")  # tinted in game: dull, teal (stable) or red (unstable)
# Electrophoresis gel tray (240 x 104), drawn here: PixelLab draws it in perspective, and it has to be perfectly flat.
h = random.Random(3)
gel = Image.new("RGBA", (240, 104))
for y in range(104):
    for x in range(240):
        edge = min(x, y, 239 - x, 103 - y)
        corner = (x < 3 or x > 236) and (y < 3 or y > 100) and (min(x, 239 - x) + min(y, 103 - y) < 3)
        if corner:
            continue
        if edge == 0:
            c = (11, 17, 24)
        elif edge <= 3:
            c = (226, 232, 238) if edge < 3 else (150, 160, 176)
            if y > 99 and edge < 3:
                c = (176, 186, 200)
        elif edge == 4:
            c = (11, 17, 24)
        else:
            # midnight blue gel, a little more violet and lighter in the middle (UV lamp), with a light grain
            d = abs(x - 120) / 120
            v = 1 - d * 0.45
            g = h.choice((0, 0, 0, 3, -3))
            c = (int(26 * v) + 10 + g, int(30 * v) + 14 + g, int(74 * v) + 30 + g)
        gel.putpixel((x, y), c + (255,))
save(gel, "gui", "gel")
save(load("viewfinder_a"), "gui", "viewfinder")  # scanner viewfinder, tinted in game
footprint = load("footprint")
footprint = footprint.crop(footprint.getbbox())
save(footprint, "gui", "footprint")
assert footprint.size == (74, 70), "MeasureGame.EL / EH must be updated"
# Colorimetry Erlenmeyer flask: the glass keeps its colours; the liquid (light, warm area at the bottom) goes into a
# second texture in light grey, the only one tinted in game.
glass = load("erlenmeyer")
liquid = Image.new("RGBA", glass.size)
px = glass.load()
for y in range(37, glass.height):
    for x in range(glass.width):
        r, g, b, a = px[x, y]
        if a and r * 3 + g * 6 + b >= 450 and not b > r + 8:
            v = min(255, int(max(r, g, b) * 1.08) + 8)
            liquid.putpixel((x, y), (v, v, v, 255))
            px[x, y] = (0, 0, 0, 0)
save(glass, "gui", "erlenmeyer")
save(liquid, "gui", "erlenmeyer_liquid")

# The language files are written by tools/gen_lang.py.

# Tree climb branches: the PixelLab image points to the right; the left one is its mirror image.
# The dead branch is made from the living one, so they share the same shape and bark: its leaves are removed, its tip
# broken, its wood dulled and a split opened along it.
from PIL import ImageOps


def dead_branch(living):
    im = living.copy()
    px = im.load()
    is_bark = lambda c: c[3] > 0 and c[0] > c[1] * 1.15 and c[0] > 60
    tip = max(x for x in range(im.width) for y in range(im.height) if is_bark(px[x, y]))
    bark_rows = [y for y in range(im.height) if any(is_bark(px[x, y]) for x in range(im.width))]
    middle = (min(bark_rows) + max(bark_rows)) // 2
    for y in range(im.height):
        # The tip is snapped off clean, in a jagged line.
        limit = tip - (2 if (y - middle) % 3 == 0 else 0) - abs(y - middle) // 3
        for x in range(im.width):
            r, v, b, a = px[x, y]
            if a == 0:
                continue
            if x > limit or (v > r * 1.05 and v > b):
                px[x, y] = (0, 0, 0, 0)  # leaves and end of the branch
            elif is_bark((r, v, b, a)):
                grey = (r * 3 + v * 6 + b) // 10
                px[x, y] = ((r + grey * 2) * 26 // 100, (v + grey * 2) * 27 // 100, (b + grey * 2) * 29 // 100, a)
    # A dark split along the wood, and a black edge on the break.
    for x in range(im.width):
        if x % 7 != 3 and px[x, middle][3] > 0 and px[x, middle][0] > 30:
            px[x, middle] = (28, 24, 24, 255)
    for y in range(im.height):
        for x in range(im.width - 1, -1, -1):
            if px[x, y][3] > 0:
                px[x, y] = (20, 16, 16, 255)
                break
    return im


for name in ("branch", "dead_branch"):
    raw = load("branch")
    if name == "dead_branch":
        raw = dead_branch(raw)
    cropped = raw.crop(raw.getbbox())
    im = Image.new("RGBA", (32, 32))
    im.alpha_composite(cropped, (0, (32 - cropped.height) // 2))  # flush left: that side touches the trunk
    save(im, "gui", name + "_right")
    save(ImageOps.mirror(im), "gui", name + "_left")

# Tree climb scenery: the forest in the background is fixed; the trunk is a straight 56 px strip (the part of a PixelLab
# image without roots, joined by a ten-row fade) that tiles seamlessly and scrolls.
for name in ("trunk", "forest"):
    save(load(name), "gui", name)

# Investigator badge of the logbook (tinted in game with the rank colour).
save(load("badge"), "gui", "badge")

# Abseil: climber (32 x 48, drawn at 16 x 24), a clue on the rock per trace kind (32 x 32, drawn at 16 x 16), the
# boulder (cropped, drawn at the size of its hit circle) and the rock bar: the middle of a PixelLab outcrop, mirrored so
# that it tiles, 32 rows high (drawn 16 px high).
save(load("abseil_climber"), "gui", "abseil_climber")
# The clues get a pale gold rim (2 texels = 1 px on screen): they must stand out on the dark rock and read as pickable.
def rimmed(im, colour=(242, 214, 140, 255), width=2):
    out = Image.new("RGBA", im.size)
    a = im.getchannel("A")
    for dx in range(-width, width + 1):
        for dy in range(-width, width + 1):
            if dx * dx + dy * dy <= width * width + 1:
                shifted = Image.new("L", im.size)
                shifted.paste(a, (dx, dy))
                out.paste(Image.new("RGBA", im.size, colour), (0, 0), shifted.point(lambda v: 255 if v > 96 else 0))
    out.alpha_composite(im)
    return out


for kind in ("stat", "size", "egg", "type1", "type2"):
    save(rimmed(load("abseil_" + kind)), "gui", "abseil_" + kind)
boulder = load("abseil_boulder")
save(boulder.crop(boulder.getbbox()), "gui", "abseil_boulder")
slab = load("abseil_ledge").crop((24, 8, 104, 27))
tile = Image.new("RGBA", (slab.width * 2, slab.height))
tile.paste(slab, (0, 0))
tile.paste(slab.transpose(Image.FLIP_LEFT_RIGHT), (slab.width, 0))
save(tile.resize((tile.width, 32), Image.NEAREST), "gui", "abseil_ledge")

# Bite: a fang cropped to its outline (stretched in game to each tooth's length), and two 64 x 64 tiles drawn at half
# size: the fossil bone of the jaw and the moulding putty of the cast.
fang = load("bite_tooth")
save(fang.crop(fang.getbbox()), "gui", "bite_tooth")
save(load("bite_bone"), "gui", "bite_bone")
save(load("bite_putty"), "gui", "bite_putty")

# Brushing: two soil layers read texel by texel by BrushGame (a warm dry crust, then damp earth), mirrored four ways so
# that they tile without seams, and the brush that follows the pointer (32 x 32, drawn at 16 x 16).
def mirrored(im):
    out = Image.new("RGBA", (im.width * 2, im.height * 2))
    out.paste(im, (0, 0))
    out.paste(im.transpose(Image.FLIP_LEFT_RIGHT), (im.width, 0))
    out.paste(im.transpose(Image.FLIP_TOP_BOTTOM), (0, im.height))
    out.paste(im.transpose(Image.ROTATE_180), (im.width, im.height))
    return out


def tinted(im, k):
    r, g, b, a = im.split()
    return Image.merge("RGBA", [c.point(lambda v, f=f: min(255, int(v * f))) for c, f in zip((r, g, b), k)] + [a])


# The crust has a light-to-dark gradient that would show as a diamond pattern once mirrored: keep only its fine detail
# (the image minus its blur, around its mean colour) and soften the cracks.
def flattened(im, radius=10, cracks=0.45):
    from PIL import ImageFilter, ImageStat
    rgb = im.convert("RGB")
    blurred = rgb.filter(ImageFilter.GaussianBlur(radius))
    mean = ImageStat.Stat(rgb).mean
    out = Image.new("RGB", rgb.size)
    for y in range(rgb.height):
        for x in range(rgb.width):
            p, q = rgb.getpixel((x, y)), blurred.getpixel((x, y))
            v = [p[i] - q[i] + mean[i] for i in range(3)]
            if sum(v) < sum(mean) * 0.8:
                v = [v[i] + (mean[i] - v[i]) * cracks for i in range(3)]
            out.putpixel((x, y), tuple(max(0, min(255, int(c))) for c in v))
    return out.convert("RGBA")


save(mirrored(tinted(flattened(load("brush_crust")), (0.93, 0.80, 0.62))), "gui", "brush_crust")
save(mirrored(load("brush_damp").convert("RGBA").crop((10, 10, 54, 54))), "gui", "brush_damp")
save(load("brush_tool"), "gui", "brush_tool")

# Cast: the jug of plaster (32 x 32, drawn at 16 x 16), and the ground in cross-section: a 128 x 168 strip (drawn at
# 64 x 84) starting with the surface layers of the PixelLab mud, then the damp earth of the brushing game, mirrored so
# that it goes on without seams.
save(load("cast_jug"), "gui", "cast_jug")
top = load("cast_mud").convert("RGBA").crop((0, 8, 64, 30))
from PIL import ImageEnhance
earth = mirrored(ImageEnhance.Contrast(load("brush_damp").convert("RGBA").crop((10, 10, 54, 54))).enhance(0.55))
wide = Image.new("RGBA", (128, 168))
for y in range(0, 168, earth.height):
    for x in range(0, 128, earth.width):
        wide.paste(earth, (x, y))
wide.paste(top, (0, 0))
wide.paste(top.transpose(Image.FLIP_LEFT_RIGHT), (64, 0))
save(wide, "gui", "cast_mud")
