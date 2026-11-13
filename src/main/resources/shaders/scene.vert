#version 330 core

// Static world geometry: position, normal, color. Positions are already in world space.
layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec3 aColor;

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;

out vec3 vNormal;
out vec3 vColor;
out float vDistance;

void main() {
    vNormal = aNormal;
    vColor = aColor;
    vDistance = distance(aPosition, uCameraPosition);
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
}
