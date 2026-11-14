package com.aresstack.threed4j.demo;

import com.aresstack.threed4j.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands in the way in the town: houses as boxes, the fountain, tree trunks
 * and lamp posts as round posts. Everything stands on the ground and is solid up
 * to its height; above it the way is free, and one can come down on top of it.
 */
final class Obstacles {

    /** How far the eye stays above what it stands on; the same as above the ground. */
    static final float EYE_ABOVE = 0.6f;

    private record Box(float minX, float minZ, float maxX, float maxZ, float height) {
    }

    private record Post(float x, float z, float radius, float height) {
    }

    private final List<Box> boxes = new ArrayList<>();
    private final List<Post> posts = new ArrayList<>();

    void box(float minX, float minZ, float maxX, float maxZ, float height) {
        boxes.add(new Box(Math.min(minX, maxX), Math.min(minZ, maxZ), Math.max(minX, maxX), Math.max(minZ, maxZ), height));
    }

    void post(float x, float z, float radius, float height) {
        posts.add(new Post(x, z, radius, height));
    }

    int count() {
        return boxes.size() + posts.size();
    }

    /**
     * Where somebody ends up who goes from one place to another. A place inside
     * an obstacle is moved to the obstacle's nearest edge, so walking against a wall
     * at an angle slides along it. Who comes from above stays on top instead.
     *
     * @param from     where the eye is now
     * @param wanted   where it would be without obstacles
     * @param distance how far the eye stays away from walls
     */
    Vec3 move(Vec3 from, Vec3 wanted, float distance) {
        float x = wanted.x();
        float y = wanted.y();
        float z = wanted.z();
        // Twice, so that being pushed out of one obstacle into its neighbor is settled too.
        for (int pass = 0; pass < 2; pass++) {
            for (Box box : boxes) {
                float top = box.height + EYE_ABOVE;
                if (y >= top || x <= box.minX - distance || x >= box.maxX + distance
                        || z <= box.minZ - distance || z >= box.maxZ + distance) {
                    continue;
                }
                if (from.y() >= top - 0.001f) {
                    y = top;
                    continue;
                }
                float toMinX = x - (box.minX - distance);
                float toMaxX = box.maxX + distance - x;
                float toMinZ = z - (box.minZ - distance);
                float toMaxZ = box.maxZ + distance - z;
                float nearest = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
                if (nearest == toMinX) {
                    x = box.minX - distance;
                } else if (nearest == toMaxX) {
                    x = box.maxX + distance;
                } else if (nearest == toMinZ) {
                    z = box.minZ - distance;
                } else {
                    z = box.maxZ + distance;
                }
            }
            for (Post post : posts) {
                float top = post.height + EYE_ABOVE;
                float nearest = post.radius + distance;
                double fromMiddle = Math.hypot(x - post.x, z - post.z);
                if (y >= top || fromMiddle >= nearest) {
                    continue;
                }
                if (from.y() >= top - 0.001f) {
                    y = top;
                } else if (fromMiddle < 0.001) {
                    z = post.z + nearest;
                } else {
                    float scale = (float) (nearest / fromMiddle);
                    x = post.x + (x - post.x) * scale;
                    z = post.z + (z - post.z) * scale;
                }
            }
        }
        return x == wanted.x() && y == wanted.y() && z == wanted.z() ? wanted : new Vec3(x, y, z);
    }
}
