package com.aresstack.threed4j.gfx.gl;

import com.aresstack.threed4j.gfx.Mesh;

import static com.aresstack.threed4j.gl.GL.*;

/** Vertex array object with one interleaved vertex buffer and an index buffer. */
final class GLMesh implements Mesh {

    private final int vertexArray;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int indexCount;

    GLMesh(float[] vertices, int[] indices, int... attributeSizes) {
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

    void draw() {
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
