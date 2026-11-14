package com.aresstack.threed4j.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The game's mouse pointer for its menus: an arrowhead without a shaft, in the
 * dark pink of a chosen button. It is one flowing outline: the two lower ends
 * of the triangle sweep outward, grow ever thinner and turn up into a point,
 * like the ends of a twirled moustache. A few translucent strokes trail from
 * the notch between them.
 */
final class GameCursor {

    static final int SIZE = 48;
    /** The pixel that points: the tip of the arrowhead. */
    static final int HOTSPOT_X = 7;
    static final int HOTSPOT_Y = 7;

    private static final Color OUTLINE = new Color(0x4A1F3D);
    private static final Color FILL = new Color(0xD9629C);
    private static final Color LIGHT = new Color(0xF9B6D6);

    /** How far the arrowhead leans away from straight down, in degrees. */
    private static final double LEAN = 45;
    private static final double SCALE = 1.2;

    private GameCursor() {
    }

    static BufferedImage paint() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        // Drawn upright with the tip at 0, 0 and then leant like any pointer.
        AffineTransform lean = new AffineTransform();
        lean.translate(HOTSPOT_X, HOTSPOT_Y);
        lean.rotate(Math.toRadians(-LEAN));
        lean.scale(SCALE, SCALE);
        g.transform(lean);

        // The translucent strokes first: they trail from the notch, between the two ends.
        trail(g, 0, 24, 0, 37.5, 0, 150);
        trail(g, -2.2, 25, -5.5, 35, -1.8, 105);
        trail(g, 2.2, 25, 5.5, 35, 1.8, 105);
        trail(g, -4.6, 26.5, -10.5, 32.5, -3, 60);
        trail(g, 4.6, 26.5, 10.5, 32.5, 3, 60);

        Path2D head = head();
        g.setColor(FILL);
        g.fill(head);
        // A lighter stroke inside the left edge, as a highlight.
        Path2D shine = new Path2D.Double();
        shine.moveTo(-1.2, 6);
        shine.curveTo(-3.4, 11, -5.6, 15.5, -9, 19);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(LIGHT);
        g.draw(shine);
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(OUTLINE);
        g.draw(head);
        g.dispose();
        return image;
    }

    /** The outline: tip, a side, an end sweeping out and up to a point, back under it to the notch; twice. */
    private static Path2D head() {
        Path2D path = new Path2D.Double();
        path.moveTo(0, 0);
        half(path, 1);
        half(path, -1);
        path.closePath();
        return path;
    }

    private static void half(Path2D path, int side) {
        if (side > 0) {
            path.curveTo(3.4, 7.5, 6.4, 14, 9.6, 17.8);
            path.curveTo(13, 21.8, 20.2, 21.6, 18.2, 12.6);
            path.curveTo(22.4, 20.6, 19, 27.6, 11.6, 27.2);
            path.curveTo(6.6, 26.9, 2.8, 24.6, 0, 21.6);
        } else {
            // The same way back, mirrored: from the notch out, round the end and up the side.
            path.curveTo(-2.8, 24.6, -6.6, 26.9, -11.6, 27.2);
            path.curveTo(-19, 27.6, -22.4, 20.6, -18.2, 12.6);
            path.curveTo(-20.2, 21.6, -13, 21.8, -9.6, 17.8);
            path.curveTo(-6.4, 14, -3.4, 7.5, 0, 0);
        }
    }

    /** One translucent stroke: a line that bends a little and fades toward its end. */
    private static void trail(Graphics2D g, double fromX, double fromY, double toX, double toY, double bend,
            int alpha) {
        int steps = 10;
        double lastX = fromX, lastY = fromY;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double x = fromX + (toX - fromX) * t + bend * Math.sin(t * Math.PI) * 0.5;
            double y = fromY + (toY - fromY) * t;
            g.setStroke(new BasicStroke((float) (1.7 - t * 1.1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(FILL.getRed(), FILL.getGreen(), FILL.getBlue(), (int) (alpha * (1 - t * 0.75))));
            Path2D piece = new Path2D.Double();
            piece.moveTo(lastX, lastY);
            piece.lineTo(x, y);
            g.draw(piece);
            lastX = x;
            lastY = y;
        }
    }
}
