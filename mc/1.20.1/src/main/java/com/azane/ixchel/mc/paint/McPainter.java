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
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
 * <p>Native drawings go through vanilla and write depth above the level they draw at. Each command
 * draws at the highest depth reached by the earlier commands it overlaps ({@link Layers}), so it
 * paints on top of them, and the depth grows only with how much stacks at one spot.
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
    private final Layers layers = new Layers();
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
        buffer = Tesselator.getInstance().getBuilder();
        layers.clear();
        list.replay(this);
        flush();
        RenderSystem.disableBlend();
        graphics = null;
        pose = null;
        buffer = null;
    }

    /**
     * The depth, in the pose {@link #paint} drew in, above everything it drew; an overlay such as a
     * tooltip raised by this much paints on top.
     */
    public float topZ() {
        return layers.top();
    }

    @Override
    public Void visitRect(DrawCommand.Rect c) {
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
        float z = layers.level(x0, y0, x1, y1);
        layers.add(x0, y0, x1, y1, z);
        buffer.vertex(pose, x0, y0, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x0, y1, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y1, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y0, z).color(r, g, b, a).endVertex();
        return null;
    }

    @Override
    public Void visitImage(DrawCommand.Image c) {
        String src = c.src();
        float u0 = c.u0();
        float v0 = c.v0();
        float u1 = c.u1();
        float v1 = c.v1();
        if (src.startsWith(Sprites.PREFIX)) {
            ResourceLocation id = textures.computeIfAbsent(src, key -> {
                ResourceLocation parsed = Sprites.id(key);
                return parsed == null ? MissingTextureAtlasSprite.getLocation() : parsed;
            });
            TextureAtlasSprite s = Sprites.sprite(id);
            float du = s.getU1() - s.getU0();
            float dv = s.getV1() - s.getV0();
            u0 = s.getU0() + u0 * du;
            u1 = s.getU0() + u1 * du;
            v0 = s.getV0() + v0 * dv;
            v1 = s.getV0() + v1 * dv;
            begin(Batch.TEXTURE, TextureAtlas.LOCATION_BLOCKS);
        } else {
            begin(Batch.TEXTURE, textures.computeIfAbsent(src, ResourceLocation::new));
        }
        int argb = c.tint();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = c.x();
        float y0 = c.y();
        float x1 = x0 + c.width();
        float y1 = y0 + c.height();
        float z = layers.level(x0, y0, x1, y1);
        layers.add(x0, y0, x1, y1, z);
        buffer.vertex(pose, x0, y0, z).color(r, g, b, a).uv(u0, v0).endVertex();
        buffer.vertex(pose, x0, y1, z).color(r, g, b, a).uv(u0, v1).endVertex();
        buffer.vertex(pose, x1, y1, z).color(r, g, b, a).uv(u1, v1).endVertex();
        buffer.vertex(pose, x1, y0, z).color(r, g, b, a).uv(u1, v0).endVertex();
        return null;
    }

    @Override
    public Void visitText(DrawCommand.Text c) {
        // Font draws alpha below 4 as opaque; that text is invisible, as fading text must end.
        if ((c.color() & 0xFC000000) == 0) {
            return null;
        }
        float scale = c.size() / VANILLA_SIZE;
        float z = 0;
        // Measuring is only needed once something is raised.
        if (!layers.isEmpty()) {
            float x1 = c.x() + font.width(c.text()) * scale;
            float y1 = c.y() + c.size();
            z = layers.level(c.x(), c.y(), x1, y1);
            layers.add(c.x(), c.y(), x1, y1, z);
        }
        begin(Batch.TEXT, null);
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
        float x1 = c.x() + c.width();
        float y1 = c.y() + c.height();
        float z = layers.level(c.x(), c.y(), x1, y1);
        layers.add(c.x(), c.y(), x1, y1, z + p.paint(graphics, c, z, pointerX, pointerY));
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
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            case TEXTURE -> {
                RenderSystem.setShaderTexture(0, nextTexture);
                RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            }
            case TEXT, NONE -> {
            }
        }
    }

    private void flush() {
        switch (batch) {
            case COLOR, TEXTURE -> BufferUploader.drawWithShader(buffer.end());
            case TEXT -> graphics.flush();
            case NONE -> {
            }
        }
        batch = Batch.NONE;
        texture = null;
    }
}
