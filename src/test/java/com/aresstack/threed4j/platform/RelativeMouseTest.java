package com.aresstack.threed4j.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeMouseTest {

    /** A window of 800 x 800: the middle is 400, 400 and the pointer is put back beyond 200 from it. */
    private static RelativeMouse captured() {
        RelativeMouse mouse = new RelativeMouse();
        mouse.begin(400, 400, 800, 800);
        mouse.expectAtCenter();
        return mouse;
    }

    @Test
    void everyPositionCountsOnceHoweverManyArriveInAFrame() {
        RelativeMouse mouse = captured();
        Input input = new Input();
        assertFalse(mouse.move(400, 400, input)); // arrived in the middle
        mouse.move(410, 400, input);
        mouse.move(430, 395, input);
        mouse.move(460, 390, input);
        assertEquals(60, input.mouseDeltaX(), "not 10 + 30 + 60 as measured from the middle each time");
        assertEquals(-10, input.mouseDeltaY());
    }

    @Test
    void whereThePointerWasBeforeCapturingDoesNotCount() {
        RelativeMouse mouse = captured();
        Input input = new Input();
        mouse.move(50, 700, input); // still where the menu left it
        mouse.move(402, 400, input); // now near the middle
        assertEquals(2, input.mouseDeltaX());
        assertEquals(0, input.mouseDeltaY());
    }

    @Test
    void farFromTheMiddleThePointerIsPutBackAndLeftoversFromBeforeAreLeftOut() {
        RelativeMouse mouse = captured();
        Input input = new Input();
        mouse.move(400, 400, input);
        mouse.move(480, 400, input);
        mouse.move(560, 400, input); // quick, but step by step: this is no leftover
        assertTrue(mouse.move(620, 400, input), "220 from the middle: back it goes");
        assertFalse(mouse.move(650, 400, input), "moved on before the jump took effect: real movement");
        assertEquals(250, input.mouseDeltaX());

        input.beginFrame();
        mouse.move(403, 400, input); // the jump has taken effect
        assertEquals(3, input.mouseDeltaX(), "measured from the middle, not a leap of -247");
        mouse.move(655, 400, input); // a remote session still reports the old place
        mouse.move(406, 401, input);
        assertEquals(6, input.mouseDeltaX(), "no leap there and back");
        assertEquals(1, input.mouseDeltaY());
    }
}
