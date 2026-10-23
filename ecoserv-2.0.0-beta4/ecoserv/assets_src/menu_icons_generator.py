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


# ------------------------------------------------------------------------------------------------
# 1.8.0 (2): race, orders, admin/config categories, animated planet button.

def race():
    """Checkered flag waving (Quest race)."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 1, 2, 15, rgba('7a5232')); f.p(2, 0, rgba('f6c945'))
        for x in range(3, 15):
            k = x - 3
            off = round(1.3 * math.sin(k / 2.6 - i / N * 2 * math.pi))
            for y in range(2, 10):
                c = rgba('f4f4f4') if ((x // 2) + (y // 2)) % 2 == 0 else rgba('1d1d24')
                if k > 8 and (k + y + i) % 5 == 0:
                    c = mix(c, rgba('9aa4b5'), 0.4)
                f.p(x, y + off, c)
        f.outline(EDGE)
        sparkle(f, 13, 13, [0, 0, 0.5, 1, 0.5, 0, 0, 0][i])
        fr.append(f)
    strip('anim_race', fr)


def orders():
    """Clipboard; ticks appear line by line (buy orders)."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(3, 2, 12, 14, rgba('b07a43')); f.rect(4, 3, 11, 13, rgba('f3ead2'))
        f.rect(6, 1, 9, 3, rgba('9aa4b5')); f.rect(7, 1, 8, 1, rgba('d8dee9'))
        for k, y in enumerate((6, 9, 12)):
            f.line(7, y, 10, y, rgba('9a8a70'))
            if i >= 2 + k * 2:
                f.p(5, y, rgba('3fbf5a')); f.p(6, y - 1, rgba('3fbf5a'))
            else:
                f.p(5, y, rgba('cfc6ad'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_orders', fr)


def market():
    """Trade terminal: screen blinks, coin drops into slot."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 2, 13, 14, rgba('6b6f7a')); f.rect(2, 12, 13, 14, rgba('4a4e58'))
        f.rect(4, 4, 11, 8, rgba('0e2a1a'))
        bar = [3, 5, 7, 4, 6, 8, 5, 7][i]
        for x in range(5, 11):
            h = (x * 3 + i) % 4
            f.line(x, 8, x, 8 - h, rgba('3fdc6a'))
        f.rect(6, 10, 9, 10, rgba('1d1d24'))
        cy = [0, 2, 4, 6, 8, 10, -5, -5][i]
        if cy >= 0:
            f.ellipse(7.5, cy + 0.5, 1.6, 1.2, rgba('f6c945'), rgba('c9922a'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_market', fr)


def vending():
    """Storage chest whose lid opens with a glint."""
    fr = []
    for i in range(N):
        f = F()
        lid = [0, 1, 2, 3, 3, 2, 1, 0][i]
        f.rect(2, 7, 13, 14, rgba('a0682f')); f.rect(2, 7, 13, 8, rgba('7a4a1e'))
        f.rect(2, 3 - lid, 13, 6 - lid, rgba('b97a3a')); f.rect(2, 6 - lid, 13, 6 - lid, rgba('7a4a1e'))
        f.rect(7, 6, 8, 9, rgba('d8dee9'))
        if lid >= 2:
            f.rect(4, 7 - lid + 3, 11, 7, rgba('3a2410'))
            sparkle(f, 8, 4, 1.0 if lid == 3 else 0.5)
        f.outline(EDGE)
        fr.append(f)
    strip('anim_vending', fr)


def backpack():
    fr = []
    for i in range(N):
        f = F()
        dy = [0, 0, -1, -1, 0, 0, 0, 0][i]
        body = rgba('5f8f3a'); dark = rgba('3d6125')
        f.rect(3, 4 + dy, 12, 14 + dy, body); f.rect(3, 11 + dy, 12, 14 + dy, dark)
        f.rect(5, 2 + dy, 10, 4 + dy, dark)
        f.rect(5, 7 + dy, 10, 10 + dy, rgba('7fb84f')); f.rect(7, 8 + dy, 8, 8 + dy, rgba('f6c945'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_backpack', fr)


def ban():
    """Red prohibition sign pulsing over an item."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(5, 5, 10, 10, rgba('9aa4b5'))
        pulse = [0.0, 0.3, 0.6, 1.0, 0.6, 0.3, 0.0, 0.0][i]
        red = mix(rgba('b02828'), rgba('ff5050'), pulse)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if 5.6 <= d <= 7.4:
                    f.p(x, y, red)
        for t in range(-5, 6):
            f.p(8 + t * 0.7, 8 + t * 0.7, red); f.p(8 + t * 0.7 + 1, 8 + t * 0.7, red)
        fr.append(f)
    strip('anim_ban', fr)


def bomb():
    """Blacklist: bomb with a burning fuse."""
    fr = []
    for i in range(N):
        f = F()
        f.ellipse(7, 10, 5, 5, rgba('2a2d36'), rgba('15171d'), rgba('5d6676'))
        f.rect(9, 4, 10, 5, rgba('5d6676'))
        f.line(11, 3, 13, 1, rgba('c9a86a'))
        sp = [(13, 1), (12, 2), (13, 0), (14, 1), (12, 1), (13, 2), (14, 0), (12, 0)][i]
        f.p(sp[0], sp[1], rgba('ffd23f')); f.p(sp[0] + 1, sp[1], rgba('ff8a2a', 200))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_bomb', fr)


def pickaxe():
    fr = []
    for i in range(N):
        f = F()
        a = [-0.35, -0.2, 0.0, 0.25, 0.4, 0.2, 0.0, -0.2][i]
        ca, sa = math.cos(a), math.sin(a)

        def rp(x, y, c):
            dx, dy = x - 8, y - 10
            f.p(8 + dx * ca - dy * sa, 10 + dx * sa + dy * ca, c)
        for t in range(0, 11):
            rp(4 + t * 0.8, 14 - t, rgba('8a5a2a'))
        for x in range(2, 15):
            y = 3 + round(abs(x - 8) * 0.45)
            rp(x, y, rgba('9fd4ff')); rp(x, y + 1, rgba('5c9cf0'))
        if i == 4:
            sparkle(f, 13, 12, 1.0)
        f.outline(EDGE)
        fr.append(f)
    strip('anim_pickaxe', fr)


def wand():
    """Activator wand with orbiting sparks."""
    fr = []
    for i in range(N):
        f = F()
        for t in range(0, 11):
            f.p(3 + t, 13 - t, rgba('7a5232') if t < 8 else rgba('f6c945'))
        a = i / N * 2 * math.pi
        for k in range(3):
            b = a + k * 2 * math.pi / 3
            f.p(12 + math.cos(b) * 3, 4 + math.sin(b) * 3, [rgba('ff6ad5'), rgba('6ae0ff'), rgba('ffe066')][k])
        f.outline(EDGE)
        fr.append(f)
    strip('anim_wand', fr)


def cube():
    """Content blocks: rotating isometric cube."""
    fr = []
    for i in range(N):
        f = F()
        t = i / N * math.pi / 2
        pts = []
        for (x, y, z) in ((-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1), (-1, 1, -1), (1, 1, -1), (1, 1, 1), (-1, 1, 1)):
            rx = x * math.cos(t) - z * math.sin(t)
            rz = x * math.sin(t) + z * math.cos(t)
            pts.append((8 + rx * 4.2, 8 + y * 3.6 + rz * 1.6))
        faces = [(0, 1, 2, 3, rgba('ffd27a')), (3, 2, 6, 7, rgba('e09a3a')), (0, 3, 7, 4, rgba('b06a20')),
                 (1, 2, 6, 5, rgba('c97a28'))]
        # paint top + two side faces via scanline polygon fill
        def poly(idx, c):
            P = [pts[k] for k in idx]
            for y in range(16):
                for x in range(16):
                    inside = False
                    j = len(P) - 1
                    for k in range(len(P)):
                        xi, yi = P[k]; xj, yj = P[j]
                        if ((yi > y + 0.5) != (yj > y + 0.5)) and (x + 0.5 < (xj - xi) * (y + 0.5 - yi) / (yj - yi + 1e-9) + xi):
                            inside = not inside
                        j = k
                    if inside:
                        f.p(x, y, c)
        for a, b, c, d, col in sorted(faces[1:], key=lambda q: -(pts[q[0]][1] + pts[q[1]][1])):
            poly((a, b, c, d), col)
        poly(faces[0][:4], faces[0][4])
        f.outline(EDGE)
        fr.append(f)
    strip('anim_cube', fr)


def anvil():
    """Tools: hammer strikes an anvil."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 9, 13, 10, rgba('5d6676')); f.rect(5, 11, 10, 12, rgba('4a4e58')); f.rect(3, 13, 12, 14, rgba('5d6676'))
        f.rect(2, 9, 13, 9, rgba('9aa4b5'))
        up = [4, 3, 1, 0, 0, 2, 4, 5][i]
        hx = 9
        f.rect(hx, 1 + 0, hx, 7 - up + 0 if 7 - up > 1 else 2, rgba('8a5a2a'))
        f.rect(hx - 2, 7 - up - 1, hx + 2, 7 - up, rgba('9aa4b5'))
        if up == 0:
            sparkle(f, 6, 7, 1.0); sparkle(f, 13, 7, 0.5)
        f.outline(EDGE)
        fr.append(f)
    strip('anim_anvil', fr)


def disk():
    """Backups: disk with an arrow going in."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 3, 13, 14, rgba('3c74e0')); f.rect(4, 3, 11, 7, rgba('d8dee9')); f.rect(9, 4, 10, 6, rgba('3c74e0'))
        f.rect(4, 10, 11, 14, rgba('2a4fa0'))
        y = [-4, -3, -2, -1, 0, 1, 2, -5][i]
        for k in range(3):
            f.p(7, y + k + 9 - 6, rgba('7cf07a')); f.p(8, y + k + 9 - 6, rgba('7cf07a'))
        if -1 <= y <= 2:
            f.p(6, y + 6, rgba('7cf07a')); f.p(9, y + 6, rgba('7cf07a'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_disk', fr)


def power():
    """Restart: power symbol with a rotating arc."""
    fr = []
    for i in range(N):
        f = F()
        a0 = i / N * 2 * math.pi
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8.5
                d = math.hypot(dx, dy)
                ang = (math.atan2(dy, dx) + math.pi / 2) % (2 * math.pi)
                if 4.6 <= d <= 6.4 and not (ang < 0.55 or ang > 2 * math.pi - 0.55):
                    lit = ((math.atan2(dy, dx) - a0) % (2 * math.pi)) < 1.4
                    f.p(x, y, rgba('7cf07a') if lit else rgba('2e9e62'))
        f.rect(7, 1, 8, 8, rgba('7cf07a'))
        f.outline(rgba('0d2a18'))
        fr.append(f)
    strip('anim_power', fr)


def megaphone():
    fr = []
    for i in range(N):
        f = F()
        for x in range(3, 11):
            h = 1 + (x - 3) * 0.55
            for y in range(int(round(8 - h)), int(round(8 + h)) + 1):
                f.p(x, y, rgba('e04040') if x < 10 else rgba('f4f4f4'))
        f.rect(1, 7, 3, 9, rgba('5d6676'))
        f.outline(EDGE)
        for k in range(3):
            r = 2 + k * 2 + (i % 4) * 0.5
            if (i + k) % 4 != 3:
                for t in (-0.6, -0.3, 0.0, 0.3, 0.6):
                    f.p(10 + math.cos(t) * r, 8 + math.sin(t) * r, rgba('ffe066', 230 - k * 60))
        fr.append(f)
    strip('anim_megaphone', fr)


def terminal():
    """RCON / ComputerCraft: terminal screen with a typing prompt."""
    fr = []
    text = [[1, 1, 0, 1, 1, 1], [1, 0, 1, 1], [1, 1, 1, 0, 1]]
    for i in range(N):
        f = F()
        f.rect(1, 2, 14, 13, rgba('c9c3a8')); f.rect(2, 3, 13, 11, rgba('101418'))
        f.rect(5, 14, 10, 14, rgba('8a846d'))
        f.p(3, 4, rgba('3fdc6a')); f.p(4, 5, rgba('3fdc6a')); f.p(3, 6, rgba('3fdc6a'))
        shown = i
        y = 5
        for row in text:
            for k, on in enumerate(row):
                if shown <= 0:
                    break
                if on:
                    f.p(6 + k, y, rgba('d8f8e0'))
                shown -= 1
            y += 2
        if i % 2 == 0:
            f.rect(6 + min(5, i), 9, 6 + min(5, i), 10, rgba('3fdc6a'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_terminal', fr)


def reload_():
    fr = []
    for i in range(N):
        f = F()
        a0 = i / N * 2 * math.pi
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                d = math.hypot(dx, dy)
                ang = (math.atan2(dy, dx) - a0) % (2 * math.pi)
                if 4.2 <= d <= 6.0 and (ang < 2.6 or 3.14 < ang < 5.74):
                    f.p(x, y, rgba('4fa8ff') if ang < 3 else rgba('7cf07a'))
        for base, col in ((a0 + 2.6, rgba('4fa8ff')), (a0 + 5.74, rgba('7cf07a'))):
            hx, hy = 8 + math.cos(base) * 5.1, 8 + math.sin(base) * 5.1
            for k in range(-2, 3):
                f.p(hx + math.cos(base + math.pi / 2) * 0 + math.cos(base) * k * 0.5, hy + math.sin(base) * k * 0.5, col)
            f.p(hx + math.cos(base + 1.2) * 1.5, hy + math.sin(base + 1.2) * 1.5, col)
        f.outline(rgba('0c1a2a'))
        fr.append(f)
    strip('anim_reload', fr)


def save_():
    """Save data: floppy with a blinking light."""
    fr = []
    for i in range(N):
        f = F()
        f.rect(2, 2, 13, 13, rgba('5b3a9a')); f.rect(4, 2, 11, 6, rgba('d8dee9')); f.rect(9, 3, 10, 5, rgba('5b3a9a'))
        f.rect(4, 9, 11, 13, rgba('f3ead2'))
        for y in (10, 12):
            f.line(5, y, 10, y, rgba('9a8a70'))
        f.p(12, 12, rgba('7cf07a') if i % 2 == 0 else rgba('2e5e3a'))
        f.outline(EDGE)
        fr.append(f)
    strip('anim_save', fr)


# Animated planet button for the inventory (replaces the static planet_button.png): 2 columns × 16 frames, 32×32.
PB = 32


def planet_button():
    im = Image.new('RGBA', (PB * 2, PB * BN))
    for i in range(BN):
        for col, hover in ((0, False), (1, True)):
            f = F(PB)
            globe(f, 16, 16, 13.2, i / BN)
            halo(f, rgba('ffd23f', 230) if hover else rgba('bfe6ff', 150), 2.4)
            im.paste(f.im, (col * PB, i * PB))
    im.save(os.path.join(GUI, 'planet_button.png'))


if __name__ == '__main__':
    os.makedirs(ICONS, exist_ok=True)
    planet(); team(); create(); compass(); admin()
    book('anim_book', rgba('8b2f2f'), rgba('5a1d1d'))
    book('anim_quest_book', rgba('5b3a9a'), rgba('35215e'), rgba('e6c8ff'))
    key(); map_(); feather(); sky(); glitch(); gear(); cake()
    button_sheet('coin_button', coin_button)
    button_sheet('trade_button', trade_button)
    race(); orders(); market(); vending(); backpack(); ban(); bomb(); pickaxe(); wand(); cube(); anvil()
    disk(); power(); megaphone(); terminal(); reload_(); save_()
    planet_button()
    print('ok')
