#!/usr/bin/env python3
"""Synthesises the tick/tock sounds and draws the ticker app's bitmaps.

A mechanical tick is a sharp click followed by a couple of fast-decaying
metallic resonances. The "tock" is the same, pitched slightly lower, so
alternating seconds sound like an escapement. Re-run after tweaking:
  python3 tools/generate_tick.py
"""
import math
import os
import random
import struct
import wave

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "wear", "src", "main", "res")
RATE = 44100


def tick(resonances, seed):
    rng = random.Random(seed)
    n = int(RATE * 0.045)
    out = []
    prev = 0.0
    for i in range(n):
        t = i / RATE
        # 1 ms high-passed noise burst: the "click" of the pallet.
        noise = rng.uniform(-1, 1) if t < 0.001 else 0.0
        click, prev = noise - prev, noise
        ring = sum(a * math.exp(-t / tau) * math.sin(2 * math.pi * f * t)
                   for f, a, tau in resonances)
        out.append(0.6 * click + ring)
    # Short fade-in so the onset doesn't pop on small speakers.
    for i in range(32):
        out[i] *= i / 32
    peak = max(abs(v) for v in out)
    return [v / peak * 0.9 for v in out]


def write_wav(name, samples):
    path = os.path.join(RES, "raw", name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(v * 32767)) for v in samples))


def icon(size):
    """Dark dial with a seconds hand and two sound arcs."""
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, s - 1, s - 1], fill=(24, 30, 48, 255))
    c = s / 2
    for h in range(12):
        a = math.radians(h * 30)
        r1, r2 = s * 0.36, s * 0.43
        d.line([(c + r1 * math.sin(a), c - r1 * math.cos(a)),
                (c + r2 * math.sin(a), c - r2 * math.cos(a))],
               fill=(220, 222, 230, 255), width=int(s * 0.025))
    # Seconds hand pointing at ~2 o'clock.
    a = math.radians(55)
    d.line([(c - s * 0.08 * math.sin(a), c + s * 0.08 * math.cos(a)),
            (c + s * 0.33 * math.sin(a), c - s * 0.33 * math.cos(a))],
           fill=(214, 40, 56, 255), width=int(s * 0.03))
    r = s * 0.04
    d.ellipse([c - r, c - r, c + r, c + r], fill=(214, 40, 56, 255))
    # Sound arcs to the lower left.
    for k, rr in enumerate((0.16, 0.24)):
        box = [c - s * rr - s * 0.06, c - s * rr + s * 0.14,
               c + s * rr - s * 0.06, c + s * rr + s * 0.14]
        d.arc(box, 115, 155, fill=(236, 234, 220, 255), width=int(s * 0.028))
    return img.resize((size, size), Image.LANCZOS)


def tile_preview():
    s = 384
    img = Image.new("RGBA", (s, s), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype("/usr/share/fonts/truetype/inter-zorin-os/Inter-SemiBold.ttf", 34)
    big = ImageFont.truetype("/usr/share/fonts/truetype/inter-zorin-os/Inter-Bold.ttf", 44)
    d.text((s / 2, 110), "Tick sound", font=font, fill=(220, 222, 230), anchor="mm")
    d.ellipse([s / 2 - 70, s / 2 - 50, s / 2 + 70, s / 2 + 90], fill=(214, 40, 56))
    d.text((s / 2, s / 2 + 20), "On", font=big, fill=(255, 255, 255), anchor="mm")
    return img


def main():
    os.makedirs(os.path.join(RES, "raw"), exist_ok=True)
    write_wav("tick.wav", tick([(3400, 1.0, 0.0035), (5600, 0.6, 0.0022),
                                (1500, 0.35, 0.006)], seed=1))
    write_wav("tock.wav", tick([(2900, 1.0, 0.0038), (4800, 0.55, 0.0024),
                                (1300, 0.35, 0.0065)], seed=2))
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                        ("xxhdpi", 144), ("xxxhdpi", 192)]:
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        icon(px).save(os.path.join(folder, "ic_launcher.png"), optimize=True)
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    tile_preview().save(os.path.join(RES, "drawable-nodpi", "tile_preview.png"), optimize=True)
    print("Generated tick sounds, icons and tile preview")


if __name__ == "__main__":
    main()
