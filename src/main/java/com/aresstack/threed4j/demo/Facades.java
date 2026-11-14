package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.math.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The walls of the houses with their windows, doors and shop fronts, as one
 * mesh. A wall is a single rectangle; the fragment shader repeats the part's
 * picture along it and lets the wall colour show where the picture is
 * transparent. So a house costs a dozen rectangles however many windows it has,
 * and no pixel is drawn twice.
 *
 * <p>Which parts a house gets follows from the house and the parts there are:
 * every house rates every part with a number that depends only on the two, and
 * takes the part it rates highest. With one part per kind all houses look alike;
 * every part that is added takes over its share of the houses, and the others
 * stay as they were. Floors, window spacing and the place of the door are the
 * house's own as well.</p>
 *
 * <p>Layout of a vertex: position (3), normal (3), wall colour (3), colour seen
 * from far away (3), and the place in the grid of parts: column, row, atlas cell (3).</p>
 */
final class Facades {

    /**
     * A house as far as its walls are concerned.
     *
     * @param half   half the edge length of its square footprint
     * @param height of the walls; the ground floor is the lowest {@link #GROUND_FLOOR} of it
     * @param seed   the house's own number, the source of everything random about it
     */
    record House(float x, float z, float half, float height, Vec3 wall, long seed) {
    }

    /** Height of the ground floor, which stands a little proud of the wall above and is darker. */
    static final float GROUND_FLOOR = 2.6f;
    /** How far the ground floor stands out. */
    static final float PLINTH = 0.15f;

    private static final int FLOATS_PER_VERTEX = 15;

    private float[] vertices = new float[FLOATS_PER_VERTEX * 4 * 512];
    private int quads;

    private Facades() {
    }

    /** The finished mesh and how many triangles it has. */
    record Built(Mesh mesh, int triangles) {
    }

    static Built build(GraphicsDevice graphics, List<House> houses, FacadeAtlas atlas) {
        Facades facades = new Facades();
        for (House house : houses) {
            facades.add(house, atlas);
        }
        int[] indices = new int[facades.quads * 6];
        for (int quad = 0; quad < facades.quads; quad++) {
            int first = quad * 4;
            int at = quad * 6;
            indices[at] = first;
            indices[at + 1] = first + 1;
            indices[at + 2] = first + 2;
            indices[at + 3] = first;
            indices[at + 4] = first + 2;
            indices[at + 5] = first + 3;
        }
        return new Built(graphics.createMesh(Arrays.copyOf(facades.vertices, facades.quads * 4 * FLOATS_PER_VERTEX),
                indices, 3, 3, 3, 3, 3), facades.quads * 2);
    }

    /**
     * The part a house takes: the one it rates highest. The rating depends only on
     * the house and the part's number, so a new part changes only the houses that
     * rate it above what they had.
     */
    static FacadeAtlas.Cell choose(List<FacadeAtlas.Cell> parts, long houseSeed, int salt) {
        FacadeAtlas.Cell best = parts.get(0);
        long bestRating = Long.MIN_VALUE;
        for (FacadeAtlas.Cell part : parts) {
            long rating = rating(houseSeed, part.number(), salt);
            if (rating > bestRating) {
                bestRating = rating;
                best = part;
            }
        }
        return best;
    }

    /** How much the owner of {@code seed} likes picture {@code number}; {@code salt} separates the kinds of choice. */
    static long rating(long seed, int number, int salt) {
        return mix(seed * 0x9E3779B97F4A7C15L + number * 0xC2B2AE3D27D4EB4FL + salt);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }

    private void add(House house, FacadeAtlas atlas) {
        Random random = new Random(house.seed());
        FacadeAtlas.Cell window = choose(atlas.cells(PartLibrary.Kind.WINDOW), house.seed(), 1);
        FacadeAtlas.Cell door = choose(atlas.cells(PartLibrary.Kind.DOOR), house.seed(), 2);
        FacadeAtlas.Cell shop = choose(atlas.cells(PartLibrary.Kind.SHOP), house.seed(), 3);
        // The house's own proportions: how close its windows stand, how high its floors are, whether it has shops.
        float windowSpacing = 2.5f + random.nextFloat() * 0.9f;
        float floorHeight = 2.9f + random.nextFloat() * 0.5f;
        boolean shops = random.nextInt(10) < 7;
        // A door on each side that looks towards one of the two main streets, which cross in the middle of the town.
        int doorSideX = doorSideTowardsX0(house);
        int doorSideZ = doorSideTowardsZ0(house);

        Vec3 plinth = house.wall().scale(0.82f);
        float upper = house.height() - GROUND_FLOOR;
        for (int side = 0; side < 4; side++) {
            float width = 2 * house.half();
            int columns = Math.max(1, Math.round(width / windowSpacing));
            int rows = Math.max(1, Math.round(upper / floorHeight));
            wall(house, side, house.half(), 0f, 1f, GROUND_FLOOR, house.height(), house.wall(), window, columns, rows);

            // Ground floor: shop fronts or windows in wider steps; on the door's side the middle one is the door.
            float out = house.half() + PLINTH;
            int steps = Math.max(1, Math.round(2 * out / 3.1f));
            FacadeAtlas.Cell part = shops ? shop : window;
            if (side != doorSideX && side != doorSideZ) {
                wall(house, side, out, 0f, 1f, 0f, GROUND_FLOOR, plinth, part, steps, 1);
            } else {
                int doorStep = steps / 2;
                for (int step = 0; step < steps; step++) {
                    wall(house, side, out, step / (float) steps, (step + 1) / (float) steps, 0f, GROUND_FLOOR, plinth,
                            step == doorStep ? door : part, 1, 1);
                }
            }
        }
    }

    /** The side of the house (see {@link #wall}) that looks towards the street along x = 0. */
    static int doorSideTowardsX0(House house) {
        return house.x() > 0 ? 0 : 1;
    }

    /** The side of the house that looks towards the street along z = 0. */
    static int doorSideTowardsZ0(House house) {
        return house.z() > 0 ? 2 : 3;
    }

    /**
     * One rectangle of wall on a side of the house (0: -x, 1: +x, 2: -z, 3: +z), from
     * {@code from} to {@code to} along the side (0 to 1, left to right as seen from
     * outside), carrying {@code columns} by {@code rows} copies of a part.
     *
     * @param distance of the wall from the middle of the house
     */
    private void wall(House house, int side, float distance, float from, float to, float minY, float maxY, Vec3 color,
                      FacadeAtlas.Cell part, int columns, int rows) {
        // Left and right end of the whole side as seen from outside, and the direction it faces.
        float leftX;
        float leftZ;
        float rightX;
        float rightZ;
        Vec3 normal;
        switch (side) {
            case 0 -> { // -x: seen from there, z grows to the right
                leftX = rightX = house.x() - distance;
                leftZ = house.z() - distance;
                rightZ = house.z() + distance;
                normal = new Vec3(-1f, 0f, 0f);
            }
            case 1 -> {
                leftX = rightX = house.x() + distance;
                leftZ = house.z() + distance;
                rightZ = house.z() - distance;
                normal = new Vec3(1f, 0f, 0f);
            }
            case 2 -> { // -z: seen from there, x grows to the left
                leftZ = rightZ = house.z() - distance;
                leftX = house.x() + distance;
                rightX = house.x() - distance;
                normal = new Vec3(0f, 0f, -1f);
            }
            default -> {
                leftZ = rightZ = house.z() + distance;
                leftX = house.x() - distance;
                rightX = house.x() + distance;
                normal = new Vec3(0f, 0f, 1f);
            }
        }
        float x0 = leftX + (rightX - leftX) * from;
        float z0 = leftZ + (rightZ - leftZ) * from;
        float x1 = leftX + (rightX - leftX) * to;
        float z1 = leftZ + (rightZ - leftZ) * to;
        // From far away a wall shows the mixture of its own colour and its parts.
        float share = part.coverage();
        Vec3 far = color.scale(1f - share).add(part.averageColor().scale(share));

        if ((quads + 1) * 4 * FLOATS_PER_VERTEX > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        // Counter-clockwise from outside: bottom left, bottom right, top right, top left. The picture's top is row 0.
        vertex(x0, minY, z0, normal, color, far, 0f, rows, part.index());
        vertex(x1, minY, z1, normal, color, far, columns, rows, part.index());
        vertex(x1, maxY, z1, normal, color, far, columns, 0f, part.index());
        vertex(x0, maxY, z0, normal, color, far, 0f, 0f, part.index());
        quads++;
    }

    private int next;

    private void vertex(float x, float y, float z, Vec3 normal, Vec3 color, Vec3 far, float column, float row, int cell) {
        vertices[next++] = x;
        vertices[next++] = y;
        vertices[next++] = z;
        vertices[next++] = normal.x();
        vertices[next++] = normal.y();
        vertices[next++] = normal.z();
        vertices[next++] = color.x();
        vertices[next++] = color.y();
        vertices[next++] = color.z();
        vertices[next++] = far.x();
        vertices[next++] = far.y();
        vertices[next++] = far.z();
        vertices[next++] = column;
        vertices[next++] = row;
        vertices[next++] = cell;
    }
}
