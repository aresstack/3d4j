package com.aresstack.threed4j.gfx;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Creates textures from images. */
public final class Textures {

    private Textures() {
    }

    /** Loads a PNG or JPEG from the classpath, e.g. {@code "/images/loading.jpg"}. */
    public static Texture fromResource(GraphicsDevice graphics, String path, boolean smooth) {
        return fromImage(graphics, readResource(path), smooth);
    }

    /** Reads a PNG or JPEG from the classpath as an image, e.g. to paint with it in Java 2D. */
    public static BufferedImage readResource(String path) {
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Image resource not found: " + path);
            }
            // Decode in memory. The default would be a cache file in the temp directory,
            // which can be slow or forbidden on locked-down machines.
            ImageIO.setUseCache(false);
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException("Not a readable image: " + path);
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uploads an image, e.g. one painted with Java 2D. */
    /** As {@link #fromImage}, with the slow part done here, on whatever thread calls; see {@code prepareTexture}. */
    public static GraphicsDevice.PendingTexture prepare(GraphicsDevice graphics, BufferedImage image, boolean smooth) {
        int width = image.getWidth();
        int height = image.getHeight();
        return graphics.prepareTexture(width, height, image.getRGB(0, 0, width, height, null, 0, width), smooth);
    }

    public static Texture fromImage(GraphicsDevice graphics, BufferedImage image, boolean smooth) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        return graphics.createTexture(width, height, argb, smooth);
    }
}
