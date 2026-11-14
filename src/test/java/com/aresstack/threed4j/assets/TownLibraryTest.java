package com.aresstack.threed4j.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownLibraryTest {

    @TempDir
    Path directory;

    private static BufferedImage picture() {
        return new BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void everyDistrictHasItsCollectionAndThePeopleShareOne() {
        TownLibrary town = new TownLibrary(new AssetStore(directory));
        assertTrue(PartLibrary.Kind.VISITOR.shared());
        assertSame(town.shared(), town.of(District.WORKS_QUARTER, PartLibrary.Kind.VISITOR));
        assertSame(town.district(District.WORKS_QUARTER), town.of(District.WORKS_QUARTER, PartLibrary.Kind.WINDOW));
        assertEquals(directory.resolve("library/werksviertel"), town.district(District.WORKS_QUARTER).directory());
        assertEquals(directory.resolve("library/stadt"), town.shared().directory());
        assertEquals(List.of(PartLibrary.Kind.VISITOR, PartLibrary.Kind.SEA_ANIMAL), TownLibrary.sharedKinds());
        assertEquals(PartLibrary.Kind.values().length - 2, TownLibrary.districtKinds().size());
    }

    @Test
    void theSeaLiesEastOfACurvedCoastAndTheTownDoesNotGrowIntoIt() {
        assertEquals(200f, District.coastX(0f), 0.001f, "a headland in the middle of the plot");
        assertEquals(140f, District.coastX(100f), 0.001f, "a bay where two plots meet");
        assertTrue(District.onLand(150f, 0f, 10f));
        assertTrue(!District.onLand(195f, 0f, 10f), "too near the water");
        assertTrue(!District.onLand(150f, 100f, 0f), "water in the bay");
        assertTrue(District.coastal(1) && !District.coastal(0));
        for (int[] plot : District.plotOrder()) {
            assertTrue(plot[0] <= 1, "no plot out in the sea");
        }
    }

    @Test
    void peopleKeptInADistrictMoveToTheSharedCollectionWithTheirLimit() throws Exception {
        TownLibrary town = new TownLibrary(new AssetStore(directory));
        PartLibrary blossom = town.district(District.BLOSSOM_QUARTER);
        blossom.add(PartLibrary.Kind.VISITOR, "<svg/>", picture(), null);
        blossom.add(PartLibrary.Kind.VISITOR, null, picture(), null);
        blossom.add(PartLibrary.Kind.LAMP, null, picture(), null);
        blossom.setTarget(PartLibrary.Kind.VISITOR, 17);
        String before = town.signature();

        assertEquals(2, town.gatherSharedKinds());

        assertTrue(blossom.parts(PartLibrary.Kind.VISITOR).isEmpty());
        assertEquals(1, blossom.parts(PartLibrary.Kind.LAMP).size(), "what belongs to the district stays");
        assertEquals(List.of(1, 2),
                town.shared().parts(PartLibrary.Kind.VISITOR).stream().map(PartLibrary.Part::number).toList());
        assertTrue(Files.isRegularFile(directory.resolve("library/stadt/visitor-001.svg")));
        assertTrue(Files.isRegularFile(directory.resolve("library/stadt/visitor-001.json")));
        assertEquals(17, town.shared().target(PartLibrary.Kind.VISITOR));
        assertNotEquals(before, town.signature());
        assertEquals(0, town.gatherSharedKinds(), "nothing is left to move");
    }

    @Test
    void theWerksviertelLiesBeyondTheBorderAndGivesEveryKindAMeaningOfItsOwn() {
        assertSame(District.BLOSSOM_QUARTER, District.at(0f, 34f));
        assertSame(District.BLOSSOM_QUARTER, District.at(40f, -99f));
        assertSame(District.WORKS_QUARTER, District.at(0f, -116f));
        for (District district : District.ALL) {
            for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
                District.Motif motif = district.motif(kind);
                assertNotNull(motif, district.name() + " " + kind);
                assertTrue(!motif.name().isBlank() && !motif.plural().isBlank() && !motif.variations().isEmpty());
            }
        }
        assertEquals("Birke", District.WORKS_QUARTER.motif(PartLibrary.Kind.BLOSSOM_TREE).name());
        assertEquals("Tor", District.WORKS_QUARTER.motif(PartLibrary.Kind.SHOP).name());
        assertTrue(District.BLOSSOM_QUARTER.flowerBushes());
        assertTrue(!District.WORKS_QUARTER.flowerBushes());
    }
}
