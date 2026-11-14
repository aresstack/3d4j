package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;
import com.aresstack.threed4j.math.Vec3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;
import java.util.List;
import java.util.Map;

/**
 * The building parts of one district side by side in one image: windows, doors,
 * shop fronts, each transparent around the part. While the district's
 * {@link PartLibrary} has no part of a kind, one built-in part stands in, so
 * that at first all houses look alike.
 *
 * <p>As in {@link GroundAtlas}, every cell repeats its tile's opposite edges
 * around it, because the parts are repeated along a wall.</p>
 */
final class FacadeAtlas {

    /** The kinds of the collection that are parts of house fronts. */
    static final List<PartLibrary.Kind> KINDS =
            List.of(PartLibrary.Kind.WINDOW, PartLibrary.Kind.DOOR, PartLibrary.Kind.SHOP);

    static final int TILE = PartLibrary.SIZE;
    static final int GUTTER = 4;
    static final int CELL = TILE + 2 * GUTTER;

    /**
     * One part in the atlas.
     *
     * @param number       the part's number in the library; 0 for the built-in part
     * @param averageColor colour of the part where it is not transparent
     * @param coverage     how much of the cell the part covers, 0 to 1
     */
    record Cell(int index, int number, Vec3 averageColor, float coverage) {
    }

    final BufferedImage image;
    final int cellCount;
    private final Map<String, List<Cell>> cells = new HashMap<>();

    private static String key(District district, PartLibrary.Kind kind) {
        return district.id() + "/" + kind;
    }

    private FacadeAtlas(BufferedImage image, int cellCount) {
        this.image = image;
        this.cellCount = cellCount;
    }

    /** The parts of this kind in the Blütenviertel; never empty. */
    List<Cell> cells(PartLibrary.Kind kind) {
        return cells(District.BLOSSOM_QUARTER, kind);
    }

    /** The parts of this kind in a district; never empty. */
    List<Cell> cells(District district, PartLibrary.Kind kind) {
        return cells.get(key(district, kind));
    }

    /** Paints the atlas from one library, as the Blütenviertel. */
    static FacadeAtlas paint(PartLibrary library) {
        return paint(List.of(District.BLOSSOM_QUARTER), district -> library);
    }

    /** Paints the atlas of the whole town. */
    static FacadeAtlas paint(TownLibrary town) {
        return paint(District.ALL, town::district);
    }

    /** A built-in part for every kind a district's library has none of. */
    private static FacadeAtlas paint(List<District> districts, Function<District, PartLibrary> libraries) {
        List<String> keys = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        List<BufferedImage> tiles = new ArrayList<>();
        int generated = 0;
        for (District district : districts) {
            PartLibrary library = libraries.apply(district);
            for (PartLibrary.Kind kind : KINDS) {
                int before = tiles.size();
                for (PartLibrary.Part part : library.parts(kind)) {
                    BufferedImage picture = library.load(part);
                    if (picture != null) {
                        keys.add(key(district, kind));
                        numbers.add(part.number());
                        tiles.add(scaled(picture));
                        generated++;
                    }
                }
                if (tiles.size() == before) {
                    keys.add(key(district, kind));
                    numbers.add(0);
                    tiles.add(builtIn(district, kind));
                }
            }
        }
        BufferedImage image = new BufferedImage(tiles.size() * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        FacadeAtlas atlas = new FacadeAtlas(image, tiles.size());
        for (String key : keys) {
            atlas.cells.putIfAbsent(key, new ArrayList<>());
        }
        Graphics2D g = image.createGraphics();
        for (int index = 0; index < tiles.size(); index++) {
            BufferedImage tile = tiles.get(index);
            Graphics2D clipped = (Graphics2D) g.create(index * CELL, 0, CELL, CELL);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    clipped.drawImage(tile, GUTTER + dx * TILE, GUTTER + dy * TILE, null);
                }
            }
            clipped.dispose();
            atlas.cells.get(keys.get(index)).add(measure(index, numbers.get(index), tile));
        }
        g.dispose();
        System.out.println("Facades: " + generated + " building parts from "
                + libraries.apply(districts.get(0)).directory().getParent()
                + (generated == 0 ? ", using the built-in ones" : ""));
        return atlas;
    }

    private static BufferedImage scaled(BufferedImage picture) {
        if (picture.getWidth() == TILE && picture.getHeight() == TILE) {
            return picture;
        }
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(picture, 0, 0, TILE, TILE, null);
        g.dispose();
        return tile;
    }

    private static Cell measure(int index, int number, BufferedImage tile) {
        double red = 0;
        double green = 0;
        double blue = 0;
        double alpha = 0;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int argb = tile.getRGB(x, y);
                double a = (argb >>> 24) / 255.0;
                alpha += a;
                red += a * ((argb >> 16) & 0xFF);
                green += a * ((argb >> 8) & 0xFF);
                blue += a * (argb & 0xFF);
            }
        }
        double weight = Math.max(alpha, 1e-6) * 255.0;
        return new Cell(index, number, new Vec3((float) (red / weight), (float) (green / weight),
                (float) (blue / weight)), (float) (alpha / (TILE * TILE)));
    }

    // ----------------------------------------------------------- built-in parts

    private static final Color OUTLINE = new Color(0x3B2A4A);
    private static final Color FRAME = new Color(0xFFF4E4);
    private static final Color GLASS = new Color(0xA9DCEF);
    private static final Color SHINE = new Color(0xD8F1FA);

    private static final Color STEEL = new Color(0x59606E);
    private static final Color WORKS_GLASS = new Color(0x9CC7D6);

    /** The built-in parts of the Werksviertel: a steel window of many panes, a steel door, a roller gate. */
    private static void builtInWorks(Graphics2D g, PartLibrary.Kind kind) {
        switch (kind) {
            case WINDOW -> {
                outlined(g, new RoundRectangle2D.Float(28, 16, 72, 96, 4, 4), STEEL);
                for (int row = 0; row < 4; row++) {
                    for (int column = 0; column < 3; column++) {
                        g.setColor(row == 1 && column == 2 ? SHINE : WORKS_GLASS);
                        g.fillRect(35 + column * 21, 23 + row * 22, 16, 17);
                    }
                }
            }
            case DOOR -> {
                outlined(g, new RoundRectangle2D.Float(36, 22, 56, 110, 4, 4), new Color(0x6F9A86));
                outlined(g, new RoundRectangle2D.Float(48, 34, 32, 24, 4, 4), WORKS_GLASS);
                g.setColor(OUTLINE);
                g.drawLine(40, 76, 88, 76);
                g.fill(new Ellipse2D.Float(78, 88, 8, 8));
            }
            default -> {
                outlined(g, new RoundRectangle2D.Float(12, 20, 104, 96, 4, 4), STEEL);
                g.setColor(new Color(0xA9AEB8));
                g.fillRect(20, 28, 88, 84);
                g.setColor(new Color(0x8A8F9B));
                for (int slat = 0; slat < 7; slat++) {
                    g.fillRect(20, 36 + slat * 12, 88, 3);
                }
            }
        }
    }

    private static BufferedImage builtIn(District district, PartLibrary.Kind kind) {
        BufferedImage tile = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tile.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (district == District.WORKS_QUARTER) {
            builtInWorks(g, kind);
            g.dispose();
            return tile;
        }
        switch (kind) {
            case WINDOW -> {
                outlined(g, new RoundRectangle2D.Float(30, 22, 68, 80, 10, 10), FRAME);
                outlined(g, new RoundRectangle2D.Float(38, 30, 52, 64, 6, 6), GLASS);
                g.setColor(SHINE);
                g.fill(new RoundRectangle2D.Float(44, 36, 12, 22, 6, 6));
                g.setColor(OUTLINE);
                g.drawLine(64, 30, 64, 94);
                g.drawLine(38, 62, 90, 62);
                outlined(g, new RoundRectangle2D.Float(24, 100, 80, 10, 6, 6), FRAME);
            }
            case DOOR -> {
                Path2D door = new Path2D.Float();
                door.moveTo(36, 126);
                door.lineTo(36, 50);
                door.append(new Arc2D.Float(36, 22, 56, 56, 180, -180, Arc2D.OPEN), true);
                door.lineTo(92, 126);
                door.closePath();
                outlined(g, door, new Color(0xF2A7C4));
                outlined(g, new Ellipse2D.Float(52, 40, 24, 24), GLASS);
                g.setColor(new Color(0xFFD86B));
                g.fill(new Ellipse2D.Float(78, 86, 8, 8));
                g.setColor(OUTLINE);
                g.draw(new Ellipse2D.Float(78, 86, 8, 8));
            }
            default -> {
                outlined(g, new RoundRectangle2D.Float(14, 44, 100, 68, 8, 8), FRAME);
                outlined(g, new RoundRectangle2D.Float(22, 52, 84, 52, 6, 6), GLASS);
                g.setColor(SHINE);
                g.fill(new RoundRectangle2D.Float(30, 58, 14, 26, 6, 6));
                Path2D awning = new Path2D.Float();
                awning.moveTo(12, 22);
                awning.lineTo(116, 22);
                awning.lineTo(120, 46);
                awning.lineTo(8, 46);
                awning.closePath();
                outlined(g, awning, new Color(0xF7A8CF));
                g.setColor(Color.WHITE);
                for (int stripe = 0; stripe < 3; stripe++) {
                    g.fillRect(28 + stripe * 30, 25, 14, 18);
                }
            }
        }
        g.dispose();
        return tile;
    }

    private static void outlined(Graphics2D g, java.awt.Shape shape, Color fill) {
        g.setColor(fill);
        g.fill(shape);
        g.setColor(OUTLINE);
        g.draw(shape);
    }
}
