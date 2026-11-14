package com.aresstack.threed4j.assets;

import com.aresstack.threed4j.assets.PartLibrary.Kind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * All collections of the town: one per district, and one for what is the same
 * everywhere. The people of the town belong to no district; they live in the
 * shared collection, the folder {@code library/stadt}.
 */
public final class TownLibrary {

    /** Name of the shared collection's folder. */
    public static final String SHARED = "stadt";
    /** What the player reads for the shared collection. */
    public static final String SHARED_NAME = "Ganze Stadt";

    private final Map<District, PartLibrary> districts = new LinkedHashMap<>();
    private final PartLibrary shared;

    public TownLibrary(AssetStore store) {
        for (District district : District.ALL) {
            districts.put(district, new PartLibrary(store, district.id()));
        }
        shared = new PartLibrary(store, SHARED);
    }

    public PartLibrary district(District district) {
        return districts.get(district);
    }

    public PartLibrary shared() {
        return shared;
    }

    /** The collection that holds the pictures of this kind for this district. */
    public PartLibrary of(District district, Kind kind) {
        return kind.shared() ? shared : districts.get(district);
    }

    /** The kinds a district collects itself: all but the shared ones. */
    public static List<Kind> districtKinds() {
        return Stream.of(Kind.values()).filter(kind -> !kind.shared()).toList();
    }

    public static List<Kind> sharedKinds() {
        return Stream.of(Kind.values()).filter(Kind::shared).toList();
    }

    /** Changes whenever a picture is added, removed or chosen anywhere in the town. */
    public String signature() {
        StringBuilder signature = new StringBuilder();
        for (PartLibrary library : districts.values()) {
            signature.append(library.signature()).append('|');
        }
        return signature.append(shared.signature()).toString();
    }

    /**
     * Moves what earlier versions kept per district into the shared collection: the
     * people, with the limit set for them. Pictures keep their numbers; one whose
     * number is taken in the shared collection stays where it is.
     *
     * @return how many pictures moved
     */
    public int gatherSharedKinds() {
        int moved = 0;
        for (PartLibrary library : districts.values()) {
            for (Kind kind : sharedKinds()) {
                List<PartLibrary.Part> parts = library.parts(kind);
                if (parts.isEmpty()) {
                    continue;
                }
                try {
                    Files.createDirectories(shared.directory());
                    if (shared.parts(kind).isEmpty() && library.hasOwnTarget(kind)) {
                        shared.setTarget(kind, library.target(kind));
                    }
                    for (PartLibrary.Part part : parts) {
                        String base = part.png().getFileName().toString().replaceFirst("\\.png$", "");
                        if (Files.exists(shared.directory().resolve(base + ".png"))) {
                            continue;
                        }
                        // The picture last: the other files are only looked for beside a picture.
                        for (String extension : new String[] {".svg", ".json", ".png"}) {
                            Path file = part.png().resolveSibling(base + extension);
                            if (Files.isRegularFile(file)) {
                                Files.move(file, shared.directory().resolve(base + extension));
                            }
                        }
                        moved++;
                    }
                } catch (IOException e) {
                    System.out.println("Library: " + kind.fileName() + " of " + library.directory()
                            + " could not be moved (" + e.getMessage() + ")");
                }
            }
        }
        return moved;
    }
}
