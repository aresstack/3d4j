package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartArtistTest {

    private static final String WINDOW = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 128 128\">"
            + "<rect x=\"30\" y=\"24\" width=\"68\" height=\"80\" rx=\"8\" fill=\"#FFF4E4\" stroke=\"#3B2A4A\" "
            + "stroke-width=\"5\"/></svg>";
    private static final String WALL_BEHIND = WINDOW.replace("<rect x=\"30\"",
            "<rect x=\"0\" y=\"0\" width=\"128\" height=\"128\" fill=\"#EEE\"/><rect x=\"30\"");

    private static BufferedImage picture(int x, int y, int width, int height) {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xF2A7C4));
        g.fillRect(x, y, width, height);
        g.dispose();
        return image;
    }

    @Test
    void aPartLeavesTheWallVisibleAroundItAndOnlyDoorsAndShopsStandOnTheBottomEdge() {
        assertNull(PartArtist.problem(PartLibrary.Kind.WINDOW, picture(24, 20, 80, 88)));
        assertTrue(PartArtist.problem(PartLibrary.Kind.WINDOW, picture(60, 60, 8, 8)).contains("empty"));
        assertTrue(PartArtist.problem(PartLibrary.Kind.WINDOW, picture(0, 0, 128, 128)).contains("transparent"));
        assertTrue(PartArtist.problem(PartLibrary.Kind.WINDOW, picture(0, 20, 80, 88)).contains("edge"));
        assertTrue(PartArtist.problem(PartLibrary.Kind.WINDOW, picture(34, 40, 60, 88)).contains("edge"),
                "a window must not stand on the bottom edge");
        assertNull(PartArtist.problem(PartLibrary.Kind.DOOR, picture(34, 12, 60, 116)));
        assertNull(PartArtist.problem(PartLibrary.Kind.SHOP, picture(12, 20, 104, 108)));
        assertTrue(PartArtist.problem(PartLibrary.Kind.DOOR, picture(34, 0, 60, 128)).contains("edge"));
    }

    @Test
    void everyRequestCarriesTheStyleOfTheDistrictAndItsOwnVariation() {
        String first = PartArtist.task(PartLibrary.Kind.WINDOW, 1, "pastel old town");
        String second = PartArtist.task(PartLibrary.Kind.WINDOW, 2, "pastel old town");
        assertTrue(first.contains("pastel old town") && second.contains("pastel old town"));
        assertNotEquals(first, second);
        assertEquals(first, PartArtist.task(PartLibrary.Kind.WINDOW, 9, "pastel old town"), "the list starts again");
        assertTrue(PartArtist.rules(PartLibrary.Kind.DOOR).contains("stands on the bottom edge"));
    }

    @Test
    void asksAgainWithTheReasonAndAddsTheUsablePartToTheLibrary(@TempDir Path directory) throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        List<String> prompts = new ArrayList<>();
        FlowerComposer.Model model = (system, user) -> {
            prompts.add(user);
            return prompts.size() == 1 ? WALL_BEHIND : "Here you are:\n" + WINDOW;
        };

        assertNull(PartArtist.generate(model, library, PartLibrary.Kind.WINDOW, 1, "pastel old town", "some/model"));

        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).contains("previous answer was unusable") && prompts.get(1).contains("transparent"));
        assertEquals(1, library.parts(PartLibrary.Kind.WINDOW).size());
        String info = Files.readString(library.directory().resolve("window-001.json"));
        assertTrue(info.contains("some/model") && info.contains("pastel old town"), info);
    }

    @Test
    void givesUpWithTheReason(@TempDir Path directory) {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        String problem = PartArtist.generate((system, user) -> WALL_BEHIND, library, PartLibrary.Kind.WINDOW, 1, "x", "m");
        assertTrue(problem.contains("transparent") && problem.contains("3 attempts"), problem);
        assertTrue(library.parts().isEmpty());
        assertEquals("offline", PartArtist.generate((system, user) -> {
            throw new java.io.IOException("offline");
        }, library, PartLibrary.Kind.DOOR, 1, "x", "m"));
    }
}
