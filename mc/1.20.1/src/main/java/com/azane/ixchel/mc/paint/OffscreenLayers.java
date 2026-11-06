package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.ClipPath;
import com.azane.ixchel.core.css.property.Filter;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.paint.ClipShape;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.RoundRect;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The private layers {@link McPainter} draws a group into ({@code DrawCommand.PushLayer}). A layer is
 * a region of the window in physical pixels, drawn into a pooled {@link TextureTarget} with depth:
 * the viewport is moved so that the GUI projection lands the region on the target's origin, so the
 * commands inside draw exactly as they would on the parent. Draws inside blend with separate alpha
 * factors, which leaves premultiplied color on the cleared target; a masked layer's mask is drawn the
 * same way onto a second target ({@link #mask}). {@link #filter} runs the group's filters over it and
 * {@link #composite} draws the result back onto the parent inside its {@code clip-path} shape, times
 * the mask's alpha, at the group's opacity with its blend mode: the shape is tested per pixel in the
 * composite program, so its edge is antialiased at any transform.
 *
 * <p>Entering and leaving keep the parent's framebuffer binding and viewport, so a layer opened while
 * another mod's target is bound returns there. Targets, the filter passes' included, are shared by the
 * painters of this library copy, reused by size across frames and released after
 * {@value #IDLE_SECONDS} seconds unused. Render thread only.
 */
final class OffscreenLayers {
    private static final int GRANULE = 64;
    private static final int IDLE_SECONDS = 2;
    private static final List<Target> POOL = new ArrayList<>();

    /** A pooled framebuffer: with depth for layers, without (and linearly filtered) for filter passes. */
    static final class Target {
        final TextureTarget fbo;
        final boolean depth;
        boolean busy;
        long lastUsed;

        Target(int width, int height, boolean depth) {
            fbo = new TextureTarget(width, height, depth, Minecraft.ON_OSX);
            fbo.setClearColor(0, 0, 0, 0);
            this.depth = depth;
            if (!depth) {
                fbo.setFilterMode(GL11.GL_LINEAR);
            }
        }
    }

    /** One open layer; reused across frames by nesting depth. */
    static final class Layer {
        Target target;
        /** The region in window pixels, y up from the bottom as GL counts it; empty when nothing inside shows. */
        int x, y, width, height;
        /** The same region in GUI pixels, y down. */
        float guiX0, guiY0, guiX1, guiY1;
        float opacity;
        List<Filter> filters;
        Keywords.MixBlendMode blend;
        /** The layer effects of the {@code -ix-paint()} filters, the element's border box they see and the time. */
        List<DrawCommand.LayerPaint> paints;
        RoundRect frame;
        float time;
        /**
         * GUI pixels back into the coordinates inside the element's transform, those of the frame and
         * the clip; {@code null} for a transform no point reaches.
         */
        Affine toLocal;
        /** The pointer in the frame's coordinates; NaN for none. */
        float mouseX, mouseY;
        /** The {@code clip-path} shape, in {@link #toLocal}'s coordinates; {@code null} for none. */
        ClipShape clip;
        /** The layer's mask once {@link #mask} started it; {@code null} for none. */
        Target mask;
        /** What {@link #composite} draws: the target, or the last filter pass's output. */
        Target result;
        int parentFbo;
        int parentX, parentY, parentWidth, parentHeight;

        boolean visible() {
            return target != null;
        }
    }

    private final List<Layer> layers = new ArrayList<>();
    private int depth;
    /** A clip polygon's vertices for the composite program's {@code Points}, x then y. */
    private final FloatBuffer points = BufferUtils.createFloatBuffer(2 * ClipPath.MAX_POINTS);

    boolean active() {
        return depth > 0;
    }

    /**
     * Opens a layer over the GUI rectangle, which the caller has already cut by its clip, cut further
     * by the window and the enclosing layer. Draws that follow go into it; with nothing left, they
     * are scissored away. {@code outer} is the transform current at the command; the pointer
     * ({@code pointerX}, {@code pointerY}) and {@code time} are what its layer effects see.
     */
    void push(float x0, float y0, float x1, float y1, DrawCommand.PushLayer c, Affine outer, float pointerX, float pointerY,
              float time) {
        if (depth == layers.size()) {
            layers.add(new Layer());
        }
        Layer parent = depth > 0 ? layers.get(depth - 1) : null;
        Layer l = layers.get(depth++);
        Window window = Minecraft.getInstance().getWindow();
        double s = window.getGuiScale();
        int h = window.getHeight();
        int left = Math.max(0, (int) Math.floor(x0 * s));
        int right = Math.min(window.getWidth(), (int) Math.ceil(x1 * s));
        int bottom = Math.max(0, h - (int) Math.ceil(y1 * s));
        int top = Math.min(h, h - (int) Math.floor(y0 * s));
        if (parent != null) {
            if (!parent.visible()) {
                left = right;
            } else {
                left = Math.max(left, parent.x);
                right = Math.min(right, parent.x + parent.width);
                bottom = Math.max(bottom, parent.y);
                top = Math.min(top, parent.y + parent.height);
            }
        }
        l.opacity = c.opacity();
        l.filters = c.filters();
        l.blend = c.blend();
        l.paints = c.paints();
        l.frame = c.frame();
        l.time = time;
        l.clip = c.clip();
        if (c.frame() != null || c.clip() != null) {
            l.toLocal = (c.transform() == null ? outer : outer.then(c.transform())).invert();
        }
        if (c.frame() != null) {
            boolean reached = l.toLocal != null && !Float.isNaN(pointerX);
            l.mouseX = reached ? l.toLocal.x(pointerX, pointerY) - c.frame().x() : Float.NaN;
            l.mouseY = reached ? l.toLocal.y(pointerX, pointerY) - c.frame().y() : Float.NaN;
        }
        l.target = null;
        l.result = null;
        l.mask = null;
        if (right <= left || top <= bottom) {
            return;
        }
        l.x = left;
        l.y = bottom;
        l.width = right - left;
        l.height = top - bottom;
        l.guiX0 = (float) (left / s);
        l.guiX1 = (float) (right / s);
        l.guiY0 = (float) ((h - top) / s);
        l.guiY1 = (float) ((h - bottom) / s);
        l.parentFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        l.parentX = GlStateManager.Viewport.x();
        l.parentY = GlStateManager.Viewport.y();
        l.parentWidth = GlStateManager.Viewport.width();
        l.parentHeight = GlStateManager.Viewport.height();
        l.target = acquire(l.width, l.height, true);
        l.result = l.target;
        RenderSystem.disableScissor();
        l.target.fbo.clear(Minecraft.ON_OSX);
        l.target.fbo.bindWrite(false);
        RenderSystem.viewport(-l.x, -l.y, window.getWidth(), h);
    }

    /**
     * Starts the innermost layer's mask ({@code DrawCommand.Mask}): draws that follow go onto a cleared
     * target of the layer's region, whose alpha {@link #composite} multiplies the layer by. The caller
     * applies the clip again, which this turns off.
     */
    void mask() {
        Layer l = layers.get(depth - 1);
        if (!l.visible()) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        l.mask = acquire(l.width, l.height, true);
        RenderSystem.disableScissor();
        l.mask.fbo.clear(Minecraft.ON_OSX);
        l.mask.fbo.bindWrite(false);
        RenderSystem.viewport(-l.x, -l.y, window.getWidth(), window.getHeight());
    }

    /** Closes the innermost layer and binds its parent again; the caller composites it, then {@link #release}s it. */
    Layer pop() {
        Layer l = layers.get(--depth);
        if (l.visible()) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, l.parentFbo);
            RenderSystem.viewport(l.parentX, l.parentY, l.parentWidth, l.parentHeight);
        }
        return l;
    }

    /**
     * Scissors the innermost layer to the GUI rectangle {@code (x0, y0)}-{@code (x1, y1)}, as
     * {@code GuiGraphics.enableScissor} does on the window; {@code clip} false for no clip.
     */
    void scissor(boolean clip, int x0, int y0, int x1, int y1) {
        Layer l = layers.get(depth - 1);
        if (!l.visible()) {
            RenderSystem.enableScissor(0, 0, 0, 0);
            return;
        }
        if (!clip) {
            RenderSystem.disableScissor();
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        double s = window.getGuiScale();
        RenderSystem.enableScissor((int) (x0 * s) - l.x, (int) (window.getHeight() - y1 * s) - l.y,
                Math.max(0, (int) ((x1 - x0) * s)), Math.max(0, (int) ((y1 - y0) * s)));
    }

    /**
     * Runs the closed layer's filters, leaving the parent bound again; the caller then restores the
     * scissor, which the passes turn off.
     */
    void filter(Layer l, FilterPasses passes) {
        if (!l.visible() || l.filters.isEmpty()) {
            return;
        }
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        l.result = passes.run(l.target, l.width, l.height, l.filters, (float) s, l);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, l.parentFbo);
        RenderSystem.viewport(l.parentX, l.parentY, l.parentWidth, l.parentHeight);
    }

    /**
     * Draws the closed layer {@code l} onto the bound target at its opacity with its blend mode, at
     * depth {@code z} of {@code pose}, only inside its clip shape (antialiased) and times its mask's
     * alpha. The layer holds premultiplied color. {@code multiply} drops the term for where the
     * target is transparent; {@code darken} and {@code lighten} are the MIN and MAX equations, the
     * layer put over white for MIN so that its transparent part changes nothing, which for partly
     * covered pixels is close to, not exactly, the CSS result.
     */
    void composite(Layer l, Matrix4f pose, float z, FilterShaders shaders) {
        Target t = l.result;
        if (l.clip != null && l.toLocal == null) {
            return; // a singular transform: no point of the shape shows
        }
        float u1 = l.width / (float) t.fbo.width;
        float v1 = l.height / (float) t.fbo.height;
        ShaderInstance shader = shaders.composite;
        shader.safeGetUniform("Opacity").set(l.opacity);
        shader.safeGetUniform("Under").set(l.blend == Keywords.MixBlendMode.DARKEN ? 1f : 0f);
        shader.safeGetUniform("Masked").set(l.mask != null ? 1f : 0f);
        shader.setSampler("Sampler1", (l.mask != null ? l.mask : t).fbo.getColorTextureId());
        if (l.mask != null) {
            shader.safeGetUniform("MaskScale").set(t.fbo.width / (float) l.mask.fbo.width, t.fbo.height / (float) l.mask.fbo.height);
        }
        int points = clip(l, t, shader);
        FilterShaders.apply(shader, t.fbo.getColorTextureId());
        if (points > 0) {
            // Arrays are not uniforms a program definition can declare; set on the bound program.
            GL20.glUniform2fv(GL20.glGetUniformLocation(shader.getId(), "Points"), this.points);
        }
        RenderSystem.enableBlend();
        GlStateManager.SourceFactor one = GlStateManager.SourceFactor.ONE;
        GlStateManager.DestFactor rest = GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA;
        int equation = GL14.GL_FUNC_ADD;
        switch (l.blend) {
            case NORMAL -> RenderSystem.blendFuncSeparate(one, rest, one, rest);
            case MULTIPLY -> RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, rest, one, rest);
            case SCREEN -> RenderSystem.blendFuncSeparate(one, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, one, rest);
            case PLUS_LIGHTER -> RenderSystem.blendFuncSeparate(one, GlStateManager.DestFactor.ONE, one, GlStateManager.DestFactor.ONE);
            case DARKEN -> equation = GL14.GL_MIN;
            case LIGHTEN -> equation = GL14.GL_MAX;
        }
        RenderSystem.blendEquation(equation);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, l.guiX0, l.guiY0, z).uv(0, v1).endVertex();
        buffer.vertex(pose, l.guiX0, l.guiY1, z).uv(0, 0).endVertex();
        buffer.vertex(pose, l.guiX1, l.guiY1, z).uv(u1, 0).endVertex();
        buffer.vertex(pose, l.guiX1, l.guiY0, z).uv(u1, v1).endVertex();
        FilterShaders.draw(shader, buffer.end());
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
    }

    void release(Layer l) {
        if (l.result != null && l.result != l.target) {
            release(l.result);
        }
        if (l.target != null) {
            release(l.target);
            l.target = null;
        }
        l.result = null;
        if (l.mask != null) {
            release(l.mask);
            l.mask = null;
        }
    }

    /**
     * Sets the clip uniforms of {@code shader} for {@code l}, whose image is in {@code t}: the shape,
     * and the map from texture coordinates to the shape's coordinates. Returns the polygon's vertex
     * count, left in {@link #points} to upload once the program is bound; 0 for none.
     */
    private int clip(Layer l, Target t, ShaderInstance shader) {
        if (l.clip == null) {
            shader.safeGetUniform("ClipKind").set(0f);
            return 0;
        }
        Window window = Minecraft.getInstance().getWindow();
        float s = (float) window.getGuiScale();
        // Texture coordinates to window pixels (y up) to GUI pixels (y down), then back through the transforms.
        Affine tex = new Affine(t.fbo.width / s, 0, 0, -t.fbo.height / s, l.x / s, (window.getHeight() - l.y) / s);
        Affine m = l.toLocal.then(tex);
        shader.safeGetUniform("LocalX").set(m.a(), m.c(), m.e());
        shader.safeGetUniform("LocalY").set(m.b(), m.d(), m.f());
        if (l.clip instanceof ClipShape.Box box) {
            RoundRect r = box.shape();
            RoundRect.Radii q = r.radii();
            shader.safeGetUniform("ClipKind").set(1f);
            shader.safeGetUniform("Box").set(r.x(), r.y(), r.width(), r.height());
            shader.safeGetUniform("RadiiTop").set(q.topLeftX(), q.topLeftY(), q.topRightX(), q.topRightY());
            shader.safeGetUniform("RadiiBottom").set(q.bottomRightX(), q.bottomRightY(), q.bottomLeftX(), q.bottomLeftY());
            return 0;
        }
        ClipShape.Polygon p = (ClipShape.Polygon) l.clip;
        int n = p.points().length / 2;
        shader.safeGetUniform("ClipKind").set(p.evenOdd() ? 3f : 2f);
        shader.safeGetUniform("PointCount").set((float) n);
        points.clear();
        points.put(p.points()).flip();
        return n;
    }

    /**
     * The {@code n}th open layer from the innermost one ({@code 0}), or {@code null} past the
     * outermost, where the target is the one bound before any layer.
     */
    Layer open(int n) {
        return n < depth ? layers.get(depth - 1 - n) : null;
    }

    /** The framebuffer bound before the outermost open layer, or now when none is open. */
    int baseFbo() {
        return depth > 0 ? layers.get(0).parentFbo : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    static void release(Target t) {
        t.busy = false;
        t.lastUsed = System.nanoTime();
    }

    /** Frees the targets no layer used for {@value #IDLE_SECONDS} seconds. */
    static void evictIdle() {
        long now = System.nanoTime();
        for (int i = POOL.size() - 1; i >= 0; i--) {
            Target t = POOL.get(i);
            if (!t.busy && now - t.lastUsed > IDLE_SECONDS * 1_000_000_000L) {
                t.fbo.destroyBuffers();
                POOL.remove(i);
            }
        }
    }

    /**
     * The smallest free target at least {@code width} by {@code height} with or without depth, or a
     * new one rounded up to {@link #GRANULE}; {@link #release(Target)} hands it back.
     */
    static Target acquire(int width, int height, boolean depth) {
        Target best = null;
        for (Target t : POOL) {
            if (!t.busy && t.depth == depth && t.fbo.width >= width && t.fbo.height >= height
                    && (best == null || (long) t.fbo.width * t.fbo.height < (long) best.fbo.width * best.fbo.height)) {
                best = t;
            }
        }
        if (best == null) {
            best = new Target(roundUp(width), roundUp(height), depth);
            POOL.add(best);
        }
        best.busy = true;
        return best;
    }

    private static int roundUp(int v) {
        return (v + GRANULE - 1) / GRANULE * GRANULE;
    }
}
