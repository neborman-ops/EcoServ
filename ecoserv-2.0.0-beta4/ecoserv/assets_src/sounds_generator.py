#!/usr/bin/env python3
"""
EcoServ 1.9: процедурные звуки мода (моно, 44.1 кГц, OGG Vorbis через ffmpeg).
Запуск: python3 sounds_generator.py <папка assets/ecoserv>
Создаёт sounds/**/*.ogg и sounds.json (ключи совпадают с ModSounds).
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(1909)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.01, r=0.2, sustain=1.0):
    """Огибающая: атака a, затухание r (сек)."""
    e = np.ones(n) * sustain
    na = max(1, int(a * SR))
    nr = max(1, int(r * SR))
    e[:na] = np.linspace(0, 1, na) * sustain
    if nr < n:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def expdecay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def lowpass(x, cutoff):
    # однополюсный фильтр, достаточно для звуков игры
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, lo, hi):
    return lowpass(highpass(x, lo), hi)


def noise(n):
    return rng.uniform(-1, 1, n)


def sweep(f0, f1, sec):
    tt = t(sec)
    f = np.geomspace(f0, f1, len(tt))
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def tone(freq, sec, kind='sine'):
    tt = t(sec)
    ph = 2 * np.pi * freq * tt
    if kind == 'square':
        return np.sign(np.sin(ph)) * 0.6
    if kind == 'tri':
        return 2 / np.pi * np.arcsin(np.sin(ph))
    return np.sin(ph)


def norm(x, peak=0.85):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def concat(*parts):
    return np.concatenate(parts)


def pad(x, sec):
    return np.concatenate([x, np.zeros(int(SR * sec))])


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


# --- звуки ------------------------------------------------------------------------------------------

def portal_hum():
    sec = 2.6
    tt = t(sec)
    x = np.sin(2 * np.pi * 55 * tt) * 0.6 + np.sin(2 * np.pi * 82.4 * tt + 0.3) * 0.35
    x += np.sin(2 * np.pi * 110.7 * tt) * 0.15 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * tt))
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * 1.3 * tt)
    x += lowpass(noise(len(tt)), 300) * 0.5
    return norm(x * env(len(tt), 0.5, 0.8), 0.7)


def portal_whoosh():
    sec = 1.2
    n = int(SR * sec)
    x = noise(n)
    f = np.geomspace(200, 3000, n)
    y = np.zeros(n)
    acc = 0.0
    for i in range(n):
        a = np.exp(-2 * np.pi * f[i] / SR)
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    y = highpass(y, 120)
    return norm(y * np.sin(np.linspace(0, np.pi, n)) ** 1.5)


def portal_release():
    w = portal_whoosh()[::-1] * 0.7
    thump = sweep(120, 35, 0.5) * expdecay(int(SR * 0.5), 0.12)
    shimmer = (tone(1320, 1.0) + tone(1980, 1.0) * 0.5) * expdecay(SR, 0.25) * 0.25
    return norm(mix(w, pad(np.zeros(0), 0.75), concat(np.zeros(int(SR * 0.9)), thump),
                    concat(np.zeros(int(SR * 0.95)), shimmer)))


def monolith():
    sec = 2.2
    tt = t(sec)
    x = sum(np.sin(2 * np.pi * f * tt) / (i + 1) for i, f in enumerate([392, 587.3, 784, 1174.7]))
    x *= 0.6 + 0.4 * np.sin(2 * np.pi * 3.2 * tt)
    return norm(x * env(len(tt), 0.6, 0.9), 0.45)


def abyss_fall():
    s = sweep(900, 60, 1.6) * expdecay(int(SR * 1.6), 0.8)
    n = lowpass(noise(int(SR * 1.6)), 1200) * np.linspace(0.2, 1, int(SR * 1.6)) * 0.6
    x = mix(s, n)
    # глитч-заикания
    out = x.copy()
    for k in range(6):
        a = int(rng.uniform(0.2, 1.3) * SR)
        ln = int(0.04 * SR)
        out[a:a + ln * 3] = np.tile(x[a:a + ln], 3)[:len(out[a:a + ln * 3])]
    return norm(out * env(len(out), 0.01, 0.4))


def ui_click():
    n = int(SR * 0.06)
    x = tone(2100, 0.06) * expdecay(n, 0.008) + noise(n) * expdecay(n, 0.002) * 0.4
    return norm(x, 0.6)


def ui_open():
    a = tone(660, 0.07) * env(int(SR * 0.07), 0.003, 0.05)
    b = tone(990, 0.1) * env(int(SR * 0.1), 0.003, 0.08)
    return norm(concat(a, b), 0.5)


def ui_toast():
    n = int(SR * 0.7)
    x = (tone(880, 0.7) + tone(1320, 0.7) * 0.5 + tone(1760, 0.7) * 0.2) * expdecay(n, 0.18)
    return norm(x, 0.5)


def coin():
    a = tone(988, 0.08, 'square') * env(int(SR * 0.08), 0.002, 0.02)
    b = tone(1319, 0.35, 'square') * expdecay(int(SR * 0.35), 0.12)
    return norm(lowpass(concat(a, b), 6000), 0.5)


def countdown():
    n = int(SR * 0.15)
    return norm(tone(880, 0.15, 'tri') * env(n, 0.003, 0.06), 0.6)


def arpeggio(freqs, step, tail, kind='tri'):
    parts = []
    for i, f in enumerate(freqs):
        last = i == len(freqs) - 1
        d = tail if last else step
        n = int(SR * d)
        parts.append(tone(f, d, kind) * (expdecay(n, d * 0.6) if last else env(n, 0.004, step * 0.3)))
    return norm(concat(*parts), 0.6)


def game_start():
    return arpeggio([523.3, 659.3, 784, 1046.5], 0.09, 0.5)


def game_win():
    x = arpeggio([523.3, 659.3, 784, 1046.5, 1318.5], 0.11, 0.9)
    chord = (tone(523.3, 0.9) + tone(659.3, 0.9) + tone(784, 0.9)) * expdecay(int(SR * 0.9), 0.4) * 0.3
    return norm(mix(x, concat(np.zeros(int(SR * 0.44)), chord)), 0.65)


def game_lose():
    return arpeggio([392, 349.2, 311.1, 233.1], 0.16, 0.8, 'sine')


def checkpoint():
    n = int(SR * 0.6)
    x = (tone(1568, 0.6) + tone(2093, 0.6) * 0.6) * expdecay(n, 0.15)
    return norm(x, 0.55)


def trap():
    n = int(SR * 0.9)
    x = noise(n) * expdecay(n, 0.02)
    for f in (217, 389, 593, 811, 1270):
        x += np.sin(2 * np.pi * f * t(0.9)) * expdecay(n, 0.25) * 0.4
    return norm(lowpass(x, 5000))


def alarm():
    seg = []
    for i in range(4):
        seg.append(tone(700 if i % 2 == 0 else 930, 0.3, 'square') * env(int(SR * 0.3), 0.01, 0.02))
    return norm(lowpass(concat(*seg), 3500), 0.55)


def steam():
    n = int(SR * 1.5)
    x = highpass(noise(n), 2500) * env(n, 0.05, 0.6)
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * 17 * t(1.5))
    return norm(x, 0.6)


def valve():
    sq = sweep(1400, 900, 0.35) * expdecay(int(SR * 0.35), 0.15) * 0.5
    clunk = lowpass(noise(int(SR * 0.2)), 600) * expdecay(int(SR * 0.2), 0.04)
    return norm(concat(sq, clunk), 0.6)


def chaser():
    sec = 1.8
    n = int(SR * sec)
    g = lowpass(noise(n), 400) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t(sec)))
    g += np.sin(2 * np.pi * 41 * t(sec)) * 0.6
    return norm(g * env(n, 0.3, 0.6), 0.8)


def heartbeat():
    def thump(f, d, amp):
        n = int(SR * d)
        return sweep(f * 1.6, f, d) * expdecay(n, d * 0.35) * amp
    x = concat(thump(70, 0.16, 1.0), np.zeros(int(SR * 0.08)), thump(60, 0.2, 0.75), np.zeros(int(SR * 0.3)))
    return norm(lowpass(x, 400), 0.9)


SOUNDS = {
    'portal.hum': ('portal/hum', portal_hum, 'ambient'),
    'portal.whoosh': ('portal/whoosh', portal_whoosh, 'player'),
    'portal.release': ('portal/release', portal_release, 'player'),
    'portal.monolith': ('portal/monolith', monolith, 'ambient'),
    'abyss.fall': ('abyss/fall', abyss_fall, 'player'),
    'ui.click': ('ui/click', ui_click, 'master'),
    'ui.open': ('ui/open', ui_open, 'master'),
    'ui.toast': ('ui/toast', ui_toast, 'master'),
    'ui.coin': ('ui/coin', coin, 'player'),
    'game.countdown': ('game/countdown', countdown, 'player'),
    'game.start': ('game/start', game_start, 'player'),
    'game.win': ('game/win', game_win, 'player'),
    'game.lose': ('game/lose', game_lose, 'player'),
    'game.checkpoint': ('game/checkpoint', checkpoint, 'player'),
    'game.trap': ('game/trap', trap, 'block'),
    'game.alarm': ('game/alarm', alarm, 'block'),
    'game.steam': ('game/steam', steam, 'block'),
    'game.valve': ('game/valve', valve, 'block'),
    'game.chaser': ('game/chaser', chaser, 'hostile'),
    'game.heartbeat': ('game/heartbeat', heartbeat, 'player'),
}

SUBTITLES = {
    'portal.hum': 'Гул портала', 'portal.whoosh': 'Свист портала', 'portal.release': 'Переход через портал',
    'portal.monolith': 'Звон монолита', 'abyss.fall': 'Падение в бездну', 'ui.click': 'Щелчок',
    'ui.open': 'Окно открыто', 'ui.toast': 'Уведомление', 'ui.coin': 'Монеты', 'game.countdown': 'Отсчёт',
    'game.start': 'Старт игры', 'game.win': 'Победа', 'game.lose': 'Поражение', 'game.checkpoint': 'Точка сохранения',
    'game.trap': 'Ловушка', 'game.alarm': 'Тревога', 'game.steam': 'Шипение пара', 'game.valve': 'Вентиль',
    'game.chaser': 'Тьма приближается', 'game.heartbeat': 'Стук сердца',
}


def write_ogg(samples, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.unlink(wav_path)


def main(assets):
    data = {}
    for key, (rel, fn, cat) in SOUNDS.items():
        write_ogg(fn(), os.path.join(assets, 'sounds', rel + '.ogg'))
        data[key] = {'category': cat, 'subtitle': 'ecoserv.subtitle.' + key, 'sounds': ['ecoserv:' + rel]}
        print('ok', key)
    with open(os.path.join(assets, 'sounds.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    with open(os.path.join(assets, 'subtitles_ru.txt'), 'w', encoding='utf-8') as f:
        for k, v in SUBTITLES.items():
            f.write('ecoserv.subtitle.%s=%s\n' % (k, v))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
