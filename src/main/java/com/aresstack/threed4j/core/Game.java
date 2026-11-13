package com.aresstack.threed4j.core;

/**
 * What a game implements. All methods are called on the main thread, and
 * {@link EngineContext#graphics()} may be used in every one of them.
 */
public interface Game {

    /** Called once before the first frame: load shaders and meshes. */
    void init(EngineContext context);

    /**
     * Advances the game state.
     *
     * @param deltaSeconds time since the previous frame, capped so that a long pause
     *                     (window drag, breakpoint) does not cause a huge jump
     */
    void update(EngineContext context, double deltaSeconds);

    /**
     * Draws the 3D scene: clear, then draw meshes. This is rendered at the scene
     * resolution (window size times render scale) with depth test.
     */
    void render(EngineContext context);

    /**
     * Draws what lies on top of the scene at full window resolution, without depth
     * test: loading screen, menus, HUD.
     */
    default void renderOverlay(EngineContext context) {
    }

    /** Called once after the last frame: release GPU resources. */
    default void dispose() {
    }
}
