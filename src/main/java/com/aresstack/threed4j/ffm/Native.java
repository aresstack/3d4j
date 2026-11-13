package com.aresstack.threed4j.ffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Small helper layer on top of the Foreign Function &amp; Memory API.
 *
 * <p>Everything in the FFM API that was renamed between the Java 21 preview and the
 * final API in Java 22 ({@code allocateUtf8String}, {@code getUtf8String},
 * {@code allocateArray}) is used in this class only. Moving the engine to a newer
 * Java version therefore means touching this one file.</p>
 */
public final class Native {

    public static final Linker LINKER = Linker.nativeLinker();

    private Native() {
    }

    // ---------------------------------------------------------------- libraries

    /**
     * Loads a shared library and keeps it loaded for the lifetime of the JVM.
     *
     * @param names candidate names, tried in order (e.g. {@code "libGL.so.1", "libGL.so"})
     */
    public static SymbolLookup library(String... names) {
        IllegalArgumentException last = null;
        for (String name : names) {
            try {
                return SymbolLookup.libraryLookup(name, Arena.global());
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw new UnsatisfiedLinkError("Cannot load native library " + String.join(" / ", names)
                + (last != null ? ": " + last.getMessage() : ""));
    }

    /** Looks up an exported function, or returns {@link MemorySegment#NULL} if it does not exist. */
    public static MemorySegment symbol(SymbolLookup library, String name) {
        return library.find(name).orElse(MemorySegment.NULL);
    }

    /** Creates a method handle for an exported function. */
    public static MethodHandle downcall(SymbolLookup library, String name, FunctionDescriptor descriptor,
                                        Linker.Option... options) {
        MemorySegment address = library.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Native function not found: " + name));
        return LINKER.downcallHandle(address, descriptor, options);
    }

    /** Creates a method handle for a function pointer. */
    public static MethodHandle downcall(MemorySegment address, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(address, descriptor);
    }

    /**
     * Creates a method handle for calling through function pointers that are only
     * known at call time (COM methods). The pointer is the handle's first argument.
     */
    public static MethodHandle functionPointer(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(descriptor);
    }

    // ------------------------------------------------------------------ strings

    /** Allocates a zero-terminated UTF-8 string ({@code const char*}). */
    public static MemorySegment cString(SegmentAllocator allocator, String value) {
        return allocator.allocateUtf8String(value);
    }

    /** Allocates a zero-terminated UTF-16 string ({@code LPCWSTR} on Windows). */
    public static MemorySegment wideString(SegmentAllocator allocator, String value) {
        char[] chars = new char[value.length() + 1];
        value.getChars(0, value.length(), chars, 0);
        return allocator.allocateArray(ValueLayout.JAVA_CHAR, chars);
    }

    /** Reads a zero-terminated UTF-8 string from a native pointer; {@code null} for a NULL pointer. */
    public static String readCString(MemorySegment pointer) {
        if (isNull(pointer)) {
            return null;
        }
        return pointer.reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    /** Reads a zero-terminated UTF-16 string of at most {@code maxChars} characters. */
    public static String readWideString(MemorySegment memory, int maxChars) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < maxChars; i++) {
            char c = memory.get(ValueLayout.JAVA_CHAR, i * 2L);
            if (c == 0) {
                break;
            }
            text.append(c);
        }
        return text.toString();
    }

    // ------------------------------------------------------------------- memory

    public static MemorySegment ints(SegmentAllocator allocator, int... values) {
        return allocator.allocateArray(ValueLayout.JAVA_INT, values);
    }

    public static MemorySegment floats(SegmentAllocator allocator, float... values) {
        return allocator.allocateArray(ValueLayout.JAVA_FLOAT, values);
    }

    /** Allocates {@code count} zero-initialized elements of the given layout. */
    public static MemorySegment array(SegmentAllocator allocator, MemoryLayout element, long count) {
        return allocator.allocateArray(element, count);
    }

    /** Allocates a zero-initialized block of raw memory, aligned for any primitive. */
    public static MemorySegment bytes(SegmentAllocator allocator, long byteSize) {
        return allocator.allocate(byteSize, 8);
    }

    /** Gives a pointer returned by native code a size, so that its target can be read. */
    public static MemorySegment sized(MemorySegment pointer, long byteSize) {
        return pointer.reinterpret(byteSize);
    }

    public static boolean isNull(MemorySegment pointer) {
        return pointer == null || pointer.address() == 0L;
    }

    // ------------------------------------------------------------------- errors

    /**
     * Converts the {@code Throwable} that {@link MethodHandle#invokeExact} declares
     * into an unchecked exception. Usage: {@code throw Native.rethrow(t);}
     */
    public static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException runtime) {
            return runtime;
        }
        if (t instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Native call failed", t);
    }
}
