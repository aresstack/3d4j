package com.aresstack.threed4j.gfx.gl;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.PixelImage;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.platform.GLWindow;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.aresstack.threed4j.gl.GL.*;

/** OpenGL 3.3 core backend. The context belongs to the window and is already current. */
public final class GLDevice implements GraphicsDevice {

    private final GLWindow window;
    private int width;
    private int height;

    public GLDevice(GLWindow window) {
        this.window = window;
        this.width = window.width();
        this.height = window.height();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE); // back faces; counter-clockwise is front by default
    }

    @Override
    public String description() {
        return "OpenGL " + glGetString(GL_VERSION) + " | " + glGetString(GL_RENDERER);
    }

    @Override
    public void beginFrame(int width, int height) {
        this.width = width;
        this.height = height;
        glViewport(0, 0, width, height);
    }

    @Override
    public void clear(float red, float green, float blue) {
        glClearColor(red, green, blue, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public Shader createShader(String name, ShaderLayout layout) {
        return new GLShader(name, layout);
    }

    @Override
    public Mesh createMesh(float[] vertices, int[] indices, int... attributeSizes) {
        return new GLMesh(vertices, indices, attributeSizes);
    }

    @Override
    public void draw(Mesh mesh, Shader shader) {
        ((GLShader) shader).use();
        ((GLMesh) mesh).draw();
    }

    @Override
    public PixelImage readPixels() {
        int[] rgb = new int[width * height];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixels = Native.bytes(arena, (long) width * height * 4);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < height; y++) {
                long row = (long) (height - 1 - y) * width * 4; // OpenGL rows start at the bottom
                for (int x = 0; x < width; x++) {
                    long offset = row + x * 4L;
                    int r = pixels.get(ValueLayout.JAVA_BYTE, offset) & 0xFF;
                    int g = pixels.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xFF;
                    int b = pixels.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xFF;
                    rgb[y * width + x] = (r << 16) | (g << 8) | b;
                }
            }
        }
        return new PixelImage(width, height, rgb);
    }

    @Override
    public void present() {
        window.swapBuffers();
    }

    @Override
    public void close() {
        // The window owns the context and destroys it.
    }
}
