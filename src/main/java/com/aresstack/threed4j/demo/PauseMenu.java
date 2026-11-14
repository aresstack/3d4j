package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.assets.TownLibrary;
import com.aresstack.threed4j.generator.AiSetup;
import com.aresstack.threed4j.generator.BackgroundArtist;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;
import com.aresstack.threed4j.platform.MouseButton;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The pause menu (Escape while playing): continue, settings, quit. It is a card in
 * the look of the "create the world" menu, drawn over the paused game. Whatever can
 * be adjusted in the game belongs on one of the tabs of its settings page.
 *
 * <p>Like that menu it is painted with Java 2D into an image; the game shows the
 * image as one texture in the middle of the window. The card is laid out on a fixed
 * {@value #CARD_WIDTH} x {@value #CARD_HEIGHT} surface whose size relative to the
 * window follows from {@link #scale}. Outside its rounded outline the image is
 * transparent.</p>
 */
final class PauseMenu {

    static final int CARD_WIDTH = 520;
    static final int CARD_HEIGHT = 560;
    private static final int COLLECTION_WIDTH = 960;
    private static final int COLLECTION_HEIGHT = 640;
    /** Pictures per page of the collection: four in a row, two rows. */
    private static final int PICTURES_PER_PAGE = 8;

    /** The window is treated as at least this large in card units, so the card never fills it completely. */
    private static final int DESIGN_WIDTH = 1280;
    private static final int DESIGN_HEIGHT = 720;

    static final float[] RENDER_SCALES = {1f, 0.75f, 0.5f, 1f / 3f, 0.25f};
    private static final String[] RENDER_SCALE_LABELS = {"100 %", "75 %", "50 %", "33 %", "25 %"};

    private static final Color INK = new Color(0x4A2A5E);
    private static final Color TEXT = new Color(0x5C4A66);
    private static final Color PINK = new Color(0xF29BC4);
    private static final Color PINK_DARK = new Color(0xD9649E);
    private static final Color PINK_LIGHT = new Color(0xFFE3F0);
    private static final Color CARD = new Color(0xFFFCFE);

    /** What the menu asks the game to do. */
    interface Actions {
        void resume();

        void quit();

        void renderScale(float scale);

        void smoothUpscale(boolean smooth);

        /** @param speed factor on the turning of the view per pixel of mouse movement */
        void mouseSpeed(float speed);

        /**
         * How the artwork is requested from the model from now on.
         *
         * @param reasoningEffort {@code low}, {@code medium}, {@code high}, or {@code null} for the model's default
         */
        void requestOptions(int maxTokens, String reasoningEffort);

        /** Open the menu for the access to the AI service (KeePass or by hand, model). */
        void openAccessMenu();

        /** Whether building parts are drawn in the background while the game runs. */
        void backgroundDrawing(boolean enabled);

        /** Two short lines for the settings: what the background drawing does, and how far the collection is. */
        String[] backgroundStatus();

        /** Whether the game fills the screen. */
        default boolean fullscreen() {
            return false;
        }

        /** How many requests may be with the AI at the same time, for everything that asks it. */
        default int aiParallel() {
            return 1;
        }

        default void aiParallel(int parallel) {
        }

        /** Whether looking around in full screen needs a held mouse button. */
        default boolean holdToLook() {
            return false;
        }

        default void holdToLook(boolean hold) {
        }

        /** Fill the screen, or go back to a window. */
        default void fullscreen(boolean fill) {
        }

        /** The screens there are, each as x, y, width, height; see {@code Window.monitors()}. */
        default java.util.List<int[]> monitors() {
            return java.util.List.of();
        }

        /** The index of the screen the game is on. */
        default int monitor() {
            return 0;
        }

        /** Show the game on this screen. */
        default void monitor(int index) {
        }

        /** Have a further district planned now. */
        default void planDistrict() {
        }

        /** Have another landmark planned for a planned district. */
        default void planLandmark(District district) {
        }

        /** What the planning of districts is doing, in German. */
        default String plannerStatus() {
            return "";
        }
    }

    /**
     * Everything the settings show, as it is when the menu opens.
     *
     * @param reasoningEffort {@code low}, {@code medium}, {@code high}, or {@code null} for the model's default
     */
    record State(float renderScale, boolean smoothUpscale, float mouseSpeed, String reasoningEffort, int maxTokens,
                 boolean backgroundDrawing) {
    }

    private record Region(Rectangle2D area, Runnable action) {
    }

    private static final String[] TABS = {"Bild", "Steuerung", "KI", "Welt"};
    /** The range of the mouse speed's slider, and where the slider lies on the card. */
    private static final float MOUSE_SPEED_MIN = 0.1f;
    private static final float MOUSE_SPEED_MAX = 3f;
    private static final int SLIDER_LEFT = 52;
    private static final int SLIDER_RIGHT = 352;
    private static final int SLIDER_Y = 240;
    private static final Rectangle2D SPEED_BOX = new Rectangle2D.Double(378, 220, 92, 40);
    /** The slider's knob follows the mouse while the button is held. */
    private boolean draggingSpeed;
    /** What is typed into the number box; {@code null} while it is not being edited. */
    private StringBuilder speedText;
    /** Numbers of requests at the same time that are offered; any other can be typed. */
    private static final int[] PARALLEL_CHOICES = {1, 2, 4, 8, 16, 32, 64};
    private static final Rectangle2D PARALLEL_BOX = new Rectangle2D.Double(396, 418, 84, 40);
    private StringBuilder parallelText;
    private static final String[] EFFORTS = {null, "low", "medium", "high"};
    private static final String[] EFFORT_LABELS = {"Standard", "Wenig", "Mittel", "Viel"};
    private static final int[] TOKEN_LIMITS = {2048, 4096, 8192, 16384};

    private final Actions actions;
    private float renderScale;
    private boolean smoothUpscale;
    private float mouseSpeed;
    private String reasoningEffort;
    private int maxTokens;
    private boolean backgroundDrawing;
    private String[] shownStatus = {"", ""};
    private boolean settingsPage;
    private int tab;
    // The page that shows the district's collection of pictures.
    /**
     * One collection the page can show.
     *
     * @param district whose names the kinds carry; the shared collection borrows those of the first district
     */
    private record Shelf(String name, PartLibrary library, District district, List<PartLibrary.Kind> kinds) {
    }

    /** The town on disk; {@code null} if the menu was given a single collection. */
    private TownLibrary town;
    // The page of the town hall: the districts, and how the town grows.
    private boolean townHallPage;
    private int districtsFrom;
    private String demolishAsked;
    private String shownTown = "";
    private String shownPlanner = "";
    private final List<Shelf> shelves = new ArrayList<>();
    private int shelf;
    private boolean shelfList;
    private boolean collectionPage;
    private PartLibrary.Kind shownKind = PartLibrary.Kind.BLOSSOM_TREE;
    private int shownFrom;
    private String shownSignature = "";
    private long signatureCheckedAt;
    private final java.util.Map<java.nio.file.Path, BufferedImage> thumbnails = new java.util.HashMap<>();
    // The list to choose the model that draws the shown kind.
    private boolean modelList;
    private int modelsFrom;
    /** The models the service offers; {@code null} until asked, empty while the answer is on its way. */
    private volatile List<String> offeredModels;
    private volatile boolean modelsArrived;
    /** Index of the button under the mouse or chosen with the arrow keys; -1 for none. */
    private int marked = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private final List<Region> regions = new ArrayList<>();
    private boolean dirty = true;

    /**
     * @param library      the collection of pictures of the district the player is in
     * @param districtName what the player reads as the district's name
     */
    PauseMenu(Actions actions, State state, PartLibrary library, String districtName) {
        this(actions, state);
        shelves.add(new Shelf(districtName, library, District.BLOSSOM_QUARTER, List.of(PartLibrary.Kind.values())));
    }

    /**
     * @param town    the collections of all districts and the one they share
     * @param current the district the player is in; its collection is shown first
     */
    PauseMenu(Actions actions, State state, TownLibrary town, District current) {
        this(actions, state);
        this.town = town;
        fillShelves();
        showShelf(Math.max(0, town.districts().indexOf(current)));
    }

    /** The collections of the town as it is now: one per district, and the shared one. */
    private void fillShelves() {
        shelves.clear();
        for (District district : town.districts()) {
            shelves.add(new Shelf(district.name(), town.district(district), district, TownLibrary.districtKinds()));
        }
        shelves.add(new Shelf(TownLibrary.SHARED_NAME, town.shared(), District.ALL.get(0), TownLibrary.sharedKinds()));
    }

    /** Reads the town anew, after a district was planned or torn down; the shown collection stays if it still exists. */
    private void reloadTown() {
        String shownName = shelves.get(shelf).name();
        PartLibrary.Kind kind = shownKind;
        town = town.reload();
        fillShelves();
        int index = 0;
        for (int i = 0; i < shelves.size(); i++) {
            if (shelves.get(i).name().equals(shownName)) {
                index = i;
            }
        }
        showShelf(index);
        if (shelves.get(index).name().equals(shownName)) {
            shownKind = kind;
        }
    }

    private void showShelf(int index) {
        shelf = index;
        shownKind = shelves.get(index).kinds().get(0);
        shownFrom = 0;
        shelfList = false;
        modelList = false;
    }

    private PartLibrary library() {
        return shelves.get(shelf).library();
    }

    private PauseMenu(Actions actions, State state) {
        this.actions = actions;
        this.renderScale = state.renderScale();
        this.smoothUpscale = state.smoothUpscale();
        this.mouseSpeed = state.mouseSpeed();
        this.reasoningEffort = state.reasoningEffort();
        this.maxTokens = state.maxTokens();
        this.backgroundDrawing = state.backgroundDrawing();
    }

    /** {@code true} if the image has to be painted again; resets the flag. */
    boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    /** The card's size in card units; the page with the collection needs more room than the others. */
    int cardWidth() {
        return collectionPage || townHallPage ? COLLECTION_WIDTH : CARD_WIDTH;
    }

    int cardHeight() {
        return collectionPage || townHallPage ? COLLECTION_HEIGHT : CARD_HEIGHT;
    }

    /** Window pixels per card unit: the card keeps its share of a 1280 x 720 window at any window size. */
    static double scale(int windowWidth, int windowHeight) {
        return Math.min((double) windowWidth / DESIGN_WIDTH, (double) windowHeight / DESIGN_HEIGHT);
    }

    // ------------------------------------------------------------------- input

    void update(Input input, int windowWidth, int windowHeight) {
        if (modelsArrived) {
            modelsArrived = false;
            dirty = true;
        }
        if (input.wasPressed(Key.ESCAPE)) {
            if (modelList || shelfList) {
                modelList = false;
                shelfList = false;
                dirty = true;
            } else if (townHallPage) {
                showTownHall(false);
            } else if (collectionPage) {
                showCollection(false);
            } else if (settingsPage) {
                showPage(false);
            } else {
                actions.resume();
            }
            return;
        }
        if (collectionPage && System.nanoTime() - signatureCheckedAt > 1_000_000_000L) {
            signatureCheckedAt = System.nanoTime();
            if (!library().signature().equals(shownSignature)) {
                dirty = true; // a picture arrived or left while the page is open
            }
        }
        if (townHallPage && System.nanoTime() - signatureCheckedAt > 1_000_000_000L) {
            signatureCheckedAt = System.nanoTime();
            // A district was planned, got a landmark or pictures, or the planning has moved on.
            if (!town.reload().signature().equals(shownTown) || !actions.plannerStatus().equals(shownPlanner)) {
                reloadTown();
                dirty = true;
            }
        }
        if (regions.isEmpty()) {
            return; // not painted yet
        }
        if (settingsPage && tab == 3 && !java.util.Arrays.equals(shownStatus, actions.backgroundStatus())) {
            dirty = true; // the background drawing has moved on
        }
        double scale = scale(windowWidth, windowHeight);
        double x = (input.mouseX() - (windowWidth - cardWidth() * scale) / 2) / scale;
        double y = (input.mouseY() - (windowHeight - cardHeight() * scale) / 2) / scale;
        int under = -1;
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).area().contains(x, y)) {
                under = i;
            }
        }
        // Only a moved mouse marks, so that it does not take the mark away from the arrow keys.
        if (input.mouseX() != lastMouseX || input.mouseY() != lastMouseY) {
            lastMouseX = input.mouseX();
            lastMouseY = input.mouseY();
            mark(under);
        }
        if (!settingsPage && !collectionPage && !townHallPage) {
            // The three buttons of the first page can be reached without the mouse.
            if (input.wasPressed(Key.DOWN)) {
                mark(Math.min(regions.size() - 1, marked + 1));
            }
            if (input.wasPressed(Key.UP)) {
                mark(Math.max(0, marked - 1));
            }
            if (input.wasPressed(Key.ENTER) && marked >= 0 && marked < regions.size()) {
                run(regions.get(marked));
                return;
            }
        }
        if (settingsPage && tab == 1) {
            updateMouseSpeed(input, x, y);
        }
        if (settingsPage && tab == 2) {
            updateParallel(input, x, y);
        }
        if (input.wasPressed(MouseButton.LEFT) && under >= 0) {
            run(regions.get(under));
        }
    }

    /** The number box of the requests at the same time: clicking, typing, Enter. */
    private void updateParallel(Input input, double x, double y) {
        if (input.wasPressed(MouseButton.LEFT)) {
            if (parallelText != null && !PARALLEL_BOX.contains(x, y)) {
                applyParallelText(); // a click elsewhere takes what was typed
            } else if (PARALLEL_BOX.contains(x, y) && parallelText == null) {
                parallelText = new StringBuilder();
                dirty = true;
            }
        }
        if (parallelText != null) {
            for (char character : input.typedText().toCharArray()) {
                if (character == '\r' || character == '\t') {
                    applyParallelText();
                    break;
                } else if (character == '\b') {
                    if (parallelText.length() > 0) {
                        parallelText.setLength(parallelText.length() - 1);
                    }
                    dirty = true;
                } else if (Character.isDigit(character) && parallelText.length() < 4) {
                    parallelText.append(character);
                    dirty = true;
                }
            }
        }
    }

    private void applyParallelText() {
        try {
            actions.aiParallel(Integer.parseInt(parallelText.toString()));
        } catch (NumberFormatException e) {
            // nothing usable was typed: the number stays
        }
        parallelText = null;
        dirty = true;
    }

    /** The slider and the number box of the mouse speed: dragging, clicking, typing. */
    private void updateMouseSpeed(Input input, double x, double y) {
        boolean onSlider = x >= SLIDER_LEFT - 16 && x <= SLIDER_RIGHT + 16 && Math.abs(y - SLIDER_Y) <= 18;
        if (input.wasPressed(MouseButton.LEFT)) {
            if (speedText != null && !SPEED_BOX.contains(x, y)) {
                applySpeedText(); // a click elsewhere takes what was typed
            }
            draggingSpeed = onSlider; // a press elsewhere ends a drag whose release was missed
            if (onSlider) {
                dirty = true;
            } else if (SPEED_BOX.contains(x, y) && speedText == null) {
                speedText = new StringBuilder();
                dirty = true;
            }
        }
        if (draggingSpeed) {
            if (input.isDown(MouseButton.LEFT)) {
                double share = (Math.max(SLIDER_LEFT, Math.min(SLIDER_RIGHT, x)) - SLIDER_LEFT) / (SLIDER_RIGHT - SLIDER_LEFT);
                // In steps of 0.05, so that the number is a round one.
                float speed = Math.round((MOUSE_SPEED_MIN + share * (MOUSE_SPEED_MAX - MOUSE_SPEED_MIN)) * 20) / 20f;
                setMouseSpeed(speed);
            } else {
                draggingSpeed = false;
                dirty = true;
            }
        }
        if (speedText != null) {
            for (char character : input.typedText().toCharArray()) {
                if (character == '\r' || character == '\t') {
                    applySpeedText();
                    break;
                } else if (character == '\b') {
                    if (speedText.length() > 0) {
                        speedText.setLength(speedText.length() - 1);
                    }
                    dirty = true;
                } else if ((Character.isDigit(character) || character == ',' || character == '.') && speedText.length() < 4) {
                    speedText.append(character);
                    dirty = true;
                }
            }
        }
    }

    private void applySpeedText() {
        try {
            setMouseSpeed(Float.parseFloat(speedText.toString().replace(',', '.')));
        } catch (NumberFormatException e) {
            // nothing usable was typed: the speed stays
        }
        speedText = null;
        dirty = true;
    }

    private void setMouseSpeed(float speed) {
        float clamped = Math.max(MOUSE_SPEED_MIN, Math.min(MOUSE_SPEED_MAX, speed));
        if (Math.abs(clamped - mouseSpeed) > 0.0001f) {
            mouseSpeed = clamped;
            actions.mouseSpeed(clamped);
            dirty = true;
        }
    }

    private void run(Region region) {
        region.action().run();
        dirty = true;
    }

    private void mark(int index) {
        if (index != marked) {
            marked = index;
            dirty = true;
        }
    }

    private void showCollection(boolean show) {
        collectionPage = show;
        modelList = false;
        shelfList = false;
        marked = -1;
        regions.clear();
        dirty = true;
    }

    /** Something the menu shows was changed from outside, e.g. full screen by Alt+Enter. */
    void changed() {
        dirty = true;
    }

    private void showTownHall(boolean show) {
        townHallPage = show;
        modelList = false;
        demolishAsked = null;
        marked = -1;
        regions.clear();
        dirty = true;
        if (show) {
            reloadTown();
        }
    }

    private void showPage(boolean settings) {
        settingsPage = settings;
        marked = -1;
        regions.clear();
        dirty = true;
    }

    // ------------------------------------------------------------------- paint

    /** Paints the card for a window of the given size; the image is as large as the card appears. */
    BufferedImage paint(int windowWidth, int windowHeight) {
        double scale = scale(windowWidth, windowHeight);
        int cardWidth = cardWidth();
        int cardHeight = cardHeight();
        int width = Math.max(1, (int) Math.round(cardWidth * scale));
        int height = Math.max(1, (int) Math.round(cardHeight * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale((double) width / cardWidth, (double) height / cardHeight);

        // A soft shadow, then the card with its pink frame; everything else stays transparent.
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(74, 42, 94, 14));
            g.fill(new RoundRectangle2D.Double(10 - i, 14 - i, cardWidth - 14 + 2 * i, cardHeight - 16 + 2 * i,
                    52 + 2 * i, 52 + 2 * i));
        }
        Shape card = new RoundRectangle2D.Double(8, 6, cardWidth - 20, cardHeight - 20, 48, 48);
        g.setColor(CARD);
        g.fill(card);
        g.setStroke(new BasicStroke(5f));
        g.setColor(PINK);
        g.draw(card);
        SetupScreen.blossom(g, 44, 46, 30);
        SetupScreen.blossom(g, cardWidth - 60, cardHeight - 44, 30);
        SetupScreen.blossom(g, cardWidth - 52, 44, 17);
        SetupScreen.blossom(g, 42, cardHeight - 40, 19);

        regions.clear();
        if (townHallPage) {
            paintTownHall(g);
        } else if (collectionPage) {
            paintCollection(g);
        } else if (settingsPage) {
            paintSettings(g);
        } else {
            paintMain(g);
        }
        g.dispose();
        return image;
    }

    private void paintMain(Graphics2D g) {
        title(g, "Pause");
        button(g, "Weiterspielen", 90, 180, 340, 58, actions::resume);
        button(g, "Einstellungen", 90, 262, 340, 58, () -> showPage(true));
        button(g, "Spiel beenden", 90, 344, 340, 58, actions::quit);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        centered(g, "Esc: weiterspielen", 470);
    }

    /** The settings: one tab per subject, so that new settings find room. */
    private void paintSettings(Graphics2D g) {
        title(g, "Einstellungen");
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            choice(g, TABS[i], 40 + i * 112, 126, 106, tab == i, () -> {
                tab = index;
                marked = -1;
            });
        }
        switch (tab) {
            case 0 -> paintPictureTab(g);
            case 1 -> paintControlsTab(g);
            case 2 -> paintAiTab(g);
            default -> paintWorldTab(g);
        }
        button(g, "Zurück", 40, 478, 150, 50, () -> showPage(false));
    }

    private void paintPictureTab(Graphics2D g) {
        label(g, "Auflösung", 206);
        for (int i = 0; i < RENDER_SCALES.length; i++) {
            float scale = RENDER_SCALES[i];
            choice(g, RENDER_SCALE_LABELS[i], 40 + i * 89, 218, 84, Math.abs(scale - renderScale) < 0.01f, () -> {
                renderScale = scale;
                actions.renderScale(scale);
            });
        }
        note(g, "Weniger Prozent: unschärfer, aber mehr Bilder pro Sekunde.", 284);

        label(g, "Hochskalieren", 330);
        choice(g, "Weich", 40, 342, 150, smoothUpscale, () -> {
            smoothUpscale = true;
            actions.smoothUpscale(true);
        });
        choice(g, "Pixelig", 200, 342, 150, !smoothUpscale, () -> {
            smoothUpscale = false;
            actions.smoothUpscale(false);
        });

        // Also switched with Alt+Enter. In full screen the mouse looks around without a button held.
        boolean fills = actions.fullscreen();
        label(g, "Anzeige", 410);
        choice(g, "Vollbild", 40, 422, 106, fills, () -> actions.fullscreen(true));
        choice(g, "Fenster", 152, 422, 106, !fills, () -> actions.fullscreen(false));

        // Which screen: numbered from left to right; the chosen one's size beside the label.
        java.util.List<int[]> screens = actions.monitors();
        if (screens.size() > 1) {
            int on = Math.max(0, Math.min(screens.size() - 1, actions.monitor()));
            label(g, "Bildschirm", 284, 410, 16);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(TEXT);
            g.drawString(screens.get(on)[2] + " × " + screens.get(on)[3], 376, 410);
            for (int i = 0; i < Math.min(4, screens.size()); i++) {
                int index = i;
                choice(g, String.valueOf(i + 1), 284 + i * 50, 422, 44, i == on, () -> actions.monitor(index));
            }
        }
    }

    private void paintControlsTab(Graphics2D g) {
        label(g, "Mausgeschwindigkeit beim Umsehen", 206);
        // The slider: a track, filled up to the knob.
        double share = (Math.max(MOUSE_SPEED_MIN, Math.min(MOUSE_SPEED_MAX, mouseSpeed)) - MOUSE_SPEED_MIN)
                / (MOUSE_SPEED_MAX - MOUSE_SPEED_MIN);
        double knob = SLIDER_LEFT + share * (SLIDER_RIGHT - SLIDER_LEFT);
        Shape track = new RoundRectangle2D.Double(SLIDER_LEFT - 8, SLIDER_Y - 7, SLIDER_RIGHT - SLIDER_LEFT + 16, 14, 14, 14);
        g.setColor(PINK_LIGHT);
        g.fill(track);
        g.setColor(PINK_DARK);
        g.fill(new RoundRectangle2D.Double(SLIDER_LEFT - 8, SLIDER_Y - 7, knob - SLIDER_LEFT + 16, 14, 14, 14));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(track);
        Shape handle = new java.awt.geom.Ellipse2D.Double(knob - 14, SLIDER_Y - 14, 28, 28);
        g.setColor(draggingSpeed ? PINK_DARK : Color.WHITE);
        g.fill(handle);
        g.setColor(PINK_DARK);
        g.draw(handle);
        // The number beside it; a click lets it be typed.
        Shape box = new RoundRectangle2D.Double(SPEED_BOX.getX(), SPEED_BOX.getY(), SPEED_BOX.getWidth(),
                SPEED_BOX.getHeight(), 16, 16);
        g.setColor(Color.WHITE);
        g.fill(box);
        g.setStroke(new BasicStroke(speedText != null ? 3.5f : 2.5f));
        g.setColor(PINK_DARK);
        g.draw(box);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        String number = speedText != null ? speedText + "|" : String.format(java.util.Locale.GERMAN, "%.2f", mouseSpeed);
        g.drawString(number, (int) (SPEED_BOX.getCenterX() - g.getFontMetrics().stringWidth(number) / 2.0),
                (int) SPEED_BOX.getCenterY() + 6);
        note(g, "1,00 ist die Vorgabe. Zahl anklicken, tippen, Enter.", 290);
        label(g, "Umsehen im Vollbild", 326);
        boolean hold = actions.holdToLook();
        choice(g, "Maus bewegen", 40, 338, 170, !hold, () -> actions.holdToLook(false));
        choice(g, "Maustaste halten", 222, 338, 190, hold, () -> actions.holdToLook(true));
        note(g, "Im Fenster immer Maustaste halten und ziehen. Die Pfeiltasten", 404);
        note(g, "gehen immer. Loslassen und neu greifen dreht von dort weiter.", 424);
        note(g, "Gehen: W A S D, schneller mit Umschalt, E und Q hoch und runter.", 456);
    }

    private void paintAiTab(Graphics2D g) {
        label(g, "Nachdenken vor dem Zeichnen", 202);
        for (int i = 0; i < EFFORTS.length; i++) {
            String effort = EFFORTS[i];
            choice(g, EFFORT_LABELS[i], 40 + i * 112, 214, 106, java.util.Objects.equals(effort, reasoningEffort), () -> {
                reasoningEffort = effort;
                actions.requestOptions(maxTokens, reasoningEffort);
            });
        }
        note(g, "Mehr Nachdenken dauert länger. Standard: Modell entscheidet.", 274);

        label(g, "Antwortlänge in Tokens", 304);
        for (int i = 0; i < TOKEN_LIMITS.length; i++) {
            int limit = TOKEN_LIMITS[i];
            choice(g, String.valueOf(limit), 40 + i * 112, 316, 106, limit == maxTokens, () -> {
                maxTokens = limit;
                actions.requestOptions(maxTokens, reasoningEffort);
            });
        }
        note(g, "Nachdenken zählt mit. Bei abgeschnittenen Antworten erhöhen.", 376);

        // For everything that asks the AI: drawing and planning alike. Usual numbers to pick, any other to type.
        int parallel = actions.aiParallel();
        label(g, "Anfragen gleichzeitig", 406);
        for (int i = 0; i < PARALLEL_CHOICES.length; i++) {
            int count = PARALLEL_CHOICES[i];
            choice(g, String.valueOf(count), 40 + i * 50, 418, 44, 40, count == parallel, 15,
                    () -> actions.aiParallel(count));
        }
        Shape box = new RoundRectangle2D.Double(PARALLEL_BOX.getX(), PARALLEL_BOX.getY(), PARALLEL_BOX.getWidth(),
                PARALLEL_BOX.getHeight(), 16, 16);
        g.setColor(Color.WHITE);
        g.fill(box);
        g.setColor(PINK_DARK);
        g.setStroke(new BasicStroke(parallelText != null ? 3.5f : 2.5f));
        g.draw(box);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        String number = parallelText != null ? parallelText + "|" : String.valueOf(parallel);
        g.drawString(number, (int) (PARALLEL_BOX.getCenterX() - g.getFontMetrics().stringWidth(number) / 2.0),
                (int) PARALLEL_BOX.getCenterY() + 6);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Gilt für alles: Zeichnen und Planen.", 204, 498);
        g.drawString("Andere Zahl: Feld anklicken, tippen.", 204, 516);
    }

    private void paintWorldTab(Graphics2D g) {
        label(g, "Häuser im Hintergrund weiterzeichnen", 206);
        choice(g, "An", 40, 218, 106, backgroundDrawing, () -> {
            backgroundDrawing = true;
            actions.backgroundDrawing(true);
        });
        choice(g, "Aus", 152, 218, 106, !backgroundDrawing, () -> {
            backgroundDrawing = false;
            actions.backgroundDrawing(false);
        });
        note(g, "Die KI zeichnet beim Spielen Fenster, Türen und Läden;", 284);
        note(g, "die Häuser bekommen sie nach und nach.", 304);
        shownStatus = actions.backgroundStatus();
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(fitted(g, shownStatus[1], 440), 40, 340);
        g.drawString(fitted(g, "Stand: " + shownStatus[0], 440), 40, 362);

        if (town == null) {
            button(g, "Sammlung ansehen …", 40, 380, 440, 42, () -> showCollection(true));
        } else {
            button(g, "Sammlung ansehen …", 40, 380, 228, 42, () -> showCollection(true));
            button(g, "Stadtverwaltung …", 276, 380, 204, 42, () -> showTownHall(true));
        }
        button(g, "KI-Zugang …", 40, 428, 440, 42, actions::openAccessMenu);
    }

    // ------------------------------------------------------------ the town hall

    private static final int DISTRICTS_PER_PAGE = 7;

    /**
     * The town hall: the districts of the town, each with its landmark and how far
     * its collection is, and how the town grows. A planned district can get another
     * landmark or be torn down; the two that come with the game stay.
     */
    private void paintTownHall(Graphics2D g) {
        shownTown = town.signature();
        shownPlanner = actions.plannerStatus();
        g.setColor(INK);
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34));
        g.drawString("Stadtverwaltung", 84, 72);

        List<District> districts = town.districts();
        districtsFrom = Math.max(0, Math.min(districtsFrom, (districts.size() - 1) / DISTRICTS_PER_PAGE * DISTRICTS_PER_PAGE));
        for (int i = 0; i < DISTRICTS_PER_PAGE && districtsFrom + i < districts.size(); i++) {
            District district = districts.get(districtsFrom + i);
            int top = 96 + i * 50;
            g.setColor(PINK_LIGHT);
            g.fill(new RoundRectangle2D.Double(40, top, 880, 44, 18, 18));
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.drawString(fitted(g, district.name(), 200), 56, top + 20);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String planner = district.builtIn() ? "gehört zum Spiel" : "geplant von "
                    + (district.layout().plannedBy().isBlank() ? "Hand" : shortName(district.layout().plannedBy()));
            g.drawString(fitted(g, planner, 200), 56, top + 37);
            District.Landmark landmark = district.builtIn() ? null : district.layout().landmark();
            String landmarkName = district == District.BLOSSOM_QUARTER ? "Brunnen" : district == District.WORKS_QUARTER
                    ? "Schornstein, Wasserturm, Gasometer" : landmark == null ? "noch keines" : landmark.name();
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString(fitted(g, "Wahrzeichen: " + landmarkName, 250), 270, top + 20);
            int[] progress = town.progress(district);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("Sammlung: " + progress[0] + " von " + progress[1] + " Bildern", 270, top + 37);

            int index = districtsFrom + i;
            choice(g, "Sammlung", 534, top + 8, 100, 28, false, 13, () -> {
                showTownHall(false);
                showCollection(true);
                showShelf(index);
            });
            if (!district.builtIn()) {
                choice(g, "Wahrzeichen neu", 640, top + 8, 144, 28, false, 13, () -> {
                    demolishAsked = null;
                    actions.planLandmark(district);
                });
                // Tearing down asks once more: the second click on the same button does it.
                boolean asked = district.id().equals(demolishAsked);
                choice(g, asked ? "Wirklich?" : "Abreißen", 790, top + 8, 118, 28, asked, 13, () -> {
                    if (asked) {
                        demolish(district);
                    } else {
                        demolishAsked = district.id();
                    }
                });
            }
        }
        int pages = Math.max(1, (districts.size() + DISTRICTS_PER_PAGE - 1) / DISTRICTS_PER_PAGE);
        if (pages > 1) {
            choice(g, "‹", 790, 448, 40, 30, false, 16, () -> districtsFrom = Math.max(0, districtsFrom - DISTRICTS_PER_PAGE));
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            String text = (districtsFrom / DISTRICTS_PER_PAGE + 1) + "/" + pages;
            g.drawString(text, 855 - g.getFontMetrics().stringWidth(text) / 2, 469);
            choice(g, "›", 880, 448, 40, 30, false, 16, () -> {
                if (districtsFrom + DISTRICTS_PER_PAGE < districts.size()) {
                    districtsFrom += DISTRICTS_PER_PAGE;
                }
            });
        }

        // How the town grows.
        boolean grows = AiSetup.worldEnabled();
        int most = AiSetup.worldMaxDistricts();
        String planning = AiSetup.worldModel();
        label(g, "Die Stadt wächst von selbst", 40, 506, 16);
        choice(g, "An", 262, 484, 70, 34, grows, 15, () -> saveWorld(true, planning, most));
        choice(g, "Aus", 338, 484, 70, 34, !grows, 15, () -> saveWorld(false, planning, most));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(TEXT);
        g.drawString("bis", 432, 506);
        choice(g, "–", 462, 484, 38, 34, false, 17, () -> saveWorld(grows, planning, most - 1));
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        String number = String.valueOf(most);
        g.drawString(number, 524 - g.getFontMetrics().stringWidth(number) / 2, 507);
        choice(g, "+", 548, 484, 38, 34, false, 17, () -> saveWorld(grows, planning, most + 1));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(TEXT);
        g.drawString("Viertel", 596, 506);
        int modelX = 700;
        int modelWidth = 220;
        choice(g, "", modelX, 485, modelWidth, 32, modelList, 13, () -> openModelList(!modelList));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(modelList ? Color.WHITE : INK);
        String shownModel = fitted(g, "Planung: " + shortName(planning), modelWidth - 44) + "  ▾";
        g.drawString(shownModel, modelX + (modelWidth - g.getFontMetrics().stringWidth(shownModel)) / 2, 506);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Ein neues Viertel wird geplant, sobald das jüngste alle Bilder seiner Sammlung hat.", 40, 540);

        button(g, "Zurück", 40, 568, 150, 46, () -> showTownHall(false));
        button(g, "Neues Viertel planen", 206, 568, 250, 46, actions::planDistrict);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(fitted(g, "Planung: " + (shownPlanner.isBlank() ? "ruht" : shownPlanner), 450), 472, 597);

        if (modelList) {
            // Above the button, so that the list stays on the card.
            paintModelList(g, modelX - 40, 150, planning, model -> saveWorld(grows, model, most));
        }
    }

    private void saveWorld(boolean grows, String model, int most) {
        try {
            AiSetup.saveWorld(grows, model, most);
        } catch (java.io.IOException e) {
            System.err.println("Settings not saved: " + e.getMessage());
        }
    }

    /** Tears the district down: its plan and pictures move to the folder "demolished". */
    private void demolish(District district) {
        try {
            town.demolish(district);
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("District not torn down: " + e.getMessage());
        }
        demolishAsked = null;
        reloadTown();
    }

    // ---------------------------------------------------------- the collection

    /**
     * The district's pictures: the kinds on the left with how many there are of
     * each, on the right the pictures of the chosen kind. A picture can be taken
     * out, the limit of a kind raised or lowered.
     */
    private void paintCollection(Graphics2D g) {
        shownSignature = library().signature();
        g.setColor(INK);
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34));
        g.drawString("Sammlung ·", 84, 72);
        int titleEnd = 84 + g.getFontMetrics().stringWidth("Sammlung · ");
        Shelf shown = shelves.get(shelf);
        PartLibrary library = shown.library();
        // Which collection: a district's, or the one of the whole town. A button if there is a choice.
        int shelfWidth = 0;
        if (shelves.size() > 1) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            shelfWidth = g.getFontMetrics().stringWidth(shown.name() + "  ▾") + 44;
            choice(g, shown.name() + "  ▾", titleEnd, 42, shelfWidth, 40, shelfList, 20, () -> {
                shelfList = !shelfList;
                modelList = false;
            });
        } else {
            g.drawString(shown.name(), titleEnd, 72);
        }

        // Kinds
        int y = 96;
        for (PartLibrary.Kind kind : shown.kinds()) {
            int have = library.parts(kind).size();
            String text = BackgroundArtist.plural(shown.district(), kind) + "  " + have + "/" + library.target(kind);
            PartLibrary.Kind chosen = kind;
            choice(g, text, 40, y, 250, 38, kind == shownKind, 15, () -> {
                shownKind = chosen;
                shownFrom = 0;
                modelList = false;
            });
            y += 43;
        }

        // The limit of the chosen kind
        int limit = library.target(shownKind);
        label(g, BackgroundArtist.plural(shown.district(), shownKind), 330, 116, 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        int labelEnd = 330 + g.getFontMetrics().stringWidth(BackgroundArtist.plural(shown.district(), shownKind));
        if (shownKind.uniform()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.setColor(TEXT);
            g.drawString("Im Stadtteil steht überall dieselbe: die gewählte.", 330, 566);
        }
        // Which model draws this kind, in the middle between the name and the limit.
        String kindModel = modelOf(shownKind);
        int modelWidth = 190;
        int modelX = (labelEnd + 690 - modelWidth) / 2;
        choice(g, "", modelX, 94, modelWidth, 32, modelList, 13, () -> openModelList(!modelList));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(modelList ? Color.WHITE : INK);
        String shownModel = fitted(g, "KI: " + shortName(kindModel), modelWidth - 44);
        g.drawString(shownModel + "  ▾", modelX + (modelWidth - g.getFontMetrics().stringWidth(shownModel + "  ▾")) / 2, 115);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.setColor(TEXT);
        g.drawString("höchstens", 690, 116);
        choice(g, "–", 770, 92, 40, 36, false, 18, () -> setLimit(limit - 1));
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        String number = String.valueOf(limit);
        g.drawString(number, 838 - g.getFontMetrics().stringWidth(number) / 2, 117);
        choice(g, "+", 866, 92, 40, 36, false, 18, () -> setLimit(limit + 1));

        // Pictures
        java.util.List<PartLibrary.Part> parts = library.parts(shownKind);
        PartLibrary.Part inUse = shownKind.uniform() ? library.chosen(shownKind) : null;
        if (shownFrom >= parts.size()) {
            shownFrom = Math.max(0, (parts.size() - 1) / PICTURES_PER_PAGE * PICTURES_PER_PAGE);
        }
        if (parts.isEmpty()) {
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.drawString("Noch kein Bild. Bis eines gezeichnet ist, zeigt das Spiel das eingebaute.", 330, 170);
        }
        for (int i = 0; i < PICTURES_PER_PAGE && shownFrom + i < parts.size(); i++) {
            PartLibrary.Part part = parts.get(shownFrom + i);
            int x = 330 + (i % 4) * 146;
            int top = 136 + (i / 4) * 212;
            Shape frame = new RoundRectangle2D.Double(x, top, 134, 150, 16, 16);
            g.setColor(PINK_LIGHT);
            g.fill(frame);
            BufferedImage picture = thumbnail(part);
            if (picture != null) {
                double fit = Math.min(122.0 / picture.getWidth(), 138.0 / picture.getHeight());
                int w = (int) Math.round(picture.getWidth() * fit);
                int h = (int) Math.round(picture.getHeight() * fit);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(picture, x + (134 - w) / 2, top + (150 - h) / 2, w, h, null);
            }
            g.setStroke(new BasicStroke(2f));
            g.setColor(PINK);
            g.draw(frame);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            com.google.gson.JsonObject info = library.info(part);
            String model = info.has("model") && !info.get("model").getAsString().isBlank()
                    ? info.get("model").getAsString() : "von Hand";
            g.drawString(fitted(g, "Nr. " + part.number() + " · " + model.substring(model.lastIndexOf('/') + 1), 134),
                    x, top + 166);
            if (shownKind.uniform()) {
                // One of them is the district's; another can be put in its place.
                boolean used = part.equals(inUse);
                choice(g, used ? "In Gebrauch" : "Verwenden", x, top + 172, 78, 26, used, 11, () -> use(part));
                choice(g, "Löschen", x + 82, top + 172, 52, 26, false, 11, () -> delete(part));
            } else {
                choice(g, "Löschen", x, top + 172, 134, 26, false, 13, () -> delete(part));
            }
        }

        // Pages, the way back, and what the background drawing is doing
        int pages = Math.max(1, (parts.size() + PICTURES_PER_PAGE - 1) / PICTURES_PER_PAGE);
        if (pages > 1) {
            int page = shownFrom / PICTURES_PER_PAGE + 1;
            choice(g, "‹", 770, 574, 40, 36, false, 18, () -> shownFrom = Math.max(0, shownFrom - PICTURES_PER_PAGE));
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            String text = page + "/" + pages;
            g.drawString(text, 838 - g.getFontMetrics().stringWidth(text) / 2, 598);
            choice(g, "›", 866, 574, 40, 36, false, 18, () -> {
                if (shownFrom + PICTURES_PER_PAGE < parts.size()) {
                    shownFrom += PICTURES_PER_PAGE;
                }
            });
        }
        button(g, "Zurück", 40, 568, 150, 46, () -> showCollection(false));
        shownStatus = actions.backgroundStatus();
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(fitted(g, "Im Hintergrund: " + shownStatus[0], 420), 330, 598);

        if (modelList) {
            paintModelList(g, modelX, 130, kindModel, this::chooseModel);
        }
        if (shelfList) {
            paintShelfList(g, titleEnd, shelfWidth);
        }
    }

    /** The list below the collection's name: the districts and the whole town. Painted last, it lies on top. */
    private void paintShelfList(Graphics2D g, int left, int buttonWidth) {
        regions.add(new Region(new Rectangle2D.Double(0, 0, COLLECTION_WIDTH, COLLECTION_HEIGHT), () -> shelfList = false));
        int width = Math.max(buttonWidth, 230);
        int height = 12 + shelves.size() * 36;
        Shape panel = new RoundRectangle2D.Double(left, 86, width, height, 22, 22);
        g.setColor(CARD);
        g.fill(panel);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(panel);
        regions.add(new Region(new Rectangle2D.Double(left, 86, width, height), () -> { }));
        for (int i = 0; i < shelves.size(); i++) {
            int top = 93 + i * 36;
            if (i == shelf) {
                g.setColor(PINK_LIGHT);
                g.fill(new RoundRectangle2D.Double(left + 6, top, width - 12, 32, 32, 32));
            }
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, i == shelf ? Font.BOLD : Font.PLAIN, 16));
            g.drawString(shelves.get(i).name(), left + 20, top + 22);
            int index = i;
            regions.add(new Region(new Rectangle2D.Double(left + 6, top, width - 12, 32), () -> showShelf(index)));
        }
    }

    private static final int MODELS_PER_PAGE = 9;

    /** The list below the model's button: the models of the service, a page at a time. Painted last, it lies on top. */
    private void paintModelList(Graphics2D g, int x, int panelTop, String current,
                                java.util.function.Consumer<String> choose) {
        // A click beside the list closes it and does nothing else.
        regions.add(new Region(new Rectangle2D.Double(0, 0, COLLECTION_WIDTH, COLLECTION_HEIGHT), () -> modelList = false));
        List<String> offered = offeredModels;
        List<String> models = new ArrayList<>();
        if (offered != null) {
            models.addAll(offered);
        }
        if (!models.contains(current)) {
            models.add(0, current);
        }
        modelsFrom = Math.max(0, Math.min(modelsFrom, (models.size() - 1) / MODELS_PER_PAGE * MODELS_PER_PAGE));
        int shown = Math.min(MODELS_PER_PAGE, models.size() - modelsFrom);
        boolean pages = models.size() > MODELS_PER_PAGE;
        boolean waiting = offered != null && offered.isEmpty();
        int width = 300;
        int left = x - (width - 190) / 2;
        int height = 12 + shown * 32 + (pages || waiting ? 34 : 0);
        Shape panel = new RoundRectangle2D.Double(left, panelTop, width, height, 22, 22);
        g.setColor(CARD);
        g.fill(panel);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(panel);
        regions.add(new Region(new Rectangle2D.Double(left, panelTop, width, height), () -> { }));
        for (int i = 0; i < shown; i++) {
            String model = models.get(modelsFrom + i);
            int top = panelTop + 7 + i * 32;
            boolean chosen = model.equals(current);
            if (chosen) {
                g.setColor(PINK_LIGHT);
                g.fill(new RoundRectangle2D.Double(left + 6, top, width - 12, 28, 28, 28));
            }
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, chosen ? Font.BOLD : Font.PLAIN, 13));
            g.drawString(fitted(g, model, width - 36), left + 18, top + 19);
            regions.add(new Region(new Rectangle2D.Double(left + 6, top, width - 12, 28), () -> {
                choose.accept(model);
                modelList = false;
            }));
        }
        int foot = panelTop + 7 + shown * 32;
        if (pages) {
            choice(g, "‹", left + 10, foot, 36, 26, false, 14, () -> modelsFrom = Math.max(0, modelsFrom - MODELS_PER_PAGE));
            choice(g, "›", left + width - 46, foot, 36, 26, false, 14, () -> {
                if (modelsFrom + MODELS_PER_PAGE < models.size()) {
                    modelsFrom += MODELS_PER_PAGE;
                }
            });
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            String text = (modelsFrom / MODELS_PER_PAGE + 1) + "/" + ((models.size() + MODELS_PER_PAGE - 1) / MODELS_PER_PAGE);
            g.drawString(text, left + (width - g.getFontMetrics().stringWidth(text)) / 2, foot + 18);
        } else if (waiting) {
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString("Die Modelle des Dienstes werden geladen …", left + 18, foot + 18);
        }
    }

    /** The model that draws this kind: its own, else the general one. */
    private static String modelOf(PartLibrary.Kind kind) {
        String own = AiSetup.modelFor(kind);
        return own != null ? own : AiSetup.generalModel();
    }

    private static String shortName(String model) {
        return model.substring(model.lastIndexOf('/') + 1);
    }

    /** Opens the list of models; the first time, the service is asked for its models on another thread. */
    private void openModelList(boolean open) {
        modelList = open;
        modelsFrom = 0;
        AiSetup.Session session = AiSetup.session();
        if (!open || offeredModels != null || session == null) {
            return; // without access the list shows what is set; the access menu knows more
        }
        offeredModels = List.of();
        Thread asking = new Thread(() -> {
            List<String> answer;
            try {
                answer = session.models();
            } catch (java.io.IOException | RuntimeException e) {
                answer = null; // asked again the next time the list opens
                System.err.println("Models not listed: " + e.getMessage());
            }
            offeredModels = answer;
            modelsArrived = true;
        }, "model list");
        asking.setDaemon(true);
        asking.start();
    }

    private void chooseModel(String model) {
        try {
            // The general model is not written down for the kind, so that the kind follows it when it changes.
            AiSetup.saveModelFor(shownKind,
                    model.equals(AiSetup.generalModel()) ? null : model);
        } catch (java.io.IOException e) {
            System.err.println("Model not saved: " + e.getMessage());
        }
        modelList = false;
    }

    private BufferedImage thumbnail(PartLibrary.Part part) {
        return thumbnails.computeIfAbsent(part.png(), file -> library().load(part));
    }

    private void setLimit(int limit) {
        try {
            library().setTarget(shownKind, limit);
        } catch (java.io.IOException e) {
            System.err.println("Limit not saved: " + e.getMessage());
        }
    }

    /** Makes this picture the one the district uses for its kind, e.g. its street lamp. */
    private void use(PartLibrary.Part part) {
        try {
            library().setChosen(part);
        } catch (java.io.IOException e) {
            System.err.println("Choice not saved: " + e.getMessage());
        }
    }

    /** Takes the picture out of the collection; its files move to the folder "deleted". */
    private void delete(PartLibrary.Part part) {
        try {
            library().remove(part);
            thumbnails.remove(part.png());
        } catch (java.io.IOException e) {
            System.err.println("Picture not removed: " + e.getMessage());
        }
    }

    private static String fitted(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && g.getFontMetrics().stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private void note(Graphics2D g, String text, int y) {
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(text, 40, y);
    }

    // ------------------------------------------------------------------ widgets

    private void title(Graphics2D g, String text) {
        g.setColor(INK);
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 44));
        centered(g, text, 104);
    }

    private static void centered(Graphics2D g, String text, int y) {
        g.drawString(text, (CARD_WIDTH - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private void label(Graphics2D g, String text, int y) {
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString(text, 40, y);
    }

    /** A button; the one under the mouse (or chosen with the arrow keys) is filled. */
    private void button(Graphics2D g, String text, int x, int y, int width, int height, Runnable action) {
        boolean isMarked = regions.size() == marked;
        pill(g, text, x, y, width, height, isMarked, 19);
        regions.add(new Region(new Rectangle2D.Double(x, y, width, height), action));
    }

    /** One of several options side by side; the chosen one is filled. */
    private void choice(Graphics2D g, String text, int x, int y, int width, boolean chosen, Runnable action) {
        choice(g, text, x, y, width, 40, chosen, 16, action);
    }

    private void choice(Graphics2D g, String text, int x, int y, int width, int height, boolean chosen, int fontSize,
                        Runnable action) {
        pill(g, text, x, y, width, height, chosen, fontSize);
        regions.add(new Region(new Rectangle2D.Double(x, y, width, height), action));
    }

    private void label(Graphics2D g, String text, int x, int y, int size) {
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        g.drawString(text, x, y);
    }

    private static void pill(Graphics2D g, String text, int x, int y, int width, int height, boolean filled, int fontSize) {
        Shape shape = new RoundRectangle2D.Double(x, y, width, height, height, height);
        g.setColor(filled ? PINK_DARK : PINK_LIGHT);
        g.fill(shape);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(shape);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        g.setColor(filled ? Color.WHITE : INK);
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (width - textWidth) / 2, y + height / 2 + fontSize / 3 + 1);
    }
}
