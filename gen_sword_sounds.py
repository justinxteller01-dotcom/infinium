#!/usr/bin/env python3
"""Generates Fire Force / Crimson Moon style audio suite for the Infinium Sword:
- slash_launch.ogg      (Crimson Moon heavy seismic sub-bass drop + laser cut)
- stab_launch.ogg       (Concussive piercing railgun beam blast, 75% damage beam)
- slash_charge.ogg      (Smooth capacitor vacuum intake buildup, ~0.48s)
- stab_charge.ogg       (25% longer heavier vacuum buildup, ~0.62s)
- sword_spin.ogg        (Fast metallic blade twirl whoosh + crystal shimmer)
- slash_block_hit.ogg   (Gentle muffled energy scorch)
"""
import os, subprocess, tempfile, wave
import sys, zlib
import numpy as np

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
import gen_crystal_sounds as cs

SR = 48000
rng = np.random.default_rng(2026)

def env(n, a=0.005, r=0.1, d=None):
    t = np.arange(n) / SR
    d = d or n / SR
    e = np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / max(r, 1e-3))
    tail = np.clip((d - t) / 0.02, 0.0, 1.0)
    return e * tail

def sine(f, d, ph=0.0):
    t = np.arange(int(SR * d)) / SR
    return np.sin(2 * np.pi * f * t + ph)

def sweep(f0, f1, d, curve=1.0):
    t = np.arange(int(SR * d)) / SR
    progress = (t / d) ** curve
    f = f0 * (f1 / f0) ** progress
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph[:len(t)])

def noise(d):
    return rng.standard_normal(int(SR * d))

def lp(x, a=0.15):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y

def write_ogg(path, x):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1.0, 1.0) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', path], check=True)
    os.unlink(wav)
    print(f'Wrote {path} ({len(x)/SR:.2f}s)')

# 1. Crimson Moon Slash Launch (Deep seismic bass drop + laser cleave)
def gen_slash_launch():
    d = 0.22
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    # Crimson Moon Seismic Sub-bass (145Hz plunging deeply into 32Hz)
    sub_env = np.exp(-t / 0.075) * np.clip((d - t) / 0.03, 0.0, 1.0)
    sub = sweep(145, 32, d, curve=1.4) * sub_env * 1.15
    sig += sub
    
    # Sharp instantaneous laser cleave snap
    click_n = int(SR * 0.007)
    click = noise(0.007) * np.linspace(1.0, 0.0, click_n) ** 2 * 0.70
    sig[:click_n] += click
    
    # Resonant plasma blade slice (780Hz -> 220Hz)
    d_p = 0.16
    n_p = int(SR * d_p)
    t_p = np.arange(n_p) / SR
    p_env = np.minimum(1.0, t_p / 0.003) * np.exp(-t_p / 0.045)
    f_p = 780.0 * (220.0 / 780.0) ** (t_p / d_p)
    ph_p = 2 * np.pi * np.cumsum(f_p) / SR
    plasma = np.sin(ph_p + 1.1 * np.sin(ph_p * 0.5)) * p_env * 0.50
    sig[:n_p] += plasma
    
    # Vacuum displacement swoosh
    d_w = 0.15
    whoosh = lp(noise(d_w), 0.22) * env(int(SR * d_w), 0.004, 0.04) * 0.35
    sig[:len(whoosh)] += whoosh
    
    # Heavy analog saturation
    sig = np.tanh(sig * 2.0) / np.tanh(2.0) * 0.92
    return sig

# 2. Concussive Piercing Stab Beam Launch (75% damage straight laser lance)
def gen_stab_launch():
    d = 0.28
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    # Massive concussive chest thump (180Hz diving into 28Hz)
    thump_env = np.exp(-t / 0.09) * np.clip((d - t) / 0.04, 0.0, 1.0)
    thump = sweep(180, 28, d, curve=1.5) * thump_env * 1.30
    sig += thump
    
    # Supersonic railgun crack
    crack_n = int(SR * 0.012)
    crack = noise(0.012) * np.linspace(1.0, 0.0, crack_n) ** 1.8 * 0.90
    sig[:crack_n] += crack
    
    # Piercing concentrated beam resonance (1200Hz down to 350Hz)
    d_b = 0.22
    n_b = int(SR * d_b)
    t_b = np.arange(n_b) / SR
    b_env = np.minimum(1.0, t_b / 0.002) * np.exp(-t_b / 0.06)
    f_b = 1200.0 * (350.0 / 1200.0) ** (t_b / d_b)
    ph_b = 2 * np.pi * np.cumsum(f_b) / SR
    beam = np.sin(ph_b + 1.6 * np.sin(ph_b * 0.5)) * b_env * 0.65
    sig[:n_b] += beam
    
    # Hyper-speed air compression
    d_w = 0.20
    whoosh = lp(noise(d_w), 0.30) * env(int(SR * d_w), 0.003, 0.05) * 0.45
    sig[:len(whoosh)] += whoosh
    
    sig = np.tanh(sig * 2.2) / np.tanh(2.2) * 0.95
    return sig

# 3. Normal Cooldown Buildup (Left -> Right, ~0.48s)
def gen_slash_charge():
    d = 0.48
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    swell = np.sin(np.pi * (t / d)) ** 1.8
    
    f_curve = 135.0 + (300.0 - 135.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
    ph = 2 * np.pi * np.cumsum(f_curve) / SR
    sig += np.sin(ph) * swell * 0.55
    sig += np.sin(ph * 0.5) * swell * 0.35
    sig += lp(noise(d), 0.10) * swell * 0.20
    
    fade = int(SR * 0.09)
    sig[-fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    sig = np.tanh(sig * 1.3) / np.tanh(1.3) * 0.55
    return sig

# 4. 25% Longer Heavier Buildup (Right -> Stab, ~0.62s)
def gen_stab_charge():
    d = 0.62
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    swell = np.sin(np.pi * (t / d)) ** 1.9
    
    # Deeper, heavier vacuum surge for the stab
    f_curve = 110.0 + (340.0 - 110.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
    ph = 2 * np.pi * np.cumsum(f_curve) / SR
    sig += np.sin(ph) * swell * 0.65
    sig += np.sin(ph * 0.5) * swell * 0.45
    sig += lp(noise(d), 0.12) * swell * 0.28
    
    fade = int(SR * 0.11)
    sig[-fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    sig = np.tanh(sig * 1.4) / np.tanh(1.4) * 0.65
    return sig

# 5. Post-Stab Sword Spin Flourish
def gen_sword_spin():
    d = 0.32
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    # Twin metallic air whooshes from the blade spin
    w1 = lp(noise(0.14), 0.35) * env(int(SR * 0.14), 0.01, 0.05) * 0.40
    w2 = lp(noise(0.14), 0.40) * env(int(SR * 0.14), 0.01, 0.05) * 0.45
    sig[:len(w1)] += w1
    sig[int(SR * 0.10):int(SR * 0.10) + len(w2)] += w2
    
    # Resonant crystal blade ring / metallic sheen
    ring_d = 0.24
    ring = (sine(1150, ring_d) * 0.30 + sine(2300, ring_d) * 0.18 + sine(3450, ring_d) * 0.08) * env(int(SR * ring_d), 0.002, 0.08)
    sig[int(SR * 0.08):int(SR * 0.08) + len(ring)] += ring
    
    sig = np.tanh(sig * 1.2) / np.tanh(1.2) * 0.70
    return sig

# 6. Block Hit
def gen_slash_block_hit():
    d = 0.14
    n = int(SR * d)
    thud = sweep(120, 50, d) * env(n, 0.002, 0.035) * 0.60
    sizzle = lp(noise(0.10), 0.25) * env(int(SR * 0.10), 0.002, 0.025) * 0.35
    chime = sine(780, 0.08) * env(int(SR * 0.08), 0.002, 0.02) * 0.18
    sig = np.zeros(n)
    sig += thud
    sig[:len(sizzle)] += sizzle
    sig[:len(chime)] += chime
    sig = np.tanh(sig * 1.3) / np.tanh(1.3) * 0.65
    return sig

# ==============================================================================
# Armor-Set Themed Sword Audio Suite (5 Sets x 9 Kinds = 45 Files)
# Reuses crystal voice styling (gen_crystal_sounds.py) layered with sword kinetics.
# Sets (Perk.FAMILY_IDS): astral, cybernetic, singularity, cyberpsycho, infinity
# ==============================================================================

SETS = ['astral', 'cybernetic', 'singularity', 'cyberpsycho', 'infinity']
KINDS = [
    'slash_launch',
    'stab_launch',
    'slash_charge',
    'stab_charge',
    'spin',
    'crit_twirl',
    'block_hit',
    'entity_hit',
    'mode_toggle'
]

def finalize(sig, target_peak=0.89, fade_in_s=0.005, fade_out_s=0.02):
    n_in = int(SR * fade_in_s)
    if n_in > 0 and len(sig) >= n_in:
        sig[:n_in] *= np.linspace(0.0, 1.0, n_in)
    n_out = int(SR * fade_out_s)
    if n_out > 0 and len(sig) >= n_out:
        sig[-n_out:] *= np.linspace(1.0, 0.0, n_out)
    max_val = np.max(np.abs(sig))
    if max_val > 1e-6:
        sig = sig / max_val
    sig = np.tanh(sig * 1.5) / np.tanh(1.5) * target_peak
    return sig

def make_slash_launch(set_name, r):
    d = 0.22
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    # Base sword air displacement whoosh
    whoosh = lp(r.standard_normal(int(SR * 0.14)), 0.25) * env(int(SR * 0.14), 0.004, 0.04) * 0.35
    sig[:len(whoosh)] += whoosh
    
    # Sharp cutting transient
    click_n = int(SR * 0.007)
    click = r.standard_normal(click_n) * np.linspace(1.0, 0.0, click_n) ** 2 * 0.60
    sig[:click_n] += click
    
    if set_name == 'astral':
        chime = cs.bell(880, 0.20, r=0.4) * env(int(SR * 0.20), 0.002, 0.06) * 0.45
        slice_tone = sweep(880, 330, 0.18) * env(int(SR * 0.18), 0.002, 0.05) * 0.40
        sub = sweep(130, 40, d, curve=1.3) * np.exp(-t / 0.07) * 0.60
        sig[:len(chime)] += chime
        sig[:len(slice_tone)] += slice_tone
        sig += sub
        sig = cs.fast_reverb(sig, tail=0.35, mix=0.25)[:n]
    elif set_name == 'cybernetic':
        fm_tone = cs.fm(660, 1320, 1.8, 0.18) * env(int(SR * 0.18), 0.002, 0.04) * 0.55
        zap = cs.crush(cs.sweep(1400, 350, 0.14, 'sq'), bits=5, hold=3) * env(int(SR * 0.14), 0.001, 0.03) * 0.35
        sub = sweep(150, 36, d, curve=1.4) * np.exp(-t / 0.07) * 0.85
        sig[:len(fm_tone)] += fm_tone
        sig[:len(zap)] += zap
        sig += sub
    elif set_name == 'singularity':
        sub = sweep(165, 26, d, curve=1.6) * np.exp(-t / 0.08) * 1.30
        rumble = lp(r.standard_normal(int(SR * 0.20)), 0.04) * env(int(SR * 0.20), 0.01, 0.08) * 0.50
        void = sweep(350, 65, 0.18) * env(int(SR * 0.18), 0.01, 0.05) * 0.50
        sig += sub
        sig[:len(rumble)] += rumble
        sig[:len(void)] += void
    elif set_name == 'cyberpsycho':
        saw_cleave = cs.dist(cs.sweep(700, 140, 0.18, 'saw') * env(int(SR * 0.18), 0.001, 0.05), 5.0) * 0.65
        glitch = cs.crush(r.standard_normal(int(SR * 0.12)) * 0.4, bits=3, hold=10) * env(int(SR * 0.12), 0.001, 0.03) * 0.40
        sub = sweep(150, 32, d, curve=1.4) * np.exp(-t / 0.07) * 0.80
        sig[:len(saw_cleave)] += saw_cleave
        sig[:len(glitch)] += glitch
        sig += sub
    else:  # infinity
        sub = sweep(160, 28, d, curve=1.5) * np.exp(-t / 0.08) * 0.90
        fm_tone = cs.fm(880, 1760, 1.4, 0.16) * env(int(SR * 0.16), 0.002, 0.04) * 0.35
        bell_sheen = cs.bell(1320, 0.20, r=0.35) * env(int(SR * 0.20), 0.002, 0.05) * 0.35
        sig += sub
        sig[:len(fm_tone)] += fm_tone
        sig[:len(bell_sheen)] += bell_sheen
        sig = cs.fast_reverb(sig, tail=0.40, mix=0.20)[:n]
        
    return finalize(sig, 0.89)

def make_stab_launch(set_name, r):
    d = 0.28
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    crack_n = int(SR * 0.012)
    crack = r.standard_normal(crack_n) * np.linspace(1.0, 0.0, crack_n) ** 1.8 * 0.80
    sig[:crack_n] += crack
    
    whoosh = lp(r.standard_normal(int(SR * 0.20)), 0.30) * env(int(SR * 0.20), 0.003, 0.05) * 0.40
    sig[:len(whoosh)] += whoosh
    
    if set_name == 'astral':
        thump = sweep(160, 35, d, curve=1.4) * np.exp(-t / 0.08) * 0.95
        beam = sweep(1320, 440, 0.24) * env(int(SR * 0.24), 0.002, 0.06) * 0.65
        bells = cs.bell(1760, 0.26, r=0.45) * env(int(SR * 0.26), 0.002, 0.07) * 0.45
        sig += thump
        sig[:len(beam)] += beam
        sig[:len(bells)] += bells
        sig = cs.fast_reverb(sig, tail=0.50, mix=0.30)[:n]
    elif set_name == 'cybernetic':
        thump = sweep(180, 28, d, curve=1.5) * np.exp(-t / 0.09) * 1.15
        fm_beam = cs.fm(1100, 2200, 2.0, 0.22) * env(int(SR * 0.22), 0.002, 0.06) * 0.70
        blast = cs.crush(cs.sweep(1800, 400, 0.16, 'sq'), bits=5, hold=2) * env(int(SR * 0.16), 0.001, 0.04) * 0.40
        sig += thump
        sig[:len(fm_beam)] += fm_beam
        sig[:len(blast)] += blast
    elif set_name == 'singularity':
        thump = sweep(190, 20, d, curve=1.7) * np.exp(-t / 0.09) * 1.50
        void_lance = sweep(550, 45, 0.24) * env(int(SR * 0.24), 0.005, 0.07) * 0.75
        roar = lp(r.standard_normal(int(SR * 0.22)), 0.04) * env(int(SR * 0.22), 0.01, 0.08) * 0.55
        sig += thump
        sig[:len(void_lance)] += void_lance
        sig[:len(roar)] += roar
    elif set_name == 'cyberpsycho':
        thump = sweep(180, 26, d, curve=1.4) * np.exp(-t / 0.08) * 1.10
        saw_beam = cs.dist(cs.sweep(1400, 220, 0.22, 'saw') * env(int(SR * 0.22), 0.001, 0.06), 6.0) * 0.85
        glitch = cs.crush(r.standard_normal(int(SR * 0.16)) * 0.5, bits=3, hold=16) * env(int(SR * 0.16), 0.001, 0.04) * 0.50
        sig += thump
        sig[:len(saw_beam)] += saw_beam
        sig[:len(glitch)] += glitch
    else:  # infinity
        thump = sweep(180, 24, d, curve=1.6) * np.exp(-t / 0.09) * 1.25
        fm_crack = cs.fm(1200, 2400, 1.8, 0.20) * env(int(SR * 0.20), 0.002, 0.05) * 0.50
        archangel_chime = cs.bell(1760, 0.26, r=0.4) * env(int(SR * 0.26), 0.002, 0.06) * 0.40
        sig += thump
        sig[:len(fm_crack)] += fm_crack
        sig[:len(archangel_chime)] += archangel_chime
        sig = cs.fast_reverb(sig, tail=0.55, mix=0.25)[:n]
        
    return finalize(sig, 0.89)

def make_slash_charge(set_name, r):
    d = 0.48
    n = int(SR * d)
    t = np.arange(n) / SR
    swell = np.sin(np.pi * (t / d)) ** 1.8
    sig = np.zeros(n)
    
    air = lp(r.standard_normal(n), 0.10) * swell * 0.20
    sig += air
    
    if set_name == 'astral':
        f_curve = 150.0 + (360.0 - 150.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += np.sin(ph) * swell * 0.50
        bells = cs.bell(880, 0.35, r=0.3) * swell[:int(SR * 0.35)] * 0.25
        sig[int(SR * 0.10):int(SR * 0.10) + len(bells)] += bells
        sig = cs.fast_reverb(sig, tail=0.30, mix=0.20)[:n]
    elif set_name == 'cybernetic':
        f_curve = 135.0 + (320.0 - 135.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) + 0.3 * np.sin(ph * 2.0)) * swell * 0.55
        tech = cs.crush(np.sin(ph * 3.0), bits=6, hold=4) * swell * 0.20
        sig += tech
    elif set_name == 'singularity':
        f_curve = 80.0 + (220.0 - 80.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) * 0.70 + np.sin(ph * 0.5) * 0.45) * swell
        rumble = lp(r.standard_normal(n), 0.05) * swell * 0.30
        sig += rumble
    elif set_name == 'cyberpsycho':
        f_curve = 120.0 + (340.0 - 120.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        base_tone = np.sin(ph) * swell
        sig += cs.dist(base_tone, 2.5) * 0.60
        buzz = cs.crush(cs.sweep(200, 500, d, 'saw'), bits=4, hold=8) * swell * 0.22
        sig += buzz
    else:  # infinity
        f_curve = 100.0 + (300.0 - 100.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) * 0.55 + np.sin(ph * 0.5) * 0.35) * swell
        bells = cs.bell(1100, 0.30, r=0.25) * swell[:int(SR * 0.30)] * 0.20
        sig[int(SR * 0.12):int(SR * 0.12) + len(bells)] += bells
        sig = cs.fast_reverb(sig, tail=0.30, mix=0.15)[:n]
        
    return finalize(sig, 0.89)

def make_stab_charge(set_name, r):
    d = 0.62
    n = int(SR * d)
    t = np.arange(n) / SR
    swell = np.sin(np.pi * (t / d)) ** 1.9
    sig = np.zeros(n)
    
    air = lp(r.standard_normal(n), 0.12) * swell * 0.25
    sig += air
    
    if set_name == 'astral':
        f_curve = 120.0 + (380.0 - 120.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += np.sin(ph) * swell * 0.55
        bells = cs.bell(1100, 0.40, r=0.35) * swell[:int(SR * 0.40)] * 0.30
        sig[int(SR * 0.15):int(SR * 0.15) + len(bells)] += bells
        sig = cs.fast_reverb(sig, tail=0.35, mix=0.22)[:n]
    elif set_name == 'cybernetic':
        f_curve = 110.0 + (360.0 - 110.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) * 0.60 + np.sin(ph * 2.0) * 0.35) * swell
        tech = cs.crush(np.sin(ph * 2.5), bits=5, hold=4) * swell * 0.25
        sig += tech
    elif set_name == 'singularity':
        f_curve = 65.0 + (240.0 - 65.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) * 0.75 + np.sin(ph * 0.5) * 0.50) * swell
        rumble = lp(r.standard_normal(n), 0.04) * swell * 0.35
        sig += rumble
    elif set_name == 'cyberpsycho':
        f_curve = 100.0 + (380.0 - 100.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        base_tone = np.sin(ph) * swell
        sig += cs.dist(base_tone, 3.0) * 0.65
        buzz = cs.crush(cs.sweep(180, 550, d, 'saw'), bits=3, hold=10) * swell * 0.28
        sig += buzz
    else:  # infinity
        f_curve = 85.0 + (330.0 - 85.0) * (1.0 - np.cos(np.pi * (t / d) * 0.95)) * 0.5
        ph = 2 * np.pi * np.cumsum(f_curve) / SR
        sig += (np.sin(ph) * 0.65 + np.sin(ph * 0.5) * 0.40) * swell
        bells = cs.bell(1320, 0.38, r=0.3) * swell[:int(SR * 0.38)] * 0.25
        sig[int(SR * 0.16):int(SR * 0.16) + len(bells)] += bells
        sig = cs.fast_reverb(sig, tail=0.35, mix=0.18)[:n]
        
    return finalize(sig, 0.89)

def make_spin(set_name, r):
    d = 0.32
    n = int(SR * d)
    sig = np.zeros(n)
    
    w1 = lp(r.standard_normal(int(SR * 0.14)), 0.35) * env(int(SR * 0.14), 0.01, 0.05) * 0.40
    w2 = lp(r.standard_normal(int(SR * 0.14)), 0.40) * env(int(SR * 0.14), 0.01, 0.05) * 0.45
    sig[:len(w1)] += w1
    sig[int(SR * 0.10):int(SR * 0.10) + len(w2)] += w2
    
    if set_name == 'astral':
        ring = cs.bell(1320, 0.22, r=0.35) * env(int(SR * 0.22), 0.002, 0.08) * 0.50
        sig[int(SR * 0.08):int(SR * 0.08) + len(ring)] += ring
        sig = cs.fast_reverb(sig, tail=0.35, mix=0.25)[:n]
    elif set_name == 'cybernetic':
        fm_ring = cs.fm(990, 1980, 1.2, 0.20) * env(int(SR * 0.20), 0.002, 0.06) * 0.45
        chip = cs.crush(cs.sweep(1200, 600, 0.12, 'sq'), bits=5, hold=3) * 0.25
        sig[int(SR * 0.08):int(SR * 0.08) + len(fm_ring)] += fm_ring
        sig[int(SR * 0.12):int(SR * 0.12) + len(chip)] += chip
    elif set_name == 'singularity':
        sub_ring = sweep(220, 75, 0.22) * env(int(SR * 0.22), 0.01, 0.08) * 0.60
        sig[int(SR * 0.08):int(SR * 0.08) + len(sub_ring)] += sub_ring
    elif set_name == 'cyberpsycho':
        saw_whip = cs.dist(cs.sweep(800, 200, 0.18, 'saw') * env(int(SR * 0.18), 0.002, 0.05), 4.0) * 0.50
        sig[int(SR * 0.08):int(SR * 0.08) + len(saw_whip)] += saw_whip
    else:  # infinity
        ring = cs.bell(1760, 0.22, r=0.35) * env(int(SR * 0.22), 0.002, 0.07) * 0.35
        sub = sweep(180, 60, 0.20) * env(int(SR * 0.20), 0.01, 0.07) * 0.40
        sig[int(SR * 0.08):int(SR * 0.08) + len(ring)] += ring
        sig[int(SR * 0.08):int(SR * 0.08) + len(sub)] += sub
        sig = cs.fast_reverb(sig, tail=0.35, mix=0.20)[:n]
        
    return finalize(sig, 0.89)

def make_crit_twirl(set_name, r):
    d = 0.70
    n = int(SR * d)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    
    # 3 successive rotary air whooshes (spinning blade cycles at 0.0s, 0.18s, 0.36s)
    for k, delay in enumerate([0.0, 0.18, 0.36]):
        idx = int(SR * delay)
        w_d = 0.18
        w = lp(r.standard_normal(int(SR * w_d)), 0.32 + 0.05 * k) * env(int(SR * w_d), 0.01, 0.05) * 0.38
        end = min(n, idx + len(w))
        sig[idx:end] += w[:end - idx]
        
    if set_name == 'astral':
        for k, delay in enumerate([0.05, 0.22, 0.40]):
            idx = int(SR * delay)
            b = cs.bell(880 * (1.25 ** k), 0.24, r=0.35) * env(int(SR * 0.24), 0.002, 0.07) * 0.40
            end = min(n, idx + len(b))
            sig[idx:end] += b[:end - idx]
        sig = cs.fast_reverb(sig, tail=0.45, mix=0.28)[:n]
    elif set_name == 'cybernetic':
        for k, delay in enumerate([0.05, 0.22, 0.40]):
            idx = int(SR * delay)
            f = cs.fm(660 * (1.5 ** (k % 2)), 1320, 1.4, 0.18) * env(int(SR * 0.18), 0.002, 0.05) * 0.45
            end = min(n, idx + len(f))
            sig[idx:end] += f[:end - idx]
        flutter = cs.crush(cs.sweep(1600, 400, 0.45, 'sq'), bits=5, hold=3) * env(int(SR * 0.45), 0.01, 0.15) * 0.25
        sig[int(SR * 0.08):int(SR * 0.08) + len(flutter)] += flutter
    elif set_name == 'singularity':
        vortex = sweep(180, 50, d, curve=1.2) * (np.sin(np.pi * (t / d)) ** 1.5) * 0.65
        rumble = lp(r.standard_normal(n), 0.04) * (np.sin(np.pi * (t / d)) ** 1.5) * 0.40
        sig += vortex + rumble
    elif set_name == 'cyberpsycho':
        for k, delay in enumerate([0.05, 0.22, 0.40]):
            idx = int(SR * delay)
            s = cs.dist(cs.sweep(900, 250, 0.16, 'saw') * env(int(SR * 0.16), 0.002, 0.04), 4.5) * 0.45
            end = min(n, idx + len(s))
            sig[idx:end] += s[:end - idx]
        buzz = cs.crush(r.standard_normal(int(SR * 0.40)) * 0.4, bits=3, hold=12) * env(int(SR * 0.40), 0.01, 0.10) * 0.30
        sig[int(SR * 0.10):int(SR * 0.10) + len(buzz)] += buzz
    else:  # infinity
        vortex = sweep(160, 45, d, curve=1.3) * (np.sin(np.pi * (t / d)) ** 1.5) * 0.45
        sig += vortex
        for k, delay in enumerate([0.06, 0.24, 0.42]):
            idx = int(SR * delay)
            b = cs.bell(1100 * (1.25 ** k), 0.22, r=0.3) * env(int(SR * 0.22), 0.002, 0.06) * 0.30
            end = min(n, idx + len(b))
            sig[idx:end] += b[:end - idx]
        sig = cs.fast_reverb(sig, tail=0.45, mix=0.22)[:n]
        
    return finalize(sig, 0.89)

def make_block_hit(set_name, r):
    d = 0.14
    n = int(SR * d)
    sig = np.zeros(n)
    
    thud = sweep(120, 50, d) * env(n, 0.002, 0.035) * 0.60
    sizzle = lp(r.standard_normal(int(SR * 0.10)), 0.25) * env(int(SR * 0.10), 0.002, 0.025) * 0.35
    sig += thud
    sig[:len(sizzle)] += sizzle
    
    if set_name == 'astral':
        chime = cs.bell(990, 0.08, r=0.2) * env(int(SR * 0.08), 0.002, 0.025) * 0.30
        sig[:len(chime)] += chime
        sig = cs.fast_reverb(sig, tail=0.20, mix=0.20)[:n]
    elif set_name == 'cybernetic':
        zap = cs.crush(cs.sweep(1600, 300, 0.08, 'sq'), bits=5, hold=2) * env(int(SR * 0.08), 0.001, 0.02) * 0.35
        sig[:len(zap)] += zap
    elif set_name == 'singularity':
        sub_thud = sweep(150, 30, d) * env(n, 0.002, 0.04) * 0.70
        sig += sub_thud
    elif set_name == 'cyberpsycho':
        scrape = cs.dist(cs.sweep(1000, 200, 0.09, 'saw') * env(int(SR * 0.09), 0.001, 0.025), 4.5) * 0.45
        sig[:len(scrape)] += scrape
    else:  # infinity
        chime = cs.bell(1320, 0.08, r=0.2) * env(int(SR * 0.08), 0.002, 0.02) * 0.25
        sub_thud = sweep(140, 35, d) * env(n, 0.002, 0.035) * 0.45
        sig += sub_thud
        sig[:len(chime)] += chime
        sig = cs.fast_reverb(sig, tail=0.20, mix=0.15)[:n]
        
    return finalize(sig, 0.89)

def make_entity_hit(set_name, r):
    d = 0.20
    n = int(SR * d)
    sig = np.zeros(n)
    
    punch_n = int(SR * 0.010)
    punch = r.standard_normal(punch_n) * np.linspace(1.0, 0.0, punch_n) ** 1.5 * 0.75
    sig[:punch_n] += punch
    
    thud = sweep(220, 55, 0.16) * env(int(SR * 0.16), 0.002, 0.04) * 0.65
    sig[:len(thud)] += thud
    
    if set_name == 'astral':
        flare = cs.bell(1100, 0.16, r=0.25) * env(int(SR * 0.16), 0.002, 0.05) * 0.50
        slice_tone = sweep(770, 220, 0.14) * env(int(SR * 0.14), 0.002, 0.04) * 0.35
        sig[:len(flare)] += flare
        sig[:len(slice_tone)] += slice_tone
        sig = cs.fast_reverb(sig, tail=0.25, mix=0.25)[:n]
    elif set_name == 'cybernetic':
        shock = cs.fm(880, 1760, 2.0, 0.15) * env(int(SR * 0.15), 0.001, 0.035) * 0.60
        zap = cs.crush(r.standard_normal(int(SR * 0.08)) * 0.5, bits=4, hold=3) * env(int(SR * 0.08), 0.001, 0.02) * 0.40
        sig[:len(shock)] += shock
        sig[:len(zap)] += zap
    elif set_name == 'singularity':
        implosion = sweep(180, 28, d, curve=1.5) * np.exp(-np.arange(n) / SR / 0.06) * 1.20
        sig += implosion
    elif set_name == 'cyberpsycho':
        rip = cs.dist(cs.sweep(1200, 180, 0.16, 'saw') * env(int(SR * 0.16), 0.001, 0.045), 5.5) * 0.75
        sig[:len(rip)] += rip
    else:  # infinity
        implosion = sweep(170, 32, d, curve=1.5) * np.exp(-np.arange(n) / SR / 0.06) * 0.85
        shock = cs.fm(990, 1980, 1.5, 0.14) * env(int(SR * 0.14), 0.002, 0.035) * 0.40
        flare = cs.bell(1320, 0.15, r=0.25) * env(int(SR * 0.15), 0.002, 0.04) * 0.35
        sig += implosion
        sig[:len(shock)] += shock
        sig[:len(flare)] += flare
        sig = cs.fast_reverb(sig, tail=0.25, mix=0.20)[:n]
        
    return finalize(sig, 0.89)

def make_mode_toggle(set_name, r):
    d = 0.25
    n = int(SR * d)
    sig = np.zeros(n)
    
    click_n = int(SR * 0.005)
    click = r.standard_normal(click_n) * np.linspace(1.0, 0.0, click_n) ** 2 * 0.50
    sig[:click_n] += click
    
    if set_name == 'astral':
        b1 = cs.bell(880, 0.14, r=0.25) * env(int(SR * 0.14), 0.002, 0.04) * 0.45
        b2 = cs.bell(1320, 0.16, r=0.30) * env(int(SR * 0.16), 0.002, 0.05) * 0.55
        sig[:len(b1)] += b1
        sig[int(SR * 0.07):int(SR * 0.07) + len(b2)] += b2
        sig = cs.fast_reverb(sig, tail=0.30, mix=0.25)[:n]
    elif set_name == 'cybernetic':
        f1 = cs.fm(660, 1320, 1.2, 0.10) * env(int(SR * 0.10), 0.002, 0.03) * 0.50
        f2 = cs.fm(990, 1980, 1.5, 0.12) * env(int(SR * 0.12), 0.002, 0.035) * 0.60
        sig[:len(f1)] += f1
        sig[int(SR * 0.06):int(SR * 0.06) + len(f2)] += f2
    elif set_name == 'singularity':
        pulse = sweep(90, 220, 0.20) * env(int(SR * 0.20), 0.01, 0.08) * 0.85
        sig[:len(pulse)] += pulse
    elif set_name == 'cyberpsycho':
        saw_blip = cs.dist(cs.sweep(330, 660, 0.16, 'saw') * env(int(SR * 0.16), 0.002, 0.04), 4.5) * 0.65
        sig[:len(saw_blip)] += saw_blip
    else:  # infinity
        chord = (sine(440, 0.22) * 0.35 + sine(660, 0.22) * 0.30 + sine(880, 0.22) * 0.25) * env(int(SR * 0.22), 0.005, 0.07)
        bell_sheen = cs.bell(1760, 0.18, r=0.3) * env(int(SR * 0.18), 0.002, 0.05) * 0.35
        sig[:len(chord)] += chord
        sig[int(SR * 0.05):int(SR * 0.05) + len(bell_sheen)] += bell_sheen
        sig = cs.fast_reverb(sig, tail=0.30, mix=0.20)[:n]
        
    return finalize(sig, 0.89)

THEMED_DISPATCH = {
    'slash_launch': make_slash_launch,
    'stab_launch': make_stab_launch,
    'slash_charge': make_slash_charge,
    'stab_charge': make_stab_charge,
    'spin': make_spin,
    'crit_twirl': make_crit_twirl,
    'block_hit': make_block_hit,
    'entity_hit': make_entity_hit,
    'mode_toggle': make_mode_toggle,
}

def gen_themed(set_name, kind):
    seed = zlib.crc32(f'{set_name}:{kind}'.encode()) & 0x7FFFFFFF  # stable across runs (str hash() is randomised)
    r = np.random.default_rng(seed)
    fn = THEMED_DISPATCH[kind]
    return fn(set_name, r)

if __name__ == '__main__':
    base_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src/main/resources/assets/infinium/sounds/sword')
    os.makedirs(base_dir, exist_ok=True)
    
    # 6 default sounds (byte-for-byte unchanged in behaviour)
    write_ogg(os.path.join(base_dir, 'slash_launch.ogg'), gen_slash_launch())
    write_ogg(os.path.join(base_dir, 'stab_launch.ogg'), gen_stab_launch())
    write_ogg(os.path.join(base_dir, 'slash_charge.ogg'), gen_slash_charge())
    write_ogg(os.path.join(base_dir, 'stab_charge.ogg'), gen_stab_charge())
    write_ogg(os.path.join(base_dir, 'sword_spin.ogg'), gen_sword_spin())
    write_ogg(os.path.join(base_dir, 'slash_block_hit.ogg'), gen_slash_block_hit())
    
    # 45 themed sounds (5 sets x 9 kinds)
    for s in SETS:
        for k in KINDS:
            out_file = os.path.join(base_dir, f'{s}_{k}.ogg')
            write_ogg(out_file, gen_themed(s, k))
            
    print('All 51 sword audio files (6 default + 45 themed) synthesized successfully!')
