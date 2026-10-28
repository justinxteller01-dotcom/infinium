# batch1 (2026-10-28): Infinity elytra = round-5 "Event Horizon" base + variant A1 "echo_outline" (Justin's pick).
# Sources: ref/wings_r5/infinity/{eventhorizon_anim,eventhorizon_anim_glow,echo_anim}.png, exported by
# /workspace/elytra_designs/round5_infinity/export_mod.py (64x32 per frame, vanilla elytra UV, 100 frames).
# echo = dotted copy of the outer wing edge with the twinkle baked per frame; the renderer draws it on a slightly
# larger, offset elytra pass so it floats just off the wing.
# Output: textures/entity/infinium_wings/r5/infinity/{base,glow,glow_gray,echo}/N.png + border.png (thrust trim mask).
import os
import numpy as np
from PIL import Image
import palette_guard as pg

SRC = 'ref/wings_r5/infinity/'
OUT = 'src/main/resources/assets/infinium/textures/entity/infinium_wings/r5/infinity/'

def frames(path):
    a = np.array(Image.open(path).convert('RGBA'))
    return [a[i * 32:(i + 1) * 32] for i in range(a.shape[0] // 32)]

base = frames(SRC + 'eventhorizon_anim.png')
glow = frames(SRC + 'eventhorizon_anim_glow.png')
echo = frames(SRC + 'echo_anim.png')
for d in ('base', 'glow', 'glow_gray', 'echo'): os.makedirs(OUT + d, exist_ok=True)
for i, (b, g, e) in enumerate(zip(base, glow, echo)):
    b = pg.fix_array(b); g = pg.fix_array(g); e = pg.fix_array(e)
    Image.fromarray(b).save(OUT + f'base/{i}.png')
    Image.fromarray(g).save(OUT + f'glow/{i}.png')
    Image.fromarray(e).save(OUT + f'echo/{i}.png')
    lum = (g[..., :3].astype(np.float32) @ np.array([0.30, 0.59, 0.11])).clip(0, 255)
    lum = np.clip(lum * 1.6, 0, 255).astype(np.uint8)
    Image.fromarray(np.dstack([lum, lum, lum, g[..., 3]])).save(OUT + f'glow_gray/{i}.png')
a = base[0][..., 3] > 0
pad = np.pad(a, 1)
edge = a & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
m = np.zeros((32, 64, 4), np.uint8); m[edge] = (255, 255, 255, 255)
Image.fromarray(m).save(OUT + 'border.png')
print(f'gen_wings_r5_infinity: {len(base)} frames')
