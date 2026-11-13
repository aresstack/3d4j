package com.aresstack.threed4j.gfx.gl;

import com.aresstack.threed4j.ffm.Native;
import com.aresstack.threed4j.gfx.Shader;
import com.aresstack.threed4j.gfx.ShaderLayout;
import com.aresstack.threed4j.gfx.ShaderSource;
import com.aresstack.threed4j.gfx.Texture;
import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

import static com.aresstack.threed4j.gl.GL.*;

/** A linked GLSL program. */
final class GLShader implements Shader {

    private final int id;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    /** Reused for every matrix upload, so that rendering allocates no native memory. */
    private final Arena arena = Arena.ofConfined();
    private final MemorySegment matrixBuffer = Native.array(arena, ValueLayout.JAVA_FLOAT, 16);
    private final float[] matrixValues = new float[16];
    private GLTexture texture;

    GLShader(String name, ShaderLayout layout) {
        int vertexShader = compile(GL_VERTEX_SHADER, ShaderSource.read("/shaders/" + name + ".vert"));
        int fragmentShader = compile(GL_FRAGMENT_SHADER, ShaderSource.read("/shaders/" + name + ".frag"));
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
            throw new IllegalStateException("Shader program '" + name + "' does not link:\n" + log);
        }
        for (ShaderLayout.Uniform uniform : layout.uniforms()) {
            // -1 if the GLSL compiler optimized the uniform away; OpenGL ignores writes to it.
            uniformLocations.put(uniform.name(), glGetUniformLocation(id, uniform.name()));
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

    void use() {
        glUseProgram(id);
        if (texture != null) {
            glActiveTexture(GL_TEXTURE0); // sampler uniforms default to unit 0
            glBindTexture(GL_TEXTURE_2D, texture.id());
        }
    }

    @Override
    public void setTexture(Texture texture) {
        this.texture = (GLTexture) texture;
    }

    @Override
    public void setUniform(String name, Mat4 matrix) {
        matrix.copyTo(matrixValues, 0);
        MemorySegment.copy(matrixValues, 0, matrixBuffer, ValueLayout.JAVA_FLOAT, 0, 16);
        glUseProgram(id);
        glUniformMatrix4fv(location(name), 1, false, matrixBuffer);
    }

    @Override
    public void setUniform(String name, Vec3 vector) {
        glUseProgram(id);
        glUniform3f(location(name), vector.x(), vector.y(), vector.z());
    }

    @Override
    public void setUniform(String name, float value) {
        glUseProgram(id);
        glUniform1f(location(name), value);
    }

    private int location(String name) {
        Integer location = uniformLocations.get(name);
        if (location == null) {
            throw new IllegalArgumentException("Uniform is not declared in the ShaderLayout: " + name);
        }
        return location;
    }

    @Override
    public void close() {
        glDeleteProgram(id);
        arena.close();
    }
}
