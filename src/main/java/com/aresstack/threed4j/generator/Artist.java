package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws one picture of any kind for a district's collection. Every kind has its
 * way of coming about (a figure in one piece, a ground tile, a bush from plan and
 * parts, a house part) and a list of variations; the number of the picture picks
 * the variation, and the style of the district goes into every request.
 */
final class Artist {

    private Artist() {
    }

    /** As {@link #description(PartLibrary.Kind, int, District)}, for the motifs of the Blütenviertel in this style. */
    static String description(PartLibrary.Kind kind, int number, String districtStyle) {
        return description(kind, number, new District("district", "district", districtStyle));
    }

    /** What to draw for the picture with this number: the district's variation, and the look of the district. */
    static String description(PartLibrary.Kind kind, int number, District district) {
        if (kind == PartLibrary.Kind.WINDOW || kind == PartLibrary.Kind.DOOR || kind == PartLibrary.Kind.SHOP) {
            throw new IllegalArgumentException(kind + " is a house part");
        }
        List<String> variations = district.motif(kind).variations();
        String subject = variations.get(Math.floorMod(number - 1, variations.size()));
        if (kind == PartLibrary.Kind.SEA_ANIMAL) {
            return subject + ", seen from the side, friendly. The game shows it in the sea and hides the lowest "
                    + "quarter of the picture under water, so draw no water and no waves yourself";
        }
        if (kind == PartLibrary.Kind.ANIMAL) {
            // An animal is an animal; only where it lives differs from district to district.
            return subject + ". A friendly cartoon animal; it fills the tall picture, so draw it upright and large";
        }
        if (kind.shared()) {
            // People are at home in the whole town; no district lends them its look.
            return "a friendly person standing upright, seen from the front: " + subject;
        }
        return subject + ". It belongs to a district of the town with this look, which everything in the district "
                + "shares: " + district.style();
    }

    /**
     * Draws one picture and adds it to the collection.
     *
     * @param number which variation to ask for, usually the number the picture will get
     * @return {@code null} on success, otherwise why it failed
     */
    static String draw(ChatClient chat, PartLibrary library, PartLibrary.Kind kind, int number, String districtStyle) {
        return draw(chat, library, new District("district", "district", districtStyle), kind, number);
    }

    /**
     * Draws one picture of what this kind is in the district and adds it to the collection.
     *
     * @param library the collection the picture goes to: the district's, or the town's for a shared kind
     * @return {@code null} on success, otherwise why it failed
     */
    static String draw(ChatClient chat, PartLibrary library, District district, PartLibrary.Kind kind, int number) {
        if (kind == PartLibrary.Kind.WINDOW || kind == PartLibrary.Kind.DOOR || kind == PartLibrary.Kind.SHOP) {
            return PartArtist.generate(chat::ask, library, kind, number, district, chat.model());
        }
        AssetSpec.Recipe recipe = switch (kind) {
            case BUSH -> district.flowerBushes() ? AssetSpec.Recipe.FLOWER_BUSH : AssetSpec.Recipe.DRAWING;
            case LAWN, STREET -> AssetSpec.Recipe.TILE;
            default -> AssetSpec.Recipe.DRAWING;
        };
        // One drawing is a figure of 256 x 512, also where the kind's own picture is square (a bush from parts).
        int height = recipe == AssetSpec.Recipe.DRAWING ? 512 : kind.height();
        // The generators for single pictures write into a folder; they get one of their own for this
        // drawing, and what they made moves into the collection.
        Path work = library.directory().resolve("work").resolve(kind.fileName() + "-" + Thread.currentThread().getId()
                + "-" + System.nanoTime());
        AssetStore scratch = new AssetStore(work);
        String description = description(kind, number, district);
        AssetSpec spec = new AssetSpec("drawing", kind.width(), height, description, recipe);
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
