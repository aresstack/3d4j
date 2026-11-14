package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.gfx.MeshBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WestSideTest {

    /** The sketch model has about 23,000 triangles; the rebuilt one must stay cheap for a software renderer. */
    @Test
    void theEnsembleStaysWithinItsTriangleBudget() {
        MeshBuilder builder = new MeshBuilder();
        WestSide.build(builder, -116f);
        int triangles = builder.triangleCount();
        assertTrue(triangles > 1500 && triangles < 4500, triangles + " triangles");
    }
}
