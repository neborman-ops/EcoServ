#version 120

// EcoServ 2.0 beta3: яркие места для свечения (Bloom).
uniform sampler2D DiffuseSampler;
varying vec2 texCoord;
uniform float Thresh;

void main() {
    vec3 c = texture2D(DiffuseSampler, texCoord).rgb;
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    float k = smoothstep(Thresh, Thresh + 0.2, lum);
    gl_FragColor = vec4(c * k, 1.0);
}
