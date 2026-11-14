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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The collection of building parts of one district: windows, doors and shop
 * fronts, each a small picture with a transparent surrounding. Houses take their
 * parts from here, so the collection decides how a district looks; it grows
 * while the game runs.
 *
 * <p>Files: {@code library/<district>/<kind>-<number>.svg|.png|.json} below the
 * asset folder; the JSON file says which model drew the part, when, and with which
 * instruction. A picture put there by hand under such a name counts as well.</p>
 */
public final class PartLibrary {

    /** What a part is, and how many of that kind a district collects. */
    public enum Kind {
        WINDOW(8), DOOR(4), SHOP(4);

        private final int target;

        Kind(int target) {
            this.target = target;
        }

        /** How many parts of this kind a district collects before it stops asking for more. */
        public int target() {
            return target;
        }

        public String fileName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** @param number distinguishes the parts of a kind; stays the same for as long as the file exists */
    public record Part(Kind kind, int number, Path png) {
    }

    /** Edge length of a part's picture in pixels. */
    public static final int SIZE = 128;

    private static final Pattern FILE = Pattern.compile("(window|door|shop)-(\\d+)\\.png");

    private final Path directory;

    public PartLibrary(AssetStore store, String district) {
        this.directory = store.directory().resolve("library").resolve(district);
    }

    public Path directory() {
        return directory;
    }

    /** All parts of the district, by kind and number. */
    public List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return parts;
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher name = FILE.matcher(file.getFileName().toString());
                if (name.matches()) {
                    parts.add(new Part(Kind.valueOf(name.group(1).toUpperCase(Locale.ROOT)),
                            Integer.parseInt(name.group(2)), file));
                }
            }
        } catch (IOException | NumberFormatException e) {
            return new ArrayList<>(); // an unreadable folder is treated like an empty one
        }
        parts.sort(Comparator.comparing(Part::kind).thenComparingInt(Part::number));
        return parts;
    }

    public List<Part> parts(Kind kind) {
        return parts().stream().filter(part -> part.kind() == kind).toList();
    }

    /** Changes whenever a part is added or removed; cheap enough to ask once a second. */
    public String signature() {
        StringBuilder signature = new StringBuilder();
        for (Part part : parts()) {
            signature.append(part.kind().fileName()).append(part.number()).append(' ');
        }
        return signature.toString();
    }

    /** @return the picture, or {@code null} if the file cannot be read as an image */
    public BufferedImage load(Part part) {
        try {
            ImageIO.setUseCache(false);
            return ImageIO.read(part.png().toFile());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Adds a part under the next free number of its kind.
     *
     * @param details facts for the JSON file besides kind, number and time, e.g. model and instruction
     */
    public synchronized Part add(Kind kind, String svg, BufferedImage image, JsonObject details) throws IOException {
        Files.createDirectories(directory);
        int number = parts(kind).stream().mapToInt(Part::number).max().orElse(0) + 1;
        String base = kind.fileName() + "-" + String.format(Locale.ROOT, "%03d", number);
        Files.writeString(directory.resolve(base + ".svg"), svg, StandardCharsets.UTF_8);
        JsonObject info = new JsonObject();
        info.addProperty("kind", kind.fileName());
        info.addProperty("number", number);
        info.addProperty("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        if (details != null) {
            details.entrySet().forEach(entry -> info.add(entry.getKey(), entry.getValue()));
        }
        Files.writeString(directory.resolve(base + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(info) + "\n", StandardCharsets.UTF_8);
        // The picture last: it is what makes the part visible to the game.
        Path png = directory.resolve(base + ".png");
        ImageIO.setUseCache(false);
        ImageIO.write(image, "png", png.toFile());
        return new Part(kind, number, png);
    }
}
