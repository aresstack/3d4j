#version 330 core

in vec3 vNormal;
in vec3 vColor;
in float vDistance;

uniform vec3 uLightDirection; // direction in which the light travels
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 fragColor;

void main() {
    // Comic look: three flat brightness steps instead of a smooth gradient.
    float diffuse = max(dot(normalize(vNormal), -normalize(uLightDirection)), 0.0);
    float shade = diffuse > 0.55 ? 1.0 : (diffuse > 0.15 ? 0.82 : 0.64);
    float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fragColor = vec4(mix(vColor * shade, uFogColor, fog), 1.0);
}
