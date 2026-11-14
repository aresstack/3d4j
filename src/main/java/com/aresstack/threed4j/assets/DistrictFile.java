package com.aresstack.threed4j.assets;

import com.aresstack.threed4j.assets.District.Component;
import com.aresstack.threed4j.assets.District.Landmark;
import com.aresstack.threed4j.assets.District.Layout;
import com.aresstack.threed4j.assets.District.Motif;
import com.aresstack.threed4j.assets.PartLibrary.Kind;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A planned district as a file: {@code district.json} in the district's folder of
 * the library. It holds everything a model decided, so that the district can be
 * read by a person, changed by hand and built again at the next start.
 */
public final class DistrictFile {

    public static final String NAME = "district.json";

    private DistrictFile() {
    }

    public static void write(Path file, District district) throws IOException {
        Layout layout = district.layout();
        JsonObject root = new JsonObject();
        root.addProperty("id", district.id());
        root.addProperty("name", district.name());
        root.addProperty("style", district.style());
        JsonArray plot = new JsonArray();
        plot.add(layout.plotX());
        plot.add(layout.plotZ());
        root.add("plot", plot);
        root.addProperty("plannedBy", layout.plannedBy());
        root.addProperty("plannedAt", layout.plannedAt());

        JsonObject palette = new JsonObject();
        palette.add("walls", colors(layout.walls()));
        palette.add("roofs", colors(layout.roofs()));
        palette.addProperty("lawn", hex(layout.lawn()));
        palette.addProperty("street", hex(layout.street()));
        palette.addProperty("plaza", hex(layout.plaza()));
        root.add("palette", palette);

        JsonObject buildings = new JsonObject();
        buildings.addProperty("minHeight", layout.minHeight());
        buildings.addProperty("maxHeight", layout.maxHeight());
        buildings.addProperty("minWidth", layout.minHalf() * 2);
        buildings.addProperty("maxWidth", layout.maxHalf() * 2);
        buildings.addProperty("spacing", layout.spacing());
        JsonArray roofShapes = new JsonArray();
        layout.roofShapes().forEach(roofShapes::add);
        buildings.add("roofs", roofShapes);
        root.add("buildings", buildings);

        JsonObject motifs = new JsonObject();
        for (Kind kind : TownLibrary.districtKinds()) {
            Motif motif = district.motif(kind);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", motif.name());
            entry.addProperty("plural", motif.plural());
            JsonArray variations = new JsonArray();
            motif.variations().forEach(variations::add);
            entry.add("variations", variations);
            motifs.add(kind.fileName(), entry);
        }
        root.add("motifs", motifs);
        if (layout.landmark() != null) {
            root.add("landmark", landmark(layout.landmark()));
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n",
                StandardCharsets.UTF_8);
    }

    private static JsonObject landmark(Landmark landmark) {
        JsonObject root = new JsonObject();
        root.addProperty("name", landmark.name());
        root.addProperty("idea", landmark.idea());
        JsonArray components = new JsonArray();
        for (Component component : landmark.components()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", component.name());
            entry.addProperty("shape", component.shape().name().toLowerCase(Locale.ROOT));
            entry.addProperty("x", component.x());
            entry.addProperty("z", component.z());
            entry.addProperty("base", component.base());
            entry.addProperty("height", component.height());
            entry.addProperty("width", component.width());
            entry.addProperty("depth", component.depth());
            entry.addProperty("roofHeight", component.roofHeight());
            entry.addProperty("color", hex(component.color()));
            entry.addProperty("topColor", hex(component.topColor()));
            JsonArray outline = new JsonArray();
            for (float[] point : component.outline()) {
                JsonArray pair = new JsonArray();
                pair.add(point[0]);
                pair.add(point[1]);
                outline.add(pair);
            }
            entry.add("outline", outline);
            components.add(entry);
        }
        root.add("components", components);
        return root;
    }

    /**
     * @return the district
     * @throws IOException if the file cannot be read or does not describe a district
     */
    public static District read(Path file) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject palette = root.getAsJsonObject("palette");
            JsonObject buildings = root.getAsJsonObject("buildings");
            List<String> roofShapes = new ArrayList<>();
            buildings.getAsJsonArray("roofs").forEach(shape -> roofShapes.add(shape.getAsString()));
            Map<Kind, Motif> motifs = new EnumMap<>(Kind.class);
            JsonObject entries = root.getAsJsonObject("motifs");
            for (Kind kind : TownLibrary.districtKinds()) {
                if (kind == Kind.ANIMAL && !entries.has(kind.fileName())) {
                    motifs.put(kind, District.townAnimals()); // a plan from before districts had animals
                    continue;
                }
                JsonObject entry = entries.getAsJsonObject(kind.fileName());
                List<String> variations = new ArrayList<>();
                entry.getAsJsonArray("variations").forEach(variation -> variations.add(variation.getAsString()));
                if (variations.isEmpty()) {
                    throw new IOException("no variations for " + kind.fileName());
                }
                motifs.put(kind, new Motif(entry.get("name").getAsString(), entry.get("plural").getAsString(), variations));
            }
            for (Kind kind : TownLibrary.sharedKinds()) {
                motifs.put(kind, District.sharedMotif(kind));
            }
            JsonArray plot = root.getAsJsonArray("plot");
            Layout layout = new Layout(plot.get(0).getAsInt(), plot.get(1).getAsInt(),
                    colors(palette.getAsJsonArray("walls")), colors(palette.getAsJsonArray("roofs")),
                    color(palette.get("lawn").getAsString()), color(palette.get("street").getAsString()),
                    color(palette.get("plaza").getAsString()),
                    buildings.get("minHeight").getAsFloat(), buildings.get("maxHeight").getAsFloat(),
                    buildings.get("minWidth").getAsFloat() / 2, buildings.get("maxWidth").getAsFloat() / 2,
                    buildings.get("spacing").getAsFloat(), roofShapes,
                    root.has("landmark") ? landmark(root.getAsJsonObject("landmark")) : null,
                    text(root, "plannedBy"), text(root, "plannedAt"));
            if (layout.walls().isEmpty() || layout.roofs().isEmpty()) {
                throw new IOException("the palette lacks walls or roofs");
            }
            return new District(root.get("id").getAsString(), root.get("name").getAsString(),
                    root.get("style").getAsString(), motifs, layout);
        } catch (RuntimeException e) {
            throw new IOException(file + " does not describe a district: " + e.getMessage(), e);
        }
    }

    private static String text(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }

    private static Landmark landmark(JsonObject root) {
        List<Component> components = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("components")) {
            JsonObject entry = element.getAsJsonObject();
            List<float[]> outline = new ArrayList<>();
            for (JsonElement pair : entry.getAsJsonArray("outline")) {
                outline.add(new float[] {pair.getAsJsonArray().get(0).getAsFloat(), pair.getAsJsonArray().get(1).getAsFloat()});
            }
            components.add(new Component(entry.get("name").getAsString(),
                    District.Shape.valueOf(entry.get("shape").getAsString().toUpperCase(Locale.ROOT)),
                    entry.get("x").getAsFloat(), entry.get("z").getAsFloat(), entry.get("base").getAsFloat(),
                    entry.get("height").getAsFloat(), entry.get("width").getAsFloat(), entry.get("depth").getAsFloat(),
                    entry.get("roofHeight").getAsFloat(), color(entry.get("color").getAsString()),
                    color(entry.get("topColor").getAsString()), outline));
        }
        return new Landmark(root.get("name").getAsString(), root.get("idea").getAsString(), components);
    }

    private static JsonArray colors(List<Integer> colors) {
        JsonArray array = new JsonArray();
        colors.forEach(color -> array.add(hex(color)));
        return array;
    }

    private static List<Integer> colors(JsonArray array) {
        List<Integer> colors = new ArrayList<>();
        array.forEach(color -> colors.add(color(color.getAsString())));
        return colors;
    }

    /** {@code #RRGGBB} for an RGB number. */
    public static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    /**
     * @return the RGB number of {@code #RRGGBB} or {@code #RGB}
     * @throws IllegalArgumentException if the text is not such a colour
     */
    public static int color(String hex) {
        String digits = hex == null ? "" : hex.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 3) {
            digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1) + digits.charAt(2)
                    + digits.charAt(2);
        }
        if (!digits.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("'" + hex + "' is not a colour like #A2D08C");
        }
        return Integer.parseInt(digits, 16);
    }
}
