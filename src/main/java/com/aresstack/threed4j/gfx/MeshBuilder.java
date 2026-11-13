package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.math.Vec3;

import java.util.Arrays;

/**
 * Collects flat-shaded, vertex-colored geometry and uploads it as one mesh. One
 * mesh means one draw call, however many boxes it contains.
 *
 * <p>Vertex layout: position, normal, color (3 floats each). Colors are
 * {@link Vec3}s with red, green, blue from 0 to 1.</p>
 */
public final class MeshBuilder {

    private static final int FLOATS_PER_VERTEX = 9;

    private float[] vertices = new float[FLOATS_PER_VERTEX * 1024];
    private int[] indices = new int[6 * 256];
    private int vertexCount;
    private int indexCount;

    /** A triangle; counter-clockwise when seen from its front. */
    public MeshBuilder triangle(Vec3 a, Vec3 b, Vec3 c, Vec3 color) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        int first = vertexCount;
        vertex(a, normal, color);
        vertex(b, normal, color);
        vertex(c, normal, color);
        index(first, first + 1, first + 2);
        return this;
    }

    /** A flat four-sided face; corners counter-clockwise when seen from its front. */
    public MeshBuilder quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 color) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a)).normalize();
        int first = vertexCount;
        vertex(a, normal, color);
        vertex(b, normal, color);
        vertex(c, normal, color);
        vertex(d, normal, color);
        index(first, first + 1, first + 2);
        index(first, first + 2, first + 3);
        return this;
    }

    /** A horizontal rectangle facing up, e.g. a piece of ground. */
    public MeshBuilder floor(float minX, float minZ, float maxX, float maxZ, float y, Vec3 color) {
        return quad(new Vec3(minX, y, maxZ), new Vec3(maxX, y, maxZ),
                new Vec3(maxX, y, minZ), new Vec3(minX, y, minZ), color);
    }

    /** An axis-aligned box without a bottom face. */
    public MeshBuilder box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                           Vec3 sideColor, Vec3 topColor) {
        quad(new Vec3(maxX, minY, maxZ), new Vec3(maxX, minY, minZ),
                new Vec3(maxX, maxY, minZ), new Vec3(maxX, maxY, maxZ), sideColor);
        quad(new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, maxZ), new Vec3(minX, maxY, minZ), sideColor);
        quad(new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ), sideColor);
        quad(new Vec3(maxX, minY, minZ), new Vec3(minX, minY, minZ),
                new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ), sideColor);
        return floor(minX, minZ, maxX, maxZ, maxY, topColor);
    }

    /** A four-sided pyramid on a square base, e.g. a roof. */
    public MeshBuilder pyramid(float centerX, float baseY, float centerZ, float halfSize, float height, Vec3 color) {
        Vec3 apex = new Vec3(centerX, baseY + height, centerZ);
        Vec3[] base = {
                new Vec3(centerX - halfSize, baseY, centerZ + halfSize),
                new Vec3(centerX + halfSize, baseY, centerZ + halfSize),
                new Vec3(centerX + halfSize, baseY, centerZ - halfSize),
                new Vec3(centerX - halfSize, baseY, centerZ - halfSize)};
        for (int i = 0; i < 4; i++) {
            triangle(base[i], base[(i + 1) % 4], apex, color);
        }
        return this;
    }

    /** An upright prism with a regular polygon as footprint – a coarse cylinder. No bottom face. */
    public MeshBuilder prism(float centerX, float minY, float maxY, float centerZ, float radius, int sides,
                             Vec3 sideColor, Vec3 topColor) {
        Vec3 topCenter = new Vec3(centerX, maxY, centerZ);
        for (int i = 0; i < sides; i++) {
            double angle0 = 2 * Math.PI * i / sides;
            double angle1 = 2 * Math.PI * (i + 1) / sides;
            float x0 = centerX + radius * (float) Math.cos(angle0);
            float z0 = centerZ + radius * (float) Math.sin(angle0);
            float x1 = centerX + radius * (float) Math.cos(angle1);
            float z1 = centerZ + radius * (float) Math.sin(angle1);
            quad(new Vec3(x1, minY, z1), new Vec3(x0, minY, z0),
                    new Vec3(x0, maxY, z0), new Vec3(x1, maxY, z1), sideColor);
            triangle(topCenter, new Vec3(x1, maxY, z1), new Vec3(x0, maxY, z0), topColor);
        }
        return this;
    }

    public int triangleCount() {
        return indexCount / 3;
    }

    public Mesh build(GraphicsDevice graphics) {
        return graphics.createMesh(Arrays.copyOf(vertices, vertexCount * FLOATS_PER_VERTEX),
                Arrays.copyOf(indices, indexCount), 3, 3, 3);
    }

    private void vertex(Vec3 position, Vec3 normal, Vec3 color) {
        int offset = vertexCount * FLOATS_PER_VERTEX;
        if (offset + FLOATS_PER_VERTEX > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        vertices[offset] = position.x();
        vertices[offset + 1] = position.y();
        vertices[offset + 2] = position.z();
        vertices[offset + 3] = normal.x();
        vertices[offset + 4] = normal.y();
        vertices[offset + 5] = normal.z();
        vertices[offset + 6] = color.x();
        vertices[offset + 7] = color.y();
        vertices[offset + 8] = color.z();
        vertexCount++;
    }

    private void index(int a, int b, int c) {
        if (indexCount + 3 > indices.length) {
            indices = Arrays.copyOf(indices, indices.length * 2);
        }
        indices[indexCount++] = a;
        indices[indexCount++] = b;
        indices[indexCount++] = c;
    }
}
