package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.runtime.UiDocument;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * What a take shows over the game's frame, drawn on the copy that becomes the video and never on the
 * game's own: the pointer, an arrow or, over a text input, an I-beam; and a ripple where each press
 * lands (left blue, right orange, middle green). The game draws no pointer of its own, and the
 * system's is not in its frame. Sizes are in GUI pixels, so the pointer keeps its size against the
 * interface at any GUI scale. Client thread only.
 */
final class RecordingOverlay implements AutoCloseable {
    /** The arrow's height in GUI pixels: a little over a line of vanilla text. */
    private static final float POINTER_HEIGHT = 12;
    private static final long RIPPLE_NANOS = 420_000_000L;
    /** The arrow's outline in its own units (its height is {@value #ARROW_HEIGHT}), tip at the origin. */
    private static final float[] ARROW = {0, 0, 0, 15.5f, 3.6f, 12.1f, 6.1f, 17.6f, 8.4f, 16.6f, 6, 11.3f, 10.8f, 11.3f};
    private static final float ARROW_HEIGHT = 17.6f;
    private static final float ARROW_WIDTH = 10.8f;
    /** The I-beam's boxes, center x, center y, half width, half height each, about its center. */
    private static final float[] BEAM = {0, 0, 0.7f, 7.6f, 0, -7.6f, 2.6f, 0.7f, 0, 7.6f, 2.6f, 0.7f};
    private static final float BEAM_HEIGHT = 16.6f;
    private static final float BEAM_WIDTH = 5.2f;
    private static final int RING_PX = 64;

    private record Ripple(float x, float y, int button, long at) {
    }

    private final List<Ripple> ripples = new ArrayList<>();
    private Sprite arrow;
    private Sprite beam;
    private DynamicTexture ring;
    private DynamicTexture dot;
    /** The GUI scale the pointer sprites were drawn for. */
    private double spriteScale = Double.NaN;

    /** A press of {@code button} at {@code (x, y)} at {@code now}, which ripples from there. */
    void pressed(double x, double y, int button, long now) {
        ripples.add(new Ripple((float) x, (float) y, button, now));
    }

    /** Forgets the ripples, for a new take. */
    void clear() {
        ripples.clear();
    }

    /**
     * Draws over the bound target, a copy of the window's frame: the ripples, then the pointer at
     * {@code (x, y)}, an I-beam over a text input of {@code document} (may be {@code null}).
     */
    void draw(float guiWidth, float guiHeight, double guiScale, long now, float x, float y, UiDocument document) {
        ensureSprites(guiScale);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, guiWidth, guiHeight, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        try {
            drawRipples(now);
            Sprite pointer = overTextInput(document, x, y) ? beam : arrow;
            RenderSystem.setShaderColor(1, 1, 1, 1);
            quad(pointer.texture.getId(), x - pointer.hotX, y - pointer.hotY, pointer.width, pointer.height);
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void drawRipples(long now) {
        ripples.removeIf(r -> now - r.at >= RIPPLE_NANOS);
        for (Ripple r : ripples) {
            float t = (now - r.at) / (float) RIPPLE_NANOS;
            int rgb = switch (r.button) {
                case 1 -> 0xFFA94D;
                case 2 -> 0x8BE38B;
                default -> 0x62B0FF;
            };
            float red = (rgb >> 16 & 0xFF) / 255f;
            float green = (rgb >> 8 & 0xFF) / 255f;
            float blue = (rgb & 0xFF) / 255f;
            if (t < 0.5f) {
                float radius = 2.5f;
                RenderSystem.setShaderColor(red, green, blue, 0.6f * (1 - t / 0.5f));
                quad(dot.getId(), r.x - radius, r.y - radius, 2 * radius, 2 * radius);
            }
            float grow = 1 - (1 - t) * (1 - t) * (1 - t);
            float radius = 3 + 8 * grow;
            RenderSystem.setShaderColor(red, green, blue, (float) Math.pow(1 - t, 1.3));
            quad(ring.getId(), r.x - radius, r.y - radius, 2 * radius, 2 * radius);
        }
    }

    private static boolean overTextInput(UiDocument document, float x, float y) {
        if (document == null) {
            return false;
        }
        for (Element e = document.events().hitTest(x, y); e != null; e = e.parent()) {
            if (e.textField() != null) {
                return true;
            }
        }
        return false;
    }

    /** A textured quad over {@code (x, y, width, height)} in GUI pixels, in the current shader color. */
    private static void quad(int texture, float x, float y, float width, float height) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(x, y, 0).uv(0, 0).endVertex();
        b.vertex(x, y + height, 0).uv(0, 1).endVertex();
        b.vertex(x + width, y + height, 0).uv(1, 1).endVertex();
        b.vertex(x + width, y, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** Draws the pointer images at the window's resolution for {@code guiScale}; the ripple images once. */
    private void ensureSprites(double guiScale) {
        if (ring == null) {
            ring = upload(ringImage(RING_PX, 0.16f));
            dot = upload(ringImage(RING_PX, 1));
        }
        if (guiScale == spriteScale) {
            return;
        }
        spriteScale = guiScale;
        closeSprites();
        float unit = (float) (POINTER_HEIGHT * guiScale / ARROW_HEIGHT);
        arrow = Sprite.of(unit, ARROW_WIDTH, ARROW_HEIGHT, false, guiScale);
        beam = Sprite.of(unit, BEAM_WIDTH, BEAM_HEIGHT, true, guiScale);
    }

    private static DynamicTexture upload(NativeImage image) {
        DynamicTexture t = new DynamicTexture(image);
        t.setFilter(true, false);
        return t;
    }

    /** A white disc of {@code size} pixels, as a ring {@code stroke} of its radius thick (1: the whole disc), anti-aliased. */
    private static NativeImage ringImage(int size, float stroke) {
        NativeImage image = new NativeImage(size, size, true);
        float outer = size / 2f - 1;
        float inner = outer * (1 - stroke);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float d = (float) Math.hypot(px + 0.5f - size / 2f, py + 0.5f - size / 2f);
                float a = clamp01(outer - d + 0.5f) * (stroke >= 1 ? 1 : clamp01(d - inner + 0.5f));
                image.setPixelRGBA(px, py, Math.round(a * 255) << 24 | 0xFFFFFF);
            }
        }
        return image;
    }

    /** A pointer image at window resolution, with its hot spot. */
    private record Sprite(DynamicTexture texture, float width, float height, float hotX, float hotY) {
        /**
         * The arrow (white, dark outline) or the I-beam (dark, white outline), {@code unit} pixels to a
         * shape unit, with a soft shadow; sized in GUI pixels for {@code guiScale}.
         */
        static Sprite of(float unit, float shapeWidth, float shapeHeight, boolean beam, double guiScale) {
            float outline = beam ? 0.8f : 1.1f;
            float pad = outline + 2.2f;
            int w = (int) Math.ceil((shapeWidth + 2 * pad) * unit);
            int h = (int) Math.ceil((shapeHeight + 2 * pad) * unit);
            // The hot spot, in pixels: the arrow's tip, the I-beam's center.
            float hotX = beam ? pad * unit + shapeWidth * unit / 2 : pad * unit;
            float hotY = beam ? pad * unit + shapeHeight * unit / 2 : pad * unit;
            int fill = beam ? 0x20 : 0xFF;
            int edge = beam ? 0xFF : 0x18;
            NativeImage image = new NativeImage(w, h, true);
            float pixel = 1 / unit;
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    float ux = (px + 0.5f - hotX) / unit;
                    float uy = (py + 0.5f - hotY) / unit;
                    float d = beam ? beamDistance(ux, uy) : arrowDistance(ux, uy);
                    float shape = clamp01((outline - d) / pixel + 0.5f);
                    float inside = clamp01(-d / pixel + 0.5f);
                    float shadow = 0.35f * smoothstep(outline + 1.5f, outline - 0.5f,
                            beam ? beamDistance(ux - 0.4f, uy - 0.8f) : arrowDistance(ux - 0.4f, uy - 0.8f));
                    float alpha = shape + shadow * (1 - shape);
                    int grey = alpha <= 0 ? 0 : Math.round((fill * inside + edge * (1 - inside)) * shape / alpha);
                    image.setPixelRGBA(px, py, Math.round(alpha * 255) << 24 | grey << 16 | grey << 8 | grey);
                }
            }
            DynamicTexture t = upload(image);
            float gui = (float) guiScale;
            return new Sprite(t, w / gui, h / gui, hotX / gui, hotY / gui);
        }
    }

    /** Signed distance to the arrow's outline in arrow units, negative inside. */
    private static float arrowDistance(float x, float y) {
        float best = Float.MAX_VALUE;
        boolean inside = false;
        int n = ARROW.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float ax = ARROW[2 * j];
            float ay = ARROW[2 * j + 1];
            float bx = ARROW[2 * i];
            float by = ARROW[2 * i + 1];
            if (by > y != ay > y && x < (ax - bx) * (y - by) / (ay - by) + bx) {
                inside = !inside;
            }
            float ex = bx - ax;
            float ey = by - ay;
            float k = clamp01(((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey));
            float dx = x - ax - ex * k;
            float dy = y - ay - ey * k;
            best = Math.min(best, dx * dx + dy * dy);
        }
        float d = (float) Math.sqrt(best);
        return inside ? -d : d;
    }

    /** Signed distance to the I-beam, the union of its boxes. */
    private static float beamDistance(float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < BEAM.length; i += 4) {
            float dx = Math.abs(x - BEAM[i]) - BEAM[i + 2];
            float dy = Math.abs(y - BEAM[i + 1]) - BEAM[i + 3];
            float outside = (float) Math.hypot(Math.max(dx, 0), Math.max(dy, 0));
            best = Math.min(best, outside + Math.min(Math.max(dx, dy), 0));
        }
        return best;
    }

    private static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float smoothstep(float edge0, float edge1, float v) {
        float t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }

    private void closeSprites() {
        if (arrow != null) {
            arrow.texture.close();
            beam.texture.close();
            arrow = null;
            beam = null;
        }
    }

    @Override
    public void close() {
        closeSprites();
        if (ring != null) {
            ring.close();
            dot.close();
            ring = null;
            dot = null;
        }
        spriteScale = Double.NaN;
    }
}
