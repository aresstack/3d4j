package com.aresstack.threed4j.platform;

/**
 * A native window with keyboard input. What draws into it is a
 * {@code GraphicsDevice}.
 *
 * <p>All methods must be called from the thread that created the window.</p>
 */
public interface Window extends AutoCloseable {

    /** Processes pending window and input events without blocking. */
    void pollEvents();

    /** {@code true} once the user closed the window or {@link #requestClose()} was called. */
    boolean shouldClose();

    void requestClose();

    /** Current width of the drawable area in pixels. */
    int width();

    /** Current height of the drawable area in pixels. */
    int height();

    Input input();

    void setTitle(String title);

    /**
     * Fills the screen the window is on, without a frame, or goes back to the window
     * it was. Alt+Enter switches too; the game sees it in {@link #fullscreen()}.
     */
    default void setFullscreen(boolean fullscreen) {
    }

    default boolean fullscreen() {
        return false;
    }

    /**
     * The screens there are, from left to right: each as x, y, width and height in
     * pixels of the desktop. At least one.
     */
    default java.util.List<int[]> monitors() {
        return java.util.List.of(new int[] {0, 0, width(), height()});
    }

    /** The index in {@link #monitors()} of the screen the window is on. */
    default int monitor() {
        return 0;
    }

    /** Moves the window to a screen: it fills that one if it is full screen, otherwise it is centered there. */
    default void setMonitor(int index) {
    }

    /**
     * Captures the mouse for looking around: the pointer disappears and stays
     * inside the window, and its movement arrives as {@link Input#mouseDeltaX()} and
     * {@link Input#mouseDeltaY()} without end at the screen's edge. Call it every frame
     * with what is wanted: a window that lost the keyboard lets the mouse go and takes
     * it again when it is back in front.
     */
    default void captureMouse(boolean capture) {
    }

    /**
     * The pointer shown over the window while the mouse is not captured.
     *
     * @param image    the picture with transparency, e.g. 48 x 48; {@code null} for the system's arrow
     * @param hotspotX the pixel of the picture that points, with {@code hotspotY}
     */
    default void setCursor(java.awt.image.BufferedImage image, int hotspotX, int hotspotY) {
    }

    /** Destroys the window (and its OpenGL context, if it has one). */
    @Override
    void close();
}
