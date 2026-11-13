#version 330 core

in vec3 vNormal;
in vec3 vColor;

// Direction in which the light travels, in world space.
uniform vec3 uLightDirection;

out vec4 fragColor;

void main() {
    float diffuse = max(dot(normalize(vNormal), -normalize(uLightDirection)), 0.0);
    float ambient = 0.25;
    fragColor = vec4(vColor * (ambient + (1.0 - ambient) * diffuse), 1.0);
}
