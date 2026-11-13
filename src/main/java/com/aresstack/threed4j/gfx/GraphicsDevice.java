package com.aresstack.threed4j.gfx;

/**
 * The graphics API as the engine sees it. There is one implementation per native
 * API: OpenGL 3.3 ({@code gfx.gl}) and Direct3D 11 ({@code gfx.d3d11}).
 *
 * <p>Both behave the same: depth testing is on, back faces are culled, triangles are
 * front-facing when their vertices appear counter-clockwise, and matrices follow the
 * OpenGL conventions of {@link com.aresstack.threed4j.math.Mat4}.</p>
 */
public interface GraphicsDevice extends AutoCloseable {

    /** Human-readable name of the API, the adapter and the driver version. */
    String description();

    // ------------------------------------------------------------------- frame
    //
    // A frame has two phases. The scene is rendered into an offscreen image whose
    // size is the window size times the render scale. beginOverlay() stretches that
    // image over the window; what is drawn afterwards (menus, loading screen, HUD)
    // is drawn at full window resolution, without depth test.

    /** Starts the scene phase of a frame for a window of the given size. */
    void beginFrame(int windowWidth, int windowHeight);

    /** Clears color and depth of the scene. */
    void clear(float red, float green, float blue);

    /**
     * Ends the scene phase: copies the scene to the window and switches to overlay
     * drawing. Does nothing if the frame is already in the overlay phase.
     */
    void beginOverlay();

    /** Copies the finished frame as the user will see it. Ends the scene phase. */
    PixelImage readPixels();

    /** Shows the finished frame. Ends the scene phase if that has not happened yet. */
    void present();

    /**
     * Sets the size of the scene image relative to the window, between 0.1 and 1.
     * Rendering fewer pixels is the main lever against a slow or software renderer.
     */
    void setRenderScale(float scale);

    float renderScale();

    /** {@code true} blends neighboring pixels when the scene is stretched, {@code false} keeps them blocky. */
    void setSmoothUpscale(boolean smooth);

    /** Width of the scene image in pixels (valid after {@link #beginFrame}). */
    int sceneWidth();

    int sceneHeight();

    // --------------------------------------------------------------- resources

    /**
     * Loads the shader {@code name} from the classpath: {@code /shaders/name.vert} and
     * {@code .frag} for OpenGL, {@code /shaders/name.hlsl} for Direct3D.
     */
    Shader createShader(String name, ShaderLayout layout);

    /**
     * Uploads indexed triangles. Vertices are interleaved floats;
     * {@code attributeSizes} gives the number of floats per attribute, e.g.
     * {@code 3, 3, 3} for position, normal and color.
     */
    Mesh createMesh(float[] vertices, int[] indices, int... attributeSizes);

    /**
     * @param argb   one {@code 0xAARRGGBB} value per pixel, top row first; texture
     *               coordinate (0, 0) is the top left corner
     * @param smooth blend neighboring pixels when the texture is magnified
     */
    Texture createTexture(int width, int height, int[] argb, boolean smooth);

    /** Draws the mesh with the shader and the uniform values and texture set on it. */
    void draw(Mesh mesh, Shader shader);

    @Override
    void close();
}
