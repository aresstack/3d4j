package com.aresstack.threed4j.gfx;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the inputs of a shader: its vertex attributes and its uniforms.
 *
 * <p>OpenGL finds both by location and by name. Direct3D needs the exact memory
 * layout instead, so the order here is binding for the HLSL source: attribute
 * {@code i} has the semantic {@code ATTRIBUTEi}, and the uniforms form one
 * {@code cbuffer} at register {@code b0}, declared in the same order.</p>
 */
public final class ShaderLayout {

    public enum UniformType {
        FLOAT(1), VEC3(3), MAT4(16);

        private final int floats;

        UniformType(int floats) {
            this.floats = floats;
        }

        public int floats() {
            return floats;
        }
    }

    public record Uniform(String name, UniformType type) {
    }

    private final int[] attributeSizes;
    private final List<Uniform> uniforms = new ArrayList<>();

    private ShaderLayout(int[] attributeSizes) {
        this.attributeSizes = attributeSizes.clone();
    }

    /** @param sizes number of floats per vertex attribute, in shader location order */
    public static ShaderLayout attributes(int... sizes) {
        for (int size : sizes) {
            if (size < 1 || size > 4) {
                throw new IllegalArgumentException("A vertex attribute has 1 to 4 floats");
            }
        }
        return new ShaderLayout(sizes);
    }

    public ShaderLayout mat4(String name) {
        uniforms.add(new Uniform(name, UniformType.MAT4));
        return this;
    }

    public ShaderLayout vec3(String name) {
        uniforms.add(new Uniform(name, UniformType.VEC3));
        return this;
    }

    public ShaderLayout scalar(String name) {
        uniforms.add(new Uniform(name, UniformType.FLOAT));
        return this;
    }

    public int[] attributeSizes() {
        return attributeSizes.clone();
    }

    public List<Uniform> uniforms() {
        return List.copyOf(uniforms);
    }
}
