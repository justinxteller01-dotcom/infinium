import os, subprocess, tempfile, wave
import numpy as np

SR = 48000
rng = np.random.default_rng(42)

def t_(d): return np.arange(int(SR * d)) / SR

def env(n, a=0.005, r=0.3, d=None):
    t = np.arange(n) / SR
    d = d or n / SR
    e = np.minimum(1.0, t / max(a, 1e-4)) * np.exp(-t / max(r, 1e-3))
    tail = np.clip((d - t) / 0.03, 0.0, 1.0)
    return e * tail

def sine(f, d, ph=0.0):
    t = t_(d)
    return np.sin(2 * np.pi * f * t + ph)

def fm(fc, fmod, idx, d):
    t = t_(d)
    return np.sin(2 * np.pi * fc * t + idx * np.sin(2 * np.pi * fmod * t))

def sweep(f0, f1, d, kind='sin'):
    t = t_(d)
    f = f0 * (f1 / f0) ** (t / d)
    ph = 2 * np.pi * np.cumsum(f) / SR
    ph = ph[:len(t)]
    if kind == 'sq': return np.sign(np.sin(ph)) * 0.6
    if kind == 'saw': return 2 * ((ph / (2 * np.pi)) % 1) - 1
    return np.sin(ph)

def noise(d):
    return rng.standard_normal(int(SR * d))

def lp(x, a=0.1):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y

def hp(x, a=0.9):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = a * (acc + v - (x[i-1] if i > 0 else 0))
        y[i] = acc
    return y

def crush(x, bits=5, hold=4):
    q = np.round(x * 2 ** bits) / 2 ** bits
    return np.repeat(q[::hold], hold)[:len(x)]

def dist(x, g=4.0):
    return np.tanh(x * g) / np.tanh(g)

def bell(f, d, r=0.4):
    return sum(a * sine(f * m, d) * env(int(SR * d), 0.002, r / (1 + k))
               for k, (m, a) in enumerate([(1, 1), (2.76, .45), (5.4, .25), (8.93, .12)]))

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
    print(f"Wrote {path} ({len(x)/SR:.2f}s)")

# --- 1. Tool Mode Switch ---
def gen_tool_mode():
    d = 0.16
    n = int(SR * d)
    t = t_(d)
    # Crisp tactile snap: short click + fast pitch sweep + subtle sub punch
    click = noise(0.015) * np.linspace(1, 0, int(SR * 0.015)) ** 2 * 0.7
    sub = sine(120, d) * env(n, 0.002, 0.04) * 0.6
    swp = sweep(350, 750, 0.08) * env(int(SR * 0.08), 0.001, 0.025) * 0.4
    chime = (sine(1200, 0.12) * 0.3 + sine(1800, 0.12) * 0.2) * env(int(SR * 0.12), 0.001, 0.02)
    sig = np.zeros(n)
    sig[:len(click)] += click
    sig += sub
    sig[:len(swp)] += swp
    sig[:len(chime)] += chime
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.75
    return sig

# --- 2. Elytra Toggle (Enable / Disable) ---
def gen_wings_toggle(family, enable):
    d = 0.32 if family != 4 else 0.42
    n = int(SR * d)
    sig = np.zeros(n)
    t = t_(d)
    
    if family == 0:  # Astral (cosmic chime, starry sweep, gentle punch)
        if enable:
            f0, f1 = 180, 520
            sw = sweep(f0, f1, 0.18) * env(int(SR * 0.18), 0.01, 0.08)
            bl = bell(880, 0.28, 0.15) * 0.45
            sub = sine(90, 0.22) * env(int(SR * 0.22), 0.005, 0.06) * 0.5
            sig[:len(sw)] += sw * 0.6
            sig[:len(bl)] += bl
            sig[:len(sub)] += sub
        else:
            f0, f1 = 480, 160
            sw = sweep(f0, f1, 0.20) * env(int(SR * 0.20), 0.002, 0.09) * 0.6
            bl = bell(660, 0.22, 0.08) * 0.35
            sig[:len(sw)] += sw
            sig[:len(bl)] += bl

    elif family == 1:  # Cybernetic (crisp mechanical servo latch + tech clicks)
        if enable:
            snap = crush(sweep(1200, 300, 0.06, 'saw'), 5, 2) * env(int(SR * 0.06), 0.001, 0.02) * 0.7
            servo = fm(320, 640, 2.0, 0.16) * env(int(SR * 0.16), 0.005, 0.05) * 0.5
            sub = dist(sine(80, 0.18) * env(int(SR * 0.18), 0.002, 0.04), 2.5) * 0.6
            sig[:len(snap)] += snap
            sig[:len(servo)] += servo
            sig[:len(sub)] += sub
        else:
            snap = crush(sweep(400, 150, 0.08, 'saw'), 4, 3) * env(int(SR * 0.08), 0.001, 0.03) * 0.6
            servo = fm(220, 440, 1.5, 0.18) * env(int(SR * 0.18), 0.002, 0.06) * 0.4
            sig[:len(snap)] += snap
            sig[:len(servo)] += servo

    elif family == 2:  # Singularity (gravitational vacuum pop, dark void snap)
        if enable:
            # Reverse vacuum suction ramp into deep bass drop
            vac_d = 0.08
            vac = sweep(90, 280, vac_d) * np.linspace(0.1, 1.0, int(SR * vac_d)) ** 2 * 0.5
            pop = sweep(240, 45, 0.24) * env(int(SR * 0.24), 0.002, 0.08) * 0.8
            glitch = crush(noise(0.04), 4, 8) * env(int(SR * 0.04), 0.001, 0.015) * 0.4
            sig[:len(vac)] += vac
            sig[len(vac):len(vac)+len(pop)] += pop
            sig[len(vac):len(vac)+len(glitch)] += glitch
        else:
            drop = sweep(220, 38, 0.26) * env(int(SR * 0.26), 0.003, 0.10) * 0.7
            dissolve = lp(noise(0.20), 0.05) * env(int(SR * 0.20), 0.01, 0.07) * 0.4
            sig[:len(drop)] += drop
            sig[:len(dissolve)] += dissolve

    elif family == 3:  # CyberPsycho (overclocked electric zap, heavy distorted glitch)
        if enable:
            zap = dist(sweep(1600, 200, 0.12, 'saw'), 6.0) * env(int(SR * 0.12), 0.001, 0.04) * 0.65
            sub = dist(sine(65, 0.20), 4.0) * env(int(SR * 0.20), 0.002, 0.05) * 0.75
            gl = crush(noise(0.08), 3, 16) * env(int(SR * 0.08), 0.001, 0.03) * 0.5
            sig[:len(zap)] += zap
            sig[:len(sub)] += sub
            sig[:len(gl)] += gl
        else:
            zap = dist(sweep(300, 80, 0.14, 'saw'), 4.0) * env(int(SR * 0.14), 0.001, 0.05) * 0.6
            gl = crush(noise(0.06), 3, 20) * env(int(SR * 0.06), 0.001, 0.02) * 0.5
            sig[:len(zap)] += zap
            sig[:len(gl)] += gl

    elif family == 4:  # Infinity (Crimson Moon signature: reverse suction punch + cosmic resonance)
        if enable:
            # 0.06s pre-vacuum suck
            vac_d = 0.06
            vac = sweep(60, 320, vac_d) * np.linspace(0.05, 1.0, int(SR * vac_d)) ** 2 * 0.55
            # Sub-bass punch (42 Hz) with slight saturation
            punch_d = 0.32
            punch = dist(sweep(180, 42, punch_d), 2.2) * env(int(SR * punch_d), 0.002, 0.10) * 0.85
            # Tech servo snap + celestial harmonic bell
            tech = crush(sweep(900, 350, 0.05, 'sq'), 5, 2) * env(int(SR * 0.05), 0.001, 0.02) * 0.35
            chime = bell(1100, 0.30, 0.18) * 0.35
            sig[:len(vac)] += vac
            at_idx = int(SR * vac_d)
            sig[at_idx:at_idx+len(punch)] += punch
            sig[at_idx:at_idx+len(tech)] += tech
            sig[at_idx:at_idx+len(chime)] += chime
        else:
            drop = sweep(160, 36, 0.30) * env(int(SR * 0.30), 0.002, 0.10) * 0.75
            chime = bell(750, 0.25, 0.10) * 0.35
            sig[:len(drop)] += drop
            sig[:len(chime)] += chime

    fade = min(len(sig), int(SR * 0.02))
    sig[-fade:] *= np.linspace(1, 0, fade)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.82
    return sig

# --- 3. Flight Takeoff Cue ---
def gen_flight_enter(family):
    d = 0.55
    n = int(SR * d)
    sig = np.zeros(n)
    
    if family == 0:  # Astral (airy celestial rush, harmonic chime swell, warm sub)
        rush = lp(noise(0.40), 0.15) * env(int(SR * 0.40), 0.05, 0.15) * 0.45
        chime = bell(880, 0.45, 0.22) * 0.4
        sub = sweep(70, 140, 0.35) * env(int(SR * 0.35), 0.04, 0.12) * 0.5
        sig[:len(rush)] += rush
        sig[:len(chime)] += chime
        sig[:len(sub)] += sub

    elif family == 2:  # Singularity (gravitational vacuum pop, deep void pulse)
        vac = sweep(60, 200, 0.08) * np.linspace(0.1, 1.0, int(SR * 0.08)) ** 2 * 0.5
        pulse = sweep(220, 40, 0.42) * env(int(SR * 0.42), 0.002, 0.14) * 0.85
        rumble = lp(noise(0.45), 0.03) * env(int(SR * 0.45), 0.02, 0.18) * 0.4
        sig[:len(vac)] += vac
        at = int(SR * 0.08)
        sig[at:at+len(pulse)] += pulse
        sig[at:at+len(rumble)] += rumble

    elif family == 4:  # Infinity (Crimson Moon reference: suction -> resonant sub punch -> harmonic wake)
        vac = sweep(50, 360, 0.07) * np.linspace(0.05, 1.0, int(SR * 0.07)) ** 2 * 0.6
        punch = dist(sweep(210, 38, 0.45), 2.5) * env(int(SR * 0.45), 0.002, 0.15) * 0.90
        chime = bell(1320, 0.40, 0.20) * 0.35
        whoosh = lp(noise(0.45), 0.2) * env(int(SR * 0.45), 0.01, 0.16) * 0.4
        sig[:len(vac)] += vac
        at = int(SR * 0.07)
        sig[at:at+len(punch)] += punch
        sig[at:at+len(chime)] += chime
        sig[at:at+len(whoosh)] += whoosh

    fade = min(len(sig), int(SR * 0.03))
    sig[-fade:] *= np.linspace(1, 0, fade)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.85
    return sig

# --- 4. Continuous Flight Thrust Loop (Seamless) ---
def gen_thrust_loop(family):
    dur = 3.0
    n = int(SR * dur)
    t = t_(dur)
    f0 = 1.0 / dur  # 0.33333 Hz fundamental for exact periodic boundary
    
    def periodic_sine(freq, amp, ph=0.0):
        # Round freq to nearest integer multiple of f0
        k = round(freq / f0)
        actual_f = k * f0
        return amp * np.sin(2 * np.pi * actual_f * t + ph)

    sig = np.zeros(n)
    
    if family == 0:  # Astral: smooth warm interstellar engine, harmonic resonance, airy flow
        # Sub drone 110 Hz + octave harmonics
        sig += periodic_sine(110.0, 0.45)
        sig += periodic_sine(220.0, 0.25)
        sig += periodic_sine(330.0, 0.15)
        sig += periodic_sine(440.0, 0.10)
        sig += periodic_sine(880.0, 0.05)
        # Slow breathing amplitude modulation (1 cycle per 3 sec)
        am = 0.85 + 0.15 * np.cos(2 * np.pi * 1.0 * t / dur)
        sig *= am
        # Periodic pink noise bed
        ns = noise(dur)
        # Make noise loop seamlessly by blending head and tail
        fade_n = int(SR * 0.2)
        ns[:fade_n] = ns[:fade_n] * np.linspace(0, 1, fade_n) + ns[-fade_n:] * np.linspace(1, 0, fade_n)
        ns = lp(ns, 0.08) * 0.20
        sig += ns

    elif family == 2:  # Singularity: deep gravitational hum, sub-bass resonance (55 Hz), darker void engine
        sig += periodic_sine(55.0, 0.60)
        sig += periodic_sine(110.0, 0.35)
        sig += periodic_sine(165.0, 0.15)
        sig += periodic_sine(220.0, 0.20)
        # 3-pulse per 3s cyclic void ripple
        lfo = 0.75 + 0.25 * np.sin(2 * np.pi * 3.0 * t / dur)
        sig *= lfo
        # Deep filtered rumble
        ns = noise(dur)
        fade_n = int(SR * 0.2)
        ns[:fade_n] = ns[:fade_n] * np.linspace(0, 1, fade_n) + ns[-fade_n:] * np.linspace(1, 0, fade_n)
        ns = lp(ns, 0.03) * 0.35
        sig += ns

    elif family == 4:  # Infinity: most intense, dual thrusters + celestial crystal harmonics
        # Deep 55 Hz sub foundation
        sig += periodic_sine(55.0, 0.50)
        # Dual booster turbine hum (220 Hz & 330 Hz with subtle saturation)
        turb = periodic_sine(110.0, 0.30) + periodic_sine(220.0, 0.25) + periodic_sine(330.0, 0.20)
        sig += dist(turb, 1.8) * 0.40
        # Celestial shimmer harmonics
        sig += periodic_sine(660.0, 0.12)
        sig += periodic_sine(1320.0, 0.06)
        # Smooth flow noise bed
        ns = noise(dur)
        fade_n = int(SR * 0.25)
        ns[:fade_n] = ns[:fade_n] * np.linspace(0, 1, fade_n) + ns[-fade_n:] * np.linspace(1, 0, fade_n)
        ns = lp(ns, 0.10) * 0.22
        sig += ns

    # Ensure exact seamless loop at boundary: crossfade first/last 0.05s
    cf = int(SR * 0.05)
    sig[:cf] = sig[:cf] * np.linspace(0, 1, cf) + sig[-cf:] * np.linspace(1, 0, cf)
    sig[-cf:] = sig[:cf]  # mirror exact samples
    
    vol = 0.65 if family == 0 else (0.75 if family == 2 else 0.85)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * vol
    return sig

if __name__ == '__main__':
    base_dir = r'F:\GrokBot\work\infinium_pass2\src\main\resources\assets\infinium\sounds'
    
    # 1. Tool mode
    write_ogg(os.path.join(base_dir, 'tools', 'mode_switch.ogg'), gen_tool_mode())
    
    # 2. Elytra toggle
    fams = ['astral', 'cybernetic', 'singularity', 'cyberpsycho', 'infinity']
    for idx, name in enumerate(fams):
        write_ogg(os.path.join(base_dir, 'wings', f'{name}_enable.ogg'), gen_wings_toggle(idx, True))
        write_ogg(os.path.join(base_dir, 'wings', f'{name}_disable.ogg'), gen_wings_toggle(idx, False))
        
    # 3. Flight enter & thrust
    flight_fams = [(0, 'astral'), (2, 'singularity'), (4, 'infinity')]
    for idx, name in flight_fams:
        write_ogg(os.path.join(base_dir, 'flight', f'enter_{name}.ogg'), gen_flight_enter(idx))
        write_ogg(os.path.join(base_dir, 'flight', f'thrust_{name}.ogg'), gen_thrust_loop(idx))
    print('All sound files generated successfully!')
