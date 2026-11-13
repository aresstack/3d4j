package com.aresstack.threed4j.gfx;

/**
 * @param rgb one {@code 0xRRGGBB} value per pixel, top row first
 */
public record PixelImage(int width, int height, int[] rgb) {
}
