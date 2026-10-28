# batch1 (2026-10-28): Cyber Psycho elytra = round-7 variant A "armor_hue" (Justin's final pick): angular plates on the
# option-4 base, plum base #2e102e, seams/edge in the armor crack purple #a21aa3, glow synced to the armor glow
# (80 frames x 100 ms, wing frame k = armor frame k). Replaces the r3 option-4 Cyber Psycho frames.
# Sources: ref/wings_r7/cyberpsycho/{armor_hue_anim,armor_hue_anim_glow}.png, exported by
# /workspace/elytra_designs/round7_cyberpsycho/export_mod.py (64x32 per frame, vanilla elytra UV).
# Output: textures/entity/infinium_wings/r7/cyberpsycho/{base,glow,glow_gray}/N.png + border.png (thrust trim mask).
import os
import numpy as np
from PIL import Image
import palette_guard as pg

SRC = 'ref/wings_r7/cyberpsycho/'
OUT = 'src/main/resources/assets/infinium/textures/entity/infinium_wings/r7/cyberpsycho/'

def frames(path):
    a = np.array(Image.open(path).convert('RGBA'))
    return [a[i * 32:(i + 1) * 32] for i in range(a.shape[0] // 32)]

base = frames(SRC + 'armor_hue_anim.png')
glow = frames(SRC + 'armor_hue_anim_glow.png')
for d in ('base', 'glow', 'glow_gray'): os.makedirs(OUT + d, exist_ok=True)
for i, (b, g) in enumerate(zip(base, glow)):
    b = pg.fix_array(b); g = pg.fix_array(g)
    Image.fromarray(b).save(OUT + f'base/{i}.png')
    Image.fromarray(g).save(OUT + f'glow/{i}.png')
    lum = (g[..., :3].astype(np.float32) @ np.array([0.30, 0.59, 0.11])).clip(0, 255)
    lum = np.clip(lum * 1.6, 0, 255).astype(np.uint8)
    Image.fromarray(np.dstack([lum, lum, lum, g[..., 3]])).save(OUT + f'glow_gray/{i}.png')
a = base[0][..., 3] > 0
pad = np.pad(a, 1)
edge = a & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
m = np.zeros((32, 64, 4), np.uint8); m[edge] = (255, 255, 255, 255)
Image.fromarray(m).save(OUT + 'border.png')
print(f'gen_wings_r7_cyberpsycho: {len(base)} frames')
