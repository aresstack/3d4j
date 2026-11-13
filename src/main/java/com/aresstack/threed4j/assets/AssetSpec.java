package com.aresstack.threed4j.assets;

/**
 * One piece of artwork the game can take from a file.
 *
 * @param id          file name without extension, e.g. {@code blossom-tree}
 * @param width       pixel width the artwork is rendered at
 * @param height      pixel height the artwork is rendered at
 * @param description what to draw, in English, as handed to the model
 */
public record AssetSpec(String id, int width, int height, String description) {
}
