package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Draws one picture of any kind for a district's collection. Every kind has its
 * way of coming about (a figure in one piece, a ground tile, a bush from plan and
 * parts, a house part) and a list of variations; the number of the picture picks
 * the variation, and the style of the district goes into every request.
 */
final class Artist {

    private static final String[] BLOSSOM_TREES = {
            "a cherry tree in full pink blossom: brown trunk, big round fluffy pink crown",
            "a tall slender cherry tree in pink blossom: long trunk, crown of three fluffy pink clouds",
            "an old cherry tree in blossom: thick bent trunk, wide flat pink crown",
            "a young cherry tree in blossom: thin trunk, small light pink crown with darker pink dots",
            "a cherry tree in white and pale pink blossom: forked trunk, two round crowns side by side",
            "a weeping cherry tree: brown trunk, long hanging branches full of pink blossom"};
    private static final String[] GREEN_TREES = {
            "a leafy green park tree: brown trunk, big round green crown",
            "a tall poplar: straight trunk, narrow pointed green crown",
            "a broad old oak: thick trunk, wide crown of several green clouds",
            "a small round ball-shaped tree, trimmed like in a formal garden",
            "a birch: thin white trunk with dark marks, light green airy crown"};
    private static final String[] LAMPS = {
            "an old-fashioned street lamp: slim dark iron post, warm glowing yellow lantern on top",
            "a street lamp with two lanterns on curved arms, dark green post",
            "a street lamp with a round white glass globe on a slim post with a decorated foot",
            "a street lamp with a hanging lantern on a curved swan-neck post"};
    private static final String[] VISITORS = {
            "purple hoodie, red-brown hair", "blue jacket, dark hair", "yellow raincoat, blond hair",
            "green dress, black hair in a bun", "red cardigan, grey hair and glasses", "orange t-shirt, curly brown hair",
            "white shirt and dark tie, short black hair", "pink coat, long light brown hair",
            "striped sweater, a cap on the head", "light blue overalls, a ponytail",
            "brown coat and a scarf, a hat", "turquoise jacket, a backpack, short red hair"};
    private static final String[] LAWNS = {
            "a mown lawn: base colour #A2D08C, small tufts of grass in slightly darker and lighter greens, a few "
                    + "tiny white and pink flowers",
            "a meadow: base colour #A2D08C, short blades of grass in slightly different greens, some clover leaves",
            "a lawn with fallen cherry blossom: base colour #A2D08C, small tufts of grass, scattered pale pink petals"};
    private static final String[] STREETS = {
            "the paving of a town street: base colour #D2CEDB, flat paving stones of different sizes in slightly "
                    + "lighter and darker shades of the base colour, a few short cracks",
            "cobblestones: base colour #D2CEDB, small rounded stones in slightly lighter and darker shades of the "
                    + "base colour",
            "large stone slabs: base colour #D2CEDB, a few big rectangular slabs in slightly different shades of "
                    + "the base colour, thin gaps between them"};

    private Artist() {
    }

    /** What to draw for the picture with this number: the variation, and the look of the district. */
    static String description(PartLibrary.Kind kind, int number, String districtStyle) {
        String subject = switch (kind) {
            case BLOSSOM_TREE -> pick(BLOSSOM_TREES, number);
            case GREEN_TREE -> pick(GREEN_TREES, number);
            case LAMP -> pick(LAMPS, number);
            case VISITOR -> "a friendly person standing upright, seen from the front: " + pick(VISITORS, number);
            case LAWN -> pick(LAWNS, number);
            case STREET -> pick(STREETS, number);
            case BUSH -> "a bush of pink peonies";
            default -> throw new IllegalArgumentException(kind + " is a house part");
        };
        return subject + ". It belongs to a district of the town with this look, which everything in the district "
                + "shares: " + districtStyle;
    }

    private static String pick(String[] variations, int number) {
        return variations[Math.floorMod(number - 1, variations.length)];
    }

    /**
     * Draws one picture and adds it to the collection.
     *
     * @param number which variation to ask for, usually the number the picture will get
     * @return {@code null} on success, otherwise why it failed
     */
    static String draw(ChatClient chat, PartLibrary library, PartLibrary.Kind kind, int number, String districtStyle) {
        if (kind == PartLibrary.Kind.WINDOW || kind == PartLibrary.Kind.DOOR || kind == PartLibrary.Kind.SHOP) {
            return PartArtist.generate(chat::ask, library, kind, number, districtStyle, chat.model());
        }
        AssetSpec.Recipe recipe = switch (kind) {
            case BUSH -> AssetSpec.Recipe.FLOWER_BUSH;
            case LAWN, STREET -> AssetSpec.Recipe.TILE;
            default -> AssetSpec.Recipe.DRAWING;
        };
        // The generators for single pictures write into a folder; they get one of their own for this
        // drawing, and what they made moves into the collection.
        Path work = library.directory().resolve("work").resolve(kind.fileName() + "-" + Thread.currentThread().getId()
                + "-" + System.nanoTime());
        AssetStore scratch = new AssetStore(work);
        String description = description(kind, number, districtStyle);
        AssetSpec spec = new AssetSpec("drawing", kind.width(), kind.height(), description, recipe);
        try {
            String problem = AssetGenerator.generate(chat, scratch, spec);
            if (problem != null) {
                return problem;
            }
            BufferedImage image = scratch.load(spec);
            if (image == null) {
                return "the finished picture cannot be read";
            }
            JsonObject details = JsonParser.parseString(
                    Files.readString(scratch.infoFile(spec), StandardCharsets.UTF_8)).getAsJsonObject();
            details.remove("id");
            library.add(kind, Files.readString(scratch.svgFile(spec), StandardCharsets.UTF_8), image, details);
            return null;
        } catch (IOException | RuntimeException e) {
            return "cannot store the picture in " + library.directory() + ": " + e.getMessage();
        } finally {
            discard(work);
        }
    }

    /** Removes the working folder of a finished drawing; a rejected answer kept for inspection stays. */
    private static void discard(Path work) {
        try (Stream<Path> files = Files.isDirectory(work) ? Files.list(work) : Stream.empty()) {
            boolean keep = false;
            for (Path file : (Iterable<Path>) files::iterator) {
                if (file.getFileName().toString().endsWith(".rejected.txt")) {
                    keep = true;
                } else {
                    Files.deleteIfExists(file);
                }
            }
            if (!keep) {
                Files.deleteIfExists(work);
            }
        } catch (IOException e) {
            // a left-over working folder does no harm
        }
    }
}
