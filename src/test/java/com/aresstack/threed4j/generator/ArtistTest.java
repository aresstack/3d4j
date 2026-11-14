package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtistTest {

    @Test
    void everyPictureOfAKindIsAskedForDifferentlyAndInTheStyleOfTheDistrict() {
        String first = Artist.description(PartLibrary.Kind.VISITOR, 1, "pastel old town");
        String second = Artist.description(PartLibrary.Kind.VISITOR, 2, "pastel old town");
        assertNotEquals(first, second);
        assertTrue(first.contains("pastel old town") && first.contains("standing upright"));
        assertEquals(first, Artist.description(PartLibrary.Kind.VISITOR, 13, "pastel old town"), "the list starts again");
        assertTrue(Artist.description(PartLibrary.Kind.LAWN, 1, "x").contains("base colour"));
        assertTrue(Artist.description(PartLibrary.Kind.BLOSSOM_TREE, 2, "x").contains("cherry"));
    }

    @Test
    void theBackgroundDrawsWhatIsFurthestFromItsLimitAndStopsWhenAllLimitsAreReached(@TempDir Path directory)
            throws Exception {
        PartLibrary library = new PartLibrary(new AssetStore(directory), "altstadt");
        BufferedImage picture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
            library.setTarget(kind, 2);
            library.add(kind, null, picture, null);
        }
        library.setTarget(PartLibrary.Kind.LAMP, 4);
        assertEquals(PartLibrary.Kind.LAMP, BackgroundArtist.next(library), "one of four is less than one of two");

        library.add(PartLibrary.Kind.LAMP, null, picture, null);
        library.add(PartLibrary.Kind.LAMP, null, picture, null);
        library.add(PartLibrary.Kind.LAMP, null, picture, null);
        for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
            if (kind != PartLibrary.Kind.LAMP && kind != PartLibrary.Kind.DOOR) {
                library.add(kind, null, picture, null);
            }
        }
        assertEquals(PartLibrary.Kind.DOOR, BackgroundArtist.next(library));
        library.setTarget(PartLibrary.Kind.DOOR, 0);
        assertNull(BackgroundArtist.next(library), "a limit of zero means: none of this kind");
    }
}
