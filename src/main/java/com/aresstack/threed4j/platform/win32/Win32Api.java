package com.aresstack.threed4j.platform.win32;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Bindings for the parts of the Win32 API the window needs (64-bit Windows).
 *
 * <p>C types map as follows: handles and pointers ({@code HWND}, {@code HDC},
 * {@code HGLRC}, {@code LPCWSTR}, ...) are {@code MemorySegment}; {@code BOOL},
 * {@code UINT} and {@code DWORD} are {@code int}; {@code WPARAM}, {@code LPARAM}
 * and {@code LRESULT} are pointer-sized and therefore {@code long}.</p>
 *
 * <p>Calls whose failure reason matters capture {@code GetLastError} into
 * {@link #lastError()}. The engine is single-threaded, so one shared capture buffer
 * is enough.</p>
 */
final class Win32Api {

    private Win32Api() {
    }

    private static final SymbolLookup KERNEL32 = Native.library("kernel32");
    private static final SymbolLookup USER32 = Native.library("user32");
    private static final SymbolLookup GDI32 = Native.library("gdi32");
    private static final SymbolLookup OPENGL32 = Native.library("opengl32");

    // ---------------------------------------------------------------- constants

    static final int CS_VREDRAW = 0x0001;
    static final int CS_HREDRAW = 0x0002;
    static final int CS_OWNDC = 0x0020;

    static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
    static final int CW_USEDEFAULT = 0x80000000;
    static final int SW_SHOW = 5;
    static final int SM_REMOTESESSION = 0x1000;
    static final int GL_VENDOR = 0x1F00;
    static final int GL_RENDERER = 0x1F01;
    static final int GL_VERSION = 0x1F02;
    static final int PM_REMOVE = 0x0001;
    /** {@code MAKEINTRESOURCE(32512)}. */
    static final long IDC_ARROW = 32512;

    static final int WM_DESTROY = 0x0002;
    static final int WM_SIZE = 0x0005;
    static final int WM_CLOSE = 0x0010;
    static final int WM_ERASEBKGND = 0x0014;
    static final int WM_KEYDOWN = 0x0100;
    static final int WM_KEYUP = 0x0101;
    static final int WM_SYSKEYDOWN = 0x0104;
    static final int WM_SYSKEYUP = 0x0105;
    static final int WM_MOUSEMOVE = 0x0200;
    static final int WM_LBUTTONDOWN = 0x0201;
    static final int WM_LBUTTONUP = 0x0202;
    static final int WM_RBUTTONDOWN = 0x0204;
    static final int WM_RBUTTONUP = 0x0205;
    static final int WM_MBUTTONDOWN = 0x0207;
    static final int WM_MBUTTONUP = 0x0208;

    static final int VK_TAB = 0x09;
    static final int VK_RETURN = 0x0D;
    static final int VK_SHIFT = 0x10;
    static final int VK_CONTROL = 0x11;
    static final int VK_ESCAPE = 0x1B;
    static final int VK_SPACE = 0x20;
    static final int VK_LEFT = 0x25;
    static final int VK_UP = 0x26;
    static final int VK_RIGHT = 0x27;
    static final int VK_DOWN = 0x28;

    static final int PFD_DOUBLEBUFFER = 0x0001;
    static final int PFD_DRAW_TO_WINDOW = 0x0004;
    static final int PFD_SUPPORT_OPENGL = 0x0020;
    static final byte PFD_TYPE_RGBA = 0;
    static final byte PFD_MAIN_PLANE = 0;

    static final int WGL_CONTEXT_MAJOR_VERSION_ARB = 0x2091;
    static final int WGL_CONTEXT_MINOR_VERSION_ARB = 0x2092;
    static final int WGL_CONTEXT_PROFILE_MASK_ARB = 0x9126;
    static final int WGL_CONTEXT_CORE_PROFILE_BIT_ARB = 0x0001;

    // ------------------------------------------------------------ struct layouts

    /** {@code sizeof(WNDCLASSEXW)} and the offsets of its fields. */
    static final long SIZEOF_WNDCLASSEX = 80;
    static final long WNDCLASSEX_SIZE = 0;
    static final long WNDCLASSEX_STYLE = 4;
    static final long WNDCLASSEX_WNDPROC = 8;
    static final long WNDCLASSEX_INSTANCE = 24;
    static final long WNDCLASSEX_CURSOR = 40;
    static final long WNDCLASSEX_CLASS_NAME = 64;

    /** {@code sizeof(MSG)}. */
    static final long SIZEOF_MSG = 48;

    /** {@code sizeof(RECT)}: left, top, right, bottom as 32-bit integers. */
    static final long SIZEOF_RECT = 16;
    static final long RECT_LEFT = 0;
    static final long RECT_TOP = 4;
    static final long RECT_RIGHT = 8;
    static final long RECT_BOTTOM = 12;

    /** {@code sizeof(PIXELFORMATDESCRIPTOR)} and the offsets of the fields that are set. */
    static final long SIZEOF_PIXELFORMATDESCRIPTOR = 40;
    static final long PFD_SIZE = 0;
    static final long PFD_VERSION = 2;
    static final long PFD_FLAGS = 4;
    static final long PFD_PIXEL_TYPE = 8;
    static final long PFD_COLOR_BITS = 9;
    static final long PFD_DEPTH_BITS = 23;
    static final long PFD_STENCIL_BITS = 24;
    static final long PFD_LAYER_TYPE = 26;

    /** {@code LRESULT CALLBACK WindowProc(HWND, UINT, WPARAM, LPARAM)}. */
    static final FunctionDescriptor WNDPROC = FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG);
    /** {@code HGLRC wglCreateContextAttribsARB(HDC, HGLRC share, const int* attributes)}. */
    static final FunctionDescriptor CREATE_CONTEXT_ATTRIBS = FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS);
    /** {@code BOOL wglSwapIntervalEXT(int)}. */
    static final FunctionDescriptor SWAP_INTERVAL = FunctionDescriptor.of(JAVA_INT, JAVA_INT);

    // --------------------------------------------------------------- last error

    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");
    private static final MemorySegment CAPTURED_STATE = Arena.global().allocate(Linker.Option.captureStateLayout());
    private static final long LAST_ERROR_OFFSET = Linker.Option.captureStateLayout()
            .byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));

    /** The Windows error code of the most recent capturing call. */
    static int lastError() {
        return CAPTURED_STATE.get(JAVA_INT, LAST_ERROR_OFFSET);
    }

    // ------------------------------------------------------------------ handles

    private static final MethodHandle GetModuleHandleW = Native.downcall(KERNEL32, "GetModuleHandleW",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    private static final MethodHandle SetProcessDPIAware = Native.downcall(USER32, "SetProcessDPIAware",
            FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle RegisterClassExW = Native.downcall(USER32, "RegisterClassExW",
            FunctionDescriptor.of(JAVA_SHORT, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle UnregisterClassW = Native.downcall(USER32, "UnregisterClassW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CreateWindowExW = Native.downcall(USER32, "CreateWindowExW",
            FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle DestroyWindow = Native.downcall(USER32, "DestroyWindow",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle ShowWindow = Native.downcall(USER32, "ShowWindow",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle SetWindowTextW = Native.downcall(USER32, "SetWindowTextW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle DefWindowProcW = Native.downcall(USER32, "DefWindowProcW",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle PeekMessageW = Native.downcall(USER32, "PeekMessageW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle TranslateMessage = Native.downcall(USER32, "TranslateMessage",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle DispatchMessageW = Native.downcall(USER32, "DispatchMessageW",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle LoadCursorW = Native.downcall(USER32, "LoadCursorW",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle AdjustWindowRect = Native.downcall(USER32, "AdjustWindowRect",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle GetClientRect = Native.downcall(USER32, "GetClientRect",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GetDC = Native.downcall(USER32, "GetDC",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle ReleaseDC = Native.downcall(USER32, "ReleaseDC",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    private static final MethodHandle ChoosePixelFormat = Native.downcall(GDI32, "ChoosePixelFormat",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle SetPixelFormat = Native.downcall(GDI32, "SetPixelFormat",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle SwapBuffers = Native.downcall(GDI32, "SwapBuffers",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final MethodHandle wglCreateContext = Native.downcall(OPENGL32, "wglCreateContext",
            FunctionDescriptor.of(ADDRESS, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle wglDeleteContext = Native.downcall(OPENGL32, "wglDeleteContext",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle wglMakeCurrent = Native.downcall(OPENGL32, "wglMakeCurrent",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR);
    private static final MethodHandle SetCapture = Native.downcall(USER32, "SetCapture",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle ReleaseCapture = Native.downcall(USER32, "ReleaseCapture",
            FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GetSystemMetrics = Native.downcall(USER32, "GetSystemMetrics",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    /** OpenGL 1.1 function, a plain export of opengl32.dll: usable before the GL class is. */
    private static final MethodHandle glGetString = Native.downcall(OPENGL32, "glGetString",
            FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private static final MethodHandle wglGetProcAddress = Native.downcall(OPENGL32, "wglGetProcAddress",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    /**
     * Finds an OpenGL or WGL function. Everything newer than OpenGL 1.1 comes from the
     * driver through {@code wglGetProcAddress} (needs a current context); the 1.1
     * functions are plain exports of opengl32.dll.
     */
    static MemorySegment glFunction(String name) {
        MemorySegment function;
        try (Arena arena = Arena.ofConfined()) {
            function = (MemorySegment) wglGetProcAddress.invokeExact(Native.cString(arena, name));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        // Some drivers signal failure with small integers or -1 instead of NULL.
        long address = function.address();
        if (address == 0L || address == 1L || address == 2L || address == 3L || address == -1L) {
            return Native.symbol(OPENGL32, name);
        }
        return function;
    }

    // ----------------------------------------------------------- typed wrappers

    /** Keeps mouse messages coming to the window while the pointer is outside of it. */
    static void setCapture(MemorySegment window) {
        try {
            MemorySegment previous = (MemorySegment) SetCapture.invokeExact(window);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void releaseCapture() {
        try {
            int ignored = (int) ReleaseCapture.invokeExact();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code true} if this process runs in a Remote Desktop session. */
    static boolean isRemoteSession() {
        try {
            return (int) GetSystemMetrics.invokeExact(SM_REMOTESESSION) != 0;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code glGetString} of the current context; {@code "?"} if there is no answer. */
    static String glString(int name) {
        try {
            String value = Native.readCString((MemorySegment) glGetString.invokeExact(name));
            return value != null ? value : "?";
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment getModuleHandle() {
        try {
            return (MemorySegment) GetModuleHandleW.invokeExact(MemorySegment.NULL);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Opts out of bitmap scaling on high-DPI displays: one window pixel is one screen pixel. */
    static void setProcessDpiAware() {
        try {
            int ignored = (int) SetProcessDPIAware.invokeExact();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @return the class atom, 0 on failure */
    static int registerClassEx(MemorySegment windowClass) {
        try {
            return (short) RegisterClassExW.invokeExact(CAPTURED_STATE, windowClass) & 0xFFFF;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void unregisterClass(MemorySegment className, MemorySegment instance) {
        try {
            int ignored = (int) UnregisterClassW.invokeExact(className, instance);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment createWindowEx(MemorySegment className, MemorySegment title, int style,
                                        int x, int y, int width, int height, MemorySegment instance) {
        try {
            return (MemorySegment) CreateWindowExW.invokeExact(CAPTURED_STATE, 0, className, title, style,
                    x, y, width, height, MemorySegment.NULL, MemorySegment.NULL, instance, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void destroyWindow(MemorySegment window) {
        try {
            int ignored = (int) DestroyWindow.invokeExact(window);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void showWindow(MemorySegment window, int command) {
        try {
            int ignored = (int) ShowWindow.invokeExact(window, command);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void setWindowText(MemorySegment window, MemorySegment text) {
        try {
            int ignored = (int) SetWindowTextW.invokeExact(window, text);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long defWindowProc(MemorySegment window, int message, long wParam, long lParam) {
        try {
            return (long) DefWindowProcW.invokeExact(window, message, wParam, lParam);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Removes the next message of this thread from the queue, if there is one. */
    static boolean peekMessage(MemorySegment message) {
        try {
            return (int) PeekMessageW.invokeExact(message, MemorySegment.NULL, 0, 0, PM_REMOVE) != 0;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void translateMessage(MemorySegment message) {
        try {
            int ignored = (int) TranslateMessage.invokeExact(message);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void dispatchMessage(MemorySegment message) {
        try {
            long ignored = (long) DispatchMessageW.invokeExact(message);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment loadArrowCursor() {
        try {
            return (MemorySegment) LoadCursorW.invokeExact(MemorySegment.NULL, MemorySegment.ofAddress(IDC_ARROW));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void adjustWindowRect(MemorySegment rect, int style) {
        try {
            int ignored = (int) AdjustWindowRect.invokeExact(rect, style, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void getClientRect(MemorySegment window, MemorySegment rect) {
        try {
            int ignored = (int) GetClientRect.invokeExact(window, rect);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment getDC(MemorySegment window) {
        try {
            return (MemorySegment) GetDC.invokeExact(window);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void releaseDC(MemorySegment window, MemorySegment deviceContext) {
        try {
            int ignored = (int) ReleaseDC.invokeExact(window, deviceContext);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @return the pixel format index, 0 on failure */
    static int choosePixelFormat(MemorySegment deviceContext, MemorySegment descriptor) {
        try {
            return (int) ChoosePixelFormat.invokeExact(CAPTURED_STATE, deviceContext, descriptor);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static boolean setPixelFormat(MemorySegment deviceContext, int format, MemorySegment descriptor) {
        try {
            return (int) SetPixelFormat.invokeExact(CAPTURED_STATE, deviceContext, format, descriptor) != 0;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void swapBuffers(MemorySegment deviceContext) {
        try {
            int ignored = (int) SwapBuffers.invokeExact(deviceContext);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static MemorySegment wglCreateContext(MemorySegment deviceContext) {
        try {
            return (MemorySegment) wglCreateContext.invokeExact(CAPTURED_STATE, deviceContext);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void wglDeleteContext(MemorySegment context) {
        try {
            int ignored = (int) wglDeleteContext.invokeExact(context);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static boolean wglMakeCurrent(MemorySegment deviceContext, MemorySegment context) {
        try {
            return (int) wglMakeCurrent.invokeExact(CAPTURED_STATE, deviceContext, context) != 0;
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }
}
