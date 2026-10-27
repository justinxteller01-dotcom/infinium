"""R2: Infinium Rocket uses an Infinium-style animated glow: 16-frame strip, the purple/magenta pixels pulse with a wave
running up the rocket. Only saturated house-palette pixels change (no blue/cyan, no pale). Writes the .mcmeta."""
import colorsys, json
from PIL import Image
P = 'src/main/resources/assets/infinium/textures/item/infinium_rocket.png'
src = Image.open(P).convert('RGBA')
if src.size[1] != 16: src = src.crop((0, 0, 16, 16))      # idempotent: re-run on an existing strip
N = 16
out = Image.new('RGBA', (16, 16 * N))
for f in range(N):
    fr = src.copy(); px = fr.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.45 or not (265 / 360 <= h <= 335 / 360): continue
            import math
            k = 0.5 + 0.5 * math.sin(2 * math.pi * (f / N + (15 - y) / 16))
            v2 = min(0.95, v * (0.72 + 0.4 * k))
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v2)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    out.paste(fr, (0, 16 * f))
out.save(P)
json.dump({"animation": {"frametime": 2, "interpolate": True}}, open(P + '.mcmeta', 'w'), indent=2)
print('gen_rocket_anim: 16 frames')
