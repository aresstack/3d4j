package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;

/**
 * Keeps drawing pictures for a district while the game runs: one at a time, on a
 * thread of its own, until every kind in the district's {@link PartLibrary} has
 * reached its limit. When pictures are removed or a limit is raised, it goes on.
 *
 * <p>It needs access to the AI service. It uses the access chosen in the world
 * menu during this run; otherwise it fetches the remembered one without asking
 * (KeePass entry of the last run, or the environment variable). Without access,
 * or switched off in the settings, it waits and tries again later; the game does
 * not depend on it.</p>
 */
public final class BackgroundArtist implements AutoCloseable {

    private final PartLibrary library;
    private final String districtStyle;
    private final Thread thread;
    private volatile boolean closed;
    private volatile String status = "wartet";

    private BackgroundArtist(PartLibrary library, String districtStyle) {
        this.library = library;
        this.districtStyle = districtStyle;
        this.thread = new Thread(this::work, "3d4j-background-artist");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.MIN_PRIORITY); // the game comes first
    }

    /**
     * @param district      name of the district's folder in the library
     * @param districtStyle the look all parts of the district share, in English, as handed to the model
     */
    public static BackgroundArtist start(AssetStore store, String district, String districtStyle) {
        BackgroundArtist artist = new BackgroundArtist(new PartLibrary(store, district), districtStyle);
        artist.thread.start();
        return artist;
    }

    /** What it is doing, in German, for the settings: e.g. "zeichnet Fenster 3". */
    public String status() {
        return status;
    }

    /** How far the collection is, e.g. "34 von 60 Bildern". */
    public String progress() {
        int have = 0;
        int want = 0;
        for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
            int target = library.target(kind);
            have += Math.min(library.parts(kind).size(), target);
            want += target;
        }
        return have + " von " + want + " Bildern";
    }

    /** What a kind is called in German, one of it. */
    public static String name(PartLibrary.Kind kind) {
        return switch (kind) {
            case BLOSSOM_TREE -> "Kirschbaum";
            case GREEN_TREE -> "Parkbaum";
            case LAMP -> "Laterne";
            case VISITOR -> "Besucher";
            case BUSH -> "Pfingstrosenbusch";
            case LAWN -> "Rasen";
            case STREET -> "Straßenpflaster";
            case WINDOW -> "Fenster";
            case DOOR -> "Tür";
            case SHOP -> "Laden";
        };
    }

    /** What a kind is called in German, several of it. */
    public static String plural(PartLibrary.Kind kind) {
        return switch (kind) {
            case BLOSSOM_TREE -> "Kirschbäume";
            case GREEN_TREE -> "Parkbäume";
            case LAMP -> "Laternen";
            case VISITOR -> "Besucher";
            case BUSH -> "Pfingstrosen";
            case LAWN -> "Rasen";
            case STREET -> "Pflaster";
            case WINDOW -> "Fenster";
            case DOOR -> "Türen";
            case SHOP -> "Läden";
        };
    }

    /**
     * The kind to draw next: the one that is furthest from its limit, measured in
     * parts of the limit, so that every kind gets its first pictures early.
     *
     * @return the kind, or {@code null} if every kind has reached its limit
     */
    static PartLibrary.Kind next(PartLibrary library) {
        PartLibrary.Kind choice = null;
        double least = 1.0;
        for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
            int target = library.target(kind);
            double filled = target == 0 ? 1.0 : library.parts(kind).size() / (double) target;
            if (filled < least) {
                least = filled;
                choice = kind;
            }
        }
        return choice;
    }

    private void work() {
        long retryAccessAt = 0;
        try {
            Thread.sleep(4000); // let the game start first
            while (!closed) {
                if (!AiSetup.backgroundEnabled()) {
                    status = "ausgeschaltet";
                    Thread.sleep(2000);
                    continue;
                }
                PartLibrary.Kind kind = next(library);
                if (kind == null) {
                    status = "fertig: die Sammlung ist vollständig";
                    Thread.sleep(10_000);
                    continue;
                }
                AiSetup.Session session = AiSetup.session();
                if (session == null && System.currentTimeMillis() >= retryAccessAt) {
                    status = "sucht den Zugang zur KI";
                    session = AiSetup.resumeSession();
                    retryAccessAt = System.currentTimeMillis() + 60_000;
                }
                if (session == null) {
                    status = "wartet auf den Zugang zur KI";
                    Thread.sleep(2000);
                    continue;
                }
                int number = library.parts(kind).stream().mapToInt(PartLibrary.Part::number).max().orElse(0) + 1;
                status = "zeichnet " + name(kind) + " " + number;
                String problem = session.draw(library, kind, number, districtStyle);
                if (problem == null) {
                    status = name(kind) + " " + number + " ist fertig";
                    Thread.sleep(3000);
                } else {
                    // Not again at once: a service that refuses or a model that fails should not be hammered.
                    status = name(kind) + " " + number + " misslang: " + problem;
                    System.out.println("Background artist: " + kind + " " + number + " failed: " + problem);
                    Thread.sleep(45_000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
