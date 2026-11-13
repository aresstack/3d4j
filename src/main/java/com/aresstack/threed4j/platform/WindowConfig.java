package com.aresstack.threed4j.platform;

/**
 * @param width  requested width of the drawable area in pixels
 * @param height requested height of the drawable area in pixels
 * @param vsync  synchronize buffer swaps with the display refresh, if the driver supports it
 */
public record WindowConfig(String title, int width, int height, boolean vsync) {
}
