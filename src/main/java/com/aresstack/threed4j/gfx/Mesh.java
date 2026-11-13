package com.aresstack.threed4j.gfx;

import static com.aresstack.threed4j.gl.GL.*;

/**
 * Indexed triangle geometry on the GPU.
 *
 * <p>Vertices are interleaved floats. The layout is described by the number of
 * components per attribute: {@code new Mesh(vertices, indices, 3, 3, 3)} means
 * position (shader location 0), normal (location 1) and color (location 2),
 * nine floats per vertex.</p>
 */
public final class Mesh implements AutoCloseable {

    private final int vertexArray;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int indexCount;

    public Mesh(float[] vertices, int[] indices, int... attributeSizes) {
        int floatsPerVertex = 0;
        for (int size : attributeSizes) {
            floatsPerVertex += size;
        }
        if (floatsPerVertex == 0 || vertices.length % floatsPerVertex != 0) {
            throw new IllegalArgumentException("Vertex data does not match the attribute layout");
        }
        indexCount = indices.length;

        vertexArray = glGenVertexArray();
        glBindVertexArray(vertexArray);

        vertexBuffer = glGenBuffer();
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        indexBuffer = glGenBuffer();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        int stride = floatsPerVertex * Float.BYTES;
        long offset = 0;
        for (int location = 0; location < attributeSizes.length; location++) {
            glEnableVertexAttribArray(location);
            glVertexAttribPointer(location, attributeSizes[location], GL_FLOAT, false, stride, offset);
            offset += (long) attributeSizes[location] * Float.BYTES;
        }
        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vertexArray);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
    }

    @Override
    public void close() {
        glDeleteVertexArray(vertexArray);
        glDeleteBuffer(vertexBuffer);
        glDeleteBuffer(indexBuffer);
    }
}
