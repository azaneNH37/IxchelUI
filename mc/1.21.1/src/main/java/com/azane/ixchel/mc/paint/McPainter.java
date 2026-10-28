package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Replays a {@link DisplayList} into a {@link GuiGraphics}. Rects and images are written as raw
 * quads and submitted only when the render state (shader, texture, clip) changes or a text run
 * starts; text goes through {@link Font#drawInBatch} and is flushed before the next non-text command,
 * so paint order is kept. Native elements are drawn by their {@link NativePainter}.
 *
 * <p>Native drawings go through vanilla and write depth above the current level. Commands after one
 * are raised by what it reported, so they still paint on top; consecutive native drawings share one
 * level, so a grid of items costs one raise.
 */
public final class McPainter implements DrawCommand.Visitor<Void> {
    private static final float VANILLA_SIZE = 9f;

    private enum Batch { NONE, COLOR, TEXTURE, TEXT }

    private final Font font;
    private final Map<String, NativePainter> natives;
    private final Map<String, ResourceLocation> textures = new HashMap<>();
    private final Matrix4f textMatrix = new Matrix4f();
    private GuiGraphics graphics;
    private Matrix4f pose;
    private BufferBuilder buffer;
    private Batch batch = Batch.NONE;
    private ResourceLocation texture;
    private float z;
    /** Depth the native drawings since the last raise wrote above {@link #z}. */
    private float nativeDepth;
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;

    /** @param natives the painter of each native element kind ({@code DrawCommand.Custom#kind}) */
    public McPainter(Font font, Map<String, NativePainter> natives) {
        this.font = font;
        this.natives = Map.copyOf(natives);
    }

    /**
     * Draws {@code list} in the current pose of {@code g}, with the pointer native elements may react
     * to (NaN in a HUD); leaves blending disabled, like vanilla blits.
     */
    public void paint(GuiGraphics g, DisplayList list, float pointerX, float pointerY) {
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        graphics = g;
        pose = g.pose().last().pose();
        z = 0;
        nativeDepth = 0;
        list.replay(this);
        flush();
        RenderSystem.disableBlend();
        graphics = null;
        pose = null;
    }

    /**
     * The depth, in the pose {@link #paint} drew in, above everything it drew; an overlay such as a
     * tooltip raised by this much paints on top.
     */
    public float topZ() {
        return z + nativeDepth;
    }

    @Override
    public Void visitRect(DrawCommand.Rect c) {
        raiseAboveNatives();
        begin(Batch.COLOR, null);
        int argb = c.color();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = c.x();
        float y0 = c.y();
        float x1 = x0 + c.width();
        float y1 = y0 + c.height();
        buffer.addVertex(pose, x0, y0, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x0, y1, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y0, z).setColor(r, g, b, a);
        return null;
    }

    @Override
    public Void visitImage(DrawCommand.Image c) {
        raiseAboveNatives();
        begin(Batch.TEXTURE, textures.computeIfAbsent(c.src(), ResourceLocation::parse));
        int argb = c.tint();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = c.x();
        float y0 = c.y();
        float x1 = x0 + c.width();
        float y1 = y0 + c.height();
        buffer.addVertex(pose, x0, y0, z).setColor(r, g, b, a).setUv(c.u0(), c.v0());
        buffer.addVertex(pose, x0, y1, z).setColor(r, g, b, a).setUv(c.u0(), c.v1());
        buffer.addVertex(pose, x1, y1, z).setColor(r, g, b, a).setUv(c.u1(), c.v1());
        buffer.addVertex(pose, x1, y0, z).setColor(r, g, b, a).setUv(c.u1(), c.v0());
        return null;
    }

    @Override
    public Void visitText(DrawCommand.Text c) {
        // Font draws alpha below 4 as opaque; that text is invisible, as fading text must end.
        if ((c.color() & 0xFC000000) == 0) {
            return null;
        }
        raiseAboveNatives();
        begin(Batch.TEXT, null);
        float scale = c.size() / VANILLA_SIZE;
        textMatrix.set(pose).translate(c.x(), c.y(), z).scale(scale, scale, 1);
        font.drawInBatch(c.text(), 0, 0, c.color(), false, textMatrix, graphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return null;
    }

    /** @throws IllegalStateException for a kind without a painter */
    @Override
    public Void visitCustom(DrawCommand.Custom c) {
        NativePainter p = natives.get(c.kind());
        if (p == null) {
            throw new IllegalStateException("no painter for native element <" + c.kind() + ">");
        }
        flush();
        nativeDepth = Math.max(nativeDepth, p.paint(graphics, c, z, pointerX, pointerY));
        return null;
    }

    @Override
    public Void visitPushClip(DrawCommand.PushClip c) {
        flush();
        graphics.enableScissor((int) Math.floor(c.x()), (int) Math.floor(c.y()),
                (int) Math.ceil(c.x() + c.width()), (int) Math.ceil(c.y() + c.height()));
        return null;
    }

    @Override
    public Void visitPopClip(DrawCommand.PopClip c) {
        flush();
        graphics.disableScissor();
        return null;
    }

    private void raiseAboveNatives() {
        z += nativeDepth;
        nativeDepth = 0;
    }

    private void begin(Batch next, ResourceLocation nextTexture) {
        if (batch == next && texture == nextTexture) {
            return;
        }
        flush();
        batch = next;
        texture = nextTexture;
        if (next == Batch.COLOR || next == Batch.TEXTURE) {
            // A text or item flush runs a render type's cleanup, which turns blending off again.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        switch (next) {
            case COLOR -> {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            case TEXTURE -> {
                RenderSystem.setShaderTexture(0, nextTexture);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            case TEXT, NONE -> {
            }
        }
    }

    private void flush() {
        switch (batch) {
            case COLOR, TEXTURE -> {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
                buffer = null;
            }
            case TEXT -> graphics.flush();
            case NONE -> {
            }
        }
        batch = Batch.NONE;
        texture = null;
    }
}
