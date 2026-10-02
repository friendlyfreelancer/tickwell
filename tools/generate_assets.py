#!/usr/bin/env python3
"""Renders every bitmap the watch face uses and writes res/raw/watchface.xml.

Watch Face Format has no path drawing, so dials and hands are pre-rendered
PNGs. Everything is drawn at 4x and downsampled for clean anti-aliasing.
Re-run after changing any style:  python3 tools/generate_assets.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "face", "src", "main", "res")
DRAWABLE = os.path.join(RES, "drawable-nodpi")
# The phone companion shows the same previews and icon.
PHONE_RES = os.path.join(ROOT, "phone", "src", "main", "res")

SIZE = 450          # Watch face canvas (px).
SS = 4              # Supersampling factor.
S = SIZE * SS
C = S // 2

FONTS = {
    "roman": "/usr/share/fonts/opentype/urw-base35/C059-Roman.otf",
    "sans_semibold": "/usr/share/fonts/truetype/inter-zorin-os/Inter-SemiBold.ttf",
    "sans_bold": "/usr/share/fonts/truetype/inter-zorin-os/Inter-Bold.ttf",
    "serif_italic": "/usr/share/fonts/truetype/gelasio-zorin-os/Gelasio-Italic.ttf",
}

# Shown in the editor and in previews.
PREVIEW_TIME = (10, 8, 32)


# ---------------------------------------------------------------- helpers

def polar(r, deg):
    a = math.radians(deg)
    return C + r * math.sin(a), C - r * math.cos(a)


def canvas(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (S, S), color)


def radial_gradient(inner, outer):
    img = canvas(outer + (255,))
    d = ImageDraw.Draw(img)
    for r in range(C, 0, -2):
        t = r / C
        col = tuple(int(inner[i] + (outer[i] - inner[i]) * t ** 1.6) for i in range(3))
        d.ellipse([C - r, C - r, C + r, C + r], fill=col + (255,))
    return img


def ring(d, r, width, color):
    d.ellipse([C - r, C - r, C + r, C + r], outline=color, width=width)


def tick(d, r1, r2, deg, width, color):
    """A rectangular index from radius r1 to r2 (square ends)."""
    a = math.radians(deg)
    ux, uy = math.sin(a), -math.cos(a)      # radial
    px, py = -uy * width / 2, ux * width / 2  # perpendicular
    x1, y1 = C + ux * r1, C + uy * r1
    x2, y2 = C + ux * r2, C + uy * r2
    d.polygon([(x1 + px, y1 + py), (x2 + px, y2 + py),
               (x2 - px, y2 - py), (x1 - px, y1 - py)], fill=color)


def dot(d, x, y, r, color):
    d.ellipse([x - r, y - r, x + r, y + r], fill=color)


def text_at(img, text, font, x, y, color, angle=0):
    """Draws text centred on (x, y), optionally rotated clockwise."""
    l, t, r, b = font.getbbox(text)
    w, h = r - l, b - t
    pad = 8
    tile = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(tile).text((pad - l, pad - t), text, font=font, fill=color)
    if angle:
        tile = tile.rotate(-angle, resample=Image.BICUBIC, expand=True)
    img.alpha_composite(tile, (int(x - tile.width / 2), int(y - tile.height / 2)))


def downsample(img, size=SIZE):
    return img.resize((size, size), Image.LANCZOS)


def circle_mask(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, img.width - 1, img.height - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def save(img, name):
    img.save(os.path.join(DRAWABLE, name + ".png"), optimize=True)


# ---------------------------------------------------------------- hands
#
# A hand is described pointing straight up, pivot at (0, 0), +y toward the
# tip, in supersampled pixels. Shapes: ("poly", pts, fill),
# ("circle", (x, y), r, fill), ("line", (x1, y1), (x2, y2), width, fill).

def leaf(length, start, max_w, stem_w, tail=0):
    """Breguet-ish leaf: slim stem, then a pointed leaf swelling mid-way."""
    right = [(stem_w / 2, -tail), (stem_w / 2, start)]
    steps = 40
    for i in range(1, steps + 1):
        t = i / steps
        y = start + (length - start) * t
        w = max(stem_w / 2, max_w / 2 * math.sin(math.pi * t) ** 0.7)
        right.append((w, y))
    right.append((0, length))
    left = [(-x, y) for x, y in reversed(right[:-1])]
    return right + left


def draw_hand(shapes, outline=None):
    xs, ys = [], []
    for s in shapes:
        if s[0] == "poly":
            xs += [p[0] for p in s[1]]
            ys += [p[1] for p in s[1]]
        elif s[0] == "circle":
            (x, y), r = s[1], s[2]
            xs += [x - r, x + r]
            ys += [y - r, y + r]
        elif s[0] == "line":
            (x1, y1), (x2, y2), w = s[1], s[2], s[3]
            xs += [x1 - w, x2 + w]
            ys += [y1, y2]
    pad = 12 + (outline[1] if outline else 0)
    half_w = max(abs(v) for v in xs) + pad
    top = max(ys) + pad          # distance pivot -> top of image
    bottom = -min(ys) + pad      # distance pivot -> bottom of image
    # Round to the supersampling grid so the pivot lands on a whole pixel.
    q = SS * 2
    half_w = math.ceil(half_w / q) * q
    top = math.ceil(top / SS) * SS
    bottom = math.ceil(bottom / SS) * SS
    w, h = int(half_w * 2), int(top + bottom)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def tx(p):
        return (half_w + p[0], top - p[1])

    for s in shapes:
        kind = s[0]
        if kind == "poly":
            if outline:
                d.polygon([tx(p) for p in s[1]], fill=s[2],
                          outline=outline[0], width=outline[1])
            else:
                d.polygon([tx(p) for p in s[1]], fill=s[2])
        elif kind == "circle":
            x, y = tx(s[1])
            r = s[2]
            if outline:
                d.ellipse([x - r, y - r, x + r, y + r], fill=s[3],
                          outline=outline[0], width=outline[1])
            else:
                d.ellipse([x - r, y - r, x + r, y + r], fill=s[3])
        elif kind == "line":
            d.line([tx(s[1]), tx(s[2])], fill=s[4], width=s[3])
    return {"img": img, "pivot_y": top}


def hand_asset(name, hand, center=(SIZE // 2, SIZE // 2)):
    """Saves a hand; x/y place its pivot on `center` (in parent coordinates)."""
    img = hand["img"]
    small = img.resize((img.width // SS, img.height // SS), Image.LANCZOS)
    save(small, name)
    w, h = small.size
    return {
        "resource": name,
        "x": center[0] - w // 2,
        "y": center[1] - hand["pivot_y"] // SS,
        "width": w,
        "height": h,
        "pivotY": round((hand["pivot_y"] // SS) / h, 4),
    }


def compose_hand(layer, hand, angle, center=(C, C)):
    img = hand["img"]
    cx, cy = center
    tmp = canvas()
    tmp.alpha_composite(img, (int(cx - img.width / 2), int(cy - hand["pivot_y"])))
    layer.alpha_composite(tmp.rotate(-angle, resample=Image.BICUBIC, center=center))




# ---------------------------------------------------------------- colours

INK = (26, 26, 28, 255)
BLUE_STEEL = (29, 56, 132, 255)
RED = (196, 22, 42, 255)
WHITE = (246, 246, 242, 255)
SILVER_LIGHT = (232, 233, 238, 255)
SILVER_DARK = (150, 154, 164, 255)


def palette(main, light, dark):
    return {"main": main + (255,), "light": light + (255,), "dark": dark + (255,)}


# Hand colours offered in the editor. "original" uses each style's own.
HAND_COLORS = [
    ("original", None),
    ("blued", palette((29, 56, 132), (58, 92, 178), (18, 36, 90))),
    ("black", palette((26, 26, 28), (78, 78, 84), (14, 14, 16))),
    ("silver", palette((200, 203, 211), (236, 237, 241), (150, 154, 164))),
    ("gold", palette((201, 162, 77), (232, 200, 124), (158, 120, 48))),
    ("rose", palette((192, 128, 108), (226, 168, 148), (150, 92, 76))),
]


def luminance(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2])


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


# While set, every hand gets this outline instead of a contrast-based one
# (used when rendering the tinted hand layers).
FORCE_EDGE = None


def edge_for(color, dial, width=6):
    """An outline that keeps a hand readable when it blends into the dial."""
    if FORCE_EDGE is not None:
        return FORCE_EDGE
    if contrast(color, dial) >= 2.2:
        return None
    edge = (28, 28, 30, 255) if luminance(dial) > 0.3 else (215, 215, 215, 255)
    return (edge, width)


# ---------------------------------------------------------------- styles
#
# Each style has a dial, the dial colour under the hands (for contrast
# checks), its original hand palette, and a function drawing the hands in a
# given palette.

def heritage_dial():
    img = radial_gradient((250, 245, 234), (226, 215, 194))
    d = ImageDraw.Draw(img)
    # Chemin de fer minute track.
    ring(d, 850, 5, INK)
    ring(d, 800, 3, INK)
    for m in range(60):
        tick(d, 800, 850, m * 6, 10 if m % 5 == 0 else 4, INK)
    font = ImageFont.truetype(FONTS["roman"], 150)
    numerals = ["XII", "I", "II", "III", "IIII", "V", "VI",
                "VII", "VIII", "IX", "X", "XI"]
    for i, n in enumerate(numerals):
        x, y = polar(685, i * 30)
        text_at(img, n, font, x, y, INK, angle=i * 30)
    ring(d, 560, 3, (60, 55, 50, 160))
    script = ImageFont.truetype(FONTS["serif_italic"], 70)
    text_at(img, "AJAR", script, C, C - 300, (60, 55, 50, 255))
    return img


def heritage_hands(pal, dial):
    col = pal["main"]
    edge = edge_for(col, dial, 5)
    hour = draw_hand([("poly", leaf(420, 60, 88, 18, tail=40), col)], outline=edge)
    minute = draw_hand([("poly", leaf(745, 90, 50, 12, tail=50), col),
                        ("circle", (0, 0), 30, col)], outline=edge)
    sec = col if edge is None else pal["dark"]
    second = draw_hand([("line", (0, -190), (0, 800), 7, sec),
                        ("circle", (0, -150), 22, sec),
                        ("circle", (0, 0), 20, sec)])
    return hour, minute, second


def railroad_dial():
    img = canvas((250, 250, 247, 255))
    d = ImageDraw.Draw(img)
    ring(d, 860, 6, INK)
    ring(d, 790, 4, INK)
    for m in range(60):
        tick(d, 790, 860, m * 6, 14 if m % 5 == 0 else 5, INK)
    font = ImageFont.truetype(FONTS["sans_semibold"], 170)
    for i in range(12):
        x, y = polar(640, i * 30)
        text_at(img, str(12 if i == 0 else i), font, x, y, INK)
    small = ImageFont.truetype(FONTS["sans_semibold"], 52)
    text_at(img, "AJAR", small, C, C + 290, (90, 90, 90, 255))
    return img


def railroad_hands(pal, dial):
    col = pal["main"]
    edge = edge_for(col, dial, 5)
    hour = draw_hand([
        ("poly", [(-20, -70), (20, -70), (20, 330), (-20, 330)], col),
        ("poly", [(-62, 350), (0, 300), (62, 350), (0, 470)], col),
    ], outline=edge)
    minute = draw_hand([
        ("poly", [(-18, -100), (18, -100), (7, 760), (-7, 760)], col),
        ("circle", (0, 0), 34, col),
    ], outline=edge)
    # The red lollipop seconds hand is the railroad signature; keep it.
    # Plain tapered needle: no disc near the tip, unlike the Swiss railway clock.
    second = draw_hand([("poly", [(-9, -210), (9, -210), (3, 810), (-3, 810)], RED),
                        ("circle", (0, 0), 22, RED)])
    return hour, minute, second


def pilot_dial():
    img = radial_gradient((34, 34, 36), (16, 16, 17))
    d = ImageDraw.Draw(img)
    for m in range(60):
        if m % 5 == 0:
            if m != 0:
                tick(d, 790, 870, m * 6, 16, WHITE)
        else:
            tick(d, 830, 870, m * 6, 6, WHITE)
    # Type-A triangle with two dots at 12.
    d.polygon([(C - 60, C - 870), (C + 60, C - 870), (C, C - 740)], fill=WHITE)
    dot(d, C - 90, C - 845, 16, WHITE)
    dot(d, C + 90, C - 845, 16, WHITE)
    font = ImageFont.truetype(FONTS["sans_bold"], 190)
    for i in range(1, 12):
        x, y = polar(655, i * 30)
        text_at(img, str(i), font, x, y, WHITE)
    small = ImageFont.truetype(FONTS["sans_semibold"], 46)
    text_at(img, "AJAR", small, C, C - 300, (220, 220, 220, 255))
    return img


def pilot_hands(pal, dial):
    col = pal["main"]
    black = (10, 10, 10, 255)
    edge = edge_for(col, dial) or (black, 6)
    fuller = black if luminance(col) > 0.25 else (200, 200, 200, 255)
    hour = draw_hand([
        ("poly", [(-36, -70), (36, -70), (40, 360), (0, 470), (-40, 360)], col),
        ("line", (0, 40), (0, 340), 8, fuller),
    ], outline=edge)
    minute = draw_hand([
        ("poly", [(-28, -90), (28, -90), (30, 650), (0, 750), (-30, 650)], col),
        ("line", (0, 60), (0, 620), 7, fuller),
        ("circle", (0, 0), 36, col),
    ], outline=edge)
    second = draw_hand([("line", (0, -200), (0, 800), 8, WHITE),
                        ("circle", (0, -170), 20, WHITE),
                        ("circle", (0, 0), 20, RED)])
    return hour, minute, second


def dress_dial():
    img = radial_gradient((38, 58, 102), (10, 18, 38))
    rays = canvas()
    rd = ImageDraw.Draw(rays)
    for i in range(240):
        x, y = polar(C, i * 1.5)
        rd.line([(C, C), (x, y)], fill=(255, 255, 255, 10 if i % 2 else 0), width=4)
    img.alpha_composite(rays)
    d = ImageDraw.Draw(img)
    for m in range(60):
        if m % 5:
            x, y = polar(860, m * 6)
            dot(d, x, y, 5, (200, 205, 215, 255))
    for h in range(12):
        if h == 3:
            continue  # date window
        offsets = (-26, 26) if h == 0 else (0,)
        for off in offsets:
            a = h * 30
            # Offset batons sideways for the double index at 12.
            ox = off * math.cos(math.radians(a))
            oy = off * math.sin(math.radians(a))
            layer = canvas()
            ld = ImageDraw.Draw(layer)
            tick(ld, 690, 830, a, 30, SILVER_DARK)
            tick(ld, 690, 830, a, 16, SILVER_LIGHT)
            img.alpha_composite(layer.transform(
                layer.size, Image.AFFINE, (1, 0, -ox, 0, 1, -oy), Image.BICUBIC))
    # Date window at 3 (the numeral is drawn by the watch face).
    x0, y0, x1, y1 = C + 610, C - 64, C + 790, C + 64
    d.rounded_rectangle([x0 - 8, y0 - 8, x1 + 8, y1 + 8], 18, fill=SILVER_DARK)
    d.rounded_rectangle([x0, y0, x1, y1], 12, fill=(250, 250, 248, 255))
    script = ImageFont.truetype(FONTS["serif_italic"], 66)
    text_at(img, "AJAR", script, C, C - 330, (220, 222, 230, 255))
    return img


def dauphine(pal, length, width, tail):
    """Two-tone faceted dauphine blade: light left facet, dark right."""
    tip, mid, base = (0, length), width / 2, (0, -tail)
    widest = length * 0.2
    return [
        ("poly", [base, (-mid, widest), tip], pal["light"]),
        ("poly", [base, tip, (mid, widest)], pal["dark"]),
    ]


def dress_hands(pal, dial):
    edge = edge_for(pal["main"], dial, 4)
    hour = draw_hand(dauphine(pal, 460, 64, 50), outline=edge)
    minute = draw_hand(dauphine(pal, 760, 48, 60) + [("circle", (0, 0), 28, pal["light"])],
                       outline=edge)
    sec = pal["light"] if edge is None else SILVER_LIGHT
    second = draw_hand([("line", (0, -170), (0, 820), 5, sec),
                        ("circle", (0, 0), 16, sec)])
    return hour, minute, second


# Quartz: plain white dial, upright Roman numerals, slim black hands.

def quartz_dial():
    img = canvas((251, 251, 250, 255))
    d = ImageDraw.Draw(img)
    ring(d, 862, 4, INK)
    for m in range(60):
        tick(d, 815, 860, m * 6, 9 if m % 5 == 0 else 4, INK)
    font = ImageFont.truetype(FONTS["roman"], 128)
    numerals = ["XII", "I", "II", "III", "IV", "V", "VI",
                "VII", "VIII", "IX", "X", "XI"]
    for i, n in enumerate(numerals):
        x, y = polar(690, i * 30)
        text_at(img, n, font, x, y, INK)
    small = ImageFont.truetype(FONTS["sans_semibold"], 40)
    text_at(img, "AJAR", small, C, C + 270, (110, 110, 110, 255))
    return img


def quartz_hands(pal, dial):
    col = pal["main"]
    edge = edge_for(col, dial, 5)
    hour = draw_hand([("poly", [(-18, -60), (18, -60), (14, 380), (0, 440), (-14, 380)], col)],
                     outline=edge)
    minute = draw_hand([("poly", [(-13, -80), (13, -80), (9, 700), (0, 770), (-9, 700)], col),
                        ("circle", (0, 0), 26, col)], outline=edge)
    sec = col if edge is None else pal["dark"]
    second = draw_hand([("line", (0, -180), (0, 800), 5, sec),
                        ("circle", (0, 0), 16, sec)])
    return hour, minute, second


# Vintage: 1950s champagne dress watch, applied gold batons, small seconds.

VINTAGE_SUB = (C, C + 390, 185)   # small seconds centre x, y and radius (SS)
GOLD_LIGHT = (238, 210, 138, 255)
GOLD_DARK = (146, 108, 40, 255)


def vintage_dial():
    img = radial_gradient((242, 231, 204), (204, 182, 136))
    d = ImageDraw.Draw(img)
    track = (130, 106, 62, 255)
    ring(d, 872, 3, track)
    for m in range(60):
        if m % 5:
            x, y = polar(852, m * 6)
            dot(d, x, y, 4, track)
    for h in range(12):
        if h == 6:
            continue  # small seconds
        offsets = (-24, 24) if h == 0 else (0,)
        for off in offsets:
            a = h * 30
            ox = off * math.cos(math.radians(a))
            oy = off * math.sin(math.radians(a))
            layer = canvas()
            ld = ImageDraw.Draw(layer)
            # Applied baton: dark bevel under a lighter face.
            tick(ld, 700, 825, a, 34, GOLD_DARK)
            tick(ld, 704, 821, a, 18, GOLD_LIGHT)
            img.alpha_composite(layer.transform(
                layer.size, Image.AFFINE, (1, 0, -ox, 0, 1, -oy), Image.BICUBIC))
    cx, cy, r = VINTAGE_SUB
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(222, 206, 168, 255))
    for k in range(r - 8, 20, -14):   # guilloché-style grooves
        d.ellipse([cx - k, cy - k, cx + k, cy + k], outline=(210, 192, 150, 255), width=2)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=track, width=3)
    for s in range(60):
        a = math.radians(s * 6)
        r1 = r - (26 if s % 5 == 0 else 14)
        d.line([(cx + r1 * math.sin(a), cy - r1 * math.cos(a)),
                (cx + (r - 4) * math.sin(a), cy - (r - 4) * math.cos(a))],
               fill=track, width=5 if s % 5 == 0 else 3)
    script = ImageFont.truetype(FONTS["serif_italic"], 62)
    text_at(img, "AJAR", script, C, C - 330, (110, 86, 44, 255))
    return img


def vintage_hands(pal, dial):
    edge = edge_for(pal["main"], dial, 4)
    hour = draw_hand(dauphine(pal, 420, 60, 46), outline=edge)
    minute = draw_hand(dauphine(pal, 740, 46, 56) + [("circle", (0, 0), 26, pal["light"])],
                       outline=edge)
    sec = pal["dark"]
    second = draw_hand([("line", (0, -36), (0, 168), 6, sec),
                        ("circle", (0, 0), 14, sec)])
    return hour, minute, second


# Field: silver-white dial, grey Arabic numerals, date at 3, steel hands.

FIELD_DATE = (C + 615, C - 55, C + 765, C + 55)


def field_dial():
    img = radial_gradient((247, 247, 245), (214, 215, 218))
    d = ImageDraw.Draw(img)
    grey = (128, 130, 136, 255)
    for m in range(60):
        if m % 5:
            x, y = polar(855, m * 6)
            dot(d, x, y, 5, grey)
        else:
            tick(d, 828, 876, m * 6, 12, grey)
    font = ImageFont.truetype(FONTS["sans_semibold"], 150)
    for i in range(1, 13):
        if i == 3:
            continue  # date window
        x, y = polar(690, i * 30)
        text_at(img, str(i), font, x, y, grey)
    x0, y0, x1, y1 = FIELD_DATE
    d.rounded_rectangle([x0 - 6, y0 - 6, x1 + 6, y1 + 6], 10, fill=(160, 162, 168, 255))
    d.rounded_rectangle([x0, y0, x1, y1], 6, fill=(252, 252, 252, 255))
    small = ImageFont.truetype(FONTS["sans_semibold"], 46)
    text_at(img, "AJAR", small, C, C - 300, (90, 90, 90, 255))
    return img


def field_hands(pal, dial):
    col = pal["main"]
    edge = edge_for(col, dial, 5)
    lume = (236, 236, 232, 255) if luminance(col) < 0.3 else (60, 62, 68, 255)
    hour = draw_hand([
        ("poly", [(-22, -60), (22, -60), (22, 380), (0, 440), (-22, 380)], col),
        ("line", (0, 70), (0, 360), 12, lume),
    ], outline=edge)
    minute = draw_hand([
        ("poly", [(-16, -80), (16, -80), (16, 700), (0, 770), (-16, 700)], col),
        ("line", (0, 90), (0, 680), 10, lume),
        ("circle", (0, 0), 28, col),
    ], outline=edge)
    sec = col if edge is None else pal["dark"]
    second = draw_hand([("line", (0, -190), (0, 800), 5, sec),
                        ("circle", (0, -150), 18, sec),
                        ("circle", (0, 0), 16, sec)])
    return hour, minute, second


# Chrono: black sports chronograph. Working sub-dials: running seconds at 9,
# 24-hour at 3 and a battery gauge at 6.

CHRONO_SUBS = {"seconds": (C - 400, C), "hours24": (C + 400, C), "battery": (C, C + 400)}
CHRONO_SUB_R = 195
BATTERY_SWEEP = 270   # degrees from empty to full


def chrono_subdial(img, center, labels, ticks, arc=None):
    d = ImageDraw.Draw(img)
    cx, cy = center
    r = CHRONO_SUB_R
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(30, 30, 33, 255),
              outline=(190, 190, 194, 255), width=4)
    for k in range(r - 10, 20, -12):
        d.ellipse([cx - k, cy - k, cx + k, cy + k], outline=(38, 38, 42, 255), width=2)
    for a, major in ticks:
        rad = math.radians(a)
        r1 = r - (34 if major else 20)
        d.line([(cx + r1 * math.sin(rad), cy - r1 * math.cos(rad)),
                (cx + (r - 8) * math.sin(rad), cy - (r - 8) * math.cos(rad))],
               fill=(230, 230, 230, 255), width=6 if major else 3)
    if arc:
        start, end, color = arc
        box = [cx - r + 10, cy - r + 10, cx + r - 10, cy + r - 10]
        d.arc(box, start - 90, end - 90, fill=color, width=10)
    font = ImageFont.truetype(FONTS["sans_semibold"], 50)
    for text, a in labels:
        rad = math.radians(a)
        text_at(img, text, font, cx + (r - 72) * math.sin(rad), cy - (r - 72) * math.cos(rad),
                (220, 220, 220, 255))


def chrono_dial():
    img = radial_gradient((42, 42, 44), (14, 14, 15))
    d = ImageDraw.Draw(img)
    white = (238, 238, 238, 255)
    for q in range(240):   # quarter-second scale
        a = q * 1.5
        if q % 20 == 0:
            tick(d, 800, 872, a, 12, white)
        elif q % 4 == 0:
            tick(d, 830, 872, a, 5, white)
        else:
            tick(d, 850, 872, a, 3, (170, 170, 170, 255))
    font = ImageFont.truetype(FONTS["sans_bold"], 120)
    for i in (12, 1, 2, 4, 5, 7, 8, 10, 11):
        x, y = polar(690, (i % 12) * 30)
        text_at(img, str(i), font, x, y, white)
    chrono_subdial(img, CHRONO_SUBS["seconds"],
                   [("60", 0), ("20", 120), ("40", 240)],
                   [(s * 6, s % 5 == 0) for s in range(60)])
    chrono_subdial(img, CHRONO_SUBS["hours24"],
                   [("24", 0), ("6", 90), ("12", 180), ("18", 270)],
                   [(h * 15, h % 6 == 0) for h in range(24)])
    half = BATTERY_SWEEP / 2
    chrono_subdial(img, CHRONO_SUBS["battery"],
                   [("E", -half + 20), ("F", half - 20)],
                   [(-half + p * BATTERY_SWEEP / 10, p % 5 == 0) for p in range(11)],
                   arc=(-half, -half + BATTERY_SWEEP * 0.2, RED))
    small = ImageFont.truetype(FONTS["sans_semibold"], 44)
    text_at(img, "AJAR", small, C, C - 280, (200, 200, 200, 255))
    return img


def chrono_hands(pal, dial):
    col = pal["main"]
    black = (10, 10, 10, 255)
    edge = edge_for(col, dial) or (black, 5)
    fuller = black if luminance(col) > 0.25 else (200, 200, 200, 255)
    hour = draw_hand([
        ("poly", [(-26, -60), (26, -60), (26, 400), (0, 450), (-26, 400)], col),
        ("line", (0, 60), (0, 380), 10, fuller),
    ], outline=edge)
    minute = draw_hand([
        ("poly", [(-20, -80), (20, -80), (20, 720), (0, 780), (-20, 720)], col),
        ("line", (0, 80), (0, 700), 9, fuller),
        ("circle", (0, 0), 30, col),
    ], outline=edge)
    second = draw_hand([("line", (0, -36), (0, 172), 7, WHITE),
                        ("circle", (0, 0), 16, WHITE)])
    return hour, minute, second


# Date window in place of the 9 o'clock marker.
DIVER_DATE = (C - 735, C - 50, C - 585, C + 50)


def diver_dial():
    img = radial_gradient((30, 32, 36), (8, 9, 11))
    d = ImageDraw.Draw(img)
    # Dive bezel: 60-minute scale, denser for the first 15 minutes.
    ring(d, 890, 120, (24, 25, 28, 255))
    ring(d, 770, 6, (90, 92, 98, 255))
    bezel = (225, 226, 230, 255)
    for m in range(60):
        a = m * 6
        if m == 0:
            continue
        if m % 5 == 0:
            if m % 15:
                tick(d, 800, 870, a, 12, bezel)
        elif m < 15:
            tick(d, 830, 870, a, 7, bezel)
        else:
            x, y = polar(850, a)
            dot(d, x, y, 7, bezel)
    d.polygon([(C - 48, C - 872), (C + 48, C - 872), (C, C - 790)], fill=bezel)
    font = ImageFont.truetype(FONTS["sans_bold"], 80)
    for m in (15, 30, 45):
        x, y = polar(832, m * 6)
        text_at(img, str(m), font, x, y, bezel, angle=m * 6)
    # Lume markers, framed in steel.
    lume = (236, 235, 226, 255)
    frame = SILVER_DARK
    # Baton at every hour, doubled at 12.
    for h in range(12):
        a = h * 30
        if h == 0:
            for ox in (-44, 44):
                d.rectangle([C + ox - 31, C - 735, C + ox + 31, C - 590], fill=frame)
                d.rectangle([C + ox - 23, C - 727, C + ox + 23, C - 598], fill=lume)
        elif h != 9:
            tick(d, 590, 735, a, 62, frame)
            tick(d, 598, 727, a, 46, lume)
    for m in range(60):
        if m % 5:
            tick(d, 715, 745, m * 6, 5, (180, 180, 180, 255))
    # Date window at 9 (the numeral is drawn by the watch face).
    x0, y0, x1, y1 = DIVER_DATE
    d.rounded_rectangle([x0 - 8, y0 - 8, x1 + 8, y1 + 8], 14, fill=frame)
    d.rounded_rectangle([x0, y0, x1, y1], 10, fill=(250, 250, 248, 255))
    small = ImageFont.truetype(FONTS["sans_semibold"], 46)
    text_at(img, "AJAR", small, C, C - 300, (225, 225, 225, 255))
    text_at(img, "200 m", small, C, C + 300, (196, 22, 42, 255))
    return img


def diver_hands(pal, dial):
    col = pal["main"]
    edge = edge_for(col, dial) or ((10, 10, 10, 255), 6)
    lume = (236, 235, 226, 255)
    hour = draw_hand([
        ("poly", [(-34, -70), (34, -70), (44, 330), (0, 470), (-44, 330)], col),
        ("poly", [(-18, 90), (18, 90), (26, 320), (0, 410), (-26, 320)], lume),
    ], outline=edge)
    minute = draw_hand([
        ("poly", [(-26, -90), (26, -90), (32, 640), (0, 760), (-32, 640)], col),
        ("poly", [(-12, 110), (12, 110), (18, 630), (0, 700), (-18, 630)], lume),
        ("circle", (0, 0), 38, col),
    ], outline=edge)
    second = draw_hand([("line", (0, -200), (0, 700), 8, RED),
                        ("poly", [(-26, 640), (26, 640), (0, 780)], RED),
                        ("poly", [(-12, 660), (12, 660), (0, 730)], lume),
                        ("circle", (0, 0), 24, RED)])
    return hour, minute, second


def sub_hand(color):
    return draw_hand([("poly", [(-8, -28), (8, -28), (5, 158), (-5, 158)], color),
                      ("circle", (0, 0), 16, color)])


# "Original" hand colours. WFF v1 allows at most 5 colours per option.
TINT_SLOTS = [
    palette((29, 56, 132), (58, 92, 178), (18, 36, 90)),         # blued steel
    palette((26, 26, 28), (78, 78, 84), (14, 14, 16)),           # black
    palette((236, 235, 226), (250, 250, 248), (190, 190, 184)),  # lume white
    palette((200, 203, 211), SILVER_LIGHT[:3], SILVER_DARK[:3]),  # silver
    palette((201, 162, 77), GOLD_LIGHT[:3], GOLD_DARK[:3]),      # gold
]


def style_slot(style):
    return TINT_SLOTS.index(style["tint"])


STYLES = [
    {"key": "heritage", "tint": TINT_SLOTS[0], "dial": heritage_dial, "dial_rgb": (240, 232, 216),
     "original": palette((29, 56, 132), (58, 92, 178), (18, 36, 90)),
     "hands": heritage_hands},
    {"key": "railroad", "tint": TINT_SLOTS[1], "dial": railroad_dial, "dial_rgb": (250, 250, 247),
     "original": palette((26, 26, 28), (78, 78, 84), (14, 14, 16)),
     "hands": railroad_hands},
    {"key": "pilot", "tint": TINT_SLOTS[2], "dial": pilot_dial, "dial_rgb": (26, 26, 28),
     "original": palette((236, 234, 220), (246, 246, 242), (200, 198, 186)),
     "hands": pilot_hands},
    {"key": "dress", "tint": TINT_SLOTS[3], "dial": dress_dial, "dial_rgb": (24, 38, 70),
     "original": palette((200, 203, 211), SILVER_LIGHT[:3], SILVER_DARK[:3]),
     "hands": dress_hands,
     "date": ((C + 610, C - 64, C + 790, C + 64), 24)},
    {"key": "quartz", "tint": TINT_SLOTS[1], "dial": quartz_dial, "dial_rgb": (251, 251, 250),
     "original": palette((26, 26, 28), (78, 78, 84), (14, 14, 16)),
     "hands": quartz_hands},
    {"key": "vintage", "tint": TINT_SLOTS[4], "dial": vintage_dial, "dial_rgb": (228, 212, 176),
     "original": palette((201, 162, 77), GOLD_LIGHT[:3], GOLD_DARK[:3]),
     "hands": vintage_hands,
     "small_seconds": VINTAGE_SUB},
    {"key": "field", "tint": TINT_SLOTS[1], "dial": field_dial, "dial_rgb": (236, 236, 236),
     "original": palette((70, 72, 78), (110, 112, 120), (40, 42, 46)),
     "hands": field_hands,
     "date": (FIELD_DATE, 20)},
    {"key": "chrono", "tint": TINT_SLOTS[2], "dial": chrono_dial, "dial_rgb": (32, 32, 34),
     "original": palette((236, 236, 236), (250, 250, 250), (170, 170, 170)),
     "hands": chrono_hands,
     "small_seconds": CHRONO_SUBS["seconds"] + (CHRONO_SUB_R,),
     # name, hand, centre (SS), rotation expression, angle shown in previews
     "indicators": [
         ("hours24", lambda: sub_hand(WHITE), CHRONO_SUBS["hours24"],
          "([HOUR_0_23] + [MINUTE] / 60) * 15", None),
         ("battery", lambda: sub_hand(WHITE), CHRONO_SUBS["battery"],
          f"[BATTERY_PERCENT] * {BATTERY_SWEEP / 100} - {BATTERY_SWEEP / 2}",
          -BATTERY_SWEEP / 2 + BATTERY_SWEEP * 0.75),
     ]},
    {"key": "diver", "tint": TINT_SLOTS[3], "dial": diver_dial, "dial_rgb": (20, 21, 24),
     "original": palette((200, 203, 211), SILVER_LIGHT[:3], SILVER_DARK[:3]),
     "hands": diver_hands,
     "date": (DIVER_DATE, 22)},
]

# Always ticks, to match the Classic Tick sound. (A per-style seconds option
# would need a configuration nested in the style list, which crashes
# Samsung's watch face editor.)
SECONDS_MOTION = '<Tick duration="0.1" strength="1.0"/>'


# ---------------------------------------------------------------- tinting
#
# Hand colour is a ColorConfiguration applied as a tint, so it needs no
# nested configuration. Each hand is split into a white base (tinted at
# runtime) and an untinted overlay carrying facets, fullers and outlines.

NEUTRAL = palette((160, 160, 160), (225, 225, 225), (95, 95, 95))
NEUTRAL_MAIN = 160


def split_hand(hand):
    """(base, overlay) images for a hand drawn in the NEUTRAL palette."""
    img = hand["img"]
    base = Image.new("RGBA", img.size, (255, 255, 255, 0))
    base.putalpha(img.getchannel("A"))
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src, dst = img.load(), overlay.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if not a:
                continue
            v = (r + g + b) // 3
            if v >= NEUTRAL_MAIN:
                dst[x, y] = (255, 255, 255, a * (v - NEUTRAL_MAIN) // (255 - NEUTRAL_MAIN))
            else:
                dst[x, y] = (0, 0, 0, a * (NEUTRAL_MAIN - v) // NEUTRAL_MAIN)
    return {**hand, "img": base}, {**hand, "img": overlay}


def tinted(hand, color):
    img = Image.new("RGBA", hand["img"].size, color[:3] + (0,))
    img.putalpha(hand["img"].getchannel("A"))
    return {**hand, "img": img}


def style_edge(dial_rgb):
    """Fixed hand outline per dial, since the tint colour isn't known here."""
    if luminance(dial_rgb) > 0.3:
        return ((28, 28, 30, 120), 5)
    return ((150, 150, 150, 170), 5)


def argb(c):
    return "#FF%02X%02X%02X" % c[:3]


# ---------------------------------------------------------------- ambient

# Always-on mode is a top-level editor setting (not nested in the style list,
# which crashes Samsung's editor). Both options hide the second hand.
#  - Full colour: the same face under this black layer. One layer on top dims
#    every colour evenly; lowering each part's alpha instead would let the dial
#    show through the hands.
#  - Minimal: an opaque black face with grey indices and slim light hands. Few
#    lit pixels, which is easier on the battery and the screen, and what Play's
#    quality guidelines prefer.
AMBIENT_DIM = "#66000000"   # 40% black
AOD_OPTIONS = ["full", "minimal"]   # list position = option id; 0 is the default


def ambient():
    """Minimal always-on face: black, sparse grey indices, slim light hands."""
    img = canvas((0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    grey = (150, 150, 150, 255)
    for h in range(12):
        if h == 0:
            tick(d, 740, 850, -2.2, 12, grey)
            tick(d, 740, 850, 2.2, 12, grey)
        else:
            tick(d, 760, 850, h * 30, 12, grey)
    white = (225, 225, 225, 255)
    hour = draw_hand([("poly", [(-14, -40), (14, -40), (10, 450), (-10, 450)], white)])
    minute = draw_hand([("poly", [(-10, -50), (10, -50), (7, 740), (-7, 740)], white),
                        ("circle", (0, 0), 20, white)])
    return img, hour, minute


# ---------------------------------------------------------------- output

def angles(h, m, s):
    return (h % 12 + m / 60) * 30, (m + s / 60) * 6, s * 6


def render_preview(dial, hands):
    """hands: (hand, angle, centre in SS px or None for the dial centre)."""
    img = dial.copy()
    for hand, angle, center in hands:
        compose_hand(img, hand, angle, center or (C, C))
    return circle_mask(downsample(img))


def xml_hand(tag, a, extra=""):
    attrs = (f'resource="{a["resource"]}" x="{a["x"]}" y="{a["y"]}" '
             f'width="{a["width"]}" height="{a["height"]}" pivotX="0.5" '
             f'pivotY="{a["pivotY"]}"')
    return f"<{tag} {attrs}>{extra}</{tag}>" if extra else f"<{tag} {attrs}/>"


def indent(text, spaces):
    pad = " " * spaces
    return "\n".join(pad + line if line else line for line in text.split("\n"))


def seconds_box(style):
    """AnalogClock bounds (450px space) for the seconds hand."""
    if "small_seconds" not in style:
        return 0, 0, SIZE
    cx, cy, r = style["small_seconds"]
    size = (2 * r) // SS + 2
    return cx // SS - size // 2, cy // SS - size // 2, size


def date_xml(style):
    (x0, y0, x1, y1), size = style["date"]
    return f"""
<PartText x="{x0 // SS}" y="{y0 // SS}" width="{(x1 - x0) // SS}" height="{(y1 - y0) // SS}">
  <Text align="CENTER">
    <Font family="SYNC_TO_DEVICE" size="{size}" weight="BOLD" color="#FF1B1B1B">
      <Template>%s<Parameter expression="[DAY]"/></Template>
    </Font>
  </Text>
</PartText>"""


def indicator_xml(key, name, hand, center, expr):
    a = hand_asset(f"hand_{key}_{name}", hand, (center[0] // SS, center[1] // SS))
    return f"""
<PartImage x="{a["x"]}" y="{a["y"]}" width="{a["width"]}" height="{a["height"]}" pivotX="0.5" pivotY="{a["pivotY"]}">
  <Transform target="angle" value="{expr}"/>
  <Image resource="{a["resource"]}"/>
</PartImage>"""


def style_scene(idx, style):
    """Everything drawn for one style, and its editor preview."""
    global FORCE_EDGE
    key = style["key"]
    dial = style["dial"]()
    save(circle_mask(downsample(dial)), f"dial_{key}")
    ha, ma, sa = angles(*PREVIEW_TIME)

    extras = ""
    preview_hands = []
    for name, make, center, expr, preview_angle in style.get("indicators", []):
        hand = make()
        extras += indicator_xml(key, name, hand, center, expr)
        if preview_angle is None:   # 24-hour hand
            preview_angle = (PREVIEW_TIME[0] + PREVIEW_TIME[1] / 60) * 15
        preview_hands.append((hand, preview_angle, center))
    if "date" in style:
        extras += date_xml(style)

    # Hour/minute: neutral render split into tinted base + fixed overlay.
    FORCE_EDGE = style_edge(style["dial_rgb"])
    hour, minute, _ = style["hands"](NEUTRAL, style["dial_rgb"])
    FORCE_EDGE = None
    hour_base, hour_over = split_hand(hour)
    min_base, min_over = split_hand(minute)
    # Seconds hand keeps the style's own colour, untinted.
    _, _, second = style["hands"](style["original"], style["dial_rgb"])

    tint = TINT_SLOTS[style_slot(style)]["main"]
    preview_hands += [(tinted(hour_base, tint), ha, None), (hour_over, ha, None),
                      (tinted(min_base, tint), ma, None), (min_over, ma, None),
                      (second, sa, style["small_seconds"][:2] if "small_seconds" in style else None)]
    preview = render_preview(dial, preview_hands)
    no_seconds = render_preview(dial, preview_hands[:-1])   # seconds hand is last
    save(preview.resize((180, 180), Image.LANCZOS), f"icon_{key}")

    hb = hand_asset(f"hand_{key}_hour", hour_base)
    ho = hand_asset(f"hand_{key}_hour_shade", hour_over)
    mb = hand_asset(f"hand_{key}_minute", min_base)
    mo = hand_asset(f"hand_{key}_minute_shade", min_over)
    bx, by, bsize = seconds_box(style)
    s = hand_asset(f"hand_{key}_second", second, (bsize // 2, bsize // 2))

    xml = f"""<ListOption id="{idx}">
  <Group name="{key}" x="0" y="0" width="{SIZE}" height="{SIZE}">
    <PartImage x="0" y="0" width="{SIZE}" height="{SIZE}">
      <Image resource="dial_{key}"/>
    </PartImage>{indent(extras, 4)}
    <AnalogClock x="0" y="0" width="{SIZE}" height="{SIZE}" tintColor="[CONFIGURATION.hands.{style_slot(style)}]">
      {xml_hand("HourHand", hb)}
      {xml_hand("MinuteHand", mb)}
    </AnalogClock>
    <AnalogClock x="0" y="0" width="{SIZE}" height="{SIZE}">
      {xml_hand("HourHand", ho)}
      {xml_hand("MinuteHand", mo)}
    </AnalogClock>
    <Group name="{key}_seconds" x="0" y="0" width="{SIZE}" height="{SIZE}">
      <Variant mode="AMBIENT" target="alpha" value="0"/>
      <AnalogClock x="{bx}" y="{by}" width="{bsize}" height="{bsize}">
        {xml_hand("SecondHand", s, SECONDS_MOTION)}
      </AnalogClock>
    </Group>
  </Group>
</ListOption>"""
    return xml, preview, no_seconds


def main():
    os.makedirs(DRAWABLE, exist_ok=True)
    for f in os.listdir(DRAWABLE):
        if f.startswith(("hand_", "icon_", "dial_")):
            os.remove(os.path.join(DRAWABLE, f))

    style_options, scenes = [], []
    first_preview = first_no_seconds = None
    for idx, style in enumerate(STYLES):
        xml, preview, no_seconds = style_scene(idx, style)
        scenes.append(xml)
        gallery = os.path.join(PHONE_RES, "drawable-nodpi")
        os.makedirs(gallery, exist_ok=True)
        preview.resize((360, 360), Image.LANCZOS).save(
            os.path.join(gallery, f"gallery_{style['key']}.png"), optimize=True)
        first_preview = first_preview or preview
        first_no_seconds = first_no_seconds or no_seconds
        key = style["key"]
        style_options.append(
            f'<ListOption id="{idx}" displayName="style_{key}" '
            f'screenReaderText="style_{key}" icon="icon_{key}"/>')

    # Each colour option lists one tint per slot (max 5 in WFF v1); styles
    # sharing an original colour share a slot.
    color_options = []
    for cid, (color, pal) in enumerate(HAND_COLORS):
        tints = [(pal or slot)["main"] for slot in TINT_SLOTS]
        color_options.append(
            f'<ColorOption id="{cid}" displayName="hands_{color}" '
            f'screenReaderText="hands_{color}" colors="{" ".join(argb(t) for t in tints)}"/>')

    # Always-on: minimal face assets, plus an editor icon for each option.
    amb_dial, amb_hour, amb_min = ambient()
    save(circle_mask(downsample(amb_dial)), "dial_ambient")
    ah = hand_asset("hand_ambient_hour", amb_hour)
    am = hand_asset("hand_ambient_minute", amb_min)
    ha, ma, _ = angles(*PREVIEW_TIME)
    minimal_icon = render_preview(amb_dial, [(amb_hour, ha, None), (amb_min, ma, None)])
    dim = Image.new("RGBA", first_no_seconds.size, (0, 0, 0, int(AMBIENT_DIM[1:3], 16)))
    full_icon = Image.alpha_composite(first_no_seconds.convert("RGBA"), dim)
    for name, icon in (("full", full_icon), ("minimal", minimal_icon)):
        save(icon.resize((180, 180), Image.LANCZOS), f"icon_aod_{name}")
    aod_options = [
        f'<ListOption id="{i}" displayName="aod_{name}" '
        f'screenReaderText="aod_{name}" icon="icon_aod_{name}"/>'
        for i, name in enumerate(AOD_OPTIONS)]

    save(first_preview, "preview")
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                        ("xxhdpi", 144), ("xxxhdpi", 192)]:
        for res in (RES, PHONE_RES):
            folder = os.path.join(res, f"mipmap-{density}")
            os.makedirs(folder, exist_ok=True)
            first_preview.resize((px, px), Image.LANCZOS).save(
                os.path.join(folder, "ic_launcher.png"), optimize=True)

    hh, mm, sec = PREVIEW_TIME
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_assets.py. Edit the script, not this file. -->
<WatchFace width="{SIZE}" height="{SIZE}" clipShape="CIRCLE">
  <Metadata key="CLOCK_TYPE" value="ANALOG"/>
  <Metadata key="PREVIEW_TIME" value="{hh:02d}:{mm:02d}:{sec:02d}"/>
  <UserConfigurations>
    <ListConfiguration id="style" displayName="style_label" screenReaderText="style_label" defaultValue="0">
{indent(chr(10).join(style_options), 6)}
    </ListConfiguration>
    <ColorConfiguration id="hands" displayName="hands_label" screenReaderText="hands_label" defaultValue="0">
{indent(chr(10).join(color_options), 6)}
    </ColorConfiguration>
    <ListConfiguration id="aod" displayName="aod_label" screenReaderText="aod_label" defaultValue="0">
{indent(chr(10).join(aod_options), 6)}
    </ListConfiguration>
  </UserConfigurations>
  <Scene backgroundColor="#FF000000">
    <!-- Keep configurations un-nested: Samsung's editor crashes on nesting. -->
    <ListConfiguration id="style">
{indent(chr(10).join(scenes), 6)}
    </ListConfiguration>
    <ListConfiguration id="aod">
      <ListOption id="{AOD_OPTIONS.index("full")}">
        <Group name="ambient_dim" x="0" y="0" width="{SIZE}" height="{SIZE}" alpha="0">
          <Variant mode="AMBIENT" target="alpha" value="255"/>
          <PartDraw x="0" y="0" width="{SIZE}" height="{SIZE}">
            <Rectangle x="0" y="0" width="{SIZE}" height="{SIZE}">
              <Fill color="{AMBIENT_DIM}"/>
            </Rectangle>
          </PartDraw>
        </Group>
      </ListOption>
      <ListOption id="{AOD_OPTIONS.index("minimal")}">
        <!-- Opaque, so it fully covers the styled face underneath. -->
        <Group name="ambient_minimal" x="0" y="0" width="{SIZE}" height="{SIZE}" alpha="0">
          <Variant mode="AMBIENT" target="alpha" value="255"/>
          <PartDraw x="0" y="0" width="{SIZE}" height="{SIZE}">
            <Rectangle x="0" y="0" width="{SIZE}" height="{SIZE}">
              <Fill color="#FF000000"/>
            </Rectangle>
          </PartDraw>
          <PartImage x="0" y="0" width="{SIZE}" height="{SIZE}">
            <Image resource="dial_ambient"/>
          </PartImage>
          <AnalogClock x="0" y="0" width="{SIZE}" height="{SIZE}">
            {xml_hand("HourHand", ah)}
            {xml_hand("MinuteHand", am)}
          </AnalogClock>
        </Group>
      </ListOption>
    </ListConfiguration>
  </Scene>
</WatchFace>
"""
    with open(os.path.join(RES, "raw", "watchface.xml"), "w") as f:
        f.write(xml)
    print(f"Generated {len(STYLES)} styles, {len(HAND_COLORS)} hand colours")


if __name__ == "__main__":
    main()
