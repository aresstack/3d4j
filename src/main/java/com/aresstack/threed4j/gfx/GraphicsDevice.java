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

    /** Starts a frame: adapts the back buffer to the window size and sets the viewport. */
    void beginFrame(int width, int height);

    /** Clears color and depth. */
    void clear(float red, float green, float blue);

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

    /** Draws the mesh with the shader and the uniform values set on it. */
    void draw(Mesh mesh, Shader shader);

    /** Copies the frame rendered so far. Call before {@link #present()}. */
    PixelImage readPixels();

    /** Shows the finished frame. */
    void present();

    @Override
    void close();
}
