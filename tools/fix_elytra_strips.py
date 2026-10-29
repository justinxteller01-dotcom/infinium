"""Rebuild elytra box-UV edge strips so every side/top/bottom texel equals the face texel it physically touches.
Layout (texOffs 22,0, box 10x20x2): north u24-33 v2-21, south u36-45 (x mirrored), west u22-23, east u34-35,
down u24-33 v0-1, up u34-43 v0-1. Idempotent. Usage: python3 tools/fix_elytra_strips.py"""
import glob, os
import numpy as np
from PIL import Image
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/infinium/textures/entity/infinium_wings')
def fix(a):
    s = a.shape[1] // 64; a = a.copy()
    def px(c, r): return a[r*s:(r+1)*s, c*s:(c+1)*s].copy()
    def put(c, r, v): a[r*s:(r+1)*s, c*s:(c+1)*s] = v
    for r in range(2, 22):
        put(23, r, px(24, r)); put(22, r, px(45, r))   # west: minZ->north minX, maxZ->south minX
        put(34, r, px(33, r)); put(35, r, px(36, r))   # east: minZ->north maxX, maxZ->south maxX
    for x in range(10):
        put(24+x, 1, px(24+x, 2));  put(24+x, 0, px(45-x, 2))    # down (top edge)
        put(34+x, 1, px(24+x, 21)); put(34+x, 0, px(45-x, 21))   # up (bottom edge)
    return a
n = 0
for d in ('r3/cybernetic', 'r7/cyberpsycho', 'r5/infinity'):
    for p in sorted(glob.glob(f'{ROOT}/{d}/**/*.png', recursive=True)):
        im = Image.open(p).convert('RGBA'); a = np.asarray(im)
        if a.shape[1] != a.shape[0]*2: continue
        b = fix(a)
        if not np.array_equal(a, b): Image.fromarray(b).save(p, optimize=True); n += 1
print('fixed', n)
