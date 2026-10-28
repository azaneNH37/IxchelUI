package com.azane.ixchel.mc;

import com.azane.ixchel.core.css.property.Syntaxes;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
    /**
     * Prefix of an image source naming a sprite of the block atlas (blocks and items in 1.20.1), as
     * {@code sprite:minecraft:block/water_still}; animated sprites animate.
     */
    public static final String SPRITE = Syntaxes.SPRITE;
    /** Tag and payload name of the item element. */
    public static final String ITEM = "item";
    /** Tag and payload name of the entity element. */
    public static final String ENTITY = "entity";
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
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;

    public McPainter(Font font) {
        this.font = font;
    }

    /** Draws {@code list} in the current pose of {@code g}; leaves blending disabled, like vanilla blits. */
    public void paint(GuiGraphics g, DisplayList list) {
        paint(g, list, Float.NaN, Float.NaN);
    }

    /**
     * As {@link #paint(GuiGraphics, DisplayList)}, with the pointer {@code <entity>} elements turn
     * their head towards; NaN (a HUD) makes them face forward.
     */
    public void paint(GuiGraphics g, DisplayList list, float pointerX, float pointerY) {
        this.pointerX = pointerX;
        this.pointerY = pointerY;
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

    /**
     * The depth, in the pose {@link #paint} drew in, above everything it drew; an overlay such as a
     * tooltip raised by this much paints on top.
     */
    public float topZ() {
        return afterItem ? z + ITEM_DEPTH : z;
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
        String src = c.src();
        float u0 = c.u0();
        float v0 = c.v0();
        float u1 = c.u1();
        float v1 = c.v1();
        if (src.startsWith(SPRITE)) {
            // Looked up every time: a resource reload replaces the atlas's sprites.
            TextureAtlasSprite s = sprite(textures.computeIfAbsent(src, McPainter::spriteId));
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
        buffer.vertex(pose, x0, y0, z).color(r, g, b, a).uv(u0, v0).endVertex();
        buffer.vertex(pose, x0, y1, z).color(r, g, b, a).uv(u0, v1).endVertex();
        buffer.vertex(pose, x1, y1, z).color(r, g, b, a).uv(u1, v1).endVertex();
        buffer.vertex(pose, x1, y0, z).color(r, g, b, a).uv(u1, v0).endVertex();
        return null;
    }

    /** The sprite id of a {@link #SPRITE} source. */
    static ResourceLocation spriteId(String src) {
        return new ResourceLocation(src.substring(SPRITE.length()));
    }

    /** The block atlas sprite {@code id}, or the missing-texture sprite. */
    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    @Override
    public Void visitText(DrawCommand.Text c) {
        // Font draws alpha below 4 as opaque; that text is invisible, as fading text must end.
        if ((c.color() & 0xFC000000) == 0) {
            return null;
        }
        raiseAboveItems();
        begin(Batch.TEXT, null);
        float scale = c.size() / VANILLA_SIZE;
        textMatrix.set(pose).translate(c.x(), c.y(), z).scale(scale, scale, 1);
        font.drawInBatch(c.text(), 0, 0, c.color(), false, textMatrix, graphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return null;
    }

    /**
     * Draws an {@code <item>} or an {@code <entity>}. An item payload is either an {@link ItemStack}
     * (a snapshot from a value cell) or a {@code Supplier<ItemStack>} handle read here, at replay, so a
     * live stack is drawn every frame without any comparison or copy; an entity payload is a
     * {@link LivingEntity} or a {@code Supplier} of one, the same way.
     *
     * @throws IllegalStateException for another kind or payload type
     */
    @Override
    public Void visitCustom(DrawCommand.Custom c) {
        if (c.kind().equals(ENTITY)) {
            LivingEntity e = entityOf(c.payload());
            if (e != null) {
                drawEntity(c, e);
            }
            return null;
        }
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

    /**
     * The entity standing on the bottom center of the box, scaled to fit it, drawn as the vanilla
     * inventory draws the player: lit from the front, head and body turned towards the pointer.
     */
    private void drawEntity(DrawCommand.Custom c, LivingEntity e) {
        flush();
        float fit = Math.min(c.height() / e.getBbHeight(), c.width() / (e.getBbWidth() * 2)) * 0.9f;
        int scale = Math.max(1, Math.round(fit));
        int feetX = Math.round(c.x() + c.width() / 2);
        int feetY = Math.round(c.y() + c.height());
        float eyeY = feetY - scale * e.getEyeHeight();
        // Vanilla passes the box's eye point minus the pointer; zero looks straight out of the screen.
        float lookX = Float.isNaN(pointerX) ? 0 : feetX - pointerX;
        float lookY = Float.isNaN(pointerY) ? 0 : eyeY - pointerY;
        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(0, 0, z);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, feetX, feetY, scale, lookX, lookY, e);
        ps.popPose();
        afterItem = true;
    }

    /** The entity an {@code <entity>} payload stands for right now, or {@code null}. */
    static LivingEntity entityOf(Object payload) {
        if (payload instanceof LivingEntity e) {
            return e;
        }
        if (payload instanceof Supplier<?> handle) {
            Object v = handle.get();
            if (v == null || v instanceof LivingEntity) {
                return (LivingEntity) v;
            }
            throw new IllegalStateException("<entity> needs a LivingEntity or a Supplier of one, got a Supplier of " + v);
        }
        throw new IllegalStateException("<entity> needs a LivingEntity or a Supplier of one, got "
                + payload.getClass().getName());
    }

    /** The stack an {@code <item>} payload stands for right now; see {@link #visitCustom}. */
    static ItemStack itemOf(Object payload) {
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
