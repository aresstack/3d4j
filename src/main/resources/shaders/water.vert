#version 330 core

// Water of the fountain: drops that fly in an arc and rings that spread on the
// surface. Every drop repeats its flight; where it is at a moment follows from
// the time alone, so nothing is computed on the CPU per frame.
layout (location = 0) in vec3 aOrigin;   // where the drop starts, or the middle of the ring
layout (location = 1) in vec2 aCorner;   // this corner of the quad, -1..1
layout (location = 2) in vec3 aVelocity; // speed of the drop at the start
layout (location = 3) in vec3 aTiming;   // x: phase 0..1, y: duration of one flight in seconds, z: size
layout (location = 4) in vec3 aStyle;    // x: 0 drop, 1 ring; y: brightness

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform vec3 uCameraRight;
uniform float uTime;

out vec2 vCorner;
out vec3 vStyle; // x: kind, y: brightness, z: progress 0..1
out float vDistance;

void main() {
    float progress = fract(uTime / aTiming.y + aTiming.x);
    vec3 world;
    if (aStyle.x < 0.5) {
        float t = progress * aTiming.y;
        vec3 center = aOrigin + aVelocity * t + vec3(0.0, -4.9, 0.0) * t * t;
        // Drops start small, as if they left a nozzle.
        float size = aTiming.z * (0.55 + 0.45 * min(progress * 4.0, 1.0));
        world = center + uCameraRight * (aCorner.x * size) + vec3(0.0, aCorner.y * size, 0.0);
    } else {
        float size = aTiming.z * (0.25 + progress);
        world = aOrigin + vec3(aCorner.x, 0.0, aCorner.y) * size;
    }
    vCorner = aCorner;
    vStyle = vec3(aStyle.x, aStyle.y, progress);
    vDistance = distance(world, uCameraPosition);
    gl_Position = uViewProjection * vec4(world, 1.0);
}
