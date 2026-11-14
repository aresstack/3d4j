// Direct3D version of water.vert / water.frag.

cbuffer Uniforms : register(b0) {
    float4x4 uViewProjection;
    float3 uCameraPosition;
    float3 uCameraRight;
    float3 uFogColor;
    float uFogStart;
    float uFogEnd;
    float uTime;
};

struct VertexInput {
    float3 origin : ATTRIBUTE0;
    float2 corner : ATTRIBUTE1;
    float3 velocity : ATTRIBUTE2;
    float3 timing : ATTRIBUTE3;
    float3 style : ATTRIBUTE4;
};

struct VertexOutput {
    float4 position : SV_Position;
    float2 corner : TEXCOORD0;
    float3 style : TEXCOORD1;
    float viewDistance : TEXCOORD2;
};

VertexOutput vertexMain(VertexInput input) {
    VertexOutput output;
    float progress = frac(uTime / input.timing.y + input.timing.x);
    float3 world;
    if (input.style.x < 0.5) {
        float t = progress * input.timing.y;
        float3 center = input.origin + input.velocity * t + float3(0.0, -4.9, 0.0) * t * t;
        float size = input.timing.z * (0.55 + 0.45 * min(progress * 4.0, 1.0));
        world = center + uCameraRight * (input.corner.x * size) + float3(0.0, input.corner.y * size, 0.0);
    } else {
        float size = input.timing.z * (0.25 + progress);
        world = input.origin + float3(input.corner.x, 0.0, input.corner.y) * size;
    }
    float4 clip = mul(uViewProjection, float4(world, 1.0));
    clip.z = (clip.z + clip.w) * 0.5; // OpenGL depth range -1..1 to Direct3D 0..1
    output.position = clip;
    output.corner = input.corner;
    output.style = float3(input.style.x, input.style.y, progress);
    output.viewDistance = distance(world, uCameraPosition);
    return output;
}

float4 pixelMain(VertexOutput input) : SV_Target {
    float radius = length(input.corner);
    float3 color;
    if (input.style.x < 0.5) {
        clip(1.0 - radius);
        color = radius > 0.68 ? float3(0.33, 0.62, 0.86) : float3(0.86, 0.96, 1.0) * input.style.y;
    } else {
        float width = 0.22 * (1.0 - input.style.z);
        clip(1.0 - radius);
        clip(radius - (1.0 - width));
        color = float3(0.93, 0.99, 1.0);
    }
    float fog = clamp((input.viewDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    return float4(lerp(color, uFogColor, fog), 1.0);
}
