# EcoServ 2.0 — процедурные текстуры 32×32 для новых блоков и предметов.
# python3 v2_textures.py <assets/ecoserv/textures>
import sys, os, math, random
from PIL import Image, ImageDraw

OUT = sys.argv[1]
S = 32

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i]-a[i]) * t) for i in range(4))

def noise_fill(img, base, amp=10, seed=1):
    r = random.Random(seed)
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = r.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, base[i] + d)) for i in range(3)) + (255,)

def bevel(img, light, dark, w=1):
    d = ImageDraw.Draw(img)
    for i in range(w):
        d.line([(i, i), (S-1-i, i)], fill=light); d.line([(i, i), (i, S-1-i)], fill=light)
        d.line([(i, S-1-i), (S-1-i, S-1-i)], fill=dark); d.line([(S-1-i, i), (S-1-i, S-1-i)], fill=dark)

def save(img, rel):
    p = os.path.join(OUT, rel); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p)

# --- банкомат ---
def metal(seed):
    im = Image.new('RGBA', (S, S)); noise_fill(im, hexc('8a9099'), 7, seed); bevel(im, hexc('c5ccd6'), hexc('4a5058'), 2); return im
side = metal(3)
d = ImageDraw.Draw(side)
for y in (10, 21): d.line([(4, y), (27, y)], fill=hexc('6b717a'))
save(side, 'blocks/atm_side.png')
top = metal(4); d = ImageDraw.Draw(top); d.rectangle([6, 6, 25, 25], outline=hexc('5c626b')); save(top, 'blocks/atm_top.png')
front = metal(5); d = ImageDraw.Draw(front)
d.rectangle([5, 4, 26, 15], fill=hexc('0f2a1c'), outline=hexc('2b3036'))          # экран
for x in range(7, 25, 3): d.point((x, 7), fill=hexc('5dffa0')); d.point((x+1, 10), fill=hexc('3fd98a'))
d.line([(7, 13), (18, 13)], fill=hexc('ffd34d'))
for i in range(3):                                                                    # клавиши
    for j in range(3):
        d.rectangle([7+j*4, 18+i*3, 9+j*4, 19+i*3], fill=hexc('d9dde3'))
d.rectangle([20, 18, 25, 20], fill=hexc('2b2f35')); d.line([(21, 19), (24, 19)], fill=hexc('111111'))   # щель карты
d.rectangle([20, 23, 25, 27], fill=hexc('3a3f46')); d.line([(21, 26), (24, 26)], fill=hexc('caa13a'))  # лоток
save(front, 'blocks/atm_front.png')

# --- блок-программа ---
im = Image.new('RGBA', (S, S)); noise_fill(im, hexc('2a2f3a'), 6, 9); bevel(im, hexc('5a6578'), hexc('14171d'), 2)
d = ImageDraw.Draw(im)
d.rectangle([6, 6, 25, 25], fill=hexc('11141a'), outline=hexc('3fc8ff'))
for (x0, y0, x1, y1) in [(8, 10, 14, 10), (14, 10, 14, 16), (14, 16, 22, 16), (22, 16, 22, 22), (10, 20, 18, 20)]:
    d.line([(x0, y0), (x1, y1)], fill=hexc('3fc8ff'))
for (x, y) in [(8, 10), (22, 22), (18, 20), (10, 20)]:
    d.rectangle([x-1, y-1, x+1, y+1], fill=hexc('ff4d6d'))
d.text((15, 6), '{}', fill=hexc('ffe066'))
save(im, 'blocks/logic_block.png')

# --- маркер (частицы при ломании) ---
im = Image.new('RGBA', (S, S), hexc('000000', 0)); d = ImageDraw.Draw(im)
d.rectangle([2, 2, 29, 29], outline=hexc('ffffff', 200)); d.line([(2, 2), (29, 29)], fill=hexc('ffffff', 120)); d.line([(29, 2), (2, 29)], fill=hexc('ffffff', 120))
save(im, 'blocks/marker.png')

# --- иконки маркеров ---
IDS = ["spawn","team_red","team_blue","checkpoint","finish","kill","item","mob","npc","camera","path","connector","light","sound","text","custom"]
COLORS = [0x40FF40,0xFF4040,0x4080FF,0xFFE040,0xFFD700,0xFF2020,0x40FFFF,0xA040FF,0x40FF90,0xFFFFFF,0x90C0FF,0xFF9020,0xFFFF90,0xFF80C0,0xE0E0E0,0xC0C0C0]
def glyph(d, kind, c):
    w = (255, 255, 255, 255)
    if kind in ("spawn", "team_red", "team_blue"):
        d.ellipse([12, 6, 19, 13], fill=w); d.polygon([(10, 26), (21, 26), (19, 14), (12, 14)], fill=w)
    elif kind == "checkpoint":
        d.line([(11, 6), (11, 26)], fill=w, width=2); d.polygon([(12, 6), (23, 10), (12, 14)], fill=w)
    elif kind == "finish":
        for i in range(4):
            for j in range(4):
                if (i + j) % 2 == 0: d.rectangle([8+i*4, 8+j*4, 11+i*4, 11+j*4], fill=w)
    elif kind == "kill":
        d.ellipse([9, 7, 22, 19], fill=w); d.rectangle([12, 18, 19, 23], fill=w)
        d.rectangle([11, 11, 13, 13], fill=(0,0,0,255)); d.rectangle([18, 11, 20, 13], fill=(0,0,0,255))
    elif kind == "item":
        d.polygon([(16, 7), (24, 12), (24, 21), (16, 26), (8, 21), (8, 12)], outline=w); d.line([(8, 12), (16, 17), (24, 12)], fill=w); d.line([(16, 17), (16, 26)], fill=w)
    elif kind == "mob":
        d.rectangle([9, 9, 22, 22], fill=w); d.rectangle([11, 12, 13, 14], fill=(0,0,0,255)); d.rectangle([18, 12, 20, 14], fill=(0,0,0,255)); d.rectangle([13, 17, 18, 20], fill=(0,0,0,255))
    elif kind == "npc":
        d.ellipse([11, 6, 20, 15], fill=w); d.rectangle([9, 16, 22, 26], fill=w); d.rectangle([13, 9, 14, 10], fill=(0,0,0,255)); d.rectangle([17, 9, 18, 10], fill=(0,0,0,255))
    elif kind == "camera":
        d.rectangle([7, 11, 20, 22], fill=w); d.polygon([(21, 14), (26, 11), (26, 22), (21, 19)], fill=w); d.ellipse([10, 13, 16, 19], fill=(0,0,0,255))
    elif kind == "path":
        for k, (x, y) in enumerate([(8, 24), (14, 17), (20, 21), (24, 9)]):
            d.ellipse([x-2, y-2, x+2, y+2], fill=w)
        d.line([(8, 24), (14, 17), (20, 21), (24, 9)], fill=w)
    elif kind == "connector":
        d.rectangle([7, 13, 15, 19], fill=w); d.rectangle([17, 13, 25, 19], outline=w); d.rectangle([15, 15, 17, 17], fill=w)
    elif kind == "light":
        d.ellipse([10, 6, 21, 17], fill=w); d.rectangle([13, 18, 18, 23], fill=w)
        for a in range(0, 360, 45):
            x = 16 + 13 * math.cos(math.radians(a)); y = 12 + 10 * math.sin(math.radians(a)); d.point((x, y), fill=w)
    elif kind == "sound":
        d.polygon([(8, 13), (12, 13), (17, 8), (17, 24), (12, 19), (8, 19)], fill=w)
        d.arc([16, 10, 24, 22], -60, 60, fill=w); d.arc([18, 7, 28, 25], -60, 60, fill=w)
    elif kind == "text":
        d.line([(9, 8), (23, 8)], fill=w, width=2); d.line([(16, 8), (16, 25)], fill=w, width=2)
    else:
        d.text((12, 9), '?', fill=w)

for kind, col in zip(IDS, COLORS):
    c = ((col >> 16) & 255, (col >> 8) & 255, col & 255, 255)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 1, 30, 30], radius=6, fill=mix(c, (20, 20, 30, 255), 0.55), outline=c, width=2) if hasattr(d, 'rounded_rectangle') else d.rectangle([1, 1, 30, 30], fill=mix(c, (20, 20, 30, 255), 0.55), outline=c)
    glyph(d, kind, c)
    # тонкая рамка «невидимости»
    for i in range(2, 30, 4): d.point((i, 1), fill=(255, 255, 255, 180)); d.point((1, i), fill=(255, 255, 255, 180))
    save(im, 'items/marker_%s.png' % kind)

# --- очки разработчика ---
im = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.line([(2, 14), (29, 14)], fill=hexc('1d1f26'), width=3)
for x0 in (4, 18):
    d.rounded_rectangle([x0, 10, x0 + 10, 21], radius=3, fill=hexc('3fc8ff', 220), outline=hexc('1d1f26'), width=2) if hasattr(d, 'rounded_rectangle') else d.rectangle([x0, 10, x0+10, 21], fill=hexc('3fc8ff', 220), outline=hexc('1d1f26'))
    d.line([(x0 + 3, 13), (x0 + 6, 13)], fill=hexc('e8fbff'))
d.line([(14, 14), (17, 14)], fill=hexc('ff4d6d'), width=2)
d.point((7, 18), fill=hexc('ff4d6d')); d.point((24, 18), fill=hexc('3070ff'))
save(im, 'items/dev_glasses.png')
print('ok')
