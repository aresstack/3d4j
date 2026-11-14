package com.aresstack.threed4j.assets;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownPlanTest {

    @Test
    void theDistrictsOfTheStartHaveTheirPlotsAndLandmarks() {
        assertArrayEquals(new int[] {0, 0}, TownPlan.plot(District.BLOSSOM_QUARTER));
        assertArrayEquals(new int[] {0, -1}, TownPlan.plot(District.WORKS_QUARTER));
        assertTrue(TownPlan.landmark(District.BLOSSOM_QUARTER).contains("fountain"));
        assertTrue(TownPlan.landmark(District.WORKS_QUARTER).contains("WEST.SIDE"));
    }

    @Test
    void thePlanNamesEveryDistrictItsLandmarkAndWhatLiesAroundTheNewPlot() {
        String plan = TownPlan.describe(List.of(District.BLOSSOM_QUARTER, District.WORKS_QUARTER), 1, -1);
        assertTrue(plan.contains("- Blütenviertel (plot 0, 0)"), plan);
        assertTrue(plan.contains("Landmark: Springbrunnen"), plan);
        assertTrue(plan.contains("NEW"), plan);
        // The new plot lies north-east of the start: the works to its west, the sea to its east.
        assertTrue(plan.contains("west: the Werksviertel"), plan);
        assertTrue(plan.contains("east: the sea"), plan);
        assertTrue(plan.contains("north: free land"), plan);
        // On the map the works stand above the start, and the new plot beside the works.
        int works = plan.indexOf("| Werksviertel");
        int blossom = plan.indexOf("| Blütenviertel");
        assertTrue(works > 0 && works < blossom, plan);
        assertTrue(plan.indexOf("NEW") > works && plan.indexOf("NEW") < blossom, plan);
    }
}
