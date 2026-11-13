package com.aresstack.threed4j.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Paints the placeholder sprites of the test plaza with Java 2D: trees, a street
 * lamp and visitors, side by side in one image. Real artwork can replace this
 * image later without touching the scene code.
 */
final class SpriteAtlas {

    static final int CELL_WIDTH = 128;
    static final int CELL_HEIGHT = 256;

    static final int BLOSSOM_TREE = 0;
    static final int GREEN_TREE = 1;
    static final int LAMP = 2;
    static final int FIRST_VISITOR = 3;
    static final int VISITOR_COUNT = 3;
    static final int CELLS = FIRST_VISITOR + VISITOR_COUNT;

    private static final Color OUTLINE = new Color(0x3B2A4A);
    private static final BasicStroke PEN = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private SpriteAtlas() {
    }

    static BufferedImage paint() {
        BufferedImage image = new BufferedImage(CELLS * CELL_WIDTH, CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(PEN);

        tree(cell(g, BLOSSOM_TREE), new Color(0xF7A8CF), new Color(0xFFD3E6));
        tree(cell(g, GREEN_TREE), new Color(0x74C365), new Color(0xA5E08C));
        lamp(cell(g, LAMP));
        visitor(cell(g, FIRST_VISITOR), new Color(0x7B5EA7), new Color(0xB5532F));
        visitor(cell(g, FIRST_VISITOR + 1), new Color(0x3F8FC4), new Color(0x3A2C22));
        visitor(cell(g, FIRST_VISITOR + 2), new Color(0xE0A23A), new Color(0xD9B26A));
        g.dispose();
        return image;
    }

    /** A graphics context whose origin is the top left corner of the given cell. */
    private static Graphics2D cell(Graphics2D g, int index) {
        Graphics2D cell = (Graphics2D) g.create();
        cell.translate(index * CELL_WIDTH, 0);
        cell.clipRect(4, 4, CELL_WIDTH - 8, CELL_HEIGHT - 4); // a transparent margin keeps neighbors apart
        return cell;
    }

    private static void outlined(Graphics2D g, java.awt.Shape shape, Color fill) {
        g.setColor(fill);
        g.fill(shape);
        g.setColor(OUTLINE);
        g.draw(shape);
    }

    private static void tree(Graphics2D g, Color crown, Color highlight) {
        outlined(g, new RoundRectangle2D.Float(54, 130, 20, 120, 10, 10), new Color(0x8A5A3C));
        outlined(g, new Ellipse2D.Float(12, 70, 66, 66), crown);
        outlined(g, new Ellipse2D.Float(50, 76, 66, 66), crown);
        outlined(g, new Ellipse2D.Float(24, 14, 80, 80), crown);
        g.setColor(highlight);
        g.fill(new Ellipse2D.Float(40, 28, 26, 20));
        g.fill(new Ellipse2D.Float(28, 86, 18, 14));
        g.fill(new Ellipse2D.Float(78, 92, 18, 14));
    }

    private static void lamp(Graphics2D g) {
        outlined(g, new RoundRectangle2D.Float(58, 70, 12, 180, 6, 6), new Color(0x4A4458));
        outlined(g, new RoundRectangle2D.Float(48, 236, 32, 14, 6, 6), new Color(0x4A4458));
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
