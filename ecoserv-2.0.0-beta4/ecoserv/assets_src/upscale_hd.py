# EcoServ 1.9.0: Scale2x (EPX) upscaling of the 16px pixel-art (animated icons, block faces) to 32px,
# keeps hard pixel-art edges but smooths diagonals and curves.
# python3 upscale_hd.py <png> [<png> ...]   (in place; skips images that are already 32px wide)
import sys
from PIL import Image

def scale2x(im):
    w, h = im.size
    src = im.load()
    out = Image.new('RGBA', (w * 2, h * 2))
    dst = out.load()
    def g(x, y):
        return src[min(max(x, 0), w - 1), min(max(y, 0), h - 1)]
    for y in range(h):
        for x in range(w):
            P = src[x, y]; A = g(x, y - 1); B = g(x + 1, y); C = g(x - 1, y); D = g(x, y + 1)
            e0 = e1 = e2 = e3 = P
            if C == A and C != D and A != B: e0 = A
            if A == B and A != C and B != D: e1 = B
            if D == C and D != B and C != A: e2 = C
            if B == D and B != A and D != C: e3 = D
            dst[2 * x, 2 * y] = e0; dst[2 * x + 1, 2 * y] = e1
            dst[2 * x, 2 * y + 1] = e2; dst[2 * x + 1, 2 * y + 1] = e3
    return out

for f in sys.argv[1:]:
    im = Image.open(f).convert('RGBA')
    if im.size[0] != 16:
        continue
    # animated strips: scale each 16x16 frame separately so frames don't bleed into each other
    w, h = im.size
    out = Image.new('RGBA', (w * 2, h * 2))
    for fy in range(0, h, 16):
        out.paste(scale2x(im.crop((0, fy, 16, fy + 16))), (0, fy * 2))
    out.save(f)
    print('hd', f)
