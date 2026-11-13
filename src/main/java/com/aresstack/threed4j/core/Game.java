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

    /** Draws the current state: clear, then draw meshes. */
    void render(EngineContext context);

    /** Called once after the last frame: release GPU resources. */
    default void dispose() {
    }
}
