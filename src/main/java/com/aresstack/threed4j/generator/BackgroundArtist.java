package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final AssetStore store;
    /** One worker per request that may run at the same time; those beyond the setting wait. */
    private final Thread[] threads = new Thread[AiSetup.MAX_PARALLEL];
    /** What each worker is doing; {@code null} for one that has nothing to say. */
    private final String[] statuses = new String[AiSetup.MAX_PARALLEL];
    private final List<Claim> inWork = new ArrayList<>();
    private volatile boolean closed;
    private volatile long pauseUntil;
    private long retryAccessAt;

    private BackgroundArtist(AssetStore store) {
        this.store = store;
        statuses[0] = "wartet";
        for (int i = 0; i < threads.length; i++) {
            int worker = i;
            threads[i] = new Thread(() -> work(worker), "3d4j-background-artist-" + (i + 1));
            threads[i].setDaemon(true);
            threads[i].setPriority(Thread.MIN_PRIORITY); // the game comes first
        }
    }

    /** Starts drawing for all districts of the town and for what they share. */
    public static BackgroundArtist start(AssetStore store) {
        BackgroundArtist artist = new BackgroundArtist(store);
        for (Thread thread : artist.threads) {
            thread.start();
        }
        return artist;
    }

    /** What it is doing, in German, for the settings: e.g. "zeichnet Fenster 3". */
    public String status() {
        // With several requests at once: what is being drawn; otherwise whatever the first worker says.
        java.util.Set<String> drawing = new java.util.LinkedHashSet<>();
        java.util.Set<String> other = new java.util.LinkedHashSet<>();
        for (String status : statuses) {
            if (status != null) {
                (status.startsWith("zeichnet ") ? drawing : other).add(status);
            }
        }
        if (drawing.isEmpty()) {
            return other.isEmpty() ? "wartet" : other.iterator().next();
        }
        StringBuilder text = new StringBuilder("zeichnet ");
        for (String status : drawing) {
            text.append(text.length() > 9 ? ", " : "").append(status.substring(9));
        }
        return text.toString();
    }

    /** How far the collection is, e.g. "34 von 60 Bildern". */
    public String progress() {
        TownLibrary town = new TownLibrary(store);
        int have = 0;
        int want = 0;
        for (Job job : jobs(town)) {
            PartLibrary library = town.of(job.district(), job.kind());
            int target = library.target(job.kind());
            have += Math.min(library.parts(job.kind()).size(), target);
            want += target;
        }
        return have + " von " + want + " Bildern";
    }

    /** Everything there is to collect: the kinds of every district, and the shared kinds once. */
    private static List<Job> jobs(TownLibrary town) {
        List<Job> jobs = new ArrayList<>();
        for (District district : town.districts()) {
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
        return next(town, Map.of());
    }

    /**
     * @param inWork how many pictures of a job are being drawn right now; they count as there
     */
    static Job next(TownLibrary town, Map<Job, Integer> inWork) {
        Job choice = null;
        double least = 1.0;
        for (Job job : jobs(town)) {
            PartLibrary library = town.of(job.district(), job.kind());
            int target = library.target(job.kind());
            double filled = target == 0 ? 1.0
                    : (library.parts(job.kind()).size() + inWork.getOrDefault(job, 0)) / (double) target;
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

    /** A picture being drawn right now: nobody else takes its number, and it counts as nearly there. */
    private record Claim(Job job, int number) {
    }

    /**
     * Chooses what a worker draws next and reserves it, so that several workers neither draw the same
     * picture nor all rush at the same kind.
     *
     * @return the claim, or {@code null} if everything has reached its limit or is being drawn
     */
    private synchronized Claim claim(TownLibrary town) {
        Map<Job, Integer> counts = new HashMap<>();
        for (Claim claim : inWork) {
            counts.merge(claim.job(), 1, Integer::sum);
        }
        Job job = next(town, counts);
        if (job == null) {
            return null;
        }
        PartLibrary library = town.of(job.district(), job.kind());
        int number = library.parts(job.kind()).stream().mapToInt(PartLibrary.Part::number).max().orElse(0) + 1;
        while (inWork.contains(new Claim(job, number))) {
            number++;
        }
        Claim claim = new Claim(job, number);
        inWork.add(claim);
        return claim;
    }

    private synchronized void done(Claim claim) {
        inWork.remove(claim);
    }

    /** The access to the AI; only one worker at a time looks for it. */
    private synchronized AiSetup.Session access(int worker) {
        AiSetup.Session session = AiSetup.session();
        if (session == null && System.currentTimeMillis() >= retryAccessAt) {
            statuses[worker] = "sucht den Zugang zur KI";
            session = AiSetup.resumeSession();
            retryAccessAt = System.currentTimeMillis() + 60_000;
        }
        return session;
    }

    private void work(int worker) {
        try {
            Thread.sleep(4000 + worker * 700L); // let the game start first
            while (!closed) {
                if (worker >= AiSetup.backgroundParallel()) {
                    statuses[worker] = null; // not wanted right now
                    Thread.sleep(2000);
                    continue;
                }
                if (!AiSetup.backgroundEnabled()) {
                    statuses[worker] = "ausgeschaltet";
                    Thread.sleep(2000);
                    continue;
                }
                long wait = pauseUntil - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(Math.min(wait, 2000)); // a request failed: nobody hammers the service
                    continue;
                }
                AiSetup.Session session = access(worker);
                if (session == null) {
                    statuses[worker] = "wartet auf den Zugang zur KI";
                    Thread.sleep(2000);
                    continue;
                }
                TownLibrary town = new TownLibrary(store); // read anew: districts come and go
                Claim claim = claim(town);
                if (claim == null) {
                    statuses[worker] = inWorkCount() == 0 ? "fertig: die Sammlungen sind vollständig" : null;
                    Thread.sleep(inWorkCount() == 0 ? 10_000 : 2000);
                    continue;
                }
                Job job = claim.job();
                PartLibrary.Kind kind = job.kind();
                // "Birke 2 (Werksviertel)"; the people belong to no district.
                String what = name(job.district(), kind) + " " + claim.number()
                        + (kind.shared() ? "" : " (" + job.district().name() + ")");
                statuses[worker] = "zeichnet " + what;
                String problem;
                try {
                    problem = session.draw(town.of(job.district(), kind), job.district(), kind, claim.number());
                } finally {
                    done(claim);
                }
                if (problem == null) {
                    statuses[worker] = what + " ist fertig";
                    Thread.sleep(3000);
                } else {
                    // Not again at once: a service that refuses or a model that fails should not be hammered.
                    statuses[worker] = what + " misslang: " + problem;
                    System.out.println("Background artist: " + what + " failed: " + problem);
                    pauseUntil = System.currentTimeMillis() + 45_000;
                    Thread.sleep(45_000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized int inWorkCount() {
        return inWork.size();
    }

    @Override
    public void close() {
        closed = true;
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }
}
