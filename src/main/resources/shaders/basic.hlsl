// Direct3D version of basic.vert / basic.frag.

// Must list the uniforms in the order of the ShaderLayout in Java.
cbuffer Uniforms : register(b0) {
    float4x4 uModel;
    float4x4 uView;
    float4x4 uProjection;
    float3 uLightDirection; // direction in which the light travels, in world space
};

// ATTRIBUTEi is vertex attribute i of the mesh: position, normal, color.
struct VertexInput {
    float3 position : ATTRIBUTE0;
    float3 normal : ATTRIBUTE1;
    float3 color : ATTRIBUTE2;
};

struct VertexOutput {
    float4 position : SV_Position;
    float3 normal : NORMAL;
    float3 color : COLOR;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float4 world = mul(uModel, float4(input.position, 1.0));
    float4 clip = mul(uProjection, mul(uView, world));
    // The matrices follow OpenGL, where depth runs from -1 to 1. Direct3D expects 0 to 1.
    clip.z = (clip.z + clip.w) * 0.5;
    output.position = clip;
    // Correct as long as the model matrix has no non-uniform scaling.
    output.normal = mul(uModel, float4(input.normal, 0.0)).xyz;
    output.color = input.color;
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float diffuse = max(dot(normalize(input.normal), -normalize(uLightDirection)), 0.0);
    float ambient = 0.25;
    return float4(input.color * (ambient + (1.0 - ambient) * diffuse), 1.0);
}
