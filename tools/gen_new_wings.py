#!/usr/bin/env python3
"""
Generates dedicated 128x64 textures for Infinium 3D Wings:
Left Wing (0..64) and Right Wing (64..128)

Follows exact armor texture design rules:
- Cybernetic & CyberPsycho: Symmetrically mirrored left-to-right (crisp vector lines, mirrored circuits, scan bars).
- Astral & Singularity: Asymmetric and randomized (independent celestial field noise, cosmic nebula drift, star glints that blink independently on left and right).
- Infinity: Grand synthesis fusing symmetrical archangel vector chassis with independently shimmering void plumage.

Outputs:
- base_<style>.png (128x64)
- accent_gray_<style>.png (128x64) for dye support
- glow/<style>/<frame>.png (128x64) animated emissive overlay
- glow_gray/<style>/<frame>.png (128x64) for dyed glow
"""

import os, math, random, shutil
from PIL import Image, ImageDraw

ROOT = "F:/GrokBot/work/infinium_pass2/src/main/resources/assets/infinium/textures/entity/infinium_wings"
os.makedirs(ROOT, exist_ok=True)

PALETTES = {
    'astral': {
        'symmetric': False,
        'base_plate': (22, 18, 26, 255),
        'base_edge': (35, 28, 42, 255),
        'base_accent': (55, 42, 68, 255),
        'glow_core': (176, 130, 240, 255),
        'glow_mid': (130, 80, 170, 255),
        'glow_deep': (73, 7, 103, 255),
        'frames': 40,
        'ms': 300
    },
    'cybernetic': {
        'symmetric': True,
        'base_plate': (20, 16, 22, 255),
        'base_edge': (38, 30, 40, 255),
        'base_accent': (58, 44, 62, 255),
        'glow_core': (210, 60, 240, 255),
        'glow_mid': (165, 80, 178, 255),
        'glow_deep': (104, 8, 78, 255),
        'frames': 40,
        'ms': 300
    },
    'singularity': {
        'symmetric': False,
        'base_plate': (14, 10, 18, 255),
        'base_edge': (26, 18, 34, 255),
        'base_accent': (44, 28, 58, 255),
        'glow_core': (180, 80, 255, 255),
        'glow_mid': (134, 47, 156, 255),
        'glow_deep': (59, 10, 94, 255),
        'frames': 100,
        'ms': 100
    },
    'cyberpsycho': {
        'symmetric': True,
        'base_plate': (18, 14, 18, 255),
        'base_edge': (32, 24, 30, 255),
        'base_accent': (50, 36, 48, 255),
        'glow_core': (255, 118, 203, 255),
        'glow_mid': (184, 25, 147, 255),
        'glow_deep': (131, 12, 114, 255),
        'frames': 80,
        'ms': 100
    },
    'infinity': {
        'symmetric': False, # Hybrid: core is mirrored, plumage has independent shimmer
        'base_plate': (18, 12, 22, 255),
        'base_edge': (34, 22, 40, 255),
        'base_accent': (52, 34, 62, 255),
        'glow_core': (245, 160, 255, 255),
        'glow_mid': (206, 72, 228, 255),
        'glow_deep': (134, 47, 156, 255),
        'frames': 80,
        'ms': 40
    }
}

# 9 UV regions for wing pieces (x, y, w, h) in 64x64 space
PIECE_UVS = [
    (0, 0, 16, 16),    # piece_0 (base core)
    (16, 0, 16, 16),   # piece_1
    (32, 0, 16, 16),   # piece_2
    (48, 0, 16, 16),   # piece_3
    (0, 16, 16, 16),   # piece_4
    (16, 16, 16, 16),  # piece_5
    (32, 16, 16, 16),  # piece_6
    (48, 16, 16, 16),  # piece_7
    (0, 32, 16, 16),   # piece_8
]

def render_piece_base(d, px, py, pw, ph, pal, style_key, is_right=False):
    # Base plate body
    d.rectangle([px, py, px + pw - 1, py + ph - 1], fill=pal['base_plate'])
    # Beveled outer edge
    d.rectangle([px, py, px + pw - 1, py + 1], fill=pal['base_edge'])
    d.rectangle([px, py, px + 1, py + ph - 1], fill=pal['base_edge'])
    d.rectangle([px, py + ph - 2, px + pw - 1, py + ph - 1], fill=(10, 8, 12, 255))
    d.rectangle([px + pw - 2, py, px + pw - 1, py + ph - 1], fill=(10, 8, 12, 255))
    
    if 'cyber' in style_key:
        # Symmetrical panel lines
        for vy in range(py + 3, py + ph - 3, 3):
            d.line([px + 3, vy, px + pw - 4, vy], fill=pal['base_accent'])
    else:
        # Organic feather quills (offset on right wing for asymmetry)
        off = 2 if not is_right else 4
        d.line([px + off, py + 2, px + pw - 3, py + ph - 3], fill=pal['base_accent'])
        d.line([px + off + 2, py + 2, px + pw - 3, py + ph - 5], fill=pal['base_edge'])

def make_base_texture(style_key):
    pal = PALETTES[style_key]
    im = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    
    # 1. Left Wing (x: 0..64)
    for idx, (px, py, pw, ph) in enumerate(PIECE_UVS):
        render_piece_base(d, px, py, pw, ph, pal, style_key, is_right=False)
        
    # 2. Right Wing (x: 64..128)
    for idx, (px, py, pw, ph) in enumerate(PIECE_UVS):
        rx = px + 64
        render_piece_base(d, rx, py, pw, ph, pal, style_key, is_right=True)
        
    return im

def make_gray_texture(base_im):
    gray = base_im.copy()
    p = gray.load()
    for y in range(gray.size[1]):
        for x in range(gray.size[0]):
            r, g, b, a = p[x, y]
            if a > 0:
                v = int(0.299 * r + 0.587 * g + 0.114 * b)
                p[x, y] = (v, v, v, a)
    return gray

def make_glow_frames(style_key):
    pal = PALETTES[style_key]
    frames = pal['frames']
    symmetric = pal['symmetric']
    out_frames = []
    
    # Random generators with fixed seeds for deterministic animated shimmer
    rng_left = random.Random(1337)
    rng_right = random.Random(9999 if not symmetric else 1337)
    
    for f in range(frames):
        im = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        t = f / float(frames)
        phase_L = t * 2.0 * math.pi
        # If not symmetric, right wing has a distinct phase shift (cosmic asymmetry!)
        phase_R = phase_L if symmetric else phase_L + 1.85
        
        # Draw Left Wing
        for idx, (px, py, pw, ph) in enumerate(PIECE_UVS):
            wave_L = (math.sin(phase_L + idx * 0.75) + 1.0) * 0.5
            col_L = pal['glow_core'] if wave_L > 0.68 else (pal['glow_mid'] if wave_L > 0.35 else pal['glow_deep'])
            
            if 'cyber' in style_key:
                scan_y = py + 3 + int(wave_L * (ph - 6))
                d.line([px + 3, scan_y, px + pw - 4, scan_y], fill=col_L)
                if wave_L > 0.5:
                    d.point([px + 2, py + 2], fill=pal['glow_core'])
                    d.point([px + pw - 3, py + ph - 3], fill=pal['glow_core'])
            else:
                sweep_x = px + 3 + int(wave_L * (pw - 6))
                d.line([sweep_x, py + 3, sweep_x + 2, py + ph - 4], fill=col_L)
                # Random starlight glints on left wing
                if (f + idx * 7) % 11 == 0:
                    d.point([px + 5, py + 5], fill=pal['glow_core'])
                if (f + idx * 13) % 17 == 0:
                    d.point([px + 10, py + 11], fill=pal['glow_core'])

        # Draw Right Wing
        for idx, (px, py, pw, ph) in enumerate(PIECE_UVS):
            rx = px + 64
            if symmetric:
                # Perfectly mirrored left-to-right!
                # Crop left piece, flip horizontally, paste onto right piece
                piece_crop = im.crop((px, py, px + pw, py + ph)).transpose(Image.FLIP_LEFT_RIGHT)
                im.paste(piece_crop, (rx, py))
            else:
                # Asymmetric / independent cosmic drift!
                wave_R = (math.sin(phase_R + idx * 0.75 + 0.4) + 1.0) * 0.5
                col_R = pal['glow_core'] if wave_R > 0.68 else (pal['glow_mid'] if wave_R > 0.35 else pal['glow_deep'])
                sweep_x = rx + 3 + int(wave_R * (pw - 6))
                d.line([sweep_x, py + 3, sweep_x + 2, py + ph - 4], fill=col_R)
                # Independent random starlight glints on right wing (blinks at different times!)
                if (f + idx * 5 + 3) % 13 == 0:
                    d.point([rx + 6, py + 7], fill=pal['glow_core'])
                if (f + idx * 11 + 7) % 19 == 0:
                    d.point([rx + 11, py + 4], fill=pal['glow_core'])

        out_frames.append(im)
    return out_frames

def main():
    print("Generating Infinium 128x64 Wing Textures (Symmetric Mecha vs Asymmetric Celestial)...")
    for st in PALETTES.keys():
        print(f" -> Style: {st} (symmetric={PALETTES[st]['symmetric']})")
        # 1. Base texture
        base_im = make_base_texture(st)
        base_im.save(os.path.join(ROOT, f"base_{st}.png"))
        
        # 2. Accent gray texture
        gray_im = make_gray_texture(base_im)
        gray_im.save(os.path.join(ROOT, f"accent_gray_{st}.png"))
        
        # 3. Glow frames
        glow_dir = os.path.join(ROOT, "glow", st)
        glow_gray_dir = os.path.join(ROOT, "glow_gray", st)
        shutil.rmtree(glow_dir, ignore_errors=True)
        shutil.rmtree(glow_gray_dir, ignore_errors=True)
        os.makedirs(glow_dir, exist_ok=True)
        os.makedirs(glow_gray_dir, exist_ok=True)
        
        frames = make_glow_frames(st)
        for i, fr in enumerate(frames):
            fr.save(os.path.join(glow_dir, f"{i}.png"))
            fr_gray = make_gray_texture(fr)
            fr_gray.save(os.path.join(glow_gray_dir, f"{i}.png"))
            
    print("All 128x64 wing textures successfully generated!")

if __name__ == "__main__":
    main()
