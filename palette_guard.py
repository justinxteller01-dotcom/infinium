# Infinium palette rule (Justin, 2026-10-28): only purple, pink, magenta and black (+ shades). No cyan/teal,
# no gold, no red-orange. Shared by generators so rebuilds stay compliant. Player dye tints are applied at
# runtime and are not affected. Neutral pixels (low saturation: black/gray/white) are allowed.
import colorsys
import numpy as np

ALLOWED_HUE = (255.0, 345.0)   # degrees: purple -> magenta -> pink
TARGET_HUE = 320.0             # banned hues are moved here (pink-magenta), keeping saturation and value
MIN_SAT = 0.20                 # below this a pixel counts as neutral
MIN_VAL = 0.10

def classify(h_deg):
    if 150 <= h_deg < 220: return 'cyan'
    if 40 <= h_deg < 70: return 'gold'
    if h_deg < 40 or h_deg >= 345: return 'red_orange'
    if ALLOWED_HUE[0] <= h_deg < ALLOWED_HUE[1]: return 'ok'
    return 'other'   # green / blue: also banned

def fix_rgb(rgb):
    """(r,g,b) 0..255 -> compliant (r,g,b)."""
    r, g, b = [c / 255.0 for c in rgb[:3]]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if s < MIN_SAT or v < MIN_VAL or classify(h * 360) == 'ok': return tuple(int(c) for c in rgb[:3])
    r, g, b = colorsys.hsv_to_rgb(TARGET_HUE / 360.0, s, v)
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))

def fix_hex(c):
    r, g, b = fix_rgb(((c >> 16) & 255, (c >> 8) & 255, c & 255))
    return (c & ~0xFFFFFF) | (r << 16) | (g << 8) | b

def _hsv(a):
    rgb = a[..., :3].astype(np.float32) / 255.0
    mx = rgb.max(-1); mn = rgb.min(-1); d = mx - mn
    s = np.where(mx > 0, d / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    dd = np.maximum(d, 1e-6)
    h = np.where(mx == r, ((g - b) / dd) % 6, np.where(mx == g, (b - r) / dd + 2, (r - g) / dd + 4)) * 60.0
    return h, s, mx

def banned_mask(a):
    """RGBA uint8 array -> bool mask of visible, saturated pixels outside the allowed hue band."""
    h, s, v = _hsv(a)
    vis = a[..., 3] > 0 if a.shape[-1] == 4 else np.ones(a.shape[:2], bool)
    return vis & (s >= MIN_SAT) & (v >= MIN_VAL) & ~((h >= ALLOWED_HUE[0]) & (h < ALLOWED_HUE[1]))

def counts(a):
    h, s, v = _hsv(a)
    m = banned_mask(a)
    return {'cyan': int((m & (h >= 150) & (h < 220)).sum()), 'gold': int((m & (h >= 40) & (h < 70)).sum()),
            'red_orange': int((m & ((h < 40) | (h >= 345))).sum()), 'other': int(m.sum())}

def fix_array(a):
    """Vectorized: RGBA/RGB uint8 array -> compliant copy (hue moved to TARGET_HUE, s and v kept)."""
    a = a.copy(); m = banned_mask(a)
    if not m.any(): return a
    _, s, v = _hsv(a)
    hh = TARGET_HUE / 60.0; i = int(hh) % 6; f = hh - int(hh)
    p = v * (1 - s); q = v * (1 - s * f); t = v * (1 - s * (1 - f))
    rgb = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i]
    for c in range(3): a[..., c] = np.where(m, np.clip(np.round(rgb[c] * 255), 0, 255), a[..., c]).astype(np.uint8)
    return a
