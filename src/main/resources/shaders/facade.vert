#version 330 core

// House walls: one rectangle per wall, carrying a grid of building parts.
layout (location = 0) in vec3 aPosition;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec3 aWall; // colour of the wall itself
layout (location = 3) in vec3 aFar;  // colour of wall and parts mixed, for the distance
layout (location = 4) in vec3 aGrid; // x: column, y: row (fractions inside a part), z: cell of the atlas

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;

out vec3 vNormal;
out vec3 vWall;
out vec3 vFar;
out vec3 vGrid;
out float vDistance;

void main() {
    vNormal = aNormal;
    vWall = aWall;
    vFar = aFar;
    vGrid = aGrid;
    vDistance = distance(aPosition, uCameraPosition);
    gl_Position = uViewProjection * vec4(aPosition, 1.0);
}
