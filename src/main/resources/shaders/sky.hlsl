// Direct3D version of sky.vert / sky.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
};

struct VertexInput {
    float3 position : ATTRIBUTE0;
    float3 color : ATTRIBUTE1;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 color : COLOR;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float4 clip = mul(uViewProjection, float4(input.position, 1.0));
    clip.z = clip.w * 0.99995; // as far away as the depth buffer allows
    output.position = clip;
    output.color = input.color;
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    return float4(input.color, 1.0);
}
