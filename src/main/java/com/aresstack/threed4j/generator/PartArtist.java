package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.SvgRasterizer;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Lets the model draw one building part (window, door, shop front) for a district
 * and adds it to the district's {@link PartLibrary}.
 *
 * <p>Two things go into every request besides the rules of the format: the style
 * of the district, so that all parts of a district belong together, and one
 * variation from a fixed list, so that the parts of a kind differ from each
 * other.</p>
 */
final class PartArtist {

    private static final int ATTEMPTS = 3;

    private static final String[] WINDOW_VARIATIONS = {
            "a tall window with two casements and a cross bar",
            "a window with a round arch on top",
            "a window with open wooden shutters on both sides",
            "a window with a flower box full of blossoms below it",
            "a small round window with a thick frame",
            "a wide window with three casements",
            "a window with a little striped awning above it",
            "a window with a balcony railing in front of its lower half"};
    private static final String[] DOOR_VARIATIONS = {
            "a wooden door with a round arch and a small window in it",
            "a double door with two panels and brass handles",
            "a door with a glass pane and a small canopy above it",
            "a door with a fanlight above it and a step below"};
    private static final String[] SHOP_VARIATIONS = {
            "a wide shop window with a striped awning above it",
            "a shop window divided into small panes, with a sign board above it",
            "a cafe window with a counter sill and a scalloped awning",
            "a shop window with an arched top and flower pots below"};

    private PartArtist() {
    }

    static String rules(PartLibrary.Kind kind) {
        String placement = switch (kind) {
            case WINDOW -> "The window is centered and about 80 to 96 units wide and high. Leave at least 12 units "
                    + "of empty, transparent space on all four sides: the wall of the house shows there.";
            case DOOR -> "The door stands on the bottom edge (y = 128), is centered, 56 to 72 units wide and 104 to "
                    + "120 units high. Leave at least 12 units of empty, transparent space on the left, on the right "
                    + "and at the top: the wall of the house shows there.";
            case SHOP -> "The shop front is centered, 96 to 108 units wide, and reaches from about y = 18 down to "
                    + "about y = 112. Leave at least 8 units of empty, transparent space on all four sides: the wall "
                    + "of the house shows there.";
        };
        return "You draw parts of house fronts for a video game as SVG. The game puts your drawing on the wall of a "
                + "house in a pastel-coloured cartoon town; the same drawing is used many times.\n\n"
                + "Rules:\n"
                + "- Answer with one complete SVG document and nothing else.\n"
                + "- Root element: <svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 128 128\">.\n"
                + "- Seen straight from the front, no perspective. " + placement + "\n"
                + "- The background must stay transparent: no rectangle behind the part, no wall, no ground.\n"
                + "- Cartoon style: flat, friendly colours, dark outlines (stroke=\"#3B2A4A\", stroke-width 4 to 5, "
                + "round joins), glass in a light blue, a few lighter highlights.\n"
                + "- Use only basic shapes and paths with solid fills. No text, no images, no filters, no gradients, "
                + "no scripts, no external references.";
    }

    /** What to draw: the variation with this number, in the style of the district. */
    static String task(PartLibrary.Kind kind, int number, String districtStyle) {
        String[] variations = switch (kind) {
            case WINDOW -> WINDOW_VARIATIONS;
            case DOOR -> DOOR_VARIATIONS;
            case SHOP -> SHOP_VARIATIONS;
        };
        return "Draw: " + variations[Math.floorMod(number - 1, variations.length)] + ".\n"
                + "It belongs to a district of the town with this look, which every part of the district shares: "
                + districtStyle + ".";
    }

    /**
     * A part is there, leaves the wall visible around it and does not run into the
     * edges of its square (a door and a shop front may stand on the bottom edge).
     *
     * @return {@code null} if the picture is usable, otherwise what is wrong with it
     */
    static String problem(PartLibrary.Kind kind, BufferedImage image) {
        double coverage = SvgRasterizer.coverage(image);
        if (coverage < 0.10) {
            return "the drawing is empty or tiny";
        }
        if (coverage > 0.86) {
            return "the background is not transparent; draw only the part itself, with empty space around it";
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int ring = Math.max(1, width / 43);
        boolean standsOnGround = kind != PartLibrary.Kind.WINDOW;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x < ring || x >= width - ring || y < ring || !standsOnGround && y >= height - ring;
                if (edge && (image.getRGB(x, y) >>> 24) > 24) {
                    return "the drawing touches the edge of the square; keep the required empty space around it";
                }
            }
        }
        return null;
    }

    /**
     * Asks for one part and adds it to the library; asks again with the reason while the answer is unusable.
     *
     * @param number which variation to ask for, usually the number the part will get
     * @return {@code null} on success, otherwise why it failed
     */
    static String generate(FlowerComposer.Model model, PartLibrary library, PartLibrary.Kind kind, int number,
                           String districtStyle, String modelName) {
        String task = task(kind, number, districtStyle);
        String problem = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String answer;
            try {
                answer = model.ask(rules(kind), task + (problem == null ? "" : "\n\nYour previous answer was "
                        + "unusable: " + problem + ". Follow the rules exactly and answer with the corrected SVG only."));
            } catch (IOException e) {
                return e.getMessage(); // a network or service problem does not get better by asking again
            }
            String svg = SvgRasterizer.extract(answer);
            if (svg.isEmpty()) {
                problem = "it contained no <svg> element";
                continue;
            }
            try {
                BufferedImage image = SvgRasterizer.render(svg, PartLibrary.SIZE, PartLibrary.SIZE);
                problem = problem(kind, image);
                if (problem == null) {
                    JsonObject details = new JsonObject();
                    details.addProperty("model", modelName == null ? "" : modelName);
                    details.addProperty("task", task);
                    library.add(kind, svg, image, details);
                    return null;
                }
            } catch (SvgRasterizer.UnreadableSvgException e) {
                problem = e.getMessage();
            } catch (IOException e) {
                return "cannot write to " + library.directory() + ": " + e.getMessage();
            }
        }
        return problem + " (after " + ATTEMPTS + " attempts)";
    }
}
