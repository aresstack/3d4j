#version 330 core

layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aColor;

// View rotation times projection: the sky turns with the camera but never comes closer.
uniform mat4 uViewProjection;

out vec3 vColor;

void main() {
    vColor = aColor;
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
    gl_Position.z = gl_Position.w * 0.9999; // as far away as the depth buffer allows
}
