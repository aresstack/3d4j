package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.SvgRasterizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerComposerTest {

    private static final String PLAN = """
            Here is the plan:
            {"layers":[
              {"id":"outerA","count":8,"angleOffset":12,"sizePercent":100,"shiftY":-5,"shapeDescription":"broad wavy petal"},
              {"id":"outerB","count":9,"angleOffset":5,"sizePercent":85,"shiftY":-8,"shapeDescription":"lobed petal"},
              {"id":"inner","count":40,"angleOffset":99,"sizePercent":10,"shiftY":-12,"shapeDescription":"tight \\"petal\\" <b>"},
              {"id":"center","count":3,"angleOffset":0,"sizePercent":50,"shiftY":-9,"shapeDescription":"bud"}],
             "colors":{"outerA":"#F48FBF","outerB":"#F7A8CF","inner":"#fcd","center":"yellow"}}
            Hope that helps.""";

    private static final Map<String, String> GOOD = Map.of(
            "outerA", "<g><path d=\"M0,0 C-85,-40 -80,-150 -20,-190 C0,-175 20,-195 40,-180 C90,-130 70,-40 0,0 Z\" fill=\"#F48FBF\"/>"
                    + "<path d=\"M0,-20 C-40,-60 -40,-120 0,-160 C30,-120 30,-60 0,-20 Z\" fill=\"#FFD3E6\"/></g>",
            "outerB", "<g><path d=\"M0,0 C-80,-30 -75,-120 -30,-150 C0,-160 30,-150 45,-140 C85,-100 60,-30 0,0 Z\" fill=\"#F7A8CF\"/></g>",
            "inner", "<g><path d=\"M0,0 C-55,-30 -50,-90 0,-110 C50,-90 55,-30 0,0 Z\" fill=\"#FFC2DD\"/></g>",
            "center", "<g><circle cx=\"0\" cy=\"0\" r=\"26\" fill=\"#FFE08A\"/><circle cx=\"-9\" cy=\"-7\" r=\"6\" fill=\"#F2B84B\"/></g>",
            "leaf", "<g><path d=\"M0,0 C-45,-50 -40,-120 0,-170 C40,-120 45,-50 0,0 Z\" fill=\"#74C365\"/>"
                    + "<path d=\"M-3,-10 L0,-150 L3,-10 Z\" fill=\"#3E8E41\"/></g>");

    private static FlowerComposer.Part partOf(String prompt) {
        for (FlowerComposer.Part part : FlowerComposer.PARTS) {
            if (prompt.contains(part.task())) {
                return part;
            }
        }
        throw new IllegalArgumentException("not a part prompt: " + prompt);
    }

    @Test
    void readsThePlanAndPullsNumbersIntoTheirRanges() throws Exception {
        FlowerComposer.Plan plan = FlowerComposer.parsePlan(PLAN);

        assertEquals(List.of("outerA", "outerB", "inner", "center"), plan.rings().stream().map(FlowerComposer.Ring::id).toList());
        FlowerComposer.Ring inner = plan.rings().get(2);
        assertEquals(14, inner.count());
        assertEquals(25, inner.angleOffset());
        assertEquals(55, inner.sizePercent());
        assertFalse(inner.shape().contains("\"") || inner.shape().contains("<"), inner.shape());
        FlowerComposer.Ring center = plan.rings().get(3);
        assertEquals(1, center.count());
        assertEquals(100, center.sizePercent());
        assertEquals(0, center.shiftY());
        assertEquals("#fcd", plan.colors().get("inner"));
        assertEquals(FlowerComposer.CENTER.defaultColor(), plan.colors().get("center"), "not a colour: the default");
        assertEquals(FlowerComposer.LEAF.defaultColor(), plan.colors().get("leaf"), "missing: the default");
    }

    @Test
    void aPlanWithoutTheFourLayersInOrderIsUnusable() {
        assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.parsePlan("Sorry, I cannot do that."));
        assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.parsePlan("{\"layers\":[]}"));
        assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.parsePlan("{\"layers\": 4}"));
        assertThrows(FlowerComposer.Unusable.class,
                () -> FlowerComposer.parsePlan(PLAN.replace("\"id\":\"outerB\"", "\"id\":\"middle\"")));
        assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.parsePlan("{\"layers\":[{]}"));
    }

    @Test
    void aPartIsRebuiltWithThePlanColourClippedShadingAndTheOutlineOnTop() throws Exception {
        String answer = "```xml\n<g id=\"petal\" transform=\"rotate(40)\"><!-- silhouette -->\n"
                + "<path d=\"M0,0 C-85,-40 -80,-150 -20,-190 C0,-175 20,-195 40,-180 C90,-130 70,-40 0,0 Z\" fill=\"red\""
                + " stroke=\"#000\" stroke-width=\"40\" opacity=\"0.2\" onclick=\"x()\"/>\n"
                + "<path d=\"M0,-20 C-40,-60 -40,-120 0,-160 C30,-120 30,-60 0,-20 Z\" fill=\"#FFD3E6\"/></g>\n```";

        String part = FlowerComposer.cleanPart(FlowerComposer.OUTER_A, "#F48FBF", answer);

        assertTrue(part.startsWith("<g><clipPath id=\"clip-outerA\">"), part);
        assertFalse(part.contains("transform") || part.contains("onclick") || part.contains("opacity=\"0.2\"")
                || part.contains("#000") || part.contains("red") || part.contains("petal"), part);
        assertTrue(part.contains("fill=\"#F48FBF\""), "the silhouette has the colour of the plan: " + part);
        assertTrue(part.contains("clip-path=\"url(#clip-outerA)\""), part);
        assertTrue(part.endsWith("fill=\"none\" stroke=\"" + FlowerComposer.OUTLINE_COLOR
                + "\" stroke-width=\"12\" stroke-linejoin=\"round\"/></g>"), "the outline is drawn last: " + part);
    }

    @Test
    void whatIsNotAFlatGroupOfPathsIsUnusable() {
        FlowerComposer.Part petal = FlowerComposer.OUTER_A;
        String silhouette = "<path d=\"M0,0 C-85,-40 -80,-150 -20,-190 C90,-130 70,-40 0,0 Z\" fill=\"#F48FBF\"/>";
        for (String answer : List.of(
                "I would draw a petal like this.",
                "<g>" + silhouette,
                "<g>" + silhouette + "<circle cx=\"0\" cy=\"-100\" r=\"30\" fill=\"#fff\"/></g>",
                "<g>" + silhouette + "<g>" + silhouette + "</g></g>",
                "<g>" + silhouette + "<script>alert(1)</script></g>",
                "<g>" + silhouette + "<image href=\"http://example.org/x.png\"/></g>",
                "<g><path d=\"M0,0 url(#x)\" fill=\"#F48FBF\"/></g>",
                "<g></g>",
                "<!DOCTYPE g [<!ENTITY x \"y\">]><g>&x;" + silhouette + "</g>")) {
            assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.cleanPart(petal, "#F48FBF", answer), answer);
        }
    }

    @Test
    void aPartOutsideItsAreaOrFarTooSmallIsUnusable() throws Exception {
        // Shading that hangs out of the silhouette (as in one of the first real answers) is cut off, not refused.
        String cut = FlowerComposer.cleanPart(FlowerComposer.OUTER_B, "#F7A8CF",
                "<g><path d=\"M-30,0 C-70,-20 -80,-80 -40,-130 C20,-150 80,-80 30,0 Z\" fill=\"#F7A8CF\"/>"
                        + "<path d=\"M-30,0 C-50,10 -60,30 -40,90 C20,120 60,30 30,0 Z\" fill=\"#FF6666\"/></g>");
        assertTrue(cut.contains("clip-path"), cut);
        // A silhouette that grows downward instead of upward is out of place.
        FlowerComposer.Unusable reach = assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.cleanPart(
                FlowerComposer.OUTER_B, "#F7A8CF",
                "<g><path d=\"M-30,0 C-70,60 -80,120 0,150 C80,120 70,60 30,0 Z\" fill=\"#F7A8CF\"/></g>"));
        assertTrue(reach.getMessage().contains("allowed area"), reach.getMessage());
        FlowerComposer.Unusable tiny = assertThrows(FlowerComposer.Unusable.class, () -> FlowerComposer.cleanPart(
                FlowerComposer.OUTER_B, "#F7A8CF", "<g><path d=\"M0,0 L-5,-10 L5,-10 Z\" fill=\"#F7A8CF\"/></g>"));
        assertTrue(tiny.getMessage().contains("too small"), tiny.getMessage());
    }

    @Test
    void theAssembledBushIsADrawablePictureWithTransparentSurroundings() throws Exception {
        FlowerComposer.Plan plan = FlowerComposer.parsePlan(PLAN);
        Map<String, String> parts = new LinkedHashMap<>();
        for (FlowerComposer.Part part : FlowerComposer.PARTS) {
            parts.put(part.id(), FlowerComposer.cleanPart(part, plan.colors().get(part.id()), GOOD.get(part.id())));
        }

        String svg = FlowerComposer.assemble(plan, parts);
        BufferedImage image = SvgRasterizer.render(svg, 256, 256);

        double coverage = SvgRasterizer.coverage(image);
        assertTrue(coverage > 0.3 && coverage < 0.9, "coverage " + coverage);
        assertEquals(0, image.getRGB(2, 2) >>> 24, "the corner stays transparent");
        assertTrue((image.getRGB(128, 90) >>> 24) > 200, "the large blossom is there");
        assertEquals(8 + 9 + 14 + 1, svg.split("xlink:href=\"#(outerA|outerB|inner|center)\"", -1).length - 1,
                "one <use> per petal and centre, counts as planned");
    }

    @Test
    void generatesFromPlanAndPartsAndAsksAgainForAnUnusablePart(@TempDir Path directory) throws Exception {
        Map<String, AtomicInteger> asked = new ConcurrentHashMap<>();
        FlowerComposer.Model model = (system, user) -> {
            if (user.contains("\"layers\"")) {
                asked.computeIfAbsent("plan", key -> new AtomicInteger()).incrementAndGet();
                return PLAN;
            }
            FlowerComposer.Part part = partOf(user);
            int attempt = asked.computeIfAbsent(part.id(), key -> new AtomicInteger()).incrementAndGet();
            if (part == FlowerComposer.OUTER_A && attempt == 1) {
                return GOOD.get("outerA").replace("</g>", "<circle cx=\"0\" cy=\"-100\" r=\"30\" fill=\"#fff\"/></g>");
            }
            if (part == FlowerComposer.OUTER_A) {
                assertTrue(user.contains("previous answer was unusable") && user.contains("<circle>"), user);
            }
            return GOOD.get(part.id());
        };
        AssetStore store = new AssetStore(directory);

        assertNull(FlowerComposer.generate(model, store, AssetCatalog.PEONY_BUSH, "some/model"));

        assertEquals(1, asked.get("plan").get());
        assertEquals(2, asked.get("outerA").get(), "asked again once");
        assertEquals(1, asked.get("leaf").get());
        assertTrue(Files.isRegularFile(store.pngFile(AssetCatalog.PEONY_BUSH)));
        String info = Files.readString(store.infoFile(AssetCatalog.PEONY_BUSH));
        assertTrue(info.contains("\"recipe\": \"flower-bush\"") && info.contains("\"model\": \"some/model\"")
                && info.contains("\"shapeDescription\": \"broad wavy petal\""), info);
        assertTrue(Files.readString(store.svgFile(AssetCatalog.PEONY_BUSH)).contains("<g id=\"blossom\">"));
    }

    @Test
    void givesUpWithTheReasonWhenPlanOrPartStayUnusable(@TempDir Path directory) {
        AssetStore store = new AssetStore(directory);
        String noPlan = FlowerComposer.generate((system, user) -> "no idea", store, AssetCatalog.PEONY_BUSH, "m");
        assertTrue(noPlan.startsWith("the plan was unusable"), noPlan);

        String noLeaf = FlowerComposer.generate((system, user) -> user.contains("\"layers\"") ? PLAN
                        : partOf(user) == FlowerComposer.LEAF ? "<g><text>leaf</text></g>" : GOOD.get(partOf(user).id()),
                store, AssetCatalog.PEONY_BUSH, "m");
        assertTrue(noLeaf.startsWith("part leaf was unusable") && noLeaf.contains("<text>"), noLeaf);
        assertFalse(store.has(AssetCatalog.PEONY_BUSH));

        String offline = FlowerComposer.generate((system, user) -> {
            throw new java.io.IOException("cannot reach the service");
        }, store, AssetCatalog.PEONY_BUSH, "m");
        assertEquals("cannot reach the service", offline);
    }
}
