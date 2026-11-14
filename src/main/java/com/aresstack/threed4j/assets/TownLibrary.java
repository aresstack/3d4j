package com.aresstack.threed4j.assets;

import com.aresstack.threed4j.assets.PartLibrary.Kind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The town as it is kept on disk: its districts, each with a collection of
 * artwork, and one collection for what is the same everywhere. The people of the
 * town belong to no district; they live in the shared collection, the folder
 * {@code library/stadt}.
 *
 * <p>Two districts come with the game. Every further one was planned by a model
 * and is a folder {@code library/<id>} with a {@link DistrictFile}; the town is
 * read from the folders when this object is made, so make a new one to see what
 * has changed. A district that is torn down moves to the folder
 * {@code demolished} with all its pictures.</p>
 */
public final class TownLibrary {

    /** Name of the shared collection's folder. */
    public static final String SHARED = "stadt";
    /** What the player reads for the shared collection. */
    public static final String SHARED_NAME = "Ganze Stadt";

    private final AssetStore store;
    private final Map<District, PartLibrary> districts = new LinkedHashMap<>();
    private final PartLibrary shared;

    public TownLibrary(AssetStore store) {
        this.store = store;
        for (District district : District.ALL) {
            districts.put(district, new PartLibrary(store, district.id()));
        }
        List<District> planned = new ArrayList<>();
        Path library = store.directory().resolve("library");
        if (Files.isDirectory(library)) {
            try (Stream<Path> folders = Files.list(library)) {
                for (Path folder : (Iterable<Path>) folders.sorted()::iterator) {
                    Path file = folder.resolve(DistrictFile.NAME);
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    try {
                        District district = DistrictFile.read(file);
                        boolean usable = district.id().equals(folder.getFileName().toString())
                                && !District.plotTaken(district.layout().plotX(), district.layout().plotZ())
                                && planned.stream().noneMatch(other -> samePlot(other, district));
                        if (usable) {
                            planned.add(district);
                        } else {
                            System.out.println("Town: " + file + " is skipped: its name or its plot is taken");
                        }
                    } catch (IOException e) {
                        System.out.println("Town: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                // an unreadable library is treated like one without planned districts
            }
        }
        // In the order they were planned, so that the youngest is the last.
        planned.sort(java.util.Comparator.comparing((District district) -> district.layout().plannedAt())
                .thenComparing(District::id));
        for (District district : planned) {
            districts.put(district, new PartLibrary(store, district.id()));
        }
        shared = new PartLibrary(store, SHARED);
    }

    private static boolean samePlot(District a, District b) {
        return a.layout().plotX() == b.layout().plotX() && a.layout().plotZ() == b.layout().plotZ();
    }

    /** The town as it is on disk now. */
    public TownLibrary reload() {
        return new TownLibrary(store);
    }

    /** All districts: the two that come with the game, then the planned ones, oldest first. */
    public List<District> districts() {
        return List.copyOf(districts.keySet());
    }

    /** The planned districts, oldest first. */
    public List<District> planned() {
        return districts.keySet().stream().filter(district -> !district.builtIn()).toList();
    }

    /** @return the district with this id, or {@code null} */
    public District district(String id) {
        return districts.keySet().stream().filter(district -> district.id().equals(id)).findFirst().orElse(null);
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

    /**
     * The district a place of the world belongs to.
     *
     * @return the district, or {@code null} where the town has not grown yet
     */
    public District districtAt(float x, float z) {
        int plotX = Math.round(x / District.PLOT);
        int plotZ = Math.round(z / District.PLOT);
        if (District.plotTaken(plotX, plotZ)) {
            return District.at(x, z);
        }
        for (District district : districts.keySet()) {
            if (!district.builtIn() && district.layout().plotX() == plotX && district.layout().plotZ() == plotZ) {
                return district;
            }
        }
        return null;
    }

    /** @return the next plot the town grows into, or {@code null} if all plots are taken */
    public int[] freePlot() {
        for (int[] plot : District.plotOrder()) {
            boolean taken = false;
            for (District district : districts.keySet()) {
                taken |= !district.builtIn() && district.layout().plotX() == plot[0] && district.layout().plotZ() == plot[1];
            }
            if (!taken) {
                return plot;
            }
        }
        return null;
    }

    /** Whether a district has all the pictures it is to collect. */
    public boolean complete(District district) {
        PartLibrary library = districts.get(district);
        for (Kind kind : districtKinds()) {
            if (library.parts(kind).size() < library.target(kind)) {
                return false;
            }
        }
        return true;
    }

    /** How many pictures a district has of those it is to collect, and how many it is to collect. */
    public int[] progress(District district) {
        PartLibrary library = districts.get(district);
        int have = 0;
        int want = 0;
        for (Kind kind : districtKinds()) {
            int target = library.target(kind);
            have += Math.min(library.parts(kind).size(), target);
            want += target;
        }
        return new int[] {have, want};
    }

    /** Changes whenever a picture is added, removed or chosen in the district, or its plan is written anew. */
    public String signature(District district) {
        PartLibrary library = districts.get(district);
        String plan = "";
        if (!district.builtIn()) {
            try {
                plan = String.valueOf(Files.getLastModifiedTime(library.directory().resolve(DistrictFile.NAME)).toMillis());
            } catch (IOException e) {
                plan = "gone";
            }
        }
        return district.id() + ":" + plan + ":" + library.signature();
    }

    /** Changes whenever anything changes anywhere in the town. */
    public String signature() {
        StringBuilder signature = new StringBuilder();
        for (District district : districts.keySet()) {
            signature.append(district.builtIn() ? districts.get(district).signature() : signature(district)).append('|');
        }
        return signature.append(shared.signature()).toString();
    }

    /** Whether the town has no picture at all yet. */
    public boolean empty() {
        if (!shared.parts().isEmpty()) {
            return false;
        }
        for (PartLibrary library : districts.values()) {
            if (!library.parts().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------- planned districts

    /** Adds a planned district to the town, or writes its plan anew (e.g. with another landmark). */
    public void save(District district) throws IOException {
        if (district.builtIn()) {
            throw new IllegalArgumentException(district.name() + " comes with the game");
        }
        DistrictFile.write(store.directory().resolve("library").resolve(district.id()).resolve(DistrictFile.NAME), district);
    }

    /**
     * Tears a planned district down: its folder with the plan and all pictures moves
     * to {@code demolished}, and its plot is free again.
     */
    public void demolish(District district) throws IOException {
        if (district.builtIn()) {
            throw new IllegalArgumentException(district.name() + " comes with the game");
        }
        Path folder = store.directory().resolve("library").resolve(district.id());
        Path demolished = store.directory().resolve("demolished");
        Files.createDirectories(demolished);
        Files.move(folder, demolished.resolve(district.id() + "-" + System.currentTimeMillis()));
    }

    // ------------------------------------------------- what earlier versions kept

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
