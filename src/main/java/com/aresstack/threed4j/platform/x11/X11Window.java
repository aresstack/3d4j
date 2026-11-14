package com.aresstack.threed4j.platform.x11;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gl.GLLoader;
import com.aresstack.threed4j.platform.Input;
import com.aresstack.threed4j.platform.Key;
import com.aresstack.threed4j.platform.MouseButton;
import com.aresstack.threed4j.platform.GLWindow;
import com.aresstack.threed4j.platform.WindowConfig;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static com.aresstack.threed4j.platform.x11.X11Api.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Linux window: Xlib for the window and its events, GLX for the OpenGL context. */
public final class X11Window implements GLWindow {

    private final Arena arena = Arena.ofConfined();
    private final Input input = new Input();

    private final MemorySegment display;
    private final long window;
    private final long colormap;
    private final MemorySegment context;
    private final long wmDeleteWindow;
    private final long netWmName;
    private final long utf8String;
    private final MemorySegment event;
    private final MemorySegment textBuffer;

    private final int screen;
    private final long root;
    private boolean fullscreen;
    /** The size the window had before it filled the screen. */
    private int windowedWidth;
    private int windowedHeight;
    private boolean focused = true;
    private boolean mouseCaptured;
    private final com.aresstack.threed4j.platform.RelativeMouse relativeMouse = new com.aresstack.threed4j.platform.RelativeMouse();
    private long invisibleCursor;

    private int width;
    private int height;
    private boolean closeRequested;
    private boolean closed;

    public X11Window(WindowConfig config) {
        display = openDisplay();
        if (Native.isNull(display)) {
            throw new IllegalStateException("Cannot open the X display (is DISPLAY set?)");
        }
        screen = defaultScreen(display);
        root = rootWindow(display, screen);
        width = config.width();
        height = config.height();
        event = Native.bytes(arena, SIZEOF_XEVENT);
        textBuffer = Native.bytes(arena, 16);

        try (Arena temp = Arena.ofConfined()) {
            MemorySegment fbConfig = chooseFramebufferConfig(temp, screen);

            MemorySegment visualInfo = getVisualFromFBConfig(display, fbConfig);
            if (Native.isNull(visualInfo)) {
                throw new IllegalStateException("The framebuffer configuration has no X visual");
            }
            visualInfo = Native.sized(visualInfo, SIZEOF_VISUAL_INFO);
            MemorySegment visual = visualInfo.get(ADDRESS, VI_VISUAL);
            int depth = visualInfo.get(JAVA_INT, VI_DEPTH);
            free(visualInfo);

            colormap = createColormap(display, root, visual, ALLOC_NONE);
            MemorySegment attributes = Native.bytes(temp, SIZEOF_SET_WINDOW_ATTRIBUTES);
            attributes.set(JAVA_LONG, SWA_BORDER_PIXEL, 0L);
            attributes.set(JAVA_LONG, SWA_EVENT_MASK, KEY_PRESS_MASK | KEY_RELEASE_MASK | BUTTON_PRESS_MASK | BUTTON_RELEASE_MASK
                    | POINTER_MOTION_MASK | STRUCTURE_NOTIFY_MASK | FOCUS_CHANGE_MASK);
            attributes.set(JAVA_LONG, SWA_COLORMAP, colormap);
            window = createWindow(display, root, width, height, depth, visual,
                    CW_BORDER_PIXEL | CW_EVENT_MASK | CW_COLORMAP, attributes);
            if (window == 0L) {
                throw new IllegalStateException("XCreateWindow failed");
            }

            // Ask the window manager to send a message instead of killing the connection on close.
            wmDeleteWindow = internAtom(display, Native.cString(temp, "WM_DELETE_WINDOW"));
            MemorySegment protocols = temp.allocate(JAVA_LONG);
            protocols.set(JAVA_LONG, 0, wmDeleteWindow);
            setWMProtocols(display, window, protocols, 1);

            netWmName = internAtom(display, Native.cString(temp, "_NET_WM_NAME"));
            utf8String = internAtom(display, Native.cString(temp, "UTF8_STRING"));
            setTitle(config.title());

            // Without this, a held key produces release/press pairs instead of repeated presses.
            setDetectableAutoRepeat(display);
            mapWindow(display, window);

            context = createCoreContext(temp, fbConfig);
            if (!makeCurrent(display, window, context)) {
                throw new IllegalStateException("glXMakeCurrent failed");
            }
        }

        GLLoader.install(X11Api::glFunction);
        setSwapInterval(screen, config.vsync() ? 1 : 0);
    }

    private MemorySegment chooseFramebufferConfig(Arena temp, int screen) {
        MemorySegment attributes = Native.ints(temp,
                GLX_X_RENDERABLE, 1,
                GLX_DRAWABLE_TYPE, GLX_WINDOW_BIT,
                GLX_RENDER_TYPE, GLX_RGBA_BIT,
                GLX_X_VISUAL_TYPE, GLX_TRUE_COLOR,
                GLX_RED_SIZE, 8,
                GLX_GREEN_SIZE, 8,
                GLX_BLUE_SIZE, 8,
                GLX_DEPTH_SIZE, 24,
                GLX_STENCIL_SIZE, 8,
                GLX_DOUBLEBUFFER, 1,
                0);
        MemorySegment count = temp.allocate(JAVA_INT);
        MemorySegment configs = chooseFBConfig(display, screen, attributes, count);
        if (Native.isNull(configs) || count.get(JAVA_INT, 0) == 0) {
            throw new IllegalStateException("No matching GLX framebuffer configuration (is GLX available?)");
        }
        // The list is sorted best match first.
        MemorySegment best = Native.sized(configs, ADDRESS.byteSize()).get(ADDRESS, 0);
        free(configs);
        return best;
    }

    private MemorySegment createCoreContext(Arena temp, MemorySegment fbConfig) {
        MemorySegment function = glFunction("glXCreateContextAttribsARB");
        if (Native.isNull(function)) {
            throw new IllegalStateException("GLX_ARB_create_context is missing: cannot create an OpenGL 3.3 core context");
        }
        MethodHandle createContextAttribs = Native.downcall(function, CREATE_CONTEXT_ATTRIBS);
        MemorySegment attributes = Native.ints(temp,
                GLX_CONTEXT_MAJOR_VERSION_ARB, 3,
                GLX_CONTEXT_MINOR_VERSION_ARB, 3,
                GLX_CONTEXT_PROFILE_MASK_ARB, GLX_CONTEXT_CORE_PROFILE_BIT_ARB,
                0);
        MemorySegment created;
        try {
            created = (MemorySegment) createContextAttribs.invokeExact(display, fbConfig, MemorySegment.NULL, 1, attributes);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (Native.isNull(created)) {
            throw new IllegalStateException("The driver cannot create an OpenGL 3.3 core context");
        }
        return created;
    }

    /** Vertical sync is an optional GLX extension with three historical variants. */
    private void setSwapInterval(int screen, int interval) {
        String extensions = queryExtensionsString(display, screen);
        try {
            if (extensions.contains("GLX_EXT_swap_control")) {
                Native.downcall(glFunction("glXSwapIntervalEXT"), SWAP_INTERVAL_EXT)
                        .invokeExact(display, window, interval);
            } else if (extensions.contains("GLX_MESA_swap_control")) {
                int ignored = (int) Native.downcall(glFunction("glXSwapIntervalMESA"), SWAP_INTERVAL_INT)
                        .invokeExact(interval);
            } else if (extensions.contains("GLX_SGI_swap_control") && interval > 0) {
                int ignored = (int) Native.downcall(glFunction("glXSwapIntervalSGI"), SWAP_INTERVAL_INT)
                        .invokeExact(interval);
            }
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    @Override
    public void pollEvents() {
        while (pending(display) > 0) {
            nextEvent(display, event);
            int type = event.get(JAVA_INT, 0);
            switch (type) {
                case KEY_PRESS, KEY_RELEASE -> {
                    Key key = mapKey(lookupKeysym(event));
                    if (key == Key.ENTER && (event.get(JAVA_INT, KEY_STATE) & MOD1_MASK) != 0) {
                        if (type == KEY_PRESS) {
                            setFullscreen(!fullscreen); // Alt+Enter
                        }
                        continue;
                    }
                    input.onKey(key, type == KEY_PRESS);
                    if (type == KEY_PRESS) {
                        int length = lookupString(event, textBuffer);
                        for (int i = 0; i < length; i++) {
                            input.onText((char) (textBuffer.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i) & 0xFF));
                        }
                    }
                }
                // While a button is held, X keeps sending the motion to this window (implicit grab).
                case MOTION_NOTIFY -> {
                    int x = event.get(JAVA_INT, POINTER_X);
                    int y = event.get(JAVA_INT, POINTER_Y);
                    if (mouseCaptured) {
                        // Only the movement counts; the pointer goes back to the middle, so it never reaches an edge.
                        if (relativeMouse.move(x, y, input)) {
                            warpPointer(display, window, width / 2, height / 2);
                        }
                    } else {
                        input.onMouseMove(x, y);
                    }
                }
                case FOCUS_IN -> focused = true;
                case FOCUS_OUT -> {
                    focused = false;
                    releaseMouse(); // whoever switches to another program gets the pointer back
                }
                case BUTTON_PRESS, BUTTON_RELEASE -> {
                    if (!mouseCaptured) {
                        input.onMouseMove(event.get(JAVA_INT, POINTER_X), event.get(JAVA_INT, POINTER_Y));
                    }
                    if (type == BUTTON_PRESS && event.get(JAVA_INT, BUTTON_NUMBER) == 4) {
                        input.onScroll(1f); // X reports the wheel as buttons 4 (away) and 5 (toward the user)
                    } else if (type == BUTTON_PRESS && event.get(JAVA_INT, BUTTON_NUMBER) == 5) {
                        input.onScroll(-1f);
                    }
                    input.onMouseButton(switch (event.get(JAVA_INT, BUTTON_NUMBER)) {
                        case 1 -> MouseButton.LEFT;
                        case 2 -> MouseButton.MIDDLE;
                        case 3 -> MouseButton.RIGHT;
                        default -> null; // 4 and 5 are the scroll wheel
                    }, type == BUTTON_PRESS);
                }
                case CONFIGURE_NOTIFY -> {
                    int newWidth = event.get(JAVA_INT, CONFIGURE_WIDTH);
                    int newHeight = event.get(JAVA_INT, CONFIGURE_HEIGHT);
                    if (newWidth != width || newHeight != height) {
                        width = newWidth;
                        height = newHeight;
                        releaseMouse(); // captured anew for the new size by the next captureMouse(true)
                    }
                }
                case CLIENT_MESSAGE -> {
                    if (event.get(JAVA_LONG, CLIENT_MESSAGE_DATA) == wmDeleteWindow) {
                        closeRequested = true;
                    }
                }
                default -> {
                }
            }
        }
    }

    @Override
    public void setFullscreen(boolean on) {
        if (on == fullscreen) {
            return;
        }
        fullscreen = on;
        try (Arena temp = Arena.ofConfined()) {
            // Ask the window manager, the way the desktop standard describes it ...
            MemorySegment request = Native.bytes(temp, SIZEOF_XEVENT);
            request.set(JAVA_INT, 0, CLIENT_MESSAGE);
            request.set(JAVA_LONG, CLIENT_MESSAGE_WINDOW, window);
            request.set(JAVA_LONG, CLIENT_MESSAGE_TYPE, internAtom(display, Native.cString(temp, "_NET_WM_STATE")));
            request.set(JAVA_INT, CLIENT_MESSAGE_FORMAT, 32);
            request.set(JAVA_LONG, CLIENT_MESSAGE_DATA, on ? 1L : 0L);
            request.set(JAVA_LONG, CLIENT_MESSAGE_DATA + 8,
                    internAtom(display, Native.cString(temp, "_NET_WM_STATE_FULLSCREEN")));
            request.set(JAVA_LONG, CLIENT_MESSAGE_DATA + 24, 1L);
            sendEvent(display, root, SUBSTRUCTURE_NOTIFY_MASK | SUBSTRUCTURE_REDIRECT_MASK, request);
        }
        // ... and resize the window ourselves, for a display without a window manager.
        if (on) {
            windowedWidth = width;
            windowedHeight = height;
            moveResizeWindow(display, window, 0, 0, displayWidth(display, screen), displayHeight(display, screen));
        } else {
            moveResizeWindow(display, window, 0, 0, windowedWidth, windowedHeight);
        }
        flush(display);
    }

    @Override
    public boolean fullscreen() {
        return fullscreen;
    }

    @Override
    public void captureMouse(boolean capture) {
        if (!capture) {
            releaseMouse();
            return;
        }
        if (mouseCaptured || !focused) {
            return;
        }
        if (invisibleCursor == 0L) {
            invisibleCursor = invisibleCursor(display, window);
        }
        if (grabPointer(display, window, invisibleCursor)) {
            warpPointer(display, window, width / 2, height / 2);
            relativeMouse.begin(width / 2, height / 2, width, height);
            relativeMouse.expectAtCenter();
            mouseCaptured = true;
        }
    }

    private void releaseMouse() {
        if (mouseCaptured) {
            mouseCaptured = false;
            ungrabPointer(display);
            flush(display);
        }
    }

    private static Key mapKey(long keysym) {
        int code = (int) keysym;
        return switch (code) {
            case XK_ESCAPE -> Key.ESCAPE;
            case XK_RETURN -> Key.ENTER;
            case XK_SPACE -> Key.SPACE;
            case XK_TAB -> Key.TAB;
            case XK_CAPS_LOCK -> Key.CAPS_LOCK;
            case XK_BACKSPACE -> Key.BACKSPACE;
            case XK_SHIFT_L, XK_SHIFT_R -> Key.SHIFT;
            case XK_CONTROL_L, XK_CONTROL_R -> Key.CONTROL;
            case XK_LEFT -> Key.LEFT;
            case XK_RIGHT -> Key.RIGHT;
            case XK_UP -> Key.UP;
            case XK_DOWN -> Key.DOWN;
            default -> {
                Key letter = Key.letter(code);
                yield letter != null ? letter : Key.digit(code);
            }
        };
    }

    @Override
    public void swapBuffers() {
        X11Api.swapBuffers(display, window);
    }

    @Override
    public boolean shouldClose() {
        return closeRequested;
    }

    @Override
    public void requestClose() {
        closeRequested = true;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public void setTitle(String title) {
        try (Arena temp = Arena.ofConfined()) {
            // Legacy name for old window managers, _NET_WM_NAME for proper UTF-8.
            storeName(display, window, Native.cString(temp, title));
            byte[] utf8 = title.getBytes(StandardCharsets.UTF_8);
            MemorySegment data = Native.bytes(temp, Math.max(1, utf8.length));
            MemorySegment.copy(utf8, 0, data, JAVA_BYTE, 0, utf8.length);
            changeProperty8(display, window, netWmName, utf8String, data, utf8.length);
            flush(display);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        makeCurrent(display, 0L, MemorySegment.NULL);
        destroyContext(display, context);
        destroyWindow(display, window);
        freeColormap(display, colormap);
        closeDisplay(display);
        arena.close();
    }
}
