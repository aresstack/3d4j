#version 330 core

// A textured rectangle for the overlay phase: loading screen, HUD elements.
layout (location = 0) in vec2 aPosition; // -1..1, the whole screen
layout (location = 1) in vec2 aUv;

uniform vec3 uScale;  // x, y: size relative to the screen
uniform vec3 uOffset; // x, y: position of the center in clip space

out vec2 vUv;

void main() {
    vUv = aUv;
    gl_Position = vec4(aPosition * uScale.xy + uOffset.xy, 0.0, 1.0);
}
