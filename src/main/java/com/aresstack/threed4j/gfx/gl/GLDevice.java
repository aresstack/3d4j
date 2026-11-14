package com.aresstack.threed4j.gfx.gl;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.Mesh;
import com.aresstack.threed4j.gfx.PixelImage;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.gfx.Texture;
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

    private float renderScale = 1f;
    private boolean smoothUpscale = true;
    private boolean overlay;

    // The scene image: a framebuffer object with a color and a depth buffer.
    private int sceneFramebuffer;
    private int sceneColor;
    private int sceneDepth;
    private int sceneWidth;
    private int sceneHeight;

    public GLDevice(GLWindow window) {
        this.window = window;
        this.width = window.width();
        this.height = window.height();
        glEnable(GL_CULL_FACE); // back faces; counter-clockwise is front by default
    }

    @Override
    public String description() {
        return "OpenGL " + glGetString(GL_VERSION) + " | " + glGetString(GL_RENDERER);
    }

    @Override
    public void beginFrame(int windowWidth, int windowHeight) {
        width = windowWidth;
        height = windowHeight;
        int wantedWidth = Math.max(1, Math.round(width * renderScale));
        int wantedHeight = Math.max(1, Math.round(height * renderScale));
        if (sceneFramebuffer == 0 || wantedWidth != sceneWidth || wantedHeight != sceneHeight) {
            createSceneTarget(wantedWidth, wantedHeight);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFramebuffer);
        glViewport(0, 0, sceneWidth, sceneHeight);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        overlay = false;
    }

    private void createSceneTarget(int newWidth, int newHeight) {
        deleteSceneTarget();
        sceneWidth = newWidth;
        sceneHeight = newHeight;

        sceneColor = glGenRenderbuffer();
        glBindRenderbuffer(GL_RENDERBUFFER, sceneColor);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, sceneWidth, sceneHeight);
        sceneDepth = glGenRenderbuffer();
        glBindRenderbuffer(GL_RENDERBUFFER, sceneDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, sceneWidth, sceneHeight);

        sceneFramebuffer = glGenFramebuffer();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFramebuffer);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, sceneColor);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, sceneDepth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("The OpenGL driver rejects the scene framebuffer");
        }
    }

    private void deleteSceneTarget() {
        if (sceneFramebuffer != 0) {
            glDeleteFramebuffer(sceneFramebuffer);
            glDeleteRenderbuffer(sceneColor);
            glDeleteRenderbuffer(sceneDepth);
            sceneFramebuffer = 0;
        }
    }

    @Override
    public void clear(float red, float green, float blue) {
        glClearColor(red, green, blue, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public void beginOverlay() {
        if (overlay) {
            return;
        }
        overlay = true;
        glBindFramebuffer(GL_READ_FRAMEBUFFER, sceneFramebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, 0, sceneWidth, sceneHeight, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT, smoothUpscale ? GL_LINEAR : GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        // The overlay is 2D drawn back to front: transparent pixels let show what is below.
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public PixelImage readPixels() {
        beginOverlay();
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
        beginOverlay();
        window.swapBuffers();
    }

    @Override
    public void setRenderScale(float scale) {
        renderScale = Math.max(0.1f, Math.min(1f, scale));
    }

    @Override
    public float renderScale() {
        return renderScale;
    }

    @Override
    public void setSmoothUpscale(boolean smooth) {
        smoothUpscale = smooth;
    }

    @Override
    public int sceneWidth() {
        return sceneWidth;
    }

    @Override
    public int sceneHeight() {
        return sceneHeight;
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
    public Texture createTexture(int textureWidth, int textureHeight, int[] argb, boolean smooth) {
        return new GLTexture(textureWidth, textureHeight, argb, smooth);
    }

    /** OpenGL belongs to the render thread; putting the pixels in its order does not. */
    @Override
    public PendingTexture prepareTexture(int textureWidth, int textureHeight, int[] argb, boolean smooth) {
        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofShared();
        java.lang.foreign.MemorySegment pixels = GLTexture.pixels(arena, argb);
        return () -> {
            try (arena) {
                return new GLTexture(textureWidth, textureHeight, pixels, smooth);
            }
        };
    }

    @Override
    public void draw(Mesh mesh, Shader shader) {
        ((GLShader) shader).use();
        ((GLMesh) mesh).draw();
    }

    @Override
    public void close() {
        deleteSceneTarget();
        // The window owns the context and destroys it.
    }
}
