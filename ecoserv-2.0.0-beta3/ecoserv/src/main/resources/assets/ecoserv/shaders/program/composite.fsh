#version 120

// EcoServ 2.0 beta3: сборка — свечение (Bloom), размытие (Blur) и глубина резкости (DepthOfField).
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DepthSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec4 ProjParams;
uniform float FarDist;
uniform float BloomAmt;
uniform float BlurPx;
uniform vec4 Dof;
uniform float DofOn;

float viewDist(vec2 uv) {
    float d = texture2D(DepthSampler, uv).r;
    if (d >= 0.99999) {
        return FarDist;
    }
    float zn = d * 2.0 - 1.0;
    return ProjParams.w / (zn + ProjParams.z);
}

vec3 disk(vec2 uv, float r) {
    vec3 acc = texture2D(DiffuseSampler, uv).rgb;
    float n = 1.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5236;
        vec2 o = vec2(cos(a), sin(a)) * oneTexel * r;
        acc += texture2D(DiffuseSampler, uv + o).rgb;
        acc += texture2D(DiffuseSampler, uv + o * 0.5).rgb;
        n += 2.0;
    }
    return acc / n;
}

void main() {
    float r = BlurPx;
    if (DofOn > 0.5) {
        float dist = abs(viewDist(texCoord));
        float focus = Dof.y * FarDist;
        float diff = dist - focus;
        float coc = 0.0;
        if (abs(diff) > Dof.z) {
            float k = clamp((abs(diff) - Dof.z) / max(Dof.z + 8.0, 8.0), 0.0, 1.0);
            coc = k * (diff > 0.0 ? Dof.x : Dof.w);
        }
        r = max(r, coc * 7.0);
    }
    vec3 color = r > 0.5 ? disk(texCoord, r) : texture2D(DiffuseSampler, texCoord).rgb;
    color += texture2D(BloomSampler, texCoord).rgb * BloomAmt;
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
