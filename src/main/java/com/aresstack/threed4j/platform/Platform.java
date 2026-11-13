package com.aresstack.threed4j.platform;

import com.aresstack.threed4j.platform.win32.Win32Window;
import com.aresstack.threed4j.platform.x11.X11Window;

import java.util.Locale;

/** Picks the window implementation for the operating system the JVM runs on. */
public final class Platform {

    private Platform() {
    }

    public static Window createWindow(WindowConfig config) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return new Win32Window(config);
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return new X11Window(config);
        }
        throw new UnsupportedOperationException("3d4j has no window backend for " + os
                + " yet (supported: Windows, Linux/X11)");
    }
}
