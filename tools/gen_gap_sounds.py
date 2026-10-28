#!/usr/bin/env python3
"""Synth the 20 crystal sounds: assets/infinium/sounds/crystal/<set>_<kind>.ogg (mono 48 kHz vorbis).
kinds: insert (short), perk (slightly grander), full (much grander), denied (refuse, non-crystal)."""
import os, subprocess, tempfile, wave
import numpy as np
SR = 48000
rng = np.random.default_rng(26)

def t_(d): return np.arange(int(SR * d)) / SR
def env(n, a=0.005, r=0.3, d=None):
    t = np.arange(n) / SR; d = d or n / SR
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / max(r, 1e-3))
    tail = np.clip((d - t) / 0.03, 0, 1); return e * tail
def sine(f, d, ph=0): t = t_(d); return np.sin(2 * np.pi * f * t + ph)
def sweep(f0, f1, d, kind='sin'):
    t = t_(d); f = f0 * (f1 / f0) ** (t / d); ph = 2 * np.pi * np.cumsum(f) / SR
    ph = ph[:len(t)]
    if kind == 'sq': return np.sign(np.sin(ph)) * 0.6
    if kind == 'saw': return 2 * ((ph / (2 * np.pi)) % 1) - 1
    return np.sin(ph)
def bell(f, d, r=0.6):
    s = sum(a * sine(f * m, d) * env(int(SR * d), 0.002, r / (1 + k)) for k, (m, a) in enumerate([(1, 1), (2.76, .45), (5.4, .25), (8.93, .12)]))
    return s
def fm(fc, fmod, idx, d):
    t = t_(d); return np.sin(2 * np.pi * fc * t + idx * np.sin(2 * np.pi * fmod * t))
def crush(x, bits=5, hold=4):
    q = np.round(x * 2 ** bits) / 2 ** bits; return np.repeat(q[::hold], hold)[:len(x)]
def dist(x, g=4): return np.tanh(x * g) / np.tanh(g)
def reverb(x, size=0.25, mix=0.35):
    y = x.copy()
    for dl, g in [(0.029, .5), (0.037, .45), (0.041, .4), (0.053, .35)]:
        k = int(dl * SR * (1 + size * 4)); b = np.zeros(len(x) + k * 8)
        b[:len(x)] = x
        for i in range(k, len(b)): b[i] += g * b[i - k] if i - k < len(b) else 0
        y = y + mix * b[:len(x)] / 4
    return y
def fast_reverb(x, tail=1.0, mix=0.3):
    n = int(SR * tail); ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * 6 / tail)
    w = np.convolve(x, ir)[:len(x) + n] * 0.02
    out = np.zeros(len(w)); out[:len(x)] += x; out += mix * w; return out
def pad(x, d): n = int(SR * d); return np.pad(x, (0, max(0, n - len(x))))[:n] if len(x) < n else x
def at(buf, x, sec):
    i = int(sec * SR); end = min(len(buf), i + len(x)); buf[i:end] += x[:end - i]; return buf
def noise(d): return rng.standard_normal(int(SR * d))
def lp(x, a=0.1):
    y = np.zeros_like(x); acc = 0.0
    for i, v in enumerate(x): acc += a * (v - acc); y[i] = acc
    return y

# --- per set voice ---------------------------------------------------------------------------
def astral(f, d, grand=0):   # glassy star chimes
    s = bell(f, d, 0.5 + 0.4 * grand)
    for k in range(3 + 6 * grand):
        s = at(s, 0.25 * bell(f * 2 ** (rng.integers(0, 3)) * [1, 1.25, 1.5, 2][k % 4], max(0.2, d - 0.04 * k), 0.25), 0.03 * k)
    return s
def cybernetic(f, d, grand=0):   # clean digital bleeps
    s = fm(f, f * 2, 1.5 + grand, d) * env(int(SR * d), 0.002, 0.15 + 0.2 * grand)
    s += 0.4 * crush(sweep(f * 2, f * 3, d, 'sq'), 6, 2) * env(int(SR * d), 0.001, 0.06 + 0.1 * grand)
    return s
def singularity(f, d, grand=0):   # deep gravitational pull
    s = sweep(f * 1.6, f * 0.5, d) * env(int(SR * d), 0.02, 0.4 + 0.5 * grand)
    s += 0.5 * sweep(f * 0.5, f * 0.25, d) * env(int(SR * d), 0.05, 0.6 + 0.6 * grand)
    s += 0.25 * lp(noise(d), 0.02) * env(int(SR * d), 0.1, 0.3 + 0.4 * grand) * 4
    return s
def cyberpsycho(f, d, grand=0):   # distorted glitch saw
    s = dist(sweep(f, f * (1.5 + grand), d, 'saw') * env(int(SR * d), 0.001, 0.12 + 0.2 * grand), 5)
    g = crush(noise(d) * 0.3, 3, 40) * env(int(SR * d), 0.001, 0.05 + 0.05 * grand)
    return s * 0.7 + g
def infinity(f, d, grand=0):   # cosmic chord, all sets in one
    s = sum(sine(f * r, d) * env(int(SR * d), 0.01 + 0.05 * grand, 0.5 + 0.6 * grand) / (1 + i) for i, r in enumerate([1, 1.5, 2, 2.5, 3]))
    s += 0.3 * bell(f * 4, d, 0.4) + 0.2 * sweep(f * 0.5, f * 0.25, d) * env(int(SR * d), 0.05, 0.5)
    return s
VOICE = {'astral': (astral, 880), 'cybernetic': (cybernetic, 660), 'singularity': (singularity, 220),
         'cyberpsycho': (cyberpsycho, 330), 'infinity': (infinity, 440)}

def make(set_, kind):
    v, f = VOICE[set_]
    if kind == 'insert':
        buf = v(f, 0.35)
        out = fast_reverb(buf, 0.4, 0.15)
    elif kind == 'perk':   # slightly grander: two-note rise + shimmer
        buf = np.zeros(int(SR * 0.9)); at(buf, v(f, 0.5), 0); at(buf, v(f * 1.5, 0.6, 1) * 0.9, 0.12)
        out = fast_reverb(buf, 0.7, 0.25)
    elif kind == 'full':   # much grander: arpeggio + swell + big chord
        buf = np.zeros(int(SR * 2.6))
        for i, r in enumerate([1, 1.25, 1.5, 2]): at(buf, v(f * r, 0.6, 1) * 0.7, 0.09 * i)
        sw = sweep(f * 0.25, f, 0.5) * np.linspace(0, 1, int(SR * 0.5)) ** 2 * 0.5; at(buf, sw, 0.0)
        chord = sum(v(f * r, 1.9, 2) for r in [1, 1.5, 2]) / 2; at(buf, chord, 0.45)
        out = fast_reverb(buf, 1.4, 0.35)
    else:   # denied: two descending dull tones in the set's timbre
        buf = np.zeros(int(SR * 0.4)); lo = f / 2 if f > 300 else f
        at(buf, lp(v(lo, 0.14), 0.25), 0); at(buf, lp(v(lo * 0.75, 0.2), 0.25), 0.15)
        out = buf
    out = out / (np.max(np.abs(out)) + 1e-9) * {'insert': .7, 'perk': .8, 'full': .95, 'denied': .6}[kind]
    fade = min(len(out), int(SR * 0.05)); out[-fade:] *= np.linspace(1, 0, fade)
    return out

def write(path, x):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp: wav = tmp.name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.unlink(wav)

# ---------------------------------------------------------------------------------------------
# gen_gap_sounds.py: the 13 sounds that were vanilla stand-ins (batch1):
#   cyberpsycho/thrust_{start,loop,stop}, perk/unlock_<set>, perk/maxed_<set>.
# Helpers above are copied from g4work/sndcheck/gen_crystal_sounds.py (same voices, same
# mono 48 kHz vorbis, same peak normalisation) so these match the existing crystal/flight files.
# Pattern: one bassy "Fire Force" foundation (sub thump + saturated low swell) under everything;
# cyber sets tilt electric, Astral/Singularity spacey, Infinity both. Run from the repo root.
# Only writes .ogg files; never touches sounds.json.
OUT = 'src/main/resources/assets/infinium/sounds'
SETS = ['astral', 'cybernetic', 'singularity', 'cyberpsycho', 'infinity']
ROOT = {'astral': 55.0, 'cybernetic': 49.0, 'singularity': 41.2, 'cyberpsycho': 46.2, 'infinity': 55.0}

def foundation(d, f=55.0, hit=1.0, swell=0.0):
    """Fire Force base: pitched-down sub kick + saturated low body, optional rising swell."""
    n = int(SR * d); t = np.arange(n) / SR
    kick = np.sin(2 * np.pi * np.cumsum(f + 90 * np.exp(-t * 30)) / SR) * np.exp(-t * (6 / max(hit, .2)))
    body = dist(sine(f, d) + 0.5 * sine(f * 2, d), 2.5) * env(n, 0.01, 0.35 * hit + 0.4 * swell)
    s = kick + 0.45 * body
    if swell: s += 0.35 * lp(noise(d), 0.04) * 3 * np.minimum(1, t / max(swell, .01)) * np.exp(-t * 1.5)
    return s

def zap(d, f=1800, rate=60):   # electric crackle (cyber tilt)
    n = int(SR * d); g = (rng.random(n) < rate / SR).astype(float)
    g = np.convolve(g, np.exp(-np.arange(200) / 25))[:n]
    return crush(fm(f, f * 0.51, 3, d), 5, 3) * 0.3 * env(n, 0.002, d / 2) + g * rng.standard_normal(n) * 0.25

def tilt(set_, d, grand=0):
    n = int(SR * d)
    if set_ in ('cybernetic', 'cyberpsycho'):
        return zap(d, 1400 if set_ == 'cybernetic' else 900, 40 + 80 * grand) * (1.4 if set_ == 'cyberpsycho' else 1)
    if set_ in ('astral', 'singularity'):   # spacey: airy shimmer / gravity sweep
        sh = sum(bell(f, d, 0.4 + .5 * grand) * a for f, a in [(1760, .25), (2637, .15), (3520, .1)])
        return sh if set_ == 'astral' else 0.6 * sweep(600, 120, d) * env(n, 0.02, 0.5 + grand) + 0.4 * sh
    return 0.6 * tilt('astral', d, grand) + 0.6 * tilt('cybernetic', d, grand)

def norm(x, peak):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    f = min(len(x), int(SR * 0.05)); x[-f:] *= np.linspace(1, 0, f); return x

def perk_unlock(s):   # short, quick: thump + set voice two-note rise + tilt
    v, f = VOICE[s]; d = 0.9; buf = np.zeros(int(SR * d))
    at(buf, foundation(0.6, ROOT[s], 0.6) * 0.9, 0)
    at(buf, v(f, 0.4) * 0.6, 0.02); at(buf, v(f * 1.5, 0.5, 1) * 0.6, 0.13)
    at(buf, tilt(s, 0.6) * 0.5, 0.05)
    return norm(fast_reverb(buf, 0.6, 0.2), 0.8)

def set_maxed(s):   # bigger: swell into a heavy double hit + chord + long tilt tail
    v, f = VOICE[s]; d = 3.0; buf = np.zeros(int(SR * d))
    at(buf, foundation(0.7, ROOT[s], 0.4, swell=0.6) * 0.6, 0)
    at(buf, foundation(2.2, ROOT[s], 2.0) * 1.2, 0.55); at(buf, foundation(1.5, ROOT[s] * 0.75, 1.5) * 0.8, 0.8)
    for i, r in enumerate([1, 1.25, 1.5, 2]): at(buf, v(f * r, 0.5, 1) * 0.5, 0.06 * i)
    at(buf, sum(v(f * r, 2.0, 2) for r in (1, 1.5, 2)) / 2 * 0.8, 0.55)
    at(buf, tilt(s, 2.2, 1) * 0.7, 0.55)
    return norm(fast_reverb(buf, 1.4, 0.35), 0.95)

# --- Cyber Psycho thrust: electric start, seamless hum, smooth power-down -----------------------
LOOP_D = 3.0; F0 = 1 / LOOP_D
def per(freq): return round(freq / F0) * F0   # every partial an exact multiple of 1/3 Hz -> seamless
def cp_hum(d=LOOP_D, start=0.0):
    t = np.arange(int(SR * d)) / SR + start
    s = 0.55 * np.sin(2 * np.pi * per(46.2) * t) + 0.35 * np.sin(2 * np.pi * per(92.4) * t)
    s += 0.25 * dist(np.sin(2 * np.pi * per(138.6) * t) + 0.6 * np.sin(2 * np.pi * per(277.2) * t), 2)   # electric buzz
    s += 0.06 * np.sin(2 * np.pi * per(1108.8) * t + 2 * np.sin(2 * np.pi * per(4) * t))               # soft whine
    return s * (0.88 + 0.12 * np.cos(2 * np.pi * per(1.0) * t))                                         # gentle breathing

def cp_loop():
    x = cp_hum()   # exactly periodic, no crossfade needed
    return x / (np.max(np.abs(x)) + 1e-9) * 0.75

def cp_start():   # obvious electric cue: zap crackle + rising FM charge + bass thump, settles into hum
    d = 1.0; n = int(SR * d); buf = np.zeros(n)
    at(buf, foundation(0.5, 46.2, 0.5) * 0.9, 0)
    at(buf, zap(0.35, 900, 300) * 2.0, 0)
    ch = fm(600, 300, 4, 0.4) * env(int(SR * .4), .005, .15)
    at(buf, crush(sweep(300, 1600, 0.4, 'sq'), 5, 2) * 0.35 * env(int(SR * .4), .002, .2), 0.02)
    at(buf, ch * 0.3, 0.02)
    hum = cp_hum(0.7) / 1.3 * 0.6 * np.linspace(0, 1, int(SR * .7)) ** 0.5; at(buf, hum, 0.3)
    buf = dist(buf / (np.max(np.abs(buf)) + 1e-9), 3.0)   # glue + loudness, like the crystal cues
    return norm(buf, 0.85)

def cp_stop():   # smooth power-down: hum pitches down and fades, soft low thud
    d = 1.4; n = int(SR * d); t = np.arange(n) / SR
    k = np.linspace(1, 0.35, n) ** 1.5; ph = 2 * np.pi * np.cumsum(46.2 * k) / SR
    s = 0.55 * np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.25 * dist(np.sin(3 * ph) + 0.6 * np.sin(6 * ph), 2)
    s *= np.exp(-t * 2.2) * np.minimum(1, t / 0.01)
    s += np.pad(zap(0.15, 700, 120) * 0.4, (0, n - int(SR * .15)))
    return norm(lp(s, 0.25) * 2, 0.75)

if __name__ == '__main__':
    os.makedirs(OUT + '/cyberpsycho', exist_ok=True); os.makedirs(OUT + '/perk', exist_ok=True)
    write(OUT + '/cyberpsycho/thrust_start.ogg', cp_start())
    write(OUT + '/cyberpsycho/thrust_loop.ogg', cp_loop())
    write(OUT + '/cyberpsycho/thrust_stop.ogg', cp_stop())
    for s in SETS:
        write(f'{OUT}/perk/unlock_{s}.ogg', perk_unlock(s)); write(f'{OUT}/perk/maxed_{s}.ogg', set_maxed(s))
    print('gap sounds: 13 written')
