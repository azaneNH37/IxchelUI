package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.Filter;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.RoundRect;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * Runs {@code filter} and {@code backdrop-filter} functions over an image in a pooled target, pass by
 * pass between scratch targets, and draws a filtered backdrop back. An image occupies the region
 * {@code (0, 0)}-{@code (w, h)} of its target in pixels, and passes clamp their samples to it.
 * Consecutive color functions are one pass, as they compose into one affine color map; {@code blur}
 * is two (one per axis); {@code drop-shadow} blurs the coverage, draws it moved and tinted and the
 * image over it; {@code -ix-paint()} is its layer effect's program over the image. Passes leave a
 * scratch target bound, the scissor and blending off; the caller binds its target again. Render
 * thread only.
 */
final class FilterPasses {
    /** The most a backdrop is scaled down by before a blur. */
    private static final int MAX_DOWNSCALE = 8;
    private static final float[] IDENTITY = {
            1, 0, 0, 0, 0,
            0, 1, 0, 0, 0,
            0, 0, 1, 0, 0,
            0, 0, 0, 1, 0};

    private final FilterShaders shaders;
    private final EffectPrograms effects;
    /** The color map being built: rows r, g, b, a of {@code [r g b a 1]} coefficients. */
    private final float[] map = new float[20];
    private final float[] step = new float[20];
    private final float[] product = new float[20];
    private final Matrix4f matrix = new Matrix4f();

    FilterPasses(FilterShaders shaders, EffectPrograms effects) {
        this.shaders = shaders;
        this.effects = effects;
    }

    FilterShaders shaders() {
        return shaders;
    }

    EffectPrograms effects() {
        return effects;
    }

    /**
     * Runs {@code filters} over the image in {@code src}; returns the target holding the result,
     * {@code src} itself when no pass ran, otherwise a scratch target the caller releases.
     * {@code scale} is pixels per GUI pixel, for blur radii and shadow offsets. {@code layer} is the
     * layer the image is, whose effects the {@code -ix-paint()} filters run; {@code null} for a
     * backdrop, which has none.
     */
    OffscreenLayers.Target run(OffscreenLayers.Target src, int w, int h, List<Filter> filters, float scale, OffscreenLayers.Layer layer) {
        RenderSystem.disableScissor();
        OffscreenLayers.Target cur = src;
        int i = 0;
        int paint = 0;
        while (i < filters.size()) {
            Filter f = filters.get(i);
            OffscreenLayers.Target out;
            if (f.kind().colorMatrix()) {
                System.arraycopy(IDENTITY, 0, map, 0, 20);
                for (; i < filters.size() && filters.get(i).kind().colorMatrix(); i++) {
                    then(filters.get(i));
                }
                out = colorMap(cur, w, h);
            } else if (f.kind() == Filter.Kind.BLUR) {
                i++;
                if (f.sigma() <= 0) {
                    continue;
                }
                out = blur(cur, w, h, f.sigma() * scale);
            } else if (f.kind() == Filter.Kind.PAINT) {
                i++;
                out = paint(cur, w, h, layer.paints.get(paint++), layer, scale);
                if (out == null) {
                    continue;
                }
            } else {
                i++;
                out = dropShadow(cur, w, h, f, scale);
            }
            if (cur != src) {
                OffscreenLayers.release(cur);
            }
            cur = out;
        }
        return cur;
    }

    /**
     * Copies the region of framebuffer {@code fbo} at {@code (x, y)}, {@code w} by {@code h} pixels (GL
     * orientation), and runs {@code filters} over it; scaled down first when they blur, by about a
     * quarter of the widest blur's radius. Returns the scratch target holding the result, which
     * the caller releases, its region in {@code size}.
     */
    OffscreenLayers.Target backdrop(int fbo, int x, int y, int w, int h, List<Filter> filters, float scale, int[] size) {
        float sigma = 0;
        for (Filter f : filters) {
            if (!f.kind().colorMatrix()) {
                sigma = Math.max(sigma, f.sigma() * scale);
            }
        }
        int d = Math.max(1, Math.min(MAX_DOWNSCALE, (int) (sigma / 4)));
        int sw = Math.max(1, (w + d - 1) / d);
        int sh = Math.max(1, (h + d - 1) / d);
        RenderSystem.disableScissor();
        OffscreenLayers.Target snapshot = OffscreenLayers.acquire(sw, sh, false);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.fbo.frameBufferId);
        GlStateManager._glBlitFrameBuffer(x, y, x + w, y + h, 0, 0, sw, sh, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        OffscreenLayers.Target out = run(snapshot, sw, sh, filters, scale / d, null);
        if (out != snapshot) {
            OffscreenLayers.release(snapshot);
        }
        size[0] = sw;
        size[1] = sh;
        return out;
    }

    /**
     * Draws the backdrop in {@code t} (its region {@code sw} by {@code sh}) inside {@code shape},
     * through {@code pose} at depth {@code z}, onto the bound target, where the backdrop's rectangle
     * is {@code (x, y)}, {@code w} by {@code h} pixels. {@code opaque} when the backdrop came from a
     * target whose alpha is not coverage.
     */
    void drawBackdrop(OffscreenLayers.Target t, int sw, int sh, int x, int y, int w, int h, RoundRect shape,
                      Matrix4f pose, float z, boolean opaque) {
        ShaderInstance s = shaders.backdrop;
        RoundRect.Radii r = shape.radii();
        s.safeGetUniform("Region").set((float) x, (float) y, (float) w, (float) h);
        s.safeGetUniform("UvScale").set(sw / (float) t.fbo.width, sh / (float) t.fbo.height);
        s.safeGetUniform("Size").set(shape.width(), shape.height());
        s.safeGetUniform("RadiiTop").set(r.topLeftX(), r.topLeftY(), r.topRightX(), r.topRightY());
        s.safeGetUniform("RadiiBottom").set(r.bottomRightX(), r.bottomRightY(), r.bottomLeftX(), r.bottomLeftY());
        s.safeGetUniform("Opaque").set(opaque ? 1f : 0f);
        FilterShaders.apply(s, t.fbo.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        float x0 = shape.x(), y0 = shape.y(), x1 = x0 + shape.width(), y1 = y0 + shape.height();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(pose, x0, y0, z).setUv(0, 0);
        buffer.addVertex(pose, x0, y1, z).setUv(0, shape.height());
        buffer.addVertex(pose, x1, y1, z).setUv(shape.width(), shape.height());
        buffer.addVertex(pose, x1, y0, z).setUv(shape.width(), 0);
        FilterShaders.draw(s, buffer.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    /** {@link #map} followed by {@code f}: {@code map = step(f) * map}, as 4x5 affine maps. */
    private void then(Filter f) {
        System.arraycopy(IDENTITY, 0, step, 0, 20);
        float a = f.amount();
        switch (f.kind()) {
            case GRAYSCALE -> {
                float k = 1 - a;
                rgb(0.2126f + 0.7874f * k, 0.7152f - 0.7152f * k, 0.0722f - 0.0722f * k,
                        0.2126f - 0.2126f * k, 0.7152f + 0.2848f * k, 0.0722f - 0.0722f * k,
                        0.2126f - 0.2126f * k, 0.7152f - 0.7152f * k, 0.0722f + 0.9278f * k);
            }
            case SEPIA -> {
                float k = 1 - a;
                rgb(0.393f + 0.607f * k, 0.769f - 0.769f * k, 0.189f - 0.189f * k,
                        0.349f - 0.349f * k, 0.686f + 0.314f * k, 0.168f - 0.168f * k,
                        0.272f - 0.272f * k, 0.534f - 0.534f * k, 0.131f + 0.869f * k);
            }
            case SATURATE -> rgb(0.213f + 0.787f * a, 0.715f - 0.715f * a, 0.072f - 0.072f * a,
                    0.213f - 0.213f * a, 0.715f + 0.285f * a, 0.072f - 0.072f * a,
                    0.213f - 0.213f * a, 0.715f - 0.715f * a, 0.072f + 0.928f * a);
            case HUE_ROTATE -> {
                double rad = Math.toRadians(a);
                float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
                rgb(0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f,
                        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f,
                        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f);
            }
            case INVERT -> linear(1 - 2 * a, a);
            case BRIGHTNESS -> linear(a, 0);
            case CONTRAST -> linear(a, 0.5f - 0.5f * a);
            case OPACITY -> step[18] = a;
            default -> throw new IllegalArgumentException(f.kind() + " is not a color function");
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float v = col == 4 ? step[row * 5 + 4] : 0;
                for (int k = 0; k < 4; k++) {
                    v += step[row * 5 + k] * map[k * 5 + col];
                }
                product[row * 5 + col] = v;
            }
        }
        System.arraycopy(product, 0, map, 0, 20);
    }

    /** Sets the color rows of {@link #step} to the 3x3 matrix given row by row. */
    private void rgb(float... m) {
        for (int row = 0; row < 3; row++) {
            System.arraycopy(m, row * 3, step, row * 5, 3);
        }
    }

    /** Sets each color channel of {@link #step} to {@code slope * c + intercept}. */
    private void linear(float slope, float intercept) {
        for (int row = 0; row < 3; row++) {
            step[row * 5 + row] = slope;
            step[row * 5 + 4] = intercept;
        }
    }

    private OffscreenLayers.Target colorMap(OffscreenLayers.Target src, int w, int h) {
        // JOML takes column by column.
        matrix.set(map[0], map[5], map[10], map[15],
                map[1], map[6], map[11], map[16],
                map[2], map[7], map[12], map[17],
                map[3], map[8], map[13], map[18]);
        ShaderInstance s = shaders.matrix;
        s.safeGetUniform("ColorMatrix").set(matrix);
        s.safeGetUniform("ColorOffset").set(map[4], map[9], map[14], map[19]);
        OffscreenLayers.Target out = OffscreenLayers.acquire(w, h, false);
        pass(s, src, w, h, out, false);
        return out;
    }

    /** A Gaussian blur of {@code sigma} pixels, one pass per axis. */
    private OffscreenLayers.Target blur(OffscreenLayers.Target src, int w, int h, float sigma) {
        ShaderInstance s = shaders.blur;
        s.safeGetUniform("Sigma").set(sigma);
        OffscreenLayers.Target across = OffscreenLayers.acquire(w, h, false);
        s.safeGetUniform("Step").set(1f / src.fbo.width, 0f);
        pass(s, src, w, h, across, false);
        OffscreenLayers.Target out = OffscreenLayers.acquire(w, h, false);
        s.safeGetUniform("Step").set(0f, 1f / across.fbo.height);
        pass(s, across, w, h, out, false);
        OffscreenLayers.release(across);
        return out;
    }

    private OffscreenLayers.Target dropShadow(OffscreenLayers.Target src, int w, int h, Filter f, float scale) {
        OffscreenLayers.Target blurred = f.sigma() > 0 ? blur(src, w, h, f.sigma() * scale) : src;
        int c = f.color();
        float alpha = (c >>> 24) / 255f;
        ShaderInstance s = shaders.shadow;
        s.safeGetUniform("Color").set((c >> 16 & 0xFF) / 255f * alpha, (c >> 8 & 0xFF) / 255f * alpha,
                (c & 0xFF) / 255f * alpha, alpha);
        // GUI y grows down, texture y up.
        s.safeGetUniform("Offset").set(f.x() * scale / blurred.fbo.width, -f.y() * scale / blurred.fbo.height);
        OffscreenLayers.Target out = OffscreenLayers.acquire(w, h, false);
        pass(s, blurred, w, h, out, false);
        if (blurred != src) {
            OffscreenLayers.release(blurred);
        }
        pass(shaders.copy, src, w, h, out, true);
        return out;
    }

    /** {@code p}'s program over the layer; {@code null} when it did not compile, which leaves the image as it is. */
    private OffscreenLayers.Target paint(OffscreenLayers.Target src, int w, int h, DrawCommand.LayerPaint p,
                                         OffscreenLayers.Layer layer, float scale) {
        ShaderInstance s = effects.program(p.effect());
        if (s == null || layer.toLocal == null) {
            return null;
        }
        // Target pixels to window pixels to GUI pixels (y down), back through the element's transforms,
        // then from the frame's corner in physical pixels.
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();
        Affine gui = new Affine(1 / scale, 0, 0, -1 / scale, layer.x / scale, (windowHeight - layer.y) / scale);
        Affine local = new Affine(scale, 0, 0, scale, -scale * layer.frame.x(), -scale * layer.frame.y())
                .then(layer.toLocal).then(gui);
        effects.local(s, local);
        effects.bind(s, p.effect(), p.values(), layer.frame, scale, layer.time, layer.mouseX, layer.mouseY);
        OffscreenLayers.Target out = OffscreenLayers.acquire(w, h, false);
        pass(s, src, w, h, out, false);
        return out;
    }

    /**
     * Draws the region of {@code src} through {@code s} onto the same region of {@code dst}, replacing
     * it, or with {@code over} premultiplied over it.
     */
    private void pass(ShaderInstance s, OffscreenLayers.Target src, int w, int h, OffscreenLayers.Target dst, boolean over) {
        float u = w / (float) src.fbo.width;
        float v = h / (float) src.fbo.height;
        s.safeGetUniform("Limit").set(0.5f / src.fbo.width, 0.5f / src.fbo.height, u - 0.5f / src.fbo.width, v - 0.5f / src.fbo.height);
        dst.fbo.bindWrite(false);
        RenderSystem.viewport(0, 0, w, h);
        FilterShaders.apply(s, src.fbo.getColorTextureId());
        if (over) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.disableBlend();
        }
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1, -1, 0).setUv(0, 0);
        buffer.addVertex(1, -1, 0).setUv(u, 0);
        buffer.addVertex(1, 1, 0).setUv(u, v);
        buffer.addVertex(-1, 1, 0).setUv(0, v);
        FilterShaders.draw(s, buffer.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
