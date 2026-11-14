#version 330 core

in vec2 vWorld;
in vec3 vTile;
in vec3 vAverage;
in float vDistance;

uniform sampler2D uTexture;
uniform vec3 uAtlas;  // x: width of one cell, y: gutter, z: width of the tile inside the cell (all 0..1 of a cell)
uniform vec3 uDetail; // x: edge length of a tile in the world, y: distance where the detail starts to fade, z: where it is gone
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 fragColor;

void main() {
    // The tile repeats every uDetail.x units. Each atlas cell has the tile's own
    // edges repeated around it (gutter), so filtering never reaches a neighbor.
    vec2 inTile = fract(vWorld / uDetail.x);
    vec2 uv = vec2((vTile.x + uAtlas.y + inTile.x * uAtlas.z) * uAtlas.x, uAtlas.y + inTile.y * uAtlas.z);
    vec3 detail = texture(uTexture, uv).rgb;
    // Far away the pattern is finer than the pixels and would flicker (there are no
    // mipmaps): fade to the average colour of the tile.
    float flatness = clamp((vDistance - uDetail.y) / (uDetail.z - uDetail.y), 0.0, 1.0);
    vec3 color = mix(detail, vAverage, flatness) * vTile.y;
    float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fragColor = vec4(mix(color, uFogColor, fog), 1.0);
}
