package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.Mesh;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static com.aresstack.threed4j.gfx.d3d11.D3D11Api.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** One interleaved vertex buffer and a 32-bit index buffer. */
final class D3D11Mesh implements Mesh {

    private final ComObject context;
    private final Arena arena = Arena.ofConfined();

    private final MemorySegment vertexBuffer;
    private final MemorySegment indexBuffer;
    private final int indexCount;

    /** Arguments of IASetVertexBuffers, prepared once: buffer array, stride and offset. */
    private final MemorySegment vertexBufferArray;
    private final MemorySegment stride;
    private final MemorySegment offset;

    D3D11Mesh(ComObject device, ComObject context, float[] vertices, int[] indices, int... attributeSizes) {
        this.context = context;
        int floatsPerVertex = 0;
        for (int size : attributeSizes) {
            floatsPerVertex += size;
        }
        if (floatsPerVertex == 0 || vertices.length % floatsPerVertex != 0) {
            throw new IllegalArgumentException("Vertex data does not match the attribute layout");
        }
        indexCount = indices.length;

        try (Arena temp = Arena.ofConfined()) {
            vertexBuffer = createImmutableBuffer(device, temp, Native.floats(temp, vertices), D3D11_BIND_VERTEX_BUFFER);
            indexBuffer = createImmutableBuffer(device, temp, Native.ints(temp, indices), D3D11_BIND_INDEX_BUFFER);
        }

        vertexBufferArray = arena.allocate(ADDRESS);
        vertexBufferArray.set(ADDRESS, 0, vertexBuffer);
        stride = Native.ints(arena, floatsPerVertex * Float.BYTES);
        offset = Native.ints(arena, 0);
    }

    private static MemorySegment createImmutableBuffer(ComObject device, Arena temp, MemorySegment data, int bindFlags) {
        MemorySegment desc = Native.bytes(temp, SIZEOF_BUFFER_DESC);
        desc.set(JAVA_INT, BD_BYTE_WIDTH, (int) data.byteSize());
        desc.set(JAVA_INT, BD_USAGE, D3D11_USAGE_IMMUTABLE);
        desc.set(JAVA_INT, BD_BIND_FLAGS, bindFlags);
        MemorySegment initialData = Native.bytes(temp, SIZEOF_SUBRESOURCE_DATA);
        initialData.set(ADDRESS, 0, data);
        return createBuffer(device, desc, initialData);
    }

    void draw() {
        iaSetPrimitiveTopology(context, D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
        iaSetVertexBuffer(context, vertexBufferArray, stride, offset);
        iaSetIndexBuffer(context, indexBuffer, DXGI_FORMAT_R32_UINT);
        drawIndexed(context, indexCount);
    }

    @Override
    public void close() {
        ComObject.release(indexBuffer);
        ComObject.release(vertexBuffer);
        arena.close();
    }
}
