// Direct3D version of scene.vert / scene.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
    float3 uCameraPosition;
    float3 uLightDirection;
    float3 uFogColor;
    float uFogStart;
    float uFogEnd;
};

struct VertexInput {
    float3 position : ATTRIBUTE0;
    float3 normal : ATTRIBUTE1;
    float3 color : ATTRIBUTE2;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 normal : NORMAL;
    float3 color : COLOR;
    float viewDistance : TEXCOORD0;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float4 clip = mul(uViewProjection, float4(input.position, 1.0));
    clip.z = (clip.z + clip.w) * 0.5; // OpenGL depth range -1..1 to Direct3D 0..1
    output.position = clip;
    output.normal = input.normal;
    output.color = input.color;
    output.viewDistance = distance(input.position, uCameraPosition);
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float diffuse = max(dot(normalize(input.normal), -normalize(uLightDirection)), 0.0);
    float shade = diffuse > 0.55 ? 1.0 : (diffuse > 0.15 ? 0.82 : 0.64);
    float fog = clamp((input.viewDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    return float4(lerp(input.color * shade, uFogColor, fog), 1.0);
}
