package com.aresstack.threed4j.gfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads shader source files from the classpath. */
public final class ShaderSource {

    private ShaderSource() {
    }

    /** @param path absolute resource path, e.g. {@code "/shaders/basic.vert"} */
    public static String read(String path) {
        try (InputStream in = ShaderSource.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Shader resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
