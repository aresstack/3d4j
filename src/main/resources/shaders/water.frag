#version 330 core

in vec2 vCorner;
in vec3 vStyle;
in float vDistance;

uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 fragColor;

void main() {
    float radius = length(vCorner);
    vec3 color;
    if (vStyle.x < 0.5) {
        // A round drop with a darker rim, as drawn with a pen.
        if (radius > 1.0) {
            discard;
        }
        color = radius > 0.68 ? vec3(0.33, 0.62, 0.86) : vec3(0.86, 0.96, 1.0) * vStyle.y;
    } else {
        // A ring that gets thinner while it spreads, until it is gone.
        float width = 0.22 * (1.0 - vStyle.z);
        if (radius > 1.0 || radius < 1.0 - width) {
            discard;
        }
        color = vec3(0.93, 0.99, 1.0);
    }
    float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fragColor = vec4(mix(color, uFogColor, fog), 1.0);
}
