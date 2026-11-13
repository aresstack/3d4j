package com.aresstack.threed4j.core;

import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Window;

import java.nio.file.Path;

/** What the engine offers a {@link Game} during a frame. */
public final class EngineContext {

    private final Window window;
    private final GraphicsDevice graphics;
    private double timeSeconds;
    private long frame;
    private double framesPerSecond;
    private Path pendingScreenshot;

    EngineContext(Window window, GraphicsDevice graphics) {
        this.window = window;
        this.graphics = graphics;
    }

    /** Creates shaders and meshes, clears and draws. */
    public GraphicsDevice graphics() {
        return graphics;
    }

    public Window window() {
        return window;
    }

    public Input input() {
        return window.input();
    }

    /** Width of the drawable area divided by its height. */
    public float aspectRatio() {
        return (float) window.width() / Math.max(1, window.height());
    }

    /** Seconds since the first frame. */
    public double timeSeconds() {
        return timeSeconds;
    }

    /** Number of the current frame, starting at 1. */
    public long frame() {
        return frame;
    }

    /** Average of the last second; 0 until the first second has passed. */
    public double framesPerSecond() {
        return framesPerSecond;
    }

    /** Saves the frame that is currently being produced as a PNG once it has been rendered. */
    public void requestScreenshot(Path file) {
        pendingScreenshot = file;
    }

    /** Ends the game loop after the current frame. */
    public void quit() {
        window.requestClose();
    }

    void beginFrame(double timeSeconds) {
        this.timeSeconds = timeSeconds;
        this.frame++;
    }

    void framesPerSecond(double value) {
        this.framesPerSecond = value;
    }

    Path takePendingScreenshot() {
        Path file = pendingScreenshot;
        pendingScreenshot = null;
        return file;
    }
}
