package com.aresstack.threed4j.assets;

/**
 * One piece of artwork the game can take from a file.
 *
 * @param id          file name without extension, e.g. {@code blossom-tree}
 * @param width       pixel width the artwork is rendered at
 * @param height      pixel height the artwork is rendered at
 * @param description what to draw, in English, as handed to the model
 * @param recipe      how the generator makes it
 */
public record AssetSpec(String id, int width, int height, String description, Recipe recipe) {

    /** How generated artwork comes about. */
    public enum Recipe {
        /** The model draws the whole picture as one SVG. */
        DRAWING,
        /** The model plans a peony and draws its parts; the program assembles a bush from them. */
        FLOWER_BUSH
    }

    public AssetSpec(String id, int width, int height, String description) {
        this(id, width, height, description, Recipe.DRAWING);
    }
}
