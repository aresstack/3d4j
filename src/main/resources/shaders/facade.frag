#version 330 core

in vec3 vNormal;
in vec3 vWall;
in vec3 vFar;
in vec3 vGrid;
in float vDistance;

uniform sampler2D uTexture;
uniform vec3 uAtlas;  // x: width of one cell, y: gutter, z: width of the tile inside the cell (y, z as parts of a cell)
uniform vec3 uDetail; // y: distance where the parts start to fade into the wall, z: where they are gone
uniform vec3 uLightDirection;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 fragColor;

void main() {
    // The part repeats once per column and row; where it is transparent, the wall shows.
    vec2 inPart = fract(vGrid.xy);
    vec2 uv = vec2((vGrid.z + uAtlas.y + inPart.x * uAtlas.z) * uAtlas.x, uAtlas.y + inPart.y * uAtlas.z);
    vec4 part = texture(uTexture, uv);
    vec3 color = mix(vWall, part.rgb, part.a);
    // Far away the parts are smaller than the pixels and would flicker: fade to the mixed colour.
    float flatness = clamp((vDistance - uDetail.y) / (uDetail.z - uDetail.y), 0.0, 1.0);
    color = mix(color, vFar, flatness);

    float diffuse = max(dot(normalize(vNormal), -normalize(uLightDirection)), 0.0);
    float shade = diffuse > 0.55 ? 1.0 : (diffuse > 0.15 ? 0.82 : 0.64);
    float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fragColor = vec4(mix(color * shade, uFogColor, fog), 1.0);
}
