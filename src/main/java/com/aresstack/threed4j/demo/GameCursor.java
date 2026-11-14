package com.aresstack.threed4j.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The game's mouse pointer for its menus: a pink arrow with a dark outline, as
 * everything in the game is drawn, and a cherry blossom at its tail.
 */
final class GameCursor {

    static final int SIZE = 48;
    /** The pixel that points: the tip of the arrow. */
    static final int HOTSPOT_X = 4;
    static final int HOTSPOT_Y = 3;

    private GameCursor() {
    }

    static BufferedImage paint() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Color outline = new Color(0x3B2A4A);

        Path2D arrow = new Path2D.Float();
        arrow.moveTo(4, 3);
        arrow.lineTo(4, 33);
        arrow.lineTo(12, 26);
        arrow.lineTo(18, 39);
        arrow.lineTo(24, 36);
        arrow.lineTo(18, 24);
        arrow.lineTo(28, 23);
        arrow.closePath();
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xFFB7D9));
        g.fill(arrow);
        // A lighter stripe along the left edge, as a highlight.
        g.setColor(new Color(0xFFE3F0));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(8, 10, 8, 25);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(arrow);

        // The blossom: five petals around a yellow middle.
        double centerX = 35.5;
        double centerY = 35.5;
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int petal = 0; petal < 5; petal++) {
            double angle = -Math.PI / 2 + petal * 2 * Math.PI / 5;
            Ellipse2D shape = new Ellipse2D.Double(centerX + Math.cos(angle) * 6.2 - 4.6, centerY + Math.sin(angle) * 6.2 - 4.6,
                    9.2, 9.2);
            g.setColor(outline);
            g.draw(shape);
        }
        for (int petal = 0; petal < 5; petal++) {
            double angle = -Math.PI / 2 + petal * 2 * Math.PI / 5;
            g.setColor(new Color(0xFFD3E6));
            g.fill(new Ellipse2D.Double(centerX + Math.cos(angle) * 6.2 - 4.6, centerY + Math.sin(angle) * 6.2 - 4.6, 9.2, 9.2));
        }
        g.setColor(new Color(0xFFD25A));
        g.fill(new Ellipse2D.Double(centerX - 3.2, centerY - 3.2, 6.4, 6.4));
        g.setColor(outline);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Ellipse2D.Double(centerX - 3.2, centerY - 3.2, 6.4, 6.4));
        g.dispose();
        return image;
    }
}
