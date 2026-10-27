#!/usr/bin/env python3
"""
Generates high-resolution 3D renders and animated GIFs for the 5 custom Infinium wing designs:
1. Astral: Celestial Feathered Wings (Scapular bone, secondary plumage fan, crest quill, primary star pinions)
2. Cybernetic: Mecha Vector Thrusters (Titanium chassis, variable exhaust nozzle, stabilizer fin, flare blades)
3. Singularity: Gravitational Void Scythes (Accretion spine, dark matter plumules, event-horizon blade, warp barbs)
4. CyberPsycho: Overclocked Razor Mecha (Obsidian manifold, serrated fin, hazard heatsink teeth, kinetic blades)
5. Infinity: Grand Divine Archangel Synthesis (9-piece hybrid of void plumage and kinetic hardlight blades)

Demonstrates:
- Perk milestone progression (physical pieces dynamically added as perks unlock)
- Dynamic per-crystal opacity scaling (translucent 50%-60% up to 90% at max Infinity)
- Zero-clipping resting V-stance with 3 px spinal clearance & 100% waist leg clearance
- Expansive in-flight aerodynamic glide pose
"""

import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ARTIFACT_DIR = r"C:\Users\justi\.gemini\antigravity\brain\80bb1dc4-9ed5-43bd-ac57-d4c645e461c8"
os.makedirs(ARTIFACT_DIR, exist_ok=True)

STYLES = {
    "astral": {
        "name": "Astral Celestial Feathered Wings",
        "desc": "Graceful curved celestial bone and layered organic plumage framing the spine.",
        "color": (160, 90, 255),
        "accent": (220, 180, 255),
        "base_opacity": 0.50,
        "max_opacity": 0.88,
        "max_perks": 4
    },
    "cybernetic": {
        "name": "Cybernetic Mecha Vector Thrusters",
        "desc": "Angular mecha intake casing with articulated dual vector stabilizer flaps and exhaust nozzles.",
        "color": (210, 60, 240),
        "accent": (255, 120, 255),
        "base_opacity": 0.50,
        "max_opacity": 0.88,
        "max_perks": 4
    },
    "singularity": {
        "name": "Singularity Gravitational Void Scythes",
        "desc": "Void-curved gravitational accretion scythes with dark matter plumage and warp barbs.",
        "color": (110, 30, 210),
        "accent": (180, 80, 255),
        "base_opacity": 0.55,
        "max_opacity": 0.88,
        "max_perks": 4
    },
    "cyberpsycho": {
        "name": "CyberPsycho Overclocked Razor Mecha",
        "desc": "Aggressive overclocked kinetic blades with jagged hazard steps, serrated teeth, and heatsinks.",
        "color": (255, 40, 180),
        "accent": (255, 140, 220),
        "base_opacity": 0.55,
        "max_opacity": 0.88,
        "max_perks": 4
    },
    "infinity": {
        "name": "Infinity Grand Divine Archangel",
        "desc": "Transcendent 9-piece synthesis fusing Singularity void feathers and CyberPsycho kinetic blades.",
        "color": (230, 140, 255),
        "accent": (255, 230, 255),
        "base_opacity": 0.60,
        "max_opacity": 0.90,
        "max_perks": 8
    }
}

def rotate_point(p, rx, ry, rz):
    x, y, z = p
    # Z rotation
    cz, sz = math.cos(rz), math.sin(rz)
    x, y = x * cz - y * sz, x * sz + y * cz
    # Y rotation
    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy
    # X rotation
    cx, sx = math.cos(rx), math.sin(rx)
    y, z = y * cx - z * sx, y * sx + z * cx
    return (x, y, z)

class Box3D:
    def __init__(self, x0, y0, z0, w, h, d, color, alpha=1.0):
        self.x0, self.y0, self.z0 = x0, y0, z0
        self.w, self.h, self.d = w, h, d
        self.color = color
        self.alpha = alpha

    def get_quads(self):
        x, y, z, w, h, d = self.x0, self.y0, self.z0, self.w, self.h, self.d
        c = [
            (x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z),
            (x, y, z + d), (x + w, y, z + d), (x + w, y + h, z + d), (x, y + h, z + d)
        ]
        faces = [
            ([c[0], c[1], c[2], c[3]], 0.90), # Front -z
            ([c[5], c[4], c[7], c[6]], 0.80), # Back +z
            ([c[4], c[0], c[3], c[7]], 0.70), # Left -x
            ([c[1], c[5], c[6], c[2]], 0.85), # Right +x
            ([c[4], c[5], c[1], c[0]], 1.00), # Top -y
            ([c[3], c[2], c[6], c[7]], 0.60), # Bottom +y
        ]
        return faces

def get_wing_boxes(style_name, side='L', perks_unlocked=4):
    """
    Returns boxes matching InfiniumWingModels.java.
    side is 'L' (x > 0) or 'R' (mirrored, x < 0).
    Only includes pieces up to perks_unlocked.
    """
    sgn = 1 if side == 'L' else -1
    boxes = []
    
    def add(idx, x, y, z, w, h, d, col, alpha=1.0):
        if idx <= perks_unlocked:
            boxes.append(Box3D(x * sgn, y, z, w * sgn, h, d, col, alpha))

    if style_name == "astral":
        # piece_0: Scapular bone & inner down feathers (compact 9 px)
        add(0, 0.0, 0.0, 0.0, 3.5, 9.0, 1.2, (180, 110, 255))
        # piece_1: Secondary soft plumage fan
        add(1, 1.5, 2.0, 0.3, 4.0, 7.5, 0.8, (215, 160, 255))
        # piece_2: Astral crest quill spine
        add(2, -0.5, -2.0, -0.2, 2.0, 5.0, 0.8, (240, 210, 255))
        # piece_3: Outer celestial coverts
        add(3, 3.0, 4.0, -0.2, 3.5, 6.0, 0.6, (195, 130, 255))
        # piece_4: Primary star pinions
        add(4, 4.5, 1.0, 0.2, 4.0, 10.0, 0.6, (255, 235, 255))

    elif style_name == "cybernetic":
        # piece_0: Core titanium vector thruster housing (compact 8.5 px)
        add(0, 0.0, 0.0, 0.0, 4.0, 8.5, 1.8, (140, 70, 180))
        # piece_1: Variable-geometry exhaust nozzle
        add(1, 0.5, 8.0, 0.3, 3.0, 3.0, 1.4, (255, 120, 255))
        # piece_2: Deployable aerodynamic stabilizer fin
        add(2, 3.0, 1.0, 0.4, 3.0, 9.0, 0.8, (220, 80, 240))
        # piece_3: Micro-thruster vector pod & conduits
        add(3, -0.5, 2.0, -0.4, 1.8, 4.5, 0.8, (180, 50, 210))
        # piece_4: Lateral hardlight stabilizer flare blades
        add(4, 4.5, 3.0, 0.2, 3.5, 8.0, 0.6, (255, 150, 255))

    elif style_name == "singularity":
        # piece_0: Accretion spine & inner void plumage (compact 9.5 px)
        add(0, 0.0, 0.0, 0.0, 4.0, 9.5, 1.4, (75, 20, 150))
        # piece_1: Intermediate dark matter plumules
        add(1, 1.5, 2.5, 0.4, 4.5, 7.5, 0.9, (135, 45, 220))
        # piece_2: Event horizon scythe barb crest
        add(2, -0.5, -2.5, -0.3, 2.2, 6.0, 0.9, (180, 70, 255))
        # piece_3: Radiant null-field warp barbs
        add(3, 3.5, 3.5, -0.3, 4.0, 6.5, 0.7, (150, 55, 240))
        # piece_4: Massive gravitational void scythe wingspan
        add(4, 5.0, 0.5, 0.2, 4.5, 11.0, 0.7, (220, 130, 255))

    elif style_name == "cyberpsycho":
        # piece_0: Overclocked obsidian manifold chassis (9 px)
        add(0, 0.0, 0.0, 0.0, 4.2, 9.0, 1.8, (190, 30, 140))
        # piece_1: Lower serrated kinetic razor fin
        add(1, 0.5, 8.5, 0.2, 3.2, 3.2, 1.2, (255, 80, 210))
        # piece_2: Upper hazardous heatsink tooth
        add(2, -0.5, -1.8, -0.3, 2.0, 4.0, 1.0, (255, 120, 230))
        # piece_3: Twin split overdrive flaps
        add(3, 3.2, 1.5, 0.3, 3.8, 8.5, 0.9, (220, 40, 170))
        # piece_4: Dual overdrive kinetic razor wingtip blades
        add(4, 5.0, 2.5, -0.2, 4.2, 8.0, 0.7, (255, 160, 240))

    elif style_name == "infinity":
        # piece_0: Divine Archangel void spine & core chassis (10 px)
        add(0, 0.0, 0.0, 0.0, 4.0, 10.0, 1.6, (140, 40, 210))
        # piece_1: Lower mecha exhaust & void down
        add(1, 0.5, 9.0, 0.3, 3.0, 3.0, 1.2, (255, 100, 230))
        # piece_2: Upper radiant crest & heatsink
        add(2, -0.5, -2.2, -0.3, 2.2, 5.0, 1.0, (255, 180, 255))
        # piece_3: Inner void scythe plumage
        add(3, 2.0, 2.0, 0.4, 4.0, 8.0, 0.9, (180, 60, 240))
        # piece_4: Kinetic razor stabilizer flaps
        add(4, 3.5, 1.0, -0.3, 3.5, 9.0, 0.8, (240, 90, 220))
        # piece_5: Null-field warp barbs
        add(5, 4.5, 3.5, 0.3, 3.8, 7.0, 0.7, (200, 80, 255))
        # piece_6: Lateral hardlight vector corona
        add(6, 5.5, 0.5, -0.2, 4.0, 10.5, 0.7, (255, 140, 240))
        # piece_7: Void archangel primaries with serrated tips
        add(7, 6.5, 2.0, 0.2, 4.5, 10.0, 0.6, (220, 120, 255))
        # piece_8: Max radiant celestial solar corona blades
        add(8, 7.5, 4.0, -0.1, 3.5, 7.5, 0.5, (255, 230, 255))

    return boxes

def render_scene(style_key, is_flight=False, perks_unlocked=4, opacity=0.85, view_angle=(0.25, 0.55), size=(500, 500)):
    """
    Renders an isometric 3D view of the player torso with custom wings.
    """
    img = Image.new("RGBA", size, (16, 10, 24, 255))
    draw = ImageDraw.Draw(img)

    # Torso (Player reference body)
    torso_boxes = [
        Box3D(-4, 0, -2, 8, 12, 4, (40, 32, 55)),
        Box3D(-4, -8, -4, 8, 8, 8, (60, 50, 78)),
        Box3D(-3.9, 12, -2, 3.8, 12, 4, (30, 25, 42)),
        Box3D(0.1, 12, -2, 3.8, 12, 4, (30, 25, 42))
    ]

    # Wing Rotations matching InfiniumWingsRenderer.java
    if is_flight:
        left_rot = (0.35, -0.22, -0.85)
        right_rot = (0.35, 0.22, 0.85)
    else:
        left_rot = (0.16, -0.15, -0.12)
        right_rot = (0.16, 0.15, 0.12)

    # Left & Right Wing boxes
    left_boxes = get_wing_boxes(style_key, 'L', perks_unlocked)
    right_boxes = get_wing_boxes(style_key, 'R', perks_unlocked)

    all_quads = []

    # Add Torso quads
    for b in torso_boxes:
        for q, shade in b.get_quads():
            pts = [rotate_point(pt, view_angle[0], view_angle[1], 0) for pt in q]
            mean_z = sum(p[2] for p in pts) / 4.0
            r, g, b_col = b.color
            color = (int(r * shade), int(g * shade), int(b_col * shade), 255)
            all_quads.append((mean_z, pts, color))

    # Add Left Wing quads
    pivot_L = (1.5, 1.5, 2.2)
    for b in left_boxes:
        for q, shade in b.get_quads():
            posed_pts = []
            for pt in q:
                rx, ry, rz = rotate_point(pt, left_rot[0], left_rot[1], left_rot[2])
                world_pt = (rx + pivot_L[0], ry + pivot_L[1], rz + pivot_L[2])
                cam_pt = rotate_point(world_pt, view_angle[0], view_angle[1], 0)
                posed_pts.append(cam_pt)
            mean_z = sum(p[2] for p in posed_pts) / 4.0
            r, g, b_col = b.color
            alpha_val = int(255 * opacity * b.alpha)
            color = (int(r * shade), int(g * shade), int(b_col * shade), alpha_val)
            all_quads.append((mean_z, posed_pts, color))

    # Add Right Wing quads
    pivot_R = (-1.5, 1.5, 2.2)
    for b in right_boxes:
        for q, shade in b.get_quads():
            posed_pts = []
            for pt in q:
                rx, ry, rz = rotate_point(pt, right_rot[0], right_rot[1], right_rot[2])
                world_pt = (rx + pivot_R[0], ry + pivot_R[1], rz + pivot_R[2])
                cam_pt = rotate_point(world_pt, view_angle[0], view_angle[1], 0)
                posed_pts.append(cam_pt)
            mean_z = sum(p[2] for p in posed_pts) / 4.0
            r, g, b_col = b.color
            alpha_val = int(255 * opacity * b.alpha)
            color = (int(r * shade), int(g * shade), int(b_col * shade), alpha_val)
            all_quads.append((mean_z, posed_pts, color))

    # Painter's Algorithm: Sort back to front (ascending Z)
    all_quads.sort(key=lambda item: item[0])

    cx, cy = size[0] // 2, size[1] // 2 + 30
    scale = 13.0

    poly_layer = Image.new("RGBA", size, (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(poly_layer)

    for mean_z, pts, color in all_quads:
        screen_pts = [(cx + p[0] * scale, cy + p[1] * scale) for p in pts]
        pdraw.polygon(screen_pts, fill=color, outline=(min(255, color[0] + 30), min(255, color[1] + 30), min(255, color[2] + 30), color[3]))

    img = Image.alpha_composite(img, poly_layer)
    return img

def calc_opacity(style_key, perks):
    fam = 4 if style_key == 'infinity' else (2 if style_key == 'singularity' else (3 if style_key == 'cyberpsycho' else (1 if style_key == 'cybernetic' else 0)))
    max_p = 8 if fam == 4 else 4
    tier_frac = perks / float(max_p)
    base_prog = (2.0 / 3.0) if fam == 4 else ((1.0 / 3.0) if fam >= 2 else 0.0)
    overall = base_prog + (1.0 / 3.0) * tier_frac
    return 0.40 + 0.50 * overall

def main():
    print("Generating comprehensive wing renders showing perk piece progression & styles...")
    
    # 1. Render Resting & Flight for all styles at max perks
    for st, data in STYLES.items():
        print(f" -> Rendering Max Perks {st} (Resting & Flight)...")
        max_p = data["max_perks"]
        op = calc_opacity(st, max_p)
        rest_img = render_scene(st, is_flight=False, perks_unlocked=max_p, opacity=op)
        rest_img.save(os.path.join(ARTIFACT_DIR, f"wing_{st}_resting_perk_max.png"))

        flight_img = render_scene(st, is_flight=True, perks_unlocked=max_p, opacity=op)
        flight_img.save(os.path.join(ARTIFACT_DIR, f"wing_{st}_flight_perk_max.png"))

    # 2. Render Perk Progression Steps for each style
    for st, data in STYLES.items():
        max_p = data["max_perks"]
        print(f" -> Rendering progression snapshots for {st}...")
        for p in range(0, max_p + 1):
            op = calc_opacity(st, p)
            img = render_scene(st, is_flight=False, perks_unlocked=p, opacity=op)
            img.save(os.path.join(ARTIFACT_DIR, f"wing_{st}_perk_{p}.png"))

    # 3. Generate high-quality 360-degree Turntable GIFs
    for st, data in STYLES.items():
        print(f" -> Generating turntable GIF for {st}...")
        frames = []
        max_p = data["max_perks"]
        op = calc_opacity(st, max_p)
        steps = 24
        for step in range(steps):
            angle_y = (step / float(steps)) * 2 * math.pi
            fr = render_scene(st, is_flight=False, perks_unlocked=max_p, opacity=op, view_angle=(0.20, angle_y))
            frames.append(fr)
        gif_path = os.path.join(ARTIFACT_DIR, f"wing_{st}_turntable.gif")
        frames[0].save(gif_path, save_all=True, append_images=frames[1:], duration=80, loop=0)

    print("Showcase renders complete!")

if __name__ == "__main__":
    main()
