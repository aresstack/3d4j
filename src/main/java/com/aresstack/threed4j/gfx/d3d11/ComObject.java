package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.ffm.Native;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * A COM interface pointer.
 *
 * <p>A COM object starts with a pointer to its virtual method table, an array of
 * function pointers in the order the methods are declared in the C header
 * (including inherited ones: {@code QueryInterface}, {@code AddRef}, {@code Release}
 * are always slots 0 to 2). Calling a method means reading its slot and calling
 * that function with the object as first argument.</p>
 */
final class ComObject {

    private static final MethodHandle RELEASE =
            Native.functionPointer(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    final MemorySegment pointer;
    private final MemorySegment vtable;
    /** Function pointers already read, indexed by slot. */
    private final MemorySegment[] methods;

    /** @param methodCount number of slots this interface has (highest used slot + 1 is enough) */
    ComObject(MemorySegment pointer, int methodCount) {
        if (Native.isNull(pointer)) {
            throw new IllegalArgumentException("COM pointer is NULL");
        }
        this.pointer = pointer;
        this.vtable = Native.sized(Native.sized(pointer, ADDRESS.byteSize()).get(ADDRESS, 0),
                methodCount * ADDRESS.byteSize());
        this.methods = new MemorySegment[methodCount];
    }

    /** The function pointer in the given slot of the virtual method table. */
    MemorySegment method(int slot) {
        MemorySegment method = methods[slot];
        if (method == null) {
            method = vtable.get(ADDRESS, slot * ADDRESS.byteSize());
            methods[slot] = method;
        }
        return method;
    }

    /** Drops this reference; the object is destroyed when it was the last one. */
    void release() {
        try {
            int remaining = (int) RELEASE.invokeExact(method(2), pointer);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Releases a raw interface pointer; NULL is ignored. */
    static void release(MemorySegment pointer) {
        if (!Native.isNull(pointer)) {
            new ComObject(pointer, 3).release();
        }
    }
}
