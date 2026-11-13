#version 330 core

in vec2 vUv;
in float vDistance;

uniform sampler2D uTexture;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 fragColor;

void main() {
    vec4 color = texture(uTexture, vUv);
    // Cut-out instead of blending: no sorting needed, and the depth buffer keeps working.
    if (color.a < 0.5) {
        discard;
    }
    float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, uFogColor, fog), 1.0);
}
