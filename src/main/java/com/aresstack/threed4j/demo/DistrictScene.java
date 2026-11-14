package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary.Kind;
import com.aresstack.threed4j.assets.TownLibrary;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.gfx.Texture;
import com.aresstack.threed4j.gfx.Textures;
import com.aresstack.threed4j.math.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A planned district as the game shows it: built from the district's plan, with
 * the pictures of its collection. Every planned district is a piece of the world
 * of its own (four meshes, three textures), so that the game draws only the
 * districts near the player and builds only the one again whose pictures or
 * plan changed.
 *
 * <p>The layout is the same for all: two streets cross in the middle of the
 * plot and run on into the neighboring plots, the landmark stands on a square
 * where they cross, houses stand in a grid between them. What differs comes
 * from the plan: colours, sizes and roofs of the houses, the landmark, and what
 * the pictures show.</p>
 */
final class DistrictScene implements AutoCloseable {

    private static final float STREET_HALF_WIDTH = 5f;
    private static final float HALF_PLOT = District.PLOT / 2;

    final District district;
    /** What the scene was built from; see {@link TownLibrary#signature(District)}. */
    final String signature;
    final Mesh city;
    final Mesh facades;
    final Mesh ground;
    final Mesh sprites;
    final Texture spriteTexture;
    final Texture groundTexture;
    final Texture facadeTexture;
    /** For the ground and the facade shader: 1 / cells, gutter and tile as parts of a cell. */
    final Vec3 groundAtlasLayout;
    final Vec3 facadeAtlasLayout;
    final Obstacles obstacles = new Obstacles();
    final int triangles;
    final int houseCount;

    /** The three pictures a district scene is built from: painting them takes long and needs no graphics. */
    record Painted(SpriteAtlas sprites, GroundAtlas ground, FacadeAtlas facades,
                   GraphicsDevice.PendingTexture spriteTexture, GraphicsDevice.PendingTexture groundTexture,
                   GraphicsDevice.PendingTexture facadeTexture) {
        static Painted of(TownLibrary town, District district) {
            return new Painted(SpriteAtlas.paint(town, district), GroundAtlas.paint(town, district),
                    FacadeAtlas.paint(town, district), null, null, null);
        }

        /** Painted and with the textures prepared, all of it away from the render thread. */
        static Painted prepared(GraphicsDevice graphics, TownLibrary town, District district) {
            Painted painted = of(town, district);
            return new Painted(painted.sprites, painted.ground, painted.facades,
                    Textures.prepare(graphics, painted.sprites.image, true),
                    Textures.prepare(graphics, painted.ground.image, true),
                    Textures.prepare(graphics, painted.facades.image, true));
        }
    }

    DistrictScene(GraphicsDevice graphics, TownLibrary town, District district, String signature) {
        this(graphics, Painted.of(town, district), district, signature);
    }

    /** Builds the scene from pictures painted before, e.g. on another thread. */
    DistrictScene(GraphicsDevice graphics, Painted painted, District district, String signature) {
        this.district = district;
        this.signature = signature;
        District.Layout layout = district.layout();
        float centerX = district.centerX();
        float centerZ = district.centerZ();
        Random random = new Random(district.id().hashCode());

        SpriteAtlas spriteAtlas = painted.sprites();
        GroundAtlas groundAtlas = painted.ground();
        FacadeAtlas facadeAtlas = painted.facades();
        spriteTexture = painted.spriteTexture() != null ? painted.spriteTexture().finish()
                : Textures.fromImage(graphics, spriteAtlas.image, true);
        groundTexture = painted.groundTexture() != null ? painted.groundTexture().finish()
                : Textures.fromImage(graphics, groundAtlas.image, true);
        facadeTexture = painted.facadeTexture() != null ? painted.facadeTexture().finish()
                : Textures.fromImage(graphics, facadeAtlas.image, true);
        groundAtlasLayout = new Vec3(1f / groundAtlas.cellCount, (float) GroundAtlas.GUTTER / GroundAtlas.CELL,
                (float) GroundAtlas.TILE / GroundAtlas.CELL);
        facadeAtlasLayout = new Vec3(1f / facadeAtlas.cellCount, (float) FacadeAtlas.GUTTER / FacadeAtlas.CELL,
                (float) FacadeAtlas.TILE / FacadeAtlas.CELL);

        District.Landmark landmark = layout.landmark();
        float foot = landmark == null ? 0f : landmark.footRadius();
        float plaza = Math.max(12f, Math.min(22f, foot + 7f));

        ground = ground(graphics, groundAtlas, centerX, centerZ, plaza);

        MeshBuilder builder = new MeshBuilder();
        List<Facades.House> houses = new ArrayList<>();
        // Rows of houses on both sides of each street, as far as the plot reaches.
        List<Float> rows = new ArrayList<>();
        for (float at = STREET_HALF_WIDTH + 3f + layout.maxHalf(); at + layout.maxHalf() <= HALF_PLOT - 3f; at += layout.spacing()) {
            rows.add(at);
        }
        for (int ix = 0; ix < rows.size() * 2; ix++) {
            for (int iz = 0; iz < rows.size() * 2; iz++) {
                float dx = (ix % 2 == 0 ? 1 : -1) * rows.get(ix / 2);
                float dz = (iz % 2 == 0 ? 1 : -1) * rows.get(iz / 2);
                float half = layout.minHalf() + random.nextFloat() * (layout.maxHalf() - layout.minHalf());
                float height = layout.minHeight() + random.nextFloat() * (layout.maxHeight() - layout.minHeight());
                Vec3 wall = Landmarks.color(layout.walls().get(random.nextInt(layout.walls().size())));
                Vec3 roof = Landmarks.color(layout.roofs().get(random.nextInt(layout.roofs().size())));
                String shape = layout.roofShapes().get(random.nextInt(layout.roofShapes().size()));
                if (Math.hypot(dx, dz) < plaza + half * 1.5f + 2f) {
                    continue; // keeps the square free
                }
                float x = centerX + dx;
                float z = centerZ + dz;
                if (!District.onLand(x + half, z - half, 11f) || !District.onLand(x + half, z + half, 11f)
                        || !District.onLand(x + half, z, 11f)) {
                    continue; // nothing is built on the water or on the promenade along it
                }
                houses.add(new Facades.House(x, z, half, height, wall, 1000L * ix + iz));
                float out = half + Facades.PLINTH;
                obstacles.box(x - out, z - out, x + out, z + out, height);
                builder.floor(x - out, z - out, x + out, z + out, Facades.GROUND_FLOOR, roof);
                switch (shape) {
                    case "pyramid" -> {
                        builder.floor(x - half, z - half, x + half, z + half, height, roof);
                        builder.pyramid(x, height, z, half + 0.4f, 2.5f + half * 0.25f, roof);
                    }
                    case "sawtooth" -> PlazaScene.sawtoothRoof(builder, x - half, z - half, x + half, z + half, height);
                    case "attic" -> {
                        builder.floor(x - half, z - half, x + half, z + half, height, roof);
                        float inner = half * 0.6f;
                        builder.box(x - inner, height, z - inner, x + inner, height + 3.2f, z + inner, wall, roof);
                    }
                    default -> builder.floor(x - half, z - half, x + half, z + half, height, roof);
                }
            }
        }
        if (District.coastal(layout.plotX())) {
            promenade(builder, centerZ, Landmarks.color(layout.plaza()));
        }
        if (landmark != null) {
            Landmarks.build(builder, landmark, centerX, centerZ);
            obstacles.post(centerX, centerZ, foot, landmark.height());
        }
        city = builder.build(graphics);
        Facades.Built walls = Facades.build(graphics, houses, facadeAtlas, district);
        facades = walls.mesh();
        houseCount = houses.size();

        // Figures stand on land only; what the layout puts on the water is left out.
        SpriteBatch batch = new SpriteBatch(spriteAtlas, obstacles, district) {
            @Override
            void add(Kind kind, float x, float z, float height, float radius, float speed, float phase) {
                if (District.onLand(x + radius, z, 2f) && District.onLand(x + radius, z + radius, 2f)
                        && District.onLand(x + radius, z - radius, 2f)) {
                    super.add(kind, x, z, height, radius, speed, phase);
                }
            }
        };
        for (int i = 0; i < 24; i++) { // the district's animals, among the people
            float radius = 0.6f + random.nextFloat() * 1.8f;
            float dx;
            float dz;
            if (i < 9) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = foot + 1.5 + radius + random.nextDouble() * Math.max(1f, plaza - foot - 4f);
                dx = (float) (Math.cos(angle) * distance);
                dz = (float) (Math.sin(angle) * distance);
            } else {
                float along = (random.nextBoolean() ? 1 : -1) * (plaza + random.nextFloat() * (HALF_PLOT - plaza - 6f));
                float across = (random.nextFloat() * 2 - 1) * (STREET_HALF_WIDTH - 2.4f);
                boolean northSouth = random.nextBoolean();
                dx = northSouth ? across : along;
                dz = northSouth ? along : across;
            }
            batch.add(Kind.ANIMAL, centerX + dx, centerZ + dz, 1.1f + random.nextFloat() * 0.6f, radius,
                    (random.nextBoolean() ? 1 : -1) * (0.2f + random.nextFloat() * 0.5f), random.nextFloat() * 6.28f);
        }
        if (District.coastal(layout.plotX())) {
            // What lives in the sea swims in circles off the shore, its lowest part under water.
            for (int i = 0; i < 16; i++) {
                float z = centerZ + (random.nextFloat() * 2 - 1) * (HALF_PLOT - 8f);
                float radius = 2f + random.nextFloat() * 5f;
                float x = District.coastX(z) + 4f + radius + random.nextFloat() * 26f;
                float height = 2.4f + random.nextFloat() * 1.4f;
                batch.place(Kind.SEA_ANIMAL, x, Sea.LEVEL - height * 0.24f, z, height, radius,
                        (random.nextBoolean() ? 1 : -1) * (0.08f + random.nextFloat() * 0.2f), random.nextFloat() * 6.28f);
            }
            for (float z = centerZ - HALF_PLOT + 6f; z < centerZ + HALF_PLOT; z += 15f) { // lamps along the promenade
                if (Math.hypot(District.coastX(z) - centerX, z - centerZ) > foot + 5f) {
                    batch.add(Kind.LAMP, District.coastX(z) - 2.4f, z, 5f);
                }
            }
        }
        for (float along = plaza + 5f; along < HALF_PLOT - 4f; along += 9f) {
            for (int side = -1; side <= 1; side += 2) {
                float offset = side * (STREET_HALF_WIDTH + 0.6f);
                Kind kind = random.nextInt(4) == 0 ? Kind.GREEN_TREE : Kind.BLOSSOM_TREE;
                float height = 7.5f + random.nextFloat() * 2.5f;
                batch.add(kind, centerX + offset, centerZ + along, height);
                batch.add(kind, centerX + offset, centerZ - along, height);
                batch.add(kind, centerX + along, centerZ + offset, height);
                batch.add(kind, centerX - along, centerZ + offset, height);
                float lampAt = along + 4.5f;
                if (lampAt < HALF_PLOT - 2f) {
                    batch.add(Kind.LAMP, centerX + offset * 0.8f, centerZ + side * lampAt, 5f);
                    batch.add(Kind.LAMP, centerX + side * lampAt, centerZ + offset * 0.8f, 5f);
                }
            }
        }
        for (int i = 0; i < 16; i++) { // around the square, leaving the streets open
            double angle = 2 * Math.PI * (i + 0.5) / 16;
            float dx = (float) Math.cos(angle) * (plaza - 1.3f);
            float dz = (float) Math.sin(angle) * (plaza - 1.3f);
            if (Math.abs(dx) > STREET_HALF_WIDTH + 1f && Math.abs(dz) > STREET_HALF_WIDTH + 1f) {
                batch.add(Kind.BUSH, centerX + dx, centerZ + dz, 2.8f + random.nextFloat() * 0.8f);
            }
        }
        for (int i = 0; i < 60; i++) {
            float dx;
            float dz;
            if (i < 25) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = foot + 3 + random.nextDouble() * Math.max(1f, plaza - foot - 4f);
                dx = (float) (Math.cos(angle) * distance);
                dz = (float) (Math.sin(angle) * distance);
            } else {
                float along = (random.nextFloat() * 2 - 1) * (HALF_PLOT - 6f);
                float across = (random.nextFloat() * 2 - 1) * (STREET_HALF_WIDTH - 1.5f);
                boolean northSouth = random.nextBoolean();
                dx = northSouth ? across : along;
                dz = northSouth ? along : across;
            }
            float radius = 0.8f + random.nextFloat() * 2.2f;
            // Nobody strolls through the landmark: the whole circle stays outside of it.
            double fromMiddle = Math.hypot(dx, dz);
            float nearest = foot + 0.9f + radius;
            if (fromMiddle < nearest) {
                double angle = fromMiddle < 0.01 ? random.nextDouble() * 2 * Math.PI : Math.atan2(dz, dx);
                dx = (float) (Math.cos(angle) * nearest);
                dz = (float) (Math.sin(angle) * nearest);
            }
            batch.add(Kind.VISITOR, centerX + dx, centerZ + dz, 1.9f + random.nextFloat() * 0.3f, radius,
                    (random.nextBoolean() ? 1 : -1) * (0.15f + random.nextFloat() * 0.35f), random.nextFloat() * 6.28f);
        }
        sprites = batch.build(graphics);
        triangles = builder.triangleCount() + walls.triangles() + 50 * 50 * 2; // the ground at most
    }

    /** Squares of ground over the whole plot: the square in the middle, the two streets, and the rest. */
    private Mesh ground(GraphicsDevice graphics, GroundAtlas atlas, float centerX, float centerZ, float plaza) {
        int perSide = Math.round(District.PLOT / PlazaScene.GROUND_TILE);
        float tileSize = PlazaScene.GROUND_TILE;
        float[] vertices = new float[perSide * perSide * 4 * 9];
        int[] indices = new int[perSide * perSide * 6];
        int v = 0;
        int i = 0;
        for (int ix = 0; ix < perSide; ix++) {
            for (int iz = 0; iz < perSide; iz++) {
                float x = centerX - HALF_PLOT + ix * tileSize;
                float z = centerZ - HALF_PLOT + iz * tileSize;
                float dx = x + tileSize / 2 - centerX;
                float dz = z + tileSize / 2 - centerZ;
                // No ground on the water; the promenade covers the stepped edge this leaves.
                if (!District.onLand(x + tileSize, z, 1.5f) || !District.onLand(x + tileSize, z + tileSize, 1.5f)) {
                    continue;
                }
                GroundAtlas.Surface surface = Math.hypot(dx, dz) < plaza ? GroundAtlas.Surface.PLAZA
                        : Math.abs(dx) < STREET_HALF_WIDTH || Math.abs(dz) < STREET_HALF_WIDTH ? GroundAtlas.Surface.STREET
                        : GroundAtlas.Surface.LAWN;
                float brightness = ((ix + iz) & 1) == 0 ? 1f : 0.96f;
                GroundAtlas.Cell tile = atlas.choose(district, surface, 1000L * (ix / 3) + iz / 3);
                Vec3 average = tile.average();
                int first = v / 9;
                float[][] corners = {{x, z + tileSize}, {x + tileSize, z + tileSize}, {x + tileSize, z}, {x, z}};
                for (float[] corner : corners) {
                    vertices[v++] = corner[0];
                    vertices[v++] = 0f;
                    vertices[v++] = corner[1];
                    vertices[v++] = tile.index();
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
        return graphics.createMesh(java.util.Arrays.copyOf(vertices, v), java.util.Arrays.copyOf(indices, i), 3, 3, 3);
    }

    /**
     * The promenade of a district at the sea: a paved band that follows the shore
     * in its curve, with the quay wall down to the water and a low kerb at the edge.
     */
    private static void promenade(MeshBuilder builder, float centerZ, Vec3 paving) {
        float width = 10f;
        float step = 2f;
        Vec3 wall = paving.scale(0.78f);
        Vec3 kerb = paving.scale(0.9f);
        for (float z = centerZ - HALF_PLOT; z < centerZ + HALF_PLOT; z += step) {
            float shore0 = District.coastX(z);
            float shore1 = District.coastX(z + step);
            float top = 0.06f;
            builder.quad(new Vec3(shore1 - width, top, z + step), new Vec3(shore1, top, z + step),
                    new Vec3(shore0, top, z), new Vec3(shore0 - width, top, z), paving);
            // The kerb: a low step along the edge, its top and the side towards the land.
            float edge = 0.32f;
            builder.quad(new Vec3(shore1 - 0.5f, edge, z + step), new Vec3(shore1, edge, z + step),
                    new Vec3(shore0, edge, z), new Vec3(shore0 - 0.5f, edge, z), kerb);
            builder.quad(new Vec3(shore0 - 0.5f, top, z), new Vec3(shore1 - 0.5f, top, z + step),
                    new Vec3(shore1 - 0.5f, edge, z + step), new Vec3(shore0 - 0.5f, edge, z), wall);
            // The quay wall down to the water.
            builder.quad(new Vec3(shore1, Sea.LEVEL, z + step), new Vec3(shore0, Sea.LEVEL, z),
                    new Vec3(shore0, edge, z), new Vec3(shore1, edge, z + step), wall);
        }
    }

    /** How far a place is from the district's plot; 0 inside it. */
    float distance(float x, float z) {
        float dx = Math.max(0f, Math.abs(x - district.centerX()) - HALF_PLOT);
        float dz = Math.max(0f, Math.abs(z - district.centerZ()) - HALF_PLOT);
        return (float) Math.hypot(dx, dz);
    }

    @Override
    public void close() {
        city.close();
        facades.close();
        ground.close();
        sprites.close();
        spriteTexture.close();
        groundTexture.close();
        facadeTexture.close();
    }
}
