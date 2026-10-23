# EcoServ 1.8: animated icons for the server-driven menus (worlds, subworld, regions, roles, dates, config)
# and the round inventory buttons (coin = shop, orb with arrows = trade) drawn in the style of the planet button.
# Usage: python3 menu_icons_generator.py <assets/ecoserv/textures>
import math, os, sys
from PIL import Image

ROOT = sys.argv[1]
ICONS = os.path.join(ROOT, 'icons')
GUI = os.path.join(ROOT, 'gui')
N = 8


def rgba(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


class F:
    def __init__(s, size=16):
        s.S = size
        s.im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        s.px = s.im.load()

    def p(s, x, y, c):
        x = int(round(x)); y = int(round(y))
        if 0 <= x < s.S and 0 <= y < s.S:
            s.px[x, y] = c

    def blend(s, x, y, c):
        x = int(round(x)); y = int(round(y))
        if 0 <= x < s.S and 0 <= y < s.S:
            o = s.px[x, y]
            a = c[3] / 255.0
            if o[3] == 0:
                s.px[x, y] = c
            else:
                s.px[x, y] = (int(o[0] + (c[0] - o[0]) * a), int(o[1] + (c[1] - o[1]) * a),
                              int(o[2] + (c[2] - o[2]) * a), max(o[3], c[3]))

    def get(s, x, y):
        return s.px[x, y] if 0 <= x < s.S and 0 <= y < s.S else (0, 0, 0, 0)

    def rect(s, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                s.p(x, y, c)

    def ellipse(s, cx, cy, rx, ry, fill, shade=None, light=None):
        for y in range(s.S):
            for x in range(s.S):
                dx = (x + 0.5 - cx) / max(rx, 0.01); dy = (y + 0.5 - cy) / max(ry, 0.01)
                if dx * dx + dy * dy <= 1.0:
                    c = fill
                    if shade and (dx * 0.6 + dy * 0.8) > 0.45:
                        c = shade
                    if light and (dx * 0.6 + dy * 0.8) < -0.55:
                        c = light
                    s.p(x, y, c)

    def outline(s, edge):
        pts = []
        for y in range(s.S):
            for x in range(s.S):
                if s.px[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if s.get(x + dx, y + dy)[3] > 0:
                            pts.append((x, y)); break
        for x, y in pts:
            s.p(x, y, edge)

    def line(s, x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(n, 1)
            s.p(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c)


def strip(name, frames):
    S = frames[0].S
    im = Image.new('RGBA', (S, S * len(frames)))
    for i, f in enumerate(frames):
        im.paste(f.im, (0, i * S))
    im.save(os.path.join(ICONS, name + '.png'))


def sparkle(f, x, y, phase, col=rgba('fff7c2')):
    if phase <= 0:
        return
    f.p(x, y, col)
    if phase > 0.4:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            f.p(x + dx, y + dy, rgba('ffe26b', 200))
    if phase > 0.8:
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            f.p(x + dx, y + dy, rgba('ffd23f', 120))


EDGE = rgba('2a1d12')

# ------------------------------------------------------------------------------------------------
# Planet (worlds): globe with continents drifting to the right, like the planet button.
OCEAN = rgba('2f6fd0'); OCEAN_D = rgba('1d4596'); OCEAN_L = rgba('5c9cf0')
LAND = rgba('4f9a3a'); LAND_D = rgba('356f27'); SAND = rgba('c9a86a'); CLOUD = rgba('eef6ff')


def land_at(u, v):
    # u: longitude 0..1 (wraps), v: latitude -1..1 -> 0 ocean, 1 land, 2 sand
    blobs = [(0.12, -0.35, 0.13, 0.30), (0.20, 0.30, 0.10, 0.35), (0.55, -0.10, 0.16, 0.45),
             (0.78, 0.45, 0.08, 0.18), (0.88, -0.55, 0.07, 0.15)]
    best = 0
    for bu, bv, ru, rv in blobs:
        du = min(abs(u - bu), 1 - abs(u - bu)) / ru
        dv = (v - bv) / rv
        d = du * du + dv * dv
        if d < 0.55:
            return 2 if (int(u * 40 + v * 13) % 5 == 0) else 1
        if d < 1.0:
            best = max(best, 1)
    return best


def globe(f, cx, cy, r, turn, clouds=True):
    for y in range(f.S):
        for x in range(f.S):
            dx = (x + 0.5 - cx) / r; dy = (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1.0:
                continue
            z = math.sqrt(1 - d2)
            lon = (math.atan2(dx, z) / math.pi * 0.5 + turn) % 1.0
            kind = land_at(lon, dy)
            light = 0.55 - 0.45 * dx - 0.35 * dy + 0.25 * z
            if kind == 0:
                c = OCEAN_L if light > 0.95 else (OCEAN if light > 0.35 else OCEAN_D)
            elif kind == 2:
                c = SAND
            else:
                c = LAND if light > 0.35 else LAND_D
            if clouds:
                cl = (lon * 3.0 + dy * 0.7 + turn * 0.5) % 1.0
                if abs(dy - 0.15) < 0.09 and cl < 0.28 or abs(dy + 0.5) < 0.07 and cl > 0.62:
                    c = CLOUD
            f.p(x, y, c)


def planet():
    fr = []
    for i in range(N):
        f = F()
        globe(f, 8, 8, 6.6, i / N)
        f.outline(rgba('9fd4ff', 150))
        fr.append(f)
    strip('anim_planet', fr)


# Team: banner waving on a pole
def team():
    fr = []
    for i in range(N):
        f = F()
        pole = rgba('7a5232'); cloth = rgba('d04040'); cloth_d = rgba('8c2626'); emb = rgba('f6c945')
        f.rect(3, 1, 3, 15, pole); f.p(3, 0, rgba('f6c945'))
        for x in range(4, 14):
            k = x - 4
            off = round(1.2 * math.sin(k / 3.0 - i / N * 2 * math.pi))
            for y in range(2, 11):
                c = cloth if (k + y) % 7 else cloth_d
                if 6 <= y <= 7 and 7 <= x <= 9:
                    c = emb
                f.p(x, y + off, c)
        f.outline(EDGE)
        fr.append(f)
    strip('anim_team', fr)


# Create: a small new world with a sparkling "+"
def create():
    fr = []
    for i in range(N):
        f = F()
        globe(f, 7, 9, 5.2, i / N * 0.5, clouds=False)
        f.outline(rgba('9fd4ff', 140))
        g = rgba('7cf07a'); gd = rgba('2f8f3a')
        f.rect(11, 1, 13, 7, gd); f.rect(9, 3, 15, 5, gd)
        f.rect(12, 1, 12, 7, g); f.rect(9, 4, 15, 4, g)
        sparkle(f, 3, 2, [0, 0.5, 1, 0.5, 0, 0, 0, 0][i])
        sparkle(f, 14, 12, [0, 0, 0, 0, 0.5, 1, 0.5, 0][i])
        fr.append(f)
    strip('anim_create', fr)


# Compass: needle swings and settles
def compass():
    fr = []
    ang = [-0.9, -0.3, 0.35, 0.15, -0.12, 0.06, 0.0, 0.0]
    for i in range(N):
        f = F()
        f.ellipse(8, 8, 7, 7, rgba('c9922a'), rgba('8a5d1c'), rgba('f6d77a'))
        f.ellipse(8, 8, 5.2, 5.2, rgba('e9e2cf'), rgba('cfc6ad'))
        for k in range(4):
            a = k * math.pi / 2
            f.p(8 + math.sin(a) * 4.4, 8 - math.cos(a) * 4.4, rgba('7a6a50'))
        a = ang[i]
        for t in range(0, 5):
            f.p(8 + math.sin(a) * t, 8 - math.cos(a) * t, rgba('d63a3a'))
            f.p(8 - math.sin(a) * t, 8 + math.cos(a) * t, rgba('3a4f8f'))
        f.p(8, 8, rgba('2a2a2a'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_compass', fr)


# Admin: command block with a pulsing core
def admin():
    fr = []
    for i in range(N):
        f = F()
        body = rgba('c98a5a'); body_d = rgba('8f5a35'); grid = rgba('6b4226')
        f.rect(2, 2, 13, 13, body)
        f.rect(2, 11, 13, 13, body_d); f.rect(11, 2, 13, 13, body_d)
        for k in range(2, 14, 4):
            f.line(k, 2, k, 13, grid); f.line(2, k, 13, k, grid)
        pulse = [0.2, 0.5, 0.9, 1.0, 0.8, 0.5, 0.3, 0.2][i]
        core = mix(rgba('3a2a5a'), rgba('b98cff'), pulse)
        f.rect(5, 5, 10, 10, rgba('231a33'))
        f.rect(6, 6, 9, 9, core)
        if pulse > 0.85:
            f.p(7, 7, rgba('ffffff'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_admin', fr)


# Book: pages flip
def book(name, cover, cover_d, glow=None):
    fr = []
    for i in range(N):
        f = F()
        paper = rgba('f3ead2'); paper_d = rgba('d9cba7')
        f.rect(1, 4, 7, 13, cover); f.rect(8, 4, 14, 13, cover)
        f.rect(2, 3, 7, 12, paper); f.rect(8, 3, 13, 12, paper)
        f.line(7, 3, 7, 13, paper_d); f.line(8, 3, 8, 13, cover_d)
        for y in (5, 7, 9):
            f.line(3, y, 6, y, rgba('9a8a70')); f.line(9, y, 12, y, rgba('9a8a70'))
        # flipping page: from right to left
        t = i / (N - 1)
        w = math.cos(t * math.pi) * 5.5
        if abs(w) > 0.6:
            x0 = 8 if w > 0 else 7
            x1 = 8 + w
            for y in range(3, 12):
                f.line(x0, y - abs(w) * 0.15, x1, y - abs(w) * 0.3, paper if w > 0 else paper_d)
        if glow:
            sparkle(f, 12, 1, [0, 0.5, 1, 0.5, 0, 0, 0, 0][i], glow)
            sparkle(f, 3, 1, [0, 0, 0, 0, 0.5, 1, 0.5, 0][i], glow)
        f.outline(EDGE)
        fr.append(f)
    strip(name, fr)


# Key: glint runs along the key
def key():
    fr = []
    for i in range(N):
        f = F()
        g = rgba('f6c945'); gd = rgba('b5832a')
        f.ellipse(5, 6, 3.6, 3.6, g, gd)
        f.ellipse(5, 6, 1.4, 1.4, (0, 0, 0, 0))
        for x in range(7, 15):
            f.p(x, x - 1, g); f.p(x, x, gd)
        f.p(11, 12, g); f.p(12, 13, g); f.p(13, 11, g); f.p(14, 12, g)
        f.outline(EDGE)
        pos = [2, 4, 6, 8, 10, 12, -1, -1][i]
        if pos >= 0:
            f.p(pos, pos - 1 if pos > 6 else 4, rgba('fffbe0'))
            f.p(pos + 1, pos if pos > 6 else 5, rgba('fff1a8'))
        fr.append(f)
    strip('anim_key', fr)


# Map: folded map with a blinking marker walking a dotted path
def map_():
    fr = []
    path = [(4, 11), (6, 10), (7, 8), (9, 7), (10, 5), (12, 4), (12, 4), (12, 4)]
    for i in range(N):
        f = F()
        p1 = rgba('efe3bb'); p2 = rgba('d9c995')
        for x in range(1, 15):
            c = p1 if (x // 5) % 2 == 0 else p2
            for y in range(2, 14):
                f.p(x, y, c)
        f.p(5, 6, rgba('6fae5a')); f.p(4, 6, rgba('6fae5a')); f.p(10, 11, rgba('5c9cf0')); f.p(11, 11, rgba('5c9cf0'))
        for k in range(0, i + 1):
            x, y = path[min(k, len(path) - 1)]
            f.p(x, y, rgba('9a3a3a'))
        x, y = path[i]
        f.p(x, y - 1, rgba('e04040')); f.p(x, y - 2, rgba('ff7070'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_map', fr)


# Feather: sways
def feather():
    fr = []
    for i in range(N):
        f = F()
        a = 0.35 * math.sin(i / N * 2 * math.pi)
        vane = rgba('f4f1ff'); vane_d = rgba('b9b3d6'); quill = rgba('7a6a50')
        for t in range(0, 14):
            bend = a * (t / 13.0) ** 1.5 * 6
            cx = 3 + t * 0.7 + bend
            cy = 14 - t
            f.p(cx, cy, quill if t < 4 else vane_d)
            if t >= 3:
                wdt = 2.5 * math.sin((t - 3) / 11 * math.pi)
                for k in range(1, int(wdt) + 2):
                    f.p(cx - k * 0.7, cy - k * 0.7, vane)
                    f.p(cx + k * 0.7, cy + k * 0.7, vane if k < 2 else vane_d)
        f.outline(rgba('3b2a59'))
        fr.append(f)
    strip('anim_feather', fr)


# Sky: sun behind a drifting cloud
def sky():
    fr = []
    for i in range(N):
        f = F()
        f.rect(0, 0, 15, 15, rgba('6fb8ff'))
        for y in range(16):
            for x in range(16):
                if y > 9:
                    f.p(x, y, rgba('9fd0ff'))
        f.ellipse(10, 5.5, 3.2, 3.2, rgba('ffe066'), rgba('f6b93b'))
        for k in range(8):
            a = k * math.pi / 4 + i * 0.1
            if (k + i) % 2 == 0:
                f.p(10 + math.cos(a) * 4.6, 5.5 + math.sin(a) * 4.6, rgba('fff3a0'))
        cx = -3 + i * 1.6
        for dx, dy, r in ((0, 0, 2.2), (2.2, -1, 2.6), (4.4, 0, 2.0)):
            for y in range(16):
                for x in range(16):
                    if (x + 0.5 - (cx + dx)) ** 2 + ((y + 0.5 - (10 + dy)) * 1.3) ** 2 <= r * r:
                        f.p(x, y, rgba('ffffff') if y < 10 + dy else rgba('dfe9f5'))
        # rounded tile
        for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
            f.p(x, y, (0, 0, 0, 0))
        fr.append(f)
    strip('anim_sky', fr)


# Glitch: dark void tile with shifting RGB slices
def glitch():
    import random
    fr = []
    for i in range(N):
        rnd = random.Random(1000 + i)
        f = F()
        for y in range(1, 15):
            for x in range(1, 15):
                v = rnd.random()
                f.p(x, y, rgba('0b0814') if v > 0.12 else rgba('1d1430'))
        for _ in range(2 + i % 3):
            y = rnd.randint(2, 13); sh = rnd.randint(-3, 3); w = rnd.randint(4, 10); x0 = rnd.randint(1, 14 - w)
            for x in range(x0, x0 + w):
                f.p(x + sh, y, rgba('ff2bd6', 220)); f.p(x - sh, y + 1 if y < 14 else y, rgba('26f0ff', 200))
        eye = [1, 1, 1, 0, 1, 1, 0, 1][i]
        if eye:
            f.rect(6, 7, 9, 8, rgba('b14bff')); f.p(7, 7, rgba('ffffff'))
        f.outline(rgba('7a3cff'))
        fr.append(f)
    strip('anim_glitch', fr)


# Gear (mods/settings): rotating cog
def gear():
    fr = []
    for i in range(N):
        f = F()
        rot = i / N * (math.pi / 4)
        m = rgba('9aa4b5'); md = rgba('5d6676'); ml = rgba('d8dee9')
        for y in range(16):
            for x in range(16):
                dx = x + 0.5 - 8; dy = y + 0.5 - 8
                r = math.hypot(dx, dy); a = math.atan2(dy, dx) - rot
                tooth = (math.cos(a * 8) > 0.35)
                if r <= 4.8 or (tooth and r <= 6.9):
                    c = m
                    if dx * 0.6 + dy * 0.8 > 2.5:
                        c = md
                    if dx * 0.6 + dy * 0.8 < -3:
                        c = ml
                    if r < 2.0:
                        c = (0, 0, 0, 0)
                    f.p(x, y, c)
        f.outline(rgba('2a2f3a'))
        fr.append(f)
    strip('anim_gear', fr)


# Cake with a flickering candle
def cake():
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 9, 13, 14, rgba('f3e2c0')); f.rect(2, 12, 13, 14, rgba('b8763f'))
        f.rect(2, 8, 13, 9, rgba('ffffff'))
        for x in range(2, 14, 3):
            f.p(x, 10, rgba('e04060'))
        f.rect(7, 4, 8, 7, rgba('6fb8ff'))
        fl = [0, 1, 0, -1, 0, 1, 1, 0][i]
        f.p(7 + (fl > 0), 3, rgba('ffd23f')); f.p(7 + (fl > 0), 2, rgba('ff8a2a'))
        if fl != 0:
            f.p(8 if fl > 0 else 7, 1, rgba('ffe066', 180))
        f.outline(EDGE)
        sparkle(f, 13, 3, [0, 0, 0.5, 1, 0.5, 0, 0, 0][i])
        fr.append(f)
    strip('anim_cake', fr)


# ------------------------------------------------------------------------------------------------
# Round inventory buttons in the planet style: no grey square, a shaded round object with a soft
# halo; column 0 normal, column 1 hovered (golden halo). 12 frames, 26x26.
B = 26
BN = 16


def halo(f, col, width=2.0):
    """Soft glow that follows the outline of whatever is drawn (like the planet button)."""
    solid = [(x, y) for y in range(f.S) for x in range(f.S) if f.get(x, y)[3] > 200]
    add = {}
    for y in range(f.S):
        for x in range(f.S):
            if f.get(x, y)[3] != 0:
                continue
            best = 9.0
            for sx, sy in solid:
                if abs(sx - x) <= 2 and abs(sy - y) <= 2:
                    best = min(best, math.hypot(sx - x, sy - y))
            if best <= width:
                add[(x, y)] = int(col[3] * (1 - (best - 1) / width))
    for (x, y), a in add.items():
        f.p(x, y, (col[0], col[1], col[2], max(0, min(255, a))))


def button_sheet(name, draw):
    im = Image.new('RGBA', (B * 2, B * BN))
    for i in range(BN):
        for col, hover in ((0, False), (1, True)):
            f = F(B)
            draw(f, i, hover)
            im.paste(f.im, (col * B, i * B))
    im.save(os.path.join(GUI, name + '.png'))


def coin_button(f, i, hover):
    half = BN // 2
    a = 0.0 if i < half else (i - half) / half * 2 * math.pi
    c = math.cos(a)
    w = max(1.3, abs(c) * 11.0)
    cx, cy, ry = 13, 13, 11.0
    face = abs(c) > 0.25
    g = rgba('f6c945'); gd = rgba('c9922a'); gl = rgba('fff1a8'); ge = rgba('8a5a1a'); rim = rgba('e0a93a')
    # edge thickness (visible while turning)
    th = 2.2 * math.sin(abs(a % math.pi))
    for y in range(B):
        for x in range(B):
            dy = (y + 0.5 - cy) / ry
            if abs(dy) > 1:
                continue
            half = w * math.sqrt(1 - dy * dy)
            ex = x + 0.5 - cx
            if -half - th <= ex <= half + th and not (-half <= ex - (th if c < 0 else -th) * 0 <= half):
                f.p(x, y, ge)
    for y in range(B):
        for x in range(B):
            dx = (x + 0.5 - cx) / w; dy = (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1.0:
                l = -0.6 * dx * (1 if c > 0 else -1) - 0.8 * dy
                col = g
                if d > 0.84:
                    col = ge
                elif d > 0.66:
                    col = rim if l > -0.2 else gd
                elif l > 0.55:
                    col = gl
                elif l < -0.45:
                    col = gd
                f.p(x, y, col)
    if face:
        # stamped "E" (EcoServ) scaled with the turn
        s = abs(c)
        for yy in range(-5, 6):
            for xx in (-2, -1, 0, 1, 2, 3):
                inside = (xx == -2 or xx == -1) or (yy in (-5, -4, 0, 4, 5) and xx <= (3 if yy != 0 else 2))
                if inside:
                    px = cx + xx * s * (1 if c > 0 else -1)
                    f.p(px, cy + yy, gd)
                    f.p(px + (0.6 if c > 0 else -0.6), cy + yy + 1, gl if s > 0.6 else gd)
    tw = {2: 0.5, 3: 1.0, 4: 0.5}.get(i, 0)
    if tw:
        sparkle(f, 18, 6, tw)
    halo(f, rgba('ffd23f', 230) if hover else rgba('fff1c0', 110))


def trade_button(f, i, hover):
    cx = cy = 13; r = 11.2
    for y in range(B):
        for x in range(B):
            dx = (x + 0.5 - cx) / r; dy = (y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            z = math.sqrt(1 - d2)
            light = 0.5 - 0.45 * dx - 0.45 * dy + 0.3 * z
            c = mix(rgba('103a52'), rgba('3fa0c8'), max(0.0, min(1.0, light)))
            if d2 > 0.80:
                c = mix(c, rgba('06141f'), 0.65)
            f.p(x, y, c)
    # glass highlight
    for x, y in ((8, 6), (9, 5), (10, 5), (7, 7), (11, 4), (12, 4)):
        f.blend(x, y, rgba('ffffff', 150))
    o = 2.0 * math.sin(i / BN * 2 * math.pi)
    blue = rgba('5fb4ff'); blue_d = rgba('1f5fa8'); green = rgba('6ff08a'); green_d = rgba('1f7a36')

    def arrow(y, xa, xb, right, c, cd):
        # shaft 2px + shadow, head 4px
        for x in range(int(round(xa)), int(round(xb)) + 1):
            f.p(x, y, c); f.p(x, y + 1, c); f.p(x, y + 2, cd)
        tip = xb + 2 if right else xa - 2
        for d in range(0, 5):
            tx = tip - d if right else tip + d
            for yy in range(y - d, y + 2 + d):
                f.p(tx, yy, c)
            f.p(tx, y + 2 + d, cd)

    arrow(8, 6 + o, 16 + o, True, blue, blue_d)
    arrow(15, 10 - o, 20 - o, False, green, green_d)
    halo(f, rgba('ffd23f', 230) if hover else rgba('9fd4ff', 120))


if __name__ == '__main__':
    os.makedirs(ICONS, exist_ok=True)
    planet(); team(); create(); compass(); admin()
    book('anim_book', rgba('8b2f2f'), rgba('5a1d1d'))
    book('anim_quest_book', rgba('5b3a9a'), rgba('35215e'), rgba('e6c8ff'))
    key(); map_(); feather(); sky(); glitch(); gear(); cake()
    button_sheet('coin_button', coin_button)
    button_sheet('trade_button', trade_button)
    print('ok')
