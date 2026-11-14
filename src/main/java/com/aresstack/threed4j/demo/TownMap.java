package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.TownPlan;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The plan of the town as a picture, seen from above with north at the top: the
 * sea in the east, the mountains in the west, and every district with its ground,
 * streets, plaza, houses and landmark. Painted once whenever the town changes;
 * the mini map and the big map show parts of this one picture.
 */
final class TownMap {

    /** The land around the outermost districts that is still on the map, in metres. */
    private static final float MARGIN = 130f;
    private static final int LARGEST = 4096;
    /** The picture is at least this many times as wide as it is high. */
    private static final float WIDEST = 2.1f;
    private static final Color INK = new Color(0x3B2A4A);
    private static final Color LAND = new Color(0xBFE0A6);
    private static final Color SEA = new Color(0x8FCBEF);
    private static final Color SEA_LIGHT = new Color(0xC4E6FA);
    private static final Color SAND = new Color(0xF1E2B4);
    private static final int[] BLOSSOM_WALLS = {0xF6D5DD, 0xD9EBD3, 0xF3E6C8, 0xD8E3F2, 0xEBD5EA};
    private static final int[] WORKS_WALLS = {0xB5654A, 0x9C5A45, 0xA8764F, 0x8E8E96};

    final BufferedImage image;
    /** The part of the world the picture shows, in metres. */
    final float minX;
    final float minZ;
    final float maxX;
    final float maxZ;

    private TownMap(BufferedImage image, float minX, float minZ, float maxX, float maxZ) {
        this.image = image;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    float width() {
        return maxX - minX;
    }

    float height() {
        return maxZ - minZ;
    }

    /** One district for the map: the district and the ground plans of its houses (minX, minZ, maxX, maxZ). */
    record Piece(District district, List<float[]> houses) {
    }

    static TownMap paint(List<Piece> pieces) {
        float minX = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (Piece piece : pieces) {
            int[] plot = TownPlan.plot(piece.district());
            minX = Math.min(minX, (plot[0] - 0.5f) * District.PLOT);
            maxX = Math.max(maxX, (plot[0] + 0.5f) * District.PLOT);
            minZ = Math.min(minZ, (plot[1] - 0.5f) * District.PLOT);
            maxZ = Math.max(maxZ, (plot[1] + 0.5f) * District.PLOT);
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        maxX += MARGIN;
        maxZ += MARGIN;
        // Wide enough to be seen whole in a card on a wide screen: more sea and more mountains.
        float missing = (maxZ - minZ) * WIDEST - (maxX - minX);
        if (missing > 0) {
            minX -= missing / 2;
            maxX += missing / 2;
        }
        float scale = Math.min(4f, LARGEST / Math.max(maxX - minX, maxZ - minZ));
        int width = Math.round((maxX - minX) * scale);
        int height = Math.round((maxZ - minZ) * scale);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(LAND);
        g.fillRect(0, 0, width, height);
        // From here on in metres: x to the right (east), z down (south).
        AffineTransform pixels = g.getTransform();
        g.scale(scale, scale);
        g.translate(-minX, -minZ);

        mountains(g, minX, minZ, maxZ);
        Shape land = land(minX, minZ, maxZ);
        sea(g, land, minX, minZ, maxX, maxZ);
        for (Piece piece : pieces) {
            ground(g, piece.district(), land);
        }
        for (Piece piece : pieces) {
            houses(g, piece);
        }
        for (Piece piece : pieces) {
            landmark(g, piece.district());
        }
        for (Piece piece : pieces) {
            int[] plot = TownPlan.plot(piece.district());
            label(g, piece.district().name(), plot[0] * District.PLOT, (plot[1] - 0.36f) * District.PLOT,
                    new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 20), INK);
        }
        compass(g, maxX - 38f, minZ + 44f);
        g.setTransform(pixels);
        g.dispose();
        return new TownMap(image, minX, minZ, maxX, maxZ);
    }

    /** Everything west of the coast. */
    private static Shape land(float minX, float minZ, float maxZ) {
        Path2D land = new Path2D.Float();
        land.moveTo(minX - 10, minZ - 10);
        for (float z = minZ - 10; z <= maxZ + 10; z += 4f) {
            land.lineTo(District.coastX(z), z);
        }
        land.lineTo(minX - 10, maxZ + 10);
        land.closePath();
        return land;
    }

    private static void sea(Graphics2D g, Shape land, float minX, float minZ, float maxX, float maxZ) {
        Shape everything = g.getClip();
        java.awt.geom.Area water = new java.awt.geom.Area(new Rectangle2D.Float(minX, minZ, maxX - minX, maxZ - minZ));
        water.subtract(new java.awt.geom.Area(land));
        g.setColor(SEA);
        g.fill(water);
        g.setClip(water);
        g.setColor(SEA_LIGHT);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (float z = minZ + 14; z < maxZ; z += 26f) {
            for (float x = 150f + ((int) (z / 26f) % 2) * 17f; x < maxX; x += 34f) {
                Path2D wave = new Path2D.Float();
                wave.moveTo(x, z);
                wave.quadTo(x + 4, z - 4, x + 8, z);
                wave.quadTo(x + 12, z + 4, x + 16, z);
                g.draw(wave);
            }
        }
        g.setClip(everything);
        // The beach: a band of sand along the coast.
        Path2D coast = new Path2D.Float();
        coast.moveTo(District.coastX(minZ - 10), minZ - 10);
        for (float z = minZ - 6; z <= maxZ + 10; z += 4f) {
            coast.lineTo(District.coastX(z), z);
        }
        g.setColor(SAND);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(coast);
    }

    private static void mountains(Graphics2D g, float minX, float minZ, float maxZ) {
        g.setColor(new Color(0xA9CF95));
        g.fill(new Rectangle2D.Float(minX, minZ, Alps.FOOT_X - minX, maxZ - minZ));
        java.util.Random random = new java.util.Random(7);
        for (float x = minX - 20; x < Alps.FOOT_X - 16; x += 30f) {
            for (float z = minZ + 10 + ((int) (x / 30f) % 2 == 0 ? 0 : 20); z < maxZ + 30; z += 40f) {
                float size = 22f + random.nextFloat() * 14f;
                Path2D peak = new Path2D.Float();
                peak.moveTo(x, z);
                peak.lineTo(x + size * 0.5f, z - size);
                peak.lineTo(x + size, z);
                peak.closePath();
                g.setColor(new Color(0x9AA3AD));
                g.fill(peak);
                Path2D snow = new Path2D.Float();
                snow.moveTo(x + size * 0.5f, z - size);
                snow.lineTo(x + size * 0.33f, z - size * 0.66f);
                snow.lineTo(x + size * 0.5f, z - size * 0.55f);
                snow.lineTo(x + size * 0.67f, z - size * 0.66f);
                snow.closePath();
                g.setColor(Color.WHITE);
                g.fill(snow);
                g.setColor(INK);
                g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(peak);
            }
        }
    }

    private static void ground(Graphics2D g, District district, Shape land) {
        int[] plot = TownPlan.plot(district);
        float centerX = plot[0] * District.PLOT;
        float centerZ = plot[1] * District.PLOT;
        float half = District.PLOT / 2;
        Color lawn;
        Color street;
        Color plaza;
        if (district.builtIn()) {
            boolean works = district.equals(District.WORKS_QUARTER);
            lawn = new Color(works ? 0xA7BE96 : 0xA9D98C);
            street = new Color(works ? 0x9C9CA6 : 0xD6D2E0);
            plaza = new Color(works ? 0xBDB8AE : 0xF0E4D0);
        } else {
            lawn = new Color(district.layout().lawn());
            street = new Color(district.layout().street());
            plaza = new Color(district.layout().plaza());
        }
        Shape everything = g.getClip();
        g.setClip(land);
        g.clip(new Rectangle2D.Float(centerX - half, centerZ - half, District.PLOT, District.PLOT));
        g.setColor(lawn);
        g.fill(new Rectangle2D.Float(centerX - half, centerZ - half, District.PLOT, District.PLOT));
        g.setColor(street);
        g.fill(new Rectangle2D.Float(centerX - half, centerZ - 5f, District.PLOT, 10f));
        g.fill(new Rectangle2D.Float(centerX - 5f, centerZ - half, 10f, District.PLOT));
        g.setColor(plaza);
        g.fill(new Ellipse2D.Float(centerX - 20f, centerZ - 20f, 40f, 40f));
        g.setClip(everything);
        // The border of the district, lightly.
        g.setColor(new Color(0x3B, 0x2A, 0x4A, 70));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {4f, 4f}, 0f));
        g.draw(new Rectangle2D.Float(centerX - half, centerZ - half, District.PLOT, District.PLOT));
    }

    private static void houses(Graphics2D g, Piece piece) {
        District district = piece.district();
        int[] walls = district.builtIn() ? (district.equals(District.WORKS_QUARTER) ? WORKS_WALLS : BLOSSOM_WALLS)
                : district.layout().walls().stream().mapToInt(Integer::intValue).toArray();
        g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int index = 0;
        for (float[] house : piece.houses()) {
            Rectangle2D plan = new Rectangle2D.Float(house[0], house[1], house[2] - house[0], house[3] - house[1]);
            if (plan.getWidth() > 90 || plan.getHeight() > 90 || plan.getWidth() < 1.5 || plan.getHeight() < 1.5) {
                index++;
                continue; // a fence around something, or a kerb: not a house
            }
            g.setColor(new Color(walls[Math.floorMod(index++ * 7 + 3, walls.length)] & 0xFFFFFF));
            g.fill(plan);
            g.setColor(INK);
            g.draw(plan);
        }
    }

    private static void landmark(Graphics2D g, District district) {
        int[] plot = TownPlan.plot(district);
        float x = plot[0] * District.PLOT;
        float z = plot[1] * District.PLOT;
        String name;
        if (district.equals(District.BLOSSOM_QUARTER)) {
            name = "Springbrunnen";
        } else if (district.equals(District.WORKS_QUARTER)) {
            name = "WEST.SIDE";
        } else if (district.layout().landmark() != null) {
            name = district.layout().landmark().name();
        } else {
            return;
        }
        // A pink disc with a star, like a pin.
        g.setColor(new Color(0xD9629C));
        g.fill(new Ellipse2D.Float(x - 7f, z - 7f, 14f, 14f));
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(x - 7f, z - 7f, 14f, 14f));
        Path2D star = new Path2D.Float();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? 4.6 : 2.0;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            if (i == 0) {
                star.moveTo(px, pz);
            } else {
                star.lineTo(px, pz);
            }
        }
        star.closePath();
        g.setColor(Color.WHITE);
        g.fill(star);
        label(g, name, x, z + 19f, new Font(Font.SANS_SERIF, Font.BOLD, 9), INK);
    }

    /** Text centred on a place, with a light rim so that it reads on any ground. */
    private static void label(Graphics2D g, String text, float x, float z, Font font, Color color) {
        GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), text);
        Rectangle2D bounds = glyphs.getLogicalBounds();
        Shape outline = glyphs.getOutline((float) (x - bounds.getWidth() / 2), z);
        g.setColor(new Color(255, 255, 255, 215));
        g.setStroke(new BasicStroke(font.getSize2D() * 0.28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(color);
        g.fill(outline);
    }

    private static void compass(Graphics2D g, float x, float z) {
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(new Ellipse2D.Float(x - 17f, z - 17f, 34f, 34f));
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(x - 17f, z - 17f, 34f, 34f));
        Path2D needle = new Path2D.Float();
        needle.moveTo(x, z - 13f);
        needle.lineTo(x + 4.5f, z + 3f);
        needle.lineTo(x - 4.5f, z + 3f);
        needle.closePath();
        g.setColor(new Color(0xD9629C));
        g.fill(needle);
        g.setColor(INK);
        g.draw(needle);
        label(g, "N", x, z + 14f, new Font(Font.SANS_SERIF, Font.BOLD, 9), INK);
    }
}
