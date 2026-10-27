package com.azane.ixchel.mc;

import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Replays a {@link DisplayList} into a {@link GuiGraphics}. Rects and images are written as raw
 * quads and submitted only when the render state (shader, texture, clip) changes or a text run
 * starts; text goes through {@link Font#drawInBatch} and is flushed before the next non-text command,
 * so paint order is kept.
 *
 * <p>Items render through vanilla and write depth up to {@code z + 158}. Commands after an item are
 * raised by {@link #ITEM_DEPTH} so they still paint on top; consecutive items share one level, so a
 * grid of items costs one raise.
 */
public final class McPainter implements DrawCommand.Visitor<Void> {
    /** Tag and payload name of the item element. */
    public static final String ITEM = "item";
    private static final float VANILLA_SIZE = 9f;
    private static final float ITEM_DEPTH = 160f;
    private static final float ITEM_SIZE = 16f;

    private enum Batch { NONE, COLOR, TEXTURE, TEXT }

    private final Font font;
    private final Map<String, ResourceLocation> textures = new HashMap<>();
    private final Matrix4f textMatrix = new Matrix4f();
    private GuiGraphics graphics;
    private Matrix4f pose;
    private BufferBuilder buffer;
    private Batch batch = Batch.NONE;
    private ResourceLocation texture;
    private float z;
    private boolean afterItem;

    public McPainter(Font font) {
        this.font = font;
    }

    /** Draws {@code list} in the current pose of {@code g}; leaves blending disabled, like vanilla blits. */
    public void paint(GuiGraphics g, DisplayList list) {
        graphics = g;
        pose = g.pose().last().pose();
        buffer = Tesselator.getInstance().getBuilder();
        z = 0;
        afterItem = false;
        list.replay(this);
        flush();
        RenderSystem.disableBlend();
        graphics = null;
        pose = null;
        buffer = null;
    }

    @Override
    public Void visitRect(DrawCommand.Rect c) {
        raiseAboveItems();
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
        buffer.vertex(pose, x0, y0, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x0, y1, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y1, z).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y0, z).color(r, g, b, a).endVertex();
        return null;
    }

    @Override
    public Void visitImage(DrawCommand.Image c) {
        raiseAboveItems();
        begin(Batch.TEXTURE, textures.computeIfAbsent(c.src(), ResourceLocation::new));
        int argb = c.tint();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = c.x();
        float y0 = c.y();
        float x1 = x0 + c.width();
        float y1 = y0 + c.height();
        buffer.vertex(pose, x0, y0, z).color(r, g, b, a).uv(0, 0).endVertex();
        buffer.vertex(pose, x0, y1, z).color(r, g, b, a).uv(0, 1).endVertex();
        buffer.vertex(pose, x1, y1, z).color(r, g, b, a).uv(1, 1).endVertex();
        buffer.vertex(pose, x1, y0, z).color(r, g, b, a).uv(1, 0).endVertex();
        return null;
    }

    @Override
    public Void visitText(DrawCommand.Text c) {
        raiseAboveItems();
        begin(Batch.TEXT, null);
        float scale = c.size() / VANILLA_SIZE;
        textMatrix.set(pose).translate(c.x(), c.y(), z).scale(scale, scale, 1);
        font.drawInBatch(c.text(), 0, 0, c.color(), false, textMatrix, graphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return null;
    }

    /**
     * Draws an {@code <item>}. The payload is either an {@link ItemStack} (a snapshot from a value
     * cell) or a {@code Supplier<ItemStack>} handle read here, at replay, so a live stack is drawn
     * every frame without any comparison or copy.
     *
     * @throws IllegalStateException for a kind other than {@link #ITEM} or any other payload type
     */
    @Override
    public Void visitCustom(DrawCommand.Custom c) {
        if (!c.kind().equals(ITEM)) {
            throw new IllegalStateException("no painter for native element <" + c.kind() + ">");
        }
        ItemStack stack = itemOf(c.payload());
        if (stack.isEmpty()) {
            return null;
        }
        flush();
        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(c.x(), c.y(), z);
        ps.scale(c.width() / ITEM_SIZE, c.height() / ITEM_SIZE, 1);
        graphics.renderItem(stack, 0, 0);
        ps.popPose();
        afterItem = true;
        return null;
    }

    private static ItemStack itemOf(Object payload) {
        if (payload instanceof ItemStack stack) {
            return stack;
        }
        if (payload instanceof Supplier<?> handle && handle.get() instanceof ItemStack stack) {
            return stack;
        }
        throw new IllegalStateException("<item> needs an ItemStack or a Supplier<ItemStack>, got "
                + (payload instanceof Supplier<?> h ? "a Supplier of " + h.get() : payload.getClass().getName()));
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

    private void raiseAboveItems() {
        if (afterItem) {
            z += ITEM_DEPTH;
            afterItem = false;
        }
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
