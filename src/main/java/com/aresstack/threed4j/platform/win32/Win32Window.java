package com.aresstack.threed4j.platform.win32;

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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.aresstack.threed4j.platform.win32.Win32Api.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Windows window: user32 for the window and its messages. It can carry an OpenGL
 * context (WGL) or stay a plain window that Direct3D renders into.
 */
public final class Win32Window implements GLWindow {

    private static final String CLASS_NAME = "threed4j.Window";

    /** Lives as long as the window: holds the window procedure stub and the message buffer. */
    private final Arena arena = Arena.ofConfined();
    private final Input input = new Input();

    private final MemorySegment instance;
    private final MemorySegment className;
    private final MemorySegment message;
    private final MemorySegment window;
    private final MemorySegment deviceContext;
    private final MemorySegment context;

    private int width;
    private int height;
    private boolean closeRequested;
    private boolean closed;

    private boolean fullscreen;
    /** Where the window was before it filled the screen: left, top, width, height with its frame. */
    private final int[] windowed = new int[4];
    /** Whether the window has the keyboard; without it the mouse is let go. */
    private boolean focused = true;
    private boolean mouseCaptured;
    /** The middle of the drawable area, where the captured pointer is put back: in the window, and on the screen. */
    private int centerX;
    private int centerY;
    private int centerScreenX;
    private int centerScreenY;
    private final MemorySegment arrowCursor;
    /** The game's own pointer; {@code NULL} while the system's arrow is used. */
    private MemorySegment cursor = MemorySegment.NULL;

    /**
     * @param openGL {@code true} to create an OpenGL 3.3 core context for the window,
     *               {@code false} for a plain window (used by the Direct3D backend)
     */
    public Win32Window(WindowConfig config, boolean openGL) {
        width = config.width();
        height = config.height();
        setProcessDpiAware();

        instance = getModuleHandle();
        className = Native.wideString(arena, CLASS_NAME);
        message = Native.bytes(arena, SIZEOF_MSG);

        // Windows calls back into Java for every window message.
        MemorySegment windowProcedure = Native.LINKER.upcallStub(windowProcedureHandle().bindTo(this), WNDPROC, arena);

        MemorySegment windowClass = Native.bytes(arena, SIZEOF_WNDCLASSEX);
        windowClass.set(JAVA_INT, WNDCLASSEX_SIZE, (int) SIZEOF_WNDCLASSEX);
        windowClass.set(JAVA_INT, WNDCLASSEX_STYLE, CS_OWNDC | CS_HREDRAW | CS_VREDRAW);
        windowClass.set(ADDRESS, WNDCLASSEX_WNDPROC, windowProcedure);
        windowClass.set(ADDRESS, WNDCLASSEX_INSTANCE, instance);
        arrowCursor = loadArrowCursor();
        windowClass.set(ADDRESS, WNDCLASSEX_CURSOR, arrowCursor);
        windowClass.set(ADDRESS, WNDCLASSEX_CLASS_NAME, className);
        if (registerClassEx(windowClass) == 0) {
            throw failure("RegisterClassExW");
        }

        // The requested size is the drawable area; the outer window is larger by its frame.
        MemorySegment rect = Native.bytes(arena, SIZEOF_RECT);
        rect.set(JAVA_INT, RECT_RIGHT, width);
        rect.set(JAVA_INT, RECT_BOTTOM, height);
        adjustWindowRect(rect, WS_OVERLAPPEDWINDOW);
        int outerWidth = rect.get(JAVA_INT, RECT_RIGHT) - rect.get(JAVA_INT, RECT_LEFT);
        int outerHeight = rect.get(JAVA_INT, RECT_BOTTOM) - rect.get(JAVA_INT, RECT_TOP);

        window = createWindowEx(className, Native.wideString(arena, config.title()), WS_OVERLAPPEDWINDOW,
                CW_USEDEFAULT, CW_USEDEFAULT, outerWidth, outerHeight, instance);
        if (Native.isNull(window)) {
            throw failure("CreateWindowExW");
        }
        deviceContext = getDC(window);
        if (Native.isNull(deviceContext)) {
            throw new IllegalStateException("GetDC failed");
        }

        if (openGL) {
            context = createCoreContext();
            GLLoader.install(Win32Api::glFunction);
            setSwapInterval(config.vsync() ? 1 : 0);
        } else {
            context = MemorySegment.NULL;
        }

        showWindow(window, SW_SHOW);
        getClientRect(window, rect);
        storeSize(rect.get(JAVA_INT, RECT_RIGHT), rect.get(JAVA_INT, RECT_BOTTOM));
    }

    /**
     * Creates an OpenGL 3.3 core context. The function that can do this,
     * {@code wglCreateContextAttribsARB}, is itself an extension and only reachable
     * while some context is current – hence the temporary legacy context.
     */
    private MemorySegment createCoreContext() {
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment descriptor = Native.bytes(temp, SIZEOF_PIXELFORMATDESCRIPTOR);
            descriptor.set(JAVA_SHORT, PFD_SIZE, (short) SIZEOF_PIXELFORMATDESCRIPTOR);
            descriptor.set(JAVA_SHORT, PFD_VERSION, (short) 1);
            descriptor.set(JAVA_INT, PFD_FLAGS, PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER);
            descriptor.set(JAVA_BYTE, PFD_PIXEL_TYPE, PFD_TYPE_RGBA);
            descriptor.set(JAVA_BYTE, PFD_COLOR_BITS, (byte) 32);
            descriptor.set(JAVA_BYTE, PFD_DEPTH_BITS, (byte) 24);
            descriptor.set(JAVA_BYTE, PFD_STENCIL_BITS, (byte) 8);
            descriptor.set(JAVA_BYTE, PFD_LAYER_TYPE, PFD_MAIN_PLANE);

            int format = choosePixelFormat(deviceContext, descriptor);
            if (format == 0) {
                throw failure("ChoosePixelFormat");
            }
            if (!setPixelFormat(deviceContext, format, descriptor)) {
                throw failure("SetPixelFormat");
            }

            MemorySegment legacy = wglCreateContext(deviceContext);
            if (Native.isNull(legacy)) {
                throw failure("wglCreateContext");
            }
            if (!wglMakeCurrent(deviceContext, legacy)) {
                throw failure("wglMakeCurrent");
            }

            // What Windows offers before any version is requested – the key to every failure below.
            String legacyDriver = glString(GL_RENDERER) + " (" + glString(GL_VENDOR)
                    + "), OpenGL " + glString(GL_VERSION);

            MemorySegment function = glFunction("wglCreateContextAttribsARB");
            MemorySegment core = MemorySegment.NULL;
            if (!Native.isNull(function)) {
                MethodHandle createContextAttribs = Native.downcall(function, CREATE_CONTEXT_ATTRIBS);
                MemorySegment attributes = Native.ints(temp,
                        WGL_CONTEXT_MAJOR_VERSION_ARB, 3,
                        WGL_CONTEXT_MINOR_VERSION_ARB, 3,
                        WGL_CONTEXT_PROFILE_MASK_ARB, WGL_CONTEXT_CORE_PROFILE_BIT_ARB,
                        0);
                try {
                    core = (MemorySegment) createContextAttribs.invokeExact(deviceContext, MemorySegment.NULL, attributes);
                } catch (Throwable t) {
                    throw Native.rethrow(t);
                }
            }
            wglMakeCurrent(MemorySegment.NULL, MemorySegment.NULL);
            wglDeleteContext(legacy);
            if (Native.isNull(core)) {
                throw new IllegalStateException(noCoreContextMessage(legacyDriver, !Native.isNull(function)));
            }
            if (!wglMakeCurrent(deviceContext, core)) {
                throw failure("wglMakeCurrent");
            }
            return core;
        }
    }

    private static String noCoreContextMessage(String legacyDriver, boolean createContextAttribsAvailable) {
        StringBuilder message = new StringBuilder("Cannot create an OpenGL 3.3 core context.\n")
                .append("  OpenGL driver offered by Windows: ").append(legacyDriver).append('\n')
                .append("  wglCreateContextAttribsARB: ")
                .append(createContextAttribsAvailable ? "available, but refused version 3.3 core" : "not available").append('\n')
                .append("  Remote Desktop session: ").append(isRemoteSession() ? "yes" : "no").append('\n');
        if (legacyDriver.startsWith("GDI Generic")) {
            message.append("  \"GDI Generic\" is the OpenGL 1.1 software renderer built into Windows. It is used when\n")
                    .append("  no hardware OpenGL driver is active for this session (Remote Desktop, virtual machine,\n")
                    .append("  missing or blocked graphics driver). It has no shaders, so the engine cannot run on it.");
        }
        return message.toString();
    }

    /** Vertical sync is the optional extension WGL_EXT_swap_control. */
    private void setSwapInterval(int interval) {
        MemorySegment function = glFunction("wglSwapIntervalEXT");
        if (Native.isNull(function)) {
            return;
        }
        try {
            int ignored = (int) Native.downcall(function, SWAP_INTERVAL).invokeExact(interval);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    private static MethodHandle windowProcedureHandle() {
        try {
            return MethodHandles.lookup().findVirtual(Win32Window.class, "windowProcedure",
                    MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** The window procedure. Called by Windows from inside {@code DispatchMessageW} and friends. */
    private long windowProcedure(MemorySegment hwnd, int messageId, long wParam, long lParam) {
        try {
            switch (messageId) {
                case WM_CLOSE -> {
                    closeRequested = true; // the engine loop closes the window, not Windows
                    return 0;
                }
                case WM_SIZE -> {
                    storeSize((int) (lParam & 0xFFFF), (int) ((lParam >>> 16) & 0xFFFF));
                    releaseMouse(); // captured anew for the new size by the next captureMouse(true)
                    return 0;
                }
                case WM_SETFOCUS -> focused = true;
                case WM_KILLFOCUS -> {
                    focused = false;
                    releaseMouse(); // whoever switches to another program gets the pointer back
                }
                case WM_SETCURSOR -> {
                    if ((lParam & 0xFFFF) == HTCLIENT) {
                        showCursor(mouseCaptured ? MemorySegment.NULL : Native.isNull(cursor) ? arrowCursor : cursor);
                        return 1;
                    }
                }
                case WM_SYSCHAR -> {
                    if (wParam == VK_RETURN) {
                        return 0; // Alt+Enter is ours; without this Windows beeps
                    }
                }
                case WM_ERASEBKGND -> {
                    return 1; // OpenGL paints everything; erasing would only flicker
                }
                case WM_KEYDOWN -> {
                    input.onKey(mapKey((int) wParam), true);
                    return 0;
                }
                case WM_CHAR -> {
                    // TranslateMessage turns key presses into the characters of the keyboard layout.
                    input.onText((char) wParam);
                    return 0;
                }
                case WM_KEYUP -> {
                    input.onKey(mapKey((int) wParam), false);
                    return 0;
                }
                case WM_MOUSEMOVE -> {
                    // The position is two signed 16-bit values: negative while dragging outside the window.
                    int x = (short) (lParam & 0xFFFF);
                    int y = (short) ((lParam >>> 16) & 0xFFFF);
                    if (mouseCaptured) {
                        // Only the movement counts; the pointer goes back to the middle, so it never reaches an edge.
                        if (x != centerX || y != centerY) {
                            input.onMouseDelta(x - centerX, y - centerY);
                            setCursorPos(centerScreenX, centerScreenY);
                        }
                    } else {
                        input.onMouseMove(x, y);
                    }
                    return 0;
                }
                case WM_LBUTTONDOWN, WM_MBUTTONDOWN, WM_RBUTTONDOWN -> {
                    input.onMouseButton(mapButton(messageId), true);
                    setCapture(hwnd); // a drag keeps working when the pointer leaves the window
                    return 0;
                }
                case WM_LBUTTONUP, WM_MBUTTONUP, WM_RBUTTONUP -> {
                    input.onMouseButton(mapButton(messageId), false);
                    if (!input.isDown(MouseButton.LEFT) && !input.isDown(MouseButton.MIDDLE)
                            && !input.isDown(MouseButton.RIGHT)) {
                        releaseCapture();
                    }
                    return 0;
                }
                // Keys pressed together with Alt: record them, but let Windows handle Alt+F4 etc.
                case WM_SYSKEYDOWN -> {
                    if (wParam == VK_RETURN) {
                        if ((lParam & (1L << 30)) == 0) { // not a repeat of a held key
                            setFullscreen(!fullscreen);
                        }
                        return 0;
                    }
                    input.onKey(mapKey((int) wParam), true);
                }
                case WM_SYSKEYUP -> input.onKey(mapKey((int) wParam), false);
                default -> {
                }
            }
            return defWindowProc(hwnd, messageId, wParam, lParam);
        } catch (Throwable t) {
            // An exception must never propagate into native code: the JVM would abort.
            t.printStackTrace();
            closeRequested = true;
            return 0;
        }
    }

    @Override
    public void setFullscreen(boolean on) {
        if (on == fullscreen) {
            return;
        }
        try (Arena temp = Arena.ofConfined()) {
            MemorySegment rect = Native.bytes(temp, SIZEOF_RECT);
            if (on) {
                getWindowRect(window, rect);
                windowed[0] = rect.get(JAVA_INT, RECT_LEFT);
                windowed[1] = rect.get(JAVA_INT, RECT_TOP);
                windowed[2] = rect.get(JAVA_INT, RECT_RIGHT) - windowed[0];
                windowed[3] = rect.get(JAVA_INT, RECT_BOTTOM) - windowed[1];
                if (!monitorRect(window, rect)) {
                    return;
                }
                fullscreen = true;
                // A window without a frame that covers its monitor: no change of the screen's mode.
                setWindowStyle(window, WS_POPUP | WS_VISIBLE);
                setWindowPos(window, rect.get(JAVA_INT, RECT_LEFT), rect.get(JAVA_INT, RECT_TOP),
                        rect.get(JAVA_INT, RECT_RIGHT) - rect.get(JAVA_INT, RECT_LEFT),
                        rect.get(JAVA_INT, RECT_BOTTOM) - rect.get(JAVA_INT, RECT_TOP));
            } else {
                fullscreen = false;
                setWindowStyle(window, WS_OVERLAPPEDWINDOW | WS_VISIBLE);
                setWindowPos(window, windowed[0], windowed[1], windowed[2], windowed[3]);
            }
        }
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
        try (Arena temp = Arena.ofConfined()) {
            // The drawable area in screen coordinates: its two corners, turned one after the other.
            MemorySegment rect = Native.bytes(temp, SIZEOF_RECT);
            getClientRect(window, rect);
            clientToScreen(window, rect);
            clientToScreen(window, rect.asSlice(RECT_RIGHT));
            centerX = width / 2;
            centerY = height / 2;
            centerScreenX = rect.get(JAVA_INT, RECT_LEFT) + centerX;
            centerScreenY = rect.get(JAVA_INT, RECT_TOP) + centerY;
            clipCursor(rect);
            setCursorPos(centerScreenX, centerScreenY);
            mouseCaptured = true;
            showCursor(MemorySegment.NULL);
        }
    }

    private void releaseMouse() {
        if (mouseCaptured) {
            mouseCaptured = false;
            clipCursor(MemorySegment.NULL);
            showCursor(Native.isNull(cursor) ? arrowCursor : cursor);
        }
    }

    @Override
    public void setCursor(java.awt.image.BufferedImage image, int hotspotX, int hotspotY) {
        MemorySegment old = cursor;
        cursor = MemorySegment.NULL;
        if (image != null) {
            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            cursor = createCursor(deviceContext, argb, image.getWidth(), image.getHeight(), hotspotX, hotspotY);
            if (Native.isNull(cursor)) {
                System.out.println("The game's pointer could not be made; the system's arrow stays.");
            }
        }
        if (!mouseCaptured) {
            showCursor(Native.isNull(cursor) ? arrowCursor : cursor);
        }
        if (!Native.isNull(old)) {
            destroyCursor(old);
        }
    }

    private void storeSize(int newWidth, int newHeight) {
        if (newWidth > 0 && newHeight > 0) { // both are 0 while minimized
            width = newWidth;
            height = newHeight;
        }
    }

    private static MouseButton mapButton(int messageId) {
        return switch (messageId) {
            case WM_LBUTTONDOWN, WM_LBUTTONUP -> MouseButton.LEFT;
            case WM_MBUTTONDOWN, WM_MBUTTONUP -> MouseButton.MIDDLE;
            default -> MouseButton.RIGHT;
        };
    }

    private static Key mapKey(int virtualKey) {
        return switch (virtualKey) {
            case VK_ESCAPE -> Key.ESCAPE;
            case VK_RETURN -> Key.ENTER;
            case VK_SPACE -> Key.SPACE;
            case VK_TAB -> Key.TAB;
            case VK_BACK -> Key.BACKSPACE;
            case VK_SHIFT -> Key.SHIFT;
            case VK_CONTROL -> Key.CONTROL;
            case VK_LEFT -> Key.LEFT;
            case VK_RIGHT -> Key.RIGHT;
            case VK_UP -> Key.UP;
            case VK_DOWN -> Key.DOWN;
            default -> {
                Key letter = Key.letter(virtualKey); // VK codes of letters and digits are their ASCII codes
                yield letter != null ? letter : Key.digit(virtualKey);
            }
        };
    }

    private static IllegalStateException failure(String function) {
        return new IllegalStateException(function + " failed (Windows error code " + lastError() + ")");
    }

    @Override
    public void pollEvents() {
        while (peekMessage(message)) {
            translateMessage(message);
            dispatchMessage(message);
        }
    }

    /** The {@code HWND}. */
    public MemorySegment handle() {
        return window;
    }

    @Override
    public void swapBuffers() {
        if (Native.isNull(context)) {
            throw new IllegalStateException("This window has no OpenGL context");
        }
        Win32Api.swapBuffers(deviceContext);
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
            setWindowText(window, Native.wideString(temp, title));
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!Native.isNull(context)) {
            wglMakeCurrent(MemorySegment.NULL, MemorySegment.NULL);
            wglDeleteContext(context);
        }
        releaseMouse();
        if (!Native.isNull(cursor)) {
            destroyCursor(cursor);
        }
        releaseDC(window, deviceContext);
        destroyWindow(window); // still sends messages, so the stub must outlive it
        unregisterClass(className, instance);
        arena.close();
    }
}
