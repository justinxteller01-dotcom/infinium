#!/usr/bin/env python3
"""Procedural Infinium sword combo audio (original synthesis, numpy + ffmpeg libvorbis).
Writes per armor family: <fam>_slash_launch (slash 1), <fam>_slash2_launch (slash 2), <fam>_stab_launch (stab 3),
<fam>_slash_charge (subtle charge-up under the cooldown, no 'ready' ping), <fam>_stab_charge (longer post-stab
charge under the twirl lockout), plus the shared fallbacks. Run from repo root: python3 tools/gen_sword_combo_sounds.py"""
import numpy as np, subprocess, os, tempfile, wave
SR = 44100
OUT = 'src/main/resources/assets/infinium/sounds/sword/'
rng = np.random.default_rng(26)

def t(d): return np.arange(int(SR * d)) / SR
def env(n, a, r, curve=3.0):
    x = np.linspace(0, 1, n); ai = max(1, int(a * n))
    e = np.ones(n); e[:ai] = np.linspace(0, 1, ai) ** 0.6
    e[ai:] = (1 - np.linspace(0, 1, n - ai)) ** curve
    return e
def sweep(f0, f1, d, wave_='sin'):
    tt = t(d); f = f0 * (f1 / f0) ** (tt / d); ph = 2 * np.pi * np.cumsum(f) / SR
    if wave_ == 'saw': return 2 * ((ph / (2 * np.pi)) % 1) - 1
    return np.sin(ph)
def bandnoise(d, lo, hi):
    n = int(SR * d); X = np.fft.rfft(rng.standard_normal(n)); fr = np.fft.rfftfreq(n, 1 / SR)
    X[(fr < lo) | (fr > hi)] = 0; y = np.fft.irfft(X, n); return y / (np.abs(y).max() + 1e-9)
def movnoise(d, f0, f1, q=0.35):
    """whoosh: noise whose band centre glides f0 -> f1 (STFT-free: sum of short band chunks with crossfade)."""
    n = int(SR * d); out = np.zeros(n); seg = 2048; hop = 1024; win = np.hanning(seg)
    for i in range(0, n - seg, hop):
        c = f0 * (f1 / f0) ** (i / n); out[i:i + seg] += bandnoise(seg / SR, c * (1 - q), c * (1 + q)) * win
    return out / (np.abs(out).max() + 1e-9)
def sat(x, k): return np.tanh(k * x) / np.tanh(k)
def norm(x, peak=0.89): return x / (np.abs(x).max() + 1e-9) * peak
def fade_tail(x, ms=25):
    k = int(SR * ms / 1000); x[-k:] *= np.linspace(1, 0, k); return x

THEME = {   # base pitch, brightness, grit, sub weight, shimmer
    'astral':      dict(p=1.25, bright=1.4, grit=1.2, sub=0.25, shim=0.9),
    'cybernetic':  dict(p=1.10, bright=1.2, grit=2.2, sub=0.35, shim=0.2),
    'singularity': dict(p=0.70, bright=0.7, grit=1.6, sub=0.95, shim=0.15),
    'cyberpsycho': dict(p=0.95, bright=1.0, grit=3.5, sub=0.6, shim=0.25),
    'infinity':    dict(p=0.95, bright=1.1, grit=1.6, sub=0.75, shim=0.7),
}

def slash(th, variant):
    d = 0.62; n = int(SR * d); p = th['p'] * (1.0 if variant == 1 else 1.12)
    a, b = (2600, 380) if variant == 1 else (420, 3000)            # slash 2 sweeps the other way: chained, not a copy
    w = movnoise(d, a * th['bright'], b * th['bright']) * env(n, 0.18, 0.82, 2.2)
    body = sweep(220 * p, 70 * p, d, 'saw') * env(n, 0.05, 0.95, 3.5)
    body = np.convolve(body, np.ones(24) / 24, 'same')               # soften the saw
    sub = sweep(95 * p, 40 * p, d) * env(n, 0.02, 0.98, 4) * th['sub']
    tt = t(d)
    shim = sum(np.sin(2 * np.pi * f * p * tt) for f in (1760, 2637, 3520)) / 3 * env(n, 0.1, 0.9, 2.5) * th['shim'] * 0.35
    crack = bandnoise(d, 3000, 9000) * env(n, 0.005, 0.995, 18) * 0.5
    x = 0.85 * w + 0.55 * body + sub + shim + crack
    return fade_tail(norm(sat(x, th['grit'])))

def stab(th):
    d = 0.95; n = int(SR * d); p = th['p']; tt = t(d)
    thrust = movnoise(d, 900 * th['bright'], 5200 * th['bright'], 0.25) * env(n, 0.04, 0.96, 3.0)
    boom = sweep(150 * p, 32 * p, d) * env(n, 0.003, 0.997, 2.2) * (0.8 + th['sub'])
    rail = sweep(900 * p, 3800 * p, 0.12)
    rail = np.pad(rail * env(len(rail), 0.02, 0.98, 2), (0, n - len(rail))) * 0.45
    ring = sum(np.sin(2 * np.pi * f * p * tt) * np.exp(-tt * k) for f, k in ((330, 4), (495, 5), (990, 7))) * 0.35
    shim = np.sin(2 * np.pi * 2093 * p * tt) * np.exp(-tt * 5) * th['shim'] * 0.4
    click = bandnoise(d, 1500, 11000) * env(n, 0.002, 0.998, 30) * 0.9
    x = thrust * 0.7 + boom + rail + ring + shim + click
    return fade_tail(norm(sat(x, th['grit'] * 1.2), 0.93))

def charge(th, d):
    """Subtle rising energy under the cooldown. Swells and fades out; deliberately NO completion ping."""
    n = int(SR * d); p = th['p']; tt = t(d)
    shape = np.sin(np.pi * np.clip(tt / d, 0, 1)) ** 1.5            # in, peak ~60%, out to silence
    hum = (sweep(110 * p, 180 * p, d) + 0.5 * sweep(220 * p, 360 * p, d)) * shape
    air = movnoise(d, 500 * th['bright'], 1800 * th['bright'], 0.3) * shape * 0.35
    lfo = 1 + 0.25 * np.sin(2 * np.pi * (6 + 10 * tt / d) * tt) * (th['grit'] / 3.5)
    shim = np.sin(2 * np.pi * 1320 * p * tt) * shape * th['shim'] * 0.2
    x = (hum * lfo + air + shim)
    return fade_tail(norm(sat(x, 1.3), 0.5), 60)

def write_ogg(path, x):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: tmp = f.name
    with wave.open(tmp, 'wb') as wv:
        wv.setnchannels(1); wv.setsampwidth(2); wv.setframerate(SR)
        wv.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.unlink(tmp)

if __name__ == '__main__':
    for fam, th in THEME.items():
        write_ogg(OUT + fam + '_slash_launch.ogg', slash(th, 1))
        write_ogg(OUT + fam + '_slash2_launch.ogg', slash(th, 2))
        write_ogg(OUT + fam + '_stab_launch.ogg', stab(th))
        write_ogg(OUT + fam + '_slash_charge.ogg', charge(th, 0.65))
        write_ogg(OUT + fam + '_stab_charge.ogg', charge(th, 1.5))
    th = THEME['infinity']
    write_ogg(OUT + 'slash_launch.ogg', slash(th, 1)); write_ogg(OUT + 'stab_launch.ogg', stab(th))
    write_ogg(OUT + 'slash_charge.ogg', charge(th, 0.65)); write_ogg(OUT + 'stab_charge.ogg', charge(th, 1.5))
    print('ok')
