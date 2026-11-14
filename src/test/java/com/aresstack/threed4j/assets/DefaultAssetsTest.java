package com.aresstack.threed4j.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAssetsTest {

    @TempDir
    Path directory;

    @Test
    void everyListedFileIsInTheProgram() {
        List<String> files = DefaultAssets.files();
        assertTrue(files.size() > 100, files.size() + " files");
        for (String file : files) {
            assertNotNull(DefaultAssets.class.getResource("/defaults/" + file), file);
        }
    }

    @Test
    void theTownStartsWithPeoplePicturesAndAPlannedDistrictAndOffersEachFileOnce() throws Exception {
        AssetStore store = new AssetStore(directory);
        assertEquals(DefaultAssets.files().size(), DefaultAssets.install(store));

        TownLibrary town = new TownLibrary(store);
        assertTrue(town.shared().parts(PartLibrary.Kind.VISITOR).size() >= 20);
        assertFalse(town.district(District.WORKS_QUARTER).parts(PartLibrary.Kind.WINDOW).isEmpty());
        District planned = town.district("wellenviertel");
        assertNotNull(planned, "the planned district the game brings along");
        assertEquals("Leuchtturm", planned.layout().landmark().name());
        assertTrue(planned.layout().landmark().height() > 20f);

        // A picture the player removes does not come back, and nothing is copied twice.
        PartLibrary.Part person = town.shared().parts(PartLibrary.Kind.VISITOR).get(0);
        town.shared().remove(person);
        assertEquals(0, DefaultAssets.install(store));
        assertFalse(Files.exists(person.png()));
    }

    @Test
    void whatIsAlreadyThereIsLeftAlone() throws Exception {
        AssetStore store = new AssetStore(directory);
        Path own = directory.resolve("library/stadt/visitor-001.json");
        Files.createDirectories(own.getParent());
        Files.writeString(own, "{\"mine\": true}");

        DefaultAssets.install(store);

        assertEquals("{\"mine\": true}", Files.readString(own));
    }
}
