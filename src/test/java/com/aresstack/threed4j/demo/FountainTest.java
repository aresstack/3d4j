package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FountainTest {

    @Test
    void aPlaceInsideTheFountainIsMovedToItsEdgeInTheSameDirection() {
        Vec3 moved = PlazaScene.outsideFountain(new Vec3(3f, 2.2f, 4f), 0.7f);

        assertEquals(PlazaScene.FOUNTAIN_RADIUS + 0.7f, Math.hypot(moved.x(), moved.z()), 0.001);
        assertEquals(3.0 / 4.0, moved.x() / moved.z(), 0.001);
        assertEquals(2.2f, moved.y(), 0.0001f);
    }

    @Test
    void aPlaceBesideOrAboveTheFountainStaysWhereItIs() {
        Vec3 beside = new Vec3(6.5f, 2.2f, 0f);
        Vec3 above = new Vec3(0f, PlazaScene.FOUNTAIN_HEIGHT + 1f, 0f);

        assertSame(beside, PlazaScene.outsideFountain(beside, 0.7f));
        assertSame(above, PlazaScene.outsideFountain(above, 0.7f));
    }

    @Test
    void theExactMiddleIsMovedOutToo() {
        Vec3 moved = PlazaScene.outsideFountain(new Vec3(0f, 2.2f, 0f), 0.7f);

        assertEquals(PlazaScene.FOUNTAIN_RADIUS + 0.7f, Math.hypot(moved.x(), moved.z()), 0.001);
    }
}
