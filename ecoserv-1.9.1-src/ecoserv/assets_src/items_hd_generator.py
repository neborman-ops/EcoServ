# EcoServ 1.9.0 — HD (32x32) item textures, procedural.
# python3 items_hd_generator.py <out_dir>    (out_dir = src/main/resources/assets/ecoserv/textures/items)
import math, sys, os
from PIL import Image

S = 32
OUT = sys.argv[1] if len(sys.argv) > 1 else '.'


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


class Img:
    def __init__(self):
        self.im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.px = self.im.load()

    def p(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < S and 0 <= y < S and c[3] > 0:
            if c[3] >= 255 or self.px[x, y][3] == 0:
                self.px[x, y] = c
            else:
                self.px[x, y] = mix(self.px[x, y], (c[0], c[1], c[2], 255), c[3] / 255.0)

    def get(self, x, y):
        return self.px[x, y] if 0 <= x < S and 0 <= y < S else (0, 0, 0, 0)

    def outline(self, col):
        pts = []
        for y in range(S):
            for x in range(S):
                if self.px[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if self.get(x + dx, y + dy)[3] > 200:
                            pts.append((x, y))
                            break
        for x, y in pts:
            self.px[x, y] = col


def shaft(img, x0, y0, x1, y1, wood=('5b3a1e', '8a5a2e', 'b98447')):
    """Diagonal staff from (x0,y0) bottom-left to (x1,y1), 3px thick with lit edge and grain."""
    dark, mid, lite = hexc(wood[0]), hexc(wood[1]), hexc(wood[2])
    n = int(max(abs(x1 - x0), abs(y1 - y0)))
    for i in range(n + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        img.p(x, y, mid)
        img.p(x + 1, y, mid if i % 5 else dark)   # grain knots
        img.p(x, y - 1, lite)
        img.p(x + 1, y + 1, dark)


def ferrule(img, cx, cy):
    g, gd, gl = hexc('f2c447'), hexc('a8741f'), hexc('fff2b0')
    for dx, dy, c in ((0, 0, g), (1, 0, gd), (-1, 0, gl), (0, 1, gd), (0, -1, gl), (1, -1, g), (-1, 1, g), (1, 1, gd), (-1, -1, gl)):
        img.p(cx + dx, cy + dy, c)


def orb(img, cx, cy, r, base, glyph=None, dark=False):
    b = hexc(base)
    hi = mix(b, hexc('ffffff'), 0.65)
    lo = mix(b, hexc('000000'), 0.55 if not dark else 0.8)
    for y in range(S):
        for x in range(S):
            dx = (x + 0.5 - cx) / r
            dy = (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 <= 1.0:
                nz = math.sqrt(1 - d2)
                lam = max(0.0, -0.55 * dx - 0.6 * dy + 0.58 * nz)      # light from top-left
                c = mix(lo, b, min(1.0, lam * 1.4))
                spec = max(0.0, lam - 0.86) * 7
                c = mix(c, hi, spec)
                rim = max(0.0, d2 - 0.78) * 2.5                         # rim light / fresnel
                c = mix(c, mix(b, hexc('ffffff'), 0.25), rim * 0.5)
                img.p(x, y, c)
    if glyph:
        glyph(img, cx, cy, r)


def sparkle(img, x, y, col='ffffff', big=True):
    c = hexc(col)
    img.p(x, y, c)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        img.p(x + dx, y + dy, (c[0], c[1], c[2], 190))
    if big:
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            img.p(x + dx, y + dy, (c[0], c[1], c[2], 90))


def stripes(col, n=2, width=1.6):
    c = hexc(col)

    def g(img, cx, cy, r):
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if dx * dx + dy * dy < (r - 1) ** 2:
                    u = (dx + dy) / math.sqrt(2)
                    for k in range(n):
                        off = (k - (n - 1) / 2) * r * 0.75
                        if abs(u - off) < width * 0.5:
                            img.p(x, y, (c[0], c[1], c[2], 230))
    return g


def wand(name, base, glyph=None, accent='ffffff', dark=False, wood=None, after=None):
    img = Img()
    shaft(img, 4, 28, 19, 13, **({'wood': wood} if wood else {}))
    ferrule(img, 19, 13)
    orb(img, 23, 9, 6.6, base, glyph, dark)
    img.outline(hexc('1b1020'))
    sparkle(img, 29, 3, accent)
    sparkle(img, 15, 4, accent, big=False)
    if after:
        after(img)
    img.im.save(os.path.join(OUT, name + '.png'))
    return img


def ring_glyph(col):
    c = hexc(col)

    def g(img, cx, cy, r):
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if abs(d - r * 0.55) < 0.7:
                    img.p(x, y, (c[0], c[1], c[2], 220))
    return g


def blackhole_glyph(img, cx, cy, r):
    # dark core with a thin bright photon ring and a tilted accretion band
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d < r * 0.48:
                img.p(x, y, hexc('05020a'))
            elif d < r * 0.62:
                img.p(x, y, hexc('ffd27a', 230))
            v = dy * 0.95 - dx * 0.3
            if abs(v) < 0.9 and r * 0.5 < d < r * 1.05:
                img.p(x, y, hexc('ff9a3c', 230))


def check_glyph(col):
    c = hexc(col)

    def g(img, cx, cy, r):
        pts = [(-3, 0), (-2, 1), (-1, 2), (0, 1), (1, 0), (2, -1), (3, -2)]
        for x, y in pts:
            img.p(cx + x, cy + y, c)
            img.p(cx + x, cy + y - 1, c)
    return g


def x_glyph(col):
    c = hexc(col)

    def g(img, cx, cy, r):
        for i in range(-3, 4):
            img.p(cx + i, cy + i, c)
            img.p(cx + i, cy - i, c)
    return g


def bolt_glyph(col):
    c = hexc(col)

    def g(img, cx, cy, r):
        pts = [(1, -4), (0, -3), (0, -2), (-1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (0, 1), (-1, 2), (-1, 3), (-2, 4)]
        for x, y in pts:
            img.p(cx + x, cy + y, c)
    return g


def fill_glyph(col):
    c = hexc(col)

    def g(img, cx, cy, r):
        for y in range(-3, 4):
            for x in range(-3, 4):
                if (x + y) % 2 == 0:
                    img.p(cx + x, cy + y, (c[0], c[1], c[2], 200))
    return g


def monolith():
    img = Img()
    shaft(img, 4, 28, 17, 15, wood=('2b2b33', '4a4a57', '7a7a8c'))
    ferrule(img, 17, 15)
    # obsidian slab standing on the staff, glowing edges, starfield inside
    x0, y0, x1, y1 = 18, 1, 28, 18
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = (y - y0) / (y1 - y0)
            c = mix(hexc('1a1030'), hexc('050309'), t)
            img.p(x, y, c)
    for (sx, sy) in ((21, 5), (25, 8), (22, 12), (26, 14), (24, 3)):
        img.p(sx, sy, hexc('cfd8ff'))
    for y in range(y0, y1 + 1):
        img.p(x0, y, hexc('9a7bff'))
        img.p(x1, y, hexc('4b3a99'))
    for x in range(x0, x1 + 1):
        img.p(x, y0, hexc('c9b8ff'))
        img.p(x, y1, hexc('3a2c77'))
    img.outline(hexc('0d0816'))
    for y in range(y0 + 1, y1):         # soft outer glow
        img.p(x0 - 2, y, hexc('9a7bff', 70))
        img.p(x1 + 2, y, hexc('9a7bff', 70))
    sparkle(img, 30, 2, 'c9b8ff')
    img.im.save(os.path.join(OUT, 'portal_wand_monolith.png'))


def glitch_select():
    img = Img()
    shaft(img, 4, 28, 20, 12, wood=('2a1830', '4b2c57', '7a4f8a'))
    for y in range(3, 13):
        for x in range(19, 29):
            c = hexc('ff2e8a') if (x * 7 + y * 3) % 5 else hexc('2ef2ff')
            img.p(x, y, c)
    for y in range(5, 11, 2):   # scanline tears
        for x in range(21, 31):
            img.p(x, y, hexc('ffffff', 120))
    img.outline(hexc('1b1020'))
    img.p(30, 4, hexc('2ef2ff'))
    img.p(17, 6, hexc('ff2e8a'))
    img.im.save(os.path.join(OUT, 'glitch_select.png'))


def medal(name, star=False):
    img = Img()
    c = (16, 16)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            if star:
                a = math.atan2(dy, dx) + math.pi / 2
                k = (a % (2 * math.pi / 5)) / (2 * math.pi / 5)
                rr = 6 + 8.5 * abs(1 - 2 * k) ** 1.6
                if d < rr:
                    lam = 0.6 - 0.04 * (dx + dy)
                    img.p(x, y, mix(hexc('c48a1a'), hexc('ffe27a'), lam))
            elif d < 14:
                lam = 0.55 - 0.035 * (dx + dy)
                img.p(x, y, mix(hexc('2a1263'), hexc('6a3bd1'), lam))
    if not star:
        q = ["..####..", ".##..##.", "......##", ".....##.", "....##..", "....##..", "........", "....##.."]
        for j, row in enumerate(q):
            for i, ch in enumerate(row):
                if ch == '#':
                    for a in range(2):
                        for b in range(2):
                            img.p(8 + i * 2 + a, 8 + j * 2 + b, hexc('f4f0ff'))
    img.outline(hexc('140a24'))
    img.im.save(os.path.join(OUT, name + '.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    wand('portal_wand', 'b54ef0', stripes('f0c6ff'), 'f3d9ff')
    wand('portal_wand_blackhole', '2a0f45', blackhole_glyph, 'ffcf8a', dark=True)
    monolith()
    wand('trigger_wand', 'ffa21f', bolt_glyph('fff4c2'), 'fff4c2')
    wand('zone_wand', '6e8fd8', stripes('cfe0ff'), 'cfe0ff')
    wand('zone_wand_danger', 'e0303a', stripes('ffd23f', 2, 2.0), 'ffd23f')
    wand('activator_wand', 'e3b521', ring_glyph('3fd17a'), 'b8ffd6')
    wand('fill_wand', '22bde0', fill_glyph('ffffff'), 'c8f6ff')
    wand('permit_wand', '2fb84a', check_glyph('eaffef'), 'd6ffe0')
    glitch_select()
    medal('hidden_quest')
    medal('hidden_quest_open', star=True)
    print('items written to', OUT)
