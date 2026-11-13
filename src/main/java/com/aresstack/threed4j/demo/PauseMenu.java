package com.aresstack.threed4j.demo;

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
 * the look of the "create the world" menu, drawn over the paused game.
 *
 * <p>Like that menu it is painted with Java 2D into an image; the game shows the
 * image as one texture in the middle of the window. The card is laid out on a fixed
 * {@value #CARD_WIDTH} x {@value #CARD_HEIGHT} surface whose size relative to the
 * window follows from {@link #scale}. The corners of the card are square on
 * purpose: the image is drawn without transparency.</p>
 */
final class PauseMenu {

    static final int CARD_WIDTH = 520;
    static final int CARD_HEIGHT = 560;

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

        /** Open the "create the world" menu to draw the artwork again. */
        void recreateWorld();
    }

    private record Region(Rectangle2D area, Runnable action) {
    }

    private final Actions actions;
    private float renderScale;
    private boolean smoothUpscale;
    private boolean settingsPage;
    /** Index of the button under the mouse or chosen with the arrow keys; -1 for none. */
    private int marked = -1;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private final List<Region> regions = new ArrayList<>();
    private boolean dirty = true;

    PauseMenu(Actions actions, GameSettings settings) {
        this.actions = actions;
        this.renderScale = settings.renderScale();
        this.smoothUpscale = settings.smoothUpscale();
    }

    /** {@code true} if the image has to be painted again; resets the flag. */
    boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    /** Window pixels per card unit: the card keeps its share of a 1280 x 720 window at any window size. */
    static double scale(int windowWidth, int windowHeight) {
        return Math.min((double) windowWidth / DESIGN_WIDTH, (double) windowHeight / DESIGN_HEIGHT);
    }

    // ------------------------------------------------------------------- input

    void update(Input input, int windowWidth, int windowHeight) {
        if (input.wasPressed(Key.ESCAPE)) {
            if (settingsPage) {
                showPage(false);
            } else {
                actions.resume();
            }
            return;
        }
        if (regions.isEmpty()) {
            return; // not painted yet
        }
        double scale = scale(windowWidth, windowHeight);
        double x = (input.mouseX() - (windowWidth - CARD_WIDTH * scale) / 2) / scale;
        double y = (input.mouseY() - (windowHeight - CARD_HEIGHT * scale) / 2) / scale;
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
        if (!settingsPage) {
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
        int width = Math.max(1, (int) Math.round(CARD_WIDTH * scale));
        int height = Math.max(1, (int) Math.round(CARD_HEIGHT * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale((double) width / CARD_WIDTH, (double) height / CARD_HEIGHT);

        // Frame: pink to the very edge (the corners are not transparent), the card inside.
        g.setColor(PINK_DARK);
        g.fillRect(0, 0, CARD_WIDTH, CARD_HEIGHT);
        g.setColor(PINK);
        g.fillRect(4, 4, CARD_WIDTH - 8, CARD_HEIGHT - 8);
        g.setColor(CARD);
        g.fill(new RoundRectangle2D.Double(12, 12, CARD_WIDTH - 24, CARD_HEIGHT - 24, 40, 40));
        SetupScreen.blossom(g, 44, 46, 30);
        SetupScreen.blossom(g, CARD_WIDTH - 60, CARD_HEIGHT - 44, 30);
        SetupScreen.blossom(g, CARD_WIDTH - 52, 44, 17);
        SetupScreen.blossom(g, 42, CARD_HEIGHT - 40, 19);

        regions.clear();
        if (settingsPage) {
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

    private void paintSettings(Graphics2D g) {
        title(g, "Einstellungen");
        label(g, "Auflösung", 150);
        for (int i = 0; i < RENDER_SCALES.length; i++) {
            float scale = RENDER_SCALES[i];
            choice(g, RENDER_SCALE_LABELS[i], 40 + i * 89, 162, 84, Math.abs(scale - renderScale) < 0.01f, () -> {
                renderScale = scale;
                actions.renderScale(scale);
            });
        }
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString("Weniger Prozent: unschärfer, aber mehr Bilder pro Sekunde.", 40, 228);

        label(g, "Hochskalieren", 274);
        choice(g, "Weich", 40, 286, 150, smoothUpscale, () -> {
            smoothUpscale = true;
            actions.smoothUpscale(true);
        });
        choice(g, "Pixelig", 200, 286, 150, !smoothUpscale, () -> {
            smoothUpscale = false;
            actions.smoothUpscale(false);
        });

        label(g, "Welt", 370);
        button(g, "Welt neu zeichnen lassen …", 40, 382, 440, 50, actions::recreateWorld);
        button(g, "Zurück", 40, 468, 150, 50, () -> showPage(false));
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
        pill(g, text, x, y, width, 40, chosen, 16);
        regions.add(new Region(new Rectangle2D.Double(x, y, width, 40), action));
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
