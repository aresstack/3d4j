package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.gfx.Textures;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.MouseButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The menu without a window: painting and input handling are plain Java. */
class SetupScreenTest {

    @TempDir
    Path temp;
    private String previousHome;
    private SetupScreen screen;

    @BeforeEach
    void open() {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.resolve("home").toString());
        screen = new SetupScreen(new AssetStore(temp.resolve("assets")),
                Textures.readResource("/images/loading.jpg"), Textures.readResource("/images/cover.jpg"));
    }

    @AfterEach
    void close() {
        screen.close();
        System.setProperty("user.home", previousHome);
    }

    @Test
    void paintsAtAnyWindowSize() {
        for (int[] size : new int[][]{{1280, 720}, {800, 600}, {1920, 1080}, {640, 900}}) {
            BufferedImage image = screen.paint(size[0], size[1]);
            assertEquals(size[0], image.getWidth());
            assertEquals(size[1], image.getHeight());
        }
    }

    @Test
    void repaintsOnlyAfterSomethingChanged() {
        Input input = new Input();
        assertTrue(screen.consumeDirty(), "the first picture is due");
        screen.paint(1280, 720);
        screen.update(input, 1280, 720);
        assertFalse(screen.consumeDirty(), "nothing happened");

        input.onText('h');
        screen.update(input, 1280, 720);
        assertTrue(screen.consumeDirty(), "a typed character changes the address field");
    }

    @Test
    void anInvalidAddressKeepsTheMenuOnItsFirstStepAndPlayingWithoutAiLeavesIt() {
        Input input = new Input();
        screen.paint(1280, 720);
        for (char c : "not an address\r".toCharArray()) {
            input.onText(c);
        }
        screen.update(input, 1280, 720);
        assertFalse(screen.isFinished());
        screen.paint(1280, 720);

        // Click "Ohne KI spielen" (bottom left of the panel).
        input.beginFrame();
        input.onMouseMove(270, 622);
        input.onMouseButton(MouseButton.LEFT, true);
        screen.update(input, 1280, 720);
        assertTrue(screen.isFinished());
    }
}
