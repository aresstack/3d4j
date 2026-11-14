package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.math.Vec3;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The textures of the ground side by side in one image: lawn, street, plaza. Each
 * is a square tile that repeats without a seam. Tiles in the asset folder
 * ({@link AssetStore}) replace the built-in ones.
 *
 * <p>Around every tile its own opposite edges are repeated (the gutter), so that
 * smooth filtering at the edge of a tile blends with the pixels it meets when
 * the tile repeats, never with the neighboring tile in the image.</p>
 */
final class GroundAtlas {

    static final int LAWN = 0;
    static final int STREET = 1;
    static final int PLAZA = 2;
    static final int CELLS = 3;

    static final int TILE = 256;
    static final int GUTTER = 8;
    static final int CELL = TILE + 2 * GUTTER;

    /** Which asset file replaces which cell; the plaza has none yet. */
    private static final AssetSpec[] CELL_ASSETS = {AssetCatalog.LAWN_TILE, AssetCatalog.STREET_TILE, null};

    final BufferedImage image;
    /** Average colour of each tile: what the ground shows in the distance. */
    final Vec3[] averages = new Vec3[CELLS];

    private GroundAtlas(BufferedImage image) {
        this.image = image;
    }

    /** Paints the atlas; tiles from the asset folder where there are any, built-in ones otherwise. */
    static GroundAtlas paint(AssetStore store) {
        BufferedImage image = new BufferedImage(CELLS * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        GroundAtlas atlas = new GroundAtlas(image);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int replaced = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            BufferedImage tile = CELL_ASSETS[cell] == null ? null : store.load(CELL_ASSETS[cell]);
            if (tile != null) {
                replaced++;
            } else {
                tile = builtIn(cell);
            }
            if (tile.getWidth() != TILE || tile.getHeight() != TILE) {
                BufferedImage scaled = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D s = scaled.createGraphics();
                s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                s.drawImage(tile, 0, 0, TILE, TILE, null);
                s.dispose();
                tile = scaled;
            }
            // The tile nine times, clipped to the cell: the middle one plus the gutter around it.
            Graphics2D clipped = (Graphics2D) g.create(cell * CELL, 0, CELL, CELL);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    clipped.drawImage(tile, GUTTER + dx * TILE, GUTTER + dy * TILE, null);
                }
            }
            clipped.dispose();
            atlas.averages[cell] = average(tile);
        }
        g.dispose();
        System.out.println("Ground: " + replaced + " of " + (CELLS - 1) + " textures from " + store.directory()
                + ", the rest built in");
        return atlas;
    }

    private static Vec3 average(BufferedImage tile) {
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int y = 0; y < tile.getHeight(); y++) {
            for (int x = 0; x < tile.getWidth(); x++) {
                int rgb = tile.getRGB(x, y);
                red += (rgb >> 16) & 0xFF;
                green += (rgb >> 8) & 0xFF;
                blue += rgb & 0xFF;
            }
        }
        float pixels = 255f * tile.getWidth() * tile.getHeight();
        return new Vec3(red / pixels, green / pixels, blue / pixels);
    }

    /** The built-in tiles: the flat colours of the first version with a little texture. */
    private static BufferedImage builtIn(int cell) {
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random random = new Random(7 + cell);
        switch (cell) {
            case LAWN -> {
                g.setColor(new Color(0xA2D08C));
                g.fillRect(0, 0, TILE, TILE);
                for (int i = 0; i < 46; i++) { // tufts, away from the edges so that the tile repeats without a seam
                    double x = 14 + random.nextDouble() * (TILE - 40);
                    double y = 14 + random.nextDouble() * (TILE - 34);
                    g.setColor(i % 3 == 0 ? new Color(0xB4DC9C) : new Color(0x90C27C));
                    g.fill(new Ellipse2D.Double(x, y, 9 + random.nextInt(8), 4 + random.nextInt(3)));
                }
            }
            case STREET -> {
                g.setColor(new Color(0xD2CEDB));
                g.fillRect(0, 0, TILE, TILE);
                for (int row = 0; row < 4; row++) { // paving stones in rows, shifted like bricks
                    for (int column = 0; column < 2; column++) {
                        double x = 8 + column * 124 + (row % 2) * 30;
                        double width = row % 2 == 0 || column == 0 ? 112 : 82;
                        g.setColor(random.nextBoolean() ? new Color(0xC9C5D4) : new Color(0xDAD6E2));
                        g.fill(new RoundRectangle2D.Double(x, 8 + row * 62, width, 52, 10, 10));
                    }
                }
            }
            default -> {
                g.setColor(new Color(0xEEE1D3));
                g.fillRect(0, 0, TILE, TILE);
                g.setColor(new Color(0xE6D8CA));
                for (int i = 0; i < 4; i++) {
                    g.fill(new RoundRectangle2D.Double(10 + (i % 2) * 124, 10 + (i / 2) * 124, 112, 112, 14, 14));
                }
            }
        }
        g.dispose();
        return tile;
    }
}
