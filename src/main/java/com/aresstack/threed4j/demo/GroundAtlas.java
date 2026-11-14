package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;
import com.aresstack.threed4j.math.Vec3;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The textures of the ground side by side in one image: lawn, street, plaza. Each
 * is a square tile that repeats without a seam. Lawn and street come from the
 * district's collection ({@link PartLibrary}), several of each if there are; a
 * built-in tile stands in for a kind the collection has nothing of.
 *
 * <p>Around every tile its own opposite edges are repeated (the gutter), so that
 * smooth filtering at the edge of a tile blends with the pixels it meets when
 * the tile repeats, never with the neighboring tile in the image.</p>
 */
final class GroundAtlas {

    /** What a piece of ground is. */
    enum Surface {
        LAWN(PartLibrary.Kind.LAWN), STREET(PartLibrary.Kind.STREET), PLAZA(null);

        /** The kind of the collection its tiles come from; {@code null} if there is only the built-in tile. */
        final PartLibrary.Kind kind;

        Surface(PartLibrary.Kind kind) {
            this.kind = kind;
        }
    }

    static final int TILE = 256;
    static final int GUTTER = 8;
    static final int CELL = TILE + 2 * GUTTER;

    /**
     * One tile in the atlas.
     *
     * @param number  the tile's number in the collection; 0 for the built-in tile
     * @param average what the ground shows in the distance
     */
    record Cell(int index, int number, Vec3 average) {
    }

    final BufferedImage image;
    final int cellCount;
    private final Map<String, List<Cell>> cells = new HashMap<>();

    private static String key(District district, Surface surface) {
        return district.id() + "/" + surface;
    }

    private GroundAtlas(BufferedImage image, int cellCount) {
        this.image = image;
        this.cellCount = cellCount;
    }

    /** The tile a square of ground takes: the one it rates highest, as figures and houses choose. */
    Cell choose(Surface surface, long seed) {
        return choose(District.BLOSSOM_QUARTER, surface, seed);
    }

    /** As {@link #choose(Surface, long)}, among the tiles of the district the square lies in. */
    Cell choose(District district, Surface surface, long seed) {
        List<Cell> candidates = cells.get(key(district, surface));
        Cell best = candidates.get(0);
        long bestRating = Long.MIN_VALUE;
        for (Cell candidate : candidates) {
            long rating = Facades.rating(seed, candidate.number(), surface.ordinal() + 30);
            if (rating > bestRating) {
                bestRating = rating;
                best = candidate;
            }
        }
        return best;
    }

    /** Paints the atlas from one collection, as the Blütenviertel. */
    static GroundAtlas paint(PartLibrary library) {
        return paint(List.of(District.BLOSSOM_QUARTER), district -> library);
    }

    /** Paints the atlas of the two districts that come with the game. */
    static GroundAtlas paint(TownLibrary town) {
        return paint(District.ALL, town::district);
    }

    /** Paints the atlas of one planned district. */
    static GroundAtlas paint(TownLibrary town, District district) {
        return paint(List.of(district), town::district);
    }

    /** Tiles from the collections where there are any, built-in ones otherwise. */
    private static GroundAtlas paint(List<District> districts, Function<District, PartLibrary> libraries) {
        List<String> keys = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        List<BufferedImage> tiles = new ArrayList<>();
        int collected = 0;
        for (District district : districts) {
            PartLibrary library = libraries.apply(district);
            for (Surface surface : Surface.values()) {
                int before = tiles.size();
                if (surface.kind != null) {
                    for (PartLibrary.Part part : library.parts(surface.kind)) {
                        BufferedImage tile = library.load(part);
                        if (tile != null) {
                            keys.add(key(district, surface));
                            numbers.add(part.number());
                            tiles.add(tile);
                            collected++;
                        }
                    }
                }
                if (tiles.size() == before) {
                    keys.add(key(district, surface));
                    numbers.add(0);
                    tiles.add(builtIn(district, surface));
                }
            }
        }
        BufferedImage image = new BufferedImage(tiles.size() * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        GroundAtlas atlas = new GroundAtlas(image, tiles.size());
        for (String key : keys) {
            atlas.cells.putIfAbsent(key, new ArrayList<>());
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int cell = 0; cell < tiles.size(); cell++) {
            BufferedImage tile = tiles.get(cell);
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
            atlas.cells.get(keys.get(cell)).add(new Cell(cell, numbers.get(cell), average(tile)));
        }
        g.dispose();
        System.out.println("Ground" + (districts.get(0).builtIn() ? "" : " of " + districts.get(0).name()) + ": "
                + collected + " textures from "
                + libraries.apply(districts.get(0)).directory().getParent()
                + (collected == 0 ? ", using the built-in ones" : ""));
        return atlas;
    }

    /** The built-in ground of a planned district: the colour its plan names, with a few lighter and darker specks. */
    private static BufferedImage builtInPlain(int rgb, int seed) {
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = new Color(rgb);
        g.setColor(base);
        g.fillRect(0, 0, TILE, TILE);
        Random random = new Random(300 + seed);
        for (int i = 0; i < 40; i++) {
            double x = 14 + random.nextDouble() * (TILE - 42);
            double y = 14 + random.nextDouble() * (TILE - 38);
            g.setColor(i % 2 == 0 ? base.brighter() : base.darker());
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.22f));
            g.fill(new Ellipse2D.Double(x, y, 8 + random.nextInt(10), 5 + random.nextInt(6)));
        }
        g.dispose();
        return tile;
    }

    /** The built-in ground of the Werksviertel: gravel instead of lawn, dark cobbles as street and yard. */
    private static BufferedImage builtInWorks(Surface surface) {
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random random = new Random(70 + surface.ordinal());
        if (surface == Surface.LAWN) {
            g.setColor(new Color(0xBDB7B0));
            g.fillRect(0, 0, TILE, TILE);
            Color[] pebbles = {new Color(0xCAC4BC), new Color(0xAFA9A3), new Color(0xC2B7A8)};
            for (int i = 0; i < 70; i++) { // pebbles, away from the edges so that the tile repeats without a seam
                double x = 14 + random.nextDouble() * (TILE - 38);
                double y = 14 + random.nextDouble() * (TILE - 36);
                g.setColor(pebbles[i % 3]);
                g.fill(new Ellipse2D.Double(x, y, 6 + random.nextInt(6), 5 + random.nextInt(4)));
            }
        } else {
            g.setColor(new Color(0xA8A3AD));
            g.fillRect(0, 0, TILE, TILE);
            for (int row = 0; row < 6; row++) { // cobbles in rows, shifted like bricks
                for (int column = 0; column < 5; column++) {
                    double x = 10 + column * 48 + (row % 2) * 8;
                    g.setColor(random.nextBoolean() ? new Color(0x9D98A3) : new Color(0xB3AEB8));
                    g.fill(new RoundRectangle2D.Double(x, 10 + row * 40, 36, 30, 12, 12));
                }
            }
        }
        g.dispose();
        return tile;
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
    private static BufferedImage builtIn(District district, Surface surface) {
        if (district == District.WORKS_QUARTER) {
            return builtInWorks(surface);
        }
        if (!district.builtIn()) {
            District.Layout layout = district.layout();
            return builtInPlain(surface == Surface.LAWN ? layout.lawn() : surface == Surface.STREET ? layout.street()
                    : layout.plaza(), surface.ordinal());
        }
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random random = new Random(7 + surface.ordinal());
        switch (surface) {
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
