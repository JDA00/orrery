#version 410 core

in vec2 v_texCoord;
out vec4 fragColor;

uniform sampler2D u_sceneHDR;
uniform sampler2D u_bloom;
uniform float u_exposure;
uniform float u_bloomStrength;
uniform int u_toneMapOp;     // 0 = Reinhard, 1 = ACES (Narkowicz)
uniform float u_contrastLift;

vec3 reinhard(vec3 x) {
    return x / (1.0 + x);
}

vec3 acesNarkowicz(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 color = texture(u_sceneHDR, v_texCoord).rgb;
    if (u_bloomStrength > 0.0) {
        color += texture(u_bloom, v_texCoord).rgb * u_bloomStrength;
    }
    color *= u_exposure;
    vec3 mapped = (u_toneMapOp == 1) ? acesNarkowicz(color) : reinhard(color);
    mapped = pow(mapped, vec3(u_contrastLift));
    fragColor = vec4(mapped, 1.0);
}
