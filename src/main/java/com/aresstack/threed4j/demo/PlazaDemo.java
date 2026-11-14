package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.AssetCatalog;
import com.aresstack.threed4j.assets.AssetStore;
import com.aresstack.threed4j.generator.AiSetup;
import com.aresstack.threed4j.core.Engine;
import com.aresstack.threed4j.core.EngineConfig;
import com.aresstack.threed4j.core.EngineContext;
import com.aresstack.threed4j.core.Game;
import com.aresstack.threed4j.gfx.Camera;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.Meshes;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.gfx.Texture;
import com.aresstack.threed4j.gfx.Textures;
import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;
import com.aresstack.threed4j.platform.MouseButton;

import java.nio.file.Path;
import java.util.Locale;

/**
 * "Nur zur ZIHR" – first walkable test scene: the panorama picture as loading screen,
 * then a plaza with buildings, trees and visitors to measure what the renderer
 * can carry.
 *
 * <p>Keys: W A S D move, E / Q up and down, Shift faster, hold a mouse button and
 * drag (or arrow keys) to look around. 1 to 5 choose the internal resolution
 * (100, 75, 50, 33, 25 percent), F toggles smooth and blocky upscaling, R resets
 * the frame statistics, P saves {@code screenshot.png}, Escape opens the pause
 * menu (continue, settings, quit).</p>
 *
 * <p>Arguments: {@code --benchmark} skips the title and flies a fixed camera path,
 * {@code --scale=0.5} sets the internal resolution, {@code --frames=N} stops after
 * N frames, {@code --screenshot=FILE} saves the last of those frames,
 * {@code --time=SECONDS} freezes the scene at that moment (comparable pictures),
 * {@code --no-title} skips the loading screen, {@code --setup} / {@code --no-setup}
 * force or suppress the "create the world" menu (by default it opens while no
 * generated artwork exists), {@code --no-vsync} runs uncapped.</p>
 */
public final class PlazaDemo implements Game, PauseMenu.Actions {

    private static final float TITLE_SECONDS = 3.5f;
    private static final float[] RENDER_SCALES = PauseMenu.RENDER_SCALES;
    private static final Key[] SCALE_KEYS = {Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4, Key.DIGIT_5};

    private static final Vec3 LIGHT_DIRECTION = new Vec3(-0.45f, -1.0f, -0.6f);
    private static final float FOG_START = 45f;
    private static final float FOG_END = 150f;
    /** For the ground shader: width of an atlas cell, gutter and tile width as parts of a cell. */
    private static final Vec3 GROUND_ATLAS = new Vec3(1f / GroundAtlas.CELLS,
            (float) GroundAtlas.GUTTER / GroundAtlas.CELL, (float) GroundAtlas.TILE / GroundAtlas.CELL);
    /** Size of a ground tile in the world; from 30 to 75 units away its pattern fades to its average colour. */
    private static final Vec3 GROUND_DETAIL = new Vec3(PlazaScene.GROUND_TILE, 30f, 75f);
    private static final float WALK_SPEED = 9f;
    private static final float LOOK_SPEED = 0.004f;

    private final Options options;

    // Loading screen
    private Shader imageShader;
    private Mesh screenQuad;
    private Texture titleImage;
    private Texture white;
    private boolean playing;
    private double titleShownSince = -1;

    // The "create the world" menu between loading screen and game
    private SetupScreen setup;
    private Texture setupImage;
    private int setupImageWidth;
    private int setupImageHeight;

    // The pause menu over the running game
    private PauseMenu pause;
    private Texture pauseImage;
    private int pauseImageWidth;
    private int pauseImageHeight;
    private GameSettings settings = GameSettings.DEFAULTS;
    private EngineContext engine;

    // Scene
    private PlazaScene scene;
    private Shader sceneShader;
    private Shader spriteShader;
    private Shader groundShader;
    private Texture groundTexture;
    private Shader skyShader;
    private Texture spriteAtlas;
    private final Camera camera = new Camera().perspective((float) Math.toRadians(65), 0.1f, 400f);
    private Vec3 position = new Vec3(0f, 2.2f, 34f);
    private float yaw;
    private float pitch = 0.05f;
    private double sceneTime;
    private boolean smoothUpscale = true;

    private PlazaDemo(Options options) {
        this.options = options;
    }

    /** @param showSetup {@code null} decides by whether generated artwork exists already */
    private record Options(boolean benchmark, boolean showTitle, Boolean showSetup, Float renderScale,
                           Double frozenTime, Path finalScreenshot, long finalFrame) {
    }

    public static void main(String[] args) {
        EngineConfig config = EngineConfig.of("Nur zur ZIHR", 1280, 720);
        boolean benchmark = false;
        boolean showTitle = true;
        Boolean showSetup = null;
        Float scale = null; // not given: what the player chose in the settings
        Double time = null;
        Path screenshot = null;
        for (String arg : args) {
            if (arg.startsWith("--frames=")) {
                config = config.withMaxFrames(Long.parseLong(arg.substring("--frames=".length())));
            } else if (arg.startsWith("--screenshot=")) {
                screenshot = Path.of(arg.substring("--screenshot=".length()));
            } else if (arg.startsWith("--scale=")) {
                scale = Float.parseFloat(arg.substring("--scale=".length()));
            } else if (arg.startsWith("--time=")) {
                time = Double.parseDouble(arg.substring("--time=".length()));
            } else if (arg.equals("--benchmark")) {
                benchmark = true;
                showTitle = false;
                showSetup = false;
            } else if (arg.equals("--no-title")) {
                showTitle = false;
            } else if (arg.equals("--setup")) {
                showSetup = true;
            } else if (arg.equals("--no-setup")) {
                showSetup = false;
            } else if (arg.equals("--no-vsync")) {
                config = config.withVsync(false);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        Engine.run(config, new PlazaDemo(
                new Options(benchmark, showTitle, showSetup, scale, time, screenshot, config.maxFrames())));
    }

    // --------------------------------------------------------------------- init

    /** Loads only what the loading screen needs, so that it appears at once. */
    @Override
    public void init(EngineContext context) {
        GraphicsDevice graphics = context.graphics();
        engine = context;
        // A measurement must not depend on what was chosen in the settings.
        if (!options.benchmark()) {
            settings = GameSettings.load(GameSettings.file());
        }
        if (options.renderScale() != null) {
            settings = settings.withRenderScale(options.renderScale());
        }
        graphics.setRenderScale(settings.renderScale());
        graphics.setSmoothUpscale(settings.smoothUpscale());
        smoothUpscale = settings.smoothUpscale();
        imageShader = graphics.createShader("image", ShaderLayout.attributes(2, 2)
                .vec3("uScale")
                .vec3("uOffset")
                .vec3("uTint"));
        screenQuad = Meshes.screenQuad(graphics);
        titleImage = Textures.fromResource(graphics, "/images/loading.jpg", true);
        white = graphics.createTexture(1, 1, new int[]{0xFFFFFFFF}, false);
        if (!options.showTitle()) {
            afterTitle(context);
        }
    }

    /** The loading screen is over: open the menu if it is wanted, otherwise start the game. */
    private void afterTitle(EngineContext context) {
        AssetStore store = AssetStore.standard();
        boolean nothingGenerated = AssetCatalog.ALL.stream().noneMatch(store::has);
        boolean wanted = options.showSetup() != null ? options.showSetup() : nothingGenerated;
        if (wanted) {
            setup = new SetupScreen(store, Textures.readResource("/images/loading.jpg"),
                    Textures.readResource("/images/cover.jpg"));
        } else {
            startPlaying(context);
        }
    }

    private void loadScene(EngineContext context) {
        GraphicsDevice graphics = context.graphics();
        // The uniform order of each layout must match the cbuffer in the .hlsl file.
        sceneShader = graphics.createShader("scene", ShaderLayout.attributes(3, 3, 3)
                .mat4("uViewProjection")
                .vec3("uCameraPosition")
                .vec3("uLightDirection")
                .vec3("uFogColor")
                .scalar("uFogStart")
                .scalar("uFogEnd"));
        spriteShader = graphics.createShader("sprite", ShaderLayout.attributes(3, 2, 2, 3)
                .mat4("uViewProjection")
                .vec3("uCameraPosition")
                .vec3("uCameraRight")
                .vec3("uFogColor")
                .scalar("uFogStart")
                .scalar("uFogEnd")
                .scalar("uTime"));
        skyShader = graphics.createShader("sky", ShaderLayout.attributes(3, 3)
                .mat4("uViewProjection"));
        groundShader = graphics.createShader("ground", ShaderLayout.attributes(3, 3, 3)
                .mat4("uViewProjection")
                .vec3("uCameraPosition")
                .vec3("uAtlas")
                .vec3("uDetail")
                .vec3("uFogColor")
                .scalar("uFogStart")
                .scalar("uFogEnd"));
        loadArtwork(graphics);
        System.out.printf(Locale.ROOT, "Scene: %d buildings, %d triangles, %d sprites, 4 draw calls%n",
                scene.buildingCount, scene.cityTriangles + scene.groundTriangles, scene.spriteCount);
    }

    /**
     * Loads what comes from the asset folder (sprites, ground textures) and builds the
     * scene with it; again after the world menu, to show new artwork.
     */
    private void loadArtwork(GraphicsDevice graphics) {
        AssetStore store = AssetStore.standard();
        spriteAtlas = Textures.fromImage(graphics, SpriteAtlas.paint(store), true);
        spriteShader.setTexture(spriteAtlas);
        GroundAtlas ground = GroundAtlas.paint(store);
        groundTexture = Textures.fromImage(graphics, ground.image, true);
        groundShader.setTexture(groundTexture);
        scene = new PlazaScene(graphics, ground);
    }

    private void startPlaying(EngineContext context) {
        if (scene == null) {
            loadScene(context); // after the menu, so that freshly generated artwork is used
        } else {
            // Back from "draw the world again": show the new artwork.
            spriteAtlas.close();
            groundTexture.close();
            scene.close();
            loadArtwork(context.graphics());
        }
        playing = true;
        context.frameStats().reset(); // loading must not count as slow frames
    }

    // ------------------------------------------------------------------- update

    @Override
    public void update(EngineContext context, double deltaSeconds) {
        Input input = context.input();
        if (input.wasPressed(Key.ESCAPE)) {
            if (setup != null && scene != null) {
                setup.leave(); // opened from the pause menu: back to the game
            } else if (!playing || options.benchmark()) {
                context.quit();
            } else if (pause == null) {
                pause = new PauseMenu(this, new PauseMenu.State(settings.renderScale(), settings.smoothUpscale(),
                        settings.mouseSpeed(), AiSetup.reasoningEffort(), AiSetup.maxTokens()));
                return;
            }
        }
        if (options.finalScreenshot() != null && context.frame() == Math.max(1, options.finalFrame())) {
            context.requestScreenshot(options.finalScreenshot());
        }

        if (setup != null) {
            setup.update(input, context.window().width(), context.window().height());
            if (setup.isFinished()) {
                setup.close();
                setup = null;
                if (setupImage != null) {
                    setupImage.close();
                    setupImage = null;
                }
                startPlaying(context);
            }
            return;
        }
        if (!playing) {
            updateTitle(context, input);
            return;
        }
        if (pause != null) {
            // The game stands still while the menu is open.
            pause.update(input, context.window().width(), context.window().height());
            return;
        }
        if (input.wasPressed(Key.P)) { // not in the menu: there P is a letter to type
            context.requestScreenshot(Path.of("screenshot.png"));
        }

        sceneTime = options.frozenTime() != null ? options.frozenTime() : sceneTime + deltaSeconds;
        chooseResolution(context, input);
        if (options.benchmark()) {
            flyCameraPath();
        } else {
            moveCamera(input, (float) deltaSeconds);
        }
        camera.orient(position, yaw, pitch);
    }

    private void updateTitle(EngineContext context, Input input) {
        if (titleShownSince < 0) {
            titleShownSince = context.timeSeconds();
            return;
        }
        boolean skip = input.wasPressed(Key.SPACE) || input.wasPressed(Key.ENTER)
                || input.wasPressed(MouseButton.LEFT);
        if (skip || titleProgress(context) >= 1f) {
            afterTitle(context);
        }
    }

    private float titleProgress(EngineContext context) {
        if (titleShownSince < 0) {
            return 0f;
        }
        return (float) Math.min(1.0, (context.timeSeconds() - titleShownSince) / TITLE_SECONDS);
    }

    private void chooseResolution(EngineContext context, Input input) {
        GraphicsDevice graphics = context.graphics();
        for (int i = 0; i < SCALE_KEYS.length; i++) {
            if (input.wasPressed(SCALE_KEYS[i])) {
                renderScale(RENDER_SCALES[i]);
            }
        }
        if (input.wasPressed(Key.F)) {
            smoothUpscale(!smoothUpscale);
        }
        if (input.wasPressed(Key.R)) {
            context.frameStats().reset();
        }
    }

    // ------------------------------------------------- what the pause menu asks for

    @Override
    public void resume() {
        closePause();
        engine.frameStats().reset(); // the standstill must not count
    }

    @Override
    public void quit() {
        engine.quit();
    }

    @Override
    public void renderScale(float scale) {
        engine.graphics().setRenderScale(scale);
        engine.frameStats().reset();
        remember(settings.withRenderScale(scale));
    }

    @Override
    public void smoothUpscale(boolean smooth) {
        smoothUpscale = smooth;
        engine.graphics().setSmoothUpscale(smooth);
        remember(settings.withSmoothUpscale(smooth));
    }

    @Override
    public void mouseSpeed(float speed) {
        remember(settings.withMouseSpeed(speed));
    }

    @Override
    public void requestOptions(int maxTokens, String reasoningEffort) {
        try {
            AiSetup.saveRequestOptions(maxTokens, reasoningEffort);
        } catch (java.io.IOException e) {
            System.err.println("Settings not saved: " + e.getMessage());
        }
    }

    @Override
    public void recreateWorld() {
        closePause();
        playing = false;
        setup = new SetupScreen(AssetStore.standard(), Textures.readResource("/images/loading.jpg"),
                Textures.readResource("/images/cover.jpg"));
        setup.openedFromGame();
    }

    private void remember(GameSettings changed) {
        settings = changed;
        if (!options.benchmark()) {
            changed.save(GameSettings.file());
        }
    }

    private void closePause() {
        pause = null;
        if (pauseImage != null) {
            pauseImage.close();
            pauseImage = null;
        }
    }

    private void moveCamera(Input input, float step) {
        if (input.isDown(MouseButton.LEFT) || input.isDown(MouseButton.RIGHT)) {
            yaw += input.mouseDeltaX() * LOOK_SPEED * settings.mouseSpeed();
            pitch -= input.mouseDeltaY() * LOOK_SPEED * settings.mouseSpeed();
        }
        float turn = 1.6f * step;
        if (input.isDown(Key.LEFT)) {
            yaw -= turn;
        }
        if (input.isDown(Key.RIGHT)) {
            yaw += turn;
        }
        if (input.isDown(Key.UP)) {
            pitch += turn;
        }
        if (input.isDown(Key.DOWN)) {
            pitch -= turn;
        }
        pitch = Math.max(-1.5f, Math.min(1.5f, pitch));

        // Walking stays level: forward is the viewing direction without its vertical part.
        Vec3 forward = new Vec3((float) Math.sin(yaw), 0f, (float) -Math.cos(yaw));
        Vec3 right = new Vec3((float) Math.cos(yaw), 0f, (float) Math.sin(yaw));
        Vec3 move = Vec3.ZERO;
        if (input.isDown(Key.W)) {
            move = move.add(forward);
        }
        if (input.isDown(Key.S)) {
            move = move.subtract(forward);
        }
        if (input.isDown(Key.D)) {
            move = move.add(right);
        }
        if (input.isDown(Key.A)) {
            move = move.subtract(right);
        }
        if (input.isDown(Key.E)) {
            move = move.add(Vec3.UNIT_Y);
        }
        if (input.isDown(Key.Q)) {
            move = move.subtract(Vec3.UNIT_Y);
        }
        float speed = WALK_SPEED * (input.isDown(Key.SHIFT) ? 3f : 1f);
        position = position.add(move.normalize().scale(speed * step));
        position = new Vec3(position.x(), Math.max(0.6f, position.y()), position.z());
    }

    /**
     * A fixed tour for comparable measurements: walks a loop inside the plaza while
     * slowly turning all the way around, so that the square, the crowd and all four
     * streets come into view.
     */
    private void flyCameraPath() {
        double t = sceneTime * 0.25;
        float radius = 12f + 5f * (float) Math.sin(t * 0.7);
        position = new Vec3(radius * (float) Math.sin(t), 2.2f + 1.5f * (float) (0.5 + 0.5 * Math.sin(t * 1.3)),
                radius * (float) Math.cos(t));
        yaw = (float) (t * 1.9);
        pitch = 0.04f;
    }

    // ------------------------------------------------------------------- render

    @Override
    public void render(EngineContext context) {
        GraphicsDevice graphics = context.graphics();
        if (!playing) {
            graphics.clear(0.07f, 0.05f, 0.10f);
            return;
        }
        Vec3 haze = PlazaScene.HAZE;
        graphics.clear(haze.x(), haze.y(), haze.z());

        Mat4 projection = camera.projectionMatrix(context.aspectRatio());
        Mat4 viewProjection = projection.multiply(camera.viewMatrix());

        // Solid things first, the sky last: where a building already is, the sky costs nothing.
        sceneShader.setUniform("uViewProjection", viewProjection);
        sceneShader.setUniform("uCameraPosition", position);
        sceneShader.setUniform("uLightDirection", LIGHT_DIRECTION);
        sceneShader.setUniform("uFogColor", haze);
        sceneShader.setUniform("uFogStart", FOG_START);
        sceneShader.setUniform("uFogEnd", FOG_END);
        graphics.draw(scene.city, sceneShader);

        groundShader.setUniform("uViewProjection", viewProjection);
        groundShader.setUniform("uCameraPosition", position);
        groundShader.setUniform("uAtlas", GROUND_ATLAS);
        groundShader.setUniform("uDetail", GROUND_DETAIL);
        groundShader.setUniform("uFogColor", haze);
        groundShader.setUniform("uFogStart", FOG_START);
        groundShader.setUniform("uFogEnd", FOG_END);
        graphics.draw(scene.ground, groundShader);

        spriteShader.setUniform("uViewProjection", viewProjection);
        spriteShader.setUniform("uCameraPosition", position);
        spriteShader.setUniform("uCameraRight", camera.right());
        spriteShader.setUniform("uFogColor", haze);
        spriteShader.setUniform("uFogStart", FOG_START);
        spriteShader.setUniform("uFogEnd", FOG_END);
        spriteShader.setUniform("uTime", (float) sceneTime);
        graphics.draw(scene.sprites, spriteShader);

        skyShader.setUniform("uViewProjection", projection.multiply(camera.viewRotationMatrix()));
        graphics.draw(scene.sky, skyShader);
    }

    /** The loading screen: the title picture in full window resolution with a progress bar. */
    @Override
    public void renderOverlay(EngineContext context) {
        GraphicsDevice graphics = context.graphics();
        if (playing) {
            if (pause != null) {
                renderPause(context, graphics);
            }
            return;
        }
        if (setup != null) {
            renderSetup(context, graphics);
            return;
        }
        float windowAspect = context.aspectRatio();
        float imageAspect = (float) titleImage.width() / titleImage.height();

        // The picture is never distorted. Where its shape differs from the window's, the
        // free edges show the same picture enlarged and darkened instead of black bars.
        imageShader.setTexture(titleImage);
        float cover = Math.max(windowAspect / imageAspect, imageAspect / windowAspect);
        drawImage(graphics, fitX(windowAspect, imageAspect) * cover, fitY(windowAspect, imageAspect) * cover,
                0f, 0f, new Vec3(0.22f, 0.20f, 0.26f));
        // The whole picture, as large as fits.
        drawImage(graphics, fitX(windowAspect, imageAspect), fitY(windowAspect, imageAspect), 0f, 0f,
                new Vec3(1f, 1f, 1f));

        // Progress bar along the bottom edge.
        imageShader.setTexture(white);
        float progress = titleProgress(context);
        drawImage(graphics, 0.6f, 0.012f, 0f, -0.93f, new Vec3(0.20f, 0.14f, 0.26f));
        drawImage(graphics, 0.6f * progress, 0.012f, -0.6f * (1f - progress), -0.93f, new Vec3(0.98f, 0.62f, 0.82f));
    }

    /** The menu is one image the size of the window, repainted only when something changed. */
    private void renderSetup(EngineContext context, GraphicsDevice graphics) {
        int width = context.window().width();
        int height = context.window().height();
        if (setup.consumeDirty() || setupImage == null || width != setupImageWidth || height != setupImageHeight) {
            if (setupImage != null) {
                setupImage.close();
            }
            setupImage = Textures.fromImage(graphics, setup.paint(width, height), true);
            setupImageWidth = width;
            setupImageHeight = height;
        }
        imageShader.setTexture(setupImage);
        drawImage(graphics, 1f, 1f, 0f, 0f, new Vec3(1f, 1f, 1f));
    }

    /** The pause menu is a card in the middle of the window, over the standing game. */
    private void renderPause(EngineContext context, GraphicsDevice graphics) {
        int width = context.window().width();
        int height = context.window().height();
        if (pause.consumeDirty() || pauseImage == null || width != pauseImageWidth || height != pauseImageHeight) {
            if (pauseImage != null) {
                pauseImage.close();
            }
            pauseImage = Textures.fromImage(graphics, pause.paint(width, height), true);
            pauseImageWidth = width;
            pauseImageHeight = height;
        }
        // A veil over the standing game, so that the card stands out.
        imageShader.setTexture(white);
        drawImage(graphics, 1f, 1f, 0f, 0f, new Vec3(0.20f, 0.11f, 0.27f), 0.45f);
        float scale = (float) PauseMenu.scale(width, height);
        imageShader.setTexture(pauseImage);
        drawImage(graphics, PauseMenu.CARD_WIDTH * scale / width, PauseMenu.CARD_HEIGHT * scale / height, 0f, 0f,
                new Vec3(1f, 1f, 1f));
    }

    private static float fitX(float windowAspect, float imageAspect) {
        return windowAspect > imageAspect ? imageAspect / windowAspect : 1f;
    }

    private static float fitY(float windowAspect, float imageAspect) {
        return windowAspect > imageAspect ? 1f : windowAspect / imageAspect;
    }

    private void drawImage(GraphicsDevice graphics, float scaleX, float scaleY, float centerX, float centerY, Vec3 tint) {
        drawImage(graphics, scaleX, scaleY, centerX, centerY, tint, 1f);
    }

    /** @param opacity 1 draws the image as it is, less lets show what is below (the image's own transparency counts too) */
    private void drawImage(GraphicsDevice graphics, float scaleX, float scaleY, float centerX, float centerY, Vec3 tint,
                           float opacity) {
        imageShader.setUniform("uScale", new Vec3(scaleX, scaleY, opacity));
        imageShader.setUniform("uOffset", new Vec3(centerX, centerY, 0f));
        imageShader.setUniform("uTint", tint);
        graphics.draw(screenQuad, imageShader);
    }

    @Override
    public void dispose() {
        if (setup != null) {
            setup.close();
        }
        if (setupImage != null) {
            setupImage.close();
        }
        if (pauseImage != null) {
            pauseImage.close();
        }
        if (scene != null) {
            scene.close();
            spriteAtlas.close();
            groundTexture.close();
            groundShader.close();
            skyShader.close();
            spriteShader.close();
            sceneShader.close();
        }
        white.close();
        titleImage.close();
        screenQuad.close();
        imageShader.close();
    }
}
