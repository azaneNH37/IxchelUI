package com.azane.ixchel.mc.source;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * Projects world positions onto the GUI for the frame being rendered, so a HUD can place an element
 * over an entity through bound {@code left} / {@code top} styles:
 *
 * <pre>{@code
 * Optional<Vector2f> p = WorldProjection.toGui(entity.getPosition(WorldProjection.partialTick())
 *         .add(0, entity.getBbHeight() + 0.5, 0));
 * }</pre>
 *
 * <p>Results are in GUI-scaled pixels with the origin at the top-left of the window: the space a HUD
 * or screen document is laid out in. They use the camera and matrices the level was drawn with in
 * the current frame (rotation and roll, field of view including dynamic FOV and zoom, view bobbing,
 * hurt tilt, nausea warp), captured by a listener the loader glue installs with the first HUD
 * ({@code IxchelForge.registerHud} / {@code IxchelNeoForge.registerHud}) of this library copy.
 * The level is drawn before the GUI, so a HUD or screen sees the frame it is part of. Client render
 * thread only.
 */
public final class WorldProjection {
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static boolean captured;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static float partialTick;

    private WorldProjection() {
    }

    /**
     * Records the camera of the level render in progress; the loader glue calls this once per frame.
     *
     * @param modelView   the camera rotation the level is drawn with; positions are relative to the camera
     * @param projection  the projection the level is drawn with
     * @param camera      the camera position in the world
     * @param partialTick the partial tick the camera was placed with
     */
    public static void capture(Matrix4fc modelView, Matrix4fc projection, Vec3 camera, float partialTick) {
        projection.mul(modelView, VIEW_PROJECTION);
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        WorldProjection.partialTick = partialTick;
        captured = true;
    }

    /**
     * Where {@code world} appears on the GUI this frame, in GUI-scaled pixels from the top-left corner.
     * Empty when the point is behind the camera or outside the view, without a level, or before the
     * first frame was captured.
     */
    public static Optional<Vector2f> toGui(Vec3 world) {
        Minecraft mc = Minecraft.getInstance();
        if (!captured || mc.level == null) {
            return Optional.empty();
        }
        Window window = mc.getWindow();
        // The GUI projection spans the framebuffer divided by the scale, not the rounded-up scaled size.
        float guiWidth = (float) (window.getWidth() / window.getGuiScale());
        float guiHeight = (float) (window.getHeight() / window.getGuiScale());
        Vector2f out = new Vector2f();
        // Subtract in double first: world coordinates lose precision as floats far from the origin.
        boolean visible = project(VIEW_PROJECTION, (float) (world.x - cameraX), (float) (world.y - cameraY),
                (float) (world.z - cameraZ), guiWidth, guiHeight, out);
        return visible ? Optional.of(out) : Optional.empty();
    }

    /** The partial tick of the captured frame, for interpolating positions to pass to {@link #toGui}. */
    public static float partialTick() {
        return partialTick;
    }

    /**
     * Projects a camera-relative point through the view-projection {@code m} (OpenGL clip space) onto a
     * GUI of the given size. Returns {@code false}, leaving {@code out} unspecified, when the point is
     * behind the camera or outside the view volume; points on its boundary are inside.
     */
    static boolean project(Matrix4fc m, float x, float y, float z, float guiWidth, float guiHeight, Vector2f out) {
        float cx = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
        float cy = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
        float cz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
        float cw = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
        if (!(cw > 0) || Math.abs(cx) > cw || Math.abs(cy) > cw || Math.abs(cz) > cw) {
            return false;
        }
        out.set((cx / cw + 1) * 0.5f * guiWidth, (1 - cy / cw) * 0.5f * guiHeight);
        return true;
    }
}
