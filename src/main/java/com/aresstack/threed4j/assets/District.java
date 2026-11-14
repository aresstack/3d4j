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
 * <p>The two districts the game starts with are built by hand in the game. Every
 * further one is planned by a model, top down: theme, motifs, buildings, landmark;
 * its {@link Layout} holds what the game needs to build it.</p>
 *
 * @param id     name of the district's folder in the library; letters, digits and hyphens
 * @param name   what the player reads
 * @param style  the look all parts share, in English, as handed to the model
 * @param motifs what each kind is in this district
 * @param layout where a planned district lies and how it is built; {@code null} for the two built by hand
 */
public record District(String id, String name, String style, Map<Kind, Motif> motifs, Layout layout) {

    /**
     * What a kind is in a district.
     *
     * @param name       German, one of it, e.g. "Kirschbaum"
     * @param plural     German, several of it, e.g. "Kirschbäume"
     * @param variations what to draw, in English; the number of a picture picks one, in turn
     */
    public record Motif(String name, String plural, List<String> variations) {
    }

    /**
     * How a planned district is built. Colours are RGB numbers, lengths in metres.
     *
     * @param plotX      the district's place in the grid of plots, see {@link #PLOT}; the Blütenviertel is plot 0, 0
     * @param walls      colours of house walls, at least one
     * @param roofs      colours of roofs, at least one
     * @param lawn       colour of the ground beside the streets, {@code street} of the streets, {@code plaza} of the square
     * @param minHeight  lowest and {@code maxHeight} highest house
     * @param minHalf    half the width of the narrowest and {@code maxHalf} of the widest house
     * @param spacing    distance between the middles of neighboring houses
     * @param roofShapes which roofs occur: {@code flat}, {@code pyramid}, {@code sawtooth}, {@code attic}
     * @param landmark   what stands in the middle of the district; {@code null} while there is none
     * @param plannedBy  the model that planned the district
     * @param plannedAt  when, as ISO-8601
     */
    public record Layout(int plotX, int plotZ, List<Integer> walls, List<Integer> roofs, int lawn, int street, int plaza,
                         float minHeight, float maxHeight, float minHalf, float maxHalf, float spacing,
                         List<String> roofShapes, Landmark landmark, String plannedBy, String plannedAt) {

        public Layout withLandmark(Landmark replacement) {
            return new Layout(plotX, plotZ, walls, roofs, lawn, street, plaza, minHeight, maxHeight, minHalf, maxHalf,
                    spacing, roofShapes, replacement, plannedBy, plannedAt);
        }
    }

    /**
     * A landmark as an architect describes it: what it is, and the components it is
     * put together from, bottom to top.
     *
     * @param name German, e.g. "Funkturm"
     * @param idea one sentence in English on what it is and looks like
     */
    public record Landmark(String name, String idea, List<Component> components) {

        /** How high the landmark reaches. */
        public float height() {
            float height = 0f;
            for (Component component : components) {
                height = Math.max(height, component.base() + component.height() + component.roofHeight());
            }
            return height;
        }

        /** How far from its middle the landmark stands on the ground. */
        public float footRadius() {
            float radius = 0.5f;
            for (Component component : components) {
                if (component.base() < 2.5f) {
                    radius = Math.max(radius, (float) Math.hypot(component.x(), component.z())
                            + (component.shape() == Shape.LATHE ? component.width() : (float) Math.hypot(component.width(), component.depth())));
                }
            }
            return radius;
        }
    }

    /** How a component gets its body from a flat drawing. */
    public enum Shape {
        /** A side view of half the outline, turned around the upright axis: towers, tanks, domes, bowls. */
        LATHE,
        /** An outline seen from above, pulled up to a height: walls, wings, slabs. */
        EXTRUDE
    }

    /**
     * One component of a landmark.
     *
     * @param name       what it is, in English, e.g. "observation deck"
     * @param x          where its middle stands relative to the landmark's middle, with {@code z}
     * @param base       height of its lowest point above the ground
     * @param height     how high it is
     * @param width      turned: its greatest radius; pulled up: half its extent along x
     * @param depth      pulled up: half its extent along z; unused for a turned component
     * @param roofHeight pulled up: height of a pointed roof on top, 0 for a flat top
     * @param color      RGB of its sides, {@code topColor} of faces that look up
     * @param outline    turned: points (radius, height) from bottom to top, both 0 to 1 as parts of {@code width}
     *                   and {@code height}; pulled up: the corners (x, z) seen from above, -1 to 1 as parts of
     *                   {@code width} and {@code depth}
     */
    public record Component(String name, Shape shape, float x, float z, float base, float height, float width,
                            float depth, float roofHeight, int color, int topColor, List<float[]> outline) {
    }

    /** Districts are the same if they have the same id; a plan written anew is still the same district. */
    @Override
    public boolean equals(Object other) {
        return other instanceof District district && district.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Edge length of a plot: every district is a square of this size; plot i, j is centered at i * PLOT, j * PLOT. */
    public static final float PLOT = 200f;

    /** The middle of a planned district's plot. */
    public float centerX() {
        return layout.plotX() * PLOT;
    }

    public float centerZ() {
        return layout.plotZ() * PLOT;
    }

    /** Whether the district is one of the two the game brings along, as opposed to planned by a model. */
    public boolean builtIn() {
        return layout == null;
    }

    public District(String id, String name, String style, Map<Kind, Motif> motifs) {
        this(id, name, style, motifs, null);
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

    /** What a kind that belongs to the whole town is: the same in every district. */
    public static Motif sharedMotif(Kind kind) {
        return kind == Kind.SEA_ANIMAL ? seaAnimals() : visitors();
    }

    /** The animals of a district whose plan names none: what walks the streets of any town. */
    public static Motif townAnimals() {
        return new Motif("Tier", "Tiere", List.of(
                "a ginger cat sitting upright, tail curled around its paws",
                "a small white dog with a red collar, sitting, tongue out",
                "a grey pigeon standing, seen from the front",
                "a red squirrel sitting upright with a nut in its paws, bushy tail",
                "a black cat standing, tail straight up",
                "a brown rabbit sitting upright, long ears"));
    }

    /** What lives in the sea along the town's coast. */
    private static Motif seaAnimals() {
        return new Motif("Meerestier", "Meerestiere", List.of(
                "a dolphin leaping out of the water, grey-blue with a light belly",
                "a seal looking out of the water, head and shoulders, dark eyes and whiskers",
                "a white swan swimming, long curved neck, orange beak",
                "a small blue whale with a fountain of water spouting from its back",
                "a sea turtle swimming at the surface, green shell, head raised",
                "a red octopus with its head above the water and two arms waving",
                "a seagull sitting on a wooden post that stands in the water",
                "a yellow duck swimming",
                "an orange crab on a small rock in the water, claws raised",
                "a pelican swimming, big yellow beak",
                "a little sailing boat with a striped sail, no people in it",
                "a red and white buoy with a small lamp on top"));
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
        motifs.put(Kind.SEA_ANIMAL, seaAnimals());
        motifs.put(Kind.ANIMAL, townAnimals());
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
        motifs.put(Kind.SEA_ANIMAL, seaAnimals());
        motifs.put(Kind.ANIMAL, new Motif("Tier", "Tiere", List.of(
                "a scruffy grey stray cat sitting upright, one ear bent",
                "a black crow standing, seen from the front",
                "a fox sitting upright, bushy tail, curious look",
                "a big brown guard dog sitting, leather collar",
                "a grey pigeon standing, seen from the front",
                "a hedgehog standing on its hind legs")));
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

    /** The districts the game brings along; planned ones are added by {@link TownLibrary}. */
    public static final List<District> ALL = List.of(BLOSSOM_QUARTER, WORKS_QUARTER);

    /**
     * The coast: east of this line lies the sea. It runs north to south in a wave,
     * so that in the middle of every plot of the first column to the east a headland
     * reaches out, with a bay on either side; the Wellenviertel's lighthouse stands
     * on the tip of the first.
     *
     * @return the x of the shore at this z
     */
    public static float coastX(float z) {
        return 170f + 30f * (float) Math.cos(2 * Math.PI * z / PLOT);
    }

    /** Whether a place is on land, at least this far from the water. */
    public static boolean onLand(float x, float z, float distance) {
        return x < coastX(z) - distance;
    }

    /** Whether a plot lies at the sea: part of it is water, and a promenade runs along its shore. */
    public static boolean coastal(int plotX) {
        return plotX == 1;
    }

    /** The plots of the districts the game brings along: plot 0, 0 and the one south of it. */
    public static boolean plotTaken(int plotX, int plotZ) {
        return plotX == 0 && (plotZ == 0 || plotZ == -1);
    }

    /**
     * The plots in the order the town grows into them: around the two districts
     * the game starts with, the nearest first.
     */
    public static List<int[]> plotOrder() {
        List<int[]> plots = new java.util.ArrayList<>();
        // East of the first column there is only water, west of the first column only mountains.
        for (int x = -1; x <= 1; x++) {
            for (int z = -3; z <= 3; z++) {
                if (!plotTaken(x, z)) {
                    plots.add(new int[] {x, z});
                }
            }
        }
        // Nearest to the start first; of equal distance east before west, the plaza's row before the others.
        plots.sort(java.util.Comparator.<int[]>comparingDouble(p -> Math.hypot(p[0], p[1] + 0.25))
                .thenComparingInt(p -> -p[0]));
        return plots;
    }

    /** South of this line (smaller z) the Werksviertel begins; it lies on an edge of the ground's squares. */
    public static final float WORKS_BORDER_Z = -100f;

    /** Which of the two districts the game brings along a place belongs to: here their border is drawn. */
    public static District at(float x, float z) {
        return z < WORKS_BORDER_Z ? WORKS_QUARTER : BLOSSOM_QUARTER;
    }
}
