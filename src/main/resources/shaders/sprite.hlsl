// Direct3D version of sprite.vert / sprite.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
    float3 uCameraPosition;
    float3 uCameraRight;
    float3 uFogColor;
    float uFogStart;
    float uFogEnd;
    float uTime;
};

Texture2D uTexture : register(t0);
SamplerState uSampler : register(s0);

struct VertexInput {
    float3 center : ATTRIBUTE0;
    float2 corner : ATTRIBUTE1;
    float2 uv : ATTRIBUTE2;
    float3 motion : ATTRIBUTE3;
};

struct VertexOutput {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
    float viewDistance : TEXCOORD1;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float angle = uTime * input.motion.y + input.motion.z;
    float3 center = input.center + float3(cos(angle), 0.0, sin(angle)) * input.motion.x;
    float3 world = center + uCameraRight * input.corner.x + float3(0.0, input.corner.y, 0.0);
    float4 clip = mul(uViewProjection, float4(world, 1.0));
    clip.z = (clip.z + clip.w) * 0.5; // OpenGL depth range -1..1 to Direct3D 0..1
    output.position = clip;
    output.uv = input.uv;
    output.viewDistance = distance(world, uCameraPosition);
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float4 color = uTexture.Sample(uSampler, input.uv);
    clip(color.a - 0.5); // cut-out instead of blending
    float fog = clamp((input.viewDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    return float4(lerp(color.rgb, uFogColor, fog), 1.0);
}
