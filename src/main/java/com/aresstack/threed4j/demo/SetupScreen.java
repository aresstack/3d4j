package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetSpec;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.generator.AiSetup;
import com.aresstack.threed4j.generator.KeePassEntry;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;
import com.aresstack.threed4j.platform.MouseButton;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "create the world" menu shown after the loading screen: access to the AI
 * service, choice of the model, generation of the artwork. Access comes from
 * KeePass (pair, then pick the entry of the service from a drop-down list; its
 * address and password are the address and the API key) or is typed in by hand.
 * The steps follow the setup wizards of Space Harvester and the Enterprise AI
 * Client.
 *
 * <p>The menu is painted with Java 2D into an image the size of the window; the
 * game shows that image as one texture. Everything is laid out on a fixed
 * 1280 x 720 design surface that is scaled to the window. Network and KeePass
 * calls run on background threads and report back through fields that the game
 * thread reads in {@link #update}.</p>
 */
final class SetupScreen implements AutoCloseable {

    private static final int DESIGN_WIDTH = 1280;
    private static final int DESIGN_HEIGHT = 720;

    private static final Color INK = new Color(0x4A2A5E);
    private static final Color TEXT = new Color(0x5C4A66);
    private static final Color PINK = new Color(0xF29BC4);
    private static final Color PINK_DARK = new Color(0xD9649E);
    private static final Color PINK_LIGHT = new Color(0xFFE3F0);
    private static final Color PETAL = new Color(0xFFC2DD);
    private static final Color LEAF = new Color(0x8CCB7A);
    private static final Color GOOD = new Color(0x3F9B5B);
    private static final Color BAD = new Color(0xC2403D);
    private static final Color FIELD = new Color(0xFFFAFD);

    /** Number of models visible at once in the list. */
    private static final int MODEL_ROWS = 7;

    /** Number of KeePass entries visible at once in the open drop-down list. */
    private static final int ENTRY_ROWS = 5;

    private static final String[] STEPS = {"Zugang", "Modell", "Welt"};
    private static final int ACCESS = 0;
    private static final int MODEL = 1;
    private static final int WORLD = 2;
    private static final String HINT_KEEPASS = "Willkommen! Verbinde das Spiel mit KeePass und wähle den Eintrag des "
            + "KI-Dienstes. Adresse und API-Key kommen aus dem Eintrag und bleiben in KeePass.";
    private static final String HINT_MANUAL = "Willkommen! Trag ein, wo der KI-Dienst wohnt, und seinen API-Key. "
            + "Der Schlüssel wird nicht gespeichert.";
    private static final String[] HINTS = {
            HINT_KEEPASS,
            "Diese Modelle bietet der Dienst an. Ich habe eines vorgewählt, du kannst ein anderes anklicken.",
            "Das Modell zeichnet nun die Bewohner und Pflanzen der Stadt, alle gleichzeitig. "
                    + "Was gelingt, siehst du gleich im Spiel."
    };

    private final AssetStore store;
    private final BufferedImage backdrop;
    private final BufferedImage cover;
    private final ExecutorService background = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "3d4j-setup");
        thread.setDaemon(true);
        return thread;
    });

    // --- state; written by the game thread, and by background tasks through post() -----------------
    private int step = ACCESS;
    private final String keyFromEnvironment = AiSetup.apiKeyFromEnvironment();
    /** Where access comes from: KeePass, or typed in by hand. */
    private boolean useKeePass = keyFromEnvironment == null;
    private final StringBuilder baseUrl = new StringBuilder();
    private final StringBuilder manualKey = new StringBuilder();
    private final StringBuilder pairingCode = new StringBuilder();
    private final StringBuilder entryFilter = new StringBuilder();
    private StringBuilder focus;
    private String message = "";
    private boolean messageIsError;
    private String busy;
    private boolean connectOnOpen;
    private boolean awaitingCode;
    private CompletableFuture<String> codeAnswer;
    /** The entries KeePass handed out; {@code null} until connected. */
    private List<KeePassEntry> entries;
    private KeePassEntry selectedEntry;
    private String rememberedEntry;
    private boolean entriesOpen;
    private int entryCursor;
    private int entryScroll;
    /** Address and key in use, set when the access step is confirmed. */
    private String serviceUrl;
    private String apiKey;
    private List<String> models = List.of();
    private String selectedModel;
    private int modelScroll;
    private final Map<AssetSpec, String> assetStates = new LinkedHashMap<>();
    private boolean generating;
    private boolean generatedOnce;
    private boolean finished;
    private boolean fromGame;

    private final List<Runnable> posted = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private Runnable primaryAction;
    private boolean dirty = true;

    private record Region(Rectangle2D area, Runnable action) {
    }

    SetupScreen(AssetStore store, BufferedImage backdrop, BufferedImage cover) {
        this.store = store;
        this.backdrop = backdrop;
        this.cover = cover;
        AiSetup.Settings settings = AiSetup.load();
        baseUrl.append(settings.baseUrl());
        rememberedEntry = settings.keePassEntry();
        focus = useKeePass ? null : baseUrl;
        // Once paired, connecting needs nothing from the user: do it right away.
        connectOnOpen = useKeePass && AiSetup.isPaired();
        selectedModel = settings.model();
        for (AssetSpec spec : AssetCatalog.ALL) {
            assetStates.put(spec, store.has(spec) ? "vorhanden" : "fehlt");
        }
    }

    /** {@code true} once the user has left the menu, with or without generating anything. */
    boolean isFinished() {
        return finished;
    }

    /** {@code true} if the image has to be painted again; resets the flag. */
    boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    // ------------------------------------------------------------------- input

    /**
     * Handles one frame of input.
     *
     * @param width  current width of the window in pixels, to translate the mouse position
     * @param height current height of the window in pixels
     */
    void update(Input input, int width, int height) {
        List<Runnable> toRun;
        synchronized (posted) {
            toRun = new ArrayList<>(posted);
            posted.clear();
        }
        for (Runnable runnable : toRun) {
            runnable.run();
            dirty = true;
        }

        if (connectOnOpen) {
            connectOnOpen = false;
            connectKeePass();
            dirty = true;
        }

        String typed = input.typedText();
        for (int i = 0; i < typed.length(); i++) {
            type(typed.charAt(i));
        }
        if (step == MODEL && !models.isEmpty()) {
            int index = Math.max(0, models.indexOf(selectedModel));
            if (input.wasPressed(Key.DOWN) && index < models.size() - 1) {
                selectModel(index + 1);
            }
            if (input.wasPressed(Key.UP) && index > 0) {
                selectModel(index - 1);
            }
        }
        if (entriesOpen) {
            if (input.wasPressed(Key.DOWN)) {
                moveEntryCursor(entryCursor + 1);
            }
            if (input.wasPressed(Key.UP)) {
                moveEntryCursor(entryCursor - 1);
            }
        }
        if (input.wasPressed(MouseButton.LEFT)) {
            double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
            double x = (input.mouseX() - (width - DESIGN_WIDTH * scale) / 2) / scale;
            double y = (input.mouseY() - (height - DESIGN_HEIGHT * scale) / 2) / scale;
            for (Region region : new ArrayList<>(regions)) {
                if (region.area().contains(x, y)) {
                    region.action().run();
                    dirty = true;
                    break;
                }
            }
        }
    }

    /** Handles one typed character. Tab and Enter are part of the stream so that they act in typing order. */
    private void type(char character) {
        if (character == '\t') {
            nextField();
            return;
        }
        if (character == '\r') {
            if (primaryAction != null && (busy == null || awaitingCode)) {
                Runnable action = primaryAction;
                primaryAction = null; // the button belongs to the picture on screen; wait for the next one
                action.run();
                dirty = true;
            }
            return;
        }
        if (focus == null && step == ACCESS && useKeePass && entries != null && busy == null && character >= ' ') {
            openEntries(); // typing with the list closed starts a search
        }
        if (focus == null || busy != null && focus != pairingCode) {
            return;
        }
        if (character == '\b') {
            if (focus.length() > 0) {
                focus.setLength(focus.length() - 1);
            }
        } else if (character == Input.PASTE) {
            focus.append(clipboardText());
        } else if (focus.length() < 300) {
            focus.append(character);
        }
        if (focus == entryFilter) {
            entryCursor = 0;
            entryScroll = 0;
        }
        dirty = true;
    }

    private static String clipboardText() {
        try {
            Object text = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return String.valueOf(text).replaceAll("[\\r\\n\\t]", "").trim();
        } catch (Throwable e) {
            return ""; // no clipboard, or nothing textual in it
        }
    }

    private void nextField() {
        if (step == ACCESS && !useKeePass) {
            focus = focus == baseUrl ? manualKey : baseUrl;
            dirty = true;
        }
    }

    // ------------------------------------------------------- KeePass entry list

    /** The entries whose title or address contains what was typed into the open list. */
    private List<KeePassEntry> visibleEntries() {
        String wanted = entryFilter.toString().trim().toLowerCase(Locale.ROOT);
        if (entries == null || wanted.isEmpty()) {
            return entries == null ? List.of() : entries;
        }
        return entries.stream()
                .filter(entry -> entry.title().toLowerCase(Locale.ROOT).contains(wanted)
                        || entry.url().toLowerCase(Locale.ROOT).contains(wanted))
                .toList();
    }

    private void openEntries() {
        entriesOpen = true;
        entryFilter.setLength(0);
        focus = entryFilter;
        say("", false);
        entryScroll = 0;
        moveEntryCursor(Math.max(0, entries.indexOf(selectedEntry)));
    }

    private void closeEntries() {
        entriesOpen = false;
        entryFilter.setLength(0);
        focus = null;
    }

    private void moveEntryCursor(int index) {
        int count = visibleEntries().size();
        entryCursor = Math.max(0, Math.min(index, count - 1));
        if (entryCursor < entryScroll) {
            entryScroll = entryCursor;
        } else if (entryCursor >= entryScroll + ENTRY_ROWS) {
            entryScroll = entryCursor - ENTRY_ROWS + 1;
        }
        dirty = true;
    }

    private void pickEntry(KeePassEntry entry) {
        selectedEntry = entry;
        closeEntries();
        say("", false);
    }

    private void pickEntryUnderCursor() {
        List<KeePassEntry> visible = visibleEntries();
        if (entryCursor < visible.size()) {
            pickEntry(visible.get(entryCursor));
        }
    }

    private void chooseAccess(boolean keePass) {
        if (busy != null || useKeePass == keePass) {
            return;
        }
        useKeePass = keePass;
        closeEntries();
        focus = keePass ? null : baseUrl;
        say("", false);
    }

    private void selectModel(int index) {
        selectedModel = models.get(index);
        int visible = MODEL_ROWS;
        if (index < modelScroll) {
            modelScroll = index;
        } else if (index >= modelScroll + visible) {
            modelScroll = index - visible + 1;
        }
        dirty = true;
    }

    // ---------------------------------------------------------- step transitions

    private void say(String text, boolean error) {
        message = text;
        messageIsError = error;
    }

    /** Runs a blocking task off the game thread; its outcome is applied on the game thread. */
    private void inBackground(String label, BlockingTask task) {
        busy = label;
        say("", false);
        background.submit(() -> {
            Runnable outcome;
            try {
                outcome = task.run();
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                outcome = () -> say(reason, true);
            }
            Runnable applied = outcome;
            post(() -> {
                busy = null;
                awaitingCode = false;
                applied.run();
            });
        });
    }

    private interface BlockingTask {
        /** @return what to do on the game thread afterwards */
        Runnable run() throws Exception;
    }

    private void post(Runnable runnable) {
        synchronized (posted) {
            posted.add(runnable);
        }
    }

    /** Connects to KeePass, pairing first if needed, and fetches the entries for the drop-down list. */
    private void connectKeePass() {
        pairingCode.setLength(0);
        codeAnswer = new CompletableFuture<>();
        CompletableFuture<String> answer = codeAnswer;
        inBackground(AiSetup.isPaired() ? "Verbinde mit KeePass …" : "Kopple mit KeePass …", () -> {
            List<KeePassEntry> found;
            try {
                found = AiSetup.keePassEntries(() -> askForCode(answer));
            } catch (Exception e) {
                throw new Exception(keePassProblem(e));
            }
            return () -> {
                entries = found;
                focus = null;
                selectedEntry = suggestedEntry(found, rememberedEntry);
                if (found.isEmpty()) {
                    say("KeePass hat keine Einträge herausgegeben.", true);
                } else if (selectedEntry == null) {
                    openEntries();
                }
            };
        });
    }

    /**
     * The entry to show before the user has chosen: the one used last time; otherwise
     * the only one that has an address; otherwise none.
     */
    static KeePassEntry suggestedEntry(List<KeePassEntry> entries, String remembered) {
        for (KeePassEntry entry : entries) {
            if (entry.title().equalsIgnoreCase(remembered)) {
                return entry;
            }
        }
        List<KeePassEntry> withAddress = entries.stream().filter(KeePassEntry::hasUrl).toList();
        return withAddress.size() == 1 ? withAddress.get(0) : null;
    }

    private void confirmAccess() {
        String address;
        String key;
        if (useKeePass) {
            if (selectedEntry == null) {
                say("Bitte den Eintrag des KI-Dienstes wählen.", true);
                return;
            }
            if (AiSetup.normalizeBaseUrl(selectedEntry.url()) == null) {
                say("Der Eintrag „" + selectedEntry.title() + "“ hat keine Adresse. Trag in KeePass die URL des "
                        + "Dienstes ein (https://…/v1) und verbinde neu, oder nimm „Manuell“.", true);
                return;
            }
            readEntryAndContinue(selectedEntry.title());
            return;
        } else {
            address = AiSetup.normalizeBaseUrl(baseUrl.toString());
            key = manualKey.toString().isBlank() ? keyFromEnvironment : manualKey.toString().trim();
            if (address == null) {
                say("Das ist keine Adresse wie https://ki.intern/v1.", true);
                return;
            }
            if (key == null) {
                say("Es fehlt der API-Key.", true);
                return;
            }
            baseUrl.setLength(0);
            baseUrl.append(address);
        }
        useAccess(address, key);
    }

    /** The list carries no passwords: fetch the chosen entry with its password, then go on. */
    private void readEntryAndContinue(String title) {
        codeAnswer = new CompletableFuture<>();
        CompletableFuture<String> answer = codeAnswer;
        inBackground("Lese den Eintrag aus KeePass …", () -> {
            KeePassEntry entry;
            try {
                entry = AiSetup.keePassEntry(title, () -> askForCode(answer));
            } catch (Exception e) {
                throw new Exception(keePassProblem(e));
            }
            return () -> {
                String address = entry == null ? null : AiSetup.normalizeBaseUrl(entry.url());
                if (entry == null) {
                    say("KeePass hat keinen Eintrag „" + title + "“ mehr.", true);
                } else if (address == null) {
                    say("Der Eintrag „" + title + "“ hat keine Adresse.", true);
                } else if (entry.password().isBlank()) {
                    say("Der Eintrag „" + title + "“ hat kein Passwort. Dort gehört der API-Key hin.", true);
                } else {
                    rememberedEntry = entry.title();
                    useAccess(address, entry.password());
                }
            };
        });
    }

    private void useAccess(String address, String key) {
        serviceUrl = address;
        apiKey = key;
        if (!saveSettings()) {
            return;
        }
        say("", false);
        focus = null;
        step = MODEL;
        loadModels();
    }

    /** Called off the game thread while KeePass shows its one-time password; waits for the user. */
    private String askForCode(CompletableFuture<String> answer) {
        post(() -> {
            awaitingCode = true;
            focus = pairingCode;
            busy = "KeePass zeigt jetzt ein Einmal-Passwort.";
        });
        return answer.join();
    }

    private static String keePassProblem(Exception e) {
        String reason = firstLine(e);
        if (reason.contains("rejected the one-time password")) {
            return "KeePass hat das Einmal-Passwort abgelehnt. Bitte noch einmal koppeln und den neuen Code eintippen.";
        }
        if (reason.contains("cancelled")) {
            return "Kopplung abgebrochen.";
        }
        return "KeePass antwortet nicht. Läuft KeePass mit dem Plugin KeePassRPC, und ist die Datenbank entsperrt?\n("
                + reason + ")";
    }

    /**
     * Remembers the choices. With KeePass only the title of the entry is kept, so
     * that a changed address in KeePass is followed; the API key is never written.
     */
    private boolean saveSettings() {
        String model = selectedModel == null || selectedModel.isBlank() ? AiSetup.PREFERRED_MODEL : selectedModel;
        try {
            AiSetup.save(useKeePass
                    ? new AiSetup.Settings("", model, rememberedEntry)
                    : new AiSetup.Settings(serviceUrl, model, ""));
            return true;
        } catch (java.io.IOException e) {
            say("Die Einstellungen lassen sich nicht speichern: " + e.getMessage(), true);
            return false;
        }
    }

    private static String firstLine(Exception e) {
        String text = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    private void submitPairingCode() {
        if (codeAnswer != null && !pairingCode.toString().isBlank()) {
            awaitingCode = false;
            busy = "Kopple mit KeePass …";
            codeAnswer.complete(pairingCode.toString().trim());
        }
    }

    private void unpair() {
        try {
            AiSetup.unpair();
            entries = null;
            selectedEntry = null;
            closeEntries();
            say("Kopplung gelöscht. In KeePass bleibt sie eingetragen, bis du sie dort entfernst.", false);
        } catch (java.io.IOException e) {
            say("Die Kopplung lässt sich nicht löschen: " + e.getMessage(), true);
        }
    }

    private void loadModels() {
        String address = serviceUrl;
        String key = apiKey;
        String wanted = selectedModel == null || selectedModel.isBlank() ? AiSetup.PREFERRED_MODEL : selectedModel;
        inBackground("Lade die Modelle vom Dienst …", () -> {
            List<String> offered;
            try {
                offered = AiSetup.listModels(address, key);
            } catch (Exception e) {
                throw new Exception(firstLine(e).startsWith("proxy route")
                        ? "Der Weg zum KI-Dienst lässt sich nicht bestimmen (Proxy).\n(" + firstLine(e) + ")"
                        : "Der KI-Dienst hat die Modelle nicht geliefert.\n(" + firstLine(e) + ")");
            }
            return () -> {
                models = offered;
                modelScroll = 0;
                if (offered.isEmpty()) {
                    say("Der Dienst bietet keine Modelle an.", true);
                } else {
                    // Keep an earlier choice if the service still offers it, otherwise suggest GPT-OSS 120B.
                    String choice = offered.contains(wanted) ? wanted : AiSetup.preselect(offered, AiSetup.PREFERRED_MODEL);
                    selectModel(offered.indexOf(choice));
                }
            };
        });
    }

    private void confirmModel() {
        if (selectedModel == null || !models.contains(selectedModel)) {
            say("Bitte ein Modell auswählen.", true);
            return;
        }
        if (!saveSettings()) {
            return;
        }
        say("", false);
        step = WORLD;
    }

    /** Asks for all assets at the same time; each reports back on its own. */
    private void generate(boolean everything) {
        List<AssetSpec> wanted = new ArrayList<>();
        for (AssetSpec spec : AssetCatalog.ALL) {
            if (everything || !store.has(spec)) {
                wanted.add(spec);
                assetStates.put(spec, "wird gezeichnet …");
            }
        }
        if (wanted.isEmpty()) {
            say("Alles ist schon da. „Alles neu zeichnen“ ersetzt es.", false);
            return;
        }
        generating = true;
        generatedOnce = true;
        say("", false);
        AiSetup.Session session = new AiSetup.Session(serviceUrl, selectedModel, apiKey);
        AtomicInteger open = new AtomicInteger(wanted.size());
        for (AssetSpec spec : wanted) {
            background.submit(() -> {
                String problem;
                try {
                    problem = session.generate(store, spec);
                } catch (RuntimeException e) {
                    problem = String.valueOf(e.getMessage());
                }
                String result = problem == null ? "fertig" : "Fehler: " + inGerman(problem);
                boolean last = open.decrementAndGet() == 0;
                post(() -> {
                    assetStates.put(spec, result);
                    if (last) {
                        generating = false;
                    }
                });
            });
        }
    }

    /** The generator reports in English for the console; the menu speaks German. */
    private static String inGerman(String problem) {
        if (problem.contains("no <svg> element")) {
            return "Die Antwort enthielt keine Zeichnung.";
        }
        if (problem.contains("empty or tiny")) {
            return "Die Zeichnung war leer.";
        }
        if (problem.contains("background is not transparent")) {
            return "Das Motiv war nicht freigestellt.";
        }
        if (problem.contains("cut off at max_tokens")) {
            return "Die Antwort wurde abgeschnitten (chat.maxTokens erhöhen).";
        }
        if (problem.contains("proxy route")) {
            return "Kein Weg zum Dienst (Proxy).";
        }
        if (problem.contains("cannot reach")) {
            return "Der Dienst ist nicht erreichbar.";
        }
        if (problem.contains("answered HTTP")) {
            return "Der Dienst lehnt ab: " + problem.substring(problem.indexOf("HTTP"));
        }
        if (problem.contains("not valid SVG") || problem.contains("cannot be drawn") || problem.contains("has no size")) {
            return "Die Zeichnung war fehlerhaft.";
        }
        return problem;
    }

    /** Tells the menu that it was opened from the running game, for the wording of its way out. */
    void openedFromGame() {
        fromGame = true;
        dirty = true;
    }

    /** Leaves the menu as "Schließen" does; ignored while artwork is being drawn. */
    void leave() {
        if (!generating) {
            finish();
            dirty = true;
        }
    }

    private void finish() {
        if (codeAnswer != null) {
            codeAnswer.complete(""); // releases a pairing that still waits for its code
        }
        finished = true;
    }

    // ------------------------------------------------------------------- paint

    /** Paints the menu for a window of the given size. */
    BufferedImage paint(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Backdrop: the panorama, enlarged until it covers the window, under a pink veil.
        double cover = Math.max((double) width / backdrop.getWidth(), (double) height / backdrop.getHeight());
        int backdropWidth = (int) Math.ceil(backdrop.getWidth() * cover);
        int backdropHeight = (int) Math.ceil(backdrop.getHeight() * cover);
        g.drawImage(backdrop, (width - backdropWidth) / 2, (height - backdropHeight) / 2, backdropWidth, backdropHeight, null);
        g.setColor(new Color(255, 232, 244, 150));
        g.fillRect(0, 0, width, height);

        double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
        AffineTransform design = new AffineTransform();
        design.translate((width - DESIGN_WIDTH * scale) / 2, (height - DESIGN_HEIGHT * scale) / 2);
        design.scale(scale, scale);
        g.transform(design);

        regions.clear();
        primaryAction = null;
        paintPanel(g);
        paintHeader(g);
        switch (step) {
            case ACCESS -> paintAccess(g);
            case MODEL -> paintModel(g);
            default -> paintWorld(g);
        }
        paintFooter(g);
        g.dispose();
        return image;
    }

    private void paintPanel(Graphics2D g) {
        Shape panel = new RoundRectangle2D.Double(110, 50, 1060, 620, 48, 48);
        g.setColor(new Color(74, 42, 94, 40));
        g.fill(new RoundRectangle2D.Double(118, 60, 1060, 620, 48, 48));
        g.setColor(new Color(255, 252, 254, 240));
        g.fill(panel);
        g.setStroke(new BasicStroke(5f));
        g.setColor(PINK);
        g.draw(panel);

        // The cover picture as the menu's companion.
        Shape frame = new RoundRectangle2D.Double(146, 150, 250, 375, 30, 30);
        Shape oldClip = g.getClip();
        g.clip(frame);
        g.drawImage(cover, 146, 150, 250, 375, null);
        g.setClip(oldClip);
        g.setStroke(new BasicStroke(4f));
        g.setColor(PINK_DARK);
        g.draw(frame);

        blossom(g, 110, 56, 34);
        blossom(g, 1170, 664, 34);
        blossom(g, 1150, 70, 20);
        blossom(g, 140, 650, 22);
        blossom(g, 396, 525, 18);
    }

    /** A five-petal cherry blossom with a leaf. */
    static void blossom(Graphics2D g, double x, double y, double radius) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.setColor(LEAF);
        g.fill(new Ellipse2D.Double(radius * 0.5, radius * 0.3, radius * 1.1, radius * 0.5));
        for (int i = 0; i < 5; i++) {
            g.rotate(Math.toRadians(72));
            g.setColor(PETAL);
            g.fill(new Ellipse2D.Double(-radius * 0.42, -radius, radius * 0.84, radius));
            g.setColor(PINK);
            g.setStroke(new BasicStroke((float) Math.max(1.5, radius / 14)));
            g.draw(new Ellipse2D.Double(-radius * 0.42, -radius, radius * 0.84, radius));
        }
        g.setColor(new Color(0xFFD86B));
        g.fill(new Ellipse2D.Double(-radius * 0.25, -radius * 0.25, radius * 0.5, radius * 0.5));
        g.setTransform(old);
    }

    private void paintHeader(Graphics2D g) {
        g.setColor(INK);
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 44));
        g.drawString("Die Welt erschaffen", 430, 118);

        // Step trail
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        int x = 430;
        for (int i = 0; i < STEPS.length; i++) {
            String label = (i + 1) + "  " + STEPS[i];
            int pill = g.getFontMetrics().stringWidth(label) + 36;
            Shape shape = new RoundRectangle2D.Double(x, 142, pill, 34, 34, 34);
            g.setColor(i == step ? PINK_DARK : i < step ? PINK : PINK_LIGHT);
            g.fill(shape);
            g.setColor(i <= step ? Color.WHITE : TEXT);
            g.drawString(label, x + 18, 165);
            x += pill + 12;
        }

        // What the companion says about this step
        Shape bubble = new RoundRectangle2D.Double(430, 196, 700, 92, 26, 26);
        g.setColor(PINK_LIGHT);
        g.fill(bubble);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        wrapped(g, step == ACCESS && !useKeePass ? HINT_MANUAL : HINTS[step], 450, 222, 660, 23);
    }

    private void paintAccess(Graphics2D g) {
        choice(g, "KeePass", 430, useKeePass, () -> chooseAccess(true));
        choice(g, "Manuell", 586, !useKeePass, () -> chooseAccess(false));
        if (useKeePass) {
            paintKeePass(g);
        } else {
            paintManual(g);
        }
    }

    private void paintManual(Graphics2D g) {
        label(g, "Adresse des KI-Dienstes", 430, 372);
        field(g, baseUrl, "https://…/v1", 430, 382, 700, false);
        label(g, "API-Key", 430, 456);
        field(g, manualKey, keyFromEnvironment != null ? "aus der Umgebungsvariable THREED4J_API_KEY" : "", 430, 466, 700, true);
        if (busy == null) {
            primary(g, "Weiter", this::confirmAccess);
        }
    }

    private void paintKeePass(Graphics2D g) {
        boolean paired = AiSetup.isPaired();
        if (entries != null) {
            note(g, "Verbunden mit KeePass", 430, 364, GOOD);
        } else {
            note(g, paired ? "Gekoppelt, noch nicht verbunden" : "Noch nicht mit KeePass gekoppelt", 430, 364, TEXT);
        }

        if (awaitingCode) {
            label(g, "Einmal-Passwort aus KeePass", 430, 404);
            field(g, pairingCode, "", 430, 414, 380, true);
            primary(g, "Bestätigen", this::submitPairingCode);
            return;
        }
        if (busy != null) {
            return;
        }
        if (entries == null) {
            primary(g, paired ? "Verbinden" : "Koppeln", this::connectKeePass);
            if (paired) {
                secondary(g, "Entkoppeln", 780, this::unpair);
            }
            return;
        }

        label(g, "Eintrag des KI-Dienstes", 430, 392);
        paintEntryBox(g);
        if (entriesOpen) {
            paintEntryList(g);
            primary(g, "Übernehmen", this::pickEntryUnderCursor);
        } else {
            if (selectedEntry != null) {
                boolean hasAddress = AiSetup.normalizeBaseUrl(selectedEntry.url()) != null;
                note(g, hasAddress ? "Adresse:  " + selectedEntry.url() : "Adresse:  fehlt im Eintrag (Feld URL)",
                        430, 474, hasAddress ? TEXT : BAD);
                note(g, "API-Key:  das Passwort des Eintrags", 430, 502, TEXT);
            }
            primary(g, "Weiter", this::confirmAccess);
            secondary(g, "Entkoppeln", 780, this::unpair);
        }
    }

    /** The closed drop-down list: the chosen entry; while open, the search text. */
    private void paintEntryBox(Graphics2D g) {
        Shape box = new RoundRectangle2D.Double(430, 400, 700, 44, 20, 20);
        g.setColor(FIELD);
        g.fill(box);
        g.setStroke(new BasicStroke(entriesOpen ? 3.5f : 2f));
        g.setColor(entriesOpen ? PINK_DARK : PINK);
        g.draw(box);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        if (entriesOpen) {
            String filter = fitted(g, entryFilter.toString(), 600);
            if (filter.isEmpty()) {
                g.setColor(new Color(0xB9A9C2));
                g.drawString("Tippen zum Suchen, Pfeiltasten blättern", 456, 429);
            } else {
                g.setColor(INK);
                g.drawString(filter, 448, 429);
            }
            int caret = 448 + g.getFontMetrics().stringWidth(filter) + 2;
            g.setStroke(new BasicStroke(2f));
            g.setColor(PINK_DARK);
            g.drawLine(caret, 411, caret, 433);
        } else if (selectedEntry != null) {
            g.setColor(INK);
            g.drawString(fitted(g, selectedEntry.title(), 600), 448, 429);
        } else {
            g.setColor(new Color(0xB9A9C2));
            g.drawString("Eintrag wählen …", 448, 429);
        }
        // The arrow of a drop-down list, drawn so that it does not depend on the font.
        Path2D arrow = new Path2D.Double();
        double tip = entriesOpen ? 416 : 428;
        double base = entriesOpen ? 428 : 416;
        arrow.moveTo(1090, base);
        arrow.lineTo(1110, base);
        arrow.lineTo(1100, tip);
        arrow.closePath();
        g.setColor(PINK_DARK);
        g.fill(arrow);
        regions.add(new Region(box.getBounds2D(), () -> {
            if (entriesOpen) {
                closeEntries();
            } else {
                openEntries();
            }
        }));
    }

    private void paintEntryList(Graphics2D g) {
        List<KeePassEntry> visible = visibleEntries();
        int rowHeight = 28;
        Shape box = new RoundRectangle2D.Double(430, 448, 700, ENTRY_ROWS * rowHeight + 8, 18, 18);
        g.setColor(FIELD);
        g.fill(box);
        g.setStroke(new BasicStroke(2f));
        g.setColor(PINK);
        g.draw(box);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        if (visible.isEmpty()) {
            g.setColor(TEXT);
            g.drawString("Kein Eintrag passt zu „" + fitted(g, entryFilter.toString(), 300) + "“.", 450, 474);
        }
        for (int row = 0; row < ENTRY_ROWS && row + entryScroll < visible.size(); row++) {
            int index = row + entryScroll;
            KeePassEntry entry = visible.get(index);
            double y = 452 + row * rowHeight;
            boolean marked = index == entryCursor;
            if (marked) {
                g.setColor(PINK);
                g.fill(new RoundRectangle2D.Double(436, y + 1, 688, rowHeight - 2, 14, 14));
            }
            g.setColor(marked ? Color.WHITE : INK);
            g.drawString(fitted(g, entry.title(), 300), 450, (int) y + 20);
            g.setColor(marked ? Color.WHITE : new Color(0x9A88A6));
            g.drawString(fitted(g, entry.url(), 350), 770, (int) y + 20);
            regions.add(new Region(new Rectangle2D.Double(434, y, 692, rowHeight), () -> pickEntry(entry)));
        }
        if (visible.size() > ENTRY_ROWS) {
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            String range = (entryScroll + 1) + "–" + Math.min(visible.size(), entryScroll + ENTRY_ROWS) + " von " + visible.size();
            g.drawString(range, 1130 - g.getFontMetrics().stringWidth(range), 392);
        }
    }

    private void paintModel(Graphics2D g) {
        label(g, "Modell", 430, 322);
        int rowHeight = 30;
        int visible = MODEL_ROWS;
        Shape box = new RoundRectangle2D.Double(430, 332, 700, visible * rowHeight + 10, 18, 18);
        g.setColor(FIELD);
        g.fill(box);
        g.setStroke(new BasicStroke(2f));
        g.setColor(PINK);
        g.draw(box);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        for (int row = 0; row < visible && row + modelScroll < models.size(); row++) {
            int index = row + modelScroll;
            String model = models.get(index);
            double y = 337 + row * rowHeight;
            Rectangle2D area = new Rectangle2D.Double(434, y, 692, rowHeight);
            if (model.equals(selectedModel)) {
                g.setColor(PINK);
                g.fill(new RoundRectangle2D.Double(436, y + 1, 688, rowHeight - 2, 14, 14));
                g.setColor(Color.WHITE);
            } else {
                g.setColor(TEXT);
            }
            g.drawString(fitted(g, model, 600), 450, (int) y + 21);
            if (model.equalsIgnoreCase(AiSetup.PREFERRED_MODEL)) {
                g.drawString("empfohlen", 1030, (int) y + 21);
            }
            regions.add(new Region(area, () -> selectModel(index)));
        }
        if (models.size() > visible) {
            note(g, (modelScroll + 1) + "–" + Math.min(models.size(), modelScroll + visible) + " von " + models.size()
                    + "  (Pfeiltasten blättern)", 430, 576, TEXT);
        }
        if (busy == null) {
            if (models.isEmpty()) {
                primary(g, "Neu laden", this::loadModels);
            } else {
                primary(g, "Weiter", this::confirmModel);
            }
        }
        back(g, ACCESS);
    }

    private void paintWorld(Graphics2D g) {
        note(g, "Modell: " + selectedModel, 430, 322, TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        int y = 356;
        for (Map.Entry<AssetSpec, String> entry : assetStates.entrySet()) {
            String state = entry.getValue();
            g.setColor(state.startsWith("Fehler") ? BAD : state.equals("fertig") ? GOOD : TEXT);
            g.fill(new Ellipse2D.Double(432, y - 12, 12, 12));
            g.setColor(INK);
            g.drawString(displayName(entry.getKey()), 456, y);
            g.setColor(state.startsWith("Fehler") ? BAD : state.equals("fertig") ? GOOD : TEXT);
            g.drawString(fitted(g, state, 500), 630, y);
            y += 34;
        }
        if (!generating) {
            if (generatedOnce) {
                primary(g, "Spielen", this::finish);
                secondary(g, "Alles neu zeichnen", 720, () -> generate(true));
            } else {
                primary(g, "Zeichnen lassen", () -> generate(false));
                secondary(g, "Alles neu zeichnen", 720, () -> generate(true));
            }
            back(g, MODEL);
        }
    }

    private static String displayName(AssetSpec spec) {
        return switch (spec.id()) {
            case "blossom-tree" -> "Kirschbaum";
            case "green-tree" -> "Parkbaum";
            case "lamp" -> "Laterne";
            case "visitor-1" -> "Besucherin 1";
            case "visitor-2" -> "Besucher 2";
            case "visitor-3" -> "Besucher 3";
            default -> spec.id();
        };
    }

    private void paintFooter(Graphics2D g) {
        if (busy != null) {
            note(g, busy, 430, 606, PINK_DARK);
        } else if (!message.isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.setColor(messageIsError ? BAD : GOOD);
            // Where the step leaves room: below the fields, inside the empty model list, above the buttons.
            int y = step == MODEL ? (models.isEmpty() ? 362 : 592) : step == WORLD ? 580 : 540;
            wrapped(g, message, step == MODEL && models.isEmpty() ? 450 : 430, y, 680, 21);
        }
        // Leaving is always possible: the game runs with the built-in pictures.
        if (!generating) {
            button(g, generatedOnce ? "Schließen" : fromGame ? "Zurück ins Spiel" : "Ohne KI spielen", 146, 600, 250, PINK_LIGHT, INK, this::finish);
        }
    }

    // ------------------------------------------------------------------ widgets

    private void label(Graphics2D g, String text, int x, int y) {
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString(text, x, y);
    }

    private void note(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(color);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        g.drawString(fitted(g, text, 700), x, y);
    }

    /** A one-line text field; clicking it gives it the keyboard. */
    private void field(Graphics2D g, StringBuilder value, String placeholder, int x, int y, int width, boolean hidden) {
        Shape shape = new RoundRectangle2D.Double(x, y, width, 44, 20, 20);
        boolean focused = focus == value;
        g.setColor(FIELD);
        g.fill(shape);
        g.setStroke(new BasicStroke(focused ? 3.5f : 2f));
        g.setColor(focused ? PINK_DARK : PINK);
        g.draw(shape);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        FontMetrics metrics = g.getFontMetrics();
        String text = hidden ? "•".repeat(value.length()) : value.toString();
        // Show the end of a text that is too long: that is where typing happens.
        while (metrics.stringWidth(text) > width - 40 && text.length() > 1) {
            text = text.substring(1);
        }
        if (text.isEmpty() && !focused) {
            g.setColor(new Color(0xB9A9C2));
            g.drawString(placeholder, x + 18, y + 29);
        } else {
            g.setColor(INK);
            g.drawString(text, x + 18, y + 29);
            if (focused) {
                int caret = x + 18 + metrics.stringWidth(text) + 2;
                g.setStroke(new BasicStroke(2f));
                g.setColor(PINK_DARK);
                g.drawLine(caret, y + 11, caret, y + 33);
            }
        }
        regions.add(new Region(shape.getBounds2D(), () -> focus = value));
    }

    /** One of two options side by side; the chosen one is filled. */
    private void choice(Graphics2D g, String text, int x, boolean chosen, Runnable action) {
        Shape shape = new RoundRectangle2D.Double(x, 304, 146, 36, 36, 36);
        g.setColor(chosen ? PINK_DARK : PINK_LIGHT);
        g.fill(shape);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(shape);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.setColor(chosen ? Color.WHITE : INK);
        g.drawString(text, x + (146 - g.getFontMetrics().stringWidth(text)) / 2, 328);
        regions.add(new Region(shape.getBounds2D(), action));
    }

    private void primary(Graphics2D g, String text, Runnable action) {
        primaryAction = action;
        button(g, text, 960, 600, 170, PINK_DARK, Color.WHITE, action);
    }

    private void secondary(Graphics2D g, String text, int x, Runnable action) {
        button(g, text, x, 600, 950 - x, PINK_LIGHT, INK, action);
    }

    private void back(Graphics2D g, int target) {
        if (busy == null) {
            button(g, "Zurück", 430, 600, 110, PINK_LIGHT, INK, () -> {
                step = target;
                say("", false);
                focus = target == ACCESS && !useKeePass ? baseUrl : null;
            });
        }
    }

    private void button(Graphics2D g, String text, int x, int y, int width, Color fill, Color ink, Runnable action) {
        Shape shape = new RoundRectangle2D.Double(x, y, width, 46, 46, 46);
        g.setColor(fill);
        g.fill(shape);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(shape);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.setColor(ink);
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (width - textWidth) / 2, y + 29);
        regions.add(new Region(shape.getBounds2D(), action));
    }

    /** Draws text broken into lines of at most {@code width}. */
    private static void wrapped(Graphics2D g, String text, int x, int y, int width, int lineHeight) {
        FontMetrics metrics = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String paragraph : text.split("\n")) {
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && metrics.stringWidth(line + " " + word) > width) {
                    g.drawString(line.toString(), x, y);
                    y += lineHeight;
                    line.setLength(0);
                }
                line.append(line.length() > 0 ? " " : "").append(word);
            }
            g.drawString(line.toString(), x, y);
            y += lineHeight;
            line.setLength(0);
        }
    }

    /** Shortens a text with an ellipsis until it fits. */
    private static String fitted(Graphics2D g, String text, int width) {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    @Override
    public void close() {
        if (codeAnswer != null) {
            codeAnswer.complete("");
        }
        background.shutdownNow();
    }
}
