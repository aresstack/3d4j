package com.aresstack.threed4j.assets;

import com.aresstack.threed4j.assets.PartLibrary.Kind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A part of the town with a look of its own. Every district has its own
 * collection of artwork ({@link PartLibrary}); the style is handed to the model
 * with every request, so that what belongs to one district goes together and
 * differs from another district.
 *
 * <p>All districts collect the same kinds, but a kind means something else in
 * each: the first tree of the Blütenviertel is a cherry tree, that of the
 * Werksviertel a birch; where the one has shop fronts, the other has gates. What
 * a kind is in a district is its {@link Motif}.</p>
 *
 * @param id     name of the district's folder in the library; letters, digits and hyphens
 * @param name   what the player reads
 * @param style  the look all parts share, in English, as handed to the model
 * @param motifs what each kind is in this district
 */
public record District(String id, String name, String style, Map<Kind, Motif> motifs) {

    /**
     * What a kind is in a district.
     *
     * @param name       German, one of it, e.g. "Kirschbaum"
     * @param plural     German, several of it, e.g. "Kirschbäume"
     * @param variations what to draw, in English; the number of a picture picks one, in turn
     */
    public record Motif(String name, String plural, List<String> variations) {
    }

    /** A district with this look and the motifs of the Blütenviertel. */
    public District(String id, String name, String style) {
        this(id, name, style, blossomMotifs());
    }

    public Motif motif(Kind kind) {
        return motifs.get(kind);
    }

    /** Whether the bushes of this district are flowers put together from a plan and parts, not one drawing. */
    public boolean flowerBushes() {
        return motifs.get(Kind.BUSH).name().equals("Pfingstrosenbusch");
    }

    private static Map<Kind, Motif> blossomMotifs() {
        Map<Kind, Motif> motifs = new EnumMap<>(Kind.class);
        motifs.put(Kind.BLOSSOM_TREE, new Motif("Kirschbaum", "Kirschbäume", List.of(
                "a cherry tree in full pink blossom: brown trunk, big round fluffy pink crown",
                "a tall slender cherry tree in pink blossom: long trunk, crown of three fluffy pink clouds",
                "an old cherry tree in blossom: thick bent trunk, wide flat pink crown",
                "a young cherry tree in blossom: thin trunk, small light pink crown with darker pink dots",
                "a cherry tree in white and pale pink blossom: forked trunk, two round crowns side by side",
                "a weeping cherry tree: brown trunk, long hanging branches full of pink blossom")));
        motifs.put(Kind.GREEN_TREE, new Motif("Parkbaum", "Parkbäume", List.of(
                "a leafy green park tree: brown trunk, big round green crown",
                "a tall poplar: straight trunk, narrow pointed green crown",
                "a broad old oak: thick trunk, wide crown of several green clouds",
                "a small round ball-shaped tree, trimmed like in a formal garden",
                "a birch: thin white trunk with dark marks, light green airy crown")));
        motifs.put(Kind.LAMP, new Motif("Laterne", "Laternen", List.of(
                "an old-fashioned street lamp: slim dark iron post, warm glowing yellow lantern on top",
                "a street lamp with two lanterns on curved arms, dark green post",
                "a street lamp with a round white glass globe on a slim post with a decorated foot",
                "a street lamp with a hanging lantern on a curved swan-neck post")));
        motifs.put(Kind.VISITOR, visitors());
        motifs.put(Kind.BUSH, new Motif("Pfingstrosenbusch", "Pfingstrosen", List.of("a bush of pink peonies")));
        motifs.put(Kind.LAWN, new Motif("Rasen", "Rasen", List.of(
                "a mown lawn: base colour #A2D08C, small tufts of grass in slightly darker and lighter greens, a few "
                        + "tiny white and pink flowers",
                "a meadow: base colour #A2D08C, short blades of grass in slightly different greens, some clover leaves",
                "a lawn with fallen cherry blossom: base colour #A2D08C, small tufts of grass, scattered pale pink "
                        + "petals")));
        motifs.put(Kind.STREET, new Motif("Straßenpflaster", "Pflaster", List.of(
                "the paving of a town street: base colour #D2CEDB, flat paving stones of different sizes in slightly "
                        + "lighter and darker shades of the base colour, a few short cracks",
                "cobblestones: base colour #D2CEDB, small rounded stones in slightly lighter and darker shades of the "
                        + "base colour",
                "large stone slabs: base colour #D2CEDB, a few big rectangular slabs in slightly different shades of "
                        + "the base colour, thin gaps between them")));
        motifs.put(Kind.WINDOW, new Motif("Fenster", "Fenster", List.of(
                "a tall window with two casements and a cross bar",
                "a window with a round arch on top",
                "a window with open wooden shutters on both sides",
                "a window with a flower box full of blossoms below it",
                "a small round window with a thick frame",
                "a wide window with three casements",
                "a window with a little striped awning above it",
                "a window with a balcony railing in front of its lower half")));
        motifs.put(Kind.DOOR, new Motif("Tür", "Türen", List.of(
                "a wooden door with a round arch and a small window in it",
                "a double door with two panels and brass handles",
                "a door with a glass pane and a small canopy above it",
                "a door with a fanlight above it and a step below")));
        motifs.put(Kind.SHOP, new Motif("Laden", "Läden", List.of(
                "a wide shop window with a striped awning above it",
                "a shop window divided into small panes, with a sign board above it",
                "a cafe window with a counter sill and a scalloped awning",
                "a shop window with an arched top and flower pots below")));
        return motifs;
    }

    /** The people of the town; they are the same in every district. */
    private static Motif visitors() {
        return new Motif("Besucher", "Besucher", List.of(
                "purple hoodie, red-brown hair", "blue jacket, dark hair", "yellow raincoat, blond hair",
                "green dress, black hair in a bun", "red cardigan, grey hair and glasses",
                "orange t-shirt, curly brown hair", "white shirt and dark tie, short black hair",
                "pink coat, long light brown hair", "striped sweater, a cap on the head",
                "light blue overalls, a ponytail", "brown coat and a scarf, a hat",
                "turquoise jacket, a backpack, short red hair"));
    }

    private static Map<Kind, Motif> worksMotifs() {
        Map<Kind, Motif> motifs = new EnumMap<>(Kind.class);
        motifs.put(Kind.BLOSSOM_TREE, new Motif("Birke", "Birken", List.of(
                "a birch: slim white trunk with dark marks, light green airy crown",
                "a young birch with two thin white trunks and small light green crowns",
                "a tall old birch: white trunk with black marks, hanging light green branches",
                "a crooked birch that leans to one side, sparse yellow-green crown")));
        motifs.put(Kind.GREEN_TREE, new Motif("Baum", "Bäume", List.of(
                "a tall poplar: straight grey trunk, narrow pointed dark green crown",
                "a scruffy maple that grew wild: forked trunk, uneven green crown",
                "a pine: reddish trunk, a few flat dark green clouds of needles high up",
                "a robinia: dark furrowed trunk, loose crown of small light green leaves")));
        motifs.put(Kind.LAMP, new Motif("Werkslaterne", "Werkslaternen", List.of(
                "an industrial yard lamp: tall plain steel mast, a flat enamel shade on a short arm, warm yellow light",
                "a factory lamp: riveted iron mast, a round caged lamp on top glowing warm yellow",
                "a yard lamp: grey steel mast with a curved arm and a bell-shaped green enamel shade",
                "a modern slim dark mast with a flat rectangular lamp head")));
        motifs.put(Kind.VISITOR, visitors());
        motifs.put(Kind.BUSH, new Motif("Wildwuchs", "Wildwuchs", List.of(
                "tall fireweed growing wild: several upright green stems with spikes of pink-purple flowers",
                "a butterfly bush growing wild: arching green branches with long purple flower cones",
                "a clump of tall dry grass and thistles with purple heads",
                "a wild elder shrub: green leaves, flat plates of small white flowers")));
        motifs.put(Kind.LAWN, new Motif("Schotter", "Schotter", List.of(
                "gravel: base colour #BDB7B0, many small pebbles in slightly lighter and darker greys and beiges",
                "a gravel yard with weeds: base colour #BDB7B0, small pebbles in greys, a few tiny tufts of dull "
                        + "green grass",
                "old ballast: base colour #BDB7B0, angular small stones in slightly different greys, a few rust-brown "
                        + "ones")));
        motifs.put(Kind.STREET, new Motif("Werkspflaster", "Werkspflaster", List.of(
                "old cobblestones of a factory yard: base colour #A8A3AD, small rounded stones in slightly lighter "
                        + "and darker shades of the base colour",
                "concrete slabs of a factory yard: base colour #A8A3AD, a few big rectangular slabs in slightly "
                        + "different shades of the base colour, thin gaps and short cracks",
                "brick paving: base colour #A8A3AD, small rectangular pavers laid in rows, in slightly lighter and "
                        + "darker shades of the base colour")));
        motifs.put(Kind.WINDOW, new Motif("Werksfenster", "Werksfenster", List.of(
                "a tall factory window with a dark steel frame divided into many small panes",
                "a factory window with a segmental brick arch on top and a grid of small panes",
                "a wide low workshop window: a band of six panes in a dark steel frame",
                "a round-arched window with iron bars in front of the glass",
                "a small ventilation window with slanted metal louvres",
                "a modern office window: one large pane in a slim dark frame with a narrow opening wing",
                "a factory window where one pane is tilted open",
                "a window with a rusty roller shutter pulled half down")));
        motifs.put(Kind.DOOR, new Motif("Werkstür", "Werkstüren", List.of(
                "a heavy steel door painted weathered green, with rivets and a small wired-glass pane",
                "a double metal door in rust red with two small windows and a lamp above it",
                "a wooden workshop door with iron fittings under a brick arch",
                "a modern glass door in a dark steel frame with a flat canopy above it")));
        motifs.put(Kind.SHOP, new Motif("Tor", "Tore", List.of(
                "a wide roller shutter gate of grey metal slats in a dark steel frame",
                "a sliding wooden workshop gate hanging from an iron rail, with a small door in it",
                "a loading gate with yellow and black warning stripes along its bottom edge and a lamp above it",
                "a glazed workshop gate: a wide folding gate of many small panes in dark steel")));
        return motifs;
    }

    /** The district around the plaza where the game starts. */
    public static final District BLOSSOM_QUARTER = new District("bluetenviertel", "Blütenviertel",
            "a cheerful old town in cherry blossom season: frames in cream white and warm wood, shutters and doors "
                    + "in soft pink, mint green or sky blue, rounded friendly shapes, flowers wherever they fit",
            blossomMotifs());

    /**
     * The old industrial quarter at the far end of the street, around the WEST.SIDE
     * office ensemble: works that are turning into offices.
     */
    public static final District WORKS_QUARTER = new District("werksviertel", "Werksviertel",
            "a former industrial quarter that is turning into offices: red and brown brick, dark steel frames, "
                    + "riveted iron, weathered green and rust-red paint, grey concrete; plain sturdy shapes, still "
                    + "friendly and cartoon-like, here and there a weed",
            worksMotifs());

    /** All districts of the town, in the order the game shows them. */
    public static final List<District> ALL = List.of(BLOSSOM_QUARTER, WORKS_QUARTER);

    /** South of this line (smaller z) the Werksviertel begins; it lies on an edge of the ground's squares. */
    public static final float WORKS_BORDER_Z = -100f;

    /** The district a place of the world belongs to: here the borders are drawn. */
    public static District at(float x, float z) {
        return z < WORKS_BORDER_Z ? WORKS_QUARTER : BLOSSOM_QUARTER;
    }
}
