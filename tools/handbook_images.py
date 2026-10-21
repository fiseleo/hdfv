"""hdfv 0.24: the handbook's pictures and the release gallery from AutoShot's screenshots (tools/autoshot/release-0.24.txt,
in the development client's screenshots folder). A picture is a 16:9 part of a 1280 x 720 shot (or all of it), made
320 x 180 (HandbookTest checks the size) into assets/hdfv/textures/gui/handbook; the gallery keeps the shots whole,
under %USERPROFILE%/.mcmod/release/hdfv/gallery (not in the project). Needs Pillow.

    py -3 tools/handbook_images.py
"""
import os
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(os.path.expanduser('~'), '.mcmod', 'runs', 'hdfv', 'client', 'screenshots')
OUT = os.path.join(HERE, '..', 'neoforge', 'src', 'main', 'resources', 'assets', 'hdfv', 'textures', 'gui', 'handbook')
GALLERY = os.path.join(os.path.expanduser('~'), '.mcmod', 'release', 'hdfv', 'gallery')

# picture: (shot, crop x, y, width) in the 1280 x 720 shot (the height is 9/16 of the width); None: the whole shot
PICTURES = {
    'lineup': ('r24-lineup', (160, 140, 960)),
    'furnaces': ('r24-furnaces', None),
    'lathe': ('r24-lathe', (570, 190, 480)),
    'machines': ('r24-machines', None),
    'cnc': ('r24-cnc', None),
    'engine_stand': ('r24-engine-stand', (350, 150, 620)),
    'car_factory': ('r24-car-factory', None),
    'sedan': ('r24-sedan', None),
    'seaplane': ('r24-seaplane', None),
    'distillation': ('r24-distillation', (200, 230, 800)),
    'tower': ('r24-tower', None),
    'semi': ('r24-semi', None),
}

# gallery name: shot
GALLERY_SHOTS = {
    '01-lineup': 'r24-lineup', '02-sedan': 'r24-sedan', '03-car-factory': 'r24-car-factory', '04-design-engine': 'r24-design-engine',
    '05-machines': 'r24-machines', '06-lathe': 'r24-lathe', '07-cnc': 'r24-cnc', '08-engine-stand': 'r24-engine-stand',
    '09-seaplane': 'r24-seaplane', '10-helicopter': 'r24-heli', '11-semi': 'r24-semi', '12-tower': 'r24-tower',
    '13-night': 'r24-night', '14-handbook': 'r24-handbook-start', '15-advancements': 'en_us-advancements', '16-handbook-zh-tw': 'zh_tw-handbook-start',
}


def shot(name):
    path = os.path.join(SHOTS, 'aerocore-' + name + '.png')
    if not os.path.exists(path):
        sys.exit('no shot ' + path)
    return Image.open(path).convert('RGB')


def main():
    os.makedirs(OUT, exist_ok=True)
    for picture, (name, crop) in PICTURES.items():
        im = shot(name)
        if crop is not None:
            x, y, w = crop
            im = im.crop((x, y, x + w, y + w * 9 // 16))
        im.resize((320, 180), Image.LANCZOS).save(os.path.join(OUT, picture + '.png'), optimize=True)
    os.makedirs(GALLERY, exist_ok=True)
    for g, name in GALLERY_SHOTS.items():
        src = os.path.join(SHOTS, 'aerocore-' + name + '.png')
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(GALLERY, g + '.png'))
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print('handbook pictures:', len(PICTURES), '(%d KB);' % (size // 1024), 'gallery:', GALLERY)


if __name__ == '__main__':
    main()
