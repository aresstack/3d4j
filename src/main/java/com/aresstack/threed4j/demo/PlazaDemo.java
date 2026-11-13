package com.aresstack.threed4j.demo;

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
 * <p>Keys: W A S D move, E / Q up and down, Shift faster. Click into the window (or
 * press Tab) to look around with the mouse, Escape gives the mouse back; dragging
 * with the right button and the arrow keys work as well. 1 to 5 choose the internal resolution
 * (100, 75, 50, 33, 25 percent), F toggles smooth and blocky upscaling, R resets
 * the frame statistics, P saves {@code screenshot.png}, Escape quits.</p>
 *
 * <p>Arguments: {@code --benchmark} skips the title and flies a fixed camera path,
 * {@code --scale=0.5} sets the internal resolution, {@code --frames=N} stops after
 * N frames, {@code --screenshot=FILE} saves the last of those frames,
 * {@code --time=SECONDS} freezes the scene at that moment (comparable pictures),
 * {@code --no-title} skips the loading screen, {@code --no-vsync} runs uncapped.</p>
 */
public final class PlazaDemo implements Game {

    private static final float TITLE_SECONDS = 3.5f;
    private static final float[] RENDER_SCALES = {1f, 0.75f, 0.5f, 1f / 3f, 0.25f};
    private static final Key[] SCALE_KEYS = {Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4, Key.DIGIT_5};

    private static final Vec3 LIGHT_DIRECTION = new Vec3(-0.45f, -1.0f, -0.6f);
    private static final float FOG_START = 45f;
    private static final float FOG_END = 150f;
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

    // Scene
    private PlazaScene scene;
    private Shader sceneShader;
    private Shader spriteShader;
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

    private record Options(boolean benchmark, boolean showTitle, float renderScale, Double frozenTime,
                           Path finalScreenshot, long finalFrame) {
    }

    public static void main(String[] args) {
        EngineConfig config = EngineConfig.of("Nur zur ZIHR", 1280, 720);
        boolean benchmark = false;
        boolean showTitle = true;
        float scale = 1f;
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
            } else if (arg.equals("--no-title")) {
                showTitle = false;
            } else if (arg.equals("--no-vsync")) {
                config = config.withVsync(false);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        Engine.run(config, new PlazaDemo(new Options(benchmark, showTitle, scale, time, screenshot, config.maxFrames())));
    }

    // --------------------------------------------------------------------- init

    /** Loads only what the loading screen needs, so that it appears at once. */
    @Override
    public void init(EngineContext context) {
        GraphicsDevice graphics = context.graphics();
        graphics.setRenderScale(options.renderScale());
        imageShader = graphics.createShader("image", ShaderLayout.attributes(2, 2)
                .vec3("uScale")
                .vec3("uOffset")
                .vec3("uTint"));
        screenQuad = Meshes.screenQuad(graphics);
        titleImage = Textures.fromResource(graphics, "/images/loading.jpg", true);
        white = graphics.createTexture(1, 1, new int[]{0xFFFFFFFF}, false);
        if (!options.showTitle()) {
            loadScene(context);
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
        spriteAtlas = Textures.fromImage(graphics, SpriteAtlas.paint(), true);
        spriteShader.setTexture(spriteAtlas);
        scene = new PlazaScene(graphics);
        System.out.printf(Locale.ROOT, "Scene: %d buildings, %d triangles, %d sprites, 3 draw calls%n",
                scene.buildingCount, scene.cityTriangles, scene.spriteCount);
    }

    private void startPlaying(EngineContext context) {
        playing = true;
        context.frameStats().reset(); // loading must not count as slow frames
    }

    // ------------------------------------------------------------------- update

    @Override
    public void update(EngineContext context, double deltaSeconds) {
        Input input = context.input();
        if (input.wasPressed(Key.ESCAPE)) {
            // The first Escape gives the mouse back, the next one quits.
            if (context.window().isMouseCaptured()) {
                context.window().setMouseCaptured(false);
            } else {
                context.quit();
            }
        }
        if (input.wasPressed(Key.P)) {
            context.requestScreenshot(Path.of("screenshot.png"));
        }
        if (options.finalScreenshot() != null && context.frame() == Math.max(1, options.finalFrame())) {
            context.requestScreenshot(options.finalScreenshot());
        }

        if (!playing) {
            updateTitle(context, input);
            return;
        }

        sceneTime = options.frozenTime() != null ? options.frozenTime() : sceneTime + deltaSeconds;
        chooseResolution(context, input);
        if (options.benchmark()) {
            flyCameraPath();
        } else {
            if (input.wasPressed(MouseButton.LEFT) || input.wasPressed(Key.TAB)) {
                context.window().setMouseCaptured(!context.window().isMouseCaptured());
            }
            moveCamera(input, context.window().isMouseCaptured(), (float) deltaSeconds);
        }
        camera.orient(position, yaw, pitch);
    }

    private void updateTitle(EngineContext context, Input input) {
        if (titleShownSince < 0) {
            titleShownSince = context.timeSeconds();
            return; // let the first frame with the picture reach the screen before loading blocks
        }
        if (scene == null) {
            loadScene(context);
            return;
        }
        boolean skip = input.wasPressed(Key.SPACE) || input.wasPressed(Key.ENTER)
                || input.wasPressed(MouseButton.LEFT);
        if (skip || titleProgress(context) >= 1f) {
            startPlaying(context);
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
                graphics.setRenderScale(RENDER_SCALES[i]);
                context.frameStats().reset();
            }
        }
        if (input.wasPressed(Key.F)) {
            smoothUpscale = !smoothUpscale;
            graphics.setSmoothUpscale(smoothUpscale);
        }
        if (input.wasPressed(Key.R)) {
            context.frameStats().reset();
        }
    }

    private void moveCamera(Input input, boolean mouseCaptured, float step) {
        // Captured: the mouse turns the view directly. Otherwise drag with the right button held.
        if (mouseCaptured || input.isDown(MouseButton.RIGHT)) {
            yaw += input.mouseDeltaX() * LOOK_SPEED;
            pitch -= input.mouseDeltaY() * LOOK_SPEED;
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
        if (playing) {
            return;
        }
        GraphicsDevice graphics = context.graphics();
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

    private static float fitX(float windowAspect, float imageAspect) {
        return windowAspect > imageAspect ? imageAspect / windowAspect : 1f;
    }

    private static float fitY(float windowAspect, float imageAspect) {
        return windowAspect > imageAspect ? 1f : windowAspect / imageAspect;
    }

    private void drawImage(GraphicsDevice graphics, float scaleX, float scaleY, float centerX, float centerY, Vec3 tint) {
        imageShader.setUniform("uScale", new Vec3(scaleX, scaleY, 1f));
        imageShader.setUniform("uOffset", new Vec3(centerX, centerY, 0f));
        imageShader.setUniform("uTint", tint);
        graphics.draw(screenQuad, imageShader);
    }

    @Override
    public void dispose() {
        if (scene != null) {
            scene.close();
            spriteAtlas.close();
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
