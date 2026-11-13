package com.aresstack.threed4j.gl;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.function.Function;

/**
 * Connects the {@link GL} bindings to the platform's way of finding OpenGL functions.
 *
 * <p>OpenGL functions newer than version 1.1 are not plain library exports on
 * Windows; they must be asked for through {@code wglGetProcAddress} while a context
 * is current. The platform window therefore installs a resolver here right after it
 * has created its context, and {@link GL} binds its functions when it is first used.</p>
 */
public final class GLLoader {

    private static volatile Function<String, MemorySegment> resolver;

    private GLLoader() {
    }

    /** Called by the platform window once its OpenGL context is current. */
    public static void install(Function<String, MemorySegment> functionResolver) {
        resolver = functionResolver;
    }

    static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        Function<String, MemorySegment> current = resolver;
        if (current == null) {
            throw new IllegalStateException(
                    "No OpenGL context yet: create a Window before using the GL class");
        }
        MemorySegment address = current.apply(name);
        if (Native.isNull(address)) {
            throw new UnsupportedOperationException("OpenGL function not available: " + name);
        }
        return Native.downcall(address, descriptor);
    }
}
