# EcoServ 2.0 — текстуры электроники (16 видов × выкл/вкл) и модели.
# python3 v2_electric.py <assets/ecoserv>
import sys, os, json, random
from PIL import Image, ImageDraw

ROOT = sys.argv[1]
S = 32
IDS = ["solar", "generator", "battery", "switch", "splitter", "and", "or", "xor", "timer",
       "sensor", "rs_in", "rs_out", "lamp", "door", "alarm", "meter"]
COL = [0x3060C0, 0x606060, 0x30A050, 0xC0A030, 0xA06030, 0x5050C0, 0x5090C0, 0x9050C0,
       0xC07030, 0xC03050, 0xB02020, 0xE04040, 0xF0E080, 0x806040, 0xE02020, 0x40C0C0]

def c(h, a=255): return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def base(seed):
    im = Image.new('RGBA', (S, S)); px = im.load(); r = random.Random(seed)
    for y in range(S):
        for x in range(S):
            v = 70 + r.randint(-6, 6); px[x, y] = (v, v + 3, v + 8, 255)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=(150, 156, 166, 255))
    d.rectangle([1, 1, S - 2, S - 2], outline=(40, 42, 48, 255))
    for (x, y) in [(3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)]:
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(120, 124, 132, 255))
    return im

def glyph(d, i, col, on):
    hi = mix(col, (255, 255, 255, 255), 0.45) if on else mix(col, (0, 0, 0, 255), 0.35)
    k = IDS[i]
    if k == "solar":
        d.rectangle([5, 5, 26, 26], fill=(20, 30, 70, 255))
        for x in range(5, 27, 7): d.line([(x, 5), (x, 26)], fill=hi)
        for y in range(5, 27, 7): d.line([(5, y), (26, y)], fill=hi)
    elif k == "generator":
        d.rectangle([6, 10, 25, 25], fill=(40, 40, 44, 255)); d.rectangle([9, 13, 22, 22], fill=hi if on else (60, 30, 20, 255))
        for x in (8, 13, 18, 23): d.line([(x, 6), (x, 9)], fill=(150, 150, 150, 255))
    elif k == "battery":
        d.rectangle([8, 6, 23, 26], outline=(200, 200, 200, 255)); d.rectangle([13, 4, 18, 6], fill=(200, 200, 200, 255))
        lvl = 4 if on else 1
        for j in range(lvl): d.rectangle([10, 22 - j * 4, 21, 24 - j * 4], fill=hi)
    elif k == "switch":
        d.rectangle([11, 6, 20, 25], fill=(30, 30, 34, 255)); d.rectangle([12, 7 if on else 16, 19, 15 if on else 24], fill=hi)
    elif k == "splitter":
        d.line([(15, 26), (15, 15)], fill=hi, width=2); d.line([(15, 15), (6, 6)], fill=hi, width=2)
        d.line([(15, 15), (15, 5)], fill=hi, width=2); d.line([(15, 15), (25, 6)], fill=hi, width=2)
    elif k in ("and", "or", "xor"):
        d.text((8 if k != "xor" else 6, 10), {"and": "AND", "or": "OR", "xor": "XOR"}[k], fill=hi)
        d.line([(4, 22), (27, 22)], fill=hi)
    elif k == "timer":
        d.ellipse([7, 7, 24, 24], outline=hi, width=2); d.line([(15, 15), (15, 9)], fill=hi, width=2); d.line([(15, 15), (20, 17)], fill=hi)
    elif k == "sensor":
        d.ellipse([10, 10, 21, 21], fill=hi); d.arc([5, 5, 26, 26], 200, 340, fill=hi); d.arc([5, 5, 26, 26], 20, 160, fill=hi)
    elif k == "rs_in":
        d.polygon([(6, 15), (16, 8), (16, 22)], fill=(200, 30, 30, 255) if on else (90, 20, 20, 255)); d.rectangle([16, 13, 25, 17], fill=hi)
    elif k == "rs_out":
        d.rectangle([6, 13, 15, 17], fill=hi); d.polygon([(15, 8), (25, 15), (15, 22)], fill=(255, 40, 40, 255) if on else (90, 20, 20, 255))
    elif k == "lamp":
        d.rectangle([4, 4, 27, 27], fill=(255, 240, 170, 255) if on else (90, 85, 60, 255))
        d.rectangle([4, 4, 27, 27], outline=(200, 190, 120, 255) if on else (60, 58, 40, 255))
        d.line([(4, 15), (27, 15)], fill=(220, 200, 120, 255) if on else (70, 66, 46, 255)); d.line([(15, 4), (15, 27)], fill=(220, 200, 120, 255) if on else (70, 66, 46, 255))
    elif k == "door":
        d.rectangle([9, 5, 22, 26], fill=(110, 80, 45, 255)); d.rectangle([11, 7, 20, 14], fill=(140, 105, 60, 255))
        d.rectangle([18, 16, 20, 18], fill=hi)
    elif k == "alarm":
        d.ellipse([7, 7, 24, 24], fill=(255, 60, 40, 255) if on else (100, 25, 20, 255)); d.ellipse([12, 10, 17, 15], fill=(255, 200, 190, 255) if on else (140, 60, 50, 255))
    elif k == "meter":
        d.rectangle([6, 8, 25, 23], fill=(15, 30, 30, 255)); d.arc([8, 10, 23, 30], 200, 340, fill=hi)
        d.line([(15, 20), (20 if on else 10, 13)], fill=(255, 255, 255, 255))
    if on:
        d.point((S - 5, 4), fill=(80, 255, 80, 255)); d.point((S - 6, 4), fill=(80, 255, 80, 255))
    else:
        d.point((S - 5, 4), fill=(80, 20, 20, 255)); d.point((S - 6, 4), fill=(80, 20, 20, 255))

def write(rel, obj):
    p = os.path.join(ROOT, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f: json.dump(obj, f, indent=2)

variants = {}
for i, k in enumerate(IDS):
    for on in (False, True):
        im = base(i * 7 + (3 if on else 0)); glyph(ImageDraw.Draw(im), i, c(COL[i]), on)
        name = "electric_" + k + ("_on" if on else "")
        p = os.path.join(ROOT, "textures/blocks/" + name + ".png"); os.makedirs(os.path.dirname(p), exist_ok=True); im.save(p)
        write("models/block/" + name + ".json", {"parent": "block/cube_all", "textures": {"all": "ecoserv:blocks/" + name}})
        variants["lit=%s,type=%d" % ("true" if on else "false", i)] = {"model": "ecoserv:" + name}
    write("models/item/electric_" + k + ".json", {"parent": "ecoserv:block/electric_" + k})
write("blockstates/electric.json", {"variants": variants})
print("ok", len(variants))
