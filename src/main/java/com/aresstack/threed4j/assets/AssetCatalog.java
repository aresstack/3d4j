package com.aresstack.threed4j.assets;

import java.util.List;

/** The artwork the test plaza uses. Figures and trees are upright and twice as tall as wide. */
public final class AssetCatalog {

    public static final AssetSpec BLOSSOM_TREE = sprite("blossom-tree",
            "a cherry tree in full pink blossom: brown trunk, big round fluffy pink crown");
    public static final AssetSpec GREEN_TREE = sprite("green-tree",
            "a leafy green park tree: brown trunk, big round green crown");
    public static final AssetSpec LAMP = sprite("lamp",
            "an old-fashioned street lamp: slim dark iron post, warm glowing yellow lantern on top");
    public static final AssetSpec VISITOR_1 = sprite("visitor-1",
            "a friendly park visitor standing upright, seen from the front, purple hoodie, red-brown hair");
    public static final AssetSpec VISITOR_2 = sprite("visitor-2",
            "a friendly park visitor standing upright, seen from the front, blue jacket, dark hair");
    public static final AssetSpec VISITOR_3 = sprite("visitor-3",
            "a friendly park visitor standing upright, seen from the front, yellow coat, blond hair");

    /** Not drawn in one piece: assembled from a plan and parts (petals, centre, leaf). */
    public static final AssetSpec PEONY_BUSH = new AssetSpec("peony-bush", 256, 256,
            "a bush of pink peonies", AssetSpec.Recipe.FLOWER_BUSH);

    public static final List<AssetSpec> ALL =
            List.of(BLOSSOM_TREE, GREEN_TREE, LAMP, VISITOR_1, VISITOR_2, VISITOR_3, PEONY_BUSH);

    private AssetCatalog() {
    }

    private static AssetSpec sprite(String id, String description) {
        return new AssetSpec(id, 256, 512, description);
    }
}
