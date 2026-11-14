// Direct3D version of ground.vert / ground.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
    float3 uCameraPosition;
    float3 uAtlas;
    float3 uDetail;
    float3 uFogColor;
    float uFogStart;
    float uFogEnd;
};

Texture2D uTexture : register(t0);
SamplerState uSampler : register(s0);

struct VertexInput {
    float3 position : ATTRIBUTE0;
    float3 tile : ATTRIBUTE1;
    float3 average : ATTRIBUTE2;
};

struct VertexOutput {
    float4 position : SV_Position;
    float2 world : TEXCOORD0;
    float3 tile : TEXCOORD1;
    float3 average : TEXCOORD2;
    float viewDistance : TEXCOORD3;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float4 clip = mul(uViewProjection, float4(input.position, 1.0));
    clip.z = (clip.z + clip.w) * 0.5; // OpenGL depth range -1..1 to Direct3D 0..1
    output.position = clip;
    output.world = input.position.xz;
    output.tile = input.tile;
    output.average = input.average;
    output.viewDistance = distance(input.position, uCameraPosition);
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float2 inTile = frac(input.world / uDetail.x);
    float2 uv = float2((input.tile.x + uAtlas.y + inTile.x * uAtlas.z) * uAtlas.x, uAtlas.y + inTile.y * uAtlas.z);
    float3 detail = uTexture.Sample(uSampler, uv).rgb;
    float flatness = clamp((input.viewDistance - uDetail.y) / (uDetail.z - uDetail.y), 0.0, 1.0);
    float3 color = lerp(detail, input.average, flatness) * input.tile.y;
    float fog = clamp((input.viewDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    return float4(lerp(color, uFogColor, fog), 1.0);
}
