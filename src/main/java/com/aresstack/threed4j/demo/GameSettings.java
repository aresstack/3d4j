package com.aresstack.threed4j.demo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * What the player chose in the settings, remembered in {@code .3d4j/game.properties}.
 *
 * @param renderScale   internal resolution as a fraction of the window, 0.25 to 1
 * @param smoothUpscale blend neighboring pixels when the scene is stretched to the window
 */
record GameSettings(float renderScale, boolean smoothUpscale) {

    static final GameSettings DEFAULTS = new GameSettings(1f, true);

    static Path file() {
        return Path.of(System.getProperty("user.home"), ".3d4j", "game.properties");
    }

    /** The remembered settings; defaults for whatever is missing or unreadable. */
    static GameSettings load(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                return DEFAULTS;
            }
        }
        float scale = DEFAULTS.renderScale();
        try {
            scale = Float.parseFloat(properties.getProperty("render.scale", "1"));
        } catch (NumberFormatException e) {
            // keep the default
        }
        if (!(scale >= 0.25f && scale <= 1f)) {
            scale = DEFAULTS.renderScale();
        }
        return new GameSettings(scale, !"false".equalsIgnoreCase(properties.getProperty("render.smooth")));
    }

    /** Remembers the settings; a failure only means they are asked for again next time. */
    void save(Path file) {
        Properties properties = new Properties();
        properties.setProperty("render.scale", String.format(Locale.ROOT, "%.4f", renderScale));
        properties.setProperty("render.smooth", String.valueOf(smoothUpscale));
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(out, "Nur zur ZIHR");
            }
        } catch (IOException e) {
            System.err.println("Settings not saved: " + e.getMessage());
        }
    }
}
