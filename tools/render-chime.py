#!/usr/bin/env python3
"""Renders the Nidoo songbird call to the notification sound, core/push/src/main/res/raw/nidoo_chime.wav.

A port of the web app's chime (Nidoo repo, crates/nidoo-web/src/chime.rs): two rising whistles
("tsee-tseet") then a quick trill, each note a sine gliding between two pitches under its own
attack / hold / decay envelope, with Web Audio's exponential ramps. Only the loudness differs:
the web call is quiet, so the render is normalized to PEAK and the system volume does the rest.

Usage: python3 tools/render-chime.py [output.wav]
"""

import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22_050  # The call stays under 4 kHz, a pure sine with no harmonics: well below Nyquist.
PEAK = 0.7  # About -3 dBFS.
LEAD = 0.02  # The web call starts 20 ms after the context's current time.
SILENT = 0.0001  # Exponential ramps cannot reach zero; the envelope starts and ends here.

# (at, from, to, glide, peak, attack, hold, decay), as in chime.rs.
WHISTLES = [
    (0.0, 2300.0, 3500.0, 0.09, 0.049, 0.01, 0.0, 0.08),
    (0.17, 2500.0, 3900.0, 0.11, 0.056, 0.01, 0.02, 0.09),
]
TRILL_START, TRILL_STEP, TRILL_COUNT = 0.45, 0.055, 7
TRILL_HIGH, TRILL_LOW, TRILL_DROOP, TRILL_PEAK = 3800.0, 3300.0, 0.92, 0.0385


def notes():
    trill = []
    for i in range(TRILL_COUNT):
        pitch = TRILL_HIGH if i % 2 == 0 else TRILL_LOW
        trill.append((TRILL_START + TRILL_STEP * i, pitch, pitch * TRILL_DROOP, 0.04, TRILL_PEAK, 0.004, 0.0, 0.045))
    return WHISTLES + trill


def exp_ramp(v0, v1, t0, t1, t):
    """Web Audio's exponentialRampToValueAtTime: v0 at t0 to v1 at t1, then held."""
    if t <= t0:
        return v0
    if t >= t1:
        return v1
    return v0 * (v1 / v0) ** ((t - t0) / (t1 - t0))


def render():
    all_notes = notes()
    end = LEAD + max(at + attack + hold + decay for at, _, _, _, _, attack, hold, decay in all_notes)
    length = int(math.ceil((end + 0.03) * RATE))
    out = [0.0] * length
    for at, f0, f1, glide, peak, attack, hold, decay in all_notes:
        start = LEAD + at
        sustain = start + attack + hold
        stop = start + attack + hold + decay
        phase = 0.0
        for n in range(int(start * RATE), min(length, int(math.ceil(stop * RATE)) + 1)):
            t = n / RATE
            freq = exp_ramp(f0, f1, start, start + glide, t)
            if t < start + attack:
                gain = exp_ramp(SILENT, peak, start, start + attack, t)
            elif t < sustain:
                gain = peak
            else:
                gain = exp_ramp(peak, SILENT, sustain, stop, t)
            out[n] += gain * math.sin(phase)
            phase += 2 * math.pi * freq / RATE
    scale = PEAK / max(abs(s) for s in out)
    return [s * scale for s in out]


def main():
    default = Path(__file__).resolve().parent.parent / "core/push/src/main/res/raw/nidoo_chime.wav"
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else default
    target.parent.mkdir(parents=True, exist_ok=True)
    samples = render()
    with wave.open(str(target), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", round(s * 32767)) for s in samples))
    print(f"{target}: {len(samples) / RATE:.2f} s, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
