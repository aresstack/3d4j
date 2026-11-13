package com.aresstack.threed4j.gfx;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Saves a frame as a PNG file. */
public final class Screenshot {

    private Screenshot() {
    }

    public static void save(PixelImage frame, Path file) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.rgb(), 0, frame.width());
        ImageIO.write(image, "png", file.toFile());
    }
}
