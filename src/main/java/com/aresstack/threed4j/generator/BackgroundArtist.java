package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps drawing pictures for the town while the game runs: one at a time, on a
 * thread of its own, until every kind in every district's {@link PartLibrary} and
 * in the shared collection has reached its limit. When pictures are removed or a limit is raised, it goes on.
 *
 * <p>It needs access to the AI service. It uses the access chosen in the world
 * menu during this run; otherwise it fetches the remembered one without asking
 * (KeePass entry of the last run, or the environment variable). Without access,
 * or switched off in the settings, it waits and tries again later; the game does
 * not depend on it.</p>
 */
public final class BackgroundArtist implements AutoCloseable {

    /** One thing to draw: a kind for a district, or for the whole town if the kind is shared. */
    record Job(District district, PartLibrary.Kind kind) {
    }

    private final TownLibrary town;
    private final Thread thread;
    private volatile boolean closed;
    private volatile String status = "wartet";

    private BackgroundArtist(TownLibrary town) {
        this.town = town;
        this.thread = new Thread(this::work, "3d4j-background-artist");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.MIN_PRIORITY); // the game comes first
    }

    /** Starts drawing for all districts of the town and for what they share. */
    public static BackgroundArtist start(AssetStore store) {
        BackgroundArtist artist = new BackgroundArtist(new TownLibrary(store));
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
        for (Job job : jobs()) {
            PartLibrary library = town.of(job.district(), job.kind());
            int target = library.target(job.kind());
            have += Math.min(library.parts(job.kind()).size(), target);
            want += target;
        }
        return have + " von " + want + " Bildern";
    }

    /** Everything there is to collect: the kinds of every district, and the shared kinds once. */
    private static List<Job> jobs() {
        List<Job> jobs = new ArrayList<>();
        for (District district : District.ALL) {
            for (PartLibrary.Kind kind : TownLibrary.districtKinds()) {
                jobs.add(new Job(district, kind));
            }
        }
        for (PartLibrary.Kind kind : TownLibrary.sharedKinds()) {
            jobs.add(new Job(District.ALL.get(0), kind));
        }
        return jobs;
    }

    /**
     * What to draw next in the town: what is furthest from its limit, measured in
     * parts of the limit, so that every kind of every district gets its first
     * pictures early.
     *
     * @return the job, or {@code null} if everything has reached its limit
     */
    static Job next(TownLibrary town) {
        Job choice = null;
        double least = 1.0;
        for (Job job : jobs()) {
            PartLibrary library = town.of(job.district(), job.kind());
            int target = library.target(job.kind());
            double filled = target == 0 ? 1.0 : library.parts(job.kind()).size() / (double) target;
            if (filled < least) {
                least = filled;
                choice = job;
            }
        }
        return choice;
    }

    /** What a kind is called in a district, in German: one of it. */
    public static String name(District district, PartLibrary.Kind kind) {
        return district.motif(kind).name();
    }

    /** What a kind is called in a district, in German: several of it. */
    public static String plural(District district, PartLibrary.Kind kind) {
        return district.motif(kind).plural();
    }

    /** What a kind is called in the Blütenviertel, one of it. */
    public static String name(PartLibrary.Kind kind) {
        return name(District.BLOSSOM_QUARTER, kind);
    }

    /** What a kind is called in the Blütenviertel, several of it. */
    public static String plural(PartLibrary.Kind kind) {
        return plural(District.BLOSSOM_QUARTER, kind);
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
                Job job = next(town);
                if (job == null) {
                    status = "fertig: die Sammlungen sind vollständig";
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
                PartLibrary.Kind kind = job.kind();
                PartLibrary library = town.of(job.district(), kind);
                int number = library.parts(kind).stream().mapToInt(PartLibrary.Part::number).max().orElse(0) + 1;
                // "Birke 2 (Werksviertel)"; the people belong to no district.
                String what = name(job.district(), kind) + " " + number
                        + (kind.shared() ? "" : " (" + job.district().name() + ")");
                status = "zeichnet " + what;
                String problem = session.draw(library, job.district(), kind, number);
                if (problem == null) {
                    status = what + " ist fertig";
                    Thread.sleep(3000);
                } else {
                    // Not again at once: a service that refuses or a model that fails should not be hammered.
                    status = what + " misslang: " + problem;
                    System.out.println("Background artist: " + what + " failed: " + problem);
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
