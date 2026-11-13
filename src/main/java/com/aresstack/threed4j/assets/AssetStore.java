package com.aresstack.threed4j.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The folder with artwork files. For every {@link AssetSpec} it may hold
 * {@code <id>.svg} (the source, written by the generator or by hand) and
 * {@code <id>.png} (what the game loads). A PNG is made from the SVG whenever the
 * SVG is newer, so dropping an SVG file into the folder is enough.
 *
 * <p>Default location: {@code .3d4j/assets} in the user's home directory; the
 * system property {@code threed4j.assets} names another folder.</p>
 */
public final class AssetStore {

    public static final String DIRECTORY_PROPERTY = "threed4j.assets";

    private final Path directory;

    public AssetStore(Path directory) {
        this.directory = directory;
    }

    public static AssetStore standard() {
        String configured = System.getProperty(DIRECTORY_PROPERTY);
        return new AssetStore(configured != null && !configured.isBlank()
                ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".3d4j", "assets"));
    }

    public Path directory() {
        return directory;
    }

    public Path svgFile(AssetSpec spec) {
        return directory.resolve(spec.id() + ".svg");
    }

    public Path pngFile(AssetSpec spec) {
        return directory.resolve(spec.id() + ".png");
    }

    public boolean has(AssetSpec spec) {
        return Files.isRegularFile(svgFile(spec)) || Files.isRegularFile(pngFile(spec));
    }

    /** Stores generated artwork: the SVG source and the picture made from it. */
    public void save(AssetSpec spec, String svg, BufferedImage image) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(svgFile(spec), svg, StandardCharsets.UTF_8);
        writePng(image, pngFile(spec));
    }

    /**
     * Loads the picture for an asset, rendering it from the SVG first if that is
     * newer than the PNG or the PNG is missing.
     *
     * @return the picture, or {@code null} if the folder has no usable file for it;
     *         the reason for an unusable file is printed
     */
    public BufferedImage load(AssetSpec spec) {
        Path svg = svgFile(spec);
        Path png = pngFile(spec);
        try {
            if (Files.isRegularFile(svg) && (!Files.isRegularFile(png)
                    || Files.getLastModifiedTime(svg).compareTo(Files.getLastModifiedTime(png)) > 0)) {
                String source = SvgRasterizer.extract(Files.readString(svg, StandardCharsets.UTF_8));
                if (source.isEmpty()) {
                    System.out.println("Asset " + svg + " contains no SVG, using the built-in picture");
                    return null;
                }
                BufferedImage image = SvgRasterizer.render(source, spec.width(), spec.height());
                writePng(image, png);
                return image;
            }
            if (Files.isRegularFile(png)) {
                ImageIO.setUseCache(false);
                BufferedImage image = ImageIO.read(png.toFile());
                if (image != null) {
                    return image;
                }
                System.out.println("Asset " + png + " is not a readable image, using the built-in picture");
            }
        } catch (IOException | SvgRasterizer.UnreadableSvgException e) {
            System.out.println("Asset " + spec.id() + " in " + directory + " is unusable (" + e.getMessage()
                    + "), using the built-in picture");
        }
        return null;
    }

    private static void writePng(BufferedImage image, Path file) throws IOException {
        ImageIO.setUseCache(false);
        ImageIO.write(image, "png", file.toFile());
    }
}
