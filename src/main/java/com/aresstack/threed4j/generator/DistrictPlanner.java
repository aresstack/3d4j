package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.District.Component;
import com.aresstack.threed4j.assets.District.Landmark;
import com.aresstack.threed4j.assets.District.Layout;
import com.aresstack.threed4j.assets.District.Motif;
import com.aresstack.threed4j.assets.District.Shape;
import com.aresstack.threed4j.assets.DistrictFile;
import com.aresstack.threed4j.assets.PartLibrary.Kind;
import com.aresstack.threed4j.assets.TownLibrary;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lets a model plan a district of the town top down, the way a planner and an
 * architect work: first the theme, then what trees, lamps, windows and so on are
 * in it, then the landmark as a list of components, then each component's shape.
 *
 * <p>The model never writes code or three-dimensional geometry. Every step is a
 * question with a small JSON answer; a component's shape is a flat drawing of a
 * few points (half a side view, or an outline seen from above) that the game
 * turns into a body. Every answer is checked; an unusable one is asked for again
 * with the reason.</p>
 */
public final class DistrictPlanner {

    /** An answer that is still unusable after asking again. */
    public static final class Unusable extends Exception {
        Unusable(String message) {
            super(message);
        }
    }

    private static final int ATTEMPTS = 3;
    /** How far a landmark may reach from its middle, and how high. */
    static final float LANDMARK_REACH = 10f;
    static final float LANDMARK_HEIGHT = 45f;
    static final int MAX_COMPONENTS = 7;

    static final String SYSTEM = "You are the town planner and architect of a friendly, pastel-coloured cartoon town "
            + "in a video game. The town grows district by district; every district has a theme of its own. "
            + "You answer with exactly one JSON object and nothing else: no explanation, no markdown fences.";

    private DistrictPlanner() {
    }

    // ------------------------------------------------------------ whole district

    /**
     * Plans a new district for a plot of the town.
     *
     * @param existing  the districts there are, so that the new one differs from them
     * @param modelName recorded as the planner
     * @param progress  told in German what is being planned at the moment
     */
    public static District plan(FlowerComposer.Model model, String modelName, List<District> existing, int plotX,
                                int plotZ, Consumer<String> progress) throws IOException, Unusable {
        progress.accept("plant das Thema eines neuen Viertels");
        JsonObject theme = ask(model, themeTask(existing, plotX, plotZ), DistrictPlanner::checkTheme);
        String name = theme.get("name").getAsString().trim();
        String style = theme.get("style").getAsString().trim();
        JsonObject palette = theme.getAsJsonObject("palette");
        JsonObject buildings = theme.getAsJsonObject("buildings");
        int lawn = DistrictFile.color(palette.get("lawn").getAsString());
        int street = DistrictFile.color(palette.get("street").getAsString());

        progress.accept("plant die Motive für „" + name + "“");
        JsonObject motifAnswer = ask(model, motifsTask(name, style), DistrictPlanner::checkMotifs);
        Map<Kind, Motif> motifs = new EnumMap<>(Kind.class);
        for (Kind kind : TownLibrary.districtKinds()) {
            JsonObject entry = motifAnswer.getAsJsonObject(kind.fileName());
            List<String> variations = new ArrayList<>();
            for (JsonElement variation : entry.getAsJsonArray("variations")) {
                String text = variation.getAsString().trim();
                if (kind == Kind.LAWN || kind == Kind.STREET) {
                    // A ground tile needs its base colour; the palette decides it, not the wording.
                    text += ": base colour " + DistrictFile.hex(kind == Kind.LAWN ? lawn : street)
                            + ", the shapes in slightly lighter and darker shades of the base colour";
                }
                variations.add(text);
            }
            motifs.put(kind, new Motif(entry.get("name").getAsString().trim(), entry.get("plural").getAsString().trim(),
                    variations));
        }
        for (Kind kind : TownLibrary.sharedKinds()) {
            motifs.put(kind, District.sharedMotif(kind));
        }

        float minHeight = clamp(buildings.get("minHeight").getAsFloat(), 4f, 30f);
        float maxHeight = clamp(buildings.get("maxHeight").getAsFloat(), minHeight + 1f, 40f);
        float minHalf = clamp(buildings.get("minWidth").getAsFloat(), 7f, 14f) / 2;
        float maxHalf = clamp(buildings.get("maxWidth").getAsFloat(), minHalf * 2 + 1f, 18f) / 2;
        List<String> roofs = new ArrayList<>();
        for (JsonElement roof : buildings.getAsJsonArray("roofs")) {
            String shape = roof.getAsString().trim().toLowerCase(Locale.ROOT);
            if (List.of("flat", "pyramid", "sawtooth", "attic").contains(shape) && !roofs.contains(shape)) {
                roofs.add(shape);
            }
        }
        if (roofs.isEmpty()) {
            roofs.add("flat");
        }

        JsonObject idea = theme.getAsJsonObject("landmark");
        Landmark landmark = null;
        try {
            landmark = landmark(model, name, style, idea.get("name").getAsString().trim(),
                    idea.get("idea").getAsString().trim(), progress);
        } catch (Unusable e) {
            // The district is worth having without a landmark; one can be planned for it later.
            System.out.println("Town planner: no landmark for " + name + ": " + e.getMessage());
        }
        Layout layout = new Layout(plotX, plotZ, colors(palette.getAsJsonArray("walls")),
                colors(palette.getAsJsonArray("roofs")), lawn, street, DistrictFile.color(palette.get("plaza").getAsString()),
                minHeight, maxHeight, minHalf, maxHalf, clamp(maxHalf * 2 + 5f, 14f, 24f), roofs, landmark,
                modelName == null ? "" : modelName, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return new District(id(name, existing), name, style, motifs, layout);
    }

    /** A folder name for the district: its name in plain letters, different from all there are. */
    static String id(String name, List<District> existing) {
        String plain = name.toLowerCase(Locale.GERMAN).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("ß", "ss");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFD).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (plain.isEmpty()) {
            plain = "viertel";
        }
        String id = plain;
        for (int number = 2; taken(id, existing); number++) {
            id = plain + "-" + number;
        }
        return id;
    }

    private static boolean taken(String id, List<District> existing) {
        return id.equals(TownLibrary.SHARED) || existing.stream().anyMatch(district -> district.id().equals(id));
    }

    static String themeTask(List<District> existing, boolean coastal) {
        return themeTask(existing, coastal ? 1 : 0, 0);
    }

    /** The task with the plan of the town: what there is, where, and where the new district will lie. */
    static String themeTask(List<District> existing, int plotX, int plotZ) {
        StringBuilder task = new StringBuilder("TASK: theme of the next district.\n\n")
                .append(com.aresstack.threed4j.assets.TownPlan.describe(existing, plotX, plotZ));
        if (District.coastal(plotX)) {
            task.append("\nThe new district lies at the sea: a promenade runs along its shore, and its landmark stands "
                    + "on a headland at the water. Choose a theme that suits the coast, but a different one from the "
                    + "other districts at the sea.\n");
        }
        return task + "\nInvent the next district. It must differ clearly from all of these in theme, colours and "
                + "buildings, and still fit a friendly cartoon town. Neither its theme nor its name nor its landmark "
                + "may repeat what the town already has: the landmark must be a different kind of thing from every "
                + "landmark listed above (if there is a lighthouse, then no second lighthouse, beacon or sea tower; "
                + "if there is a fountain, no second fountain; and so on). Let it suit its neighbours on the map. "
                + "Answer with this JSON object:\n"
                + "{\n"
                + "  \"name\": \"German name of the district, one word, e.g. ending in 'viertel'\",\n"
                + "  \"style\": \"one or two English sentences on materials, colours, shapes and mood; an illustrator "
                + "gets this sentence with every drawing for the district\",\n"
                + "  \"palette\": {\n"
                + "    \"walls\": [\"#RRGGBB\", ... 3 to 5 colours of house walls],\n"
                + "    \"roofs\": [\"#RRGGBB\", ... 2 to 4 colours of roofs],\n"
                + "    \"lawn\": \"#RRGGBB colour of the ground beside the streets: grass, sand, gravel, snow ...\",\n"
                + "    \"street\": \"#RRGGBB colour of the street paving\",\n"
                + "    \"plaza\": \"#RRGGBB colour of the square in the middle\"\n"
                + "  },\n"
                + "  \"buildings\": {\n"
                + "    \"minHeight\": lowest house in metres (4 to 30), \"maxHeight\": highest house (up to 40),\n"
                + "    \"minWidth\": narrowest house in metres (7 to 14), \"maxWidth\": widest house (up to 18),\n"
                + "    \"roofs\": [one or more of \"flat\", \"pyramid\", \"sawtooth\", \"attic\"]\n"
                + "  },\n"
                + "  \"landmark\": {\"name\": \"German name of the landmark in the middle of the district\", "
                + "\"idea\": \"one English sentence: what it is and what it looks like\"}\n"
                + "}";
    }

    static String checkTheme(JsonObject theme) {
        for (String field : new String[] {"name", "style"}) {
            if (!isText(theme, field)) {
                return "\"" + field + "\" is missing";
            }
        }
        if (theme.get("name").getAsString().trim().length() > 40) {
            return "\"name\" is too long; one word";
        }
        if (!theme.has("palette") || !theme.get("palette").isJsonObject()) {
            return "\"palette\" is missing";
        }
        JsonObject palette = theme.getAsJsonObject("palette");
        try {
            for (String list : new String[] {"walls", "roofs"}) {
                if (!palette.has(list) || !palette.get(list).isJsonArray() || palette.getAsJsonArray(list).isEmpty()) {
                    return "\"palette." + list + "\" must be a list of colours";
                }
                colors(palette.getAsJsonArray(list));
            }
            for (String single : new String[] {"lawn", "street", "plaza"}) {
                if (!isText(palette, single)) {
                    return "\"palette." + single + "\" is missing";
                }
                DistrictFile.color(palette.get(single).getAsString());
            }
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        if (!theme.has("buildings") || !theme.get("buildings").isJsonObject()) {
            return "\"buildings\" is missing";
        }
        JsonObject buildings = theme.getAsJsonObject("buildings");
        for (String number : new String[] {"minHeight", "maxHeight", "minWidth", "maxWidth"}) {
            if (!isNumber(buildings, number)) {
                return "\"buildings." + number + "\" must be a number";
            }
        }
        if (!buildings.has("roofs") || !buildings.get("roofs").isJsonArray()) {
            return "\"buildings.roofs\" must be a list";
        }
        if (!theme.has("landmark") || !theme.get("landmark").isJsonObject()
                || !isText(theme.getAsJsonObject("landmark"), "name") || !isText(theme.getAsJsonObject("landmark"), "idea")) {
            return "\"landmark\" needs \"name\" and \"idea\"";
        }
        return null;
    }

    static String motifsTask(String name, String style) {
        return "TASK: motifs of a district.\n\nDistrict: " + name + ". Its look: " + style + "\n\n"
                + "An illustrator will draw the district's things one by one. Say what each of these is in this "
                + "district, with a few variations that differ from each other:\n"
                + "- \"blossom-tree\": the district's main tree or tall plant\n"
                + "- \"green-tree\": a second tree or tall plant\n"
                + "- \"lamp\": the street lamp (all lamps of a district are of one make)\n"
                + "- \"bush\": something low that stands between the houses: a bush, flowers, a barrel, a crate ...\n"
                + "- \"lawn\": the ground beside the streets, seen from above: describe only the pattern, no colours\n"
                + "- \"street\": the street paving, seen from above: describe only the pattern, no colours\n"
                + "- \"window\": a window of a house front\n"
                + "- \"door\": a house door\n"
                + "- \"shop\": what a ground floor has instead of a window: a shop window, a gate, a stall ...\n"
                + "- \"animal\": animals that walk the streets among the people and suit the district; describe each "
                + "sitting or standing upright, seen from the front\n\n"
                + "Answer with this JSON object, with all ten keys:\n"
                + "{\"blossom-tree\": {\"name\": \"German word, one of it\", \"plural\": \"German word, several\", "
                + "\"variations\": [\"3 to 6 short English descriptions, each of one thing to draw\"]}, "
                + "\"green-tree\": {...}, \"lamp\": {...}, \"bush\": {...}, \"lawn\": {...}, \"street\": {...}, "
                + "\"window\": {...}, \"door\": {...}, \"shop\": {...}, \"animal\": {...}}";
    }

    static String checkMotifs(JsonObject motifs) {
        for (Kind kind : TownLibrary.districtKinds()) {
            String key = kind.fileName();
            if (!motifs.has(key) || !motifs.get(key).isJsonObject()) {
                return "the key \"" + key + "\" is missing";
            }
            JsonObject entry = motifs.getAsJsonObject(key);
            if (!isText(entry, "name") || !isText(entry, "plural")) {
                return "\"" + key + "\" needs \"name\" and \"plural\"";
            }
            if (!entry.has("variations") || !entry.get("variations").isJsonArray()
                    || entry.getAsJsonArray("variations").isEmpty()) {
                return "\"" + key + "\" needs a list \"variations\"";
            }
            for (JsonElement variation : entry.getAsJsonArray("variations")) {
                if (!variation.isJsonPrimitive() || variation.getAsString().isBlank()) {
                    return "the variations of \"" + key + "\" must be texts";
                }
            }
        }
        return null;
    }

    // ----------------------------------------------------------------- landmark

    /** Thinks of another landmark for a district than the one it has, and plans it. */
    public static Landmark anotherLandmark(FlowerComposer.Model model, District district, Consumer<String> progress)
            throws IOException, Unusable {
        progress.accept("überlegt ein neues Wahrzeichen für „" + district.name() + "“");
        Landmark old = district.layout().landmark();
        JsonObject idea = ask(model, "TASK: idea for a landmark.\n\nDistrict: " + district.name() + ". Its look: "
                + district.style() + "\n" + (old == null ? "" : "Its landmark so far: " + old.name() + " (" + old.idea()
                + "). Invent a clearly different one.\n")
                + "\nThe landmark stands in the middle of the district's square. Answer with this JSON object:\n"
                + "{\"name\": \"German name of the landmark\", \"idea\": \"one English sentence: what it is and what it "
                + "looks like\"}", answer -> isText(answer, "name") && isText(answer, "idea") ? null
                : "\"name\" and \"idea\" are needed");
        return landmark(model, district.name(), district.style(), idea.get("name").getAsString().trim(),
                idea.get("idea").getAsString().trim(), progress);
    }

    /** Plans a landmark like an architect: the components first, then the shape of each. */
    static Landmark landmark(FlowerComposer.Model model, String districtName, String style, String name, String idea,
                             Consumer<String> progress) throws IOException, Unusable {
        progress.accept("plant das Wahrzeichen „" + name + "“");
        JsonObject plan = ask(model, landmarkTask(districtName, style, name, idea), DistrictPlanner::checkLandmarkPlan);
        JsonArray planned = plan.getAsJsonArray("components");
        List<Component> components = new ArrayList<>();
        float lowest = Float.MAX_VALUE;
        for (JsonElement element : planned) {
            lowest = Math.min(lowest, element.getAsJsonObject().get("base").getAsFloat());
        }
        for (int index = 0; index < planned.size(); index++) {
            JsonObject entry = planned.get(index).getAsJsonObject();
            Shape shape = entry.get("shape").getAsString().trim().equalsIgnoreCase("lathe") ? Shape.LATHE : Shape.EXTRUDE;
            String part = entry.get("name").getAsString().trim();
            String look = isText(entry, "look") ? entry.get("look").getAsString().trim() : part;
            // Nothing floats: the lowest component stands on the ground.
            float base = clamp(entry.get("base").getAsFloat() - Math.max(0f, lowest), 0f, LANDMARK_HEIGHT - 0.5f);
            float height = clamp(entry.get("height").getAsFloat(), 0.2f, LANDMARK_HEIGHT - base);
            float width = clamp(entry.get("width").getAsFloat(), 0.1f, LANDMARK_REACH);
            float depth = shape == Shape.LATHE ? width
                    : clamp(isNumber(entry, "depth") ? entry.get("depth").getAsFloat() : width, 0.1f, LANDMARK_REACH);
            float roofHeight = shape == Shape.EXTRUDE && isNumber(entry, "roofHeight")
                    ? clamp(entry.get("roofHeight").getAsFloat(), 0f, 20f) : 0f;
            float x = clamp(isNumber(entry, "x") ? entry.get("x").getAsFloat() : 0f, -LANDMARK_REACH + width, LANDMARK_REACH - width);
            float z = clamp(isNumber(entry, "z") ? entry.get("z").getAsFloat() : 0f, -LANDMARK_REACH + depth, LANDMARK_REACH - depth);
            int color = DistrictFile.color(entry.get("color").getAsString());
            int topColor = isText(entry, "topColor") ? colorOr(entry.get("topColor").getAsString(), color) : color;

            progress.accept("zeichnet für „" + name + "“ Bauteil " + (index + 1) + " von " + planned.size() + ": " + part);
            List<float[]> outline;
            try {
                JsonObject drawn = ask(model, outlineTask(name, part, look, shape, width, depth, height),
                        answer -> checkOutline(answer, shape));
                outline = points(drawn.getAsJsonArray("outline"), shape);
            } catch (Unusable e) {
                // A plain body in its place: a cylinder or a box. The landmark still stands.
                System.out.println("Town planner: plain shape for " + part + " of " + name + ": " + e.getMessage());
                outline = shape == Shape.LATHE ? List.of(new float[] {1f, 0f}, new float[] {1f, 1f})
                        : List.of(new float[] {-1f, -1f}, new float[] {1f, -1f}, new float[] {1f, 1f}, new float[] {-1f, 1f});
            }
            components.add(new Component(part, shape, x, z, base, height, width, depth, roofHeight, color, topColor, outline));
        }
        return new Landmark(name, idea, components);
    }

    static String landmarkTask(String districtName, String style, String name, String idea) {
        return "TASK: plan of a landmark.\n\nYou are the architect of the landmark \"" + name + "\": " + idea + "\n"
                + "It stands in the middle of the square of the district " + districtName + ", whose look is: " + style
                + "\n\nPlan how it is built from 2 to " + MAX_COMPONENTS + " components, from the ground up. Every "
                + "component is a simple solid of one of two kinds:\n"
                + "- \"lathe\": a body that is round around its upright axis: a shaft, a mast, a tank, a dome, a bowl, "
                + "a cone, a disc, a ball\n"
                + "- \"extrude\": a body with a flat outline seen from above, pulled straight up: a block, a wall, a "
                + "slab, a pillar; it may carry a pointed roof\n\n"
                + "Measures are metres. The ground is at height 0; the middle of the landmark is x = 0, z = 0. The "
                + "whole landmark is at most " + (int) LANDMARK_HEIGHT + " high and stays within " + (int) LANDMARK_REACH
                + " of its middle. Every component stands on the ground or on a component below it. A later step "
                + "asks you for the exact outline of each component.\n\n"
                + "Answer with this JSON object:\n"
                + "{\"components\": [{\"name\": \"English name of the component\", \"shape\": \"lathe\" or \"extrude\", "
                + "\"x\": 0, \"z\": 0, \"base\": height of its lowest point, \"height\": its own height, "
                + "\"width\": lathe: its greatest radius; extrude: half its extent along x, "
                + "\"depth\": extrude only: half its extent along z, "
                + "\"roofHeight\": extrude only: height of a pointed roof on top, or 0, "
                + "\"color\": \"#RRGGBB\", \"topColor\": \"#RRGGBB of faces that look up\", "
                + "\"look\": \"one English sentence on its outline\"}]}";
    }

    static String checkLandmarkPlan(JsonObject plan) {
        if (!plan.has("components") || !plan.get("components").isJsonArray()) {
            return "\"components\" must be a list";
        }
        JsonArray components = plan.getAsJsonArray("components");
        if (components.isEmpty() || components.size() > MAX_COMPONENTS) {
            return "there must be 1 to " + MAX_COMPONENTS + " components";
        }
        for (JsonElement element : components) {
            if (!element.isJsonObject()) {
                return "every component must be an object";
            }
            JsonObject entry = element.getAsJsonObject();
            if (!isText(entry, "name")) {
                return "every component needs a \"name\"";
            }
            String shape = isText(entry, "shape") ? entry.get("shape").getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (!shape.equals("lathe") && !shape.equals("extrude")) {
                return "\"shape\" must be \"lathe\" or \"extrude\"";
            }
            for (String number : new String[] {"base", "height", "width"}) {
                if (!isNumber(entry, number)) {
                    return "\"" + number + "\" of " + entry.get("name").getAsString() + " must be a number";
                }
            }
            try {
                DistrictFile.color(isText(entry, "color") ? entry.get("color").getAsString() : "");
            } catch (IllegalArgumentException e) {
                return "\"color\" of " + entry.get("name").getAsString() + ": " + e.getMessage();
            }
        }
        return null;
    }

    static String outlineTask(String landmark, String part, String look, Shape shape, float width, float depth,
                              float height) {
        String start = "TASK: outline of a component.\n\nComponent \"" + part + "\" of the landmark \"" + landmark
                + "\": " + look + "\n\n";
        if (shape == Shape.LATHE) {
            return start + "It is a lathe body, " + number(height) + " m high with a greatest radius of " + number(width)
                    + " m. Draw half of its side view as a line from its bottom to its top: 2 to 12 points "
                    + "[radius, height]. Both numbers go from 0 to 1: radius 1 is the greatest radius, height 0 the "
                    + "bottom and height 1 the top. Heights never decrease from one point to the next. At least one "
                    + "point has radius 1.\n"
                    + "Examples: a cylinder [[1,0],[1,1]]; a cone [[1,0],[0,1]]; a dome "
                    + "[[1,0],[0.92,0.4],[0.7,0.7],[0.38,0.92],[0,1]]; a tank on a thin stem "
                    + "[[0.2,0],[0.2,0.5],[1,0.55],[1,0.95],[0,1]]; a platform with a railing "
                    + "[[0.8,0],[1,0.2],[1,1],[0.9,1]].\n\n"
                    + "Answer with this JSON object: {\"outline\": [[radius, height], ...]}";
        }
        return start + "It is an extruded body, " + number(height) + " m high, " + number(width * 2) + " m along x and "
                + number(depth * 2) + " m along z. Draw its outline seen from above as a polygon: 3 to 12 corners "
                + "[x, z] in order around the shape, edges must not cross. Both numbers go from -1 to 1: x = 1 is the "
                + "right end, z = 1 the far end. Use the whole range in both directions.\n"
                + "Examples: a rectangle [[-1,-1],[1,-1],[1,1],[-1,1]]; an octagon "
                + "[[-0.4,-1],[0.4,-1],[1,-0.4],[1,0.4],[0.4,1],[-0.4,1],[-1,0.4],[-1,-0.4]]; an L "
                + "[[-1,-1],[1,-1],[1,0],[0,0],[0,1],[-1,1]]; a triangle [[-1,-1],[1,-1],[0,1]].\n\n"
                + "Answer with this JSON object: {\"outline\": [[x, z], ...]}";
    }

    static String checkOutline(JsonObject answer, Shape shape) {
        if (!answer.has("outline") || !answer.get("outline").isJsonArray()) {
            return "\"outline\" must be a list of points";
        }
        List<float[]> points;
        try {
            points = points(answer.getAsJsonArray("outline"), shape);
        } catch (RuntimeException e) {
            return "every point must be a pair of numbers";
        }
        if (shape == Shape.LATHE) {
            if (points.size() < 2 || points.size() > 16) {
                return "give 2 to 12 points";
            }
            float widest = 0f;
            for (int i = 0; i < points.size(); i++) {
                widest = Math.max(widest, points.get(i)[0]);
                if (i > 0 && points.get(i)[1] < points.get(i - 1)[1] - 0.001f) {
                    return "heights must never decrease from one point to the next";
                }
            }
            if (widest < 0.3f) {
                return "the body is too thin: at least one point must have radius 1";
            }
            if (points.get(points.size() - 1)[1] - points.get(0)[1] < 0.3f) {
                return "the line must run from the bottom (height 0) to the top (height 1)";
            }
            return null;
        }
        if (points.size() < 3 || points.size() > 16) {
            return "give 3 to 12 corners";
        }
        if (Math.abs(area(points)) < 0.15f) {
            return "the outline encloses almost no area; use the whole range from -1 to 1";
        }
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 2; j < points.size(); j++) {
                if (i == 0 && j == points.size() - 1) {
                    continue; // neighbors around the end of the list
                }
                if (cross(points.get(i), points.get((i + 1) % points.size()), points.get(j), points.get((j + 1) % points.size()))) {
                    return "edges of the outline cross; list the corners in order around the shape";
                }
            }
        }
        return null;
    }

    /** The points of an answer, small overshoots pulled back into the allowed range. */
    static List<float[]> points(JsonArray array, Shape shape) {
        List<float[]> points = new ArrayList<>();
        for (JsonElement element : array) {
            JsonArray pair = element.getAsJsonArray();
            float first = pair.get(0).getAsFloat();
            float second = pair.get(1).getAsFloat();
            if (!Float.isFinite(first) || !Float.isFinite(second)) {
                throw new IllegalArgumentException("not a number");
            }
            points.add(new float[] {clamp(first, shape == Shape.LATHE ? 0f : -1f, 1f),
                    clamp(second, shape == Shape.LATHE ? 0f : -1f, 1f)});
        }
        return points;
    }

    /** The signed area of a polygon; its sign tells which way round the corners run. */
    public static float area(List<float[]> polygon) {
        float sum = 0f;
        for (int i = 0; i < polygon.size(); i++) {
            float[] p = polygon.get(i);
            float[] q = polygon.get((i + 1) % polygon.size());
            sum += p[0] * q[1] - q[0] * p[1];
        }
        return sum / 2;
    }

    private static boolean cross(float[] a, float[] b, float[] c, float[] d) {
        float d1 = side(c, d, a);
        float d2 = side(c, d, b);
        float d3 = side(a, b, c);
        float d4 = side(a, b, d);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static float side(float[] from, float[] to, float[] point) {
        return (to[0] - from[0]) * (point[1] - from[1]) - (to[1] - from[1]) * (point[0] - from[0]);
    }

    // ------------------------------------------------------------------ helpers

    private interface Check {
        /** @return {@code null} if the answer is usable, otherwise what is wrong with it */
        String problem(JsonObject answer);
    }

    /** Asks until the answer is a usable JSON object, each time again with what was wrong. */
    private static JsonObject ask(FlowerComposer.Model model, String task, Check check) throws IOException, Unusable {
        String problem = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String answer = model.ask(SYSTEM, task + (problem == null ? "" : "\n\nYour previous answer was unusable: "
                    + problem + ". Answer again with the corrected JSON object only."));
            JsonObject object = json(answer);
            if (object == null) {
                problem = "it was not a JSON object";
                continue;
            }
            try {
                problem = check.problem(object);
            } catch (RuntimeException e) {
                problem = "a value has the wrong type (" + e.getMessage() + ")";
            }
            if (problem == null) {
                return object;
            }
        }
        throw new Unusable(problem + " (after " + ATTEMPTS + " attempts)");
    }

    /** The JSON object in an answer, also when the model wrapped it in words or a fence. */
    static JsonObject json(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(answer.substring(start, end + 1));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isText(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive() && !object.get(name).getAsString().isBlank();
    }

    private static boolean isNumber(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) {
            return false;
        }
        try {
            return Float.isFinite(object.get(name).getAsFloat());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<Integer> colors(JsonArray array) {
        List<Integer> colors = new ArrayList<>();
        for (JsonElement color : array) {
            colors.add(DistrictFile.color(color.getAsString()));
        }
        return colors;
    }

    private static int colorOr(String hex, int otherwise) {
        try {
            return DistrictFile.color(hex);
        } catch (IllegalArgumentException e) {
            return otherwise;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
