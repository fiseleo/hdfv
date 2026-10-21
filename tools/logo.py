"""hdfv 0.24: the logo (neoforge/src/main/resources/hdfv_logo.png, the mod list's, 512 x 256) and the release icon
(512 x 512, %USERPROFILE%/.mcmod/release/hdfv/icon.png, not in the project), from AutoShot's shots
(tools/autoshot/release-0.24.txt): the inline four in the design screen's 3D view, and the sedan on the pad.
Needs Pillow; the lettering is Segoe UI (Windows).

    py -3 tools/logo.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(os.path.expanduser('~'), '.mcmod', 'runs', 'hdfv', 'client', 'screenshots')
LOGO = os.path.join(HERE, '..', 'neoforge', 'src', 'main', 'resources', 'hdfv_logo.png')
ICON = os.path.join(os.path.expanduser('~'), '.mcmod', 'release', 'hdfv', 'icon.png')
BOLD = 'C:/Windows/Fonts/segoeuib.ttf'


def shot(name):
    return Image.open(os.path.join(SHOTS, 'aerocore-' + name + '.png')).convert('RGBA')


def shade(size, box, alpha):
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rectangle(box, fill=(8, 12, 20, alpha))
    return layer


def main():
    # the icon: the engine, square, its name's initials under it
    icon = shot('r24-design-engine').crop((500, 110, 1060, 670)).resize((512, 512), Image.LANCZOS)
    icon = Image.alpha_composite(icon, shade(icon.size, (0, 430, 512, 512), 190))
    d = ImageDraw.Draw(icon)
    d.text((256, 471), 'hdfv', font=ImageFont.truetype(BOLD, 64), fill=(255, 255, 255, 255), anchor='mm')
    os.makedirs(os.path.dirname(ICON), exist_ok=True)
    icon.convert('RGB').save(ICON, optimize=True)
    # the logo: the sedan, the name over a dark band on the left
    logo = shot('r24-sedan').crop((160, 160, 1280, 720)).resize((512, 256), Image.LANCZOS)   # 2:1
    logo = Image.alpha_composite(logo, shade(logo.size, (0, 0, 512, 78), 170))
    d = ImageDraw.Draw(logo)
    d.text((14, 6), 'High Degree of Freedom', font=ImageFont.truetype(BOLD, 30), fill=(255, 255, 255, 255))
    d.text((14, 40), 'Vehicles', font=ImageFont.truetype(BOLD, 30), fill=(255, 210, 90, 255))
    logo.convert('RGB').save(LOGO, optimize=True)
    print('logo', LOGO, 'icon', ICON)


if __name__ == '__main__':
    main()
