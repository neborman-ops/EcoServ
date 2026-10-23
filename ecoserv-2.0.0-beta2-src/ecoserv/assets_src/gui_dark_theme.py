# EcoServ 1.8: перекраска окон блоков (терминал, склад, торговец, обмен) в тёмную тему серверных окон.
# Геометрия (слоты, поля) не меняется — только цвета. Usage: python3 gui_dark_theme.py <textures/gui>
import os, sys
from PIL import Image

MAP = {
    (0xc6, 0xc6, 0xc6): (0x1b, 0x1e, 0x26),  # фон окна
    (0xa0, 0xa0, 0xa0): (0x25, 0x29, 0x35),  # панель
    (0x8b, 0x8b, 0x8b): (0x0c, 0x0e, 0x12),  # слот
    (0xff, 0xff, 0xff): (0x3a, 0x3f, 0x4c),  # светлая кромка
    (0x37, 0x37, 0x37): (0x07, 0x08, 0x0b),  # тёмная кромка слота
    (0x55, 0x55, 0x55): (0x10, 0x12, 0x17),  # тень окна
}


def lum_map(r, g, b):
    t = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
    lo, hi = (0x0c, 0x0e, 0x12), (0x3a, 0x3f, 0x4c)
    return tuple(int(lo[i] + (hi[i] - lo[i]) * t) for i in range(3))


root = sys.argv[1]
for name in ('market', 'vending', 'vendor', 'trade'):
    path = os.path.join(root, name + '.png')
    im = Image.open(path).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if (r, g, b) in MAP:
                px[x, y] = MAP[(r, g, b)] + (a,)
            elif abs(r - g) < 8 and abs(g - b) < 8:
                px[x, y] = lum_map(r, g, b) + (a,)
    # синяя полоска сверху, как у заголовка серверных окон
    for x in range(4, im.width):
        if px[x, 3][3] > 0 and px[x, 1][3] > 0:
            px[x, 1] = (0x4e, 0x8c, 0xff, 255)
    im.save(path)
print('ok')
