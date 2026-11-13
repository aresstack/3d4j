package com.aresstack.threed4j.platform;

/**
 * A native window with a current OpenGL 3.3 core context.
 *
 * <p>All methods must be called from the thread that created the window.</p>
 */
public interface Window extends AutoCloseable {

    /** Processes pending window and input events without blocking. */
    void pollEvents();

    /** Shows the frame that was just rendered. */
    void swapBuffers();

    /** {@code true} once the user closed the window or {@link #requestClose()} was called. */
    boolean shouldClose();

    void requestClose();

    /** Current width of the drawable area in pixels. */
    int width();

    /** Current height of the drawable area in pixels. */
    int height();

    Input input();

    void setTitle(String title);

    /** Destroys the OpenGL context and the window. */
    @Override
    void close();
}
