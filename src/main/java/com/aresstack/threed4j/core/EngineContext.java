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
    private final FrameStats frameStats = new FrameStats();
    private FrameStats.Summary lastSecond = new FrameStats.Summary(0, 0, 0, 0, 0);
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

    /** Frame times of this run; {@code frameStats().total()} gives average and percentiles. */
    public FrameStats frameStats() {
        return frameStats;
    }

    /** Frame time statistics of the last second, as shown in the window title. */
    public FrameStats.Summary lastSecond() {
        return lastSecond;
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

    void lastSecond(FrameStats.Summary summary) {
        this.lastSecond = summary;
    }

    Path takePendingScreenshot() {
        Path file = pendingScreenshot;
        pendingScreenshot = null;
        return file;
    }
}
