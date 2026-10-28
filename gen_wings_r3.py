# batch1 (2026-10-28): round-3 option-4 elytra textures for Cybernetic and Cyber Psycho, sliced into the mod's
# per-frame glow system. Sources: ref/wings_r3/<set>/option4_anim(_glow).png (copied from the locked round-3
# sheet, 64x32 per frame, vanilla elytra UV layout: wing box uv(22,0) 10x20x2, front face x24..34 / back face
# x36..46 which the design script stores horizontally FLIPPED, so the vanilla mirrored right wing reads correctly).
# Colors pass through palette_guard (no cyan/gold/red-orange; Cybernetic's cyan tip lights become pink).
# Output: textures/entity/infinium_wings/r3/<set>/{base,glow,glow_gray}/N.png + border.png (thrust trim mask).
import os
import numpy as np
from PIL import Image
import palette_guard as pg

SRC = 'ref/wings_r3/'
OUT = 'src/main/resources/assets/infinium/textures/entity/infinium_wings/r3/'
SETS = {'cybernetic': 'cybernetic'}   # source dir -> mod style id (Cyber Psycho moved to gen_wings_r7_cyberpsycho.py)

def frames(path):
    a = np.array(Image.open(path).convert('RGBA'))
    return [a[i * 32:(i + 1) * 32] for i in range(a.shape[0] // 32)]

for src, sid in SETS.items():
    base = frames(SRC + src + '/option4_anim.png')
    glow = frames(SRC + src + '/option4_anim_glow.png')
    for d in ('base', 'glow', 'glow_gray'): os.makedirs(OUT + sid + '/' + d, exist_ok=True)
    for i, (b, g) in enumerate(zip(base, glow)):
        b = pg.fix_array(b); g = pg.fix_array(g)
        Image.fromarray(b).save(OUT + f'{sid}/base/{i}.png')
        Image.fromarray(g).save(OUT + f'{sid}/glow/{i}.png')
        lum = (g[..., :3].astype(np.float32) @ np.array([0.30, 0.59, 0.11])).clip(0, 255)
        lum = np.clip(lum * 1.6, 0, 255).astype(np.uint8)                       # brighten so dye tints read
        gg = np.dstack([lum, lum, lum, g[..., 3]])
        Image.fromarray(gg).save(OUT + f'{sid}/glow_gray/{i}.png')
    # border trim: opaque wing pixels with a transparent 4-neighbour (both faces), white, tinted by dye at runtime
    a = base[0][..., 3] > 0
    pad = np.pad(a, 1)
    edge = a & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    m = np.zeros((32, 64, 4), np.uint8); m[edge] = (255, 255, 255, 255)
    Image.fromarray(m).save(OUT + f'{sid}/border.png')
    print(f'gen_wings_r3: {sid} {len(base)} frames')
