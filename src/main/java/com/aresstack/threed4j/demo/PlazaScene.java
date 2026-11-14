package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.math.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Builds the test plaza: a round square with a fountain, four streets and blocks
 * of simple buildings around it, plus trees, lamps and visitors as flat sprites.
 *
 * <p>It is a benchmark scene, not level design: buildings and fountain end up in
 * one mesh, the textured ground in a second, all sprites in a third, the sky in a
 * fourth. Four draw calls per frame.</p>
 */
final class PlazaScene implements AutoCloseable {

    /** Colour of the haze; distant geometry and the horizon fade into it. */
    static final Vec3 HAZE = new Vec3(0.94f, 0.82f, 0.90f);

    private static final float PLAZA_RADIUS = 22f;
    private static final float STREET_HALF_WIDTH = 5f;

    private static final Vec3[] WALLS = {
            new Vec3(0.98f, 0.86f, 0.80f), new Vec3(0.93f, 0.80f, 0.92f), new Vec3(0.80f, 0.88f, 0.97f),
            new Vec3(0.99f, 0.93f, 0.76f), new Vec3(0.84f, 0.94f, 0.86f), new Vec3(0.96f, 0.78f, 0.80f)};
    private static final Vec3[] ROOFS = {
            new Vec3(0.55f, 0.38f, 0.66f), new Vec3(0.82f, 0.42f, 0.44f), new Vec3(0.36f, 0.52f, 0.72f),
            new Vec3(0.45f, 0.62f, 0.58f)};

    final Mesh city;
    final Mesh ground;
    /** The walls of the houses with their windows and doors; replaced when the district gets new building parts. */
    Mesh facades;
    /** The houses of the town, for building {@link #facades}. */
    final List<Facades.House> houses = new ArrayList<>();
    int facadeTriangles;
    final Mesh sprites;
    final Mesh sky;
    final int cityTriangles;
    final int groundTriangles = 50 * (50 + WEST_SIDE_ROWS) * 2;
    final int spriteCount;
    final int buildingCount;

    /**
     * @param groundAtlas the ground textures; their average colours go into the ground mesh
     * @param facadeAtlas the building parts the houses choose from
     */
    PlazaScene(GraphicsDevice graphics, GroundAtlas groundAtlas, FacadeAtlas facadeAtlas) {
        Random random = new Random(21);

        ground = ground(graphics, groundAtlas);
        MeshBuilder builder = new MeshBuilder();
        fountain(builder);
        buildingCount = buildings(builder, random) + 3;
        westSide(builder);
        city = builder.build(graphics);
        cityTriangles = builder.triangleCount();
        Facades.Built walls = Facades.build(graphics, houses, facadeAtlas);
        facades = walls.mesh();
        facadeTriangles = walls.triangles();

        SpriteBatch batch = new SpriteBatch();
        plants(batch, random);
        visitors(batch, random);
        bushes(batch, random); // after the rest, so that everything before keeps its place
        westSideSprites(batch, random);
        sprites = batch.build(graphics);
        spriteCount = batch.count;

        sky = sky(graphics);
    }

    // ------------------------------------------------------------------- ground

    /** World z of the open side of the WEST.SIDE ensemble: straight down the street from the start, past the last houses. */
    static final float WEST_SIDE_FRONT = -116f;
    /** Rows of ground squares added beyond the town for the ensemble. */
    private static final int WEST_SIDE_ROWS = 28;

    /**
     * The Easter egg at the end of the street and a hint of its neighborhood in
     * Bonn: the old building at the open side, a dark hall to the left, housing
     * blocks with red-brown roofs to the right.
     */
    private static void westSide(MeshBuilder builder) {
        WestSide.build(builder, WEST_SIDE_FRONT);

        Vec3 oldWall = new Vec3(0.86f, 0.80f, 0.68f);
        Vec3 oldRoof = new Vec3(0.52f, 0.40f, 0.36f);
        builder.box(9f, 0f, WEST_SIDE_FRONT + 3f, 21f, 6.5f, WEST_SIDE_FRONT + 12f, oldWall, oldRoof);
        builder.pyramid(15f, 6.5f, WEST_SIDE_FRONT + 7.5f, 6.6f, 3.2f, oldRoof);

        Vec3 hall = new Vec3(0.40f, 0.41f, 0.46f);
        builder.box(-86f, 0f, WEST_SIDE_FRONT - 52f, -58f, 8f, WEST_SIDE_FRONT - 8f, hall, hall.scale(0.8f));

        Vec3 housing = new Vec3(0.93f, 0.90f, 0.86f);
        Vec3 housingRoof = new Vec3(0.66f, 0.42f, 0.38f);
        float[][] blocks = {{54f, -6f, 18f, 13f}, {76f, -22f, 13f, 18f}, {56f, -40f, 18f, 13f}, {78f, -58f, 13f, 18f},
                {57f, -74f, 18f, 13f}};
        for (float[] block : blocks) {
            float z = WEST_SIDE_FRONT + block[1];
            builder.box(block[0] - block[2] / 2, 0f, z - block[3] / 2, block[0] + block[2] / 2, 12f, z + block[3] / 2,
                    housing, housingRoof);
            builder.box(block[0] - block[2] / 2 + 1.5f, 12f, z - block[3] / 2 + 1.5f, block[0] + block[2] / 2 - 1.5f,
                    14.6f, z + block[3] / 2 - 1.5f, housing, housingRoof);
        }
    }

    /** Edge length of one ground texture tile in the world. */
    static final float GROUND_TILE = 4f;

    /**
     * The ground as its own mesh: squares of {@link #GROUND_TILE} at y = 0. Layout:
     * position (3); atlas cell and brightness (3, the last unused); average colour of
     * the texture (3). The texture coordinate follows from the position, so the
     * pattern runs on across squares. Neighboring squares differ slightly in
     * brightness, as the flat-coloured ground did.
     */
    private static Mesh ground(GraphicsDevice graphics, GroundAtlas atlas) {
        int perSide = 50; // groundTriangles follows from this and WEST_SIDE_ROWS
        int rows = perSide + WEST_SIDE_ROWS;
        float[] vertices = new float[perSide * rows * 4 * 9];
        int[] indices = new int[perSide * rows * 6];
        int v = 0;
        int i = 0;
        for (int ix = -perSide / 2; ix < perSide / 2; ix++) {
            // The town, and beyond its far edge the ground of the WEST.SIDE ensemble.
            for (int iz = -perSide / 2 - WEST_SIDE_ROWS; iz < perSide / 2; iz++) {
                float x = ix * GROUND_TILE;
                float z = iz * GROUND_TILE;
                float centerX = x + GROUND_TILE / 2;
                float centerZ = z + GROUND_TILE / 2;
                int cell;
                if (centerZ < -100f) {
                    // Paved under and around the ensemble and where the street arrives, lawn beside it.
                    boolean site = Math.abs(centerX) < WestSide.HALF_WIDTH + 6f && centerZ < WEST_SIDE_FRONT + 10f;
                    cell = site || Math.abs(centerX) < STREET_HALF_WIDTH ? GroundAtlas.STREET : GroundAtlas.LAWN;
                } else if (Math.hypot(centerX, centerZ) < PLAZA_RADIUS) {
                    cell = GroundAtlas.PLAZA;
                } else if (Math.abs(centerX) < STREET_HALF_WIDTH || Math.abs(centerZ) < STREET_HALF_WIDTH) {
                    cell = GroundAtlas.STREET;
                } else {
                    cell = GroundAtlas.LAWN;
                }
                float brightness = ((ix + iz) & 1) == 0 ? 1f : 0.96f;
                Vec3 average = atlas.averages[cell];
                int first = v / 9;
                // Counter-clockwise when seen from above.
                float[][] corners = {{x, z + GROUND_TILE}, {x + GROUND_TILE, z + GROUND_TILE}, {x + GROUND_TILE, z}, {x, z}};
                for (float[] corner : corners) {
                    vertices[v++] = corner[0];
                    vertices[v++] = 0f;
                    vertices[v++] = corner[1];
                    vertices[v++] = cell;
                    vertices[v++] = brightness;
                    vertices[v++] = 0f;
                    vertices[v++] = average.x();
                    vertices[v++] = average.y();
                    vertices[v++] = average.z();
                }
                indices[i++] = first;
                indices[i++] = first + 1;
                indices[i++] = first + 2;
                indices[i++] = first;
                indices[i++] = first + 2;
                indices[i++] = first + 3;
            }
        }
        return graphics.createMesh(vertices, indices, 3, 3, 3);
    }

    private static void fountain(MeshBuilder builder) {
        Vec3 stone = new Vec3(0.86f, 0.84f, 0.90f);
        Vec3 water = new Vec3(0.55f, 0.86f, 0.95f);
        Vec3 glow = new Vec3(0.75f, 0.97f, 1.00f);
        builder.prism(0, 0f, 0.9f, 0, 5.2f, 16, stone, stone);
        builder.prism(0, 0.9f, 1.0f, 0, 4.6f, 16, water, water);
        builder.prism(0, 1.0f, 2.2f, 0, 1.0f, 8, stone, stone);
        // The glowing cube stack of the title picture, as plain boxes.
        builder.box(-1.1f, 2.6f, -1.1f, 1.1f, 4.8f, 1.1f, glow, glow);
        builder.box(-0.6f, 4.8f, -0.6f, 0.6f, 6.0f, 0.6f, glow, glow);
    }

    // ---------------------------------------------------------------- buildings

    /** Builds the walls again, after the district got new building parts. */
    void rebuildFacades(GraphicsDevice graphics, FacadeAtlas facadeAtlas) {
        facades.close();
        Facades.Built walls = Facades.build(graphics, houses, facadeAtlas);
        facades = walls.mesh();
        facadeTriangles = walls.triangles();
    }

    /**
     * The houses: roofs and what stands on them go into the town's mesh, the walls
     * are remembered in {@link #houses} and built by {@link Facades}.
     */
    private int buildings(MeshBuilder builder, Random random) {
        int count = 0;
        float spacing = 14f;
        for (int ix = -6; ix <= 6; ix++) {
            for (int iz = -6; iz <= 6; iz++) {
                if (ix == 0 || iz == 0) {
                    continue; // the streets
                }
                float x = Math.signum(ix) * (STREET_HALF_WIDTH + 8f + (Math.abs(ix) - 1) * spacing);
                float z = Math.signum(iz) * (STREET_HALF_WIDTH + 8f + (Math.abs(iz) - 1) * spacing);
                float half = 4f + random.nextFloat() * 1.5f;
                float height = 6f + random.nextFloat() * 10f + (Math.abs(ix) + Math.abs(iz)) * 0.8f;
                Vec3 wall = WALLS[random.nextInt(WALLS.length)];
                Vec3 roof = ROOFS[random.nextInt(ROOFS.length)];
                int style = random.nextInt(3);
                if (Math.hypot(x, z) < PLAZA_RADIUS + half + 2f) {
                    continue; // keeps the square free
                }
                houses.add(new Facades.House(x, z, half, height, wall, 1000L * ix + iz));
                builder.floor(x - half, z - half, x + half, z + half, height, roof);
                if (style == 0) {
                    builder.pyramid(x, height, z, half + 0.4f, 3.5f, roof);
                } else if (style == 1) {
                    float inner = half * 0.6f;
                    builder.box(x - inner, height, z - inner, x + inner, height + 4f, z + inner, wall, roof);
                }
                // The ledge on top of the ground floor, which stands a little proud of the wall above.
                float out = half + Facades.PLINTH;
                builder.floor(x - out, z - out, x + out, z + out, Facades.GROUND_FLOOR, roof);
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ sprites

    private static void plants(SpriteBatch batch, Random random) {
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * (i + 0.5) / 16;
            float x = (float) Math.cos(angle) * (PLAZA_RADIUS - 2.5f);
            float z = (float) Math.sin(angle) * (PLAZA_RADIUS - 2.5f);
            if (Math.abs(x) < STREET_HALF_WIDTH || Math.abs(z) < STREET_HALF_WIDTH) {
                continue;
            }
            batch.add(i % 2 == 0 ? SpriteAtlas.BLOSSOM_TREE : SpriteAtlas.LAMP, x, z, i % 2 == 0 ? 9f : 5f);
        }
        for (float along = 28f; along < 95f; along += 9f) {
            for (int side = -1; side <= 1; side += 2) {
                float offset = side * (STREET_HALF_WIDTH + 0.6f);
                int kind = random.nextInt(4) == 0 ? SpriteAtlas.GREEN_TREE : SpriteAtlas.BLOSSOM_TREE;
                float height = 7.5f + random.nextFloat() * 2.5f;
                batch.add(kind, offset, along, height);
                batch.add(kind, offset, -along, height);
                batch.add(kind, along, offset, height);
                batch.add(kind, -along, offset, height);
                float lampAt = along + 4.5f;
                batch.add(SpriteAtlas.LAMP, offset * 0.8f, lampAt, 5f);
                batch.add(SpriteAtlas.LAMP, lampAt, offset * 0.8f, 5f);
            }
        }
    }

    /**
     * Peony bushes: a ring around the fountain and one between the trees at the edge
     * of the plaza. The picture fills the lower half of its cell, so a sprite of
     * height h shows a bush about h / 2 high.
     */
    private static void bushes(SpriteBatch batch, Random random) {
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            batch.add(SpriteAtlas.PEONY_BUSH, (float) Math.cos(angle) * 6.1f, (float) Math.sin(angle) * 6.1f,
                    2.6f + random.nextFloat() * 0.8f);
        }
        for (int i = 0; i < 32; i++) {
            double angle = 2 * Math.PI * (i + 0.5) / 32;
            float x = (float) Math.cos(angle) * (PLAZA_RADIUS - 1.2f);
            float z = (float) Math.sin(angle) * (PLAZA_RADIUS - 1.2f);
            if (Math.abs(x) < STREET_HALF_WIDTH + 1f || Math.abs(z) < STREET_HALF_WIDTH + 1f) {
                continue;
            }
            batch.add(SpriteAtlas.PEONY_BUSH, x, z, 3.0f + random.nextFloat() * 1.2f);
        }
    }

    /** Life around the WEST.SIDE ensemble: trees and lamps along the yard, bushes at the entrances, people. */
    private static void westSideSprites(SpriteBatch batch, Random random) {
        float front = WEST_SIDE_FRONT;
        for (int i = 0; i < 6; i++) {
            float z = front - 6f - i * 10f;
            batch.add(i % 2 == 0 ? SpriteAtlas.BLOSSOM_TREE : SpriteAtlas.LAMP, -9.5f, z, i % 2 == 0 ? 8f : 5f);
            batch.add(i % 2 == 0 ? SpriteAtlas.LAMP : SpriteAtlas.BLOSSOM_TREE, 9.5f, z, i % 2 == 0 ? 5f : 8f);
        }
        for (int side = -1; side <= 1; side += 2) { // beside the two entrances in the middle of the yard
            batch.add(SpriteAtlas.PEONY_BUSH, side * 10.6f, front - 26.2f, 3.2f);
            batch.add(SpriteAtlas.PEONY_BUSH, side * 10.6f, front - 35.8f, 3.2f);
        }
        for (int i = 0; i < 4; i++) {
            batch.add(SpriteAtlas.GREEN_TREE, -44f + i * 4f * (i % 2), front + 4f + i * 3f, 7f + i * 0.6f);
            batch.add(SpriteAtlas.GREEN_TREE, 34f + i * 3f, front + 6f - i * 14f, 7.5f);
        }
        for (int i = 0; i < 18; i++) {
            float x = (random.nextFloat() * 2 - 1) * 8f;
            float z = front + 8f - random.nextFloat() * 62f;
            batch.add(SpriteAtlas.FIRST_VISITOR + random.nextInt(SpriteAtlas.VISITOR_COUNT), x, z,
                    1.9f + random.nextFloat() * 0.3f, 0.6f + random.nextFloat() * 1.6f,
                    (random.nextBoolean() ? 1 : -1) * (0.15f + random.nextFloat() * 0.3f), random.nextFloat() * 6.28f);
        }
    }

    private static void visitors(SpriteBatch batch, Random random) {
        for (int i = 0; i < 140; i++) {
            float x;
            float z;
            if (i < 70) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = 7 + random.nextDouble() * (PLAZA_RADIUS - 9);
                x = (float) (Math.cos(angle) * distance);
                z = (float) (Math.sin(angle) * distance);
            } else {
                float along = (random.nextFloat() * 2 - 1) * 90f;
                float across = (random.nextFloat() * 2 - 1) * (STREET_HALF_WIDTH - 1.5f);
                boolean northSouth = random.nextBoolean();
                x = northSouth ? across : along;
                z = northSouth ? along : across;
            }
            float radius = 0.8f + random.nextFloat() * 2.2f;
            float speed = (random.nextBoolean() ? 1 : -1) * (0.15f + random.nextFloat() * 0.35f);
            float phase = random.nextFloat() * 6.28f;
            batch.add(SpriteAtlas.FIRST_VISITOR + random.nextInt(SpriteAtlas.VISITOR_COUNT),
                    x, z, 1.9f + random.nextFloat() * 0.3f, radius, speed, phase);
        }
    }

    /**
     * Sprites as one static mesh. Each vertex carries the sprite's foot point, its
     * own offset from that point and how the sprite strolls in a circle; the vertex
     * shader turns the sprite to the camera and moves it. Nothing is recomputed on
     * the CPU per frame. Layout: center (3), corner (2), texture coordinate (2),
     * motion as radius, speed, phase (3).
     */
    private static final class SpriteBatch {
        private static final int FLOATS_PER_SPRITE = 4 * 10;
        private float[] vertices = new float[FLOATS_PER_SPRITE * 64];
        private int count;

        void add(int cell, float x, float z, float height) {
            add(cell, x, z, height, 0f, 0f, 0f);
        }

        void add(int cell, float x, float z, float height, float radius, float speed, float phase) {
            if ((count + 1) * FLOATS_PER_SPRITE > vertices.length) {
                vertices = Arrays.copyOf(vertices, vertices.length * 2);
            }
            float half = height * SpriteAtlas.CELL_WIDTH / SpriteAtlas.CELL_HEIGHT / 2f;
            float u0 = (float) cell / SpriteAtlas.CELLS;
            float u1 = (float) (cell + 1) / SpriteAtlas.CELLS;
            float[][] corners = { // offset x, offset y, u, v – counter-clockwise for the viewer
                    {-half, 0, u0, 1}, {half, 0, u1, 1}, {half, height, u1, 0}, {-half, height, u0, 0}};
            int offset = count * FLOATS_PER_SPRITE;
            for (float[] corner : corners) {
                vertices[offset++] = x;
                vertices[offset++] = 0f;
                vertices[offset++] = z;
                vertices[offset++] = corner[0];
                vertices[offset++] = corner[1];
                vertices[offset++] = corner[2];
                vertices[offset++] = corner[3];
                vertices[offset++] = radius;
                vertices[offset++] = speed;
                vertices[offset++] = phase;
            }
            count++;
        }

        Mesh build(GraphicsDevice graphics) {
            int[] indices = new int[count * 6];
            for (int i = 0; i < count; i++) {
                int first = i * 4;
                int at = i * 6;
                indices[at] = first;
                indices[at + 1] = first + 1;
                indices[at + 2] = first + 2;
                indices[at + 3] = first;
                indices[at + 4] = first + 2;
                indices[at + 5] = first + 3;
            }
            return graphics.createMesh(Arrays.copyOf(vertices, count * FLOATS_PER_SPRITE), indices, 3, 2, 2, 3);
        }
    }

    // ---------------------------------------------------------------------- sky

    /** A dome seen from inside, coloured from haze at the horizon to blue at the top. Layout: position, color. */
    private static Mesh sky(GraphicsDevice graphics) {
        double[] elevations = {-0.35, 0.0, 0.14, 0.45, Math.PI / 2};
        Vec3[] colors = {HAZE, HAZE, new Vec3(0.82f, 0.80f, 0.96f), new Vec3(0.58f, 0.66f, 0.95f),
                new Vec3(0.42f, 0.54f, 0.92f)};
        int segments = 24;
        float radius = 10f;
        float[] vertices = new float[elevations.length * (segments + 1) * 6];
        int v = 0;
        for (int ring = 0; ring < elevations.length; ring++) {
            for (int segment = 0; segment <= segments; segment++) {
                double angle = 2 * Math.PI * segment / segments;
                vertices[v++] = radius * (float) (Math.cos(elevations[ring]) * Math.cos(angle));
                vertices[v++] = radius * (float) Math.sin(elevations[ring]);
                vertices[v++] = radius * (float) (Math.cos(elevations[ring]) * Math.sin(angle));
                vertices[v++] = colors[ring].x();
                vertices[v++] = colors[ring].y();
                vertices[v++] = colors[ring].z();
            }
        }
        int[] indices = new int[(elevations.length - 1) * segments * 6];
        int i = 0;
        for (int ring = 0; ring < elevations.length - 1; ring++) {
            for (int segment = 0; segment < segments; segment++) {
                int lower0 = ring * (segments + 1) + segment;
                int lower1 = lower0 + 1;
                int upper0 = lower0 + segments + 1;
                int upper1 = upper0 + 1;
                // Counter-clockwise when seen from the inside.
                indices[i++] = lower0;
                indices[i++] = lower1;
                indices[i++] = upper1;
                indices[i++] = lower0;
                indices[i++] = upper1;
                indices[i++] = upper0;
            }
        }
        return graphics.createMesh(vertices, indices, 3, 3);
    }

    @Override
    public void close() {
        sky.close();
        facades.close();
        sprites.close();
        ground.close();
        city.close();
    }
}
