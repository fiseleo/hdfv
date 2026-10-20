"""hdfv 0.13: the paint booth's item icons (16 px, the style of the other material icons: flat colours, the right and
lower edges a shade darker). Needs Pillow; writes into neoforge's item textures only when the image changes.

    py -3 tools/items16.py
"""
import io
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'neoforge', 'src', 'main', 'resources', 'assets', 'hdfv', 'textures', 'item')


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def paint_can():
    """A one-litre tin: a grey body, the lid's rim, a coloured band round it with a drip, a wire handle."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    body, band, rim, handle = hexrgb('#a8aeb4'), hexrgb('#b3161c'), hexrgb('#6b7077'), hexrgb('#3b3f45')
    for y in range(4, 15):
        for x in range(3, 13):
            c = band if 7 <= y <= 11 else body
            if x >= 11 or y == 14:
                c = shade(c, 0.78)
            px[x, y] = c
    for x in range(3, 13):
        px[x, 3] = rim
        px[x, 4] = shade(rim, 1.15)
    for x in range(5, 11):
        px[x, 1] = handle
    px[4, 2] = handle
    px[11, 2] = handle
    px[6, 12] = band
    px[6, 13] = shade(band, 0.8)
    return im


def sticker_film():
    """A roll of vinyl film: the roll on the left, a sheet pulled out to the right, a printed check on it, its corner peeling."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    sheet, blue, roll, core = hexrgb('#f2f2f2'), hexrgb('#2a4fa8'), hexrgb('#d6d8da'), hexrgb('#6b7077')
    for y in range(4, 14):
        for x in range(5, 15):
            c = blue if ((x // 2) + (y // 2)) % 2 == 0 else sheet
            if x == 14 or y == 13:
                c = shade(c, 0.8)
            px[x, y] = c
    for y in range(3, 15):
        for x in range(1, 6):
            px[x, y] = shade(roll, 0.85) if x in (1, 5) else roll
    for y in (3, 14):
        for x in range(2, 5):
            px[x, y] = core
    px[13, 4] = shade(sheet, 0.7)
    px[14, 4] = (0, 0, 0, 0)
    px[14, 5] = shade(sheet, 0.7)
    return im


def lamp_unit():
    """0.16: a vehicle lamp: a round chrome rim round a warm clear lens with its reflector's ring, a plug behind."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rim, lens, ring, plug = hexrgb('#cfd3d8'), hexrgb('#fff1c8'), hexrgb('#e2c27a'), hexrgb('#2a2c30')
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.5:
                c = rim if d > 5.2 else ring if 3.4 < d <= 4.2 else lens
                if x + y > 17 and d > 5.2:
                    c = shade(c, 0.75)
                px[x, y] = c
    for x in range(12, 16):
        px[x, 12] = plug
        px[x, 13] = shade(plug, 0.8)
    px[6, 5] = (255, 255, 255, 255)
    px[5, 6] = (255, 255, 255, 255)
    return im


def write_if_changed(path, im):
    data = io.BytesIO()
    im.save(data, 'PNG')
    blob = data.getvalue()
    if os.path.exists(path) and open(path, 'rb').read() == blob:
        return 0
    with open(path, 'wb') as f:
        f.write(blob)
    return 1


if __name__ == '__main__':
    written = sum(write_if_changed(os.path.join(OUT, n + '.png'), f()) for n, f in (('paint_can', paint_can), ('sticker_film', sticker_film), ('lamp_unit', lamp_unit)))
    print('items16:', written, 'written')
