"""hdfv 0.14: a car's dashboard textures: the round dial face (128 px, drawn at 64 GUI px; the scale, numbers and needle
are drawn in the game, so one face serves every engine and speed) and the warning lamps' icons (12 px each, drawn 1:1,
white on transparent: the game tints them). Needs Pillow; supersampled 8 times; writes only when an image changes.

    py -3 tools/hud.py
"""
import io
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'neoforge', 'src', 'main', 'resources', 'assets', 'hdfv', 'textures', 'gui', 'hud')
SS = 8


def save(im, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    buf = io.BytesIO()
    im.save(buf, 'PNG', optimize=True)
    data = buf.getvalue()
    if os.path.exists(path):
        with open(path, 'rb') as f:
            if f.read() == data:
                print('unchanged', name)
                return
    with open(path, 'wb') as f:
        f.write(data)
    print('wrote', name)


def dial():
    """A black face going to charcoal at its rim, a satin chrome bezel lit from above, a thin shadow inside the bezel."""
    n = 128 * SS
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    px = im.load()
    c = n / 2
    outer, bezel = 0.5 * n - SS, 0.5 * n - 7 * SS
    for y in range(n):
        for x in range(n):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            if r > outer:
                continue
            if r > bezel:
                # chrome: brightness by the angle to the light above, a highlight band and a dark edge
                t = (r - bezel) / (outer - bezel)
                up = -dy / max(r, 1e-6)
                v = 120 + 70 * up + 50 * math.sin(math.pi * t) - (60 if t > 0.85 else 0)
                v = max(30, min(235, v))
                px[x, y] = (int(v), int(v), int(v * 1.03), 255)
            else:
                t = r / bezel
                v = 10 + 26 * t ** 2
                if t > 0.95:
                    v *= 0.55   # the shadow under the bezel
                px[x, y] = (int(v), int(v * 1.02), int(v * 1.08), 255)
    return im.resize((128, 128), Image.LANCZOS)


ICON = 12


def icon(draw_fn):
    # drawn on a 16-unit grid, kept to 12 px (the dashboard draws them 1:1)
    n = 16 * SS
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), SS)
    return im.resize((ICON, ICON), Image.LANCZOS)


W = (255, 255, 255, 255)


def handbrake(d, s):
    """(( P )): a circle with an arc either side (the letter is the game's)."""
    d.ellipse([3.5 * s, 2.5 * s, 12.5 * s, 11.5 * s + s], outline=W, width=int(1.3 * s))
    d.arc([0.5 * s, 1.5 * s, 7 * s, 13.5 * s], 120, 240, fill=W, width=int(1.2 * s))
    d.arc([9 * s, 1.5 * s, 15.5 * s, 13.5 * s], -60, 60, fill=W, width=int(1.2 * s))


def fuel(d, s):
    """A fuel pump: its body with a window, the hose to a nozzle at the right."""
    d.rectangle([3 * s, 3 * s, 9 * s, 14 * s], fill=W)
    d.rectangle([4.2 * s, 4.2 * s, 7.8 * s, 6.8 * s], fill=(0, 0, 0, 0))
    d.rectangle([2 * s, 13 * s, 10 * s, 14.5 * s], fill=W)
    d.line([9 * s, 6 * s, 11.5 * s, 6 * s, 12.5 * s, 8 * s, 12.5 * s, 12 * s, 13.5 * s, 12 * s, 13.5 * s, 5 * s, 11.5 * s, 3 * s], fill=W, width=int(1.1 * s))


def engine(d, s):
    """The engine lamp: a block with its rocker cover, oil filler and the fan's side."""
    d.rectangle([4 * s, 6 * s, 12 * s, 12 * s], fill=W)
    d.rectangle([5 * s, 4 * s, 10 * s, 5.5 * s], fill=W)
    d.rectangle([7 * s, 2.5 * s, 8.5 * s, 4.5 * s], fill=W)
    d.rectangle([12 * s, 7.5 * s, 14.5 * s, 10.5 * s], fill=W)
    d.rectangle([1.5 * s, 8 * s, 4 * s, 9.5 * s], fill=W)
    d.rectangle([1.5 * s, 6.5 * s, 2.7 * s, 11 * s], fill=W)


def lock(d, s):
    """A padlock: its shackle and body with a keyhole."""
    d.arc([4.5 * s, 1.5 * s, 11.5 * s, 10 * s], 180, 360, fill=W, width=int(1.6 * s))
    d.line([4.5 * s + 0.8 * s, 5.5 * s, 4.5 * s + 0.8 * s, 8 * s], fill=W, width=int(1.6 * s))
    d.line([11.5 * s - 0.8 * s, 5.5 * s, 11.5 * s - 0.8 * s, 8 * s], fill=W, width=int(1.6 * s))
    d.rounded_rectangle([3 * s, 7.5 * s, 13 * s, 14.5 * s], radius=1.2 * s, fill=W)
    d.ellipse([7 * s, 9.5 * s, 9 * s, 11.5 * s], fill=(0, 0, 0, 0))
    d.rectangle([7.6 * s, 11 * s, 8.4 * s, 13 * s], fill=(0, 0, 0, 0))


def badge(d, s):
    """A rounded frame a label is written in (the drive mode, the automatic clutch)."""
    d.rounded_rectangle([0.5 * s, 2.5 * s, 15.5 * s, 13.5 * s], radius=2.5 * s, outline=W, width=int(1.2 * s))


def lamps():
    icons = [handbrake, fuel, engine, lock, badge]
    im = Image.new('RGBA', (ICON * len(icons), ICON), (0, 0, 0, 0))
    for i, f in enumerate(icons):
        im.paste(icon(f), (ICON * i, 0))
    return im


if __name__ == '__main__':
    save(dial(), 'dial.png')
    save(lamps(), 'lamps.png')
