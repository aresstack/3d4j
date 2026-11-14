package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary.Kind;
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
    /** The moving water of the fountain: drops and rings. */
    final Mesh water;
    /** The walls of the houses with their windows and doors. */
    final Mesh facades;
    /** The houses of the town, for building {@link #facades}. */
    final List<Facades.House> houses = new ArrayList<>();
    /** What the player cannot walk through: houses, the fountain, tree trunks and lamp posts. */
    final Obstacles obstacles = new Obstacles();
    final int facadeTriangles;
    final Mesh sprites;
    final Mesh sky;
    final int cityTriangles;
    final int groundTriangles = 50 * (50 + WORKS_ROWS) * 2;
    final int spriteCount;
    int buildingCount;

    /**
     * @param groundAtlas the ground textures; their average colours go into the ground mesh
     * @param facadeAtlas the building parts the houses choose from
     * @param spriteAtlas the pictures the trees, lamps, people and bushes choose from
     */
    PlazaScene(GraphicsDevice graphics, GroundAtlas groundAtlas, FacadeAtlas facadeAtlas, SpriteAtlas spriteAtlas) {
        Random random = new Random(21);

        ground = ground(graphics, groundAtlas);
        MeshBuilder builder = new MeshBuilder();
        fountain(builder);
        obstacles.post(0f, 0f, FOUNTAIN_RADIUS, 5.4f);
        buildingCount = buildings(builder, random) + 3;
        westSide(builder);
        List<float[]> worksPlants = new ArrayList<>();
        buildingCount += works(builder, worksPlants);
        city = builder.build(graphics);
        cityTriangles = builder.triangleCount();
        Facades.Built walls = Facades.build(graphics, houses, facadeAtlas);
        facades = walls.mesh();
        facadeTriangles = walls.triangles();

        SpriteBatch batch = new SpriteBatch(spriteAtlas, obstacles);
        plants(batch, random);
        visitors(batch, random);
        bushes(batch, random); // after the rest, so that everything before keeps its place
        westSideSprites(batch, random);
        for (float[] plant : worksPlants) { // last, so that everything before keeps its picture
            batch.add(Kind.values()[(int) plant[0]], plant[1], plant[2], plant[3]);
        }
        sprites = batch.build(graphics);
        spriteCount = batch.count;

        sky = sky(graphics);
        water = fountainWater(graphics);
    }

    // ------------------------------------------------------------------- ground

    /** World z of the open side of the WEST.SIDE ensemble: straight down the street from the start, past the last houses. */
    static final float WEST_SIDE_FRONT = -116f;
    /** Rows of ground squares added beyond the Blütenviertel for the Werksviertel with the ensemble in it. */
    private static final int WORKS_ROWS = 50;

    /**
     * The Easter egg at the end of the street: the WEST.SIDE office ensemble. What
     * surrounds it is the Werksviertel (see {@link #works}).
     */
    private void westSide(MeshBuilder builder) {
        WestSide.build(builder, WEST_SIDE_FRONT);
        WestSide.obstacles(obstacles, WEST_SIDE_FRONT);

    }

    // ------------------------------------------------------------ Werksviertel

    private static final Vec3[] BRICKS = {
            new Vec3(0.72f, 0.38f, 0.33f), new Vec3(0.62f, 0.34f, 0.31f), new Vec3(0.78f, 0.50f, 0.39f),
            new Vec3(0.58f, 0.41f, 0.37f), new Vec3(0.82f, 0.71f, 0.57f)};
    private static final Vec3 TAR = new Vec3(0.36f, 0.34f, 0.39f);
    private static final Vec3 SHED_GLASS = new Vec3(0.56f, 0.74f, 0.80f);

    /**
     * The Werksviertel around the WEST.SIDE ensemble: an old industrial quarter.
     * Brick halls, lower and wider than the houses of the Blütenviertel, with flat or
     * sawtooth roofs, stand wherever the ensemble and its neighbors leave room;
     * a chimney, a water tower and a gasometer rise above them, and the street from
     * the plaza enters through the old works gate on a track.
     *
     * @param plants what grows between the halls, to be added to the sprites: kind, x, z, height
     * @return how many halls were built
     */
    private int works(MeshBuilder builder, List<float[]> plants) {
        Random random = new Random(77); // its own, so that the Blütenviertel stays as it was
        float border = District.WORKS_BORDER_Z;

        // The gate where the street crosses into the quarter, and the track that runs through it.
        Vec3 gateBrick = BRICKS[1];
        for (int side = -1; side <= 1; side += 2) {
            builder.box(side * 7f - 1f, 0f, border - 1f, side * 7f + 1f, 8f, border + 1f, gateBrick, TAR);
            obstacles.box(side * 7f - 1f, border - 1f, side * 7f + 1f, border + 1f, 8f);
        }
        builder.box(-8f, 6.6f, border - 0.6f, 8f, 8.2f, border + 0.6f, TAR, TAR);
        builder.quad(new Vec3(-6f, 6.6f, border - 0.6f), new Vec3(6f, 6.6f, border - 0.6f),
                new Vec3(6f, 6.6f, border + 0.6f), new Vec3(-6f, 6.6f, border + 0.6f), TAR.scale(0.8f));
        Vec3 rail = new Vec3(0.30f, 0.29f, 0.33f);
        Vec3 sleeper = new Vec3(0.47f, 0.36f, 0.30f);
        for (float z = border + 9f; z > WEST_SIDE_FRONT; z -= 1.3f) {
            builder.box(-1.25f, 0f, z - 0.3f, 1.25f, 0.04f, z, sleeper, sleeper);
        }
        builder.box(-0.82f, 0f, WEST_SIDE_FRONT, -0.70f, 0.09f, border + 9.5f, rail, rail);
        builder.box(0.70f, 0f, WEST_SIDE_FRONT, 0.82f, 0.09f, border + 9.5f, rail, rail);

        // Landmarks.
        float[] chimney = {-47f, -146f};
        builder.box(chimney[0] - 2.2f, 0f, chimney[1] - 2.2f, chimney[0] + 2.2f, 4f, chimney[1] + 2.2f, BRICKS[0], TAR);
        builder.prism(chimney[0], 4f, 36f, chimney[1], 1.5f, 10, BRICKS[0], TAR);
        builder.prism(chimney[0], 33.5f, 34.5f, chimney[1], 1.75f, 10, BRICKS[3], BRICKS[3]);
        obstacles.box(chimney[0] - 2.2f, chimney[1] - 2.2f, chimney[0] + 2.2f, chimney[1] + 2.2f, 36f);

        float[] tower = {44f, -216f};
        Vec3 steel = new Vec3(0.42f, 0.45f, 0.50f);
        Vec3 tank = new Vec3(0.56f, 0.66f, 0.62f);
        for (int corner = 0; corner < 4; corner++) {
            float x = tower[0] + (corner % 2 == 0 ? -3f : 3f);
            float z = tower[1] + (corner < 2 ? -3f : 3f);
            builder.box(x - 0.3f, 0f, z - 0.3f, x + 0.3f, 14f, z + 0.3f, steel, steel);
            obstacles.post(x, z, 0.4f, 14f);
        }
        disc(builder, tower[0], 14f, tower[1], 5f, 12, tank.scale(0.75f));
        builder.prism(tower[0], 14f, 20f, tower[1], 5f, 12, tank, tank);
        builder.prism(tower[0], 20f, 20.6f, tower[1], 5.4f, 12, steel, steel);
        builder.prism(tower[0], 20.6f, 22f, tower[1], 1.2f, 8, steel, steel);

        float[] gasometer = {-72f, -212f};
        Vec3 plate = new Vec3(0.55f, 0.63f, 0.61f);
        builder.prism(gasometer[0], 0f, 15f, gasometer[1], 10f, 18, plate, plate.scale(0.9f));
        for (float ring : new float[] {5f, 10f, 14.6f}) {
            builder.prism(gasometer[0], ring, ring + 0.4f, gasometer[1], 10.3f, 18, steel, steel);
        }
        builder.prism(gasometer[0], 15f, 16.2f, gasometer[1], 7f, 18, plate.scale(0.95f), plate);
        obstacles.post(gasometer[0], gasometer[1], 10.3f, 15f);

        // Where no hall may stand: the street, the ensemble, its neighbors, the trees beside it, the landmarks.
        float[][] taken = {{-9f, -1000f, 9f, border}, {-34f, -200f, 34f, -114f}, {30f, -156f, 46f, -104f},
                {-48f, -116f, -28f, border}, {chimney[0] - 4f, chimney[1] - 4f, chimney[0] + 4f, chimney[1] + 4f},
                {tower[0] - 6f, tower[1] - 6f, tower[0] + 6f, tower[1] + 6f},
                {gasometer[0] - 11f, gasometer[1] - 11f, gasometer[0] + 11f, gasometer[1] + 11f}};
        int count = 0;
        for (int ix = 0; ix < 11; ix++) {
            for (int iz = 0; iz < 8; iz++) {
                float half = 5.5f + random.nextFloat() * 2f;
                float x = -90f + ix * 18f + (random.nextFloat() - 0.5f) * 2.5f;
                float z = border - 10.5f - iz * 17.5f + (random.nextFloat() - 0.5f) * 2f;
                float height = 6f + random.nextFloat() * 4.5f;
                Vec3 wall = BRICKS[random.nextInt(BRICKS.length)];
                int roof = random.nextInt(3);
                boolean weeds = random.nextInt(10) < 6;
                int tree = random.nextInt(10);
                float treeHeight = 6.5f + random.nextFloat() * 2.5f;
                boolean free = Math.abs(x) + half < 99.5f && z - half > -247f;
                for (float[] box : taken) {
                    free &= x + half + 1f < box[0] || x - half - 1f > box[2] || z + half + 1f < box[1] || z - half - 1f > box[3];
                }
                if (!free) {
                    continue;
                }
                houses.add(new Facades.House(x, z, half, height, wall, 500_000L + 1000L * ix + iz));
                float foot = half + Facades.PLINTH;
                obstacles.box(x - foot, z - foot, x + foot, z + foot, height);
                builder.floor(x - foot, z - foot, x + foot, z + foot, Facades.GROUND_FLOOR, wall.scale(0.7f));
                if (roof == 0) {
                    sawtoothRoof(builder, x - half, z - half, x + half, z + half, height);
                } else {
                    builder.floor(x - half, z - half, x + half, z + half, height, TAR);
                    if (roof == 1) { // a roof light
                        builder.box(x - half * 0.5f, height, z - half * 0.3f, x + half * 0.5f, height + 1.1f,
                                z + half * 0.3f, SHED_GLASS, TAR);
                    }
                }
                if (weeds) {
                    plants.add(new float[] {Kind.BUSH.ordinal(), x + half + 1.3f, z + half * 0.4f, 2.8f});
                }
                if (tree < 4) {
                    plants.add(new float[] {(tree < 2 ? Kind.BLOSSOM_TREE : Kind.GREEN_TREE).ordinal(),
                            x - half - 2.6f, z - half - 2.6f, treeHeight});
                }
                count++;
            }
        }
        // Light at the gate, growth along the track.
        plants.add(new float[] {Kind.LAMP.ordinal(), -7.6f, border - 4f, 5.5f});
        plants.add(new float[] {Kind.LAMP.ordinal(), 7.6f, border - 12f, 5.5f});
        plants.add(new float[] {Kind.BUSH.ordinal(), -6.2f, border - 9f, 2.6f});
        plants.add(new float[] {Kind.BUSH.ordinal(), 6.4f, border - 5f, 3f});
        plants.add(new float[] {Kind.BLOSSOM_TREE.ordinal(), -12f, border - 6f, 8f});
        return count;
    }

    /** A sawtooth roof: three ridges, each with an upright glazed face and a slope down to the next. */
    static void sawtoothRoof(MeshBuilder builder, float x0, float z0, float x1, float z1, float y) {
        int teeth = 3;
        float width = (x1 - x0) / teeth;
        float rise = 1.9f;
        for (int tooth = 0; tooth < teeth; tooth++) {
            float a = x0 + tooth * width;
            float c = a + width;
            builder.quad(new Vec3(a, y, z0), new Vec3(a, y, z1), new Vec3(a, y + rise, z1), new Vec3(a, y + rise, z0),
                    SHED_GLASS);
            builder.quad(new Vec3(a, y + rise, z1), new Vec3(c, y, z1), new Vec3(c, y, z0), new Vec3(a, y + rise, z0), TAR);
            builder.triangle(new Vec3(a, y, z1), new Vec3(c, y, z1), new Vec3(a, y + rise, z1), TAR.scale(0.85f));
            builder.triangle(new Vec3(c, y, z0), new Vec3(a, y, z0), new Vec3(a, y + rise, z0), TAR.scale(0.85f));
        }
    }

    /** A round plate seen from below, e.g. the bottom of a tank. */
    private static void disc(MeshBuilder builder, float x, float y, float z, float radius, int sides, Vec3 color) {
        Vec3 center = new Vec3(x, y, z);
        for (int i = 0; i < sides; i++) {
            double angle0 = 2 * Math.PI * i / sides;
            double angle1 = 2 * Math.PI * (i + 1) / sides;
            builder.triangle(center,
                    new Vec3(x + radius * (float) Math.cos(angle0), y, z + radius * (float) Math.sin(angle0)),
                    new Vec3(x + radius * (float) Math.cos(angle1), y, z + radius * (float) Math.sin(angle1)), color);
        }
    }

    /**
     * The water the fountain throws: the nozzle on top throws a plume that falls
     * into the small bowl, the small bowl runs over into the wide one and that into
     * the basin, a ring of low jets rises from the basin's rim towards the middle,
     * and where water lands, rings spread on the surface.
     *
     * <p>One quad per drop and per ring. Layout: start point (3), corner of the quad
     * (2), speed at the start (3), timing as phase, duration of a flight and size
     * (3), style as kind and brightness (3). The vertex shader computes where a drop
     * is from the time; a jet is a row of drops that follow each other.</p>
     */
    private static Mesh fountainWater(GraphicsDevice graphics) {
        List<float[]> quads = new ArrayList<>();
        Random random = new Random(5);
        float surface = 1.0f;

        int jets = 14;
        // The plume: the nozzle throws water up, it falls back into the small bowl.
        float nozzle = 5.7f;
        float smallBowl = 5.12f;
        for (int drop = 0; drop < 26; drop++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            float up = 3.6f + random.nextFloat() * 1.6f;
            float flight = flight(up, nozzle - smallBowl);
            float out = (0.25f + random.nextFloat() * 0.55f) / flight;
            quads.add(new float[] {0f, nozzle, 0f, (float) Math.cos(angle) * out, up, (float) Math.sin(angle) * out,
                    random.nextFloat(), flight, 0.13f + random.nextFloat() * 0.06f, 0f, 1f});
        }
        quads.add(new float[] {0f, smallBowl + 0.02f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0.75f, 1f, 1f});
        quads.add(new float[] {0f, smallBowl + 0.02f, 0f, 0f, 0f, 0f, 0.5f, 0.9f, 0.75f, 1f, 1f});

        // The small bowl runs over into the wide one, the wide one into the basin.
        float wideBowl = 3.47f;
        for (int jet = 0; jet < jets; jet++) {
            double high = 2 * Math.PI * (jet + 0.5) / jets;
            float flight = flight(0.5f, 5.0f - wideBowl);
            float out = 0.45f / flight;
            for (int drop = 0; drop < 4; drop++) {
                quads.add(new float[] {(float) Math.cos(high) * 1.2f, 5.0f, (float) Math.sin(high) * 1.2f,
                        (float) Math.cos(high) * out, 0.5f, (float) Math.sin(high) * out,
                        (drop + random.nextFloat() * 0.4f) / 4f, flight, 0.10f + random.nextFloat() * 0.04f, 0f, 1f});
            }
            quads.add(new float[] {(float) Math.cos(high) * 1.65f, wideBowl + 0.02f, (float) Math.sin(high) * 1.65f,
                    0f, 0f, 0f, random.nextFloat(), 0.9f + random.nextFloat() * 0.4f, 0.3f, 1f, 1f});

            double low = 2 * Math.PI * jet / jets;
            flight = flight(0.9f, 3.35f - surface);
            out = 1.3f / flight;
            for (int drop = 0; drop < 6; drop++) {
                quads.add(new float[] {(float) Math.cos(low) * 2.2f, 3.35f, (float) Math.sin(low) * 2.2f,
                        (float) Math.cos(low) * out, 0.9f, (float) Math.sin(low) * out,
                        (drop + random.nextFloat() * 0.4f) / 6f, flight, 0.13f + random.nextFloat() * 0.06f, 0f, 1f});
            }
            quads.add(new float[] {(float) Math.cos(low) * 3.5f, surface + 0.03f, (float) Math.sin(low) * 3.5f,
                    0f, 0f, 0f, random.nextFloat(), 1.1f + random.nextFloat() * 0.5f, 0.5f, 1f, 1f});
        }
        // Small jets from the rim of the basin towards the middle.
        for (int jet = 0; jet < jets; jet++) {
            double angle = 2 * Math.PI * (jet + 0.5) / jets;
            float up = 3.0f;
            float flight = flight(up, 0f);
            float inward = -1.2f / flight;
            for (int drop = 0; drop < 4; drop++) {
                quads.add(new float[] {(float) Math.cos(angle) * 4.3f, surface, (float) Math.sin(angle) * 4.3f,
                        (float) Math.cos(angle) * inward, up, (float) Math.sin(angle) * inward,
                        (drop + random.nextFloat() * 0.3f) / 4f, flight, 0.10f + random.nextFloat() * 0.04f,
                        0f, 1f});
            }
        }

        float[][] corners = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
        // A ring lies flat; seen from above its corners have to run the other way round.
        float[][] flatCorners = {{-1f, 1f}, {1f, 1f}, {1f, -1f}, {-1f, -1f}};
        float[] vertices = new float[quads.size() * 4 * 14];
        int[] indices = new int[quads.size() * 6];
        int v = 0;
        for (int quad = 0; quad < quads.size(); quad++) {
            float[] q = quads.get(quad);
            for (float[] corner : q[9] > 0.5f ? flatCorners : corners) {
                vertices[v++] = q[0];
                vertices[v++] = q[1];
                vertices[v++] = q[2];
                vertices[v++] = corner[0];
                vertices[v++] = corner[1];
                vertices[v++] = q[3];
                vertices[v++] = q[4];
                vertices[v++] = q[5];
                vertices[v++] = q[6];
                vertices[v++] = q[7];
                vertices[v++] = q[8];
                vertices[v++] = q[9];
                vertices[v++] = q[10];
                vertices[v++] = 0f;
            }
            int first = quad * 4;
            int at = quad * 6;
            indices[at] = first;
            indices[at + 1] = first + 1;
            indices[at + 2] = first + 2;
            indices[at + 3] = first;
            indices[at + 4] = first + 2;
            indices[at + 5] = first + 3;
        }
        return graphics.createMesh(vertices, indices, 3, 2, 3, 3, 3);
    }

    /** How long a drop flies that starts upwards at this speed and lands this far below its start. */
    private static float flight(float up, float fall) {
        float gravity = 9.8f; // twice the 4.9 of the water shader
        return (float) ((up + Math.sqrt(up * up + 2 * gravity * fall)) / gravity);
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
        int perSide = 50; // groundTriangles follows from this and WORKS_ROWS
        int rows = perSide + WORKS_ROWS;
        float[] vertices = new float[perSide * rows * 4 * 9];
        int[] indices = new int[perSide * rows * 6];
        int v = 0;
        int i = 0;
        for (int ix = -perSide / 2; ix < perSide / 2; ix++) {
            // The Blütenviertel, and beyond its far edge the Werksviertel.
            for (int iz = -perSide / 2 - WORKS_ROWS; iz < perSide / 2; iz++) {
                float x = ix * GROUND_TILE;
                float z = iz * GROUND_TILE;
                float centerX = x + GROUND_TILE / 2;
                float centerZ = z + GROUND_TILE / 2;
                GroundAtlas.Surface surface;
                District district = District.at(centerX, centerZ);
                if (district == District.WORKS_QUARTER) {
                    // Paved under and around the ensemble and where the street arrives, gravel beside it.
                    boolean site = Math.abs(centerX) < WestSide.HALF_WIDTH + 6f && centerZ < WEST_SIDE_FRONT + 10f;
                    surface = site || Math.abs(centerX) < STREET_HALF_WIDTH
                            ? GroundAtlas.Surface.STREET : GroundAtlas.Surface.LAWN;
                } else if (Math.hypot(centerX, centerZ) < PLAZA_RADIUS) {
                    surface = GroundAtlas.Surface.PLAZA;
                } else if (Math.abs(centerX) < STREET_HALF_WIDTH || Math.abs(centerZ) < STREET_HALF_WIDTH) {
                    surface = GroundAtlas.Surface.STREET;
                } else {
                    surface = GroundAtlas.Surface.LAWN;
                }
                float brightness = ((ix + iz) & 1) == 0 ? 1f : 0.96f;
                // Larger patches than a square share a tile, so that the ground does not look like a quilt.
                GroundAtlas.Cell tile = atlas.choose(district, surface, 1000L * Math.floorDiv(ix, 3) + Math.floorDiv(iz, 3));
                int cell = tile.index();
                Vec3 average = tile.average();
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

    /** Outer radius of the fountain's basin; it stands in the middle of the square. */
    static final float FOUNTAIN_RADIUS = 5.2f;
    private static void fountain(MeshBuilder builder) {
        Vec3 stone = new Vec3(0.86f, 0.84f, 0.90f);
        Vec3 shade = new Vec3(0.74f, 0.72f, 0.80f);
        Vec3 water = new Vec3(0.55f, 0.86f, 0.95f);
        // The basin on the ground.
        builder.prism(0, 0f, 0.9f, 0, FOUNTAIN_RADIUS, 16, stone, stone);
        builder.prism(0, 0.9f, 1.0f, 0, 4.6f, 16, water, water);
        // A foot, a column, a wide bowl, a thinner column, a small bowl and the nozzle.
        builder.prism(0, 1.0f, 1.5f, 0, 1.0f, 8, stone, stone);
        builder.prism(0, 1.5f, 2.7f, 0, 0.55f, 8, stone, stone);
        bowl(builder, 2.7f, 3.25f, 0.55f, 2.2f, 3.45f, stone, shade, water);
        builder.prism(0, 3.45f, 4.55f, 0, 0.38f, 8, stone, stone);
        bowl(builder, 4.55f, 4.95f, 0.38f, 1.2f, 5.1f, stone, shade, water);
        builder.prism(0, 5.1f, 5.7f, 0, 0.16f, 8, stone, stone);
    }

    /**
     * A bowl of the fountain around the middle of the square: it widens from the
     * column below to its rim, and is filled with water almost to the top.
     *
     * @param bottom height where the bowl leaves the column, of radius {@code neck}
     * @param widest height where the bowl has reached its {@code radius}
     * @param top    height of the rim
     */
    private static void bowl(MeshBuilder builder, float bottom, float widest, float neck, float radius, float top,
                             Vec3 stone, Vec3 shade, Vec3 water) {
        int sides = 12;
        for (int i = 0; i < sides; i++) {
            double angle0 = 2 * Math.PI * i / sides;
            double angle1 = 2 * Math.PI * (i + 1) / sides;
            float cos0 = (float) Math.cos(angle0);
            float sin0 = (float) Math.sin(angle0);
            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            // The underside, seen from below and from the side.
            builder.quad(new Vec3(cos1 * neck, bottom, sin1 * neck), new Vec3(cos0 * neck, bottom, sin0 * neck),
                    new Vec3(cos0 * radius, widest, sin0 * radius), new Vec3(cos1 * radius, widest, sin1 * radius), shade);
        }
        builder.prism(0, widest, top, 0, radius, sides, stone, stone);
        builder.prism(0, top, top + 0.02f, 0, radius - 0.18f, sides, water, water);
    }

    // ---------------------------------------------------------------- buildings

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
                float foot = half + Facades.PLINTH;
                obstacles.box(x - foot, z - foot, x + foot, z + foot, height);
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
            batch.add(i % 2 == 0 ? Kind.BLOSSOM_TREE : Kind.LAMP, x, z, i % 2 == 0 ? 9f : 5f);
        }
        for (float along = 28f; along < 95f; along += 9f) {
            for (int side = -1; side <= 1; side += 2) {
                float offset = side * (STREET_HALF_WIDTH + 0.6f);
                Kind kind = random.nextInt(4) == 0 ? Kind.GREEN_TREE : Kind.BLOSSOM_TREE;
                float height = 7.5f + random.nextFloat() * 2.5f;
                batch.add(kind, offset, along, height);
                batch.add(kind, offset, -along, height);
                batch.add(kind, along, offset, height);
                batch.add(kind, -along, offset, height);
                float lampAt = along + 4.5f;
                batch.add(Kind.LAMP, offset * 0.8f, lampAt, 5f);
                batch.add(Kind.LAMP, lampAt, offset * 0.8f, 5f);
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
            batch.add(Kind.BUSH, (float) Math.cos(angle) * 6.1f, (float) Math.sin(angle) * 6.1f,
                    2.6f + random.nextFloat() * 0.8f);
        }
        for (int i = 0; i < 32; i++) {
            double angle = 2 * Math.PI * (i + 0.5) / 32;
            float x = (float) Math.cos(angle) * (PLAZA_RADIUS - 1.2f);
            float z = (float) Math.sin(angle) * (PLAZA_RADIUS - 1.2f);
            if (Math.abs(x) < STREET_HALF_WIDTH + 1f || Math.abs(z) < STREET_HALF_WIDTH + 1f) {
                continue;
            }
            batch.add(Kind.BUSH, x, z, 3.0f + random.nextFloat() * 1.2f);
        }
    }

    /** Life around the WEST.SIDE ensemble: trees and lamps along the yard, bushes at the entrances, people. */
    private static void westSideSprites(SpriteBatch batch, Random random) {
        float front = WEST_SIDE_FRONT;
        for (int i = 0; i < 6; i++) {
            float z = front - 6f - i * 10f;
            batch.add(i % 2 == 0 ? Kind.BLOSSOM_TREE : Kind.LAMP, -9.5f, z, i % 2 == 0 ? 8f : 5f);
            batch.add(i % 2 == 0 ? Kind.LAMP : Kind.BLOSSOM_TREE, 9.5f, z, i % 2 == 0 ? 5f : 8f);
        }
        for (int side = -1; side <= 1; side += 2) { // beside the two entrances in the middle of the yard
            batch.add(Kind.BUSH, side * 10.6f, front - 26.2f, 3.2f);
            batch.add(Kind.BUSH, side * 10.6f, front - 35.8f, 3.2f);
        }
        for (int i = 0; i < 4; i++) {
            batch.add(Kind.GREEN_TREE, -44f + i * 4f * (i % 2), front + 4f + i * 3f, 7f + i * 0.6f);
            batch.add(Kind.GREEN_TREE, 34f + i * 3f, front + 6f - i * 14f, 7.5f);
        }
        for (int i = 0; i < 18; i++) {
            float x = (random.nextFloat() * 2 - 1) * 8f;
            float z = front + 8f - random.nextFloat() * 62f;
            batch.add(Kind.VISITOR, random.nextInt(3), x, z,
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
            // Nobody strolls through the fountain: the whole circle stays outside of it.
            double fromMiddle = Math.hypot(x, z);
            float nearest = FOUNTAIN_RADIUS + 0.9f + radius;
            if (fromMiddle < nearest) {
                double angle = fromMiddle < 0.01 ? random.nextDouble() * 2 * Math.PI : Math.atan2(z, x);
                x = (float) (Math.cos(angle) * nearest);
                z = (float) (Math.sin(angle) * nearest);
            }
            float speed = (random.nextBoolean() ? 1 : -1) * (0.15f + random.nextFloat() * 0.35f);
            float phase = random.nextFloat() * 6.28f;
            batch.add(Kind.VISITOR, random.nextInt(3),
                    x, z, 1.9f + random.nextFloat() * 0.3f, radius, speed, phase);
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
        water.close();
        facades.close();
        sprites.close();
        ground.close();
        city.close();
    }
}
