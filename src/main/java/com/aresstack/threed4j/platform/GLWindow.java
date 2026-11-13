package com.aresstack.threed4j.platform;

/** A window that owns a current OpenGL 3.3 core context. */
public interface GLWindow extends Window {

    /** Shows the frame that was just rendered. */
    void swapBuffers();
}
