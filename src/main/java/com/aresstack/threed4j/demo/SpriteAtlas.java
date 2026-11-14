package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;

import java.awt.BasicStroke;
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

/**
 * The pictures of everything that stands in the town as a flat figure (trees,
 * lamps, people, flower bushes), side by side in one image. They come from the
 * district's collection ({@link PartLibrary}); for a kind the collection has no
 * picture of yet, built-in placeholders painted with Java 2D stand in.
 */
final class SpriteAtlas {

    static final int CELL_WIDTH = 128;
    static final int CELL_HEIGHT = 256;
    /** Cells per row of the image; more pictures go into further rows. */
    private static final int COLUMNS = 16;

    /** The kinds of the collection that are figures. */
    static final List<PartLibrary.Kind> KINDS = List.of(PartLibrary.Kind.BLOSSOM_TREE, PartLibrary.Kind.GREEN_TREE,
            PartLibrary.Kind.LAMP, PartLibrary.Kind.VISITOR, PartLibrary.Kind.BUSH);

    /**
     * One picture in the atlas.
     *
     * @param number the picture's number in the collection; 0 and below for built-in placeholders
     */
    record Cell(int column, int row, int number) {
    }

    private static final Color OUTLINE = new Color(0x3B2A4A);
    private static final BasicStroke PEN = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    final BufferedImage image;
    final int columns;
    final int rows;
    /** The pictures by district and kind; those of a shared kind are the same list for every district. */
    private final Map<String, List<Cell>> cells = new HashMap<>();

    private static String key(District district, PartLibrary.Kind kind) {
        return (kind.shared() ? "" : district.id()) + "/" + kind;
    }

    private SpriteAtlas(BufferedImage image, int columns, int rows) {
        this.image = image;
        this.columns = columns;
        this.rows = rows;
    }

    /** The pictures of this kind in the Blütenviertel; never empty. */
    List<Cell> cells(PartLibrary.Kind kind) {
        return cells(District.BLOSSOM_QUARTER, kind);
    }

    /** The pictures of this kind in a district; never empty. */
    List<Cell> cells(District district, PartLibrary.Kind kind) {
        return cells.get(key(district, kind));
    }

    /**
     * The picture a figure takes: the one it rates highest. The rating depends only
     * on the figure and the picture's number, so a new picture changes only the
     * figures that rate it above what they had (as {@link Facades#choose} for houses).
     */
    Cell choose(PartLibrary.Kind kind, long seed) {
        return choose(District.BLOSSOM_QUARTER, kind, seed);
    }

    /** As {@link #choose(PartLibrary.Kind, long)}, among the pictures of the district the figure stands in. */
    Cell choose(District district, PartLibrary.Kind kind, long seed) {
        List<Cell> candidates = cells.get(key(district, kind));
        Cell best = candidates.get(0);
        long bestRating = Long.MIN_VALUE;
        for (Cell candidate : candidates) {
            long rating = Facades.rating(seed, candidate.number(), kind.ordinal() + 10);
            if (rating > bestRating) {
                bestRating = rating;
                best = candidate;
            }
        }
        return best;
    }

    /** Paints the atlas from one collection that holds everything, as the Blütenviertel. */
    static SpriteAtlas paint(PartLibrary library) {
        return paint(List.of(District.BLOSSOM_QUARTER), district -> library, library);
    }

    /** Paints the atlas of the two districts that come with the game, and the people the town shares. */
    static SpriteAtlas paint(TownLibrary town) {
        return paint(District.ALL, town::district, town.shared());
    }

    /** Paints the atlas of one planned district: its figures, and the people the town shares. */
    static SpriteAtlas paint(TownLibrary town, District district) {
        return paint(List.of(district), town::district, town.shared());
    }

    /** Placeholders stand in for every kind a collection has nothing of. */
    private static SpriteAtlas paint(List<District> districts, Function<District, PartLibrary> libraries,
                                     PartLibrary shared) {
        List<String> keys = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        List<BufferedImage> pictures = new ArrayList<>();
        int collected = 0;
        java.util.Set<String> done = new java.util.HashSet<>();
        for (District district : districts) {
            for (PartLibrary.Kind kind : KINDS) {
                String key = key(district, kind);
                if (!done.add(key)) {
                    continue; // a shared kind is painted once
                }
                PartLibrary library = kind.shared() ? shared : libraries.apply(district);
                int before = pictures.size();
                // Of a uniform kind (street lamps) the district shows only the one in use.
                List<PartLibrary.Part> shown = library.parts(kind);
                if (kind.uniform() && !shown.isEmpty()) {
                    shown = List.of(library.chosen(kind));
                }
                for (PartLibrary.Part part : shown) {
                    BufferedImage picture = library.load(part);
                    if (picture != null) {
                        keys.add(key);
                        numbers.add(part.number());
                        pictures.add(picture);
                        collected++;
                    }
                }
                if (pictures.size() == before) {
                    int placeholders = kind == PartLibrary.Kind.VISITOR ? 3 : 1;
                    for (int i = 0; i < placeholders; i++) {
                        keys.add(key);
                        numbers.add(-i);
                        pictures.add(placeholder(district, kind, i));
                    }
                }
            }
        }
        int columns = Math.min(COLUMNS, pictures.size());
        int rows = (pictures.size() + columns - 1) / columns;
        BufferedImage image = new BufferedImage(columns * CELL_WIDTH, rows * CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        SpriteAtlas atlas = new SpriteAtlas(image, columns, rows);
        for (String key : keys) {
            atlas.cells.putIfAbsent(key, new ArrayList<>());
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (int index = 0; index < pictures.size(); index++) {
            BufferedImage picture = pictures.get(index);
            int column = index % columns;
            int row = index / columns;
            // As large as fits into the cell minus its transparent margin, standing on the bottom edge.
            int maxWidth = CELL_WIDTH - 8;
            int maxHeight = CELL_HEIGHT - 4;
            double scale = Math.min((double) maxWidth / picture.getWidth(), (double) maxHeight / picture.getHeight());
            int width = Math.max(1, (int) Math.round(picture.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(picture.getHeight() * scale));
            g.drawImage(picture, column * CELL_WIDTH + (CELL_WIDTH - width) / 2, (row + 1) * CELL_HEIGHT - height,
                    width, height, null);
            atlas.cells.get(keys.get(index)).add(new Cell(column, row, numbers.get(index)));
        }
        g.dispose();
        System.out.println("Sprites" + (districts.get(0).builtIn() ? "" : " of " + districts.get(0).name()) + ": "
                + collected + " pictures from " + shared.directory().getParent()
                + (collected == 0 ? ", using the built-in ones" : ""));
        return atlas;
    }

    /** A built-in picture of a kind, the size of a cell; {@code variant} tells the three people apart. */
    private static BufferedImage placeholder(District district, PartLibrary.Kind kind, int variant) {
        BufferedImage image = new BufferedImage(CELL_WIDTH, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(PEN);
        g.clipRect(4, 4, CELL_WIDTH - 8, CELL_HEIGHT - 4);
        boolean works = district == District.WORKS_QUARTER;
        switch (kind) {
            case BLOSSOM_TREE -> {
                if (!district.builtIn()) {
                    tree(g, new Color(0x8A5A3C), new Color(0x8CC97A), new Color(0xB4E2A2));
                } else if (works) {
                    tree(g, new Color(0xEDEDE6), new Color(0xB7DD7C), new Color(0xD3EDA6));
                } else {
                    tree(g, new Color(0x8A5A3C), new Color(0xF7A8CF), new Color(0xFFD3E6));
                }
            }
            case GREEN_TREE -> tree(g, works ? new Color(0x6E655F) : new Color(0x8A5A3C),
                    works ? new Color(0x5E9E63) : new Color(0x74C365), works ? new Color(0x86BE86) : new Color(0xA5E08C));
            case LAMP -> lamp(g, works ? new Color(0x6F7480) : new Color(0x4A4458));
            case BUSH -> {
                if (!district.flowerBushes()) {
                    weeds(g);
                } else {
                    bush(g);
                }
            }
            default -> {
                Color[][] people = {{new Color(0x7B5EA7), new Color(0xB5532F)}, {new Color(0x3F8FC4), new Color(0x3A2C22)},
                        {new Color(0xE0A23A), new Color(0xD9B26A)}};
                visitor(g, people[variant % 3][0], people[variant % 3][1]);
            }
        }
        g.dispose();
        return image;
    }

    private static void outlined(Graphics2D g, java.awt.Shape shape, Color fill) {
        g.setColor(fill);
        g.fill(shape);
        g.setColor(OUTLINE);
        g.draw(shape);
    }

    private static void tree(Graphics2D g, Color trunk, Color crown, Color highlight) {
        outlined(g, new RoundRectangle2D.Float(54, 130, 20, 120, 10, 10), trunk);
        outlined(g, new Ellipse2D.Float(12, 70, 66, 66), crown);
        outlined(g, new Ellipse2D.Float(50, 76, 66, 66), crown);
        outlined(g, new Ellipse2D.Float(24, 14, 80, 80), crown);
        g.setColor(highlight);
        g.fill(new Ellipse2D.Float(40, 28, 26, 20));
        g.fill(new Ellipse2D.Float(28, 86, 18, 14));
        g.fill(new Ellipse2D.Float(78, 92, 18, 14));
    }

    /** A flowering bush in the lower half of the cell, as wide as the cell. */
    private static void bush(Graphics2D g) {
        outlined(g, new Ellipse2D.Float(10, 176, 108, 90), new Color(0x74C365));
        outlined(g, new Ellipse2D.Float(40, 140, 50, 50), new Color(0xF7A8CF));
        outlined(g, new Ellipse2D.Float(14, 176, 40, 40), new Color(0xF48FBF));
        outlined(g, new Ellipse2D.Float(76, 172, 40, 40), new Color(0xF48FBF));
        g.setColor(new Color(0xFFE08A));
        g.fill(new Ellipse2D.Float(58, 158, 14, 14));
        g.fill(new Ellipse2D.Float(28, 190, 12, 12));
        g.fill(new Ellipse2D.Float(90, 186, 12, 12));
    }

    /** Tall weeds with purple flower spikes, as high as the cell: what grows on old works. */
    private static void weeds(Graphics2D g) {
        int[][] stems = {{38, 96}, {64, 40}, {90, 110}};
        for (int[] stem : stems) {
            outlined(g, new RoundRectangle2D.Float(stem[0] - 4, stem[1] + 50, 8, 250 - stem[1] - 50, 6, 6),
                    new Color(0x6FA863));
            outlined(g, new Ellipse2D.Float(stem[0] - 13, stem[1], 26, 74), new Color(0xB57BC9));
            outlined(g, new Ellipse2D.Float(stem[0] - 24, stem[1] + 96, 22, 12), new Color(0x8CC27A));
            outlined(g, new Ellipse2D.Float(stem[0] + 2, stem[1] + 120, 22, 12), new Color(0x8CC27A));
        }
    }

    private static void lamp(Graphics2D g, Color post) {
        outlined(g, new RoundRectangle2D.Float(58, 70, 12, 180, 6, 6), post);
        outlined(g, new RoundRectangle2D.Float(48, 236, 32, 14, 6, 6), post);
        outlined(g, new RoundRectangle2D.Float(40, 24, 48, 56, 16, 16), new Color(0xFFE08A));
        g.setColor(new Color(0xFFF6C8));
        g.fill(new Ellipse2D.Float(52, 36, 24, 30));
    }

    private static void visitor(Graphics2D g, Color clothes, Color hair) {
        outlined(g, new RoundRectangle2D.Float(44, 168, 16, 82, 8, 8), new Color(0x4A4458));
        outlined(g, new RoundRectangle2D.Float(68, 168, 16, 82, 8, 8), new Color(0x4A4458));
        outlined(g, new RoundRectangle2D.Float(34, 84, 60, 96, 26, 26), clothes);
        outlined(g, new Ellipse2D.Float(38, 22, 52, 58), new Color(0xF2C9A5));
        g.setColor(hair);
        g.fill(new Ellipse2D.Float(36, 16, 56, 30));
        g.setColor(OUTLINE);
        g.draw(new Ellipse2D.Float(36, 16, 56, 30));
    }
}
