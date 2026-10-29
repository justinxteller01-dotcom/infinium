"""Infinium solid-plate elytra: textures + geometry for Cybernetic (r3), Cyber Psycho (r7), Infinity (r5).

Why: the original frames are a flat back-view picture of BOTH wings pasted at u22-45, v0-21 of a 64x32 sheet, but the game
drew them on the vanilla box-UV elytra (front u24-33/v2-21, back u36-45, 2px side/top strips). Each face got a cropped
slice of the picture (half of it from the other wing) and the side/top strips got slivers of art -> the "image not found"
checkerboard. The 4px inflated slab also left the front and back visibly apart, with the cut-out silhouette having no walls.

Now: each wing = its half of the picture (u22-33 = left wing as seen from behind, outer edge at u22; the right wing is the
mirror). Geometry per variant = one 12x22 plate (only north/south faces, 2px deep, zScale 0.75 -> 1.5px) plus one wall
box per silhouette edge run (zero-thickness, single visible face), each with its own atlas slot painted with the colour
of the face texel it borders, so the plate reads as a solid 1.5px slab with textured edges from every angle.
Texture sheet: 128x64. Plate texOffs(0,0): north u2-13 v2-23, south u16-27 v2-23.

Usage: python3 tools/gen_elytra_plate.py <src_root_with_original_64x32_frames>
(original frames = git show 62954c2^:src/main/resources/assets/infinium/textures/entity/infinium_wings/...)"""
import glob, json, os, sys
import numpy as np
from PIL import Image
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DST = os.path.join(REPO, 'src/main/resources/assets/infinium/textures/entity/infinium_wings')
JAVA = os.path.join(REPO, 'src/main/java/dev/justin/infinium/client/WingPlateData.java')
LAYOUT = os.path.join(REPO, 'tools/elytra_plate_layout.json')
VARIANTS = [(1, 'r3/cybernetic'), (3, 'r7/cyberpsycho'), (4, 'r5/infinity')]
TW, TH, W, H, D = 128, 64, 12, 22, 2

def half(a):   # face texel (c, j), c = 0 at local x=-11 (inner/far end), c = 11 at the shoulder pivot (outer)
    return np.stack([a[0:H, 33 - c] for c in range(W)], 1)   # (H, W, 4)

def walls(mask):
    out = []   # (dir, c0, j0, len)  dir: W E D U  (left-wing local coords)
    m = np.pad(mask, 1)
    for c in range(W):
        for dirn, nb in (('W', -1), ('E', 1)):
            j = 0
            while j < H:
                if m[j+1, c+1] and not m[j+1, c+1+nb]:
                    j0 = j
                    while j < H and m[j+1, c+1] and not m[j+1, c+1+nb]: j += 1
                    out.append((dirn, c, j0, j - j0))
                else: j += 1
    for j in range(H):
        for dirn, nb in (('D', -1), ('U', 1)):
            c = 0
            while c < W:
                if m[j+1, c+1] and not m[j+1+nb, c+1]:
                    c0 = c
                    while c < W and m[j+1, c+1] and not m[j+1+nb, c+1]: c += 1
                    out.append((dirn, c0, j, c - c0))
                else: c += 1
    return out

def pack(items):
    occ = np.zeros((TH, TW), bool); occ[0:H+D, 0:2*D+2*W] = True; pos = []
    for w, h in items:
        for v in range(TH - h + 1):
            hit = next((u for u in range(TW - w + 1) if not occ[v:v+h, u:u+w].any()), None)
            if hit is not None: occ[v:v+h, hit:hit+w] = True; pos.append((hit, v)); break
        else: raise SystemExit('atlas full')
    return pos

def build(src):
    layout = {}
    for fam, d in VARIANTS:
        bases = sorted(glob.glob(f'{src}/{d}/base/*.png'))
        mask = np.any([half(np.asarray(Image.open(p).convert('RGBA')))[..., 3] > 8 for p in bases], 0)
        ws = walls(mask); cubes = []
        items = []
        for side in 'LR':
            for (dirn, c0, j0, n) in ws:
                if side == 'R' and dirn in 'WE': continue          # vertical slots are shared (colour runs along v only)
                items.append((4, 2 + n) if dirn in 'WE' else (4 + 2 * n, 2))
        pos = pack(items); k = 0; slots = {}
        for side in 'LR':
            for wi, (dirn, c0, j0, n) in enumerate(ws):
                if side == 'R' and dirn in 'WE': slots[(side, wi)] = slots[('L', wi)]; continue
                slots[(side, wi)] = pos[k]; k += 1
        for side in 'LR':
            for wi, (dirn, c0, j0, n) in enumerate(ws):
                U, V = slots[(side, wi)]
                if dirn in 'WE':
                    x = -11 + c0 + (0 if dirn == 'W' else 1); box = [x, -1 + j0, 0, 0, n, D]
                    face = dirn
                else:
                    y = -1 + j0 + (0 if dirn == 'D' else 1); box = [-11 + c0, y, 0, n, 0, D]
                    face = 'DOWN' if dirn == 'D' else 'UP'
                if side == 'R':
                    box[0] = -(box[0] + box[3])
                    if dirn in 'WE': face = 'E' if dirn == 'W' else 'W'
                face = {'W': 'WEST', 'E': 'EAST'}.get(face, face)
                cubes.append({'side': side, 'u': U, 'v': V, 'box': box, 'face': face, 'dir': dirn, 'c0': c0, 'j0': j0, 'n': n})
        layout[fam] = {'dir': d, 'walls': cubes}
    return layout

def remap(a, cubes):
    P = half(a); out = np.zeros((TH, TW, 4), np.uint8)
    for c in range(W):
        out[D:D+H, D + c] = P[:, c]                 # north: u increases with x
        out[D:D+H, 2*D + W + (W - 1 - c)] = P[:, c] # south: u decreases with x
    for q in cubes:
        U, V, n, c0, j0 = q['u'], q['v'], q['n'], q['c0'], q['j0']
        if q['dir'] in 'WE':
            for k in range(n): out[V+D+k, U:U+2*D] = P[j0 + k, c0]
        else:
            for m in range(n):
                c = c0 + m if q['side'] == 'L' else c0 + n - 1 - m
                out[V:V+D, U+D+m] = P[j0, c]; out[V:V+D, U+D+n+m] = P[j0, c]
    return out

def main(src):
    layout = build(src); n = 0
    for fam, L in layout.items():
        d = L['dir']
        files = sorted(glob.glob(f'{src}/{d}/**/*.png', recursive=True))
        for p in files:
            a = np.asarray(Image.open(p).convert('RGBA')); b = remap(a, L['walls'])
            if '/base/' in p:   # solid edges: wall texels opaque
                for q in L['walls']:
                    U, V = q['u'], q['v']; sl = (slice(V+D, V+D+q['n']), slice(U, U+2*D)) if q['dir'] in 'WE' else (slice(V, V+D), slice(U+D, U+D+2*q['n']))
                    reg = b[sl]; reg[..., 3] = np.where(reg[..., 3] > 0, 255, 0)
            rel = os.path.relpath(p, src); o = os.path.join(DST, rel); os.makedirs(os.path.dirname(o), exist_ok=True)
            Image.fromarray(b).save(o, optimize=True); n += 1
    json.dump({str(k): v for k, v in layout.items()}, open(LAYOUT, 'w'), indent=0)
    j = ['package dev.justin.infinium.client;', '',
         '/** GENERATED by tools/gen_elytra_plate.py, do not edit. Solid-plate elytra edge walls per wing family:',
         ' *  {side(0=L,1=R), texU, texV, x, y, z, w, h, d, face(0 W,1 E,2 DOWN,3 UP)}. */',
         'final class WingPlateData {', '    private WingPlateData() {}', f'    static final int TEX_W = {TW}, TEX_H = {TH};']
    for fam, L in layout.items():
        rows = ['{%d,%d,%d,%s,%d}' % (0 if q['side'] == 'L' else 1, q['u'], q['v'], ','.join('%d' % x for x in q['box']),
                ['WEST', 'EAST', 'DOWN', 'UP'].index(q['face'])) for q in L['walls']]
        j.append(f'    static final int[][] FAM{fam} = {{' + ','.join(rows) + '};')
    j.append('}'); open(JAVA, 'w').write('\n'.join(j) + '\n')
    print('frames', n, {k: len(v['walls']) for k, v in layout.items()})

if __name__ == '__main__': main(sys.argv[1])
