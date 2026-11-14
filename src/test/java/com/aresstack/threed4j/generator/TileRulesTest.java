package com.aresstack.threed4j.generator;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What makes a drawing usable as a ground tile, and as a free-standing figure. */
class TileRulesTest {

    private static BufferedImage tile(boolean base, int shapeX) {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        if (base) {
            g.setColor(new Color(0xA2D08C));
            g.fillRect(0, 0, 128, 128);
        }
        g.setColor(new Color(0x5E8F4E));
        g.fillOval(shapeX, 50, 30, 20);
        g.dispose();
        return image;
    }

    @Test
    void aTileFillsItsSquareAndKeepsItsShapesAwayFromTheEdges() {
        assertNull(AssetGenerator.tileProblem(tile(true, 40)));

        String open = AssetGenerator.tileProblem(tile(false, 40));
        assertNotNull(open);
        assertTrue(open.contains("whole square"), open);

        String touching = AssetGenerator.tileProblem(tile(true, -10));
        assertNotNull(touching);
        assertTrue(touching.contains("seamlessly"), touching);
        assertNotNull(AssetGenerator.tileProblem(tile(true, 110)), "the right edge counts too");
    }

    @Test
    void shapesInAlmostTheBaseColourMayReachTheEdge() {
        BufferedImage image = tile(true, 40);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xA8D492)); // a shade of the base colour: no visible seam
        g.fillRect(0, 0, 128, 6);
        g.dispose();
        assertNull(AssetGenerator.tileProblem(image));
    }

    @Test
    void aFigureIsThereAndStandsFree() {
        BufferedImage figure = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = figure.createGraphics();
        g.setColor(new Color(0x7B5EA7));
        g.fillOval(30, 20, 70, 100);
        g.dispose();
        assertNull(AssetGenerator.figureProblem(figure));
        assertTrue(AssetGenerator.figureProblem(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)).contains("empty"));
        assertTrue(AssetGenerator.figureProblem(tile(true, 40)).contains("transparent"));
    }
}
