#!/usr/bin/env python3
"""Informational Crystal icon, spread background, and 8-frame set strips. No blue/cyan, no pale pixels.

Preview GIFs are not in this tree, so each set gets a flat animated plate in the
house palette (hue 265-335, near-black #0E0612). The guide also draws the real
armor items, which stay correct even without a 3D render.
"""
import struct
import zlib
from pathlib import Path

import colorsys

ROOT = Path(__file__).resolve().parent
TEX = ROOT / "src/main/resources/assets/infinium/textures"
FRAMES = 8
FRAME = 64

# Hand-checked: hue outside 200-262, and not (V>0.70 and S<0.45).
PAL = {
    "bg": (14, 6, 18, 255),
    "edge": (58, 24, 48, 255),
    "astral": (168, 64, 196, 255),
    "cybernetic": (196, 58, 122, 255),
    "singularity": (140, 48, 196, 255),
    "cyberpsycho": (196, 32, 72, 255),
    "infinity": (176, 48, 112, 255),
    "gem": (120, 36, 96, 255),
    "crystal_outline": (42, 12, 52, 255),
    "crystal_dark": (84, 22, 104, 255),
    "crystal_mid": (148, 42, 178, 255),
    "crystal_bright": (208, 58, 224, 255),
    "crystal_core": (236, 76, 188, 255),
    "spark": (244, 92, 212, 255),
}


def bad(r, g, b, a):
    if a == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    hue = h * 360
    if 200 <= hue <= 262 and s >= 0.15:
        return True
    if v > 0.70 and s < 0.45:
        return True
    return False


def check(buf, w, h):
    for i in range(0, w * h * 4, 4):
        if bad(*buf[i:i + 4]):
            raise SystemExit(f"palette reject rgba={tuple(buf[i:i+4])}")


def png(path: Path, w, h, rgba: bytearray):
    check(rgba, w, h)
    path.parent.mkdir(parents=True, exist_ok=True)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + rgba[y * w * 4:(y + 1) * w * 4] for y in range(h))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(data)


def fill(buf, w, x0, y0, x1, y1, c):
    for y in range(max(0, y0), min(len(buf) // (w * 4), y1)):
        for x in range(max(0, x0), min(w, x1)):
            i = (y * w + x) * 4
            buf[i:i + 4] = bytes(c)


def set_px(buf, w, x, y, c):
    h = len(buf) // (w * 4)
    if 0 <= x < w and 0 <= y < h:
        i = (y * w + x) * 4
        buf[i:i + 4] = bytes(c)


def icon():
    """8-frame 16x16 Informational Crystal strip (faceted cosmic crystal prism with orbiting energy specks)."""
    w, h = 16, 16 * FRAMES
    buf = bytearray([0, 0, 0, 0] * (w * h))
    # Row widths for a sleek diamond-hexagonal crystal prism (y = 1..14)
    prism_rows = [
        (1, 7, 9),
        (2, 6, 10),
        (3, 5, 11),
        (4, 4, 12),
        (5, 4, 12),
        (6, 3, 13),
        (7, 3, 13),
        (8, 3, 13),
        (9, 3, 13),
        (10, 4, 12),
        (11, 4, 12),
        (12, 5, 11),
        (13, 6, 10),
        (14, 7, 9),
    ]
    orbit_pts = [
        (2, 4), (13, 5), (13, 10), (2, 11),
        (4, 2), (11, 2), (11, 13), (4, 13)
    ]
    for f in range(FRAMES):
        y0 = f * 16
        for ry, x0, x1 in prism_rows:
            for x in range(x0, x1):
                if x == x0 or x == x1 - 1 or ry in (1, 14):
                    col = PAL["crystal_outline"]
                elif x == x0 + 1 or ry in (2, 13):
                    col = PAL["crystal_dark"]
                elif x < 8:
                    col = PAL["crystal_bright"]
                else:
                    col = PAL["crystal_mid"]
                set_px(buf, w, x, y0 + ry, col)
        # Animated inner core shimmer
        cy = y0 + 5 + (f % 4)
        fill(buf, w, 7, cy, 9, cy + 3, PAL["crystal_core"])
        set_px(buf, w, 6, y0 + 4 + ((f + 2) % 5), PAL["spark"])
        # Orbiting cosmic energy specks around the crystal
        ox1, oy1 = orbit_pts[f % len(orbit_pts)]
        ox2, oy2 = orbit_pts[(f + 4) % len(orbit_pts)]
        set_px(buf, w, ox1, y0 + oy1, PAL["spark"])
        set_px(buf, w, ox2, y0 + oy2, PAL["crystal_bright"])
    png(TEX / "item/infinium_guide.png", w, h, buf)


def book():
    w, h = 292, 196
    buf = bytearray(PAL["bg"] * (w * h))
    fill(buf, w, 0, 0, w, 4, PAL["edge"])
    fill(buf, w, 0, h - 4, w, h, PAL["edge"])
    fill(buf, w, 0, 0, 4, h, PAL["edge"])
    fill(buf, w, w - 4, 0, w, h, PAL["edge"])
    fill(buf, w, 145, 8, 147, h - 28, PAL["gem"])
    png(TEX / "gui/guide/book.png", w, h, buf)


def strip(name, color):
    w, h = FRAME, FRAME * FRAMES
    buf = bytearray(PAL["bg"] * (w * h))
    for f in range(FRAMES):
        y0 = f * FRAME
        inset = 8 + (f % 4)
        fill(buf, w, inset, y0 + inset, FRAME - inset, y0 + FRAME - inset, PAL["edge"])
        fill(buf, w, inset + 6, y0 + 16, FRAME - inset - 6, y0 + 28, color)
        fill(buf, w, 20, y0 + 30, 28, y0 + 50, color)  # helm
        fill(buf, w, 16, y0 + 34, 48, y0 + 48, PAL["edge"])  # shoulders
        gem_y = y0 + 20 + f
        fill(buf, w, 30, gem_y, 34, gem_y + 4, PAL["gem"])
    png(TEX / f"gui/guide/{name}.png", w, h, buf)


def main():
    for c in PAL.values():
        if bad(*c):
            raise SystemExit(f"palette entry failed {c}")
    icon()
    book()
    print("gen_guide_art: informational crystal icon + book pngs, palette ok")


if __name__ == "__main__":
    main()
