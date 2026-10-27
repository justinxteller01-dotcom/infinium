#!/usr/bin/env python3
"""
Generates dedicated Spacetime Warp sound effects for Infinium (Batch 01):
1. warp_departure.ogg - Spacetime tear, vacuum suck, dimensional fold transient.
2. warp_arrival.ogg   - Spatial displacement sub-bass impact, glassy crystal arrival chime.
"""

import os
import subprocess
import numpy as np

SAMPLE_RATE = 48000

def soft_clip(x, drive=1.5):
    return np.tanh(drive * x)

def bitcrush(x, bits=8):
    q = 2 ** (bits - 1)
    return np.round(x * q) / q

def save_ogg(audio, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    wav_path = path.replace('.ogg', '.wav')
    audio = audio / (np.max(np.abs(audio)) + 1e-9)
    audio = np.clip(audio * 0.92, -1.0, 1.0)
    int16_data = (audio * 32767).astype(np.int16)
    
    import wave
    with wave.open(wav_path, 'w') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(int16_data.tobytes())
        
    cmd = ['ffmpeg', '-y', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '6', path]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    if os.path.exists(wav_path):
        os.remove(wav_path)
    print(f"Generated: {path}")

def generate_warp_departure():
    duration = 0.28
    n_samples = int(SAMPLE_RATE * duration)
    t = np.linspace(0, duration, n_samples, endpoint=False)
    
    # 1. Vacuum implosion pre-suck (0 to 0.12s)
    suck_dur = 0.12
    t_suck = t[t < suck_dur]
    env_suck = np.exp(4.0 * (t_suck / suck_dur - 1.0))
    freq_suck = 450 * (90 / 450) ** (t_suck / suck_dur)
    suck = 0.6 * np.sin(2 * np.pi * np.cumsum(freq_suck) / SAMPLE_RATE) * env_suck
    
    # 2. Dimensional rip transient (pitch dive + bitcrushed FM chirp)
    t_rip = t[t >= suck_dur] - suck_dur
    env_rip = np.exp(-t_rip * 18.0)
    freq_rip = 80 + 1200 * np.exp(-t_rip * 35.0)
    modulator = np.sin(2 * np.pi * 320 * t_rip) * 3.5
    rip = np.sin(2 * np.pi * (freq_rip * t_rip + modulator)) * env_rip
    rip = bitcrush(rip, bits=7)
    
    # 3. High crystal sizzle / ethereal tail
    sparkle_freq = 2400.0
    sparkle = np.sin(2 * np.pi * sparkle_freq * t) * np.exp(-t * 12.0) * 0.25
    
    sig = np.zeros(n_samples)
    sig[:len(t_suck)] += suck * 0.85
    sig[len(t_suck):] += rip * 1.1
    sig += sparkle
    
    # Warm saturation
    out = soft_clip(sig, drive=1.8)
    return out

def generate_warp_arrival():
    duration = 0.35
    n_samples = int(SAMPLE_RATE * duration)
    t = np.linspace(0, duration, n_samples, endpoint=False)
    
    # 1. Sub-bass physical displacement punch (45 to 65 Hz)
    sub_pitch = 65.0 * np.exp(-t * 12.0) + 42.0
    sub_phase = 2 * np.pi * np.cumsum(sub_pitch) / SAMPLE_RATE
    sub_env = np.exp(-t * 14.0)
    sub = (np.sin(sub_phase) + 0.35 * np.sin(sub_phase * 2)) * sub_env
    sub = soft_clip(sub, drive=2.5)
    
    # 2. Glassy crystal arrival chime (harmonic triad: 1100Hz, 1650Hz, 2200Hz)
    chime_env = np.exp(-t * 9.0)
    chime = (
        0.50 * np.sin(2 * np.pi * 1100 * t) +
        0.35 * np.sin(2 * np.pi * 1650 * t) +
        0.20 * np.sin(2 * np.pi * 2200 * t)
    ) * chime_env
    
    # 3. Micro dimensional shimmer transient
    chirp_env = np.exp(-t * 40.0)
    chirp = np.sin(2 * np.pi * (3000 - 1500 * t) * t) * chirp_env * 0.4
    
    sig = sub * 1.2 + chime * 0.7 + chirp * 0.5
    out = soft_clip(sig, drive=1.4)
    return out

if __name__ == '__main__':
    out_dir = r'F:\GrokBot\work\infinium_pass2\src\main\resources\assets\infinium\sounds\warp'
    save_ogg(generate_warp_departure(), os.path.join(out_dir, 'warp_departure.ogg'))
    save_ogg(generate_warp_arrival(), os.path.join(out_dir, 'warp_arrival.ogg'))
