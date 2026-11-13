package com.aresstack.threed4j.core;

import com.aresstack.threed4j.gfx.GraphicsDevice;
import com.aresstack.threed4j.gfx.d3d11.D3D11Device;
import com.aresstack.threed4j.gfx.gl.GLDevice;
import com.aresstack.threed4j.platform.Window;
import com.aresstack.threed4j.platform.WindowConfig;
import com.aresstack.threed4j.platform.win32.Win32Window;
import com.aresstack.threed4j.platform.x11.X11Window;

import java.util.Locale;

/**
 * A window together with the graphics device that renders into it.
 *
 * <p>Which combination is used depends on the operating system and on the system
 * property {@code threed4j.backend}:</p>
 * <ul>
 *   <li>{@code auto} (default) – Windows: Direct3D 11, and OpenGL only if that fails;
 *       Linux: OpenGL</li>
 *   <li>{@code d3d11} – Direct3D 11 (Windows only)</li>
 *   <li>{@code opengl} – OpenGL 3.3</li>
 * </ul>
 */
record Display(Window window, GraphicsDevice graphics) implements AutoCloseable {

    static final String BACKEND_PROPERTY = "threed4j.backend";

    static Display open(WindowConfig config) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String backend = System.getProperty(BACKEND_PROPERTY, "auto").toLowerCase(Locale.ROOT);
        if (!backend.equals("auto") && !backend.equals("d3d11") && !backend.equals("opengl")) {
            throw new IllegalArgumentException(BACKEND_PROPERTY + " must be auto, d3d11 or opengl, not " + backend);
        }

        if (os.startsWith("windows")) {
            if (backend.equals("opengl")) {
                return openWindowsOpenGL(config);
            }
            try {
                return openDirect3D(config);
            } catch (RuntimeException | LinkageError direct3DFailure) {
                if (backend.equals("d3d11")) {
                    throw direct3DFailure;
                }
                try {
                    return openWindowsOpenGL(config);
                } catch (RuntimeException openGLFailure) {
                    openGLFailure.addSuppressed(direct3DFailure);
                    throw openGLFailure;
                }
            }
        }
        if (os.contains("linux") || os.contains("bsd")) {
            if (backend.equals("d3d11")) {
                throw new UnsupportedOperationException("Direct3D 11 exists on Windows only");
            }
            X11Window window = new X11Window(config);
            return new Display(window, new GLDevice(window));
        }
        throw new UnsupportedOperationException("3d4j has no window backend for " + os
                + " yet (supported: Windows, Linux/X11)");
    }

    private static Display openDirect3D(WindowConfig config) {
        Win32Window window = new Win32Window(config, false);
        try {
            return new Display(window, new D3D11Device(window, config.vsync()));
        } catch (RuntimeException | LinkageError e) {
            window.close();
            throw e;
        }
    }

    private static Display openWindowsOpenGL(WindowConfig config) {
        Win32Window window = new Win32Window(config, true);
        try {
            return new Display(window, new GLDevice(window));
        } catch (RuntimeException | LinkageError e) {
            window.close();
            throw e;
        }
    }

    @Override
    public void close() {
        graphics.close();
        window.close();
    }
}
