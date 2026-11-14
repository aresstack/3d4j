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
    private final StringBuilder typed = new StringBuilder();

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

    /**
     * The characters typed since the previous frame, as the keyboard layout produces
     * them (upper and lower case, umlauts, symbols). Use this for text fields and
     * {@link #wasPressed(Key)} for controls. Backspace arrives as {@code '\b'}
     * (repeating while held), Tab as {@code '\t'}, Enter as {@code '\r'} and
     * Ctrl+V as the character {@link #PASTE} – in the order they were typed, which
     * matters when someone types faster than the game draws.
     */
    public String typedText() {
        return typed.toString();
    }

    /** What Ctrl+V produces in {@link #typedText()}. */
    public static final char PASTE = 0x16;

    /** Called by the platform window for every character the keyboard produced. */
    public void onText(char character) {
        if (character >= ' ' && character != 0x7F || character == PASTE || character == '\b'
                || character == '\t' || character == '\r') {
            typed.append(character);
        }
    }

    /** Called by the engine before events are polled: forgets last frame's presses and movement. */
    public void beginFrame() {
        typed.setLength(0);
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

    /**
     * Called by the platform window while the mouse is captured: movement without a
     * position, as the pointer is put back in the middle after every move.
     */
    public void onMouseDelta(int dx, int dy) {
        mouseDeltaX += dx;
        mouseDeltaY += dy;
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
