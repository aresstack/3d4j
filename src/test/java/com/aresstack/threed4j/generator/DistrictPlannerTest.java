package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictPlannerTest {

    private static final String THEME = """
            Sure! ```json
            {"name": "Hafenviertel", "style": "a small harbour quarter", "palette": {"walls": ["#F4F1E8", "#DCE9F2"],
             "roofs": ["#3F6E9A"], "lawn": "#E6D7A8", "street": "#B9B2A6", "plaza": "#D9CDB8"},
             "buildings": {"minHeight": 5, "maxHeight": 11, "minWidth": 8, "maxWidth": 12, "roofs": ["pyramid", "dome"]},
             "landmark": {"name": "Leuchtturm", "idea": "a striped lighthouse"}}
            ```""";

    private static String motifs() {
        StringBuilder json = new StringBuilder("{");
        for (PartLibrary.Kind kind : TownLibrary.districtKinds()) {
            json.append('"').append(kind.fileName()).append("\": {\"name\": \"Ding\", \"plural\": \"Dinge\", "
                    + "\"variations\": [\"one\", \"two\"]},");
        }
        return json.substring(0, json.length() - 1) + "}";
    }

    /** Answers like a model: by the task named in the question; the first outline of the tower is unusable. */
    private static FlowerComposer.Model model(List<String> asked) {
        return (system, user) -> {
            asked.add(user.substring(0, user.indexOf('\n')));
            if (user.startsWith("TASK: theme")) {
                return THEME;
            }
            if (user.startsWith("TASK: motifs")) {
                return motifs();
            }
            if (user.startsWith("TASK: plan of a landmark")) {
                return "{\"components\": [{\"name\": \"quay\", \"shape\": \"extrude\", \"base\": 3, \"height\": 1, "
                        + "\"width\": 5, \"depth\": 4, \"color\": \"#B9B2A6\"}, {\"name\": \"tower\", \"shape\": \"lathe\", "
                        + "\"base\": 4, \"height\": 20, \"width\": 2.5, \"color\": \"#FFFFFF\", \"topColor\": \"#E7475E\"}]}";
            }
            if (user.contains("\"tower\"")) {
                return user.contains("previous answer") ? "{\"outline\": [[1,0],[0.7,1]]}" : "{\"outline\": [[1,1],[1,0]]}";
            }
            return "{\"outline\": [[-1,-1],[1,-1],[1,0],[0,0],[0,1],[-1,1]]}";
        };
    }

    @Test
    void plansADistrictStepByStepAndAsksAgainForAnUnusableOutline(@TempDir Path directory) throws Exception {
        List<String> asked = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        District district = DistrictPlanner.plan(model(asked), "some/model", District.ALL, 1, 0, steps::add);

        assertEquals("hafenviertel", district.id());
        assertEquals("Hafenviertel", district.name());
        assertEquals(List.of("pyramid"), district.layout().roofShapes(), "an unknown roof is left out");
        assertEquals(0xE6D7A8, district.layout().lawn());
        assertTrue(district.motif(PartLibrary.Kind.LAWN).variations().get(0).contains("base colour #E6D7A8"));
        assertNotNull(district.motif(PartLibrary.Kind.VISITOR), "the people are the town's");

        District.Landmark landmark = district.layout().landmark();
        assertEquals("Leuchtturm", landmark.name());
        assertEquals(2, landmark.components().size());
        assertEquals(0f, landmark.components().get(0).base(), 0.001f, "nothing floats: the lowest component stands on the ground");
        assertEquals(1f, landmark.components().get(1).base(), 0.001f);
        assertEquals(District.Shape.LATHE, landmark.components().get(1).shape());
        assertEquals(0.7f, landmark.components().get(1).outline().get(1)[0], 0.001f, "the corrected outline");
        assertEquals(21f, landmark.height(), 0.001f);
        assertEquals(6, asked.size(), "theme, motifs, plan, quay, tower twice: " + asked);
        assertTrue(steps.get(steps.size() - 1).contains("Bauteil 2 von 2"), steps.toString());

        // The district survives being written to its file and read again, and the town knows it.
        TownLibrary town = new TownLibrary(new AssetStore(directory));
        town.save(district);
        TownLibrary read = new TownLibrary(new AssetStore(directory));
        assertEquals(3, read.districts().size());
        District again = read.district("hafenviertel");
        assertEquals(district.style(), again.style());
        assertEquals(2, again.layout().landmark().components().size());
        assertEquals(again, read.districtAt(190f, 20f));
        assertNull(read.districtAt(-190f, 20f), "nothing has been planned there yet");
        assertEquals(-1, read.freePlot()[0], "the next district goes to the west of the start");
        assertTrue(JsonParser.parseString(Files.readString(directory.resolve("library/hafenviertel/district.json")))
                .getAsJsonObject().has("landmark"));

        read.demolish(again);
        assertEquals(2, new TownLibrary(new AssetStore(directory)).districts().size());
        assertTrue(Files.isDirectory(directory.resolve("demolished")), "a district torn down is kept aside");
    }

    @Test
    void outlinesAreChecked() {
        assertNull(DistrictPlanner.checkOutline(DistrictPlanner.json("{\"outline\": [[1,0],[0,1]]}"), District.Shape.LATHE));
        assertNotNull(DistrictPlanner.checkOutline(DistrictPlanner.json("{\"outline\": [[1,1],[1,0]]}"), District.Shape.LATHE));
        assertNotNull(DistrictPlanner.checkOutline(DistrictPlanner.json("{\"outline\": [[0.1,0],[0.1,1]]}"), District.Shape.LATHE));
        assertNull(DistrictPlanner.checkOutline(DistrictPlanner.json("{\"outline\": [[-1,-1],[1,-1],[0,1]]}"), District.Shape.EXTRUDE));
        assertNotNull(DistrictPlanner.checkOutline(DistrictPlanner.json("{\"outline\": [[-1,-1],[1,1],[1,-1],[-1,1]]}"),
                District.Shape.EXTRUDE), "edges cross");
        assertNull(DistrictPlanner.json("no json here"));
    }
}
