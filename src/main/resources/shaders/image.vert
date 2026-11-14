#version 330 core

// A textured rectangle for the overlay phase: loading screen, HUD elements.
layout (location = 0) in vec2 aPosition; // -1..1, the whole screen
layout (location = 1) in vec2 aUv;

uniform vec3 uScale;  // x, y: size relative to the screen; z: opacity (fragment shader)
uniform vec3 uOffset; // x, y: position of the center in clip space
uniform vec3 uUvScale;  // x, y: the part of the texture that is shown, as a share of it
uniform vec3 uUvOffset; // x, y: where that part begins

out vec2 vUv;

void main() {
    vUv = aUv * uUvScale.xy + uUvOffset.xy;
    gl_Position = vec4(aPosition * uScale.xy + uOffset.xy, 0.0, 1.0);
}
