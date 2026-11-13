package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.aresstack.threed4j.gl.GL.*;

/** A linked GLSL program consisting of a vertex and a fragment shader. */
public final class ShaderProgram implements AutoCloseable {

    private final int id;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    /** Reused for every matrix upload, so that rendering allocates no native memory. */
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment matrixBuffer = Native.array(arena, ValueLayout.JAVA_FLOAT, 16);
    private final float[] matrixValues = new float[16];

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource);
        id = glCreateProgram();
        glAttachShader(id, vertexShader);
        glAttachShader(id, fragmentShader);
        glLinkProgram(id);
        // The program keeps the compiled code; the shader objects are no longer needed.
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(id);
            glDeleteProgram(id);
            throw new IllegalStateException("Shader program does not link:\n" + log);
        }
    }

    /** Loads both shaders from the classpath, e.g. {@code "/shaders/basic.vert"}. */
    public static ShaderProgram fromResources(String vertexPath, String fragmentPath) {
        return new ShaderProgram(readResource(vertexPath), readResource(fragmentPath));
    }

    private static String readResource(String path) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Shader resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            String kind = type == GL_VERTEX_SHADER ? "Vertex" : "Fragment";
            throw new IllegalStateException(kind + " shader does not compile:\n" + log);
        }
        return shader;
    }

    /** Makes this the program used by subsequent draw calls and {@code setUniform} calls. */
    public void use() {
        glUseProgram(id);
    }

    public void setUniform(String name, Mat4 matrix) {
        matrix.copyTo(matrixValues, 0);
        MemorySegment.copy(matrixValues, 0, matrixBuffer, ValueLayout.JAVA_FLOAT, 0, 16);
        glUniformMatrix4fv(location(name), 1, false, matrixBuffer);
    }

    public void setUniform(String name, Vec3 vector) {
        glUniform3f(location(name), vector.x(), vector.y(), vector.z());
    }

    public void setUniform(String name, float value) {
        glUniform1f(location(name), value);
    }

    public void setUniform(String name, int value) {
        glUniform1i(location(name), value);
    }

    /** -1 (which OpenGL silently ignores) if the shader does not use the uniform. */
    private int location(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
    }

    @Override
    public void close() {
        glDeleteProgram(id);
        arena.close();
    }
}
