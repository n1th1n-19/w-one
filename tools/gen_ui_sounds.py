#!/usr/bin/env python3
"""Generates the mouse, trackpad and gamepad click sounds.

These are synthesised from scratch for this project (damped sine bursts plus a little noise),
so they carry the project licence and no third-party terms. Needs ffmpeg for the OGG encode.
Usage: tools/gen_ui_sounds.py
"""
import math, random, struct, subprocess, tempfile, wave
from pathlib import Path

SR = 48000
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/sounds/ui"


def burst(freq, dur, decay, amp=1.0, sweep=0.0, noise=0.0, rng=None):
    n = int(SR * dur)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = freq + sweep * t / dur
        phase += 2 * math.pi * f / SR
        env = math.exp(-t * decay)
        s = math.sin(phase) * env
        if noise:
            s += (rng.random() * 2 - 1) * noise * math.exp(-t * decay * 2.5)
        out.append(s * amp)
    return out


def mix(*parts):
    n = max(len(p) for p in parts)
    out = [0.0] * n
    for p in parts:
        for i, v in enumerate(p):
            out[i] += v
    peak = max(abs(v) for v in out) or 1.0
    return [v / peak * 0.8 for v in out]


SOUNDS = {
    # name: builder(rng, variant) -> samples
    "mouse_down": lambda r, v: mix(burst(2800 + v * 90, 0.03, 260, 1, noise=0.5, rng=r), burst(1400, 0.02, 300, 0.4)),
    "mouse_up": lambda r, v: mix(burst(3300 + v * 90, 0.02, 380, 1, noise=0.3, rng=r)),
    "thunk_down": lambda r, v: mix(burst(150 + v * 6, 0.07, 55, 1, sweep=-40, noise=0.25, rng=r), burst(900, 0.01, 500, 0.3)),
    "thunk_up": lambda r, v: mix(burst(210 + v * 6, 0.04, 90, 1, noise=0.15, rng=r)),
    "pad_down": lambda r, v: mix(burst(320 + v * 10, 0.05, 70, 1, sweep=-120, rng=r)),
    "pad_up": lambda r, v: mix(burst(420 + v * 10, 0.03, 110, 1, sweep=-80, rng=r)),
    "dpad_down": lambda r, v: mix(burst(1900 + v * 60, 0.025, 300, 1, noise=0.4, rng=r), burst(700, 0.02, 250, 0.4)),
    "bumper_down": lambda r, v: mix(burst(950 + v * 30, 0.04, 140, 1, noise=0.35, rng=r), burst(2300, 0.015, 400, 0.5)),
    "ratchet_tick": lambda r, v: mix(burst(3600 + v * 100, 0.012, 600, 1, noise=0.3, rng=r)),
    "chime_home": lambda r, v: mix(burst(880, 0.18, 22, 1), burst(1320, 0.18, 26, 0.6)),
}
VARIANTS = 3


def write(name, i, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32000)) for s in samples))
        subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", tmp.name,
                        "-c:a", "libvorbis", "-q:a", "4", str(OUT / f"{name}_{i:02d}.ogg")], check=True)


for name, build in SOUNDS.items():
    for v in range(VARIANTS):
        write(name, v, build(random.Random(hash(name) % 1000 + v), v))
print(len(list(OUT.glob("*.ogg"))), "files in", OUT)
