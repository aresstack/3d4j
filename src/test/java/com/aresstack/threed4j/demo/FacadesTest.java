package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.math.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadesTest {

    private static List<FacadeAtlas.Cell> parts(int count) {
        List<FacadeAtlas.Cell> parts = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            parts.add(new FacadeAtlas.Cell(number - 1, number, new Vec3(1f, 1f, 1f), 0.4f));
        }
        return parts;
    }

    @Test
    void aNewPartTakesOverItsShareOfTheHousesAndTheOthersKeepWhatTheyHad() {
        int houses = 400;
        int[] spread = new int[5];
        int changed = 0;
        int changedToSomethingElse = 0;
        for (long house = 0; house < houses; house++) {
            int before = Facades.choose(parts(4), house, 1).number();
            int after = Facades.choose(parts(5), house, 1).number();
            spread[after - 1]++;
            if (before != after) {
                changed++;
                if (after != 5) {
                    changedToSomethingElse++;
                }
            }
        }
        assertEquals(0, changedToSomethingElse, "a house changes only to the new part");
        assertTrue(changed > houses / 5 / 2 && changed < houses / 5 * 2, changed + " of " + houses + " changed");
        for (int count : spread) {
            assertTrue(count > houses / 5 / 2, "every part is used: " + java.util.Arrays.toString(spread));
        }
    }

    @Test
    void withOnePartAllHousesLookAlikeAndWindowAndDoorAreChosenIndependently() {
        for (long house = 0; house < 50; house++) {
            assertEquals(1, Facades.choose(parts(1), house, 1).number());
        }
        int different = 0;
        for (long house = 0; house < 200; house++) {
            if (Facades.choose(parts(4), house, 1).number() != Facades.choose(parts(4), house, 2).number()) {
                different++;
            }
        }
        assertTrue(different > 100, "window and door of a house are not tied together: " + different);
    }

    @Test
    void theAtlasHasABuiltInPartForEveryKindTheLibraryLacks(@TempDir Path directory) throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        FacadeAtlas builtIn = FacadeAtlas.paint(library);
        assertEquals(3, builtIn.cellCount);
        for (PartLibrary.Kind kind : FacadeAtlas.KINDS) {
            FacadeAtlas.Cell cell = builtIn.cells(kind).get(0);
            assertEquals(0, cell.number(), "0 marks the built-in part");
            assertTrue(cell.coverage() > 0.15f && cell.coverage() < 0.8f, kind + " covers " + cell.coverage());
        }
        assertEquals(3 * FacadeAtlas.CELL, builtIn.image.getWidth());

        BufferedImage window = new BufferedImage(PartLibrary.SIZE, PartLibrary.SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = window.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(32, 32, 64, 64);
        g.dispose();
        library.add(PartLibrary.Kind.WINDOW, "<svg/>", window, null);
        library.add(PartLibrary.Kind.WINDOW, "<svg/>", window, null);

        FacadeAtlas grown = FacadeAtlas.paint(library);
        assertEquals(4, grown.cellCount, "two windows from the library, built-in door and shop");
        assertEquals(List.of(1, 2), grown.cells(PartLibrary.Kind.WINDOW).stream().map(FacadeAtlas.Cell::number).toList());
        FacadeAtlas.Cell red = grown.cells(PartLibrary.Kind.WINDOW).get(0);
        assertEquals(0.25f, red.coverage(), 0.01f);
        assertEquals(1f, red.averageColor().x(), 0.01f);
        assertEquals(0f, red.averageColor().y(), 0.01f);
    }

    @Test
    void figuresAndGroundUsePlaceholdersUntilTheCollectionHasPicturesAndThenChooseAmongThem(@TempDir Path directory)
            throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        SpriteAtlas builtIn = SpriteAtlas.paint(library);
        assertEquals(3, builtIn.cells(PartLibrary.Kind.VISITOR).size(), "three built-in people");
        assertEquals(1, builtIn.cells(PartLibrary.Kind.BLOSSOM_TREE).size());
        assertTrue(builtIn.cells(PartLibrary.Kind.BLOSSOM_TREE).get(0).number() <= 0);
        GroundAtlas ground = GroundAtlas.paint(library);
        assertEquals(3, ground.cellCount, "lawn, street, plaza");

        BufferedImage tree = new BufferedImage(64, 128, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 20; i++) {
            library.add(PartLibrary.Kind.BLOSSOM_TREE, null, tree, null);
        }
        library.add(PartLibrary.Kind.LAWN, null, new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB), null);

        SpriteAtlas grown = SpriteAtlas.paint(library);
        assertEquals(20, grown.cells(PartLibrary.Kind.BLOSSOM_TREE).size(), "the placeholder is gone");
        assertEquals(2, grown.rows, "more pictures than fit into one row");
        java.util.Set<Integer> chosen = new java.util.HashSet<>();
        for (long figure = 0; figure < 300; figure++) {
            chosen.add(grown.choose(PartLibrary.Kind.BLOSSOM_TREE, figure).number());
        }
        assertEquals(20, chosen.size(), "every picture is used by some tree");
        assertEquals(1, GroundAtlas.paint(library).choose(GroundAtlas.Surface.LAWN, 7).number());
    }
}
