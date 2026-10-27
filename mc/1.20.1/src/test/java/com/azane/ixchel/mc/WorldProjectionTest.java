package com.azane.ixchel.mc;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProjectionTest {
    private static final float W = 320;
    private static final float H = 180;
    /** 90° vertical field of view on a 16:9 GUI, like the level renderer builds it. */
    private static final Matrix4f PROJECTION = new Matrix4f()
            .setPerspective((float) Math.toRadians(90), W / H, 0.05f, 256);

    private static Vector2f project(Matrix4f viewProjection, float x, float y, float z) {
        Vector2f out = new Vector2f();
        assertTrue(WorldProjection.project(viewProjection, x, y, z, W, H, out), x + " " + y + " " + z);
        return out;
    }

    @Test
    void viewAxisLandsInTheCentreAndScreenYGrowsDown() {
        Vector2f centre = project(PROJECTION, 0, 0, -10);
        assertEquals(W / 2, centre.x, 1e-3);
        assertEquals(H / 2, centre.y, 1e-3);
        // At distance 10 with a 90° vertical FOV the top edge is 10 up and the right edge 10·16/9 across.
        Vector2f upRight = project(PROJECTION, 5 * W / H, 5, -10);
        assertEquals(W * 3 / 4, upRight.x, 1e-3);
        assertEquals(H / 4, upRight.y, 1e-3);
        Vector2f below = project(PROJECTION, 0, -5, -10);
        assertEquals(H * 3 / 4, below.y, 1e-3);
    }

    @Test
    void pointsBehindOrOutsideTheViewAreNotProjected() {
        Vector2f out = new Vector2f();
        assertFalse(WorldProjection.project(PROJECTION, 0, 0, 10, W, H, out), "behind the camera");
        assertFalse(WorldProjection.project(PROJECTION, 0, 0, 0, W, H, out), "at the eye");
        assertFalse(WorldProjection.project(PROJECTION, 0, 0, -0.01f, W, H, out), "before the near plane");
        assertFalse(WorldProjection.project(PROJECTION, 0, 0, -300, W, H, out), "beyond the far plane");
        assertFalse(WorldProjection.project(PROJECTION, 0, 11, -10, W, H, out), "above the top edge");
        assertFalse(WorldProjection.project(PROJECTION, -10 * W / H - 1, 0, -10, W, H, out), "left of the left edge");
    }

    @Test
    void cameraRotationIsApplied() {
        // A view rotated 90° about Y looks along +X.
        Matrix4f view = new Matrix4f().rotationY((float) Math.toRadians(90));
        Matrix4f viewProjection = PROJECTION.mul(view, new Matrix4f());
        Vector2f ahead = project(viewProjection, 10, 0, 0);
        assertEquals(W / 2, ahead.x, 1e-3);
        assertEquals(H / 2, ahead.y, 1e-3);
        Vector2f out = new Vector2f();
        assertFalse(WorldProjection.project(viewProjection, 0, 0, -10, W, H, out), "the old view axis is now to the side");
    }
}
