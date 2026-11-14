package com.aresstack.threed4j.generator;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.TownLibrary;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lets the town grow while the game runs. On a thread of its own it plans a
 * further district as soon as the youngest has all its pictures (if growing is
 * switched on and the town is not yet as large as it may get), and it carries
 * out what the town hall orders: plan a district now, or give one another
 * landmark. The planning itself is {@link DistrictPlanner}'s.
 *
 * <p>Like the background artist it needs access to the AI service and waits
 * without it. What it plans is written to the district's folder; the game notices
 * the change and builds the district.</p>
 */
public final class TownPlanner implements AutoCloseable {

    private final AssetStore store;
    private final Thread thread;
    private final ConcurrentLinkedQueue<String> orders = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private volatile boolean busy;
    private volatile String status = "wartet";

    private TownPlanner(AssetStore store) {
        this.store = store;
        this.thread = new Thread(this::work, "3d4j-town-planner");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.MIN_PRIORITY);
    }

    public static TownPlanner start(AssetStore store) {
        TownPlanner planner = new TownPlanner(store);
        planner.thread.start();
        return planner;
    }

    /** What it is doing, in German, for the town hall. */
    public String status() {
        return status;
    }

    /** Whether it is planning right now or has orders waiting. */
    public boolean busy() {
        return busy || !orders.isEmpty();
    }

    /** Orders a further district, whether or not the town would grow by itself now. */
    public void planDistrict() {
        orders.add("district");
        thread.interrupt(); // wakes it from waiting
    }

    /** Orders another landmark for a planned district. */
    public void planLandmark(District district) {
        orders.add("landmark:" + district.id());
        thread.interrupt();
    }

    private void work() {
        long retryAccessAt = 0;
        long growAgainAt = 0;
        pause(6000); // let the game start first
        while (!closed) {
            String order = orders.peek();
            TownLibrary town = new TownLibrary(store);
            boolean grow = order == null && AiSetup.worldEnabled() && System.currentTimeMillis() >= growAgainAt
                    && town.districts().size() < AiSetup.worldMaxDistricts() && town.freePlot() != null
                    && town.complete(town.districts().get(town.districts().size() - 1));
            if (order == null && !grow) {
                if (!busy && !status.contains("misslang") && !status.contains("fertig")) {
                    status = !AiSetup.worldEnabled() ? "die Stadt wächst nicht von selbst"
                            : town.districts().size() >= AiSetup.worldMaxDistricts() ? "die Stadt hat ihre Höchstzahl an Vierteln"
                            : "wartet, bis das jüngste Viertel alle Bilder hat";
                }
                pause(3000);
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
                pause(2000);
                continue;
            }
            busy = true;
            String modelName = AiSetup.worldModel();
            AiSetup.Session access = session;
            FlowerComposer.Model model = (system, user) -> access.ask(modelName, system, user);
            try {
                if (order != null && order.startsWith("landmark:")) {
                    District district = town.district(order.substring("landmark:".length()));
                    if (district != null && !district.builtIn()) {
                        District.Landmark landmark = DistrictPlanner.anotherLandmark(model, district, text -> status = text);
                        town.save(new District(district.id(), district.name(), district.style(), district.motifs(),
                                district.layout().withLandmark(landmark)));
                        status = "fertig: „" + landmark.name() + "“ steht in " + district.name();
                    }
                } else {
                    int[] plot = town.freePlot();
                    if (plot == null) {
                        status = "misslang: kein Platz mehr für ein Viertel";
                    } else {
                        List<District> existing = town.districts();
                        District district = DistrictPlanner.plan(model, modelName, existing, plot[0], plot[1],
                                text -> status = text);
                        town.save(district);
                        status = "fertig: „" + district.name() + "“ ist geplant";
                    }
                }
            } catch (IOException | DistrictPlanner.Unusable | RuntimeException e) {
                status = "misslang: " + e.getMessage();
                System.out.println("Town planner: " + e.getMessage());
                growAgainAt = System.currentTimeMillis() + 120_000; // do not hammer a service or a model that fails
            } finally {
                if (order != null) {
                    orders.poll();
                }
                busy = false;
            }
            pause(4000);
        }
    }

    /** Waits; an order wakes it early. */
    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // woken by an order or by close()
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
