package com.aresstack.threed4j.gfx;

/** Indexed triangle geometry on the GPU. Drawn with {@link GraphicsDevice#draw}. */
public interface Mesh extends AutoCloseable {

    @Override
    void close();
}
