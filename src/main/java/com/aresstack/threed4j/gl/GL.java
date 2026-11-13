package com.aresstack.threed4j.gl;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Hand-written OpenGL 3.3 core bindings – only what the engine currently needs.
 *
 * <p>The functions are bound when this class is initialized, so it must not be
 * touched before a {@code Window} exists (see {@link GLLoader}). Keeping the method
 * handles in {@code static final} fields lets the JIT compile each call down to a
 * direct native call.</p>
 *
 * <p>To add a function: add a handle with its C signature and a static wrapper
 * method. Parameters that are byte offsets disguised as pointers
 * ({@code glVertexAttribPointer}, {@code glDrawElements}) are declared as
 * {@code long} to avoid allocating a segment per call.</p>
 */
public final class GL {

    private GL() {
    }

    // ---------------------------------------------------------------- constants

    public static final int GL_FALSE = 0;
    public static final int GL_TRUE = 1;
    public static final int GL_NO_ERROR = 0;

    public static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    public static final int GL_STENCIL_BUFFER_BIT = 0x0400;
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;

    public static final int GL_POINTS = 0x0000;
    public static final int GL_LINES = 0x0001;
    public static final int GL_TRIANGLES = 0x0004;

    public static final int GL_LESS = 0x0201;
    public static final int GL_LEQUAL = 0x0203;
    public static final int GL_BACK = 0x0405;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_PACK_ALIGNMENT = 0x0D05;

    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_UNSIGNED_INT = 0x1405;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_RGB = 0x1907;
    public static final int GL_RGBA = 0x1908;

    public static final int GL_VENDOR = 0x1F00;
    public static final int GL_RENDERER = 0x1F01;
    public static final int GL_VERSION = 0x1F02;
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;

    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_STATIC_DRAW = 0x88E4;
    public static final int GL_DYNAMIC_DRAW = 0x88E8;

    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_LINK_STATUS = 0x8B82;
    public static final int GL_INFO_LOG_LENGTH = 0x8B84;

    public static final int GL_NEAREST = 0x2600;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_RGBA8 = 0x8058;
    public static final int GL_TEXTURE0 = 0x84C0;
    public static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    public static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    public static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    public static final int GL_FRAMEBUFFER = 0x8D40;
    public static final int GL_RENDERBUFFER = 0x8D41;

    // ------------------------------------------------------------------ handles

    private static final MethodHandle glViewport = bindVoid("glViewport", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle glClearColor = bindVoid("glClearColor", JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
    private static final MethodHandle glClear = bindVoid("glClear", JAVA_INT);
    private static final MethodHandle glEnable = bindVoid("glEnable", JAVA_INT);
    private static final MethodHandle glDisable = bindVoid("glDisable", JAVA_INT);
    private static final MethodHandle glBlendFunc = bindVoid("glBlendFunc", JAVA_INT, JAVA_INT);
    private static final MethodHandle glDepthFunc = bindVoid("glDepthFunc", JAVA_INT);
    private static final MethodHandle glCullFace = bindVoid("glCullFace", JAVA_INT);
    private static final MethodHandle glGetError = bind("glGetError", JAVA_INT);
    private static final MethodHandle glGetString = bind("glGetString", ADDRESS, JAVA_INT);
    private static final MethodHandle glPixelStorei = bindVoid("glPixelStorei", JAVA_INT, JAVA_INT);
    private static final MethodHandle glReadPixels = bindVoid("glReadPixels",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);

    private static final MethodHandle glCreateShader = bind("glCreateShader", JAVA_INT, JAVA_INT);
    private static final MethodHandle glShaderSource = bindVoid("glShaderSource", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle glCompileShader = bindVoid("glCompileShader", JAVA_INT);
    private static final MethodHandle glGetShaderiv = bindVoid("glGetShaderiv", JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle glGetShaderInfoLog = bindVoid("glGetShaderInfoLog", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle glDeleteShader = bindVoid("glDeleteShader", JAVA_INT);

    private static final MethodHandle glCreateProgram = bind("glCreateProgram", JAVA_INT);
    private static final MethodHandle glAttachShader = bindVoid("glAttachShader", JAVA_INT, JAVA_INT);
    private static final MethodHandle glLinkProgram = bindVoid("glLinkProgram", JAVA_INT);
    private static final MethodHandle glGetProgramiv = bindVoid("glGetProgramiv", JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle glGetProgramInfoLog = bindVoid("glGetProgramInfoLog", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle glUseProgram = bindVoid("glUseProgram", JAVA_INT);
    private static final MethodHandle glDeleteProgram = bindVoid("glDeleteProgram", JAVA_INT);

    private static final MethodHandle glGetUniformLocation = bind("glGetUniformLocation", JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle glUniform1i = bindVoid("glUniform1i", JAVA_INT, JAVA_INT);
    private static final MethodHandle glUniform1f = bindVoid("glUniform1f", JAVA_INT, JAVA_FLOAT);
    private static final MethodHandle glUniform3f = bindVoid("glUniform3f", JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT);
    private static final MethodHandle glUniformMatrix4fv = bindVoid("glUniformMatrix4fv", JAVA_INT, JAVA_INT, JAVA_BYTE, ADDRESS);

    private static final MethodHandle glGenVertexArrays = bindVoid("glGenVertexArrays", JAVA_INT, ADDRESS);
    private static final MethodHandle glBindVertexArray = bindVoid("glBindVertexArray", JAVA_INT);
    private static final MethodHandle glDeleteVertexArrays = bindVoid("glDeleteVertexArrays", JAVA_INT, ADDRESS);
    private static final MethodHandle glGenBuffers = bindVoid("glGenBuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glBindBuffer = bindVoid("glBindBuffer", JAVA_INT, JAVA_INT);
    private static final MethodHandle glBufferData = bindVoid("glBufferData", JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT);
    private static final MethodHandle glDeleteBuffers = bindVoid("glDeleteBuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glEnableVertexAttribArray = bindVoid("glEnableVertexAttribArray", JAVA_INT);
    private static final MethodHandle glVertexAttribPointer = bindVoid("glVertexAttribPointer",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_BYTE, JAVA_INT, JAVA_LONG);

    private static final MethodHandle glDrawArrays = bindVoid("glDrawArrays", JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle glDrawElements = bindVoid("glDrawElements", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG);

    private static final MethodHandle glGenTextures = bindVoid("glGenTextures", JAVA_INT, ADDRESS);
    private static final MethodHandle glDeleteTextures = bindVoid("glDeleteTextures", JAVA_INT, ADDRESS);
    private static final MethodHandle glBindTexture = bindVoid("glBindTexture", JAVA_INT, JAVA_INT);
    private static final MethodHandle glActiveTexture = bindVoid("glActiveTexture", JAVA_INT);
    private static final MethodHandle glTexParameteri = bindVoid("glTexParameteri", JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle glTexImage2D = bindVoid("glTexImage2D",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);

    private static final MethodHandle glGenFramebuffers = bindVoid("glGenFramebuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glDeleteFramebuffers = bindVoid("glDeleteFramebuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glBindFramebuffer = bindVoid("glBindFramebuffer", JAVA_INT, JAVA_INT);
    private static final MethodHandle glCheckFramebufferStatus = bind("glCheckFramebufferStatus", JAVA_INT, JAVA_INT);
    private static final MethodHandle glFramebufferRenderbuffer = bindVoid("glFramebufferRenderbuffer",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle glBlitFramebuffer = bindVoid("glBlitFramebuffer",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle glGenRenderbuffers = bindVoid("glGenRenderbuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glDeleteRenderbuffers = bindVoid("glDeleteRenderbuffers", JAVA_INT, ADDRESS);
    private static final MethodHandle glBindRenderbuffer = bindVoid("glBindRenderbuffer", JAVA_INT, JAVA_INT);
    private static final MethodHandle glRenderbufferStorage = bindVoid("glRenderbufferStorage",
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);

    private static MethodHandle bind(String name, MemoryLayout result, MemoryLayout... parameters) {
        return GLLoader.bind(name, FunctionDescriptor.of(result, parameters));
    }

    private static MethodHandle bindVoid(String name, MemoryLayout... parameters) {
        return GLLoader.bind(name, FunctionDescriptor.ofVoid(parameters));
    }

    // -------------------------------------------------------------------- state

    public static void glViewport(int x, int y, int width, int height) {
        try {
            glViewport.invokeExact(x, y, width, height);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glClearColor(float red, float green, float blue, float alpha) {
        try {
            glClearColor.invokeExact(red, green, blue, alpha);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glClear(int mask) {
        try {
            glClear.invokeExact(mask);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glEnable(int capability) {
        try {
            glEnable.invokeExact(capability);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBlendFunc(int sourceFactor, int destinationFactor) {
        try {
            glBlendFunc.invokeExact(sourceFactor, destinationFactor);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDisable(int capability) {
        try {
            glDisable.invokeExact(capability);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDepthFunc(int function) {
        try {
            glDepthFunc.invokeExact(function);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glCullFace(int mode) {
        try {
            glCullFace.invokeExact(mode);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glGetError() {
        try {
            return (int) glGetError.invokeExact();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Returns e.g. {@code GL_VERSION} or {@code GL_RENDERER} as a Java string. */
    public static String glGetString(int name) {
        try {
            return Native.readCString((MemorySegment) glGetString.invokeExact(name));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glPixelStorei(int parameter, int value) {
        try {
            glPixelStorei.invokeExact(parameter, value);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, MemorySegment pixels) {
        try {
            glReadPixels.invokeExact(x, y, width, height, format, type, pixels);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------------------ shaders

    public static int glCreateShader(int type) {
        try {
            return (int) glCreateShader.invokeExact(type);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glShaderSource(int shader, String source) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = Native.cString(arena, source);
            MemorySegment strings = arena.allocate(ADDRESS);
            strings.set(ADDRESS, 0, text);
            glShaderSource.invokeExact(shader, 1, strings, MemorySegment.NULL);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glCompileShader(int shader) {
        try {
            glCompileShader.invokeExact(shader);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glGetShaderi(int shader, int parameter) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_INT);
            glGetShaderiv.invokeExact(shader, parameter, value);
            return value.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static String glGetShaderInfoLog(int shader) {
        int length = Math.max(1, glGetShaderi(shader, GL_INFO_LOG_LENGTH));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment log = Native.bytes(arena, length);
            glGetShaderInfoLog.invokeExact(shader, length, MemorySegment.NULL, log);
            return Native.readCString(log).trim();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteShader(int shader) {
        try {
            glDeleteShader.invokeExact(shader);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glCreateProgram() {
        try {
            return (int) glCreateProgram.invokeExact();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glAttachShader(int program, int shader) {
        try {
            glAttachShader.invokeExact(program, shader);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glLinkProgram(int program) {
        try {
            glLinkProgram.invokeExact(program);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glGetProgrami(int program, int parameter) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_INT);
            glGetProgramiv.invokeExact(program, parameter, value);
            return value.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static String glGetProgramInfoLog(int program) {
        int length = Math.max(1, glGetProgrami(program, GL_INFO_LOG_LENGTH));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment log = Native.bytes(arena, length);
            glGetProgramInfoLog.invokeExact(program, length, MemorySegment.NULL, log);
            return Native.readCString(log).trim();
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glUseProgram(int program) {
        try {
            glUseProgram.invokeExact(program);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteProgram(int program) {
        try {
            glDeleteProgram.invokeExact(program);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ----------------------------------------------------------------- uniforms

    public static int glGetUniformLocation(int program, String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) glGetUniformLocation.invokeExact(program, Native.cString(arena, name));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glUniform1i(int location, int value) {
        try {
            glUniform1i.invokeExact(location, value);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glUniform1f(int location, float value) {
        try {
            glUniform1f.invokeExact(location, value);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glUniform3f(int location, float x, float y, float z) {
        try {
            glUniform3f.invokeExact(location, x, y, z);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param matrices native memory holding {@code count} column-major 4x4 float matrices */
    public static void glUniformMatrix4fv(int location, int count, boolean transpose, MemorySegment matrices) {
        try {
            glUniformMatrix4fv.invokeExact(location, count, (byte) (transpose ? GL_TRUE : GL_FALSE), matrices);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ----------------------------------------------------- vertex arrays/buffers

    public static int glGenVertexArray() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(JAVA_INT);
            glGenVertexArrays.invokeExact(1, id);
            return id.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBindVertexArray(int vertexArray) {
        try {
            glBindVertexArray.invokeExact(vertexArray);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteVertexArray(int vertexArray) {
        try (Arena arena = Arena.ofConfined()) {
            glDeleteVertexArrays.invokeExact(1, Native.ints(arena, vertexArray));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glGenBuffer() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(JAVA_INT);
            glGenBuffers.invokeExact(1, id);
            return id.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBindBuffer(int target, int buffer) {
        try {
            glBindBuffer.invokeExact(target, buffer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Uploads the whole segment. */
    public static void glBufferData(int target, MemorySegment data, int usage) {
        try {
            glBufferData.invokeExact(target, data.byteSize(), data, usage);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBufferData(int target, float[] data, int usage) {
        try (Arena arena = Arena.ofConfined()) {
            glBufferData(target, Native.floats(arena, data), usage);
        }
    }

    public static void glBufferData(int target, int[] data, int usage) {
        try (Arena arena = Arena.ofConfined()) {
            glBufferData(target, Native.ints(arena, data), usage);
        }
    }

    public static void glDeleteBuffer(int buffer) {
        try (Arena arena = Arena.ofConfined()) {
            glDeleteBuffers.invokeExact(1, Native.ints(arena, buffer));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glEnableVertexAttribArray(int index) {
        try {
            glEnableVertexAttribArray.invokeExact(index);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param offset byte offset of the attribute inside one vertex of the bound array buffer */
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        try {
            glVertexAttribPointer.invokeExact(index, size, type, (byte) (normalized ? GL_TRUE : GL_FALSE), stride, offset);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------------------ drawing

    public static void glDrawArrays(int mode, int first, int count) {
        try {
            glDrawArrays.invokeExact(mode, first, count);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** @param offset byte offset into the bound element array buffer */
    public static void glDrawElements(int mode, int count, int type, long offset) {
        try {
            glDrawElements.invokeExact(mode, count, type, offset);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ----------------------------------------------------------------- textures

    public static int glGenTexture() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(JAVA_INT);
            glGenTextures.invokeExact(1, id);
            return id.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteTexture(int id) {
        try (Arena arena = Arena.ofConfined()) {
            glDeleteTextures.invokeExact(1, Native.ints(arena, id));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBindTexture(int target, int texture) {
        try {
            glBindTexture.invokeExact(target, texture);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glActiveTexture(int unit) {
        try {
            glActiveTexture.invokeExact(unit);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glTexParameteri(int target, int parameter, int value) {
        try {
            glTexParameteri.invokeExact(target, parameter, value);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Uploads level 0 of a 2D texture; {@code pixels} holds tightly packed rows. */
    public static void glTexImage2D(int target, int internalFormat, int width, int height, int format, int type,
                                    MemorySegment pixels) {
        try {
            glTexImage2D.invokeExact(target, 0, internalFormat, width, height, 0, format, type, pixels);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    // ------------------------------------------------------------- framebuffers

    public static int glGenFramebuffer() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(JAVA_INT);
            glGenFramebuffers.invokeExact(1, id);
            return id.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteFramebuffer(int id) {
        try (Arena arena = Arena.ofConfined()) {
            glDeleteFramebuffers.invokeExact(1, Native.ints(arena, id));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
        try {
            glBindFramebuffer.invokeExact(target, framebuffer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glCheckFramebufferStatus(int target) {
        try {
            return (int) glCheckFramebufferStatus.invokeExact(target);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        try {
            glFramebufferRenderbuffer.invokeExact(target, attachment, renderbufferTarget, renderbuffer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        try {
            glBlitFramebuffer.invokeExact(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static int glGenRenderbuffer() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(JAVA_INT);
            glGenRenderbuffers.invokeExact(1, id);
            return id.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glDeleteRenderbuffer(int id) {
        try (Arena arena = Arena.ofConfined()) {
            glDeleteRenderbuffers.invokeExact(1, Native.ints(arena, id));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glBindRenderbuffer(int target, int renderbuffer) {
        try {
            glBindRenderbuffer.invokeExact(target, renderbuffer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void glRenderbufferStorage(int target, int internalFormat, int width, int height) {
        try {
            glRenderbufferStorage.invokeExact(target, internalFormat, width, height);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }
}
