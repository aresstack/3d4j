package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.core.Engine;
import com.aresstack.threed4j.core.EngineConfig;
import com.aresstack.threed4j.core.EngineContext;
import com.aresstack.threed4j.core.Game;
import com.aresstack.threed4j.gfx.Camera;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.Meshes;
import com.aresstack.threed4j.gfx.ShaderProgram;
import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;

import java.nio.file.Path;

import static com.aresstack.threed4j.gl.GL.*;

/**
 * A lit, rotating cube.
 *
 * <p>Keys: arrow keys orbit the camera, W/S zoom, Space pauses the rotation,
 * P saves {@code screenshot.png}, Escape quits.</p>
 *
 * <p>Arguments: {@code --frames=N} stops after N frames, {@code --screenshot=FILE}
 * saves the last of those frames, {@code --no-vsync} runs uncapped.</p>
 */
public final class CubeDemo implements Game {

    private static final Vec3 LIGHT_DIRECTION = new Vec3(-0.4f, -1.0f, -0.6f);

    private final Path finalScreenshot;
    private final long finalFrame;

    private ShaderProgram shader;
    private Mesh cube;
    private final Camera camera = new Camera();

    private double cubeAngle;
    private boolean paused;
    private float orbitYaw = 0.6f;
    private float orbitPitch = 0.45f;
    private float orbitDistance = 3.2f;

    private CubeDemo(Path finalScreenshot, long finalFrame) {
        this.finalScreenshot = finalScreenshot;
        this.finalFrame = finalFrame;
    }

    public static void main(String[] args) {
        EngineConfig config = EngineConfig.of("3d4j – Cube Demo", 1280, 720);
        Path screenshot = null;
        for (String arg : args) {
            if (arg.startsWith("--frames=")) {
                config = config.withMaxFrames(Long.parseLong(arg.substring("--frames=".length())));
            } else if (arg.startsWith("--screenshot=")) {
                screenshot = Path.of(arg.substring("--screenshot=".length()));
            } else if (arg.equals("--no-vsync")) {
                config = config.withVsync(false);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        Engine.run(config, new CubeDemo(screenshot, config.maxFrames()));
    }

    @Override
    public void init(EngineContext context) {
        shader = ShaderProgram.fromResources("/shaders/basic.vert", "/shaders/basic.frag");
        cube = Meshes.cube();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glClearColor(0.08f, 0.09f, 0.12f, 1f);
    }

    @Override
    public void update(EngineContext context, double deltaSeconds) {
        Input input = context.input();
        float step = (float) deltaSeconds;

        if (input.wasPressed(Key.ESCAPE)) {
            context.quit();
        }
        if (input.wasPressed(Key.SPACE)) {
            paused = !paused;
        }
        if (input.wasPressed(Key.P)) {
            context.requestScreenshot(Path.of("screenshot.png"));
        }
        if (input.isDown(Key.LEFT)) {
            orbitYaw -= 1.5f * step;
        }
        if (input.isDown(Key.RIGHT)) {
            orbitYaw += 1.5f * step;
        }
        if (input.isDown(Key.UP)) {
            orbitPitch = Math.min(1.5f, orbitPitch + 1.5f * step);
        }
        if (input.isDown(Key.DOWN)) {
            orbitPitch = Math.max(-1.5f, orbitPitch - 1.5f * step);
        }
        if (input.isDown(Key.W)) {
            orbitDistance = Math.max(1.5f, orbitDistance - 3f * step);
        }
        if (input.isDown(Key.S)) {
            orbitDistance = Math.min(20f, orbitDistance + 3f * step);
        }

        if (!paused) {
            cubeAngle += deltaSeconds;
        }

        Vec3 eye = new Vec3(
                (float) (Math.cos(orbitPitch) * Math.sin(orbitYaw)),
                (float) Math.sin(orbitPitch),
                (float) (Math.cos(orbitPitch) * Math.cos(orbitYaw))).scale(orbitDistance);
        camera.lookAt(eye, Vec3.ZERO);

        if (finalScreenshot != null && context.frame() == Math.max(1, finalFrame)) {
            context.requestScreenshot(finalScreenshot);
        }
    }

    @Override
    public void render(EngineContext context) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        Mat4 model = Mat4.rotationY((float) cubeAngle).multiply(Mat4.rotationX((float) (cubeAngle * 0.6)));

        shader.use();
        shader.setUniform("uModel", model);
        shader.setUniform("uView", camera.viewMatrix());
        shader.setUniform("uProjection", camera.projectionMatrix(context.aspectRatio()));
        shader.setUniform("uLightDirection", LIGHT_DIRECTION);
        cube.draw();
    }

    @Override
    public void dispose() {
        cube.close();
        shader.close();
    }
}
