package com.aresstack.threed4j.gfx.d3d11;

import com.aresstack.threed4j.gfx.Texture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;

/**
 * A texture as a shader sees it: a shader resource view plus the sampler that says
 * how to filter it. The sampler belongs to the device and is shared.
 */
final class D3D11Texture implements Texture {

    /** Shared: a texture may be made on one thread and used and closed on the render thread. */
    private final Arena arena = Arena.ofShared();
    private final int width;
    private final int height;
    /** The texture itself; NULL if someone else owns it (the scene image). */
    private final MemorySegment texture;
    private final MemorySegment view;

    /** Native arrays of one pointer each, as PSSetShaderResources and PSSetSamplers expect them. */
    private final MemorySegment viewArray;
    private final MemorySegment samplerArray;

    D3D11Texture(int width, int height, MemorySegment texture, MemorySegment view, MemorySegment sampler) {
        this.width = width;
        this.height = height;
        this.texture = texture;
        this.view = view;
        viewArray = arena.allocate(ADDRESS);
        viewArray.set(ADDRESS, 0, view);
        samplerArray = arena.allocate(ADDRESS);
        samplerArray.set(ADDRESS, 0, sampler);
    }

    MemorySegment viewArray() {
        return viewArray;
    }

    MemorySegment samplerArray() {
        return samplerArray;
    }

    void setSampler(MemorySegment sampler) {
        samplerArray.set(ADDRESS, 0, sampler);
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
    public void close() {
        ComObject.release(view);
        ComObject.release(texture);
        arena.close();
    }
}
