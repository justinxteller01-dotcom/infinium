#!/usr/bin/env python3
"""R5 guide art that does not need a GPU.

Settings page: four vertical strips, 64 px wide, 8 frames of 64 px (strip is 64x512).
The book screen blits one frame every 120 ms. Labels are drawn by InfiniumGuideScreen, not baked in.

  settings_particles_on.png   Singularity-colored specks
  settings_particles_off.png  same figure, no specks
  settings_wings_on.png       back view, wings out
  settings_wings_off.png      back view, wings hidden

Armor pages keep the existing 128 px x 16 frame strips (astral.png and the rest).
Those come from the still-yaw GIF pipeline (render_gifs.py holds yaw fixed and animates
the texture and particles; it does not bob the model). Regenerate them on Windows with
the command below, then copy a 128-wide column of 16 frames into
src/main/resources/assets/infinium/textures/gui/guide/<set>.png if you replace the strips.
This script does not overwrite those armor strips.

PowerShell, from the mod folder:

    python render_r5_guide.py
"""
import math
import os
from PIL import Image

OUT = "src/main/resources/assets/infinium/textures/gui/guide"
W, F, N = 64, 64, 8


def blank():
    return Image.new("RGBA", (W, F * N), (14, 6, 18, 255))


def frame(img, i):
    return img.crop((0, i * F, W, (i + 1) * F))


def put(img, i, px):
    base = frame(img, i)
    base.paste(px, (0, 0), px)
    img.paste(base, (0, i * F))


def body(draw_wings, particles, i):
    im = Image.new("RGBA", (W, F), (0, 0, 0, 0))
    p = im.load()
    # torso
    for y in range(16, 48):
        for x in range(24, 40):
            p[x, y] = (90, 40, 140, 255)
    # head
    for y in range(6, 16):
        for x in range(27, 37):
            p[x, y] = (120, 60, 170, 255)
    if draw_wings:
        span = 14
        for y in range(18, 40):
            for x in range(8, 24):
                p[x, y] = (70, 24, 110, 230)
            for x in range(40, 56):
                p[x, y] = (70, 24, 110, 230)
        # span used so a linter-less reader sees the wings are a fixed size (no bob)
        _ = span
    if particles:
        for k in range(6):
            a = (i / N + k / 6) * math.tau
            x = int(32 + math.cos(a) * (16 + k))
            y = int(30 + math.sin(a * 0.7) * 14)
            if 0 <= x < W and 0 <= y < F:
                p[x, y] = (196, 80, 220, 255)
    return im


def build(wings, particles):
    img = blank()
    for i in range(N):
        img.paste(body(wings, particles, i), (0, i * F))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        "settings_particles_on": build(False, True),
        "settings_particles_off": build(False, False),
        "settings_wings_on": build(True, False),
        "settings_wings_off": build(False, False),
    }
    for name, im in files.items():
        path = os.path.join(OUT, name + ".png")
        im.save(path)
        print("wrote", path, im.size)
    print("armor strips were not touched")


if __name__ == "__main__":
    main()
