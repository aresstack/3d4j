package com.aresstack.threed4j.core;

import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Screenshot;
import com.aresstack.threed4j.platform.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Opens the window and runs the game loop. */
public final class Engine {

    /** Longest time step handed to {@link Game#update}, in seconds. */
    private static final double MAX_DELTA_SECONDS = 0.1;

    private Engine() {
    }

    /** Runs the game on the calling thread and returns when the window has been closed. */
    public static void run(EngineConfig config, Game game) {
        try (Display display = Display.open(config.window())) {
            System.out.println(display.graphics().description());

            EngineContext context = new EngineContext(display.window(), display.graphics());
            game.init(context);
            try {
                loop(config, game, display.window(), display.graphics(), context);
            } finally {
                game.dispose();
            }
        }
    }

    private static void loop(EngineConfig config, Game game, Window window, GraphicsDevice graphics,
                             EngineContext context) {
        long start = System.nanoTime();
        long previous = start;
        long fpsWindowStart = start;
        int fpsWindowFrames = 0;

        while (!window.shouldClose()) {
            long now = System.nanoTime();
            double delta = Math.min((now - previous) / 1e9, MAX_DELTA_SECONDS);
            previous = now;

            window.input().beginFrame();
            window.pollEvents();
            context.beginFrame((now - start) / 1e9);

            game.update(context, delta);

            graphics.beginFrame(window.width(), window.height());
            game.render(context);

            Path screenshot = context.takePendingScreenshot();
            if (screenshot != null) {
                saveScreenshot(graphics, screenshot);
            }
            graphics.present();

            fpsWindowFrames++;
            if (now - fpsWindowStart >= 1_000_000_000L) {
                double fps = fpsWindowFrames * 1e9 / (now - fpsWindowStart);
                context.framesPerSecond(fps);
                window.setTitle(config.title() + " | " + Math.round(fps) + " fps");
                fpsWindowStart = now;
                fpsWindowFrames = 0;
            }
            if (config.maxFrames() > 0 && context.frame() >= config.maxFrames()) {
                window.requestClose();
            }
        }
    }

    private static void saveScreenshot(GraphicsDevice graphics, Path file) {
        try {
            Screenshot.save(graphics.readPixels(), file);
            System.out.println("Screenshot saved: " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
