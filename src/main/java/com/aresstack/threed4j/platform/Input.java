package com.aresstack.threed4j.platform;

/**
 * Keyboard state of a window. Written by the platform window while it processes
 * events, read by the game.
 */
public final class Input {

    private final boolean[] down = new boolean[Key.values().length];
    private final boolean[] pressed = new boolean[Key.values().length];

    /** {@code true} while the key is held down. */
    public boolean isDown(Key key) {
        return down[key.ordinal()];
    }

    /** {@code true} only in the frame in which the key went down. */
    public boolean wasPressed(Key key) {
        return pressed[key.ordinal()];
    }

    /** Called by the engine before events are polled: forgets last frame's key presses. */
    public void beginFrame() {
        java.util.Arrays.fill(pressed, false);
    }

    /** Called by the platform window for every key event; {@code key} may be {@code null} (unmapped). */
    public void onKey(Key key, boolean isDown) {
        if (key == null) {
            return;
        }
        int index = key.ordinal();
        if (isDown && !down[index]) {
            pressed[index] = true; // ignores auto-repeat
        }
        down[index] = isDown;
    }
}
