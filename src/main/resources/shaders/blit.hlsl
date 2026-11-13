// Used by the Direct3D backend itself: stretches the scene image over the window.
// (OpenGL does this with glBlitFramebuffer and needs no shader.)

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
    output.position = float4(input.position, 0.0, 1.0);
    output.uv = input.uv;
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    return float4(uTexture.Sample(uSampler, input.uv).rgb, 1.0);
}
