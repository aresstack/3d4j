package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;

/**
 * A vertex and fragment (pixel) shader pair. Uniform names must be declared in the
 * {@link ShaderLayout} the shader was created with.
 */
public interface Shader extends AutoCloseable {

    void setUniform(String name, Mat4 matrix);

    void setUniform(String name, Vec3 vector);

    void setUniform(String name, float value);

    /**
     * Sets the texture the shader samples: {@code uniform sampler2D uTexture} in GLSL,
     * {@code Texture2D uTexture : register(t0)} with {@code SamplerState uSampler : register(s0)} in HLSL.
     */
    void setTexture(Texture texture);

    @Override
    void close();
}
