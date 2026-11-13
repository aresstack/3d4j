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

    /** Destroys the window (and its OpenGL context, if it has one). */
    @Override
    void close();
}
