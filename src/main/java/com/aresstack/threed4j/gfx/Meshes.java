package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.math.Vec3;

/** Factory for simple built-in geometry. */
public final class Meshes {

    private Meshes() {
    }

    /**
     * A cube with edge length 1 around the origin. Layout: position, normal, color
     * (3 floats each). Every face has its own four vertices so that normals and
     * colors are constant per face.
     */
    public static Mesh cube(GraphicsDevice graphics) {
        // Per face: two edge directions (their cross product points outwards) and a color.
        float[][] faces = {
                {0, 0, -1, 0, 1, 0, 0.90f, 0.30f, 0.25f},  // +X red
                {0, 0, 1, 0, 1, 0, 0.95f, 0.60f, 0.20f},   // -X orange
                {1, 0, 0, 0, 0, -1, 0.35f, 0.75f, 0.35f},  // +Y green
                {1, 0, 0, 0, 0, 1, 0.25f, 0.70f, 0.75f},   // -Y teal
                {1, 0, 0, 0, 1, 0, 0.30f, 0.45f, 0.90f},   // +Z blue
                {-1, 0, 0, 0, 1, 0, 0.90f, 0.80f, 0.30f},  // -Z yellow
        };
        float[] vertices = new float[faces.length * 4 * 9];
        int[] indices = new int[faces.length * 6];
        int v = 0;
        int i = 0;
        for (int face = 0; face < faces.length; face++) {
            float[] f = faces[face];
            Vec3 right = new Vec3(f[0], f[1], f[2]);
            Vec3 up = new Vec3(f[3], f[4], f[5]);
            Vec3 normal = right.cross(up);
            Vec3 center = normal.scale(0.5f);
            // Counter-clockwise when seen from outside.
            Vec3[] corners = {
                    center.subtract(right.scale(0.5f)).subtract(up.scale(0.5f)),
                    center.add(right.scale(0.5f)).subtract(up.scale(0.5f)),
                    center.add(right.scale(0.5f)).add(up.scale(0.5f)),
                    center.subtract(right.scale(0.5f)).add(up.scale(0.5f)),
            };
            for (Vec3 corner : corners) {
                vertices[v++] = corner.x();
                vertices[v++] = corner.y();
                vertices[v++] = corner.z();
                vertices[v++] = normal.x();
                vertices[v++] = normal.y();
                vertices[v++] = normal.z();
                vertices[v++] = f[6];
                vertices[v++] = f[7];
                vertices[v++] = f[8];
            }
            int first = face * 4;
            indices[i++] = first;
            indices[i++] = first + 1;
            indices[i++] = first + 2;
            indices[i++] = first;
            indices[i++] = first + 2;
            indices[i++] = first + 3;
        }
        return graphics.createMesh(vertices, indices, 3, 3, 3);
    }
}
