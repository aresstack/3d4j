package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.SvgRasterizer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * Builds a peony bush from parts instead of asking the model for one big drawing.
 *
 * <p>The division of labour follows what the model is good and bad at: it is asked
 * for small things with a clear shape (one petal, one leaf, the centre of the
 * blossom) and for a plan in JSON (how many petals per ring, how large, which
 * colours). Everything spatial is done here: the program checks and cleans every
 * part, adds the outlines of the game's cartoon style, arranges the petals into a
 * blossom by the plan and the blossoms and leaves into a bush.</p>
 *
 * <ol>
 *   <li>One request for the plan; numbers outside their range are pulled into it.</li>
 *   <li>One request per part, all at the same time. A part that is not exactly one
 *       flat group of paths, or does not stay in its box, is asked for again with
 *       the reason.</li>
 *   <li>Assembly into one SVG: each part once under {@code <defs>}, placed with
 *       {@code <use>}.</li>
 * </ol>
 */
final class FlowerComposer {

    /** The model, reduced to what the composer needs; a lambda in tests. */
    interface Model {
        String ask(String systemPrompt, String userPrompt) throws IOException;
    }

    /** An answer of the model that cannot be used; the message is handed back to the model. */
    static final class Unusable extends Exception {
        private static final long serialVersionUID = 1L;

        Unusable(String message) {
            super(message);
        }
    }

    /**
     * One part the model draws.
     *
     * @param halfWidth   the part stays within x = -halfWidth..halfWidth
     * @param top         it reaches up to y = top (negative)
     * @param bottom      and down to y = bottom (0 for petals, which are attached at the origin)
     * @param circles     whether circles are allowed besides paths
     * @param outline     width of the outline the program adds, in the part's own units
     * @param outlineAll  outline every shape (centre) or only the silhouette (petals, leaf)
     */
    record Part(String id, int halfWidth, int top, int bottom, boolean circles, double outline, boolean outlineAll,
                String task, String defaultColor) {
    }

    static final Part OUTER_A = new Part("outerA", 85, -190, 0, false, 12, false,
            "one broad outer petal of a peony, attached at the bottom, with an uneven wavy upper rim", "#F7A8CF");
    static final Part OUTER_B = new Part("outerB", 80, -155, 0, false, 12, false,
            "one wide petal of the middle ring, attached at the bottom, with an irregular curled rim", "#F48FBF");
    static final Part INNER = new Part("inner", 55, -110, 0, false, 12, false,
            "one compact inner petal, attached at the bottom, slightly cupped", "#FFC2DD");
    static final Part CENTER = new Part("center", 30, -30, 30, true, 3, true,
            "the round centre of the blossom around (0,0): a small disc with a few stamens", "#FFE08A");
    static final Part LEAF = new Part("leaf", 45, -170, 0, false, 5, false,
            "one pointed leaf, attached at the bottom, with a centre vein", "#74C365");

    /** The petal rings from back to front, then the centre: the order of the plan and of drawing. */
    static final List<Part> RINGS = List.of(OUTER_A, OUTER_B, INNER, CENTER);
    static final List<Part> PARTS = List.of(OUTER_A, OUTER_B, INNER, CENTER, LEAF);

    /**
     * One ring of the plan.
     *
     * @param count       how many copies of the part stand in a circle
     * @param angleOffset rotation of the whole ring in degrees
     * @param sizePercent size of the copies
     * @param shiftY      how far the copies are moved away from the middle (negative is outward)
     * @param shape       what the part should look like, in the model's words
     */
    record Ring(String id, int count, int angleOffset, int sizePercent, int shiftY, String shape) {
    }

    /** @param colors main colour per part id */
    record Plan(List<Ring> rings, Map<String, String> colors) {

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            JsonArray layers = new JsonArray();
            for (Ring ring : rings) {
                JsonObject layer = new JsonObject();
                layer.addProperty("id", ring.id());
                layer.addProperty("count", ring.count());
                layer.addProperty("angleOffset", ring.angleOffset());
                layer.addProperty("sizePercent", ring.sizePercent());
                layer.addProperty("shiftY", ring.shiftY());
                layer.addProperty("shapeDescription", ring.shape());
                layers.add(layer);
            }
            json.add("layers", layers);
            JsonObject palette = new JsonObject();
            colors.forEach(palette::addProperty);
            json.add("colors", palette);
            return json;
        }
    }

    static final String OUTLINE_COLOR = "#3B2A4A";
    private static final int ATTEMPTS = 3;
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}");
    private static final Pattern PATH_DATA = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz0-9eE,.\\s+-]+");

    private static final String SYSTEM = "You design artwork for a cartoon video game together with a program. "
            + "The program assembles what you return, so follow the requested format exactly and return nothing else.";

    static final String PLAN_PROMPT = """
            Design a pink peony blossom, seen from the front, that a program assembles from reusable parts.
            Return one compact JSON object and nothing else, of this form:
            {"layers":[{"id":"outerA","count":8,"angleOffset":10,"sizePercent":100,"shiftY":-5,"shapeDescription":"..."}, ...],
             "colors":{"outerA":"#RRGGBB","outerB":"#RRGGBB","inner":"#RRGGBB","center":"#RRGGBB","leaf":"#RRGGBB"}}
            Rules:
            - "layers" has exactly four objects in this order of ids: outerA, outerB, inner, center.
            - count: outerA 6 to 9, outerB 7 to 11, inner 8 to 14, center exactly 1.
            - angleOffset 0 to 25, sizePercent 60 to 100 (outerA and center: 100), shiftY -20 to 0 (center: 0).
            - shapeDescription: one short sentence per layer describing a DIFFERENT, asymmetrical petal outline \
            (for center: the middle of the blossom). No quotes inside the sentence.
            - colors: friendly flat cartoon colours. Petals in pinks that get lighter towards the middle, \
            a warm yellow or cream centre, a fresh green leaf.
            - No comments, no Markdown, no extra fields.""";

    private FlowerComposer() {
    }

    // -------------------------------------------------------------------- plan

    /**
     * Reads the plan from the model's answer. Numbers outside their range are pulled
     * into it and unusable colours replaced, because that is cheaper than asking again.
     *
     * @throws Unusable if there is no JSON or the four layers are not there in order
     */
    static Plan parsePlan(String answer) throws Unusable {
        int start = answer == null ? -1 : answer.indexOf('{');
        int end = answer == null ? -1 : answer.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new Unusable("it contained no JSON object");
        }
        JsonObject json;
        try {
            json = JsonParser.parseString(answer.substring(start, end + 1)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new Unusable("it was not valid JSON");
        }
        JsonElement layers = json.get("layers");
        if (layers == null || !layers.isJsonArray() || layers.getAsJsonArray().size() != RINGS.size()) {
            throw new Unusable("\"layers\" must be an array of exactly four objects");
        }
        List<Ring> rings = new ArrayList<>();
        for (int i = 0; i < RINGS.size(); i++) {
            Part part = RINGS.get(i);
            JsonElement element = layers.getAsJsonArray().get(i);
            if (!element.isJsonObject() || !part.id().equals(text(element.getAsJsonObject(), "id", ""))) {
                throw new Unusable("layer " + (i + 1) + " must have the id " + part.id());
            }
            JsonObject layer = element.getAsJsonObject();
            boolean center = part == CENTER;
            int count = switch (part.id()) {
                case "outerA" -> clamp(number(layer, "count", 8), 6, 9);
                case "outerB" -> clamp(number(layer, "count", 9), 7, 11);
                case "inner" -> clamp(number(layer, "count", 11), 8, 14);
                default -> 1;
            };
            int size = part == OUTER_A || center ? 100 : clamp(number(layer, "sizePercent", 80), 55, 100);
            int shift = center ? 0 : clamp(number(layer, "shiftY", -8), -20, 0);
            String shape = text(layer, "shapeDescription", "").replaceAll("[\\s\"<>]+", " ").trim();
            if (shape.length() > 200) {
                shape = shape.substring(0, 200);
            }
            rings.add(new Ring(part.id(), count, clamp(number(layer, "angleOffset", 0), 0, 25), size, shift, shape));
        }
        Map<String, String> colors = new LinkedHashMap<>();
        JsonElement palette = json.get("colors");
        for (Part part : PARTS) {
            String color = palette != null && palette.isJsonObject() ? text(palette.getAsJsonObject(), part.id(), "") : "";
            colors.put(part.id(), HEX_COLOR.matcher(color).matches() ? color : part.defaultColor());
        }
        return new Plan(rings, colors);
    }

    private static String text(JsonObject object, String name, String fallback) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int number(JsonObject object, String name, int fallback) {
        try {
            JsonElement value = object.get(name);
            return value != null && value.isJsonPrimitive() ? (int) Math.round(value.getAsDouble()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    // ------------------------------------------------------------------- parts

    static String partPrompt(Part part, String color, String shape) {
        String shapes = part.circles() ? "3 to 8 <path> or <circle> elements" : "2 to 4 <path> elements";
        return "Return exactly one SVG fragment: a single <g> element that contains " + shapes + " and nothing else.\n"
                + "- The FIRST element is the complete silhouette of the part as one closed shape. The following "
                + "elements are smaller shading or highlight shapes that lie inside the silhouette.\n"
                + "- Coordinates: stay within x=" + -part.halfWidth() + ".." + part.halfWidth() + " and y=" + part.top()
                + ".." + part.bottom() + " and use that area well. Negative y is up. Use absolute path commands.\n"
                + "- Allowed attributes: d and fill" + (part.circles() ? " (cx, cy, r on circles)" : "")
                + ". fill is a literal colour like #F7A8CF.\n"
                + "- No stroke (the program adds the outline), no opacity, no transform, no id, no style, no gradients, "
                + "no <defs>, no nested <g>, no <svg> root, no comments, no Markdown.\n"
                + "- Colours: " + color + " for the silhouette, lighter or darker shades of it for the inner shapes.\n"
                + "Part to draw: " + part.task() + "."
                + (shape == null || shape.isBlank() ? "" : "\nShape: " + shape + ".");
    }

    /**
     * Checks one part and returns it rebuilt: only the geometry and the fill colours
     * are taken from the answer (exactly one flat group of paths, circles where
     * allowed); outline, clipping of the shading to the silhouette and the order of
     * drawing are set here.
     *
     * @return the rebuilt {@code <g>...</g>}, without attributes on the group
     * @throws Unusable if the answer is not such a group or does not stay in its area
     */
    static String cleanPart(Part part, String color, String answer) throws Unusable {
        int start = answer == null ? -1 : answer.indexOf("<g");
        int end = answer == null ? -1 : answer.lastIndexOf("</g>");
        if (start < 0 || end < start) {
            throw new Unusable("it contained no complete <g> element");
        }
        Element group;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(answer.substring(start, end + "</g>".length()))));
            group = document.getDocumentElement();
        } catch (Exception e) {
            throw new Unusable("the <g> element is not well-formed XML");
        }

        // Shape by shape: the geometry as given, the paint decided here.
        List<String> geometry = new ArrayList<>();
        List<String> fills = new ArrayList<>();
        int shapes = 0;
        for (Node node = group.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element shape)) {
                continue; // text between the shapes and comments are dropped
            }
            String name = shape.getTagName();
            String fill = shape.getAttribute("fill").trim();
            fills.add(HEX_COLOR.matcher(fill).matches() ? fill : color);
            if (name.equals("path")) {
                String data = shape.getAttribute("d").trim();
                if (data.isEmpty() || !PATH_DATA.matcher(data).matches()) {
                    throw new Unusable("a <path> has no usable d attribute");
                }
                geometry.add("<path d=\"" + data + "\"");
            } else if (name.equals("circle") && part.circles()) {
                try {
                    double cx = shape.getAttribute("cx").isBlank() ? 0 : Double.parseDouble(shape.getAttribute("cx").trim());
                    double cy = shape.getAttribute("cy").isBlank() ? 0 : Double.parseDouble(shape.getAttribute("cy").trim());
                    double radius = Double.parseDouble(shape.getAttribute("r").trim());
                    geometry.add("<circle cx=\"" + number(cx) + "\" cy=\"" + number(cy) + "\" r=\"" + number(radius) + "\"");
                } catch (NumberFormatException e) {
                    throw new Unusable("a <circle> has no usable cx, cy and r");
                }
            } else {
                throw new Unusable("it contains <" + name + ">; only <path>"
                        + (part.circles() ? " and <circle>" : "") + " directly inside the <g> are allowed");
            }
            shapes++;
        }
        if (shapes == 0) {
            throw new Unusable("the <g> element contains no shapes");
        }
        if (shapes > 12) {
            throw new Unusable("the <g> element contains " + shapes + " shapes; use fewer");
        }
        String outline = " fill=\"none\" stroke=\"" + OUTLINE_COLOR + "\" stroke-width=\"" + number(part.outline())
                + "\" stroke-linejoin=\"round\"/>";
        StringBuilder clean = new StringBuilder("<g>");
        if (part.outlineAll()) {
            // Small separate shapes (stamens): each one filled and outlined.
            for (int i = 0; i < shapes; i++) {
                clean.append(geometry.get(i)).append(" fill=\"").append(fills.get(i)).append("\" stroke=\"")
                        .append(OUTLINE_COLOR).append("\" stroke-width=\"").append(number(part.outline()))
                        .append("\" stroke-linejoin=\"round\"/>");
            }
        } else {
            // The first shape is the silhouette. The others are shading: they are cut off at the
            // silhouette, and the outline is drawn last, so that nothing can cover or leave it.
            String clip = "clip-" + part.id();
            clean.append("<clipPath id=\"").append(clip).append("\">").append(geometry.get(0)).append("/></clipPath>");
            // The silhouette has the colour of the plan, whatever the part says: the rings must go together.
            clean.append(geometry.get(0)).append(" fill=\"").append(color).append("\"/>");
            if (shapes > 1) {
                // Half transparent: shading that is drawn too large tints the part instead of repainting it.
                clean.append("<g clip-path=\"url(#").append(clip).append(")\" fill-opacity=\"0.55\">");
                for (int i = 1; i < shapes; i++) {
                    clean.append(geometry.get(i)).append(" fill=\"").append(fills.get(i)).append("\"/>");
                }
                clean.append("</g>");
            }
            clean.append(geometry.get(0)).append(outline);
        }
        clean.append("</g>");
        checkArea(part, clean.toString());
        return clean.toString();
    }

    /**
     * Draws the part alone and looks where it is: it has to use its area and must not
     * reach far out of it, otherwise the assembled blossom falls apart.
     */
    private static void checkArea(Part part, String fragment) throws Unusable {
        int width = 2 * part.halfWidth();
        int height = part.bottom() - part.top();
        // The picture shows the allowed area plus half of its size on every side, at 2 pixels per 3 units.
        int marginX = width / 2;
        int marginY = height / 2;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"" + (-part.halfWidth() - marginX) + " "
                + (part.top() - marginY) + " " + (width + 2 * marginX) + " " + (height + 2 * marginY) + "\">"
                + fragment + "</svg>";
        int pixelsWide = Math.max(16, (width + 2 * marginX) * 2 / 3);
        int pixelsHigh = Math.max(16, (height + 2 * marginY) * 2 / 3);
        BufferedImage image;
        try {
            image = SvgRasterizer.render(svg, pixelsWide, pixelsHigh);
        } catch (SvgRasterizer.UnreadableSvgException e) {
            throw new Unusable("it cannot be drawn (" + e.getMessage() + ")");
        }
        int left = Integer.MAX_VALUE;
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int y = 0; y < pixelsHigh; y++) {
            for (int x = 0; x < pixelsWide; x++) {
                if ((image.getRGB(x, y) >>> 24) > 40) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            throw new Unusable("nothing is visible");
        }
        double usedWidth = (right - left + 1) / (double) pixelsWide * (width + 2 * marginX);
        double usedHeight = (bottom - top + 1) / (double) pixelsHigh * (height + 2 * marginY);
        // Allowed: the area plus a fifth (a wavy rim and the outline may stick out a little).
        if (usedWidth > width * 1.4 || usedHeight > height * 1.4
                || left < pixelsWide * 0.12 || right > pixelsWide * 0.88
                || top < pixelsHigh * 0.12 || bottom > pixelsHigh * 0.88) {
            throw new Unusable("it reaches out of the allowed area x=" + -part.halfWidth() + ".." + part.halfWidth()
                    + ", y=" + part.top() + ".." + part.bottom());
        }
        if (usedHeight < height * 0.45 || usedWidth < width * 0.3) {
            throw new Unusable("it is too small: it should fill most of x=" + -part.halfWidth() + ".."
                    + part.halfWidth() + ", y=" + part.top() + ".." + part.bottom());
        }
    }

    // ---------------------------------------------------------------- assembly

    /** Where the blossoms of the bush sit in the 512 x 512 picture: x, y, size, rotation; back to front. */
    private static final double[][] BLOSSOMS = {
            {256, 176, 0.64, 0}, {124, 278, 0.50, -24}, {392, 270, 0.50, 31}, {196, 392, 0.37, 12}, {326, 398, 0.39, -17}};
    /** The leaves fan out from the foot of the bush: angle in degrees, size; back to front. */
    private static final double[][] LEAVES = {
            {-78, 1.20}, {78, 1.20}, {-58, 1.45}, {58, 1.45}, {-38, 1.60}, {38, 1.60}, {-18, 1.70}, {18, 1.70}, {0, 1.75},
            {-66, 0.95}, {66, 0.95}, {-30, 1.05}, {30, 1.05}};
    /** Small irregularities, so that a ring does not look stamped: degrees and units, repeated along the ring. */
    private static final int[] WOBBLE = {-3, 5, -6, 2, 0};
    private static final int[] SIDESTEP = {-3, 2, 0, 4, -2};

    /**
     * Puts the parts together: the petals into a blossom by the plan, blossoms and
     * leaves into a bush that stands on the bottom edge of a 512 x 512 picture.
     *
     * @param parts cleaned parts by id, as {@link #cleanPart} returns them
     */
    static String assemble(Plan plan, Map<String, String> parts) {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 512 512\">\n<defs>\n");
        for (Part part : PARTS) {
            svg.append(parts.get(part.id()).replaceFirst("<g>", "<g id=\"" + part.id() + "\">")).append('\n');
        }
        svg.append("<g id=\"blossom\">\n");
        for (Ring ring : plan.rings()) {
            for (int i = 0; i < ring.count(); i++) {
                boolean center = ring.id().equals(CENTER.id());
                double angle = 360.0 * i / ring.count() + ring.angleOffset() + (center ? 0 : WOBBLE[i % WOBBLE.length]);
                int sidestep = center ? 0 : SIDESTEP[i % SIDESTEP.length];
                svg.append("<use xlink:href=\"#").append(ring.id()).append("\" transform=\"rotate(").append(number(angle))
                        .append(") translate(").append(sidestep).append(' ').append(ring.shiftY()).append(") scale(")
                        .append(number(ring.sizePercent() / 100.0)).append(")\"/>\n");
            }
        }
        svg.append("</g>\n</defs>\n");
        for (double[] leaf : LEAVES) {
            svg.append("<use xlink:href=\"#leaf\" transform=\"translate(256 500) rotate(").append(number(leaf[0]))
                    .append(") scale(").append(number(leaf[1])).append(")\"/>\n");
        }
        for (double[] blossom : BLOSSOMS) {
            svg.append("<use xlink:href=\"#blossom\" transform=\"translate(").append(number(blossom[0])).append(' ')
                    .append(number(blossom[1])).append(") rotate(").append(number(blossom[3])).append(") scale(")
                    .append(number(blossom[2])).append(")\"/>\n");
        }
        return svg.append("</svg>\n").toString();
    }

    private static String number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    // ---------------------------------------------------------------- generate

    /**
     * Asks for the plan and the parts, assembles the bush and stores it.
     *
     * @param modelName recorded with the artwork
     * @return {@code null} on success, otherwise why it failed
     */
    static String generate(Model model, AssetStore store, AssetSpec spec, String modelName) {
        Plan plan = null;
        String problem = null;
        for (int attempt = 1; attempt <= ATTEMPTS && plan == null; attempt++) {
            try {
                plan = parsePlan(model.ask(SYSTEM, PLAN_PROMPT + again(problem)));
            } catch (Unusable e) {
                problem = e.getMessage();
            } catch (IOException e) {
                return e.getMessage(); // a network or service problem does not get better by asking again
            }
        }
        if (plan == null) {
            return "the plan was unusable: " + problem + " (after " + ATTEMPTS + " attempts)";
        }

        // All parts at the same time; each has its own conversation about what was wrong.
        Plan thePlan = plan;
        Map<String, String> shapes = new LinkedHashMap<>();
        plan.rings().forEach(ring -> shapes.put(ring.id(), ring.shape()));
        ExecutorService requests = Executors.newFixedThreadPool(PARTS.size(), runnable -> {
            Thread thread = new Thread(runnable, "3d4j-part");
            thread.setDaemon(true);
            return thread;
        });
        Map<String, String> parts = new LinkedHashMap<>();
        try {
            Map<Part, Future<String>> pending = new LinkedHashMap<>();
            for (Part part : PARTS) {
                pending.put(part, requests.submit(() -> part(model, part, thePlan.colors().get(part.id()),
                        shapes.get(part.id()))));
            }
            for (Map.Entry<Part, Future<String>> entry : pending.entrySet()) {
                try {
                    parts.put(entry.getKey().id(), entry.getValue().get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    return cause instanceof IOException ? cause.getMessage()
                            : "part " + entry.getKey().id() + " was unusable: " + cause.getMessage()
                            + " (after " + ATTEMPTS + " attempts)";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
            }
        } finally {
            requests.shutdownNow();
        }

        String svg = assemble(plan, parts);
        try {
            BufferedImage image = SvgRasterizer.render(svg, spec.width(), spec.height());
            double coverage = SvgRasterizer.coverage(image);
            if (coverage < 0.10 || coverage > 0.93) {
                return String.format(Locale.ROOT, "the assembled picture covers %.0f %% of the area", coverage * 100);
            }
            JsonObject details = new JsonObject();
            details.addProperty("recipe", "flower-bush");
            details.add("plan", plan.toJson());
            store.save(spec, svg, image, modelName, details);
            return null;
        } catch (SvgRasterizer.UnreadableSvgException e) {
            return "the assembled picture cannot be drawn: " + e.getMessage();
        } catch (IOException e) {
            return "cannot write to " + store.directory() + ": " + e.getMessage();
        }
    }

    /** One part, asked for again with the reason while the answer is unusable. */
    private static String part(Model model, Part part, String color, String shape) throws IOException, Unusable {
        String problem = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return cleanPart(part, color, model.ask(SYSTEM, partPrompt(part, color, shape) + again(problem)));
            } catch (Unusable e) {
                problem = e.getMessage();
            }
        }
        throw new Unusable(problem);
    }

    private static String again(String problem) {
        return problem == null ? "" : "\n\nYour previous answer was unusable: " + problem
                + ". Follow the rules exactly and answer with the corrected result only.";
    }
}
