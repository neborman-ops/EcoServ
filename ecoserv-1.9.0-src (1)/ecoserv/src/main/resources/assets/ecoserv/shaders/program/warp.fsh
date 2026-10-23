#version 120

// EcoServ 1.8: full-screen effect of a "Black hole" portal (applied after the HUD — the interface warps too).
//  Warp  > 0 — approaching: hyperspace-like radial streaks, chromatic split, blue tint, speed lines.
//  Pinch > 0 — right next to it: everything is squeezed towards the hole (anomaly compression).
//  Pinch < 0 — just teleported: the picture bulges out and settles back.

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float Time;
uniform vec2 Center;
uniform float Aspect;
uniform float Warp;
uniform float Pinch;
uniform float Flash;

vec2 rot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

vec2 toUv(vec2 d) {
    d.x /= Aspect;
    return clamp(Center + d, oneTexel, vec2(1.0) - oneTexel);
}

void main() {
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float r = length(d);
    float w = clamp(Warp, 0.0, 1.0);
    float p = clamp(Pinch, -1.0, 1.0);

    // compression / inflation: a pixel shows the picture from a larger (or smaller) radius
    float fall = 1.0 - smoothstep(0.0, 1.3, r);
    float scale = max(0.12, 1.0 + p * fall * 1.7);
    vec2 src = d * scale;
    src = rot(src, w * 0.35 * exp(-r * 2.5) + p * 0.9 * exp(-r * 3.0));

    // radial streak blur with chromatic split
    vec3 col = vec3(0.0);
    float total = 0.0;
    float len = w * 0.16 * (0.25 + r) + abs(p) * 0.05;
    float ca = w * 0.012 + abs(p) * 0.012;
    for (int i = 0; i < 12; i++) {
        float t = float(i) / 11.0;
        vec2 s2 = src * (1.0 - len * t);
        float wt = 1.0 - t * 0.75;
        col.r += texture2D(DiffuseSampler, toUv(s2 * (1.0 + ca))).r * wt;
        col.g += texture2D(DiffuseSampler, toUv(s2)).g * wt;
        col.b += texture2D(DiffuseSampler, toUv(s2 * (1.0 - ca))).b * wt;
        total += wt;
    }
    col /= total;

    // tint and darkening towards the edges
    col = mix(col, col * vec3(0.75, 0.82, 1.18), w * 0.55);
    col *= 1.0 - (w * 0.35 + max(p, 0.0) * 0.65) * smoothstep(0.3, 1.0, r);
    // speed lines
    float ang = atan(d.y, d.x);
    float lines = pow(abs(sin(ang * 37.0 + Time * 2.0)), 70.0) * w * smoothstep(0.25, 0.95, r);
    col += vec3(0.7, 0.82, 1.0) * lines * 0.35 * Flash;
    // flash of the release
    col += vec3(0.6, 0.5, 1.0) * max(-p, 0.0) * 0.55 * Flash * exp(-r * 2.5);
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
