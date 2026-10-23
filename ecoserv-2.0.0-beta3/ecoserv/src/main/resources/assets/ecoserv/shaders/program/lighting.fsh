#version 120

// EcoServ 2.0 beta3: освещение в стиле Roblox — цветные источники (Point/Spot/Surface), атмосфера,
// солнечные лучи, экспозиция и цветокоррекция. Глубина — из DepthSampler.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec4 ProjParams;
uniform float FarDist;
uniform vec4 Grade;
uniform vec3 Tint;
uniform vec4 Atm;
uniform vec3 AtmColor;
uniform vec3 AtmDecay;
uniform float AtmOn;
uniform vec4 Sun;
uniform vec3 SunView;
uniform vec2 Rays;
uniform vec2 Spec;

uniform vec4 LightPos0; uniform vec4 LightCol0; uniform vec4 LightDir0;
uniform vec4 LightPos1; uniform vec4 LightCol1; uniform vec4 LightDir1;
uniform vec4 LightPos2; uniform vec4 LightCol2; uniform vec4 LightDir2;
uniform vec4 LightPos3; uniform vec4 LightCol3; uniform vec4 LightDir3;
uniform vec4 LightPos4; uniform vec4 LightCol4; uniform vec4 LightDir4;
uniform vec4 LightPos5; uniform vec4 LightCol5; uniform vec4 LightDir5;
uniform vec4 LightPos6; uniform vec4 LightCol6; uniform vec4 LightDir6;
uniform vec4 LightPos7; uniform vec4 LightCol7; uniform vec4 LightDir7;

vec3 viewPos(vec2 uv, float d) {
    float zn = d * 2.0 - 1.0;
    float z = -ProjParams.w / (zn + ProjParams.z);
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc.x * -z / ProjParams.x, ndc.y * -z / ProjParams.y, z);
}

vec3 lightAt(vec3 p, vec4 lp, vec4 lc, vec4 ld) {
    if (lp.w <= 0.0) {
        return vec3(0.0);
    }
    vec3 l = lp.xyz - p;
    float dist = length(l);
    float att = clamp(1.0 - dist / lp.w, 0.0, 1.0);
    att *= att;
    if (lc.w > 0.5) {
        float c = dot(normalize(-l), ld.xyz);
        att *= smoothstep(ld.w, ld.w + 0.08, c);
    }
    return lc.rgb * att;
}

void main() {
    vec3 color = texture2D(DiffuseSampler, texCoord).rgb;
    float d = texture2D(DepthSampler, texCoord).r;
    bool sky = d >= 0.99999;
    vec3 p = viewPos(texCoord, d);
    float dist = sky ? FarDist : length(p);

    if (!sky) {
        vec3 li = lightAt(p, LightPos0, LightCol0, LightDir0) + lightAt(p, LightPos1, LightCol1, LightDir1)
                + lightAt(p, LightPos2, LightCol2, LightDir2) + lightAt(p, LightPos3, LightCol3, LightDir3)
                + lightAt(p, LightPos4, LightCol4, LightDir4) + lightAt(p, LightPos5, LightCol5, LightDir5)
                + lightAt(p, LightPos6, LightCol6, LightDir6) + lightAt(p, LightPos7, LightCol7, LightDir7);
        color = color + color * li * 1.2 + li * 0.06;
    }

    vec3 dir = normalize(p);
    float toSun = max(dot(dir, SunView), 0.0);
    if (AtmOn > 0.5) {
        float start = Atm.y * FarDist * 0.6;
        float f = 1.0 - exp(-max(dist - start, 0.0) / max(FarDist, 1.0) * Atm.x * 4.0);
        f = clamp(f * (1.0 + Atm.w * 0.08), 0.0, 1.0);
        vec3 haze = mix(AtmDecay, AtmColor, pow(toSun, 4.0));
        color = mix(color, haze, sky ? f * 0.35 : f);
        color += AtmColor * Atm.z * 0.04 * pow(toSun, 24.0) * (sky ? 1.0 : f);
    }

    if (Sun.z > 0.5 && Rays.y > 0.5) {
        vec2 delta = (Sun.xy - texCoord) / 24.0 * (0.4 + Rays.x * 0.6);
        vec2 uv = texCoord;
        float acc = 0.0;
        float w = 1.0;
        for (int i = 0; i < 24; i++) {
            uv += delta;
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
                break;
            }
            float sd = texture2D(DepthSampler, uv).r;
            acc += (sd >= 0.99999 ? 1.0 : 0.0) * w;
            w *= 0.94;
        }
        float fall = clamp(1.0 - length((texCoord - Sun.xy) * vec2(1.6, 1.0)) * 1.2, 0.0, 1.0);
        color += vec3(1.0, 0.93, 0.8) * acc / 24.0 * Sun.w * fall * 1.6;
    }

    color *= Grade.x;
    color += Grade.y;
    color = (color - 0.5) * (1.0 + Grade.z) + 0.5;
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(lum), color, 1.0 + Grade.w);
    color *= Tint;
    color += max(color - 0.8, 0.0) * Spec.x;

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
