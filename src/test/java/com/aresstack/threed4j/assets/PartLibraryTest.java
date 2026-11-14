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
}
