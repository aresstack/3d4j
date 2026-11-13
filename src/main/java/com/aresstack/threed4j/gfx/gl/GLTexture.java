package com.aresstack.threed4j.gfx.gl;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.Texture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.aresstack.threed4j.gl.GL.*;

final class GLTexture implements Texture {

    private final int id;
    private final int width;
    private final int height;

    GLTexture(int width, int height, int[] argb, boolean smooth) {
        this.width = width;
        this.height = height;
        id = glGenTexture();
        glBindTexture(GL_TEXTURE_2D, id);
        int filter = smooth ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        try (Arena arena = Arena.ofConfined()) {
            // The first row uploaded is the row texture coordinate v = 0 addresses.
            MemorySegment pixels = Native.bytes(arena, (long) width * height * 4);
            for (int i = 0; i < argb.length; i++) {
                int color = argb[i];
                long offset = i * 4L;
                pixels.set(ValueLayout.JAVA_BYTE, offset, (byte) (color >> 16));
                pixels.set(ValueLayout.JAVA_BYTE, offset + 1, (byte) (color >> 8));
                pixels.set(ValueLayout.JAVA_BYTE, offset + 2, (byte) color);
                pixels.set(ValueLayout.JAVA_BYTE, offset + 3, (byte) (color >>> 24));
            }
            glTexImage2D(GL_TEXTURE_2D, GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        }
    }

    int id() {
        return id;
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
        glDeleteTexture(id);
    }
}
