#!/usr/bin/env python3
"""
Generates overhauled equip and fullset audio files for Infinium:
- Audio Reference: Crimson Moon (Akatsuki) move from Fire Force anime:
  * Vacuum implosion / negative sound suck pre-drop
  * Sudden silence gap
  * Colossal, saturated sub-bass drop & 808 shockwave
- Aesthetics: Deep, bassy, glitchy Brakence / Slayr hyperpop sound design
  * Bitcrushed micro-stutters, formant laser chirps, tape-stop pitch bends
- Tailored for all 5 armor tiers:
  * Astral, Cybernetic, Singularity, CyberPsycho, Infinity
"""

import os
import subprocess
import numpy as np

SAMPLE_RATE = 48000

def soft_clip(x, drive=1.5):
    """Warm saturation using hyperbolic tangent with subtle harmonic warmth."""
    return np.tanh(drive * x)

def bitcrush(x, bits=6):
    """Digital bitcrush and quantization for Brakence-style glitch transients."""
    q = 2 ** (bits - 1)
    return np.round(x * q) / q

def create_vacuum_suck(duration, start_freq=350, end_freq=75, exponent=4.5):
    """Crimson Moon vacuum suck: reverse exponential sweep and filtered noise suck."""
    t = np.linspace(0, duration, int(SAMPLE_RATE * duration), endpoint=False)
    # Exponential rise in amplitude towards the cutoff
    env = np.exp(exponent * (t / duration - 1.0))
    # Reverse frequency sweep diving down
    freq = start_freq * (end_freq / start_freq) ** (t / duration)
    phase = 2 * np.pi * np.cumsum(freq) / SAMPLE_RATE
    # Sine suck + noise suck
    noise = np.random.uniform(-1, 1, len(t))
    sig = 0.7 * np.sin(phase) + 0.3 * noise
    return sig * env

def create_sub_drop(duration, start_pitch, end_pitch, decay_rate=3.0, drive=2.0):
    """Massive, chest-thumping sub-bass drop with 808 pitch dive and harmonic saturation."""
    n_samples = int(SAMPLE_RATE * duration)
    t = np.linspace(0, duration, n_samples, endpoint=False)
    # Pitch dive
    pitch_decay = np.exp(-t * 8.0)
    freq = end_pitch + (start_pitch - end_pitch) * pitch_decay
    phase = 2 * np.pi * np.cumsum(freq) / SAMPLE_RATE
    
    # Fundamental + 2nd harmonic + 3rd harmonic
    sub = (
        0.85 * np.sin(phase) +
        0.30 * np.sin(phase * 2) +
        0.12 * np.sin(phase * 3)
    )
    # Amplitude envelope
    amp_env = np.exp(-t * decay_rate)
    # Soft saturation for physical presence
    sub_saturated = soft_clip(sub * drive) * amp_env
    return sub_saturated

def create_glitch_stutter(sig, slice_dur=0.022, repeats=3, decay=0.85):
    """Brakence-style micro-stutter repetition."""
    slice_len = int(SAMPLE_RATE * slice_dur)
    if len(sig) < slice_len:
        return sig
    out = np.copy(sig)
    chunk = sig[:slice_len]
    for i in range(repeats):
        start = i * slice_len
        end = start + slice_len
        if end <= len(out):
            out[start:end] += chunk * (decay ** (i + 1))
    return out

def create_laser_chirp(duration=0.12, f0=1800, f1=180, fm=440, mod_idx=2.5):
    """Hyperpop FM laser zap transient."""
    n = int(SAMPLE_RATE * duration)
    t = np.linspace(0, duration, n, endpoint=False)
    env = np.exp(-t * 30.0)
    carrier_freq = np.geomspace(f0, f1, n)
    modulator = np.sin(2 * np.pi * fm * t)
    phase = 2 * np.pi * np.cumsum(carrier_freq + mod_idx * fm * modulator) / SAMPLE_RATE
    return np.sin(phase) * env

def synthesize_fullset(tier_name):
    """Synthesizes the Crimson Moon full-set fanfare (~2.4s) for a given tier."""
    dur = 2.45
    total_samples = int(SAMPLE_RATE * dur)
    audio = np.zeros(total_samples, dtype=np.float64)

    # 1. Vacuum suck (0.0 to 0.22s) followed by a 25ms total silence drop
    suck_dur = 0.22
    silence_dur = 0.025
    drop_time = suck_dur + silence_dur

    suck = create_vacuum_suck(suck_dur, start_freq=380, end_freq=65, exponent=4.2)
    suck_len = len(suck)
    audio[:suck_len] = suck * 0.75

    # 2. Main drop settings by tier
    tier = tier_name.lower()
    if tier == 'astral':
        sub_pitch = 45.0
        start_pitch = 175.0
        drive = 1.9
        glitch_bits = 8
        decay = 2.4
    elif tier == 'cybernetic':
        sub_pitch = 48.0
        start_pitch = 210.0
        drive = 2.3
        glitch_bits = 6
        decay = 2.8
    elif tier == 'singularity':
        sub_pitch = 37.0
        start_pitch = 160.0
        drive = 2.6
        glitch_bits = 6
        decay = 2.1
    elif tier == 'cyberpsycho':
        sub_pitch = 47.0
        start_pitch = 240.0
        drive = 3.2
        glitch_bits = 4
        decay = 3.1
    else: # infinity
        sub_pitch = 35.0
        start_pitch = 200.0
        drive = 2.8
        glitch_bits = 7
        decay = 1.9

    drop_start_idx = int(SAMPLE_RATE * drop_time)
    sub_dur = dur - drop_time
    sub = create_sub_drop(sub_dur, start_pitch=start_pitch, end_pitch=sub_pitch, decay_rate=decay, drive=drive)
    
    # 3. Brakence / Slayr glitch burst right at the impact
    glitch_dur = 0.35
    t_g = np.linspace(0, glitch_dur, int(SAMPLE_RATE * glitch_dur), endpoint=False)
    laser = create_laser_chirp(duration=0.10, f0=2200 if tier != 'cyberpsycho' else 3200, f1=sub_pitch * 2)
    
    # Metal / Digital click transient
    click = np.random.uniform(-1, 1, int(SAMPLE_RATE * 0.015)) * np.exp(-np.linspace(0, 15, int(SAMPLE_RATE * 0.015)))
    click_crushed = bitcrush(click, bits=glitch_bits)

    # Shimmer harmonic overtone cluster (celestial / space tail)
    shimmer = np.zeros(len(sub), dtype=np.float64)
    if tier in ('astral', 'singularity', 'infinity'):
        t_sub = np.linspace(0, sub_dur, len(sub), endpoint=False)
        harmonics = [523.25, 659.25, 783.99, 1046.50, 1318.51] # C5 chord cluster
        for h in harmonics:
            shimmer += 0.06 * np.sin(2 * np.pi * h * t_sub) * np.exp(-t_sub * 3.5)

    # Overclocked cyber servo stutter for Cybernetic & CyberPsycho
    cyber_fx = np.zeros(len(sub), dtype=np.float64)
    if tier in ('cybernetic', 'cyberpsycho'):
        t_sub = np.linspace(0, sub_dur, len(sub), endpoint=False)
        # FM mod glitch
        mod = np.sin(2 * np.pi * 140 * t_sub)
        carrier = np.sin(2 * np.pi * 880 * t_sub + 4.0 * mod) * np.exp(-t_sub * 7.0)
        cyber_fx = bitcrush(carrier, bits=glitch_bits) * 0.25

    # Mix drop layers
    drop_layer = sub * 0.90 + shimmer * 0.40 + cyber_fx * 0.35
    # Inject initial laser and click
    if len(laser) < len(drop_layer):
        drop_layer[:len(laser)] += laser * 0.45
    if len(click_crushed) < len(drop_layer):
        drop_layer[:len(click_crushed)] += click_crushed * 0.60

    # Brakence stutter repetition on the early transient (0.04s - 0.12s)
    stutter_target = drop_layer[:int(SAMPLE_RATE * 0.4)]
    stuttered = create_glitch_stutter(stutter_target, slice_dur=0.024, repeats=3, decay=0.75)
    drop_layer[:len(stuttered)] = drop_layer[:len(stuttered)] * 0.4 + stuttered * 0.6

    # Combine into master audio
    rem_samples = len(audio) - drop_start_idx
    actual_len = min(rem_samples, len(drop_layer))
    audio[drop_start_idx:drop_start_idx + actual_len] += drop_layer[:actual_len]

    # Peak normalization to -0.6 dBFS
    max_val = np.max(np.abs(audio))
    if max_val > 0:
        audio = (audio / max_val) * 0.93

    return audio

def synthesize_equip(tier_name):
    """Synthesizes punchy, tactile individual piece equip sound (~0.58s) for a given tier."""
    dur = 0.58
    total_samples = int(SAMPLE_RATE * dur)
    audio = np.zeros(total_samples, dtype=np.float64)
    t = np.linspace(0, dur, total_samples, endpoint=False)

    tier = tier_name.lower()
    if tier == 'astral':
        sub_pitch = 55.0
        click_f = 2400
        drive = 1.8
        bits = 8
    elif tier == 'cybernetic':
        sub_pitch = 60.0
        click_f = 3200
        drive = 2.2
        bits = 6
    elif tier == 'singularity':
        sub_pitch = 45.0
        click_f = 1900
        drive = 2.4
        bits = 6
    elif tier == 'cyberpsycho':
        sub_pitch = 58.0
        click_f = 3800
        drive = 2.8
        bits = 4
    else: # infinity
        sub_pitch = 44.0
        click_f = 2800
        drive = 2.5
        bits = 7

    # Micro vacuum suck pre-latch (35ms)
    suck_n = int(SAMPLE_RATE * 0.035)
    t_suck = np.linspace(0, 0.035, suck_n, endpoint=False)
    suck = np.sin(2 * np.pi * 180 * t_suck) * np.exp(6.0 * (t_suck / 0.035 - 1.0)) * 0.45
    audio[:suck_n] = suck

    latch_start = suck_n
    t_rem = t[latch_start:] - t[latch_start]

    # Sub-kick thump (808 style punch)
    sub_kick = np.sin(2 * np.pi * (sub_pitch * (1.0 + np.exp(-t_rem * 25.0))) * t_rem) * np.exp(-t_rem * 12.0)
    sub_kick = soft_clip(sub_kick * drive)

    # Sharp mechanical / digital latch snap
    click = np.sin(2 * np.pi * click_f * t_rem) * np.exp(-t_rem * 45.0)
    noise = np.random.uniform(-1, 1, len(t_rem)) * np.exp(-t_rem * 35.0)
    latch_snap = bitcrush(click * 0.5 + noise * 0.5, bits=bits)

    # Glitch laser flick
    laser = create_laser_chirp(duration=0.06, f0=click_f * 1.5, f1=sub_pitch * 2)

    combined_rem = sub_kick * 0.85 + latch_snap * 0.55
    if len(laser) < len(combined_rem):
        combined_rem[:len(laser)] += laser * 0.40

    audio[latch_start:latch_start + len(combined_rem)] += combined_rem

    # Peak normalization to -0.6 dBFS
    max_val = np.max(np.abs(audio))
    if max_val > 0:
        audio = (audio / max_val) * 0.93

    return audio

def save_ogg(audio, output_path):
    """Encodes float audio buffer to Vorbis .ogg via ffmpeg."""
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    pcm = audio.astype(np.float32).tobytes()
    cmd = [
        'ffmpeg', '-y',
        '-f', 'f32le',
        '-ar', str(SAMPLE_RATE),
        '-ac', '1',
        '-i', '-',
        '-c:a', 'libvorbis',
        '-q:a', '7',
        output_path
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = proc.communicate(input=pcm)
    if proc.returncode != 0:
        raise RuntimeError(f"FFmpeg failed encoding {output_path}: {err.decode('utf-8', errors='ignore')}")
    print(f"Generated: {output_path} ({os.path.getsize(output_path)} bytes)")

def main():
    base_dir = r"F:\GrokBot\work\infinium_pass2\src\main\resources\assets\infinium\sounds"
    families = ['astral', 'cybernetic', 'singularity', 'cyberpsycho', 'infinity']

    print("Generating Crimson Moon + Brakence/Slayr equip audio overhaul...")
    for f in families:
        # Full set fanfare
        fullset_audio = synthesize_fullset(f)
        fullset_path = os.path.join(base_dir, f, "fullset.ogg")
        save_ogg(fullset_audio, fullset_path)

        # Individual piece equip
        equip_audio = synthesize_equip(f)
        equip_path = os.path.join(base_dir, f, "equip.ogg")
        save_ogg(equip_audio, equip_path)

    print("All 10 sound assets generated successfully!")

if __name__ == '__main__':
    main()
