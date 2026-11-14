package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.math.Vec3;

import java.util.Random;

/**
 * The sea east of the town. Its shore is {@link District#coastX}: a wavy line
 * with headlands and bays. The water is one flat-coloured surface a little below
 * the ground, with rings that spread on it, drawn by the shader of the fountain's
 * water; nothing is computed per frame.
 */
final class Sea implements AutoCloseable {

    static final float LEVEL = -0.45f;
    /** From how far south to how far north the sea is built. */
    private static final float EXTENT = 520f;
    private static final float STEP = 4f;

    /** The surface; layout of the scene shader. */
    final Mesh surface;
    /** Rings on the surface; layout of the water shader. */
    final Mesh rings;

    Sea(GraphicsDevice graphics) {
        MeshBuilder builder = new MeshBuilder();
        Vec3 near = new Vec3(0.52f, 0.82f, 0.93f);
        Vec3 far = new Vec3(0.40f, 0.70f, 0.88f);
        for (float z = -EXTENT; z < EXTENT; z += STEP) {
            float shore0 = District.coastX(z) - 0.5f;
            float shore1 = District.coastX(z + STEP) - 0.5f;
            // Lighter water at the shore, then the open sea.
            builder.quad(new Vec3(shore1, LEVEL, z + STEP), new Vec3(shore1 + 14f, LEVEL, z + STEP),
                    new Vec3(shore0 + 14f, LEVEL, z), new Vec3(shore0, LEVEL, z), near);
            builder.quad(new Vec3(shore1 + 14f, LEVEL, z + STEP), new Vec3(700f, LEVEL, z + STEP),
                    new Vec3(700f, LEVEL, z), new Vec3(shore0 + 14f, LEVEL, z), far);
        }
        surface = builder.build(graphics);

        Random random = new Random(11);
        int count = 260;
        float[] vertices = new float[count * 4 * 14];
        int[] indices = new int[count * 6];
        // A ring lies flat; seen from above its corners run this way round.
        float[][] corners = {{-1f, 1f}, {1f, 1f}, {1f, -1f}, {-1f, -1f}};
        int v = 0;
        for (int ring = 0; ring < count; ring++) {
            float z = (random.nextFloat() * 2 - 1) * (EXTENT - 20f);
            // Many near the shore, fewer further out.
            float out = 2.5f + random.nextFloat() * random.nextFloat() * 80f;
            float x = District.coastX(z) + out;
            float phase = random.nextFloat();
            float period = 2.6f + random.nextFloat() * 3f;
            float size = 1.2f + random.nextFloat() * 2.2f;
            for (float[] corner : corners) {
                vertices[v++] = x;
                vertices[v++] = LEVEL + 0.04f;
                vertices[v++] = z;
                vertices[v++] = corner[0];
                vertices[v++] = corner[1];
                vertices[v++] = 0f; // rings do not fly
                vertices[v++] = 0f;
                vertices[v++] = 0f;
                vertices[v++] = phase;
                vertices[v++] = period;
                vertices[v++] = size;
                vertices[v++] = 1f; // a ring
                vertices[v++] = 1f;
                vertices[v++] = 0f;
            }
            int first = ring * 4;
            int at = ring * 6;
            indices[at] = first;
            indices[at + 1] = first + 1;
            indices[at + 2] = first + 2;
            indices[at + 3] = first;
            indices[at + 4] = first + 2;
            indices[at + 5] = first + 3;
        }
        rings = graphics.createMesh(vertices, indices, 3, 2, 3, 3, 3);
    }

    /**
     * Keeps somebody on foot out of the water: who walks from the land towards the
     * sea stops at the shore and slides along it. Who flies may cross it.
     */
    static Vec3 keepAshore(Vec3 from, Vec3 wanted) {
        float edge = District.coastX(wanted.z()) + 0.3f;
        boolean wasAshore = from.x() <= District.coastX(from.z()) + 0.6f;
        if (wanted.y() < 3f && wasAshore && wanted.x() > edge) {
            return new Vec3(edge, wanted.y(), wanted.z());
        }
        return wanted;
    }

    @Override
    public void close() {
        surface.close();
        rings.close();
    }
}
