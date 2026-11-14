package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary.Kind;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;

import java.util.Arrays;

/**
 * Sprites as one static mesh. Each vertex carries the sprite's foot point, its
 * own offset from that point and how the sprite strolls in a circle; the vertex
 * shader turns the sprite to the camera and moves it. Nothing is recomputed on
 * the CPU per frame. Layout: center (3), corner (2), texture coordinate (2),
 * motion as radius, speed, phase (3).
 */
class SpriteBatch {
    private static final int FLOATS_PER_SPRITE = 4 * 10;
    private float[] vertices = new float[FLOATS_PER_SPRITE * 64];
    int count;

    private final SpriteAtlas atlas;
    private final Obstacles obstacles;
    private final District fixed;

    /** A batch for the two districts that come with the game: a figure belongs to the district it stands in. */
    SpriteBatch(SpriteAtlas atlas, Obstacles obstacles) {
        this(atlas, obstacles, null);
    }

    /** @param district the district all figures of the batch belong to; {@code null}: the one each stands in */
    SpriteBatch(SpriteAtlas atlas, Obstacles obstacles, District district) {
        this.atlas = atlas;
        this.obstacles = obstacles;
        this.fixed = district;
    }

    void add(Kind kind, float x, float z, float height) {
        add(kind, x, z, height, 0f, 0f, 0f);
    }

    /** @param unused a random number drawn at this point by earlier versions; drawing it keeps everybody in place */
    void add(Kind kind, int unused, float x, float z, float height, float radius, float speed, float phase) {
        add(kind, x, z, height, radius, speed, phase);
    }

    /**
     * Adds a figure. Which picture of its kind it shows is its own choice among the
     * pictures there are (see {@link SpriteAtlas#choose}); the figure's number in
     * the scene is what makes it differ from the others.
     */
    void add(Kind kind, float x, float z, float height, float radius, float speed, float phase) {
        place(kind, x, 0f, z, height, radius, speed, phase);
    }

    /** As {@link #add}, with the height the figure's foot is at: 0 is the ground, below it e.g. the sea. */
    final void place(Kind kind, float x, float y, float z, float height, float radius, float speed, float phase) {
        if ((count + 1) * FLOATS_PER_SPRITE > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        // Trunks and posts are in the way; the crown above them is only a picture.
        if (kind == Kind.BLOSSOM_TREE || kind == Kind.GREEN_TREE) {
            obstacles.post(x, z, 0.3f, height * 0.5f);
        } else if (kind == Kind.LAMP) {
            obstacles.post(x, z, 0.15f, height);
        }
        District district = fixed != null ? fixed : District.at(x, z);
        if (kind == Kind.BUSH && !district.flowerBushes()) {
            height *= 0.7f; // a flower bush fills the lower half of its picture, wild growth all of it
        }
        SpriteAtlas.Cell cell = atlas.choose(district, kind, count);
        float half = height * SpriteAtlas.CELL_WIDTH / SpriteAtlas.CELL_HEIGHT / 2f;
        float u0 = (float) cell.column() / atlas.columns;
        float u1 = (float) (cell.column() + 1) / atlas.columns;
        float v0 = (float) cell.row() / atlas.rows;
        float v1 = (float) (cell.row() + 1) / atlas.rows;
        float[][] corners = { // offset x, offset y, u, v – counter-clockwise for the viewer
                {-half, 0, u0, v1}, {half, 0, u1, v1}, {half, height, u1, v0}, {-half, height, u0, v0}};
        int offset = count * FLOATS_PER_SPRITE;
        for (float[] corner : corners) {
            vertices[offset++] = x;
            vertices[offset++] = y;
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
