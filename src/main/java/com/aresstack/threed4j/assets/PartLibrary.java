package com.aresstack.threed4j.assets;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The collection of artwork of one district, the game's picture database: trees,
 * lamps, people, flower bushes, ground textures and the parts of house fronts.
 * Of every kind there are several pictures; what stands in the district chooses
 * among them, so the collection decides how the district looks. It grows while
 * the game runs, up to a limit per kind, and nothing in it is overwritten.
 *
 * <p>Files below the asset folder: {@code library/<district>/<kind>-<number>.svg|.png|.json}.
 * The JSON file says which model drew the picture, when, and with which
 * instruction. A picture put there by hand under such a name counts as well; an
 * SVG without a PNG is turned into one. {@code limits.properties} holds the
 * limits that differ from the defaults and which lamp is in use; removed pictures move to
 * {@code deleted/}.</p>
 */
public final class PartLibrary {

    /** What a picture shows, how large it is, and how many of the kind a district collects by default. */
    public enum Kind {
        BLOSSOM_TREE(10, 256, 512), GREEN_TREE(6, 256, 512), LAMP(1, 256, 512), VISITOR(50, 256, 512),
        BUSH(6, 256, 256), LAWN(3, 256, 256), STREET(3, 256, 256),
        WINDOW(8, 128, 128), DOOR(4, 128, 128), SHOP(4, 128, 128);

        private final int defaultTarget;
        private final int width;
        private final int height;

        Kind(int defaultTarget, int width, int height) {
            this.defaultTarget = defaultTarget;
            this.width = width;
            this.height = height;
        }

        public int defaultTarget() {
            return defaultTarget;
        }

        /**
         * Whether a district shows one and the same picture of this kind everywhere:
         * its street lamps are all of one make. The collection may hold several; the
         * one in use is chosen (see {@link PartLibrary#chosen}).
         */
        public boolean uniform() {
            return this == LAMP;
        }

        /**
         * Whether pictures of this kind belong to the whole town instead of a district:
         * its people are at home everywhere (see {@link TownLibrary}).
         */
        public boolean shared() {
            return this == VISITOR;
        }

        /** Pixel size a picture of this kind is rendered at. */
        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** The kind as it appears in file names, e.g. {@code blossom-tree}. */
        public String fileName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        static Kind ofFileName(String name) {
            for (Kind kind : values()) {
                if (kind.fileName().equals(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** @param number distinguishes the pictures of a kind; stays the same for as long as the file exists */
    public record Part(Kind kind, int number, Path png) {
    }

    /** Edge length of a house part's picture (window, door, shop front) in pixels. */
    public static final int SIZE = 128;
    /** The most pictures of one kind a district may be set to collect. */
    public static final int MAX_TARGET = 99;

    private static final Pattern FILE = Pattern.compile("([a-z-]+)-(\\d+)\\.(png|svg)");

    private final Path directory;

    public PartLibrary(AssetStore store, String district) {
        this.directory = store.directory().resolve("library").resolve(district);
    }

    public Path directory() {
        return directory;
    }

    /** All pictures of the district, by kind and number. */
    public List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return parts;
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.toList();
        } catch (IOException e) {
            return parts; // an unreadable folder is treated like an empty one
        }
        for (Path file : files) {
            Matcher name = FILE.matcher(file.getFileName().toString());
            Kind kind = name.matches() ? Kind.ofFileName(name.group(1)) : null;
            if (kind == null) {
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(name.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            if (name.group(3).equals("png")) {
                parts.add(new Part(kind, number, file));
            } else {
                // An SVG put there by hand: make its picture, which shows up at the next look.
                Path png = file.resolveSibling(file.getFileName().toString().replaceFirst("\\.svg$", ".png"));
                if (!Files.exists(png)) {
                    render(file, png, kind);
                    if (Files.exists(png)) {
                        parts.add(new Part(kind, number, png));
                    }
                }
            }
        }
        parts.sort(Comparator.comparing(Part::kind).thenComparingInt(Part::number));
        return parts;
    }

    private static void render(Path svg, Path png, Kind kind) {
        try {
            String source = SvgRasterizer.extract(Files.readString(svg, StandardCharsets.UTF_8));
            if (source.isEmpty()) {
                return;
            }
            ImageIO.setUseCache(false);
            ImageIO.write(SvgRasterizer.render(source, kind.width(), kind.height()), "png", png.toFile());
        } catch (IOException | SvgRasterizer.UnreadableSvgException e) {
            System.out.println("Library: " + svg + " is unusable (" + e.getMessage() + ")");
        }
    }

    public List<Part> parts(Kind kind) {
        return parts().stream().filter(part -> part.kind() == kind).toList();
    }

    /** Changes whenever a picture is added or removed; cheap enough to ask once a second. */
    public String signature() {
        StringBuilder signature = new StringBuilder();
        List<Part> parts = parts();
        for (Part part : parts) {
            signature.append(part.kind().fileName()).append(part.number()).append(' ');
        }
        Properties settings = settings();
        for (Kind kind : Kind.values()) {
            if (kind.uniform()) {
                signature.append(kind.fileName()).append('=').append(settings.getProperty(kind.fileName() + ".chosen", ""))
                        .append(' ');
            }
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

    /** What is recorded about a picture (model, time, instruction); empty for a picture put there by hand. */
    public JsonObject info(Part part) {
        Path json = sibling(part, ".json");
        try {
            return Files.isRegularFile(json)
                    ? JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject()
                    : new JsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    private static Path sibling(Part part, String extension) {
        return part.png().resolveSibling(part.png().getFileName().toString().replaceFirst("\\.png$", extension));
    }

    /**
     * Adds a picture under the next free number of its kind.
     *
     * @param svg     the source, or {@code null} if there is only the picture
     * @param details facts for the JSON file besides kind, number and time, e.g. model and instruction
     */
    public synchronized Part add(Kind kind, String svg, BufferedImage image, JsonObject details) throws IOException {
        Files.createDirectories(directory);
        int number = parts(kind).stream().mapToInt(Part::number).max().orElse(0) + 1;
        String base = kind.fileName() + "-" + String.format(Locale.ROOT, "%03d", number);
        // The picture first: an SVG without its picture would be rendered again by a reader in between.
        Path png = directory.resolve(base + ".png");
        ImageIO.setUseCache(false);
        ImageIO.write(image, "png", png.toFile());
        if (svg != null) {
            Files.writeString(directory.resolve(base + ".svg"), svg, StandardCharsets.UTF_8);
        }
        JsonObject info = new JsonObject();
        info.addProperty("kind", kind.fileName());
        info.addProperty("number", number);
        if (details == null || !details.has("createdAt")) {
            info.addProperty("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        }
        if (details != null) {
            details.entrySet().forEach(entry -> info.add(entry.getKey(), entry.getValue()));
        }
        Files.writeString(directory.resolve(base + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(info) + "\n", StandardCharsets.UTF_8);
        return new Part(kind, number, png);
    }

    /**
     * Takes a picture out of the collection. Its files are not destroyed: they move
     * to the folder {@code deleted}.
     */
    public synchronized void remove(Part part) throws IOException {
        Path trash = directory.resolve("deleted");
        Files.createDirectories(trash);
        String stamp = String.valueOf(System.currentTimeMillis());
        for (String extension : new String[] {".png", ".svg", ".json"}) {
            Path file = sibling(part, extension);
            if (Files.isRegularFile(file)) {
                Files.move(file, trash.resolve(stamp + "-" + file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ------------------------------------------------------------------ limits

    private Path limitsFile() {
        return directory.resolve("limits.properties");
    }

    /** What {@code limits.properties} holds; empty if there is no such file or it cannot be read. */
    private Properties settings() {
        Properties settings = new Properties();
        Path file = limitsFile();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings.load(in);
            } catch (IOException | IllegalArgumentException e) {
                settings.clear();
            }
        }
        return settings;
    }

    private void set(String name, String value) throws IOException {
        Properties settings = settings();
        settings.setProperty(name, value);
        Files.createDirectories(directory);
        try (Writer out = Files.newBufferedWriter(limitsFile(), StandardCharsets.UTF_8)) {
            settings.store(out, "How many pictures of each kind this district collects, and which one it uses");
        }
    }

    /** How many pictures of this kind the district collects before nothing more is drawn. */
    public int target(Kind kind) {
        try {
            return Math.max(0, Math.min(MAX_TARGET, Integer.parseInt(
                    settings().getProperty(kind.fileName(), String.valueOf(kind.defaultTarget())).trim())));
        } catch (NumberFormatException e) {
            return kind.defaultTarget();
        }
    }

    /** Whether a limit for this kind was set here, as opposed to the default applying. */
    public boolean hasOwnTarget(Kind kind) {
        return settings().getProperty(kind.fileName()) != null;
    }

    public synchronized void setTarget(Kind kind, int target) throws IOException {
        set(kind.fileName(), String.valueOf(Math.max(0, Math.min(MAX_TARGET, target))));
    }

    /**
     * The one picture of a {@linkplain Kind#uniform() uniform} kind the district
     * uses: the one set with {@link #setChosen}, or the oldest as long as none is
     * set or the set one is gone.
     *
     * @return the picture, or {@code null} if the collection has none of this kind
     */
    public Part chosen(Kind kind) {
        List<Part> candidates = parts(kind);
        if (candidates.isEmpty()) {
            return null;
        }
        String number = settings().getProperty(kind.fileName() + ".chosen", "").trim();
        for (Part candidate : candidates) {
            if (String.valueOf(candidate.number()).equals(number)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    /** Makes this picture the one its district uses for its kind. */
    public synchronized void setChosen(Part part) throws IOException {
        set(part.kind().fileName() + ".chosen", String.valueOf(part.number()));
    }

    // --------------------------------------------------- artwork of earlier versions

    /**
     * Takes single pictures from the asset folder into the collection: what earlier
     * versions of the game stored there as {@code blossom-tree.svg}, {@code lamp.png}
     * and so on, including their {@code history}, and what the command
     * {@code generate} or a hand puts there. The files then move to the folder
     * {@code imported}, so that each is taken once.
     *
     * @return how many pictures were taken
     */
    public int importSingleAssets(AssetStore store) {
        int taken = 0;
        for (AssetSpec spec : AssetCatalog.ALL) {
            Kind kind = switch (spec.id()) {
                case "blossom-tree" -> Kind.BLOSSOM_TREE;
                case "green-tree" -> Kind.GREEN_TREE;
                case "lamp" -> Kind.LAMP;
                case "peony-bush" -> Kind.BUSH;
                case "lawn-tile" -> Kind.LAWN;
                case "street-tile" -> Kind.STREET;
                default -> spec.id().startsWith("visitor") ? Kind.VISITOR : null;
            };
            if (kind == null) {
                continue;
            }
            // Oldest first: the history, then the current picture.
            List<Path> bases = new ArrayList<>();
            Path history = store.historyDirectory(spec);
            if (Files.isDirectory(history)) {
                try (Stream<Path> files = Files.list(history)) {
                    files.map(file -> file.getFileName().toString())
                            .filter(name -> name.endsWith(".png") || name.endsWith(".svg"))
                            .map(name -> name.substring(0, name.length() - 4)).distinct().sorted()
                            .forEach(name -> bases.add(history.resolve(name)));
                } catch (IOException e) {
                    // the history is skipped
                }
            }
            if (store.has(spec)) {
                store.load(spec); // makes the PNG from an SVG put there by hand
                bases.add(store.directory().resolve(spec.id()));
            }
            for (Path base : bases) {
                try {
                    if (take(kind, base, store.directory().resolve("imported"), spec)) {
                        taken++;
                    }
                } catch (IOException | RuntimeException e) {
                    System.out.println("Library: " + base + " could not be taken over (" + e.getMessage() + ")");
                }
            }
        }
        return taken;
    }

    private boolean take(Kind kind, Path base, Path importedDirectory, AssetSpec spec) throws IOException {
        Path png = Path.of(base + ".png");
        Path svg = Path.of(base + ".svg");
        Path json = Path.of(base + ".json");
        boolean added = false;
        if (Files.isRegularFile(svg) && !Files.isRegularFile(png)) {
            render(svg, png, kind);
        }
        if (Files.isRegularFile(png)) {
            ImageIO.setUseCache(false);
            BufferedImage image = ImageIO.read(png.toFile());
            if (image != null) {
                JsonObject details = new JsonObject();
                if (Files.isRegularFile(json)) {
                    try {
                        details = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
                    } catch (RuntimeException e) {
                        details = new JsonObject();
                    }
                }
                details.remove("id");
                details.addProperty("importedFrom", spec.id());
                add(kind, Files.isRegularFile(svg) ? Files.readString(svg, StandardCharsets.UTF_8) : null, image, details);
                added = true;
            }
        }
        Files.createDirectories(importedDirectory);
        String prefix = base.getParent().getFileName().toString().equals(spec.id()) ? spec.id() + "-" : "";
        for (Path file : new Path[] {png, svg, json}) {
            if (Files.isRegularFile(file)) {
                Files.move(file, importedDirectory.resolve(prefix + file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return added;
    }
}
