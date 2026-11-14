package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.PartLibrary;
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
    private static final float[] MOUSE_SPEEDS = {0.5f, 1f, 2f};
    private static final String[] MOUSE_SPEED_LABELS = {"Langsam", "Normal", "Schnell"};
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
    private final PartLibrary library;
    private final String districtName;
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
        this.actions = actions;
        this.library = library;
        this.districtName = districtName;
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
        return collectionPage ? COLLECTION_WIDTH : CARD_WIDTH;
    }

    int cardHeight() {
        return collectionPage ? COLLECTION_HEIGHT : CARD_HEIGHT;
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
            if (modelList) {
                modelList = false;
                dirty = true;
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
            if (!library.signature().equals(shownSignature)) {
                dirty = true; // a picture arrived or left while the page is open
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
        if (!settingsPage && !collectionPage) {
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
        if (input.wasPressed(MouseButton.LEFT) && under >= 0) {
            run(regions.get(under));
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
        marked = -1;
        regions.clear();
        dirty = true;
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
        if (collectionPage) {
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
    }

    private void paintControlsTab(Graphics2D g) {
        label(g, "Maus beim Umsehen", 206);
        for (int i = 0; i < MOUSE_SPEEDS.length; i++) {
            float speed = MOUSE_SPEEDS[i];
            choice(g, MOUSE_SPEED_LABELS[i], 40 + i * 150, 218, 140, Math.abs(speed - mouseSpeed) < 0.01f, () -> {
                mouseSpeed = speed;
                actions.mouseSpeed(speed);
            });
        }
        note(g, "Umsehen: Maustaste halten und ziehen, oder Pfeiltasten.", 284);
        note(g, "Gehen: W A S D, schneller mit Umschalt, E und Q hoch und runter.", 308);
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
        note(g, "Mehr Nachdenken dauert länger. Standard: Modell entscheidet.", 278);

        label(g, "Antwortlänge in Tokens", 318);
        for (int i = 0; i < TOKEN_LIMITS.length; i++) {
            int limit = TOKEN_LIMITS[i];
            choice(g, String.valueOf(limit), 40 + i * 112, 330, 106, limit == maxTokens, () -> {
                maxTokens = limit;
                actions.requestOptions(maxTokens, reasoningEffort);
            });
        }
        note(g, "Nachdenken zählt mit. Bei abgeschnittenen Antworten erhöhen.", 394);
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

        button(g, "Sammlung ansehen …", 40, 380, 440, 42, () -> showCollection(true));
        button(g, "KI-Zugang …", 40, 428, 440, 42, actions::openAccessMenu);
    }

    // ---------------------------------------------------------- the collection

    /**
     * The district's pictures: the kinds on the left with how many there are of
     * each, on the right the pictures of the chosen kind. A picture can be taken
     * out, the limit of a kind raised or lowered.
     */
    private void paintCollection(Graphics2D g) {
        shownSignature = library.signature();
        g.setColor(INK);
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34));
        g.drawString("Sammlung · " + districtName, 84, 72);

        // Kinds
        int y = 96;
        for (PartLibrary.Kind kind : PartLibrary.Kind.values()) {
            int have = library.parts(kind).size();
            String text = BackgroundArtist.plural(kind) + "  " + have + "/" + library.target(kind);
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
        label(g, BackgroundArtist.plural(shownKind), 330, 116, 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        int labelEnd = 330 + g.getFontMetrics().stringWidth(BackgroundArtist.plural(shownKind));
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
            paintModelList(g, modelX, kindModel);
        }
    }

    private static final int MODELS_PER_PAGE = 9;

    /** The list below the model's button: the models of the service, a page at a time. Painted last, it lies on top. */
    private void paintModelList(Graphics2D g, int x, String current) {
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
        Shape panel = new RoundRectangle2D.Double(left, 130, width, height, 22, 22);
        g.setColor(CARD);
        g.fill(panel);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PINK_DARK);
        g.draw(panel);
        regions.add(new Region(new Rectangle2D.Double(left, 130, width, height), () -> { }));
        for (int i = 0; i < shown; i++) {
            String model = models.get(modelsFrom + i);
            int top = 137 + i * 32;
            boolean chosen = model.equals(current);
            if (chosen) {
                g.setColor(PINK_LIGHT);
                g.fill(new RoundRectangle2D.Double(left + 6, top, width - 12, 28, 28, 28));
            }
            g.setColor(INK);
            g.setFont(new Font(Font.SANS_SERIF, chosen ? Font.BOLD : Font.PLAIN, 13));
            g.drawString(fitted(g, model, width - 36), left + 18, top + 19);
            regions.add(new Region(new Rectangle2D.Double(left + 6, top, width - 12, 28), () -> chooseModel(model)));
        }
        int foot = 137 + shown * 32;
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
        return thumbnails.computeIfAbsent(part.png(), file -> library.load(part));
    }

    private void setLimit(int limit) {
        try {
            library.setTarget(shownKind, limit);
        } catch (java.io.IOException e) {
            System.err.println("Limit not saved: " + e.getMessage());
        }
    }

    /** Makes this picture the one the district uses for its kind, e.g. its street lamp. */
    private void use(PartLibrary.Part part) {
        try {
            library.setChosen(part);
        } catch (java.io.IOException e) {
            System.err.println("Choice not saved: " + e.getMessage());
        }
    }

    /** Takes the picture out of the collection; its files move to the folder "deleted". */
    private void delete(PartLibrary.Part part) {
        try {
            library.remove(part);
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
