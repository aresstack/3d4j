package com.aresstack.threed4j.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgRasterizerTest {

    /** A red disc in the lower half of a 256 x 512 drawing, as a model would send it. */
    static final String DISC = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 512">
              <circle cx="128" cy="384" r="100" fill="#FF0000" stroke="#3B2A4A" stroke-width="8"/>
            </svg>""";

    @Test
    void findsTheSvgInsideAChattyAnswer() {
        String answer = "Sure! Here is your tree:\n```svg\n" + DISC + "\n```\nHope you like it.";
        String svg = SvgRasterizer.extract(answer);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
    }

    @Test
    void answerWithoutSvgGivesEmptyString() {
        assertEquals("", SvgRasterizer.extract("I cannot draw."));
        assertEquals("", SvgRasterizer.extract(null));
    }

    @Test
    void removesScriptsAndAddsTheMissingNamespace() {
        String svg = SvgRasterizer.extract(
                "<svg viewBox=\"0 0 10 10\"><script>alert(1)</script><rect width=\"10\" height=\"10\"/></svg>");
        assertFalse(svg.contains("script"));
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
    }

    @Test
    void drawsWithTransparentBackground() throws Exception {
        BufferedImage image = SvgRasterizer.render(SvgRasterizer.extract(DISC), 256, 512);
        assertEquals(256, image.getWidth());
        assertEquals(512, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(128, 384), "center of the disc is opaque red");
        assertEquals(0, image.getRGB(128, 60) >>> 24, "above the disc nothing is drawn");
        double coverage = SvgRasterizer.coverage(image);
        assertTrue(coverage > 0.15 && coverage < 0.40, "coverage " + coverage);
    }

    @Test
    void scalesADifferentViewBoxToFitAndStandsItOnTheBottomEdge() throws Exception {
        // A square drawing in an upright cell: full width, lower half only.
        String square = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                + "<rect width=\"100\" height=\"100\" fill=\"#00FF00\"/></svg>";
        BufferedImage image = SvgRasterizer.render(square, 256, 512);
        assertEquals(0xFF00FF00, image.getRGB(128, 500));
        assertEquals(0xFF00FF00, image.getRGB(10, 270));
        assertEquals(0, image.getRGB(128, 200) >>> 24);
    }

    @Test
    void brokenSvgIsReportedNotThrownAsRuntimeError() {
        assertThrows(SvgRasterizer.UnreadableSvgException.class,
                () -> SvgRasterizer.render("<svg xmlns=\"http://www.w3.org/2000/svg\"><circle</svg>", 64, 64));
    }

    @Test
    void storeRendersADroppedSvgAndReusesThePng(@TempDir Path folder) throws Exception {
        AssetStore store = new AssetStore(folder);
        AssetSpec spec = AssetCatalog.BLOSSOM_TREE;
        assertNull(store.load(spec), "empty folder: built-in picture");

        Files.writeString(store.svgFile(spec), DISC);
        BufferedImage first = store.load(spec);
        assertNotNull(first);
        assertTrue(Files.isRegularFile(store.pngFile(spec)), "the PNG is written next to the SVG");

        BufferedImage second = store.load(spec);
        assertEquals(first.getRGB(128, 384), second.getRGB(128, 384));
    }
}
