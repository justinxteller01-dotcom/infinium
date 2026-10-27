#!/usr/bin/env python3
"""
Updates and re-renders guidebook armor renders for all 5 tiers at max energy:
- Astral (Tier 1 max, 8 crystals, pieces 0..4)
- Cybernetic (Tier 1 max, 8 crystals, vector thruster pieces 0..4)
- Singularity (Tier 2 max, 16 crystals, pieces 0..8)
- CyberPsycho (Tier 2 max, 16 crystals, overclocked thruster pieces 0..8)
- Infinity (Tier 3 max, 32 crystals, Divine Archangel pieces 0..16)

Preserves exact render style, framing, canvas size (384x192 per cell), and animation loop timings.
"""

import os, sys, math, random, json, time
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(r"F:\GrokBot\work\infinium_pass2")
ENT = ROOT / "src/main/resources/assets/infinium/textures/entity"
GUIDE_DIR = ROOT / "src/main/resources/assets/infinium/textures/gui/guide"
GUIDE_JSON = ROOT / "src/main/resources/assets/infinium/guide/strips.json"
PART_DIR = Path(r"F:\GrokBot\infinium_renders\render_kit_r5b\assets\particle")
PREVIEW_DIR = Path(r"C:\Users\justi\.gemini\antigravity\brain\80bb1dc4-9ed5-43bd-ac57-d4c645e461c8\scratch\guide_armor_previews")
PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

BG = (118, 118, 124)
SKIN = Image.new('RGBA', (64, 64), (196, 192, 186, 255))
CLK = {'astral': (40, 300), 'cybernetic': (40, 300), 'singularity': (100, 100), 'cyberpsycho': (80, 100), 'infinity': (80, 40)}
HEL = {'astral': 'glow', 'cybernetic': 'glow_cybernetic', 'singularity': 'glow_singularity', 'cyberpsycho': 'glow_cyberpsycho', 'infinity': 'glow_infinity'}
STEP_MS = 100
COLS = 8
FW, FH = 384, 192

PIECES = {
    "astral": [
        (0, 0.0, 0.0, 0.0, 3.2, 8.5, 1.2),
        (1, 1.6, 2.2, 0.3, 3.8, 7.0, 0.8),
        (2, -0.6, -2.0, -0.2, 1.8, 5.0, 0.8),
        (3, 3.2, 3.8, -0.3, 3.2, 5.5, 0.6),
        (4, 4.8, 1.2, 0.2, 3.8, 9.5, 0.6),
    ],
    "cybernetic": [
        (0, 0.2, 0.5, 0.2, 3.8, 7.5, 2.2),
        (1, 0.7, 8.0, 0.3, 2.8, 3.0, 2.0),
        (2, -0.3, -1.5, -0.2, 2.0, 5.5, 1.0),
        (3, 3.8, 2.0, 0.4, 1.8, 4.5, 1.2),
        (4, 0.5, 10.5, 0.2, 3.2, 6.5, 0.8),
    ],
    "singularity": [
        (0, 0.0, 0.0, 0.0, 3.8, 9.0, 1.4),
        (1, 1.5, 2.5, 0.4, 4.2, 7.5, 0.9),
        (2, -0.5, -2.5, -0.3, 2.2, 6.0, 0.9),
        (3, 3.5, 3.5, -0.3, 3.8, 6.0, 0.7),
        (4, 5.0, 1.0, 0.2, 4.2, 10.0, 0.7),
        (5, 6.0, 2.0, -0.2, 3.5, 8.0, 0.6),
        (6, 4.0, 5.0, 0.3, 3.0, 7.0, 0.5),
        (7, 2.0, -1.0, 0.4, 4.0, 4.0, 0.5),
        (8, 7.0, 0.0, 0.1, 4.5, 11.0, 0.6),
    ],
    "cyberpsycho": [
        (0, 0.0, 0.5, 0.2, 4.2, 8.0, 2.4),
        (1, 0.5, 8.0, 0.3, 3.2, 3.5, 2.2),
        (2, -0.4, -1.8, -0.3, 2.2, 4.5, 1.2),
        (3, 4.0, 1.5, 0.4, 2.2, 5.0, 1.4),
        (4, 4.5, 2.5, -0.2, 3.5, 8.0, 0.8),
        (5, 5.5, 3.5, 0.3, 3.0, 7.0, 0.7),
        (6, 2.5, 7.5, 0.5, 2.2, 3.0, 1.8),
        (7, -0.6, 2.0, 0.4, 2.0, 4.0, 0.8),
        (8, 0.5, 11.0, 0.2, 4.0, 8.5, 0.7),
    ],
    "infinity": [
        (0, 0.0, 0.0, 0.0, 4.0, 9.5, 1.6),
        (1, 0.5, 8.5, 0.3, 3.0, 3.0, 1.2),
        (2, -0.5, -2.2, -0.3, 2.2, 5.0, 1.0),
        (3, 2.0, 2.0, 0.4, 4.0, 8.0, 0.9),
        (4, 3.5, 1.0, -0.3, 3.5, 9.0, 0.8),
        (5, 4.5, 3.5, 0.3, 3.8, 7.0, 0.7),
        (6, 5.5, 0.5, -0.2, 4.0, 10.5, 0.7),
        (7, 6.5, 2.0, 0.2, 4.5, 10.0, 0.6),
        (8, 7.5, 4.0, -0.1, 3.5, 7.5, 0.5),
        (9, 6.0, 1.0, 0.1, 3.5, 8.0, 0.6),
        (10, 6.8, 2.0, -0.2, 3.2, 9.0, 0.5),
        (11, 3.5, -2.0, 0.3, 3.0, 6.5, 0.5),
        (12, 5.0, 6.0, 0.2, 3.5, 7.5, 0.6),
        (13, 7.5, 0.5, 0.1, 4.0, 10.0, 0.5),
        (14, 8.2, 2.5, -0.1, 2.5, 8.5, 0.5),
        (15, 5.5, -1.5, -0.2, 3.2, 7.0, 0.5),
        (16, 8.8, 1.0, 0.0, 4.5, 11.0, 0.5),
    ]
}

def get_uv(idx):
    return (idx * 16) % 64, (((idx * 16) // 64) * 16) % 64

def rot(p, rx, ry, rz):
    x, y, z = p
    c, s = math.cos(rx), math.sin(rx); y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(ry), math.sin(ry); x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(rz), math.sin(rz); x, y = x * c - y * s, x * s + y * c
    return (x, y, z)

_TC = {}
def comp(a, b):
    c = a.copy(); c.alpha_composite(b); return c

def tex(st, layer, f):
    k = (st, layer, f)
    if k not in _TC:
        d = ENT / f"infinium_glow/{st}/{layer}"
        n = len(list(d.glob("*.png")))
        F = CLK[st][0]
        ff = f if n == F else (f * n // F) % n
        base = 'infinity' if st == 'infinity' and (ENT / f"equipment/{layer}/infinity.png").exists() else 'infinium'
        im_base = Image.open(ENT / f"equipment/{layer}/{base}.png").convert('RGBA')
        im_glow = Image.open(d / f"{ff}.png").convert('RGBA')
        _TC[k] = comp(im_base, im_glow)
    return _TC[k]

_HT = {}
def helmet_tex(st, hf):
    k = (st, hf)
    if k not in _HT:
        b = Image.open(ENT / "infinium_helmet_extra/base.png").convert('RGBA')
        b.alpha_composite(Image.open(ENT / f"infinium_helmet_extra/{HEL[st]}/{hf}.png").convert('RGBA'))
        _HT[k] = b.load()
    return _HT[k]

def box_quads(img, u, v, x0, y0, z0, w, h, d, g=0.0, mirror=False):
    if mirror: x0 = -(x0 + w)
    X0, Y0, Z0 = x0-g, y0-g, z0-g; sx, sy, sz = (w+2*g)/w, (h+2*g)/h, (d+2*g)/d
    px = img.load(); out = []
    def face(fu, fv, fw, fh, mapf):
        for j in range(fh):
            for i in range(fw):
                c = px[fu+i, fv+j]
                if c[3] < 128: continue
                cs = [mapf(i+a, j+b) for a, b in ((0, 0), (1, 0), (1, 1), (0, 1))]
                if mirror: cs = [(-x, y, z) for x, y, z in cs]
                out.append((cs, c[:3] + (255,)))
    face(u+d, v+d, w, h, lambda a, b: (X0+a*sx, Y0+b*sy, Z0))
    face(u+2*d+w, v+d, w, h, lambda a, b: (X0+(w-a)*sx, Y0+b*sy, Z0+d*sz))
    face(u, v+d, d, h, lambda a, b: (X0, Y0+b*sy, Z0+(d-a)*sz))
    face(u+d+w, v+d, d, h, lambda a, b: (X0+w*sx, Y0+b*sy, Z0+a*sz))
    face(u+d, v, w, d, lambda a, b: (X0+a*sx, Y0, Z0+b*sz))
    face(u+d+w, v, w, d, lambda a, b: (X0+a*sx, Y0+h*sy, Z0+b*sz))
    return out

def part(quads, pivot, r=(0, 0, 0)):
    return [([tuple(pivot[k] + rot(p, *r)[k] for k in range(3)) for p in cs], c) for cs, c in quads]

HX = [(0, 0, -4.0, -6.0, -5.6, 1.0, 6.0, 0.6, False), (0, 0, 3.0, -6.0, -5.6, 1.0, 6.0, 0.6, True), (4, 0, -3.0, -6.0, -5.6, 6.0, 1.0, 0.6, False),
      (12, 9, -0.25, -8.90, -5.35, 0.5, 0.4, 0.35, False), (16, 9, -0.5, -8.50, -5.35, 1.0, 0.4, 0.35, False), (22, 9, -0.75, -8.10, -5.35, 1.5, 0.4, 0.35, False),
      (16, 12, -0.5, -7.70, -5.35, 1.0, 0.4, 0.35, False), (12, 12, -0.25, -7.30, -5.35, 0.5, 0.4, 0.35, False)]

def helmet_quads(st, hf):
    px = helmet_tex(st, hf); Q = []
    for u, v, x0, y0, z0, w, h, d, mir in HX:
        def face(fu, fv, fw, fh, mapf):
            nu = max(1, math.ceil(fw-1e-6)); nv = max(1, math.ceil(fh-1e-6))
            for j in range(nv):
                for i in range(nu):
                    a0, a1 = fw*i/nu, fw*(i+1)/nu; b0, b1 = fh*j/nv, fh*(j+1)/nv
                    tu = int(fu+(a0+a1)/2); tv = int(fv+(b0+b1)/2)
                    if mir: tu = int(fu+fw-(a0+a1)/2)
                    c = px[min(31, tu), min(15, tv)]
                    if c[3] < 128: continue
                    Q.append(([mapf(a0, b0), mapf(a1, b0), mapf(a1, b1), mapf(a0, b1)], c[:3] + (255,)))
        face(u+d, v+d, w, h, lambda a, b: (x0+a, y0+b, z0)); face(u+2*d+w, v+d, w, h, lambda a, b: (x0+w-a, y0+b, z0+d))
        face(u, v+d, d, h, lambda a, b: (x0, y0+b, z0+d-a)); face(u+d+w, v+d, d, h, lambda a, b: (x0+w, y0+b, z0+a))
        face(u+d, v, w, d, lambda a, b: (x0+a, y0, z0+b)); face(u+d+w, v, w, d, lambda a, b: (x0+a, y0+h, z0+b))
    return Q

def wing_quads(st, f):
    b = Image.open(ENT / f"infinium_wings/base_{st}.png").convert("RGBA")
    g = Image.open(ENT / f"infinium_wings/glow/{st}/{f}.png").convert("RGBA")
    tex = b.copy()
    tex.alpha_composite(g)
    px = tex.load()
    iw, ih = tex.size

    # Overall linear progression at max energy:
    if st in ("astral", "cybernetic"):
        overall = 1.0 / 3.0
    elif st in ("singularity", "cyberpsycho"):
        overall = 2.0 / 3.0
    else:
        overall = 1.0

    scale = 0.72 + 0.33 * overall
    alpha_mul = 0.40 + 0.50 * overall

    # Sleek V-stance resting angles matching InfiniumWingsRenderer.java
    rx, ry, rz = 0.16, -0.15, -0.12
    pivot_L = (1.5, 1.5, 2.2)
    pivot_R = (-1.5, 1.5, 2.2)

    quads = []

    def make_box(u, v, x0, y0, z0, w, h, d, pivot, r_rot):
        def add_face(u0, v0, fw, fh, map_xyz):
            nu = max(1, int(round(fw)))
            nv = max(1, int(round(fh)))
            for j in range(nv):
                v_tex = min(ih - 1, max(0, int(v0 + (j + 0.5) * fh / nv)))
                for i in range(nu):
                    u_tex = min(iw - 1, max(0, int(u0 + (i + 0.5) * fw / nu)))
                    c = px[u_tex, v_tex]
                    if c[3] < 20: continue
                    
                    u_f0, u_f1 = i / nu, (i + 1) / nu
                    v_f0, v_f1 = j / nv, (j + 1) / nv
                    
                    local_cs = [
                        map_xyz(u_f0, v_f0),
                        map_xyz(u_f1, v_f0),
                        map_xyz(u_f1, v_f1),
                        map_xyz(u_f0, v_f1)
                    ]
                    
                    world_cs = []
                    for pt in local_cs:
                        spt = (pt[0] * scale, pt[1] * scale, pt[2] * scale)
                        rpt = rot(spt, *r_rot)
                        wpt = (pivot[0] + rpt[0], pivot[1] + rpt[1], pivot[2] + rpt[2])
                        world_cs.append(wpt)
                    
                    final_c = (c[0], c[1], c[2], int(c[3] * alpha_mul))
                    quads.append((world_cs, final_c))

        add_face(u + d, v + d, w, h, lambda a, b: (x0 + a * w, y0 + b * h, z0))
        add_face(u + 2 * d + w, v + d, w, h, lambda a, b: (x0 + (1 - a) * w, y0 + b * h, z0 + d))
        add_face(u, v + d, d, h, lambda a, b: (x0, y0 + b * h, z0 + (1 - a) * d))
        add_face(u + d + w, v + d, d, h, lambda a, b: (x0 + w, y0 + b * h, z0 + a * d))
        add_face(u + d, v, w, d, lambda a, b: (x0 + a * w, y0, z0 + b * d))
        add_face(u + d + w, v, w, d, lambda a, b: (x0 + a * w, y0 + h, z0 + (1 - b) * d))

    for idx, x, y, z, w, h, d in PIECES[st]:
        uL, vL = get_uv(idx)
        uR, vR = uL + 64, vL
        make_box(uL, vL, x, y, z, w, h, d, pivot_L, (rx, ry, rz))
        make_box(uR, vR, -x - w, y, z, w, h, d, pivot_R, (rx, -ry, -rz))

    return quads

def build(st, f, wings=True):
    arm = tex(st, 'humanoid', f); leg = tex(st, 'humanoid_leggings', f); Q = []
    body = [(0, 0, -4, -8, -4, 8, 8, 8, (0, 0, 0), (0, 0, 0), False),
            (16, 16, -4, 0, -2, 8, 12, 4, (0, 0, 0), (0, 0, 0), False),
            (40, 16, -3, -2, -2, 4, 12, 4, (-5, 2, 0), (0, 0, 0), False),
            (40, 16, -1, -2, -2, 4, 12, 4, (5, 2, 0), (0, 0, 0), True),
            (0, 16, -2, 0, -2, 4, 12, 4, (-1.9, 12, 0), (0, 0, 0), False),
            (0, 16, -2, 0, -2, 4, 12, 4, (1.9, 12, 0), (0, 0, 0), True)]
    def limbset(img, g, parts):
        for (u, v, x0, y0, z0, w, h, d, pv, r, m) in parts:
            Q.extend(part(box_quads(img, u, v, x0, y0, z0, w, h, d, g, m), pv, r))
    limbset(SKIN, 0.0, body)
    limbset(arm, 1.0, body)
    limbset(leg, 0.5, [body[1], body[4], body[5]])
    if wings:
        Q.extend(wing_quads(st, f))
    return Q

SIZE = 560; SCALE = 10.0; YOFF = 5.5; CROP = (SIZE//2-200, SIZE//2-215, SIZE//2+200, SIZE//2+215)
def render_z(Q, yaw):
    P = np.array([cs for cs, c in Q], float); C = np.array([c[:3] for cs, c in Q], np.int64)
    x, y, z = P[..., 0], P[..., 1]-12, P[..., 2]
    c, s_ = math.cos(yaw), math.sin(yaw); x, z = x*c + z*s_, -x*s_ + z*c
    ax, ay, az = x[:, 1]-x[:, 0], y[:, 1]-y[:, 0], z[:, 1]-z[:, 0]; bx, by, bz = x[:, 3]-x[:, 0], y[:, 3]-y[:, 0], z[:, 3]-z[:, 0]
    nx, ny, nz = ay*bz-az*by, az*bx-ax*bz, ax*by-ay*bx; nl = np.sqrt(nx*nx+ny*ny+nz*nz)+1e-9
    lit = 0.55+0.45*np.abs((-0.3*nx-0.6*ny-0.75*nz)/nl)
    C = np.clip(C*lit[:, None]+np.where(C.max(1, keepdims=True) < 70, 14, 0), 0, 255).astype(np.int64)
    size = SIZE; sx = size/2+x*SCALE; sy = size/2+y*SCALE; dep = z.mean(1)
    x0 = np.floor(sx.min(1)-0.5).astype(int); y0 = np.floor(sy.min(1)-0.5).astype(int)
    bw = int(np.ceil((sx.max(1)-sx.min(1)).max()))+2; bh = int(np.ceil((sy.max(1)-sy.min(1)).max()))+2
    hits = []
    for dy in range(bh):
        for dx in range(bw):
            px_ = x0+dx; py_ = y0+dy; cx = px_+0.5; cy = py_+0.5; sg = []
            for k in range(4):
                axx, ayy = sx[:, k], sy[:, k]; bxx, byy = sx[:, (k+1) % 4], sy[:, (k+1) % 4]; sg.append((bxx-axx)*(cy-ayy)-(byy-ayy)*(cx-axx))
            sg = np.stack(sg, 1); inside = (np.all(sg >= -1e-9, 1) | np.all(sg <= 1e-9, 1)) & (px_ >= 0) & (px_ < size) & (py_ >= 0) & (py_ < size)
            idx = np.nonzero(inside)[0]
            if len(idx): hits.append((py_[idx]*size+px_[idx], dep[idx], idx))
    img = np.zeros((size*size, 3), np.uint8); img[:] = BG; zb = np.full(size*size, 1e9)
    if hits:
        pix = np.concatenate([h[0] for h in hits]); d = np.concatenate([h[1] for h in hits]); qi = np.concatenate([h[2] for h in hits])
        key = C[qi, 0]*65536+C[qi, 1]*256+C[qi, 2]; o = np.lexsort((-key, np.round(d, 6), pix)); pix, qi, d = pix[o], qi[o], d[o]
        first = np.ones(len(pix), bool); first[1:] = pix[1:] != pix[:-1]
        img[pix[first]] = C[qi[first]]; zb[pix[first]] = d[first]
    im = img.reshape(size, size, 3); zb = zb.reshape(size, size)
    fg = zb < 1e8; edge = fg & ~(np.asarray(Image.fromarray((fg*255).astype(np.uint8)).filter(ImageFilter.MinFilter(3))) > 0)
    im = np.where(edge[..., None], np.array([20, 18, 22]), im).astype(np.uint8)
    a, b, c2, d2 = CROP
    return im[b:d2, a:c2], zb[b:d2, a:c2]

_FC = {}
def figure(st, ms, yaw, wings=True):
    F, fms = CLK[st]; f = (ms//fms) % F; hf = f
    k = (st, f, round(yaw, 4), wings)
    if k not in _FC:
        if len(_FC) > 300: _FC.clear()
        Q = build(st, f, wings) + helmet_quads(st, hf)
        Q = [([(x, y+YOFF, z) for x, y, z in cs], c) for cs, c in Q]
        _FC[k] = render_z(Q, yaw)
    return _FC[k]

# ---------------- particles ----------------
GEN = [Image.open(PART_DIR / f"generic_{i}.png").convert('RGBA') for i in range(8)]
def sprite(idx, col, px):
    s = GEN[max(0, min(7, idx))]; a = np.asarray(s).astype(float)/255
    a[..., 0] *= col[0]/255; a[..., 1] *= col[1]/255; a[..., 2] *= col[2]/255
    return Image.fromarray((a*255).astype(np.uint8)).resize((max(1, px), max(1, px)), Image.NEAREST)

SG = [(0x32, 0x09, 0x60), (0x44, 0x0D, 0x83), (0x62, 0x1C, 0xAC), (0x75, 0x26, 0xC8), (0x85, 0x2F, 0xE3), (0x9B, 0x41, 0xF0), (0xA8, 0x4E, 0xF4)]
PSY = [(0xB0, 0x4C, 0xFF), (0xD2, 0x4C, 0xFF), (0xFF, 0x4C, 0xE0), (0xFF, 0x7A, 0xD9), (0xE0, 0x3C, 0xC8), (0xFF, 0x5F, 0xB0)]
CYB = [(0xB0, 0x48, 0xF0), (0xC8, 0x54, 0xFF), (0xC8, 0x50, 0xE8), (0xDC, 0x54, 0xD0)]
RAD = 0.55; H = 1.8; RATE = 1.75

def glitch(rnd, ps, col, y0, streak, lifek):
    a = rnd.random()*2*math.pi; rr = RAD+(rnd.random()-.4)*.2; lx, lz = math.cos(a)*rr, math.sin(a)*rr
    n = (5+rnd.randrange(3) if streak else 1+rnd.randrange(2)); d = [(rnd.random()-.5)*.12 for _ in range(3)]
    life = max(1, int(8/(rnd.random()*.8+.2)*lifek))
    for i in range(n):
        zig = (1 if i % 2 == 0 else -1)*0.05
        ox = lx+d[0]*i+zig*math.sin(a); oy = y0+d[1]*i+(zig if n > 2 else 0); oz = lz+d[2]*i-zig*math.cos(a)
        if oy > 1.30 and -0.85 < oz < 0 and abs(ox) < 0.35: continue
        for s in (-1, 1): ps.append(dict(p=[s*ox, oy, oz], v=[0, 0, 0], age=0, life=life, col=col, big=streak))

def emit(kind, tc, rnd, ps, mult):
    if kind == 'singularity':
        budget = 1.5 * (1.2 * 1.3 * 3.12 / 3) * mult
        cap = max(2, math.ceil(3.0 * mult))
        pn = 0
        while pn < cap and rnd.random() < budget:
            pn += 1; budget -= 1
            band = rnd.randrange(3); hy = (0.30, 0.81, 1.30)[band]; sp = (0.035, -0.045, 0.04)[band]
            a = tc*abs(sp)+band*2*math.pi/3+(rnd.random()-.5)*.6; rr = RAD+.15+rnd.random()*.25; vs = math.copysign(.04, sp)
            k = rnd.random(); col = (14, 6, 18) if k < 0.2 else ((0xB7, 0x3F, 0x9B) if k < 0.26 else rnd.choice(SG))
            ps.append(dict(p=[math.cos(a)*rr, hy+(rnd.random()-.5)*.15, math.sin(a)*rr], v=[-math.sin(a)*vs, 0, math.cos(a)*vs], age=0,
                           life=int(8/(rnd.random()*.8+.2)*0.7)+1, col=col, big=True))
    elif kind == 'cyberpsycho':
        pr = 2.45 / 4 * mult
        passes = int(pr) + (1 if rnd.random() < pr - int(pr) else 0)
        for _ in range(passes):
            events = 2 if rnd.random() < 0.2 else 1; streak = rnd.random() < 0.45
            for _e in range(events): glitch(rnd, ps, rnd.choice(PSY), 0.1+rnd.random()*H*0.85, streak, 0.5 if streak else 0.35)
    elif kind == 'cybernetic':
        if rnd.random() < 1.7 / 20 * mult:
            y0 = (0.08+rnd.random()*0.6) if rnd.random() < 0.5 else (0.8+rnd.random()*0.45)
            glitch(rnd, ps, rnd.choice(CYB), y0, rnd.random() < 0.4, 0.45)
    else:   # astral
        if rnd.random() < (RATE / 8 * 1.5) * mult:
            a = rnd.random()*2*math.pi
            ps.append(dict(p=[math.cos(a)*RAD, 0.1+rnd.random()*H*0.55, math.sin(a)*RAD], v=[0, 0.01, 0], age=0, life=40+rnd.randint(0, 10), portal=True, br=rnd.random()))

class Sim:
    def __init__(s, st, loop_ticks):
        s.st = st; s.loop = loop_ticks; s.ps = []
    def step(s, tick):
        rnd = random.Random(f'{s.st}/{tick % s.loop}')
        if s.st == 'infinity':
            emit('singularity', tick, rnd, s.ps, 0.8 * 2.50)
            emit('cyberpsycho', tick, rnd, s.ps, 1.2 * 2.50)
        elif s.st in ('singularity', 'cyberpsycho'):
            emit(s.st, tick, rnd, s.ps, 1.883)
        else: # astral, cybernetic
            emit(s.st, tick, rnd, s.ps, 1.267)
        for q in s.ps:
            q['age'] += 1
            q['p'] = [a+b for a, b in zip(q['p'], q['v'])]
        s.ps = [q for q in s.ps if q['age'] < q['life']]

def project(p, yaw):
    x, y, z = -p[0]*16, 24-p[1]*16+YOFF-12, p[2]*16
    c, s_ = math.cos(yaw), math.sin(yaw); xx, zz = x*c+z*s_, -x*s_+z*c
    return SIZE/2+xx*SCALE-CROP[0], SIZE/2+y*SCALE-CROP[1], zz

def compose(img, zb, ps, yaw, transparent):
    fig = Image.fromarray(img).convert('RGBA'); Hh, Ww = zb.shape
    part_layer = Image.new('RGBA', fig.size, (0, 0, 0, 0))
    for (sx, sy, dz), q in sorted([(project(q['p'], yaw), q) for q in ps], key=lambda t: -t[0][2]):
        ix, iy = int(sx), int(sy)
        if 0 <= iy < Hh and 0 <= ix < Ww and zb[iy, ix] < dz: continue
        f = q['age']/q['life']
        if q.get('portal'):
            col = tuple(int(c*(q['br']*0.6+0.4)) for c in (230, 77, 255)); spr = sprite(int(8*(1-f)), col, int(26*(1-f*f)+4))
        else:
            spr = sprite(7-int(8*f), q['col'], 16 if q.get('big') else 12)
        part_layer.alpha_composite(spr, (int(sx-spr.width/2), int(sy-spr.height/2)))
    if transparent:
        a = np.where(zb < 1e8, 255, 0).astype(np.uint8); fig.putalpha(Image.fromarray(a))
    fig.alpha_composite(part_layer)
    return fig

def view_cell(fig, w, h):
    k = min(w/fig.width, h/fig.height)
    im = fig.resize((max(1, round(fig.width*k)), max(1, round(fig.height*k))), Image.LANCZOS)
    c = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    c.alpha_composite(im, ((w-im.width)//2, (h-im.height)//2))
    return c

def harden(im):
    a = np.asarray(im).copy()
    a[..., 3] = np.where(a[..., 3] >= 100, 255, 0)
    return Image.fromarray(a, 'RGBA')

def render_armor_set(st):
    t0 = time.time()
    F, fms = CLK[st]
    loop = F * fms
    TT = loop // 50
    n = loop // STEP_MS
    sim = Sim(st, TT)
    for t in range(-TT, 0): sim.step(t)

    rows = (n + COLS - 1) // COLS
    sheet = Image.new('RGBA', (COLS * FW, rows * FH), (0, 0, 0, 0))
    gif_frames = []

    # Single strip fallback (16 frames of 128x128 front view)
    strip_step = max(1, n // 16)
    single_strip = Image.new('RGBA', (128, 2048), (0, 0, 0, 0))
    strip_idx = 0

    tick = -1
    for i in range(n):
        while tick < i * STEP_MS // 50:
            tick += 1
            sim.step(tick)
        ms = i * STEP_MS
        cell = Image.new('RGBA', (FW, FH), (0, 0, 0, 0))
        prev = Image.new('RGB', (800, 430), BG)

        for k, yaw in enumerate((0.0, math.pi)):
            img, zb = figure(st, ms, yaw)
            composed_trans = compose(img, zb, sim.ps, yaw, True)
            cell.alpha_composite(harden(view_cell(composed_trans, FW // 2, FH)), (k * FW // 2, 0))
            
            composed_opaque = compose(img, zb, sim.ps, yaw, False)
            prev.paste(composed_opaque.convert('RGB'), (k * 400, 0))

            if k == 0 and i % strip_step == 0 and strip_idx < 16:
                # Add front view to single strip fallback
                front_cell = harden(view_cell(composed_trans, 128, 128))
                single_strip.alpha_composite(front_cell, (0, strip_idx * 128))
                strip_idx += 1

        sheet.alpha_composite(cell, ((i % COLS) * FW, (i // COLS) * FH))
        
        d = ImageDraw.Draw(prev)
        d.text((8, 6), f"{st.upper()} - Max Energy Guide Render", fill=(20, 20, 24))
        gif_frames.append(prev)

    # Save _fb sheet
    sheet_path = GUIDE_DIR / f"{st}_fb.png"
    sheet.save(sheet_path, optimize=True)

    # Save single strip fallback
    strip_path = GUIDE_DIR / f"{st}.png"
    single_strip.save(strip_path, optimize=True)

    # Save preview gif
    q = gif_frames[0].quantize(128, dither=Image.Dither.NONE)
    fr = [g.quantize(palette=q, dither=Image.Dither.NONE) for g in gif_frames]
    gif_path = PREVIEW_DIR / f"{st}_front_back.gif"
    fr[0].save(gif_path, save_all=True, append_images=fr[1:], duration=STEP_MS, loop=0, optimize=True, disposal=1)

    elapsed = time.time() - t0
    print(f"[{st}] {n} frames completed in {elapsed:.1f}s -> {sheet_path.name}")
    return st, {"file": f"{st}_fb", "fw": FW, "fh": FH, "cols": COLS, "frames": n, "ms": STEP_MS}

def render_settings():
    print("Rendering updated settings strips (Singularity with new wings)...")
    st = 'singularity'
    N = 8
    S = 64
    TT = CLK[st][0] * CLK[st][1] // 50
    sim = Sim(st, TT)
    for t in range(-TT, 0): sim.step(t)
    strips = {k: Image.new('RGBA', (S, S * N), (0, 0, 0, 0)) for k in ('particles_on', 'particles_off', 'wings_on', 'wings_off')}
    tick = -1
    for i in range(N):
        ms = i * 120
        while tick < ms // 50:
            tick += 1
            sim.step(tick)
        img, zb = figure(st, ms, 0.0)
        strips['particles_on'].alpha_composite(harden(view_cell(compose(img, zb, sim.ps, 0.0, True), S, S)), (0, i * S))
        strips['particles_off'].alpha_composite(harden(view_cell(compose(img, zb, [], 0.0, True), S, S)), (0, i * S))
        img, zb = figure(st, ms, math.pi, True)
        strips['wings_on'].alpha_composite(harden(view_cell(compose(img, zb, [], math.pi, True), S, S)), (0, i * S))
        img, zb = figure(st, ms, math.pi, False)
        strips['wings_off'].alpha_composite(harden(view_cell(compose(img, zb, [], math.pi, True), S, S)), (0, i * S))
    for k, im in strips.items():
        p = GUIDE_DIR / f"settings_{k}.png"
        im.save(p, optimize=True)
    print("Settings strips completed.")

if __name__ == "__main__":
    t_start = time.time()
    results = {}
    
    # Render all 5 sets
    for st in ["astral", "cybernetic", "singularity", "cyberpsycho", "infinity"]:
        k, meta = render_armor_set(st)
        results[k] = meta

    render_settings()

    # Update strips.json
    strips_json_path = ROOT / "src/main/resources/assets/infinium/guide/strips.json"
    strips_data = json.load(open(strips_json_path, "r", encoding="utf-8")) if strips_json_path.exists() else {}
    strips_data.update(results)
    with open(strips_json_path, "w", encoding="utf-8") as f:
        json.dump(strips_data, f, indent=2)

    total_time = time.time() - t_start
    print(f"ALL GUIDEBOOK RENDERS COMPLETED SUCCESSFULLY in {total_time:.1f}s!")
