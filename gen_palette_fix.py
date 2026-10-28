# Final palette pass (Infinium colour rule, 2026-10-28): every asset PNG may only use purple / pink / magenta /
# black (+ neutral shades). Runs LAST in build.ps1 so any generator output is forced compliant. Idempotent.
# Skips LabPBR data maps (*_n.png / *_s.png): their channels encode normals/specular, not colour.
# Player dye tints are applied at runtime (grayscale *_gray / dye layers stay neutral and are untouched).
import glob, sys, numpy as np
from PIL import Image
import palette_guard as pg

changed = 0
for f in sorted(glob.glob('src/main/resources/assets/**/*.png', recursive=True)):
    if f.endswith(('_n.png', '_s.png')): continue
    im = Image.open(f); mode = im.mode
    a = np.asarray(im.convert('RGBA'))
    if not pg.banned_mask(a).any(): continue
    b = pg.fix_array(a)
    out = Image.fromarray(b, 'RGBA')
    if mode in ('RGB', 'L', 'P'): out = out.convert('RGB') if mode != 'P' else out.convert('RGBA')
    out.save(f); changed += 1
    if '-v' in sys.argv: print('fixed', f)
print(f'gen_palette_fix: {changed} files recoloured')
