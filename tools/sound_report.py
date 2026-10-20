"""Look at sound without hearing it (hdfv 0.19).

For each WAV file given (or every WAV in a folder given), writes next to it:
  <name>.png   a spectrogram (0-8 kHz, dB) over the waveform and the averaged spectrum with its peaks marked
  <name>.txt   level (RMS dBFS), the strongest peaks (Hz, dB), 1/3-octave band levels

Usage: py -3 tools/sound_report.py <file.wav|folder> [...] [--max-hz 8000]
The WAVs come from /hdfvdev sound record (exports/sound/hdfv-<time>/) or /hdfvdev sound render.
"""
import sys
import pathlib

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy.io import wavfile
from scipy import signal

THIRDS = [20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000,
          2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000]


def load(path):
    rate, data = wavfile.read(path)
    x = data.astype(np.float64)
    if data.dtype == np.int16:
        x /= 32767.0
    if x.ndim > 1:
        x = x.mean(axis=1)
    return rate, x


def peaks(f, p, count=12, min_hz=20, spacing_hz=8):
    db = 10 * np.log10(np.maximum(p, 1e-30))
    idx = signal.argrelmax(db, order=2)[0]
    idx = [i for i in idx if f[i] >= min_hz]
    idx.sort(key=lambda i: -db[i])
    out = []
    for i in idx:
        if all(abs(f[i] - f[j]) >= spacing_hz for j in out):
            out.append(i)
        if len(out) == count:
            break
    return [(float(f[i]), float(db[i])) for i in sorted(out, key=lambda i: f[i])]


def report(path, max_hz):
    rate, x = load(path)
    if len(x) < rate // 10:
        print(f"{path}: too short ({len(x)} samples)")
        return
    rms = float(np.sqrt(np.mean(x * x)))
    n = 16384 if len(x) >= 16384 else 1 << int(np.log2(len(x)))
    f, p = signal.welch(x, fs=rate, nperseg=n, noverlap=n // 2, window="hann", scaling="spectrum")
    found = peaks(f, p)
    bands = []
    for c in THIRDS:
        lo, hi = c / 2 ** (1 / 6), c * 2 ** (1 / 6)
        s = p[(f >= lo) & (f < hi)].sum()
        bands.append((c, 10 * np.log10(max(s, 1e-30))))

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 8), gridspec_kw={"height_ratios": [3, 2]})
    nfft = 4096 if rate >= 32000 else 2048
    ff, tt, ss = signal.spectrogram(x, fs=rate, nperseg=nfft, noverlap=nfft * 3 // 4, window="hann")
    sel = ff <= max_hz
    img = 10 * np.log10(np.maximum(ss[sel], 1e-14))
    ax1.pcolormesh(tt, ff[sel], img, shading="auto", vmin=img.max() - 80, vmax=img.max(), cmap="magma")
    ax1.set_ylabel("Hz")
    ax1.set_title(f"{pathlib.Path(path).name}: {len(x) / rate:.2f} s, RMS {20 * np.log10(max(rms, 1e-12)):.1f} dBFS")
    db = 10 * np.log10(np.maximum(p, 1e-30))
    ax2.semilogx(f[1:], db[1:], lw=0.7)
    for hz, level in found:
        ax2.annotate(f"{hz:.0f}", (hz, level), fontsize=7, xytext=(0, 4), textcoords="offset points", ha="center")
    ax2.set_xlim(20, min(rate / 2, 20000))
    ax2.set_ylim(db.max() - 90, db.max() + 6)
    ax2.set_xlabel("Hz")
    ax2.set_ylabel("dB re full scale")
    ax2.grid(True, which="both", alpha=0.3)
    fig.tight_layout()
    out = pathlib.Path(path).with_suffix(".png")
    fig.savefig(out, dpi=90)
    plt.close(fig)

    lines = [f"file {path}", f"seconds {len(x) / rate:.3f}", f"rms_dbfs {20 * np.log10(max(rms, 1e-12)):.2f}", "peaks (Hz, dB):"]
    lines += [f"  {hz:9.2f}  {level:7.2f}" for hz, level in found]
    lines.append("third octaves (Hz, dB):")
    lines += [f"  {c:7g}  {level:7.2f}" for c, level in bands]
    pathlib.Path(path).with_suffix(".txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{out}  rms {20 * np.log10(max(rms, 1e-12)):.1f} dBFS, peaks " + ", ".join(f"{hz:.0f}" for hz, _ in found[:6]))


def main(argv):
    max_hz = 8000
    paths = []
    i = 0
    while i < len(argv):
        if argv[i] == "--max-hz":
            max_hz = float(argv[i + 1])
            i += 2
            continue
        paths.append(pathlib.Path(argv[i]))
        i += 1
    if not paths:
        print(__doc__)
        return 1
    for p in paths:
        files = sorted(p.glob("*.wav")) if p.is_dir() else [p]
        for f in files:
            report(str(f), max_hz)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
