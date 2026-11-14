// Direct3D version of facade.vert / facade.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
    float3 uCameraPosition;
    float3 uAtlas;
    float3 uDetail;
    float3 uLightDirection;
    float3 uFogColor;
    float uFogStart;
    float uFogEnd;
};

Texture2D uTexture : register(t0);
SamplerState uSampler : register(s0);

struct VertexInput {
    float3 position : ATTRIBUTE0;
    float3 normal : ATTRIBUTE1;
    float3 wall : ATTRIBUTE2;
    float3 farColor : ATTRIBUTE3;
    float3 grid : ATTRIBUTE4;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 normal : NORMAL;
    float3 wall : COLOR0;
    float3 farColor : COLOR1;
    float3 grid : TEXCOORD0;
    float viewDistance : TEXCOORD1;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float4 clip = mul(uViewProjection, float4(input.position, 1.0));
    clip.z = (clip.z + clip.w) * 0.5; // OpenGL depth range -1..1 to Direct3D 0..1
    output.position = clip;
    output.normal = input.normal;
    output.wall = input.wall;
    output.farColor = input.farColor;
    output.grid = input.grid;
    output.viewDistance = distance(input.position, uCameraPosition);
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float2 inPart = frac(input.grid.xy);
    float2 uv = float2((input.grid.z + uAtlas.y + inPart.x * uAtlas.z) * uAtlas.x, uAtlas.y + inPart.y * uAtlas.z);
    float4 part = uTexture.Sample(uSampler, uv);
    float3 color = lerp(input.wall, part.rgb, part.a);
    float flatness = clamp((input.viewDistance - uDetail.y) / (uDetail.z - uDetail.y), 0.0, 1.0);
    color = lerp(color, input.farColor, flatness);

    float diffuse = max(dot(normalize(input.normal), -normalize(uLightDirection)), 0.0);
    float shade = diffuse > 0.55 ? 1.0 : (diffuse > 0.15 ? 0.82 : 0.64);
    float fog = clamp((input.viewDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    return float4(lerp(color * shade, uFogColor, fog), 1.0);
}
