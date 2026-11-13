package com.aresstack.threed4j.assets;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The folder with artwork files. For every {@link AssetSpec} it may hold
 * {@code <id>.svg} (the source, written by the generator or by hand) and
 * {@code <id>.png} (what the game loads). A PNG is made from the SVG whenever the
 * SVG is newer, so dropping an SVG file into the folder is enough. Generated
 * artwork also gets {@code <id>.json} with its origin, and what it replaces moves
 * to {@code history/<id>/}.
 *
 * <p>Default location: {@code .3d4j/assets} in the user's home directory; the
 * system property {@code threed4j.assets} names another folder.</p>
 */
public final class AssetStore {

    public static final String DIRECTORY_PROPERTY = "threed4j.assets";

    private static final DateTimeFormatter HISTORY_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

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

    /** Facts about the stored artwork ({@code <id>.json}): which model drew it, when, from which description. */
    public Path infoFile(AssetSpec spec) {
        return directory.resolve(spec.id() + ".json");
    }

    /** Where replaced artwork of this asset is kept: {@code history/<id>/}. */
    public Path historyDirectory(AssetSpec spec) {
        return directory.resolve("history").resolve(spec.id());
    }

    /** Stores generated artwork whose origin is not known. */
    public void save(AssetSpec spec, String svg, BufferedImage image) throws IOException {
        save(spec, svg, image, null);
    }

    /**
     * Stores generated artwork: the SVG source, the picture made from it and a small
     * JSON file with its origin. Artwork that is replaced is not lost: it moves to
     * {@link #historyDirectory} first.
     *
     * @param model the model that drew it, or {@code null} if unknown
     */
    public void save(AssetSpec spec, String svg, BufferedImage image, String model) throws IOException {
        Files.createDirectories(directory);
        archive(spec);
        Files.writeString(svgFile(spec), svg, StandardCharsets.UTF_8);
        writePng(image, pngFile(spec));
        JsonObject info = new JsonObject();
        info.addProperty("id", spec.id());
        info.addProperty("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        info.addProperty("model", model == null ? "" : model);
        info.addProperty("description", spec.description());
        info.addProperty("width", spec.width());
        info.addProperty("height", spec.height());
        Files.writeString(infoFile(spec), new GsonBuilder().setPrettyPrinting().create().toJson(info) + "\n",
                StandardCharsets.UTF_8);
    }

    /**
     * Moves the current files of an asset to its history, named after the moment the
     * artwork was made: {@code history/<id>/<yyyyMMdd-HHmmss>.svg|.png|.json}.
     *
     * @return the common path of the moved files without extension, or {@code null} if there was nothing to keep
     */
    Path archive(AssetSpec spec) throws IOException {
        Path svg = svgFile(spec);
        Path png = pngFile(spec);
        Path info = infoFile(spec);
        Path oldest = Files.isRegularFile(svg) ? svg : Files.isRegularFile(png) ? png : null;
        if (oldest == null) {
            return null;
        }
        // The file time says when the artwork was made (also for a file put there by hand).
        String stamp = HISTORY_STAMP.format(Files.getLastModifiedTime(oldest).toInstant());
        Path history = historyDirectory(spec);
        Files.createDirectories(history);
        Path base = history.resolve(stamp);
        for (int copy = 2; Files.exists(withExtension(base, ".svg")) || Files.exists(withExtension(base, ".png")); copy++) {
            base = history.resolve(stamp + "-" + copy);
        }
        for (String[] move : new String[][] {{".svg"}, {".png"}, {".json"}}) {
            Path from = move[0].equals(".svg") ? svg : move[0].equals(".png") ? png : info;
            if (Files.isRegularFile(from)) {
                Files.move(from, withExtension(base, move[0]));
            }
        }
        return base;
    }

    private static Path withExtension(Path base, String extension) {
        return base.resolveSibling(base.getFileName() + extension);
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
