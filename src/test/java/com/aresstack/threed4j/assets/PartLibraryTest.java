package com.aresstack.threed4j.assets;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartLibraryTest {

    @TempDir
    Path directory;

    private static BufferedImage picture() {
        return new BufferedImage(PartLibrary.SIZE, PartLibrary.SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void partsAreNumberedPerKindAndKeptPerDistrict() throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        assertTrue(library.parts().isEmpty());
        String empty = library.signature();

        JsonObject details = new JsonObject();
        details.addProperty("model", "some/model");
        assertEquals(1, library.add(PartLibrary.Kind.WINDOW, "<svg/>", picture(), details).number());
        assertEquals(2, library.add(PartLibrary.Kind.WINDOW, "<svg/>", picture(), null).number());
        assertEquals(1, library.add(PartLibrary.Kind.DOOR, "<svg/>", picture(), null).number());

        assertEquals(List.of(1, 2), library.parts(PartLibrary.Kind.WINDOW).stream().map(PartLibrary.Part::number).toList());
        assertEquals(1, library.parts(PartLibrary.Kind.DOOR).size());
        assertTrue(library.parts(PartLibrary.Kind.SHOP).isEmpty());
        assertNotEquals(empty, library.signature(), "the game notices new parts by this");
        assertNotNull(library.load(library.parts().get(0)));
        String info = Files.readString(directory.resolve("library/altstadt/window-001.json"));
        assertTrue(info.contains("\"model\": \"some/model\"") && info.contains("\"kind\": \"window\""), info);

        assertTrue(new PartLibrary(new AssetStore(directory), "hafen").parts().isEmpty(), "another district has its own");
    }

    @Test
    void aPictureCopiedIntoTheFolderCountsAndOtherFilesDoNot() throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        library.add(PartLibrary.Kind.SHOP, "<svg/>", picture(), null);
        Files.copy(library.parts().get(0).png(), library.directory().resolve("shop-017.png"));
        Files.writeString(library.directory().resolve("notes.txt"), "x");
        Files.writeString(library.directory().resolve("roof-001.png"), "x");

        assertEquals(List.of(1, 17), library.parts(PartLibrary.Kind.SHOP).stream().map(PartLibrary.Part::number).toList());
        assertEquals(18, library.add(PartLibrary.Kind.SHOP, "<svg/>", picture(), null).number(), "numbers are not reused");
    }

    @Test
    void limitsCanBeChangedPerKindAndRemovedPicturesAreKeptAside() throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        assertEquals(PartLibrary.Kind.BLOSSOM_TREE.defaultTarget(), library.target(PartLibrary.Kind.BLOSSOM_TREE));
        library.setTarget(PartLibrary.Kind.BLOSSOM_TREE, 3);
        library.setTarget(PartLibrary.Kind.LAMP, 999);
        assertEquals(3, library.target(PartLibrary.Kind.BLOSSOM_TREE));
        assertEquals(PartLibrary.MAX_TARGET, library.target(PartLibrary.Kind.LAMP));
        assertEquals(PartLibrary.Kind.DOOR.defaultTarget(), library.target(PartLibrary.Kind.DOOR));

        PartLibrary.Part first = library.add(PartLibrary.Kind.LAMP, "<svg/>", picture(), null);
        library.add(PartLibrary.Kind.LAMP, "<svg/>", picture(), null);
        library.remove(first);
        assertEquals(List.of(2), library.parts(PartLibrary.Kind.LAMP).stream().map(PartLibrary.Part::number).toList());
        try (java.util.stream.Stream<Path> kept = Files.list(library.directory().resolve("deleted"))) {
            assertEquals(3, kept.count(), "picture, source and facts are kept in the folder deleted");
        }
        assertEquals(3, library.add(PartLibrary.Kind.LAMP, "<svg/>", picture(), null).number());
    }

    @Test
    void anSvgPutIntoTheFolderGetsItsPicture() throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        Files.createDirectories(library.directory());
        Files.writeString(library.directory().resolve("green-tree-004.svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 256 512\">"
                        + "<circle cx=\"128\" cy=\"200\" r=\"100\" fill=\"#74C365\"/></svg>");

        List<PartLibrary.Part> parts = library.parts(PartLibrary.Kind.GREEN_TREE);
        assertEquals(1, parts.size());
        assertEquals(4, parts.get(0).number());
        BufferedImage image = library.load(parts.get(0));
        assertEquals(256, image.getWidth());
        assertEquals(512, image.getHeight());
        assertTrue(library.info(parts.get(0)).entrySet().isEmpty(), "nothing is recorded about a picture put there by hand");
    }

    @Test
    void singlePicturesOfEarlierVersionsJoinTheCollectionWithTheirHistory() throws Exception {
        AssetStore store = new AssetStore(directory);
        store.save(AssetCatalog.BLOSSOM_TREE, "<svg>one</svg>", picture(), "model-one");
        store.save(AssetCatalog.BLOSSOM_TREE, "<svg>two</svg>", picture(), "model-two");
        store.save(AssetCatalog.VISITOR_2, "<svg>person</svg>", picture(), "model-two");
        PartLibrary library = new PartLibrary(store, "altstadt");

        assertEquals(3, library.importSingleAssets(store));

        List<PartLibrary.Part> trees = library.parts(PartLibrary.Kind.BLOSSOM_TREE);
        assertEquals(2, trees.size(), "the replaced tree from the history and the current one");
        assertEquals("model-one", library.info(trees.get(0)).get("model").getAsString(), "the older one first");
        assertEquals("model-two", library.info(trees.get(1)).get("model").getAsString());
        assertEquals("blossom-tree", library.info(trees.get(1)).get("importedFrom").getAsString());
        assertEquals(1, library.parts(PartLibrary.Kind.VISITOR).size());
        assertTrue(!store.has(AssetCatalog.BLOSSOM_TREE), "taken once: the files moved away");
        assertTrue(Files.isRegularFile(directory.resolve("imported/blossom-tree.svg")));
        assertEquals(0, library.importSingleAssets(store), "nothing is taken twice");
    }
}
