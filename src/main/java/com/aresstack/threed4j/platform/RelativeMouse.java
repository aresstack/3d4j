package com.aresstack.threed4j.platform;

/**
 * Turns the positions of a captured pointer into movement. The pointer is free
 * to move around the middle of the window and is put back there only when it
 * has strayed far, so that it never reaches an edge.
 *
 * <p>Movement is always measured from the previous position, never from the
 * middle: several positions may arrive between two looks at the pointer, and
 * each counts once. Putting the pointer back takes effect with a delay, most of
 * all in a remote session, where positions from before the jump still arrive
 * after it. Those are recognized by where they are and left out, so that the
 * view does not leap there and back.</p>
 */
public final class RelativeMouse {

    private int centerX;
    private int centerY;
    /** Beyond this distance from the middle the pointer is put back. */
    private int radius;
    private int lastX;
    private int lastY;
    /** The pointer was sent to the middle and has not been seen there yet. */
    private boolean returning;
    /** Until then positions that leap far from the middle are leftovers from before the pointer was put back. */
    private long settledAt;

    /** Starts measuring; the pointer has just been put in the middle of the window. */
    public void begin(int centerX, int centerY, int windowWidth, int windowHeight) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = Math.max(40, Math.min(windowWidth, windowHeight) / 4);
        lastX = centerX;
        lastY = centerY;
        returning = true; // whatever arrives from where the pointer was before does not count
        settledAt = 0;
    }

    /**
     * Takes a position of the pointer and reports the movement to the input.
     *
     * @return {@code true} if the pointer has to be put back in the middle now
     */
    public boolean move(int x, int y, Input input) {
        long now = System.nanoTime();
        double fromCenter = Math.hypot(x - centerX, y - centerY);
        boolean near = fromCenter <= radius * 0.5;
        if (returning) {
            if (!near) {
                // Still where it was before being put back: real movement, measured as always.
                int dx = x - lastX;
                int dy = y - lastY;
                lastX = x;
                lastY = y;
                // ... unless this is the very first look after capturing: then nothing is known yet.
                if (settledAt != -1 && (dx != 0 || dy != 0)) {
                    input.onMouseDelta(dx, dy);
                }
                return false;
            }
            // It has arrived near the middle: from here on it is measured from the middle.
            returning = false;
            lastX = centerX;
            lastY = centerY;
            settledAt = now + 300_000_000L;
        } else if (now < settledAt && !near && Math.hypot(x - lastX, y - lastY) > radius * 0.5) {
            // Far from the middle and far from where the pointer just was: a leftover from before
            // it was put back. Quick movement by hand gets there too, but step by step.
            return false;
        }
        int dx = x - lastX;
        int dy = y - lastY;
        lastX = x;
        lastY = y;
        if (dx != 0 || dy != 0) {
            input.onMouseDelta(dx, dy);
        }
        if (fromCenter > radius) {
            returning = true;
            settledAt = 0;
            return true;
        }
        return false;
    }

    /** Marks that the pointer was just put in the middle without having moved there by hand (capturing begins). */
    public void expectAtCenter() {
        returning = true;
        settledAt = -1;
    }
}
