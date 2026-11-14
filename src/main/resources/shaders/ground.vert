#version 330 core

// The ground: flat pieces at y = 0, textured by where they lie in the world.
layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aTile;    // x: cell of the texture atlas, y: brightness of this piece
layout (location = 2) in vec3 aAverage; // average colour of the texture, for the distance

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;

out vec2 vWorld;
out vec3 vTile;
out vec3 vAverage;
out float vDistance;

void main() {
    vWorld = aPosition.xz;
    vTile = aTile;
    vAverage = aAverage;
    vDistance = distance(aPosition, uCameraPosition);
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
}
