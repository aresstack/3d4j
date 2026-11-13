// Direct3D version of image.vert / image.frag.

cbuffer Uniforms : register(b0) {
    float3 uScale;
    float3 uOffset;
    float3 uTint;
};

Texture2D uTexture : register(t0);
SamplerState uSampler : register(s0);

struct VertexInput {
    float2 position : ATTRIBUTE0;
    float2 uv : ATTRIBUTE1;
};

struct VertexOutput {
    float4 position : SV_Position;
    float2 uv : TEXCOORD0;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    output.position = float4(input.position * uScale.xy + uOffset.xy, 0.0, 1.0);
    output.uv = input.uv;
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    // uScale.z is the opacity; it counts in the overlay, where the device blends.
    float4 texel = uTexture.Sample(uSampler, input.uv);
    return float4(texel.rgb * uTint, texel.a * uScale.z);
}
