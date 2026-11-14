package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObstaclesTest {

    private static Obstacles town() {
        Obstacles obstacles = new Obstacles();
        obstacles.box(10f, 10f, 20f, 20f, 8f);  // a house
        obstacles.post(0f, 0f, 5f, 6f);         // the fountain
        return obstacles;
    }

    @Test
    void walkingIntoAWallStopsInFrontOfItAndSlidesAlong() {
        Vec3 from = new Vec3(9f, 2.2f, 14f);
        Vec3 moved = town().move(from, new Vec3(10.5f, 2.2f, 14.4f), 0.7f);

        assertEquals(9.3f, moved.x(), 0.0001f); // held in front of the wall at x = 10
        assertEquals(14.4f, moved.z(), 0.0001f); // but the step along the wall counts
    }

    @Test
    void aPlaceInsideARoundObstacleIsMovedToItsEdgeInTheSameDirection() {
        Vec3 moved = town().move(new Vec3(6f, 2.2f, 8f), new Vec3(3f, 2.2f, 4f), 0.7f);

        assertEquals(5.7, Math.hypot(moved.x(), moved.z()), 0.001);
        assertEquals(3.0 / 4.0, moved.x() / moved.z(), 0.001);
    }

    @Test
    void theWayIsFreeBesideAndAboveObstacles() {
        Obstacles town = town();
        Vec3 beside = new Vec3(25f, 2.2f, 15f);
        Vec3 above = new Vec3(15f, 9f, 15f);

        assertSame(beside, town.move(new Vec3(26f, 2.2f, 15f), beside, 0.7f));
        assertSame(above, town.move(new Vec3(15f, 9.5f, 15f), above, 0.7f));
    }

    @Test
    void whoComesDownFromAboveStaysOnTheRoof() {
        Vec3 moved = town().move(new Vec3(15f, 8.7f, 15f), new Vec3(15f, 8.2f, 15f), 0.7f);

        assertEquals(15f, moved.x(), 0.0001f);
        assertEquals(8f + Obstacles.EYE_ABOVE, moved.y(), 0.0001f);
    }

    @Test
    void theOfficesAndTheCarParkOfWestSideAreSolidAndTheYardIsFree() {
        Obstacles obstacles = new Obstacles();
        WestSide.obstacles(obstacles, -116f);

        assertEquals(3, obstacles.count());
        // In the yard between the two office buildings the way is free.
        Vec3 yard = new Vec3(0f, 2.2f, -140f);
        assertSame(yard, obstacles.move(new Vec3(0f, 2.2f, -139f), yard, 0.7f));
        // Building A stands to the right of it.
        Vec3 moved = obstacles.move(new Vec3(12f, 2.2f, -140f), new Vec3(15f, 2.2f, -140f), 0.7f);
        assertEquals(13.3f, moved.x(), 0.0001f);
    }
}
