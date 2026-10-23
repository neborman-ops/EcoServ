#!/usr/bin/env python3
"""
EcoServ 1.9: таблицы для чёрных дыр (точная метрика Шварцшильда).

Луч света в плоскости орбиты описывается уравнением Бине (G = c = M = 1):
    d^2u/dphi^2 = 3u^2 - u,   u = 1/r.
Луч, выпущенный назад из камеры (на бесконечности), стартует с u = 0, du/dphi = 1/b (b — прицельный параметр).

Две таблицы (PNG, каждое значение — 16 бит в двух байтах: старший, младший):
  schwarzschild_deflection.png  1024 x 1  RG = alpha(b)/(4pi) — полный угол отклонения (только b > b_c)
                                         BA = 1/u_min / 64      — ближайшее сближение (не нужно шейдеру, справочно)
  schwarzschild_disk.png         512 x 256  RG = r1/64, BA = r2/64 — радиусы, где луч пересекает плоскость
                                         диска в первый (phi = phi_d) и второй (phi = phi_d + pi) раз; 0 — не пересекает
                                         (улетел или упал в горизонт раньше).

Оси: столбец — b по кусочной шкале (см. b_of_t), строка — phi_d in (0, pi).
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy.integrate import solve_ivp

BC = 3.0 * math.sqrt(3.0)
BMAX = 64.0
W_DEF = 1024
W_DISK = 512
H_DISK = 256


def b_of_t(t):
    """Столбец таблицы диска -> b: четверть ширины — тень (0..b_c), остальное — b_c..64 по квадрату."""
    if t < 0.25:
        return BC * t / 0.25
    s = (t - 0.25) / 0.75
    return BC + (BMAX - BC) * s * s


def b_of_s(s):
    """Столбец таблицы отклонения -> b (только снаружи тени)."""
    return BC + (BMAX - BC) * s * s


def trace(b):
    """Траектория u(phi). Возвращает (sol, phi_end, captured)."""
    b = max(b, 1e-4)
    phi0 = 1e-9
    y0 = [phi0 / b, 1.0 / b]

    def rhs(_, y):
        return [y[1], 3.0 * y[0] * y[0] - y[0]]

    def escape(_, y):
        return y[0]
    escape.terminal = True
    escape.direction = -1

    def horizon(_, y):
        return y[0] - 0.5
    horizon.terminal = True
    horizon.direction = 1

    sol = solve_ivp(rhs, (phi0, 6.0 * math.pi), y0, method='DOP853', rtol=1e-11, atol=1e-13,
                    events=(escape, horizon), dense_output=True, max_step=0.01)
    captured = len(sol.t_events[1]) > 0
    if len(sol.t_events[0]) > 0:
        end = sol.t_events[0][0]
    elif captured:
        end = sol.t_events[1][0]
    else:
        end = sol.t[-1]
    return sol, end, captured


def enc16(v):
    q = int(round(min(max(v, 0.0), 1.0) * 65535.0))
    return (q >> 8) & 255, q & 255


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    # --- отклонение -----------------------------------------------------------------------------------
    dfl = Image.new('RGBA', (W_DEF, 1))
    px = dfl.load()
    for i in range(W_DEF):
        s = (i + 0.5) / W_DEF
        b = b_of_s(s)
        sol, end, captured = trace(b)
        alpha = (end - math.pi) if not captured else 4.0 * math.pi
        umax = float(np.max(sol.sol(np.linspace(1e-9, end, 400))[0]))
        rmin = 1.0 / max(umax, 1e-9)
        r_, g_ = enc16(alpha / (4.0 * math.pi))
        b_, a_ = enc16(rmin / BMAX)
        px[i, 0] = (r_, g_, b_, a_)
        if i % 128 == 0:
            print('deflection', i, 'b=%.4f alpha=%.5f weak=%.5f rmin=%.3f' % (b, alpha, 4.0 / b, rmin))
    dfl.save(os.path.join(out_dir, 'schwarzschild_deflection.png'))

    # --- пересечения с диском -------------------------------------------------------------------------
    disk = Image.new('RGBA', (W_DISK, H_DISK))
    px = disk.load()
    phis = np.array([math.pi * (j + 0.5) / H_DISK for j in range(H_DISK)])
    for i in range(W_DISK):
        t = (i + 0.5) / W_DISK
        b = b_of_t(t)
        sol, end, captured = trace(b)
        for k in range(2):
            ph = phis + k * math.pi
            valid = ph < end
            u = np.zeros_like(ph)
            if valid.any():
                u[valid] = sol.sol(ph[valid])[0]
            for j in range(H_DISK):
                r = 0.0
                if valid[j] and u[j] > 1.0 / BMAX:
                    r = 1.0 / u[j]
                hi, lo = enc16(r / BMAX)
                old = px[i, j] if k == 1 else (0, 0, 0, 0)
                px[i, j] = (hi, lo, old[2], old[3]) if k == 0 else (old[0], old[1], hi, lo)
        if i % 64 == 0:
            print('disk', i, 'b=%.4f end=%.4f captured=%s' % (b, end, captured))
    disk.save(os.path.join(out_dir, 'schwarzschild_disk.png'))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
