#!/usr/bin/env python3
"""Renders the Google Play graphics into store/: the 512 px icon, the
1024x500 feature graphic and one square Wear OS screenshot per style.
Run after generate_assets.py:  python3 tools/generate_store.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import generate_assets as g

OUT = os.path.join(g.ROOT, "store")
BG = (18, 22, 34)


def face(style, masked=True):
    """The editor preview of a style, optionally without the round mask."""
    mask = g.circle_mask
    if not masked:
        g.circle_mask = lambda img: img   # Play applies its own mask to Wear shots.
    try:
        _, preview = g.style_scene(g.STYLES.index(style), style)
    finally:
        g.circle_mask = mask
    return preview


def shadowed(img, blur=18, offset=10):
    pad = blur * 3
    shadow = Image.new("RGBA", (img.width + pad * 2, img.height + pad * 2), (0, 0, 0, 0))
    alpha = img.getchannel("A").point(lambda a: a * 0.7)
    shadow.paste((0, 0, 0, 255), (pad, pad + offset), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    shadow.alpha_composite(img, (pad, pad))
    return shadow


def icon(heritage):
    img = Image.new("RGBA", (512, 512), BG + (255,))
    dial = shadowed(heritage.resize((430, 430), Image.LANCZOS), blur=10, offset=6)
    img.alpha_composite(dial, ((512 - dial.width) // 2, (512 - dial.height) // 2))
    return img.convert("RGB")


def feature(faces):
    w, h = 1024, 500
    img = Image.new("RGBA", (w, h), BG + (255,))
    d = ImageDraw.Draw(img)
    for x in range(w):   # soft light from the right
        t = x / w
        d.line([(x, 0), (x, h)], fill=(int(18 + 20 * t), int(22 + 20 * t), int(34 + 26 * t), 255))
    title = ImageFont.truetype(g.FONTS["sans_bold"], 84)
    sub = ImageFont.truetype(g.FONTS["sans_semibold"], 30)
    d.text((64, 170), "Tickwell", font=title, fill=(246, 246, 242))
    d.text((68, 280), "Classic watch faces", font=sub, fill=(200, 204, 214))
    d.text((68, 322), "that tick for real", font=sub, fill=(214, 178, 106))
    # Kept clear of the edges, which Play may crop.
    for f, size, x, y in [(faces[0], 280, 450, 130), (faces[2], 280, 690, 130),
                          (faces[1], 340, 540, 80)]:
        dial = shadowed(f.resize((size, size), Image.LANCZOS))
        img.alpha_composite(dial, (x - 54, y - 54))
    return img.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    by_key = {s["key"]: s for s in g.STYLES}
    previews = {k: face(s) for k, s in by_key.items()}
    icon(previews["heritage"]).save(os.path.join(OUT, "icon-512.png"), optimize=True)
    feature([previews["heritage"], previews["diver"], previews["dress"]]).save(
        os.path.join(OUT, "feature-graphic-1024x500.png"), optimize=True)
    for i, style in enumerate(g.STYLES, 1):
        shot = face(style, masked=False).convert("RGB").resize((480, 480), Image.LANCZOS)
        shot.save(os.path.join(OUT, f"wear-screenshot-{i}-{style['key']}.png"), optimize=True)
    print(f"Wrote store graphics to {OUT}")


if __name__ == "__main__":
    main()
