package com.aresstack.threed4j.gfx;

/** A 2D image on the GPU. Bound to a shader with {@link Shader#setTexture}. */
public interface Texture extends AutoCloseable {

    int width();

    int height();

    @Override
    void close();
}
