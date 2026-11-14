package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.assets.District.Component;
import com.aresstack.threed4j.assets.District.Landmark;
import com.aresstack.threed4j.assets.District.Shape;
import com.aresstack.threed4j.gfx.MeshBuilder;
import com.aresstack.threed4j.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a landmark from its plan. A component is a flat drawing with a rule
 * that gives it a body: half a side view turned around the upright axis, or an
 * outline seen from above pulled up to a height. The model that planned the
 * landmark only ever drew the flat part.
 */
final class Landmarks {

    private static final int SIDES = 14;

    private Landmarks() {
    }

    /** Adds the landmark to the mesh, its middle at this place of the world. */
    static void build(MeshBuilder builder, Landmark landmark, float centerX, float centerZ) {
        for (Component component : landmark.components()) {
            float x = centerX + component.x();
            float z = centerZ + component.z();
            if (component.shape() == Shape.LATHE) {
                lathe(builder, component, x, z);
            } else {
                extrude(builder, component, x, z);
            }
        }
    }

    static Vec3 color(int rgb) {
        return new Vec3(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    /**
     * Turns half a side view around the upright axis. The line runs from bottom to
     * top; it is closed towards the axis at both ends, so that the body has a
     * bottom and a top.
     */
    private static void lathe(MeshBuilder builder, Component component, float x, float z) {
        List<float[]> line = new ArrayList<>(); // radius and height in metres
        for (float[] point : component.outline()) {
            line.add(new float[] {point[0] * component.width(), component.base() + point[1] * component.height()});
        }
        if (line.get(0)[0] > 0.001f) {
            line.add(0, new float[] {0f, line.get(0)[1]});
        }
        if (line.get(line.size() - 1)[0] > 0.001f) {
            line.add(new float[] {0f, line.get(line.size() - 1)[1]});
        }
        Vec3 side = color(component.color());
        Vec3 top = color(component.topColor());
        for (int i = 0; i + 1 < line.size(); i++) {
            float lowRadius = line.get(i)[0];
            float lowY = line.get(i)[1];
            float highRadius = line.get(i + 1)[0];
            float highY = line.get(i + 1)[1];
            float rise = highY - lowY;
            float inward = lowRadius - highRadius;
            if (Math.abs(rise) < 0.0005f && Math.abs(inward) < 0.0005f) {
                continue;
            }
            // A face that looks up: flat or gently sloping, and narrowing towards the top.
            Vec3 color = inward > 0 && rise < 0.6f * inward ? top : side;
            for (int step = 0; step < SIDES; step++) {
                double angle0 = 2 * Math.PI * step / SIDES;
                double angle1 = 2 * Math.PI * (step + 1) / SIDES;
                float cos0 = (float) Math.cos(angle0);
                float sin0 = (float) Math.sin(angle0);
                float cos1 = (float) Math.cos(angle1);
                float sin1 = (float) Math.sin(angle1);
                Vec3 low0 = new Vec3(x + cos0 * lowRadius, lowY, z + sin0 * lowRadius);
                Vec3 low1 = new Vec3(x + cos1 * lowRadius, lowY, z + sin1 * lowRadius);
                Vec3 high0 = new Vec3(x + cos0 * highRadius, highY, z + sin0 * highRadius);
                Vec3 high1 = new Vec3(x + cos1 * highRadius, highY, z + sin1 * highRadius);
                if (lowRadius < 0.001f) {
                    builder.triangle(low0, high0, high1, color);
                } else if (highRadius < 0.001f) {
                    builder.triangle(low1, low0, high0, color);
                } else {
                    builder.quad(low1, low0, high0, high1, color);
                }
            }
        }
    }

    /** Pulls an outline seen from above up to the component's height, with a flat top or a pointed roof. */
    private static void extrude(MeshBuilder builder, Component component, float x, float z) {
        List<float[]> corners = new ArrayList<>(); // x and z in metres
        for (float[] point : component.outline()) {
            corners.add(new float[] {x + point[0] * component.width(), z + point[1] * component.depth()});
        }
        // Walls look outward when the corners run this way round.
        if (area(corners) > 0) {
            java.util.Collections.reverse(corners);
        }
        float base = component.base();
        float top = base + component.height();
        Vec3 side = color(component.color());
        Vec3 roof = color(component.topColor());
        int count = corners.size();
        for (int i = 0; i < count; i++) {
            float[] p = corners.get(i);
            float[] q = corners.get((i + 1) % count);
            if (Math.hypot(p[0] - q[0], p[1] - q[1]) < 0.001) {
                continue;
            }
            builder.quad(new Vec3(p[0], base, p[1]), new Vec3(q[0], base, q[1]), new Vec3(q[0], top, q[1]),
                    new Vec3(p[0], top, p[1]), side);
        }
        if (component.roofHeight() > 0.01f) {
            float middleX = 0f;
            float middleZ = 0f;
            for (float[] corner : corners) {
                middleX += corner[0] / count;
                middleZ += corner[1] / count;
            }
            Vec3 apex = new Vec3(middleX, top + component.roofHeight(), middleZ);
            for (int i = 0; i < count; i++) {
                float[] p = corners.get(i);
                float[] q = corners.get((i + 1) % count);
                if (Math.hypot(p[0] - q[0], p[1] - q[1]) < 0.001) {
                    continue;
                }
                builder.triangle(new Vec3(p[0], top, p[1]), new Vec3(q[0], top, q[1]), apex, roof);
            }
            return;
        }
        for (int[] triangle : triangles(corners)) {
            float[] a = corners.get(triangle[0]);
            float[] b = corners.get(triangle[1]);
            float[] c = corners.get(triangle[2]);
            builder.triangle(new Vec3(a[0], top, a[1]), new Vec3(b[0], top, b[1]), new Vec3(c[0], top, c[1]), roof);
        }
    }

    private static float area(List<float[]> polygon) {
        float sum = 0f;
        for (int i = 0; i < polygon.size(); i++) {
            float[] p = polygon.get(i);
            float[] q = polygon.get((i + 1) % polygon.size());
            sum += p[0] * q[1] - q[0] * p[1];
        }
        return sum / 2;
    }

    private static float turn(float[] a, float[] b, float[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    /**
     * Cuts a polygon whose area is negative (see {@link #extrude}) into triangles by
     * cutting off ears, so that outlines with inward corners (an L, a U) get a
     * proper top.
     *
     * @return for each triangle the indices of its three corners
     */
    static List<int[]> triangles(List<float[]> polygon) {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            left.add(i);
        }
        List<int[]> triangles = new ArrayList<>();
        int guard = polygon.size() * polygon.size() + 8;
        while (left.size() > 3 && guard-- > 0) {
            boolean cut = false;
            for (int i = 0; i < left.size(); i++) {
                int before = left.get((i + left.size() - 1) % left.size());
                int here = left.get(i);
                int after = left.get((i + 1) % left.size());
                float[] a = polygon.get(before);
                float[] b = polygon.get(here);
                float[] c = polygon.get(after);
                if (turn(a, b, c) >= 0) {
                    continue; // an inward corner, or three corners in a line
                }
                boolean empty = true;
                for (int other : left) {
                    if (other == before || other == here || other == after) {
                        continue;
                    }
                    float[] p = polygon.get(other);
                    if (turn(a, b, p) <= 0 && turn(b, c, p) <= 0 && turn(c, a, p) <= 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    triangles.add(new int[] {before, here, after});
                    left.remove(i);
                    cut = true;
                    break;
                }
            }
            if (!cut) {
                left.remove(0); // a degenerate rest: drop a corner rather than loop
            }
        }
        if (left.size() == 3 && turn(polygon.get(left.get(0)), polygon.get(left.get(1)), polygon.get(left.get(2))) < 0) {
            triangles.add(new int[] {left.get(0), left.get(1), left.get(2)});
        }
        return triangles;
    }
}
