package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.assets.PartLibrary;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;
import com.aresstack.threed4j.platform.MouseButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pause menu without a window: painting and input handling are plain Java. */
class PauseMenuTest {

    private final List<String> asked = new ArrayList<>();
    @TempDir
    static Path assets;

    private final PartLibrary library = new PartLibrary(new AssetStore(assets), "altstadt");
    private final PauseMenu menu = new PauseMenu(new PauseMenu.Actions() {
        @Override
        public void resume() {
            asked.add("resume");
        }

        @Override
        public void quit() {
            asked.add("quit");
        }

        @Override
        public void renderScale(float scale) {
            asked.add("scale " + scale);
        }

        @Override
        public void smoothUpscale(boolean smooth) {
            asked.add("smooth " + smooth);
        }

        @Override
        public void mouseSpeed(float speed) {
            asked.add("mouse " + speed);
        }

        @Override
        public void requestOptions(int maxTokens, String reasoningEffort) {
            asked.add("ai " + maxTokens + " " + reasoningEffort);
        }

        @Override
        public void backgroundDrawing(boolean enabled) {
            asked.add("background " + enabled);
        }

        @Override
        public String[] backgroundStatus() {
            return new String[] {"wartet", "Fenster 0/8"};
        }

        @Override
        public void openAccessMenu() {
            asked.add("access");
        }
    }, new PauseMenu.State(1f, true, 1f, null, 8192, true), library, "Altstadt");
    private final Input input = new Input();

    /** Clicks a point given in card units, in a 1280 x 720 window (card at 380, 80, scale 1). */
    private void click(int cardX, int cardY) {
        menu.paint(1280, 720);
        input.beginFrame();
        input.onMouseMove(380 + cardX, 80 + cardY);
        input.onMouseButton(MouseButton.LEFT, true);
        menu.update(input, 1280, 720);
        input.onMouseButton(MouseButton.LEFT, false);
    }

    private void press(Key key) {
        menu.paint(1280, 720);
        input.beginFrame();
        input.onKey(key, true);
        menu.update(input, 1280, 720);
        input.onKey(key, false);
    }

    @Test
    void theCardKeepsItsShareOfTheWindow() {
        BufferedImage normal = menu.paint(1280, 720);
        assertEquals(PauseMenu.CARD_WIDTH, normal.getWidth());
        assertEquals(PauseMenu.CARD_HEIGHT, normal.getHeight());
        BufferedImage large = menu.paint(2560, 1440);
        assertEquals(2 * PauseMenu.CARD_WIDTH, large.getWidth());
        BufferedImage narrow = menu.paint(640, 900);
        assertEquals(PauseMenu.CARD_WIDTH / 2, narrow.getWidth());
    }

    @Test
    void escapeAndTheFirstButtonContinueTheLastButtonQuits() {
        press(Key.ESCAPE);
        click(260, 209);
        click(260, 373);
        assertEquals(List.of("resume", "resume", "quit"), asked);
    }

    @Test
    void theArrowKeysReachTheButtons() {
        press(Key.DOWN);
        press(Key.DOWN);
        press(Key.DOWN);
        press(Key.ENTER);
        assertEquals(List.of("quit"), asked);
    }

    @Test
    void settingsChangePictureControlsAndRequestsAndEscapeLeadsBackFirst() {
        click(260, 291); // Einstellungen, tab "Bild"
        click(260, 238); // 50 %
        click(275, 362); // Pixelig
        click(205, 146); // tab "Steuerung"
        click(410, 238); // Schnell
        click(317, 146); // tab "KI"
        click(430, 234); // Viel nachdenken
        click(200, 350); // 4096 Tokens
        click(95, 234);  // Standard: nothing is sent, the model decides
        click(429, 146); // tab "Welt"
        click(205, 238); // background drawing off
        click(260, 449); // KI-Zugang
        assertEquals(List.of("scale 0.5", "smooth false", "mouse 2.0", "ai 8192 high", "ai 4096 high",
                "ai 4096 null", "background false", "access"), asked);

        press(Key.ESCAPE); // back to the first page, the game stays paused
        assertEquals(8, asked.size());
        press(Key.ESCAPE);
        assertEquals("resume", asked.get(8));
    }

    /** Clicks a point of the wide card of the collection (960 x 640, at 160, 40 in a 1280 x 720 window). */
    private void clickCollection(int cardX, int cardY) {
        menu.paint(1280, 720);
        input.beginFrame();
        input.onMouseMove(160 + cardX, 40 + cardY);
        input.onMouseButton(MouseButton.LEFT, true);
        menu.update(input, 1280, 720);
        input.onMouseButton(MouseButton.LEFT, false);
    }

    @Test
    void theCollectionShowsThePicturesOfAKindAndRemovesOneAndChangesTheLimit() throws Exception {
        BufferedImage picture = new BufferedImage(64, 128, BufferedImage.TYPE_INT_ARGB);
        for (PartLibrary.Part old : library.parts()) {
            library.remove(old);
        }
        library.add(PartLibrary.Kind.VISITOR, null, picture, null);
        library.add(PartLibrary.Kind.VISITOR, null, picture, null);

        click(260, 291); // Einstellungen
        click(429, 146); // tab "Welt"
        click(260, 401); // Sammlung ansehen
        assertEquals(960, menu.paint(1280, 720).getWidth(), "the collection has a wider card");

        clickCollection(165, 244); // fourth kind on the left: Besucher
        clickCollection(397, 321); // "Löschen" below the first picture
        assertEquals(List.of(2), library.parts(PartLibrary.Kind.VISITOR).stream().map(PartLibrary.Part::number).toList());

        library.setTarget(PartLibrary.Kind.VISITOR, 12);
        int before = library.target(PartLibrary.Kind.VISITOR);
        clickCollection(886, 110); // +
        clickCollection(886, 110);
        clickCollection(790, 110); // -
        assertEquals(before + 1, library.target(PartLibrary.Kind.VISITOR));

        press(Key.ESCAPE); // back to the settings
        assertEquals(PauseMenu.CARD_WIDTH, menu.paint(1280, 720).getWidth());
        assertTrue(asked.isEmpty(), "none of this is the game's business: " + asked);
    }

    @Test
    void ofTheLampsOneIsInUseAndAnotherCanBeChosen() throws Exception {
        BufferedImage picture = new BufferedImage(64, 128, BufferedImage.TYPE_INT_ARGB);
        for (PartLibrary.Part old : library.parts()) {
            library.remove(old);
        }
        PartLibrary.Part first = library.add(PartLibrary.Kind.LAMP, null, picture, null);
        PartLibrary.Part second = library.add(PartLibrary.Kind.LAMP, null, picture, null);
        assertEquals(first, library.chosen(PartLibrary.Kind.LAMP));

        click(260, 291); // Einstellungen
        click(429, 146); // tab "Welt"
        click(260, 401); // Sammlung ansehen
        menu.paint(1280, 720);
        clickCollection(165, 201); // third kind on the left: Laternen
        clickCollection(515, 321); // "Verwenden" below the second picture
        assertEquals(second, library.chosen(PartLibrary.Kind.LAMP));

        clickCollection(585, 321); // "Löschen" below the second picture
        assertEquals(List.of(first), library.parts(PartLibrary.Kind.LAMP));
    }

    @Test
    void repaintsOnlyAfterSomethingChanged() {
        assertTrue(menu.consumeDirty(), "the first picture is due");
        menu.paint(1280, 720);
        input.beginFrame();
        input.onMouseMove(10, 10);
        menu.update(input, 1280, 720);
        assertFalse(menu.consumeDirty(), "the mouse is over no button");
        input.beginFrame();
        input.onMouseMove(640, 290);
        menu.update(input, 1280, 720);
        assertTrue(menu.consumeDirty(), "the button under the mouse is marked");
    }

    @Test
    void settingsAreRememberedAndBrokenFilesGiveDefaults(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("sub").resolve("game.properties");
        assertEquals(GameSettings.DEFAULTS, GameSettings.load(file));
        new GameSettings(1f / 3f, false, 2f).save(file);
        GameSettings loaded = GameSettings.load(file);
        assertEquals(1f / 3f, loaded.renderScale(), 0.001f);
        assertFalse(loaded.smoothUpscale());
        assertEquals(2f, loaded.mouseSpeed(), 0.001f);
        java.nio.file.Files.writeString(file, "render.scale=17\nrender.smooth=vielleicht\nmouse.speed=-3\n");
        assertEquals(GameSettings.DEFAULTS, GameSettings.load(file));
    }
}
