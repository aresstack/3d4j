package com.aresstack.threed4j.platform.x11;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Bindings for the parts of Xlib and GLX the window needs (64-bit Linux).
 *
 * <p>C types map as follows: pointers ({@code Display*}, {@code GLXContext}, ...) are
 * {@code MemorySegment}; XIDs and atoms ({@code Window}, {@code Colormap},
 * {@code Atom}, {@code KeySym}) are {@code unsigned long} and therefore
 * {@code long}; {@code Bool} is {@code int}.</p>
 */
final class X11Api {

    private X11Api() {
    }

    private static final SymbolLookup X11 = Native.library("libX11.so.6", "libX11.so");
    private static final SymbolLookup GLX = Native.library("libGL.so.1", "libGL.so");

    // ---------------------------------------------------------------- constants

    static final int INPUT_OUTPUT = 1;
    static final int ALLOC_NONE = 0;
    static final int PROP_MODE_REPLACE = 0;

    static final long CW_BORDER_PIXEL = 1L << 3;
    static final long CW_EVENT_MASK = 1L << 11;
    static final long CW_COLORMAP = 1L << 13;

    static final long KEY_PRESS_MASK = 1L;
    static final long KEY_RELEASE_MASK = 1L << 1;
    static final long BUTTON_PRESS_MASK = 1L << 2;
    static final long BUTTON_RELEASE_MASK = 1L << 3;
    static final long POINTER_MOTION_MASK = 1L << 6;
    static final long STRUCTURE_NOTIFY_MASK = 1L << 17;
    static final long FOCUS_CHANGE_MASK = 1L << 21;
    static final int GRAB_MODE_ASYNC = 1;

    static final int KEY_PRESS = 2;
    static final int KEY_RELEASE = 3;
    static final int BUTTON_PRESS = 4;
    static final int BUTTON_RELEASE = 5;
    static final int MOTION_NOTIFY = 6;
    static final int FOCUS_OUT = 10;
    static final int CONFIGURE_NOTIFY = 22;
    static final int CLIENT_MESSAGE = 33;

    /** {@code sizeof(XEvent)}: a union padded to 24 longs. */
    static final long SIZEOF_XEVENT = 192;
    /** {@code sizeof(XSetWindowAttributes)}. */
    static final long SIZEOF_SET_WINDOW_ATTRIBUTES = 112;
    static final long SWA_BORDER_PIXEL = 24;
    static final long SWA_EVENT_MASK = 72;
    static final long SWA_COLORMAP = 96;

    /** {@code XVisualInfo}: only the fields that are read. */
    static final long SIZEOF_VISUAL_INFO = 64;
    static final long VI_VISUAL = 0;
    static final long VI_DEPTH = 20;

    /** {@code XConfigureEvent.width / .height}. */
    static final long CONFIGURE_WIDTH = 56;
    static final long CONFIGURE_HEIGHT = 60;
    /** {@code XButtonEvent} and {@code XMotionEvent}: pointer position; button number (1 left, 2 middle, 3 right). */
    static final long POINTER_X = 64;
    static final long POINTER_Y = 68;
    static final long BUTTON_NUMBER = 84;
    /** {@code XClientMessageEvent.data.l[0]}. */
    static final long CLIENT_MESSAGE_DATA = 56;

    static final int XK_SPACE = 0x0020;
    static final int XK_TAB = 0xff09;
    static final int XK_RETURN = 0xff0d;
    static final int XK_ESCAPE = 0xff1b;
    static final int XK_LEFT = 0xff51;
    static final int XK_UP = 0xff52;
    static final int XK_RIGHT = 0xff53;
    static final int XK_DOWN = 0xff54;
    static final int XK_SHIFT_L = 0xffe1;
    static final int XK_SHIFT_R = 0xffe2;
    static final int XK_CONTROL_L = 0xffe3;
    static final int XK_CONTROL_R = 0xffe4;

    static final int GLX_DOUBLEBUFFER = 5;
    static final int GLX_RED_SIZE = 8;
    static final int GLX_GREEN_SIZE = 9;
    static final int GLX_BLUE_SIZE = 10;
    static final int GLX_DEPTH_SIZE = 12;
    static final int GLX_STENCIL_SIZE = 13;
    static final int GLX_X_VISUAL_TYPE = 0x22;
    static final int GLX_TRUE_COLOR = 0x8002;
    static final int GLX_DRAWABLE_TYPE = 0x8010;
    static final int GLX_RENDER_TYPE = 0x8011;
    static final int GLX_X_RENDERABLE = 0x8012;
    static final int GLX_WINDOW_BIT = 0x0001;
    static final int GLX_RGBA_BIT = 0x0001;
    static final int GLX_CONTEXT_MAJOR_VERSION_ARB = 0x2091;
    static final int GLX_CONTEXT_MINOR_VERSION_ARB = 0x2092;
    static final int GLX_CONTEXT_PROFILE_MASK_ARB = 0x9126;
    static final int GLX_CONTEXT_CORE_PROFILE_BIT_ARB = 0x0001;

    // --------------------------------------------------------------------- Xlib

    private static final MethodHandle XOpenDisplay = x11("XOpenDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle XCloseDisplay = x11("XCloseDisplay", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle XDefaultScreen = x11("XDefaultScreen", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle XRootWindow = x11("XRootWindow", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle XCreateColormap = x11("XCreateColormap",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle XFreeColormap = x11("XFreeColormap", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle XCreateWindow = x11("XCreateWindow",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                    JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    private static final MethodHandle XDestroyWindow = x11("XDestroyWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle XMapWindow = x11("XMapWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle XStoreName = x11("XStoreName", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    private static final MethodHandle XInternAtom = x11("XInternAtom", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle XChangeProperty = x11("XChangeProperty",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle XSetWMProtocols = x11("XSetWMProtocols",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle XPending = x11("XPending", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle XNextEvent = x11("XNextEvent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle XLookupKeysym = x11("XLookupKeysym", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle XkbSetDetectableAutoRepeat = x11("XkbSetDetectableAutoRepeat",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle XGrabPointer = x11("XGrabPointer",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG,
                    JAVA_LONG, JAVA_LONG));
    private static final MethodHandle XUngrabPointer = x11("XUngrabPointer",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle XWarpPointer = x11("XWarpPointer",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                    JAVA_INT, JAVA_INT));
    private static final MethodHandle XQueryPointer = x11("XQueryPointer",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                    ADDRESS, ADDRESS));
    private static final MethodHandle XCreateBitmapFromData = x11("XCreateBitmapFromData",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle XCreatePixmapCursor = x11("XCreatePixmapCursor",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle XFree = x11("XFree", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle XFlush = x11("XFlush", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    // ---------------------------------------------------------------------- GLX

    private static final MethodHandle glXChooseFBConfig = glx("glXChooseFBConfig",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle glXGetVisualFromFBConfig = glx("glXGetVisualFromFBConfig",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle glXMakeCurrent = glx("glXMakeCurrent",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    private static final MethodHandle glXSwapBuffers = glx("glXSwapBuffers", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
    private static final MethodHandle glXDestroyContext = glx("glXDestroyContext", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle glXGetProcAddressARB = glx("glXGetProcAddressARB", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle glXQueryExtensionsString = glx("glXQueryExtensionsString",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

    /** Signature of {@code glXCreateContextAttribsARB}, which is only reachable as a function pointer. */
    static final FunctionDescriptor CREATE_CONTEXT_ATTRIBS =
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS);
    /** {@code void glXSwapIntervalEXT(Display*, GLXDrawable, int)}. */
    static final FunctionDescriptor SWAP_INTERVAL_EXT = FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT);
    /** {@code int glXSwapIntervalMESA(unsigned)} and {@code int glXSwapIntervalSGI(int)}. */
    static final FunctionDescriptor SWAP_INTERVAL_INT = FunctionDescriptor.of(JAVA_INT, JAVA_INT);

    private static MethodHandle x11(String name, FunctionDescriptor descriptor) {
        return Native.downcall(X11, name, descriptor);
    }

    private static MethodHandle glx(String name, FunctionDescriptor descriptor) {
        return Native.downcall(GLX, name, descriptor);
    }

    /** Finds an OpenGL or GLX function: library export first, then the GLX extension mechanism. */
    static MemorySegment glFunction(String name) {
        MemorySegment exported = Native.symbol(GLX, name);
        if (!Native.isNull(exported)) {
            return exported;
        }
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            return (MemorySegment) glXGetProcAddressARB.invokeExact(Native.cString(arena, name));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ----------------------------------------------------------- typed wrappers

    static MemorySegment openDisplay() {
        try {
            return (MemorySegment) XOpenDisplay.invokeExact(MemorySegment.NULL);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void closeDisplay(MemorySegment display) {
        try {
            int ignored = (int) XCloseDisplay.invokeExact(display);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static int defaultScreen(MemorySegment display) {
        try {
            return (int) XDefaultScreen.invokeExact(display);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long rootWindow(MemorySegment display, int screen) {
        try {
            return (long) XRootWindow.invokeExact(display, screen);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long createColormap(MemorySegment display, long window, MemorySegment visual, int alloc) {
        try {
            return (long) XCreateColormap.invokeExact(display, window, visual, alloc);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void freeColormap(MemorySegment display, long colormap) {
        try {
            int ignored = (int) XFreeColormap.invokeExact(display, colormap);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long createWindow(MemorySegment display, long parent, int width, int height, int depth,
                             MemorySegment visual, long valueMask, MemorySegment attributes) {
        try {
            return (long) XCreateWindow.invokeExact(display, parent, 0, 0, width, height, 0, depth,
                    INPUT_OUTPUT, visual, valueMask, attributes);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void destroyWindow(MemorySegment display, long window) {
        try {
            int ignored = (int) XDestroyWindow.invokeExact(display, window);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void mapWindow(MemorySegment display, long window) {
        try {
            int ignored = (int) XMapWindow.invokeExact(display, window);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void storeName(MemorySegment display, long window, MemorySegment name) {
        try {
            int ignored = (int) XStoreName.invokeExact(display, window, name);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long internAtom(MemorySegment display, MemorySegment name) {
        try {
            return (long) XInternAtom.invokeExact(display, name, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Replaces a property that consists of 8-bit data. */
    static void changeProperty8(MemorySegment display, long window, long property, long type,
                                MemorySegment data, int length) {
        try {
            int ignored = (int) XChangeProperty.invokeExact(display, window, property, type, 8,
                    PROP_MODE_REPLACE, data, length);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void setWMProtocols(MemorySegment display, long window, MemorySegment atoms, int count) {
        try {
            int ignored = (int) XSetWMProtocols.invokeExact(display, window, atoms, count);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static int pending(MemorySegment display) {
        try {
            return (int) XPending.invokeExact(display);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void nextEvent(MemorySegment display, MemorySegment event) {
        try {
            int ignored = (int) XNextEvent.invokeExact(display, event);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long lookupKeysym(MemorySegment keyEvent) {
        try {
            return (long) XLookupKeysym.invokeExact(keyEvent, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void setDetectableAutoRepeat(MemorySegment display) {
        try {
            int ignored = (int) XkbSetDetectableAutoRepeat.invokeExact(display, 1, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /**
     * Holds the pointer inside the window and shows the given cursor until
     * {@link #ungrabPointer}.
     */
    static void grabPointer(MemorySegment display, long window, long eventMask, long cursor) {
        try {
            int ignored = (int) XGrabPointer.invokeExact(display, window, 1, (int) eventMask, GRAB_MODE_ASYNC,
                    GRAB_MODE_ASYNC, window, cursor, 0L);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void ungrabPointer(MemorySegment display) {
        try {
            int ignored = (int) XUngrabPointer.invokeExact(display, 0L);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Moves the pointer to a position inside the window. */
    static void warpPointer(MemorySegment display, long window, int x, int y) {
        try {
            int ignored = (int) XWarpPointer.invokeExact(display, 0L, window, 0, 0, 0, 0, x, y);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /**
     * Asks for the pointer position relative to the window.
     *
     * @param scratch at least 40 bytes; afterwards x is the int at offset 24, y at offset 28
     */
    static void queryPointer(MemorySegment display, long window, MemorySegment scratch) {
        try {
            int ignored = (int) XQueryPointer.invokeExact(display, window,
                    scratch, scratch.asSlice(8), scratch.asSlice(16), scratch.asSlice(20),
                    scratch.asSlice(24), scratch.asSlice(28), scratch.asSlice(32));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** X has no "hide the pointer": this creates a cursor that is one transparent pixel. */
    static long createInvisibleCursor(MemorySegment display, long window, MemorySegment zeros) {
        try {
            long bitmap = (long) XCreateBitmapFromData.invokeExact(display, window, zeros, 1, 1);
            return (long) XCreatePixmapCursor.invokeExact(display, bitmap, bitmap, zeros, zeros, 0, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void free(MemorySegment pointer) {
        try {
            int ignored = (int) XFree.invokeExact(pointer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void flush(MemorySegment display) {
        try {
            int ignored = (int) XFlush.invokeExact(display);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment chooseFBConfig(MemorySegment display, int screen, MemorySegment attributes, MemorySegment count) {
        try {
            return (MemorySegment) glXChooseFBConfig.invokeExact(display, screen, attributes, count);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment getVisualFromFBConfig(MemorySegment display, MemorySegment config) {
        try {
            return (MemorySegment) glXGetVisualFromFBConfig.invokeExact(display, config);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static boolean makeCurrent(MemorySegment display, long drawable, MemorySegment context) {
        try {
            return (int) glXMakeCurrent.invokeExact(display, drawable, context) != 0;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void swapBuffers(MemorySegment display, long drawable) {
        try {
            glXSwapBuffers.invokeExact(display, drawable);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void destroyContext(MemorySegment display, MemorySegment context) {
        try {
            glXDestroyContext.invokeExact(display, context);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static String queryExtensionsString(MemorySegment display, int screen) {
        try {
            String extensions = Native.readCString((MemorySegment) glXQueryExtensionsString.invokeExact(display, screen));
            return extensions != null ? extensions : "";
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }
}
