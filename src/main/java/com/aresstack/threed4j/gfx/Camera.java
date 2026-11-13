package com.aresstack.threed4j.gfx;

import com.aresstack.threed4j.math.Mat4;
import com.aresstack.threed4j.math.Vec3;

/** A perspective camera that looks from a position at a target point. */
public final class Camera {

    private Vec3 position = new Vec3(0, 0, 3);
    private Vec3 target = Vec3.ZERO;
    private Vec3 up = Vec3.UNIT_Y;
    private float fieldOfView = (float) Math.toRadians(60);
    private float near = 0.1f;
    private float far = 100f;

    public Camera lookAt(Vec3 position, Vec3 target) {
        this.position = position;
        this.target = target;
        return this;
    }

    /**
     * Places the camera and turns it like a head: {@code yaw} around the vertical
     * axis (0 looks along -Z, positive turns right), {@code pitch} up and down.
     * Both in radians.
     */
    public Camera orient(Vec3 position, float yaw, float pitch) {
        Vec3 direction = new Vec3(
                (float) (Math.cos(pitch) * Math.sin(yaw)),
                (float) Math.sin(pitch),
                (float) (-Math.cos(pitch) * Math.cos(yaw)));
        return lookAt(position, position.add(direction));
    }

    /** Unit vector from the camera towards what it looks at. */
    public Vec3 forward() {
        return target.subtract(position).normalize();
    }

    /** Horizontal unit vector pointing to the camera's right. */
    public Vec3 right() {
        return forward().cross(up).normalize();
    }

    /** The view matrix without the camera's position: for things that follow the camera, like the sky. */
    public Mat4 viewRotationMatrix() {
        return Mat4.lookAt(Vec3.ZERO, forward(), up);
    }

    /** @param fieldOfView vertical field of view in radians */
    public Camera perspective(float fieldOfView, float near, float far) {
        this.fieldOfView = fieldOfView;
        this.near = near;
        this.far = far;
        return this;
    }

    public Vec3 position() {
        return position;
    }

    public Mat4 viewMatrix() {
        return Mat4.lookAt(position, target, up);
    }

    /** @param aspect width of the drawable area divided by its height */
    public Mat4 projectionMatrix(float aspect) {
        return Mat4.perspective(fieldOfView, aspect, near, far);
    }
}
