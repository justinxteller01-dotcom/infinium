# labPBR specular maps for the animated glow overlays: for every frame <n>.png under
# textures/entity/infinium_glow{,_gray}/** writes <n>_s.png with A=254 where the frame's alpha > 0, else 0 (R=G=B=0).
# Output goes to specular_out/ (copied into the jar by build.ps1), NOT into src/, so white_fix/hue_fix/loop_check never see it.
import glob, os, shutil
import numpy as np
from PIL import Image
R = 'src/main/resources/'
OUT = 'specular_out/'
if os.path.isdir(OUT): shutil.rmtree(OUT)
for stray in glob.glob(R + 'assets/infinium/textures/entity/infinium_glow*/**/*_s.png', recursive=True): os.remove(stray)
n = 0
for d in ('infinium_glow', 'infinium_glow_gray', 'infinium_helmet_extra/glow*'):
    for p in glob.glob(R + f'assets/infinium/textures/entity/{d}/**/*.png', recursive=True):
        if p.endswith('_s.png') or p.endswith('_n.png'): continue
        a = np.asarray(Image.open(p).convert('RGBA'))[:, :, 3]
        s = np.zeros(a.shape + (4,), np.uint8)
        s[:, :, 3] = np.where(a > 0, 254, 0)
        q = OUT + os.path.relpath(p, R)[:-4] + '_s.png'
        os.makedirs(os.path.dirname(q), exist_ok=True)
        Image.fromarray(s, 'RGBA').save(q, optimize=True)
        n += 1
print(f'gen_specular: wrote {n} _s.png maps to {OUT}')
if n == 0: raise SystemExit('gen_specular: no glow frames found')
