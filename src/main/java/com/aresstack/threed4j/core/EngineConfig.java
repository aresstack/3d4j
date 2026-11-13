package com.aresstack.threed4j.core;

import com.aresstack.threed4j.platform.WindowConfig;

/**
 * @param width     requested width of the drawable area in pixels
 * @param height    requested height of the drawable area in pixels
 * @param vsync     synchronize with the display refresh rate if the driver supports it
 * @param maxFrames stop after this many frames; 0 runs until the window is closed
 *                  (useful for automated smoke tests)
 */
public record EngineConfig(String title, int width, int height, boolean vsync, long maxFrames) {

    public static EngineConfig of(String title, int width, int height) {
        return new EngineConfig(title, width, height, true, 0);
    }

    public EngineConfig withVsync(boolean enabled) {
        return new EngineConfig(title, width, height, enabled, maxFrames);
    }

    public EngineConfig withMaxFrames(long frames) {
        return new EngineConfig(title, width, height, vsync, frames);
    }

    WindowConfig window() {
        return new WindowConfig(title, width, height, vsync);
    }
}
