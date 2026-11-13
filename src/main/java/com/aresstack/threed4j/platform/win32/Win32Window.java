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
        windowClass.set(ADDRESS, WNDCLASSEX_CURSOR, loadArrowCursor());
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
                    return 0;
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
                    input.onMouseMove((short) (lParam & 0xFFFF), (short) ((lParam >>> 16) & 0xFFFF));
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
                case WM_SYSKEYDOWN -> input.onKey(mapKey((int) wParam), true);
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
        releaseDC(window, deviceContext);
        destroyWindow(window); // still sends messages, so the stub must outlive it
        unregisterClass(className, instance);
        arena.close();
    }
}
