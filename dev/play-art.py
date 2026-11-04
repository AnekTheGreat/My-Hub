#!/usr/bin/env python3
"""Render the hub's two Play Store graphics from its own launcher icon.

Play asks for a 512x512 icon and a 1024x500 feature graphic. The launcher mark is a vector drawable
on a 108-unit viewport, so the same shapes are redrawn here at store size with the same colours the
app ships. Pillow is the only dependency:

    python3 dev/play-art.py

writes `play/icon-512.png` and `play/feature-graphic.png`. Point FONTS somewhere with DejaVu if the
path below is not on this machine; any sans face will do, the placement is what matters.
"""
import os
from PIL import Image, ImageDraw, ImageFont

BG, NODE, CENTRE, INK, MUTE = "#0C1A2B", "#8FB6E0", "#EB1B2E", "#F2F6FB", "#8FA3B8"
FONTS = "/usr/share/fonts/truetype/dejavu"


def icon(size=512):
    """The launcher icon: three apps wired into one centre."""
    s = size / 108.0
    im = Image.new("RGB", (size, size), BG)
    d = ImageDraw.Draw(im, "RGBA")
    centre = (54 * s, 57 * s)
    ends = [(54 * s, 31 * s), (31.5 * s, 70 * s), (76.5 * s, 70 * s)]
    for p in ends:
        d.line([centre, p], fill=(255, 255, 255, 178), width=max(1, round(4 * s)))
    for p in ends:
        r = 7 * s
        d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=NODE)
    r = 8 * s
    d.ellipse([centre[0] - r, centre[1] - r, centre[0] + r, centre[1] + r], fill=CENTRE)
    return im


def feature(mark, out):
    im = Image.new("RGB", (1024, 500), BG)
    tile = mark.resize((230, 230), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", tile.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 229, 229], radius=52, fill=255)
    im.paste(tile, (78, 135), mask)
    d = ImageDraw.Draw(im)
    d.text((350, 152), "My Hub", fill=INK,
           font=ImageFont.truetype(os.path.join(FONTS, "DejaVuSans-Bold.ttf"), 76))
    d.text((352, 254), "One screen for the apps you open every day", fill=MUTE,
           font=ImageFont.truetype(os.path.join(FONTS, "DejaVuSans.ttf"), 27))
    im.save(out, "PNG", optimize=True)


if __name__ == "__main__":
    os.makedirs("play", exist_ok=True)
    mark = icon()
    mark.save("play/icon-512.png", "PNG", optimize=True)
    feature(mark, "play/feature-graphic.png")
    print("wrote play/icon-512.png and play/feature-graphic.png")
