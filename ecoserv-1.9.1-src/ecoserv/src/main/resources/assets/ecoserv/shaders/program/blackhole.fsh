#version 120

// EcoServ 1.9: искажение пространства.
//  Mode 0 — чёрная дыра ГЛИТЧ-бездны: тень, линза и аккреционный диск по метрике Шварцшильда.
//  Mode 1 — игрок, вышедший из бездны: он сам не искажается, пространство вокруг расходится волной.
//  Mode 2 — портал «Чёрная дыра»: внутри тени — окно в место назначения, вокруг — точная линза, по желанию диск.
//  Mode 3 — монолитный портал: лёгкая рябь только в полосе ~1 блок вокруг картинки.
//
// Физика (G = c = M = 1). Луч из камеры описывается уравнением Бине u'' = 3u^2 - u, u = 1/r.
//   Тень: b < b_c = 3*sqrt(3). Отклонение alpha(b) и радиусы, где луч пересекает плоскость диска,
//   заранее посчитаны точно (assets_src/schwarzschild_lut.py) и лежат в таблицах DeflSampler / DiskSampler.
//   Quality 3 — то же самое интегрируется прямо в шейдере (RK4) с учётом конечного расстояния до камеры.
//   Диск — профиль Шакуры–Сюняева T(r) ~ r^-3/4 (1 - sqrt(r_in/r))^1/4, r_in = 6M (ISCO),
//   доплер и гравитационное красное смещение: 1+z = (1 + Omega*L_z) / sqrt(1 - 3/r), яркость ~ g^4.

uniform sampler2D DiffuseSampler;
uniform sampler2D DeflSampler;
uniform sampler2D DiskSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float Time;
uniform float Clock;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Aspect;
uniform float Mode;
uniform float Inner;
// экранная высота 0..1 -> tan(угла): tan(theta) = |d| / Sc
uniform float Sc;
// угловой радиус тени (радианы) и расстояние до камеры в единицах M
uniform float ThetaC;
uniform float Dist;
uniform float LensK;
uniform float Quality;
uniform float Steps;
uniform float Flash;
// диск
uniform float DiskOn;
uniform vec3 DiskNormal;
uniform float DiskInner;
uniform float DiskOuter;
uniform float DiskTemp;
uniform float DiskSpin;
uniform float DiskBright;
uniform vec3 DiskTint;
uniform vec3 RimColor;
// монолит: углы картинки на экране и ширина полосы
uniform vec2 Q0;
uniform vec2 Q1;
uniform vec2 Q2;
uniform vec2 Q3;
uniform float Band;

const float PI = 3.14159265;
const float BC = 5.19615242;
const float BMAX = 64.0;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.5 * noise(p);
    v += 0.25 * noise(p * 2.03 + 17.0);
    v += 0.125 * noise(p * 4.01 + 31.0);
    return v / 0.875;
}

vec2 rot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

vec2 toUv(vec2 d) {
    d.x /= Aspect;
    return clamp(Center + d, oneTexel, vec2(1.0) - oneTexel);
}

// --- таблицы: 16 бит в двух байтах ---------------------------------------------------------------

float dec(vec2 hl) {
    return (hl.x * 65280.0 + hl.y * 255.0) / 65535.0;
}

// полный угол отклонения луча с прицельным параметром b (b > b_c)
float deflection(float b) {
    if (b >= BMAX) {
        return 4.0 / b + 15.0 * PI / (4.0 * b * b);
    }
    float s = sqrt(max(b - BC, 0.0) / (BMAX - BC));
    float x = s * 1024.0 - 0.5;
    float i0 = clamp(floor(x), 0.0, 1023.0);
    float i1 = min(i0 + 1.0, 1023.0);
    float f = clamp(x - i0, 0.0, 1.0);
    float a0 = dec(texture2D(DeflSampler, vec2((i0 + 0.5) / 1024.0, 0.5)).rg);
    float a1 = dec(texture2D(DeflSampler, vec2((i1 + 0.5) / 1024.0, 0.5)).rg);
    return mix(a0, a1, f) * 4.0 * PI;
}

// радиусы пересечения с плоскостью диска при phi = phi_d и phi_d + pi; 0 — нет пересечения
vec2 crossings(float b, float phid) {
    if (b >= BMAX) {
        // слабое поле: u = sin(phi)/b + (1 - cos(phi))^2 / b^2
        float s1 = sin(phid);
        float u1 = s1 / b + (1.0 - cos(phid)) * (1.0 - cos(phid)) / (b * b);
        return vec2(u1 > 0.0001 ? 1.0 / u1 : 0.0, 0.0);
    }
    float t = b < BC ? 0.25 * b / BC : 0.25 + 0.75 * sqrt((b - BC) / (BMAX - BC));
    vec2 p = vec2(t * 512.0 - 0.5, phid / PI * 256.0 - 0.5);
    vec2 i0 = clamp(floor(p), vec2(0.0), vec2(511.0, 255.0));
    vec2 f = clamp(p - i0, 0.0, 1.0);
    vec2 i1 = min(i0 + 1.0, vec2(511.0, 255.0));
    vec4 a = texture2D(DiskSampler, (vec2(i0.x, i0.y) + 0.5) / vec2(512.0, 256.0));
    if (Quality < 0.5) {
        return vec2(dec(a.rg), dec(a.ba)) * BMAX;
    }
    vec4 b1 = texture2D(DiskSampler, (vec2(i1.x, i0.y) + 0.5) / vec2(512.0, 256.0));
    vec4 c = texture2D(DiskSampler, (vec2(i0.x, i1.y) + 0.5) / vec2(512.0, 256.0));
    vec4 e = texture2D(DiskSampler, (vec2(i1.x, i1.y) + 0.5) / vec2(512.0, 256.0));
    vec2 ra = vec2(dec(a.rg), dec(a.ba));
    vec2 rb = vec2(dec(b1.rg), dec(b1.ba));
    vec2 rc = vec2(dec(c.rg), dec(c.ba));
    vec2 re = vec2(dec(e.rg), dec(e.ba));
    vec2 lin = mix(mix(ra, rb, f.x), mix(rc, re, f.x), f.y);
    // на краю «есть / нет пересечения» интерполировать нельзя — берём ближайший
    vec2 near = f.x < 0.5 ? (f.y < 0.5 ? ra : rc) : (f.y < 0.5 ? rb : re);
    vec2 zero = step(vec2(0.00001), ra) * step(vec2(0.00001), rb) * step(vec2(0.00001), rc) * step(vec2(0.00001), re);
    return mix(near, lin, zero) * BMAX;
}

// --- излучение диска ---------------------------------------------------------------------------------

// цвет абсолютно чёрного тела (приближение Таннера Хелланда), T в кельвинах
vec3 blackbody(float T) {
    float t = clamp(T, 1000.0, 40000.0) / 100.0;
    float r = t <= 66.0 ? 1.0 : clamp(1.29293618 * pow(t - 60.0, -0.1332047592), 0.0, 1.0);
    float g = t <= 66.0 ? clamp(0.39008158 * log(t) - 0.63184144, 0.0, 1.0)
            : clamp(1.12989086 * pow(t - 60.0, -0.0755148492), 0.0, 1.0);
    float b = t >= 66.0 ? 1.0 : (t <= 19.0 ? 0.0 : clamp(0.54320679 * log(t - 10.0) - 1.19625409, 0.0, 1.0));
    return vec3(r, g, b);
}

// излучение точки диска: радиус r, направление на неё в системе дыры, прицельный параметр и ось экрана
vec4 diskEmission(float r, vec3 pos, float b, vec3 e) {
    float rin = DiskInner;
    if (r < rin || r > DiskOuter) {
        return vec4(0.0);
    }
    vec3 N = normalize(DiskNormal);
    // Шакура–Сюняев: T ~ x^-3/4 (1 - x^-1/2)^1/4, максимум 0.4884 при x = 49/36
    float x = r / rin;
    float prof = pow(x, -0.75) * pow(max(1.0 - inversesqrt(x), 0.0), 0.25) / 0.4884;
    // орбитальная скорость и доплер: 1+z = (1 + Omega*L_z) / sqrt(1 - 3/r)
    float omega = pow(r, -1.5);
    vec3 L = cross(vec3(0.0, 0.0, 1.0), e);
    float lz = DiskSpin * b * dot(L, N);
    float g = sqrt(max(1.0 - 3.0 / r, 0.0001)) / max(1.0 + omega * lz, 0.05);
    float T = DiskTemp * prof;
    float I = pow(prof, 4.0) * pow(g, 4.0);
    // турбулентность, вращающаяся по Кеплеру (у внутренних колец быстрее)
    vec3 U = normalize(abs(N.y) < 0.9 ? cross(N, vec3(0.0, 1.0, 0.0)) : cross(N, vec3(1.0, 0.0, 0.0)));
    vec3 V = cross(N, U);
    vec2 q = vec2(dot(pos, U), dot(pos, V));
    float ang = -DiskSpin * omega * Clock * 6.0;
    vec2 qr = rot(q, ang);
    float turb = Quality >= 1.5 ? fbm(qr * (3.2 / rin) + vec2(log(r) * 3.0, 0.0)) : noise(qr * (3.2 / rin));
    float streak = noise(vec2(length(q) * 2.4, 0.0) + qr * 0.15);
    I *= 0.45 + 0.9 * turb * (0.7 + 0.3 * streak);
    vec3 col = vec3(1.0) - exp(-blackbody(T * g) * DiskTint * I * DiskBright * (0.8 + 0.4 * Flash));
    // края диска мягкие, внутри почти непрозрачный
    float a = smoothstep(rin, rin * 1.08, r) * (1.0 - smoothstep(DiskOuter * 0.7, DiskOuter, r));
    // яркий газ непрозрачен, тусклые внешние кольца просвечивают
    a *= clamp(0.25 + 3.0 * I * DiskBright, 0.0, 1.0) * (0.75 + 0.25 * turb);
    return vec4(col, a);
}

// --- фон за дырой ------------------------------------------------------------------------------------

vec3 background(vec2 dir, float beta, float split) {
    float lim = 1.45;
    float bb = clamp(beta, -lim, lim);
    float fade = 1.0 - smoothstep(1.0, lim, abs(beta));
    vec2 p = dir * Sc * tan(bb);
    vec3 col;
    if (split > 0.0) {
        col.r = texture2D(DiffuseSampler, toUv(dir * Sc * tan(bb * (1.0 + split)))).r;
        col.g = texture2D(DiffuseSampler, toUv(p)).g;
        col.b = texture2D(DiffuseSampler, toUv(dir * Sc * tan(bb * (1.0 - split)))).b;
    } else {
        col = texture2D(DiffuseSampler, toUv(p)).rgb;
    }
    return col * mix(0.15, 1.0, fade);
}

// --- точный луч прямо в шейдере (Quality 3) -------------------------------------------------------

// возвращает: x — конечный угол phi (если улетел), y — 1 если упал, zw — радиусы 1-го и 2-го пересечения диска
vec4 integrate(float b, float phid) {
    float u0 = 1.0 / max(Dist, 3.2);
    float w2 = 1.0 / (b * b) - u0 * u0 * (1.0 - 2.0 * u0);
    float u = u0;
    float w = sqrt(max(w2, 0.0));
    float phi = 0.0;
    float h = 2.0 * PI * 1.7 / max(Steps, 16.0);
    vec2 hits = vec2(0.0);
    float target0 = phid;
    float target1 = phid + PI;
    float fell = 0.0;
    float end = PI;
    for (int i = 0; i < 512; i++) {
        if (float(i) >= Steps) {
            break;
        }
        float pu = u;
        float pphi = phi;
        // RK4 для (u, w)
        float k1u = w;
        float k1w = 3.0 * u * u - u;
        float u2 = u + 0.5 * h * k1u;
        float k2u = w + 0.5 * h * k1w;
        float k2w = 3.0 * u2 * u2 - u2;
        float u3 = u + 0.5 * h * k2u;
        float k3u = w + 0.5 * h * k2w;
        float k3w = 3.0 * u3 * u3 - u3;
        float u4 = u + h * k3u;
        float k4u = w + h * k3w;
        float k4w = 3.0 * u4 * u4 - u4;
        u += h / 6.0 * (k1u + 2.0 * k2u + 2.0 * k3u + k4u);
        w += h / 6.0 * (k1w + 2.0 * k2w + 2.0 * k3w + k4w);
        phi += h;
        if (pphi < target0 && phi >= target0 && hits.x == 0.0) {
            float uu = mix(pu, u, (target0 - pphi) / h);
            hits.x = uu > 0.0001 ? 1.0 / uu : 0.0;
        }
        if (pphi < target1 && phi >= target1 && hits.y == 0.0) {
            float uu = mix(pu, u, (target1 - pphi) / h);
            hits.y = uu > 0.0001 ? 1.0 / uu : 0.0;
        }
        if (u >= 0.5) {
            fell = 1.0;
            break;
        }
        if (u <= 0.0) {
            end = pphi + h * pu / max(pu - u, 0.000001);
            break;
        }
        end = phi;
    }
    return vec4(end, fell, hits);
}

void main() {
    vec4 base = texture2D(DiffuseSampler, texCoord);
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float r = length(d);
    float s = clamp(Strength, 0.0, 1.0);
    vec3 col = base.rgb;

    if (Mode > 2.5) {
        // монолит: расстояние до краёв картинки (четырёхугольник на экране)
        vec2 p = texCoord;
        vec2 qs[4];
        qs[0] = Q0; qs[1] = Q1; qs[2] = Q2; qs[3] = Q3;
        float md = 10.0;
        for (int i = 0; i < 4; i++) {
            vec2 a = qs[i];
            vec2 bq = qs[i == 3 ? 0 : i + 1];
            vec2 ab = bq - a;
            ab.x *= Aspect;
            vec2 ap = p - a;
            ap.x *= Aspect;
            float t = clamp(dot(ap, ab) / max(dot(ab, ab), 0.0000001), 0.0, 1.0);
            md = min(md, length(ap - ab * t));
        }
        float w = 1.0 - smoothstep(0.0, Band, md);
        if (w <= 0.001 || s <= 0.001) {
            gl_FragColor = vec4(col, 1.0);
            return;
        }
        float wave = sin(md / max(Band, 0.0001) * 18.0 - Clock * 7.0) * 0.5 + 0.5;
        vec2 off = vec2(noise(p * 40.0 + Clock * 1.7) - 0.5, noise(p * 40.0 - Clock * 1.3 + 9.0) - 0.5);
        vec2 uv = clamp(p + off * Band * 0.35 * w * s * (0.5 + wave), oneTexel, vec2(1.0) - oneTexel);
        col = texture2D(DiffuseSampler, uv).rgb;
        col += RimColor * pow(w, 6.0) * 0.35 * s * Flash;
        gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        return;
    }

    if (r >= Radius || s <= 0.001) {
        gl_FragColor = vec4(base.rgb, 1.0);
        return;
    }
    float edge = 1.0 - smoothstep(Radius * 0.55, Radius, r);

    if (Mode > 0.5 && Mode < 1.5) {
        // появление: игрок в центре не трогается, вокруг — отталкивающая линза и волна
        float core = Radius * 0.2;
        float w = smoothstep(core, core * 1.7, r) * edge;
        float near = 1.0 - r / Radius;
        float einstein = Radius * 0.3 * s;
        float k = -einstein * einstein / max(r * r, 0.000001) * w * 0.55;
        float swirl = -s * w * near * 1.3;
        vec2 dd = rot(d * (1.0 - k), swirl);
        col = texture2D(DiffuseSampler, toUv(dd)).rgb;
        float ripple = 0.5 + 0.5 * sin((r / Radius) * 26.0 - Clock * 18.85);
        col += vec3(0.55, 0.3, 1.0) * pow(ripple, 8.0) * w * s * 0.35 * Flash;
        float hd = (r - core * 1.35) / (core * 0.3 + 0.0005);
        col += vec3(0.3, 0.8, 1.0) * exp(-hd * hd) * s * 0.45 * Flash;
        col = mix(base.rgb, col, smoothstep(core * 0.85, core * 1.1, r));
        gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
        return;
    }

    // --- Mode 0 / 2: Шварцшильд ------------------------------------------------------------------
    vec2 dir = r > 0.0000001 ? d / r : vec2(1.0, 0.0);
    float theta = atan(r / Sc);
    // прицельный параметр в единицах M
    float b = theta / max(ThetaC, 0.000001) * BC;
    vec3 e = vec3(dir, 0.0);
    // угол phi_d, где луч пересекает плоскость диска (отсчёт от направления на камеру)
    vec3 N = normalize(DiskNormal);
    float phid = atan(-N.z, dot(e, N));
    if (phid < 0.0) {
        phid += PI;
    }
    if (phid < 0.0001) {
        phid = PI;
    }
    bool captured;
    float beta;
    vec2 hits;
    if (Quality > 2.5) {
        float bb = Dist * sin(theta) / sqrt(max(1.0 - 2.0 / max(Dist, 2.1), 0.01));
        vec4 tr = integrate(max(bb, 0.0001), phid);
        captured = tr.y > 0.5;
        beta = PI - tr.x;
        hits = tr.zw;
        b = bb;
    } else {
        captured = b < BC;
        beta = captured ? 0.0 : theta - deflection(b);
        hits = DiskOn > 0.5 ? crossings(b, phid) : vec2(0.0);
        if (Quality < 1.5) {
            hits.y = 0.0;
        }
    }
    // сила линзы (k) и плавное включение
    float k = LensK * s * edge;
    if (captured) {
        col = Mode > 1.5 ? base.rgb : vec3(0.0);
    } else {
        float lensed = theta + (beta - theta) * k;
        col = background(dir, lensed, Quality >= 1.5 ? 0.012 * k : 0.0);
        if (Mode > 1.5) {
            // у портала у самой тени — светящийся край цвета портала
            float rim = exp(-pow((b - BC) / 0.35, 2.0));
            col += RimColor * rim * 1.2 * s * Flash;
        }
    }
    if (DiskOn > 0.5) {
        // второй образ (за дырой, огибает её) — под первым
        if (hits.y > 0.0) {
            float ph = phid + PI;
            vec3 pos = hits.y * (cos(ph) * vec3(0.0, 0.0, 1.0) + sin(ph) * e);
            vec4 em = diskEmission(hits.y, pos, b, e);
            col = mix(col, em.rgb, em.a * s);
        }
        if (hits.x > 0.0) {
            vec3 pos = hits.x * (cos(phid) * vec3(0.0, 0.0, 1.0) + sin(phid) * e);
            vec4 em = diskEmission(hits.x, pos, b, e);
            col = mix(col, em.rgb, em.a * s);
        }
    }
    col = mix(base.rgb, col, smoothstep(0.0, 0.15, edge));
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
