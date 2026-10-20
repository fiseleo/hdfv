"""Makes hdfv's effect textures (hdfv 0.11): the flame noise and the propeller blur (finishes `hdfv:fx_flame` and
`hdfv:fx_prop_blur`, added to assets/hdfv/finishes.json next to textures.py's), the exhaust smoke's frames, the soot's
and the spark's particle textures (assets/hdfv/textures/particle, listed in assets/hdfv/particles/*.json); 0.12 the
wheels' and the ground's: tyre smoke, dust, mud, snow and road spray (white or grey, tinted by the particle).

Noise is made in Fourier space with integer wave numbers (random phases under a smooth, stretched envelope), so every
texture tiles without a seam, the same way every run (fixed seeds). The flame's streaks run down the texture (v: down
a plume, AeroCore's PlumeRenderer scrolls it that way); the blur's rings run across it (v: out along a blade).
Run: scripts/blender.ps1 fx"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402

SIZE = 256
SMOKE_FRAMES, SMOKE_SIZE = 8, 32


def periodic(size, sigma_u, sigma_v, seed, roughness=0.0):
    """Tiling noise (mean 0, standard deviation 1): random phases under exp(-(ku/su)^2 - (kv/sv)^2), plus a little of
    every frequency (roughness) for grain. ku is across the image (columns), kv down it (rows)."""
    rng = np.random.default_rng(seed)
    k = np.fft.fftfreq(size, 1.0 / size)
    kv, ku = np.meshgrid(k, k, indexing='ij')
    envelope = np.exp(-(ku / sigma_u) ** 2 - (kv / sigma_v) ** 2) + roughness / (1 + ku ** 2 + kv ** 2)
    envelope[0, 0] = 0
    spectrum = envelope * (rng.normal(size=(size, size)) + 1j * rng.normal(size=(size, size)))
    img = np.real(np.fft.ifft2(spectrum))
    return (img - img.mean()) / max(img.std(), 1e-12)


def to_png(rgb, alpha):
    out = np.concatenate([rgb, alpha[..., None]], axis=2)
    pixels = np.clip(np.round(out * 255), 0, 255).astype(np.uint8)
    return common.png_rgba(pixels.shape[1], pixels.shape[0], pixels.tobytes())


def flame():
    """Hot gas in streaks along the plume, finer wisps over them; white (the plume's colour comes from its vertices)."""
    streaks = periodic(SIZE, 24, 3, 101)
    wisps = periodic(SIZE, 40, 12, 102, roughness=0.02)
    n = 0.7 * streaks + 0.3 * wisps
    value = np.clip(0.72 + 0.16 * n, 0.35, 1.0)
    rgb = np.repeat(value[..., None], 3, axis=2)
    return to_png(rgb, np.ones_like(value))


def prop_blur():
    """A spinning blade's track: rings out along the blade (its twist and taper), barely changing around."""
    rings = periodic(SIZE, 1.5, 30, 201)
    value = np.clip(0.86 + 0.09 * rings, 0.6, 1.0)
    rgb = np.repeat(value[..., None], 3, axis=2)
    return to_png(rgb, np.full_like(value, 0.75))   # gloss 0.5 + 0.5 x 0.5: a blurred disc is dull


def puff(frame, seed):
    """A smoke puff: a soft round cloud, denser and smaller in the first frames, ragged and thin in the last."""
    t = frame / (SMOKE_FRAMES - 1)
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    lumps = periodic(s, 3 + 2 * t, 3 + 2 * t, seed)
    edge = 0.55 + 0.35 * t + 0.08 * lumps
    alpha = np.clip(1 - (r / np.maximum(edge, 0.2)) ** 2, 0, 1) ** 1.5
    alpha *= np.clip(0.75 + 0.25 * lumps, 0, 1) * (1 - 0.55 * t)
    shade = np.clip(0.92 + 0.06 * lumps - 0.08 * r, 0.7, 1)
    return to_png(np.repeat(shade[..., None], 3, axis=2), alpha)


def soot(frame, seed):
    """Diesel soot: a smaller, lumpier puff (tinted black by the particle)."""
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    lumps = periodic(s, 5, 5, seed, roughness=0.05)
    alpha = np.clip(1 - (r / (0.7 + 0.1 * lumps)) ** 2, 0, 1) * np.clip(0.7 + 0.3 * lumps, 0, 1) * (1 - 0.1 * frame)
    return to_png(np.ones((s, s, 3)) * 0.9, alpha)


def spark():
    """A glowing particle: a white-hot core in a yellow halo."""
    s = 8
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    core = np.clip(1 - r / 0.5, 0, 1)
    halo = np.clip(1 - r, 0, 1) ** 2
    rgb = np.stack([np.ones_like(r), 0.85 + 0.15 * core, 0.55 + 0.45 * core], axis=2)
    return to_png(rgb, np.clip(halo + core, 0, 1))


def tyre_smoke(frame, seed):
    """Tyre smoke (0.12): a billowing puff, broader and softer than an exhaust's, lumpier as it spreads."""
    t = frame / (SMOKE_FRAMES - 1)
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    lumps = periodic(s, 2.5 + 2 * t, 2.5 + 2 * t, seed)
    edge = 0.7 + 0.25 * t + 0.1 * lumps
    alpha = np.clip(1 - (r / np.maximum(edge, 0.2)) ** 2, 0, 1) ** 1.2 * np.clip(0.8 + 0.2 * lumps, 0, 1) * (1 - 0.4 * t)
    shade = np.clip(0.95 + 0.05 * lumps - 0.06 * r, 0.8, 1)
    return to_png(np.repeat(shade[..., None], 3, axis=2), alpha)


def dust(frame, seed):
    """Dust (0.12): a thin cloud of fine grains, grainier than smoke (tinted the ground's colour by the particle)."""
    t = frame / 3
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    lumps = periodic(s, 4, 4, seed, roughness=0.3)
    alpha = np.clip(1 - (r / (0.75 + 0.1 * lumps)) ** 2, 0, 1) * np.clip(0.6 + 0.4 * lumps, 0, 1) * (1 - 0.25 * t)
    return to_png(np.repeat(np.clip(0.9 + 0.1 * lumps, 0.7, 1)[..., None], 3, axis=2), alpha)


def clod(frame, seed):
    """A clod of mud (0.12): a small dense lump with a ragged edge, darker underneath (tinted brown by the particle)."""
    s = 16
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    lumps = periodic(s, 3, 3, seed, roughness=0.2)
    alpha = (r < 0.55 + 0.18 * lumps).astype(float)
    shade = np.clip(0.75 + 0.25 * ((s - y) / s) + 0.08 * lumps, 0.5, 1)
    return to_png(np.repeat(shade[..., None], 3, axis=2), alpha)


def snow_puff(frame, seed):
    """Powder snow (0.12): a soft puff speckled with flakes."""
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot((x + 0.5) / s - 0.5, (y + 0.5) / s - 0.5) * 2
    soft = periodic(s, 3, 3, seed)
    flakes = periodic(s, 14, 14, seed + 50) > 1.6
    alpha = np.clip(1 - (r / 0.8) ** 2, 0, 1) * np.clip(0.45 + 0.2 * soft, 0, 1) * (1 - 0.2 * frame)
    alpha = np.where(flakes & (r < 0.95), np.maximum(alpha, 0.9), alpha)
    return to_png(np.ones((s, s, 3)), alpha)


def spray(frame, seed):
    """Road spray (0.12): a faint mist of fine drops, streaked along the way it flies (left to right)."""
    s = SMOKE_SIZE
    y, x = np.mgrid[0:s, 0:s]
    r = np.hypot(((x + 0.5) / s - 0.5) * 0.8, (y + 0.5) / s - 0.5) * 2
    streaks = periodic(s, 2, 10, seed, roughness=0.1)
    alpha = np.clip(1 - r ** 2, 0, 1) * np.clip(0.35 + 0.18 * streaks, 0, 1) * (1 - 0.2 * frame)
    return to_png(np.ones((s, s, 3)) * 0.95, alpha)


def main():
    changed = 0
    part = os.path.join(common.ASSETS, 'textures', 'part')
    particle = os.path.join(common.ASSETS, 'textures', 'particle')
    finishes = {
        'hdfv:fx_flame': ({'texture': 'hdfv:textures/part/fx_flame.png', 'tile': 0.5, 'gloss': 0.0, 'metal': 0}, flame()),
        'hdfv:fx_prop_blur': ({'texture': 'hdfv:textures/part/fx_prop_blur.png', 'tile': 0.3, 'gloss': 0.5, 'metal': 0}, prop_blur()),
    }
    for fid, (entry, png) in finishes.items():
        changed += common.write_if_changed(os.path.join(part, fid.split(':')[1] + '.png'), png)
    # the finishes next to textures.py's (which keeps the fx_ ones when it rewrites the file)
    path = os.path.join(common.ASSETS, 'finishes.json')
    import json
    runtime = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    for fid, (entry, png) in finishes.items():
        runtime[fid] = entry
    changed += common.write_json_if_changed(path, runtime)
    smoke = [f'hdfv:exhaust_smoke_{i}' for i in range(SMOKE_FRAMES)]
    for i in range(SMOKE_FRAMES):
        changed += common.write_if_changed(os.path.join(particle, f'exhaust_smoke_{i}.png'), puff(i, 300 + i))
    soots = [f'hdfv:soot_{i}' for i in range(4)]
    for i in range(4):
        changed += common.write_if_changed(os.path.join(particle, f'soot_{i}.png'), soot(i, 400 + i))
    changed += common.write_if_changed(os.path.join(particle, 'spark.png'), spark())
    lists = {'exhaust_smoke': smoke, 'soot': soots, 'spark': ['hdfv:spark']}
    # 0.12: the ground's
    for name, make, frames, seed in (('tyre_smoke', tyre_smoke, SMOKE_FRAMES, 500), ('dust', dust, 4, 600), ('mud', clod, 4, 700),
                                     ('snow_puff', snow_puff, 4, 800), ('road_spray', spray, 4, 900)):
        lists[name] = [f'hdfv:{name}_{i}' for i in range(frames)]
        for i in range(frames):
            changed += common.write_if_changed(os.path.join(particle, f'{name}_{i}.png'), make(i, seed + i))
    for name, textures in lists.items():
        changed += common.write_json_if_changed(os.path.join(common.ASSETS, 'particles', name + '.json'), {'textures': textures})
    common.log('fx done:', changed, 'files written')


main()
