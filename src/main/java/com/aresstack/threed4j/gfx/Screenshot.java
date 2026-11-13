package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.ffm.Native;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

import static com.aresstack.threed4j.gl.GL.*;

/** Saves the current frame as a PNG file. */
public final class Screenshot {

    private Screenshot() {
    }

    /** Reads the back buffer – call after rendering and before the buffers are swapped. */
    public static void save(int width, int height, Path file) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixels = Native.bytes(arena, (long) width * height * 4);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < height; y++) {
                long row = (long) (height - 1 - y) * width * 4; // OpenGL rows start at the bottom
                for (int x = 0; x < width; x++) {
                    long offset = row + x * 4L;
                    int r = pixels.get(ValueLayout.JAVA_BYTE, offset) & 0xFF;
                    int g = pixels.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xFF;
                    int b = pixels.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
