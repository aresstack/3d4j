package com.aresstack.threed4j.math;

/** Immutable three-component float vector. */
public record Vec3(float x, float y, float z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UNIT_X = new Vec3(1, 0, 0);
    public static final Vec3 UNIT_Y = new Vec3(0, 1, 0);
    public static final Vec3 UNIT_Z = new Vec3(0, 0, 1);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(float factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public float dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public float length() {
        return (float) Math.sqrt(dot(this));
    }

    /** Returns the unit vector with the same direction; the zero vector stays zero. */
    public Vec3 normalize() {
        float length = length();
        return length == 0f ? this : scale(1f / length);
    }
}
