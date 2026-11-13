package com.aresstack.threed4j.math;

/**
 * Immutable 4x4 float matrix in OpenGL conventions: column-major storage,
 * right-handed coordinates, clip space depth from -1 to 1.
 *
 * <p>{@code a.multiply(b)} applies {@code b} first, then {@code a} – so a typical
 * model matrix is {@code translation.multiply(rotation).multiply(scaling)}.</p>
 */
public final class Mat4 {

    /** Element at row r, column c is stored at index {@code c * 4 + r}. */
    private final float[] m;

    private Mat4(float[] columnMajor) {
        this.m = columnMajor;
    }

    public static Mat4 identity() {
        return new Mat4(new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1});
    }

    public static Mat4 translation(float x, float y, float z) {
        return new Mat4(new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                x, y, z, 1});
    }

    public static Mat4 translation(Vec3 offset) {
        return translation(offset.x(), offset.y(), offset.z());
    }

    public static Mat4 scaling(float x, float y, float z) {
        return new Mat4(new float[]{
                x, 0, 0, 0,
                0, y, 0, 0,
                0, 0, z, 0,
                0, 0, 0, 1});
    }

    public static Mat4 rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new Mat4(new float[]{
                1, 0, 0, 0,
                0, c, s, 0,
                0, -s, c, 0,
                0, 0, 0, 1});
    }

    public static Mat4 rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new Mat4(new float[]{
                c, 0, -s, 0,
                0, 1, 0, 0,
                s, 0, c, 0,
                0, 0, 0, 1});
    }

    public static Mat4 rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new Mat4(new float[]{
                c, s, 0, 0,
                -s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1});
    }

    /**
     * Perspective projection.
     *
     * @param fovY   vertical field of view in radians
     * @param aspect width divided by height
     */
    public static Mat4 perspective(float fovY, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(fovY / 2.0));
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return new Mat4(m);
    }

    /** View matrix for a camera at {@code eye} looking at {@code target}. */
    public static Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
        Vec3 forward = target.subtract(eye).normalize();
        Vec3 side = forward.cross(up).normalize();
        Vec3 upward = side.cross(forward);
        return new Mat4(new float[]{
                side.x(), upward.x(), -forward.x(), 0,
                side.y(), upward.y(), -forward.y(), 0,
                side.z(), upward.z(), -forward.z(), 0,
                -side.dot(eye), -upward.dot(eye), forward.dot(eye), 1});
    }

    /** Returns {@code this * right}. */
    public Mat4 multiply(Mat4 right) {
        float[] a = m;
        float[] b = right.m;
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                result[column * 4 + row] = sum;
            }
        }
        return new Mat4(result);
    }

    /** Transforms a point (w = 1) and performs the perspective divide. */
    public Vec3 transformPoint(Vec3 p) {
        float x = m[0] * p.x() + m[4] * p.y() + m[8] * p.z() + m[12];
        float y = m[1] * p.x() + m[5] * p.y() + m[9] * p.z() + m[13];
        float z = m[2] * p.x() + m[6] * p.y() + m[10] * p.z() + m[14];
        float w = m[3] * p.x() + m[7] * p.y() + m[11] * p.z() + m[15];
        return w == 1f || w == 0f ? new Vec3(x, y, z) : new Vec3(x / w, y / w, z / w);
    }

    public float get(int row, int column) {
        return m[column * 4 + row];
    }

    /** Copies the 16 elements in column-major order, the layout OpenGL expects. */
    public void copyTo(float[] target, int offset) {
        System.arraycopy(m, 0, target, offset, 16);
    }
}
