package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.math.Vec3;

/**
 * An Easter egg at the end of the street: the WEST.SIDE office ensemble in Bonn,
 * two red office buildings (B, the Generalzolldirektion, and A, the ministry)
 * with a car park closing the yard between them.
 *
 * <p>Measures and structure follow the sketch model of the ensemble
 * ({@code generate_assets.py}: 62 m long, 18 m deep, four storeys of 4.05 m, a
 * column every 6.9 m; the car park 20 x 64 m). That model has about 23,000
 * textured triangles, three times the whole town; here the same building is
 * rebuilt from a few thousand flat-coloured ones, like the rest of the town:
 * the red grid of columns and beams as boxes, each window as a dark frame with a
 * pane of glass in front of the wall.</p>
 *
 * <p>The plan is laid out in its own coordinates as in the sketch model (x along
 * the office buildings, starting at the open side; z across, building B at
 * negative z) and turned so that the open side faces the town: plan x runs away
 * from the viewer (to negative world z), plan z to the right (world x).</p>
 */
final class WestSide {

    /** Extent of the plan: the offices reach from x = 0 to 62, the car park to 82; across from z = -32 to 32. */
    static final float LENGTH = 82f;
    static final float HALF_WIDTH = 32f;

    private static final float[] FLOORS = {0f, 4.25f, 8.30f, 12.35f};
    private static final float[] BEAMS = {4.03f, 8.08f, 12.13f, 16.12f};
    private static final float TOP = 16.40f;

    private static final Vec3 RED = new Vec3(0.68f, 0.26f, 0.32f);
    private static final Vec3 RED_RIBBED = new Vec3(0.60f, 0.21f, 0.28f);
    private static final Vec3 RED_FINS = new Vec3(0.50f, 0.17f, 0.24f);
    private static final Vec3 GLASS = new Vec3(0.36f, 0.62f, 0.74f);
    private static final Vec3 FRAME = new Vec3(0.17f, 0.19f, 0.24f);
    private static final Vec3 PORTAL = new Vec3(0.11f, 0.12f, 0.15f);
    private static final Vec3 DARK_CONCRETE = new Vec3(0.38f, 0.38f, 0.41f);
    private static final Vec3 GRAVEL = new Vec3(0.66f, 0.65f, 0.64f);
    private static final Vec3 ROOF_GREEN = new Vec3(0.48f, 0.66f, 0.48f);
    private static final Vec3 WHITE_METAL = new Vec3(0.86f, 0.87f, 0.86f);

    private final MeshBuilder builder;
    private final float frontZ;

    private WestSide(MeshBuilder builder, float frontZ) {
        this.builder = builder;
        this.frontZ = frontZ;
    }

    /**
     * Adds the ensemble to the mesh, centered on the world's x = 0.
     *
     * @param frontZ world z of the open side; the ensemble extends from there to {@code frontZ - LENGTH}
     */
    static void build(MeshBuilder builder, float frontZ) {
        WestSide plan = new WestSide(builder, frontZ);
        plan.office(-32f, -14f, true);
        plan.office(14f, 32f, false);
        plan.carPark();
    }

    /** The two office buildings and the car park as what stands in the way; see {@link #build}. */
    static void obstacles(Obstacles obstacles, float frontZ) {
        obstacles.box(-32f, frontZ - 62f, -14f, frontZ, TOP);
        obstacles.box(14f, frontZ - 62f, 32f, frontZ, TOP);
        obstacles.box(-HALF_WIDTH, frontZ - LENGTH, HALF_WIDTH, frontZ - 62f, 18f);
    }

    // ------------------------------------------------- plan to world coordinates

    private Vec3 point(float x, float y, float z) {
        return new Vec3(z, y, frontZ - x);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1, Vec3 color) {
        box(x0, y0, z0, x1, y1, z1, color, color);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1, Vec3 side, Vec3 top) {
        builder.box(Math.min(z0, z1), y0, frontZ - Math.max(x0, x1), Math.max(z0, z1), y1, frontZ - Math.min(x0, x1),
                side, top);
    }

    /**
     * An upright rectangle on a wall that runs along plan x, at plan z, seen from
     * the side {@code outward} (+1: from greater z, -1: from smaller z).
     */
    private void onLongWall(float x0, float x1, float y0, float y1, float z, int outward, Vec3 color) {
        // Seen from its front, plan x grows to the right on the +z side and to the left on the -z side.
        float left = outward > 0 ? x0 : x1;
        float right = outward > 0 ? x1 : x0;
        builder.quad(point(left, y0, z), point(right, y0, z), point(right, y1, z), point(left, y1, z), color);
    }

    /** An upright rectangle on a wall that runs along plan z, at plan x, seen from smaller x (the open side). */
    private void onFrontWall(float z0, float z1, float y0, float y1, float x, Vec3 color) {
        builder.quad(point(x, y0, z0), point(x, y0, z1), point(x, y1, z1), point(x, y1, z0), color);
    }

    // ------------------------------------------------------------------ offices

    /**
     * One office building, x from 0 to 62.
     *
     * @param garage building B: the entrance to the underground car park in its front
     */
    private void office(float z0, float z1, boolean garage) {
        float x0 = 0f;
        float x1 = 62f;
        box(x0, 0f, z0, x1, TOP, z1, RED, GRAVEL);
        longFacade(x0, x1, z0, -1);
        longFacade(x0, x1, z1, 1);
        // The entrance faces the yard: for B that is its +z side, for A its -z side.
        entrance(x0, x1, garage ? z1 : z0, garage ? 1 : -1);
        frontFacade(x0, z0, z1, garage);
        roof(x0, x1, z0, z1, !garage);
    }

    private void longFacade(float x0, float x1, float z, int outward) {
        int bays = 9;
        float step = (x1 - x0) / bays;
        for (int floor = 0; floor < FLOORS.length; floor++) {
            float base = FLOORS[floor];
            for (int bay = 0; bay < bays; bay++) {
                float start = x0 + bay * step + 0.52f;
                float end = x0 + (bay + 1) * step - 0.30f;
                float lower = base + (floor == 0 ? 0.48f : 1.10f);
                float upper = base + 3.71f;
                window(start, end, lower, upper, 3, (a, b, c, d, depth, color) ->
                        onLongWall(a, b, c, d, z + depth * outward, outward, color));
                if (floor > 0) { // the ribbed panel below the window
                    onLongWall(start, end, base + 0.15f, base + 1.02f, z + 0.05f * outward, outward, RED_RIBBED);
                }
            }
        }
        // The red grid in front of the wall: a beam at every floor, a column between the bays.
        float near = z + 0.03f * outward;
        float far = z + 0.22f * outward;
        for (float beam : BEAMS) {
            box(x0, beam, near, x1, beam + 0.27f, far, RED);
        }
        for (int column = 0; column <= bays; column++) {
            float x = x0 + column * step;
            box(x - 0.23f, 0f, near, x + 0.23f, TOP, far, RED);
        }
        box(x0, 0.08f, near, x1, 0.41f, far, RED_RIBBED);
    }

    /** The glazed main entrance in the middle of the yard side, with a canopy. */
    private void entrance(float x0, float x1, float z, int outward) {
        float step = (x1 - x0) / 9;
        float start = x0 + 4 * step - 0.2f;
        float end = x0 + 5 * step + 0.2f;
        box(start, 3.75f, z, end, 4.0f, z + 2.4f * outward, WHITE_METAL, GRAVEL);
        box(start + 0.2f, 0f, z + 2.1f * outward, start + 0.45f, 3.75f, z + 2.35f * outward, FRAME);
        box(end - 0.45f, 0f, z + 2.1f * outward, end - 0.2f, 3.75f, z + 2.35f * outward, FRAME);
    }

    private void frontFacade(float x, float z0, float z1, boolean garage) {
        int bays = 3;
        float step = (z1 - z0) / bays;
        float front = x - 0.07f;
        for (int floor = 0; floor < FLOORS.length; floor++) {
            float base = FLOORS[floor];
            for (int bay = 0; bay < bays; bay++) {
                if (garage && floor == 0 && bay < 2) {
                    continue; // the gate to the underground car park
                }
                float start = z0 + bay * step + 0.42f;
                float end = z0 + (bay + 1) * step - 0.35f;
                float lower = base + (floor == 0 ? 0.55f : 1.10f);
                float upper = base + 3.68f;
                window(start, end, lower, upper, 2, (a, b, c, d, depth, color) ->
                        onFrontWall(a, b, c, d, x - depth, color));
                if (floor > 0) {
                    onFrontWall(start, end, base + 0.14f, base + 1.02f, x - 0.05f, RED_RIBBED);
                }
            }
        }
        for (float beam : BEAMS) {
            box(x - 0.22f, beam, z0, x - 0.03f, beam + 0.27f, z1, RED);
        }
        for (int column = 0; column <= bays; column++) {
            float z = z0 + column * step;
            box(x - 0.22f, 0f, z - 0.21f, x - 0.03f, TOP, z + 0.21f, RED);
        }
        if (garage) {
            float gate0 = z0 + 0.65f;
            float gate1 = z0 + 2 * step - 0.28f;
            onFrontWall(gate0, gate1, 0.12f, 4.03f, front, PORTAL);
            for (int slat = 0; slat < 6; slat++) { // the roller grille
                float y = 0.45f + slat * 0.6f;
                onFrontWall(gate0 + 0.2f, gate1 - 0.2f, y, y + 0.08f, front - 0.03f, FRAME);
            }
            // The barrier in front of the ramp: post and white-and-red boom.
            box(x - 3.7f, 0f, gate0 + 0.25f, x - 3.5f, 1.05f, gate0 + 0.45f, WHITE_METAL);
            box(x - 3.66f, 0.9f, gate0 + 0.35f, x - 3.54f, 1.04f, gate1 - 0.4f, WHITE_METAL);
            for (int stripe = 0; stripe < 4; stripe++) {
                float z = gate0 + 0.9f + stripe * (gate1 - gate0 - 1.6f) / 4;
                box(x - 3.68f, 0.88f, z, x - 3.52f, 1.06f, z + 0.5f, RED);
            }
        }
    }

    private interface Wall {
        /** A rectangle from a to b along the wall and from c to d in height, {@code depth} in front of the wall. */
        void rectangle(float a, float b, float c, float d, float depth, Vec3 color);
    }

    /** A window: the dark frame, the pane a little smaller in front of it, and the bars between the casements. */
    private static void window(float start, float end, float lower, float upper, int casements, Wall wall) {
        wall.rectangle(start, end, lower, upper, 0.04f, FRAME);
        wall.rectangle(start + 0.10f, end - 0.10f, lower + 0.10f, upper - 0.10f, 0.07f, GLASS);
        for (int bar = 1; bar < casements; bar++) {
            float at = start + (end - start) * bar / casements;
            wall.rectangle(at - 0.05f, at + 0.05f, lower + 0.10f, upper - 0.10f, 0.09f, FRAME);
        }
    }

    private void roof(float x0, float x1, float z0, float z1, boolean green) {
        float rim = 0.34f;
        box(x0, TOP, z0, x1, TOP + 0.52f, z0 + rim, RED);
        box(x0, TOP, z1 - rim, x1, TOP + 0.52f, z1, RED);
        box(x0, TOP, z0, x0 + rim, TOP + 0.52f, z1, RED);
        box(x1 - rim, TOP, z0, x1, TOP + 0.52f, z1, RED);
        for (int unit = 0; unit < 5; unit++) { // ventilation units
            float x = x0 + 6.5f + unit * 9.6f;
            float z = z0 + 3.4f + (unit % 2) * 5.5f;
            box(x, TOP, z, x + 2.9f, TOP + 1.02f, z + 2.4f, WHITE_METAL, FRAME);
        }
        if (green) {
            for (int patch = 0; patch < 3; patch++) {
                float x = x0 + 2f + patch * 13f;
                box(x, TOP, z0 + 10f, x + 9f, TOP + 0.07f, z1 - 2f, ROOF_GREEN);
            }
        }
    }

    // ----------------------------------------------------------------- car park

    /** The car park behind the offices: a dark block behind upright red fins. */
    private void carPark() {
        float x0 = 62f;
        float x1 = 82f;
        float z0 = -HALF_WIDTH;
        float z1 = HALF_WIDTH;
        float height = 18f;
        box(x0, 0f, z0, x1, height - 0.2f, z1, DARK_CONCRETE, GRAVEL);
        // Towards the yard only the part between the offices can be seen.
        for (float z = -13.2f; z < 13.5f; z += 1.74f) {
            box(x0 - 0.5f, 0.1f, z - 0.14f, x0, height + 0.25f, z + 0.14f, RED_FINS);
        }
        for (float y : new float[] {3.6f, 7.0f, 10.45f, 13.85f, 17.25f}) {
            box(x0 - 0.45f, y - 0.1f, -14f, x0, y + 0.1f, 14f, RED);
            box(x1, y - 0.1f, z0, x1 + 0.45f, y + 0.1f, z1, RED);
        }
        for (float z = z0 + 1f; z < z1 - 0.8f; z += 2.6f) {
            box(x1, 0.1f, z - 0.14f, x1 + 0.5f, height + 0.25f, z + 0.14f, RED_FINS);
        }
        for (float x = x0 + 0.6f; x < x1 - 0.4f; x += 1.68f) {
            box(x - 0.13f, 0.1f, z0 - 0.45f, x + 0.13f, height + 0.25f, z0, RED_FINS);
            box(x - 0.13f, 0.1f, z1, x + 0.13f, height + 0.25f, z1 + 0.45f, RED_FINS);
        }
        // The entrance for cars at the far corner.
        onLongWall(x1 - 11.5f, x1 - 2f, 0.05f, 3.45f, z1 + 0.47f, 1, PORTAL);
        box(x1 - 12f, 3.55f, z1 + 0.42f, x1 - 1.6f, 3.91f, z1 + 0.75f, WHITE_METAL);
    }
}
