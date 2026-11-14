package com.aresstack.threed4j.assets;

/**
 * A part of the town with a look of its own. Every district has its own
 * collection of artwork ({@link PartLibrary}); the style is handed to the model
 * with every request, so that what belongs to one district goes together and
 * differs from another district.
 *
 * @param id    name of the district's folder in the library; letters, digits and hyphens
 * @param name  what the player reads
 * @param style the look all parts share, in English, as handed to the model
 */
public record District(String id, String name, String style) {

    /** The district around the plaza where the game starts. So far the only one. */
    public static final District BLOSSOM_QUARTER = new District("bluetenviertel", "Blütenviertel",
            "a cheerful old town in cherry blossom season: frames in cream white and warm wood, shutters and doors "
                    + "in soft pink, mint green or sky blue, rounded friendly shapes, flowers wherever they fit");

    /**
     * The district a place of the world belongs to. With more districts, this is
     * where the borders are drawn.
     */
    public static District at(float x, float z) {
        return BLOSSOM_QUARTER;
    }
}
