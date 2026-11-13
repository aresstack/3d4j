#version 330 core

in vec2 vUv;

uniform sampler2D uTexture;
uniform vec3 uTint;
uniform vec3 uScale; // z: opacity (x and y belong to the vertex shader)

out vec4 fragColor;

void main() {
    vec4 texel = texture(uTexture, vUv);
    fragColor = vec4(texel.rgb * uTint, texel.a * uScale.z);
}
