package com.aresstack.threed4j.platform;

import java.util.Arrays;

/**
 * Keyboard and mouse state of a window. Written by the platform window while it
 * processes events, read by the game.
 */
public final class Input {

    private final boolean[] down = new boolean[Key.values().length];
    private final boolean[] pressed = new boolean[Key.values().length];

    private final boolean[] mouseDown = new boolean[MouseButton.values().length];
    private final boolean[] mousePressed = new boolean[MouseButton.values().length];
    private int mouseX;
    private int mouseY;
    private int mouseDeltaX;
    private int mouseDeltaY;
    private boolean mouseSeen;

    /** {@code true} while the key is held down. */
    public boolean isDown(Key key) {
        return down[key.ordinal()];
    }

    /** {@code true} only in the frame in which the key went down. */
    public boolean wasPressed(Key key) {
        return pressed[key.ordinal()];
    }

    /** {@code true} while the mouse button is held down. */
    public boolean isDown(MouseButton button) {
        return mouseDown[button.ordinal()];
    }

    /** {@code true} only in the frame in which the mouse button went down. */
    public boolean wasPressed(MouseButton button) {
        return mousePressed[button.ordinal()];
    }

    /** Pointer position in pixels, relative to the top left corner of the drawable area. */
    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    /** Pointer movement since the previous frame, in pixels; positive is right. */
    public int mouseDeltaX() {
        return mouseDeltaX;
    }

    /** Pointer movement since the previous frame, in pixels; positive is down. */
    public int mouseDeltaY() {
        return mouseDeltaY;
    }

    /** Called by the engine before events are polled: forgets last frame's presses and movement. */
    public void beginFrame() {
        Arrays.fill(pressed, false);
        Arrays.fill(mousePressed, false);
        mouseDeltaX = 0;
        mouseDeltaY = 0;
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

    /** Called by the platform window when the pointer moves. */
    public void onMouseMove(int x, int y) {
        if (mouseSeen) {
            mouseDeltaX += x - mouseX;
            mouseDeltaY += y - mouseY;
        }
        mouseSeen = true;
        mouseX = x;
        mouseY = y;
    }

    /** Called by the platform window for every mouse button event; {@code button} may be {@code null}. */
    public void onMouseButton(MouseButton button, boolean isDown) {
        if (button == null) {
            return;
        }
        int index = button.ordinal();
        if (isDown && !mouseDown[index]) {
            mousePressed[index] = true;
        }
        mouseDown[index] = isDown;
    }
}
