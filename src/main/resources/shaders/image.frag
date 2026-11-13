#version 330 core

in vec2 vUv;

uniform sampler2D uTexture;
uniform vec3 uTint;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(uTexture, vUv).rgb * uTint, 1.0);
}
