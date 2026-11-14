package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.math.Vec3;

import java.util.Random;

/**
 * The mountains west of the town, as the sea lies to its east: a row of peaks
 * with snow on top, right behind the first column of plots in the west. The
 * town does not grow beyond them.
 */
final class Alps implements AutoCloseable {

    /** Where the mountains begin: the west edge of the first column of plots. */
    static final float FOOT_X = -District.PLOT * 1.5f;

    final Mesh peaks;

    Alps(GraphicsDevice graphics) {
        MeshBuilder builder = new MeshBuilder();
        Random random = new Random(1887);
        Vec3 meadow = new Vec3(0.52f, 0.72f, 0.46f);
        Vec3 rock = new Vec3(0.60f, 0.62f, 0.70f);
        Vec3 snow = new Vec3(0.97f, 0.98f, 1.0f);
        for (float z = -520f; z <= 520f; z += 46f) {
            // Two rows: lower green foothills in front, high peaks behind.
            float hill = 26f + random.nextFloat() * 16f;
            peak(builder, FOOT_X - 18f - random.nextFloat() * 8f, z + random.nextFloat() * 20f, 44f + random.nextFloat() * 12f,
                    hill, hill, meadow, meadow, random);
            float height = 95f + random.nextFloat() * 55f;
            peak(builder, FOOT_X - 56f - random.nextFloat() * 22f, z + 23f + random.nextFloat() * 20f,
                    62f + random.nextFloat() * 22f, height, height * (0.55f + random.nextFloat() * 0.12f), rock, snow, random);
        }
        peaks = builder.build(graphics);
    }

    /**
     * One mountain: an irregular cone of a few flat faces, a different colour above the snow line.
     *
     * @param snowLine height where the upper colour begins; the mountain's height for none
     */
    private static void peak(MeshBuilder builder, float x, float z, float radius, float height, float snowLine,
                             Vec3 lower, Vec3 upper, Random random) {
        int sides = 7;
        float[] reach = new float[sides];
        for (int i = 0; i < sides; i++) {
            reach[i] = radius * (0.8f + random.nextFloat() * 0.4f);
        }
        float turn = random.nextFloat() * 6.28f;
        Vec3 top = new Vec3(x, height, z);
        float share = 1f - snowLine / height; // how far below the top the snow line is, as part of the slope
        for (int i = 0; i < sides; i++) {
            double angle0 = turn + 2 * Math.PI * i / sides;
            double angle1 = turn + 2 * Math.PI * (i + 1) / sides;
            Vec3 foot0 = new Vec3(x + (float) Math.cos(angle0) * reach[i], 0f, z + (float) Math.sin(angle0) * reach[i]);
            Vec3 foot1 = new Vec3(x + (float) Math.cos(angle1) * reach[(i + 1) % sides], 0f,
                    z + (float) Math.sin(angle1) * reach[(i + 1) % sides]);
            // A little darker on every other face, as drawn with two pens.
            Vec3 shade = lower.scale(i % 2 == 0 ? 1f : 0.93f);
            if (share <= 0.001f) {
                builder.triangle(foot1, foot0, top, shade);
                continue;
            }
            Vec3 line0 = top.add(foot0.subtract(top).scale(share));
            Vec3 line1 = top.add(foot1.subtract(top).scale(share));
            builder.quad(foot1, foot0, line0, line1, shade);
            builder.triangle(line1, line0, top, upper.scale(i % 2 == 0 ? 1f : 0.95f));
        }
    }

    @Override
    public void close() {
        peaks.close();
    }
}
