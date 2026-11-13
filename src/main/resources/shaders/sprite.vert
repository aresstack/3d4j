#version 330 core

// A sprite is a flat picture that always turns towards the camera.
layout (location = 0) in vec3 aCenter;  // foot point of the sprite
layout (location = 1) in vec2 aCorner;  // this corner's offset from the foot point: sideways, up
layout (location = 2) in vec2 aUv;
layout (location = 3) in vec3 aMotion;  // strolls in a circle: radius, speed, phase

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform vec3 uCameraRight;
uniform float uTime;

out vec2 vUv;
out float vDistance;

void main() {
    float angle = uTime * aMotion.y + aMotion.z;
    vec3 center = aCenter + vec3(cos(angle), 0.0, sin(angle)) * aMotion.x;
    vec3 world = center + uCameraRight * aCorner.x + vec3(0.0, aCorner.y, 0.0);
    vUv = aUv;
    vDistance = distance(world, uCameraPosition);
    gl_Position = uViewProjection * vec4(world, 1.0);
}
