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
    private final List<Thread> threads = new ArrayList<>();
    /** What each worker is doing; {@code null} for one that has nothing to say. */
    private final Map<Integer, String> statuses = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Claim> inWork = new ArrayList<>();
    private volatile boolean closed;
    private volatile long pauseUntil;
    private long retryAccessAt;

    private BackgroundArtist(AssetStore store) {
        this.store = store;
        statuses.put(0, "wartet");
        // The setting may change at any time, and a worker in the middle of a picture does not look
        // at it for minutes: somebody else does, every second.
        Thread overseer = new Thread(() -> {
            try {
                while (!closed) {
                    hire(AiSetup.parallel());
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "3d4j-background-overseer");
        overseer.setDaemon(true);
        this.overseer = overseer;
    }

    private final Thread overseer;
    private final long startedAt = System.currentTimeMillis();

    /** Starts workers until there are as many as requests may run at the same time; none is ever stopped. */
    private synchronized void hire(int wanted) {
        while (!closed && threads.size() < wanted) {
            int worker = threads.size();
            Thread thread = new Thread(() -> work(worker), "3d4j-background-artist-" + (worker + 1));
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY); // the game comes first
            threads.add(thread);
            thread.start();
        }
    }

    /** Starts drawing for all districts of the town and for what they share. */
    public static BackgroundArtist start(AssetStore store) {
        BackgroundArtist artist = new BackgroundArtist(store);
        artist.overseer.start();
        return artist;
    }

    /** What it is doing, in German, in one line: "zeichnet Fenster 3", or how many pictures and which. */
    public String status() {
        List<String> drawing = drawing();
        if (drawing.size() == 1) {
            return "zeichnet " + drawing.get(0);
        }
        if (drawing.size() > 1) {
            return "zeichnet " + drawing.size() + " Bilder: " + String.join(", ", drawing);
        }
        for (String status : new java.util.TreeMap<>(statuses).values()) {
            return status; // nothing is being drawn: whatever the first worker says
        }
        return "wartet";
    }

    /** Every picture being drawn right now, e.g. "Fenster 3 (Werksviertel)"; empty if none is. */
    public List<String> drawing() {
        List<String> drawing = new ArrayList<>();
        for (String status : new java.util.TreeMap<>(statuses).values()) {
            if (status.startsWith("zeichnet ")) {
                drawing.add(status.substring(9));
            }
        }
        return drawing;
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
            statuses.put(worker, "sucht den Zugang zur KI");
            session = AiSetup.resumeSession();
            retryAccessAt = System.currentTimeMillis() + 60_000;
        }
        return session;
    }

    private void work(int worker) {
        try {
            Thread.sleep(Math.max(300, startedAt + 4000 - System.currentTimeMillis())); // let the game start first
            while (!closed) {
                if (worker >= AiSetup.parallel()) {
                    statuses.remove(worker); // not wanted right now
                    Thread.sleep(2000);
                    continue;
                }
                if (!AiSetup.backgroundEnabled()) {
                    statuses.put(worker, "ausgeschaltet");
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
                    statuses.put(worker, "wartet auf den Zugang zur KI");
                    Thread.sleep(2000);
                    continue;
                }
                TownLibrary town = new TownLibrary(store); // read anew: districts come and go
                Claim claim = claim(town);
                if (claim == null) {
                    if (inWorkCount() == 0) {
                        statuses.put(worker, "fertig: die Sammlungen sind vollständig");
                    } else {
                        statuses.remove(worker);
                    }
                    Thread.sleep(inWorkCount() == 0 ? 10_000 : 2000);
                    continue;
                }
                Job job = claim.job();
                PartLibrary.Kind kind = job.kind();
                // "Birke 2 (Werksviertel)"; the people belong to no district.
                String what = name(job.district(), kind) + " " + claim.number()
                        + (kind.shared() ? "" : " (" + job.district().name() + ")");
                statuses.put(worker, "zeichnet " + what);
                String problem;
                try {
                    problem = session.draw(town.of(job.district(), kind), job.district(), kind, claim.number());
                } finally {
                    done(claim);
                }
                if (problem == null) {
                    statuses.put(worker, what + " ist fertig");
                    Thread.sleep(3000);
                } else {
                    // Not again at once: a service that refuses or a model that fails should not be hammered.
                    statuses.put(worker, what + " misslang: " + problem);
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
        overseer.interrupt();
        synchronized (this) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }
    }
}
