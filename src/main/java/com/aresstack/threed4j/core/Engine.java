package com.aresstack.threed4j.core;

import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Screenshot;
import com.aresstack.threed4j.platform.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;

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
                System.out.println("Frame times at " + display.graphics().sceneWidth() + "x"
                        + display.graphics().sceneHeight() + ": " + context.frameStats().total());
                game.dispose();
            }
        }
    }

    private static void loop(EngineConfig config, Game game, Window window, GraphicsDevice graphics,
                             EngineContext context) {
        long start = System.nanoTime();
        long previous = start;
        long titleUpdated = start;

        while (!window.shouldClose()) {
            long now = System.nanoTime();
            double elapsed = (now - previous) / 1e9;
            previous = now;
            if (context.frame() > 0) {
                context.frameStats().add(elapsed * 1000.0);
            }

            window.input().beginFrame();
            window.pollEvents();
            context.beginFrame((now - start) / 1e9);

            game.update(context, Math.min(elapsed, MAX_DELTA_SECONDS));

            graphics.beginFrame(window.width(), window.height());
            game.render(context);
            graphics.beginOverlay();
            game.renderOverlay(context);

            Path screenshot = context.takePendingScreenshot();
            if (screenshot != null) {
                saveScreenshot(graphics, screenshot);
            }
            graphics.present();

            if (now - titleUpdated >= 1_000_000_000L) {
                FrameStats.Summary second = context.frameStats().sinceLastCall();
                context.lastSecond(second);
                window.setTitle(String.format(Locale.ROOT, "%s | %dx%d | %.0f fps | avg %.1f ms, p99 %.1f ms",
                        config.title(), graphics.sceneWidth(), graphics.sceneHeight(),
                        second.framesPerSecond(), second.averageMillis(), second.p99Millis()));
                titleUpdated = now;
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
