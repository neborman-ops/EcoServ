#version 120

// EcoServ: space distortion around a player falling into the GLITCH abyss.
// Point-mass gravitational lens: the pixel at offset d shows the scene from d * (1 - thetaE^2 / |d|^2).
// Dark event horizon in the middle, a purple photon ring and a chromatic split near the ring.

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float Time;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Aspect;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 lens(vec2 d, float k, float swirl) {
    vec2 src = d * (1.0 - k);
    float ca = cos(swirl);
    float sa = sin(swirl);
    src = vec2(src.x * ca - src.y * sa, src.x * sa + src.y * ca);
    src.x /= Aspect;
    return clamp(Center + src, oneTexel, vec2(1.0) - oneTexel);
}

void main() {
    vec4 base = texture2D(DiffuseSampler, texCoord);
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float r = length(d);
    float s = clamp(Strength, 0.0, 1.0);
    if (r >= Radius || s <= 0.001) {
        gl_FragColor = vec4(base.rgb, 1.0);
        return;
    }

    float einstein = Radius * 0.34 * s;
    float horizon = einstein * 0.58;
    // fade the deflection out towards the edge so there is no visible seam
    float edge = 1.0 - smoothstep(Radius * 0.45, Radius, r);
    float k = einstein * einstein / max(r * r, 0.000001) * edge;
    // frame dragging: a slow swirl, stronger near the centre
    float near = 1.0 - r / Radius;
    float swirl = s * near * near * (1.6 + 0.4 * sin(Time * 6.2831853));

    // glitch: rare horizontal tears inside the funnel
    float band = floor(texCoord.y * 90.0);
    float n = rand(vec2(band, floor(Time * 20.0)));
    vec2 jitter = vec2(0.0);
    if (n > 0.9) {
        jitter.x = (n - 0.9) * 0.6 * s * edge;
    }

    // slightly different strength per channel gives the rainbow edge
    float cr = texture2D(DiffuseSampler, lens(d + jitter, k * 0.97, swirl)).r;
    float cg = texture2D(DiffuseSampler, lens(d + jitter, k, swirl)).g;
    float cb = texture2D(DiffuseSampler, lens(d + jitter, k * 1.05, swirl)).b;
    vec3 col = vec3(cr, cg, cb);

    // photon ring
    float ringW = max(einstein * 0.14, 0.002);
    float rd = (r - einstein * 0.92) / ringW;
    float ring = exp(-rd * rd);
    float flicker = 0.75 + 0.25 * sin(Time * 6.2831853 * 3.0 + r * 80.0);
    col += vec3(0.72, 0.28, 1.0) * ring * s * flicker;
    // faint cyan halo outside the ring
    float hd = (r - einstein * 1.35) / (ringW * 2.5);
    col += vec3(0.15, 0.85, 0.9) * exp(-hd * hd) * 0.25 * s;

    // event horizon
    float hole = 1.0 - smoothstep(horizon * 0.82, horizon, r);
    col = mix(col, vec3(0.0), hole);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
