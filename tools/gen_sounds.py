#!/usr/bin/env python3
"""Synthesizes every sound effect used by the mod (original audio, no samples).

Usage: python3 tools/gen_sounds.py   (needs numpy + ffmpeg with libvorbis)
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "ironhud", "sounds")
rng = np.random.default_rng(1234)


def t_of(d):
    return np.linspace(0, d, int(SR * d), endpoint=False)


def env(n, a=0.005, r=0.03):
    e = np.ones(n)
    na, nr = max(1, int(SR * a)), max(1, int(SR * r))
    e[:na] = np.linspace(0, 1, na)
    e[-nr:] = np.minimum(e[-nr:], np.linspace(1, 0, nr))
    return e


def tone(f, d, a=0.005, r=0.03, harm=(1.0,), vol=1.0):
    t = t_of(d)
    s = sum(h * np.sin(2 * np.pi * f * (i + 1) * t) for i, h in enumerate(harm))
    return vol * s / max(1e-9, sum(harm)) * env(len(t), a, r)


def sweep(f0, f1, d, a=0.01, r=0.05, vol=1.0):
    t = t_of(d)
    f = f0 * (f1 / f0) ** (t / d)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return vol * np.sin(ph) * env(len(t), a, r)


def silence(d):
    return np.zeros(int(SR * d))


def lowpass(x, k):
    kern = np.ones(k) / k
    return np.convolve(x, kern, mode="same")


def save(name, x, gain=0.8):
    x = np.asarray(x, dtype=np.float64)
    x = x / max(1e-9, np.max(np.abs(x))) * gain
    pcm = (x * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    out = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path,
                    "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    os.unlink(path)
    print("wrote", out)


os.makedirs(OUT, exist_ok=True)

# HUD boot: rising sweep, then three confirmation chirps
boot = np.concatenate([
    sweep(180, 1400, 0.7, vol=0.6) + 0.25 * sweep(360, 2800, 0.7, vol=0.6),
    silence(0.05),
    tone(1320, 0.07, harm=(1, .3)), silence(0.05),
    tone(1760, 0.07, harm=(1, .3)), silence(0.05),
    tone(2349, 0.16, r=0.12, harm=(1, .3)),
])
save("boot", boot)

save("power_down", sweep(1000, 110, 0.6, r=0.2))

# generic alert: two soft tones
save("alert", np.concatenate([tone(988, 0.11, harm=(1, .4)), silence(0.03),
                              tone(740, 0.16, r=0.08, harm=(1, .4))]))

# critical klaxon: pulsed, slightly distorted square-ish wave
pulse = np.tanh(2.2 * tone(620, 0.22, a=0.01, r=0.04, harm=(1, .5, .33, .2)))
klax = np.concatenate([pulse, silence(0.06), np.tanh(2.2 * tone(840, 0.22, a=0.01, r=0.04, harm=(1, .5, .33, .2))),
                       silence(0.06), pulse, silence(0.06),
                       np.tanh(2.2 * tone(840, 0.28, a=0.01, r=0.1, harm=(1, .5, .33, .2)))])
save("critical", klax)

# armor breach: noise crunch + sub thump + metallic ring
n = int(SR * 0.6)
noise = rng.standard_normal(n) * np.exp(-np.linspace(0, 9, n))
crunch = lowpass(noise, 6) * 1.4
thump = sweep(140, 38, 0.6, a=0.002, r=0.2) * np.exp(-np.linspace(0, 5, n))
ring = (tone(1180, 0.6, a=0.001, r=0.4) + 0.6 * tone(1770, 0.6, a=0.001, r=0.4)) * np.exp(-np.linspace(0, 8, n)) * 0.18
save("breach", crunch + thump + ring)

# lock-on blip
save("lock", np.concatenate([tone(1500, 0.045, harm=(1, .3)), silence(0.025), tone(2100, 0.06, harm=(1, .3))]))

# target eliminated: descending triple
save("kill", np.concatenate([tone(1250, 0.08, harm=(1, .3)), silence(0.03),
                             tone(940, 0.08, harm=(1, .3)), silence(0.03),
                             tone(620, 0.2, r=0.12, harm=(1, .3))]))

save("tick", tone(2600, 0.02, a=0.001, r=0.01))

# collision warning: fast triple beep
b = tone(1040, 0.09, a=0.004, r=0.02, harm=(1, .5, .3))
save("collision", np.concatenate([b, silence(0.05), b, silence(0.05), b, silence(0.05),
                                  tone(1040, 0.2, a=0.004, r=0.08, harm=(1, .5, .3))]))

# waypoint ping
save("ping", sweep(900, 1700, 0.18, a=0.005, r=0.1))


# ---------------------------------------------------------------- film-style extras
def noise(d, vol=1.0):
    return vol * rng.standard_normal(int(SR * d))


def fade(x, a=0.02, r=0.05):
    return x * env(len(x), a, r)


# helmet engage: servo clamp + rising power-up + soft click
n = int(SR * 0.9)
tt = t_of(0.9)
servo = sweep(180, 620, 0.35, a=0.02, r=0.1, vol=0.5) * (1 + 0.4 * np.sin(2 * np.pi * 38 * t_of(0.35)))
click = np.concatenate([tone(1900, 0.012, a=0.0005, r=0.008, vol=0.9), silence(0.01)])
rise = sweep(300, 1800, 0.5, a=0.1, r=0.15, vol=0.45) + 0.3 * sweep(600, 3600, 0.5, a=0.1, r=0.15)
x = np.zeros(n)
x[:len(servo)] += servo
x[int(SR * 0.3):int(SR * 0.3) + len(click)] += click
x[int(SR * 0.35):int(SR * 0.35) + len(rise)] += rise
x += fade(lowpass(noise(0.9, 0.12), 6), 0.05, 0.4)
save("engage", x)

# wide-area scan sweep: shimmering rising whoosh with a descending tail
d = 1.6
tt = t_of(d)
f = 200 * (9 ** (tt / d))
sw = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.4
shim = lowpass(noise(d, 0.5), 4) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * tt))
tail = np.exp(-np.linspace(0, 5, len(tt)))
x = (sw + shim * np.linspace(0.2, 1, len(tt))) * env(len(tt), 0.05, 0.5)
x += 0.25 * np.sin(2 * np.pi * 90 * tt) * np.exp(-tt * 2.2)
save("scan_sweep", x)

# sonar return: soft ping with echo
p1 = tone(1320, 0.5, a=0.002, r=0.45, harm=(1, .25)) * np.exp(-t_of(0.5) * 7)
x = np.zeros(int(SR * 0.9))
for i, g in enumerate((1.0, 0.45, 0.2)):
    o = int(SR * 0.17 * i)
    x[o:o + len(p1)] += g * p1
save("sonar", x)

# data blip: tiny two-tone chirp
save("blip", np.concatenate([tone(2400, 0.02, a=0.001, r=0.01), tone(3100, 0.025, a=0.001, r=0.012)]) * 0.8)

# servo whine (zoom / optics)
d = 0.42
tt = t_of(d)
fs = 420 + 260 * np.sin(np.pi * tt / d)
x = np.sin(2 * np.pi * np.cumsum(fs) / SR) * 0.5 + 0.2 * np.sign(np.sin(2 * np.pi * 55 * tt)) * 0.3
save("servo", lowpass(x, 3) * env(len(tt), 0.03, 0.12))

# panel swish
d = 0.28
tt = t_of(d)
x = lowpass(noise(d, 1.0), 12) * np.sin(np.pi * tt / d) ** 1.5
x += 0.25 * sweep(500, 2200, d, a=0.02, r=0.1)
save("swish", x)

# tactical lock engage: shock thump + rising triple chirp
thump = sweep(150, 38, 0.35, a=0.002, r=0.2, vol=1.0) * np.exp(-t_of(0.35) * 5)
chirp = np.concatenate([sweep(900, 1500, 0.06), silence(0.02), sweep(1200, 2000, 0.06), silence(0.02),
                        sweep(1600, 2800, 0.1, r=0.06)])
x = np.zeros(int(SR * 0.6))
x[:len(thump)] += thump * 0.9
x[int(SR * 0.08):int(SR * 0.08) + len(chirp)] += chirp * 0.6
x += fade(lowpass(noise(0.6, 0.2), 8), 0.01, 0.4)
save("lock_engage", x)

# hologram materialize: glassy ascending arpeggio with shimmer
notes = [660, 880, 1100, 1320, 1760, 2200]
x = np.zeros(int(SR * 1.0))
for i, fr in enumerate(notes):
    n1 = tone(fr, 0.4, a=0.004, r=0.3, harm=(1, .35, .15)) * np.exp(-t_of(0.4) * 5)
    o = int(SR * 0.09 * i)
    x[o:o + len(n1)] += 0.6 * n1
sw2 = sweep(200, 1200, 0.9, a=0.2, r=0.4)
x[:len(sw2)] += 0.2 * sw2
save("holo", x)

# terrain contact: low pulse as the scan wave hits relief
d = 0.3
tt = t_of(d)
x = np.sin(2 * np.pi * 110 * tt) * np.exp(-tt * 14) + 0.4 * np.sin(2 * np.pi * 440 * tt) * np.exp(-tt * 25)
save("terrain_pulse", x)

# acquisition: quick 3-note target-found chime
save("acquire", np.concatenate([tone(1760, 0.05, harm=(1, .3)), silence(0.02),
                                tone(2350, 0.05, harm=(1, .3)), silence(0.02),
                                tone(2960, 0.12, r=0.08, harm=(1, .3))]))
