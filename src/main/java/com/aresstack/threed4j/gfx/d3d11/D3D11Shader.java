package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.gfx.ShaderSource;
import com.aresstack.threed4j.gfx.Texture;
import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;

import static com.aresstack.threed4j.gfx.d3d11.D3D11Api.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Vertex and pixel shader compiled from one HLSL file with the entry points
 * {@code vertexMain} and {@code pixelMain}, plus the input layout and the constant
 * buffer that carries the uniforms.
 */
final class D3D11Shader implements Shader {

    private final ComObject context;
    private final Arena arena = Arena.ofConfined();

    private final MemorySegment vertexShader;
    private final MemorySegment pixelShader;
    private final MemorySegment inputLayout;
    private final MemorySegment constantBuffer;

    /** CPU copy of the constant buffer; uploaded before a draw call if it changed. */
    private final MemorySegment constants;
    /** Native array of one {@code ID3D11Buffer*}, as the Set...ConstantBuffers calls expect it. */
    private final MemorySegment constantBufferArray;
    private final Map<String, Long> uniformOffsets = new HashMap<>();
    private final Map<String, ShaderLayout.UniformType> uniformTypes = new HashMap<>();
    private final float[] matrixValues = new float[16];
    private boolean dirty = true;
    private D3D11Texture texture;

    D3D11Shader(ComObject device, ComObject context, String name, ShaderLayout layout) {
        this.context = context;
        String fileName = name + ".hlsl";
        String source = ShaderSource.read("/shaders/" + fileName);

        ComObject vertexCode = compile(source, fileName, "vertexMain", "vs_4_0");
        ComObject pixelCode = compile(source, fileName, "pixelMain", "ps_4_0");
        try {
            vertexShader = createVertexShader(device, blobPointer(vertexCode), blobSize(vertexCode));
            pixelShader = createPixelShader(device, blobPointer(pixelCode), blobSize(pixelCode));
            inputLayout = createInputLayout(device, layout.attributeSizes(), vertexCode);
        } finally {
            vertexCode.release();
            pixelCode.release();
        }

        long size = layOutConstants(layout);
        constants = Native.bytes(arena, size);
        MemorySegment desc = Native.bytes(arena, SIZEOF_BUFFER_DESC);
        desc.set(JAVA_INT, BD_BYTE_WIDTH, (int) size);
        desc.set(JAVA_INT, BD_USAGE, D3D11_USAGE_DEFAULT);
        desc.set(JAVA_INT, BD_BIND_FLAGS, D3D11_BIND_CONSTANT_BUFFER);
        constantBuffer = createBuffer(device, desc, MemorySegment.NULL);
        constantBufferArray = arena.allocate(ADDRESS);
        constantBufferArray.set(ADDRESS, 0, constantBuffer);
    }

    /** Attribute {@code i} of the vertex buffer feeds the HLSL input with semantic {@code ATTRIBUTEi}. */
    private MemorySegment createInputLayout(ComObject device, int[] attributeSizes, ComObject vertexCode) {
        MemorySegment semantic = Native.cString(arena, "ATTRIBUTE");
        MemorySegment elements = Native.bytes(arena, SIZEOF_INPUT_ELEMENT_DESC * attributeSizes.length);
        int offset = 0;
        for (int i = 0; i < attributeSizes.length; i++) {
            long base = SIZEOF_INPUT_ELEMENT_DESC * i;
            elements.set(ADDRESS, base + IED_SEMANTIC_NAME, semantic);
            elements.set(JAVA_INT, base + IED_SEMANTIC_INDEX, i);
            elements.set(JAVA_INT, base + IED_FORMAT, switch (attributeSizes[i]) {
                case 1 -> DXGI_FORMAT_R32_FLOAT;
                case 2 -> DXGI_FORMAT_R32G32_FLOAT;
                case 3 -> DXGI_FORMAT_R32G32B32_FLOAT;
                default -> DXGI_FORMAT_R32G32B32A32_FLOAT;
            });
            elements.set(JAVA_INT, base + IED_ALIGNED_BYTE_OFFSET, offset);
            offset += attributeSizes[i] * Float.BYTES;
        }
        return D3D11Api.createInputLayout(device, elements, attributeSizes.length,
                blobPointer(vertexCode), blobSize(vertexCode));
    }

    /**
     * Computes where each uniform lives in the constant buffer, following the HLSL
     * packing rules: values are packed into 16-byte rows, a value never crosses a
     * row boundary, and a matrix always starts a new row.
     *
     * @return the buffer size, a multiple of 16
     */
    private long layOutConstants(ShaderLayout layout) {
        long offset = 0;
        for (ShaderLayout.Uniform uniform : layout.uniforms()) {
            long bytes = uniform.type().floats() * (long) Float.BYTES;
            boolean crossesRow = offset % 16 + bytes > 16;
            if (uniform.type() == ShaderLayout.UniformType.MAT4 || crossesRow) {
                offset = (offset + 15) / 16 * 16;
            }
            uniformOffsets.put(uniform.name(), offset);
            uniformTypes.put(uniform.name(), uniform.type());
            offset += bytes;
        }
        return Math.max(16, (offset + 15) / 16 * 16);
    }

    @Override
    public void setUniform(String name, Mat4 matrix) {
        long offset = offset(name, ShaderLayout.UniformType.MAT4);
        matrix.copyTo(matrixValues, 0);
        MemorySegment.copy(matrixValues, 0, constants, JAVA_FLOAT, offset, 16);
        dirty = true;
    }

    @Override
    public void setUniform(String name, Vec3 vector) {
        long offset = offset(name, ShaderLayout.UniformType.VEC3);
        constants.set(JAVA_FLOAT, offset, vector.x());
        constants.set(JAVA_FLOAT, offset + 4, vector.y());
        constants.set(JAVA_FLOAT, offset + 8, vector.z());
        dirty = true;
    }

    @Override
    public void setUniform(String name, float value) {
        constants.set(JAVA_FLOAT, offset(name, ShaderLayout.UniformType.FLOAT), value);
        dirty = true;
    }

    private long offset(String name, ShaderLayout.UniformType type) {
        Long offset = uniformOffsets.get(name);
        if (offset == null) {
            throw new IllegalArgumentException("Uniform is not declared in the ShaderLayout: " + name);
        }
        if (uniformTypes.get(name) != type) {
            throw new IllegalArgumentException("Uniform " + name + " is declared as " + uniformTypes.get(name));
        }
        return offset;
    }

    /** Makes this shader and its uniform values current for the next draw call. */
    void bind() {
        if (dirty) {
            updateSubresource(context, constantBuffer, constants);
            dirty = false;
        }
        iaSetInputLayout(context, inputLayout);
        vsSetShader(context, vertexShader);
        psSetShader(context, pixelShader);
        vsSetConstantBuffer(context, constantBufferArray);
        psSetConstantBuffer(context, constantBufferArray);
        if (texture != null) {
            psSetShaderResource(context, texture.viewArray());
            psSetSampler(context, texture.samplerArray());
        }
    }

    @Override
    public void setTexture(Texture texture) {
        this.texture = (D3D11Texture) texture;
    }

    @Override
    public void close() {
        ComObject.release(constantBuffer);
        ComObject.release(inputLayout);
        ComObject.release(pixelShader);
        ComObject.release(vertexShader);
        arena.close();
    }
}
