#version 120

// EcoServ: full-screen picture glitch for a player falling into the GLITCH abyss.

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float Time;
uniform float Strength;
uniform float Seed;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float s = clamp(Strength, 0.0, 1.0);
    vec2 uv = texCoord;

    // torn rows: bands shift sideways
    float rows = 24.0 + floor(Seed * 40.0);
    float row = floor(uv.y * rows);
    float n = rand(vec2(row, floor(Seed * 97.0)));
    if (n > 1.0 - 0.35 * s) {
        uv.x += (rand(vec2(row, Seed)) - 0.5) * 0.18 * s;
    }

    // pixelated random rectangles
    vec2 block = floor(uv * vec2(16.0, 9.0));
    float bn = rand(block + vec2(floor(Seed * 13.0), 0.0));
    if (bn > 1.0 - 0.12 * s) {
        float px = 24.0 + 64.0 * rand(block);
        uv = (floor(uv * px) + 0.5) / px;
    }

    // RGB split
    float shift = 0.012 * s * (0.5 + rand(vec2(Seed, row)));
    vec2 lo = oneTexel;
    vec2 hi = vec2(1.0) - oneTexel;
    float cr = texture2D(DiffuseSampler, clamp(uv + vec2(shift, 0.0), lo, hi)).r;
    float cg = texture2D(DiffuseSampler, clamp(uv, lo, hi)).g;
    float cb = texture2D(DiffuseSampler, clamp(uv - vec2(shift, 0.0), lo, hi)).b;
    vec3 col = vec3(cr, cg, cb);

    // scanlines
    float line = fract(texCoord.y / oneTexel.y * 0.5);
    col *= 1.0 - 0.18 * s * step(0.5, line);

    // purple tint and noise
    col = mix(col, col * vec3(1.1, 0.75, 1.25), 0.35 * s);
    col += (rand(texCoord * 512.0 + vec2(Seed, Time)) - 0.5) * 0.12 * s;

    // now and then an inverted block
    if (bn > 1.0 - 0.03 * s) {
        col = vec3(1.0) - col;
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
