package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.ShapeTessellator;
import com.azane.ixchel.core.template.NativePayload;
import com.azane.ixchel.core.template.TagRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Replays a {@link DisplayList} into a {@link GuiGraphics}. Rects and images are written as raw
 * quads and submitted only when the render state (shader, texture, clip) changes or a text run
 * starts; text goes through {@link Font#drawInBatch} and is flushed before the next non-text command,
 * so paint order is kept. Native elements are drawn by their {@link NativePainter}.
 *
 * <p>Fills, borders and shadows are {@link ShapeTessellator} meshes over the host's mask atlas
 * ({@link MaskTexture}), drawn with face culling off since their quads' winding varies. An image with a
 * forced filter sets its texture's filters for its draw and restores them after.
 *
 * <p>Native drawings go through vanilla and write depth above the level they draw at. Each command
 * draws at the highest depth reached by the earlier commands it overlaps ({@link Layers}), so it
 * paints on top of them, and the depth grows only with how much stacks at one spot.
 *
 * <p>A transform goes into the pose the quads, text and native drawings are written in; depth
 * levels and scissor clips use the transformed rectangles' bounding boxes.
 */
public final class McPainter implements DrawCommand.Visitor<Void> {
    private static final float VANILLA_SIZE = 9f;
    /** The font style of each {@link StyledText} flag combination. */
    private static final Style[] STYLES = new Style[32];

    static {
        for (int f = 0; f < STYLES.length; f++) {
            STYLES[f] = Style.EMPTY.withBold((f & StyledText.BOLD) != 0).withItalic((f & StyledText.ITALIC) != 0)
                    .withUnderlined((f & StyledText.UNDERLINE) != 0).withStrikethrough((f & StyledText.STRIKETHROUGH) != 0)
                    .withObfuscated((f & StyledText.OBFUSCATED) != 0);
        }
    }

    private enum Batch { NONE, COLOR, TEXTURE, SHAPE, TEXT }

    private final Font font;
    private final TagRegistry tags;
    private final Map<String, NativePainter<?>> natives;
    private final Map<String, ResourceLocation> textures = new HashMap<>();
    private final Matrix4f textMatrix = new Matrix4f();
    private final Matrix4f local = new Matrix4f();
    private final Deque<Affine> outer = new ArrayDeque<>();
    private GuiGraphics graphics;
    private Matrix4f base;
    /** {@link #base} times {@link #transform}. */
    private final Matrix4f pose = new Matrix4f();
    private Affine transform = Affine.IDENTITY;
    /** Written by {@link #bounds}. */
    private float bx0, by0, bx1, by1;
    private BufferBuilder buffer;
    private Batch batch = Batch.NONE;
    private ResourceLocation texture;
    private DrawCommand.Image.Filter filter = DrawCommand.Image.Filter.AUTO;
    private final MaskTexture masks;
    private final ShapeTessellator shapes;
    private final Layers layers = new Layers();
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;

    /**
     * @param modid   the host mod, whose mask atlas the shapes are drawn from
     * @param tags    the types of the native elements, whose payload types {@code natives} are handed
     * @param natives the painter of each native element kind ({@code DrawCommand.Custom#kind})
     */
    public McPainter(String modid, Font font, TagRegistry tags, Map<String, NativePainter<?>> natives) {
        this.masks = MaskTexture.forHost(modid);
        this.shapes = new ShapeTessellator(masks.atlas);
        this.font = font;
        this.tags = tags;
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
        base = g.pose().last().pose();
        transform = Affine.IDENTITY;
        outer.clear();
        pose.set(base);
        layers.clear();
        masks.active = this;
        list.replay(this);
        flush();
        masks.active = null;
        shapes.endFrame();
        RenderSystem.disableBlend();
        graphics = null;
        base = null;
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
        float z = level(x0, y0, x1, y1);
        buffer.addVertex(pose, x0, y0, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x0, y1, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y0, z).setColor(r, g, b, a);
        return null;
    }

    @Override
    public Void visitImage(DrawCommand.Image c) {
        begin(Batch.TEXTURE, textures.computeIfAbsent(c.src(), ResourceLocation::parse), c.filter());
        int argb = c.tint();
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float x0 = c.x();
        float y0 = c.y();
        float x1 = x0 + c.width();
        float y1 = y0 + c.height();
        float z = level(x0, y0, x1, y1);
        buffer.addVertex(pose, x0, y0, z).setColor(r, g, b, a).setUv(c.u0(), c.v0());
        buffer.addVertex(pose, x0, y1, z).setColor(r, g, b, a).setUv(c.u0(), c.v1());
        buffer.addVertex(pose, x1, y1, z).setColor(r, g, b, a).setUv(c.u1(), c.v1());
        buffer.addVertex(pose, x1, y0, z).setColor(r, g, b, a).setUv(c.u1(), c.v0());
        return null;
    }

    @Override
    public Void visitFill(DrawCommand.Fill c) {
        return shape(c);
    }

    @Override
    public Void visitBorder(DrawCommand.Border c) {
        return shape(c);
    }

    @Override
    public Void visitShadow(DrawCommand.Shadow c) {
        return shape(c);
    }

    private Void shape(DrawCommand c) {
        float[] m = shapes.mesh(c);
        if (m.length == 0) {
            return null;
        }
        // After the mesh: building it may have written masks, or reset the atlas and drawn the batch.
        begin(Batch.SHAPE, masks.upload());
        float x0 = Float.POSITIVE_INFINITY;
        float y0 = Float.POSITIVE_INFINITY;
        float x1 = Float.NEGATIVE_INFINITY;
        float y1 = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < m.length; i += ShapeTessellator.STRIDE) {
            x0 = Math.min(x0, m[i]);
            y0 = Math.min(y0, m[i + 1]);
            x1 = Math.max(x1, m[i]);
            y1 = Math.max(y1, m[i + 1]);
        }
        float z = level(x0, y0, x1, y1);
        for (int i = 0; i < m.length; i += ShapeTessellator.STRIDE) {
            int argb = Float.floatToRawIntBits(m[i + 4]);
            buffer.addVertex(pose, m[i], m[i + 1], z).setUv(m[i + 2], m[i + 3])
                    .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24);
        }
        return null;
    }

    @Override
    public Void visitText(DrawCommand.Text c) {
        // Font draws alpha below 4 as opaque; that text is invisible, as fading text must end.
        if ((c.color() & 0xFC000000) == 0) {
            return null;
        }
        float scale = c.size() / VANILLA_SIZE;
        FormattedCharSequence text = FormattedCharSequence.forward(c.text(), STYLES[c.flags()]);
        float z = 0;
        // Measuring is only needed once something is raised.
        if (!layers.isEmpty()) {
            z = level(c.x(), c.y(), c.x() + font.width(text) * scale, c.y() + c.size());
        }
        begin(Batch.TEXT, null);
        textMatrix.set(pose).translate(c.x(), c.y(), z).scale(scale, scale, 1);
        font.drawInBatch(text, 0, 0, c.color(), false, textMatrix, graphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return null;
    }

    /** @throws IllegalStateException for a kind without a painter, or a payload value of another type */
    @Override
    @SuppressWarnings("unchecked")
    public Void visitCustom(DrawCommand.Custom c) {
        NativePainter<Object> p = (NativePainter<Object>) natives.get(c.kind());
        if (p == null) {
            throw new IllegalStateException("no painter for native element <" + c.kind() + ">");
        }
        Object value = NativePayload.valueOf(c.payload(), tags.type(c.kind()));
        if (value == null) {
            return null;
        }
        flush();
        boolean transformed = transform != Affine.IDENTITY;
        if (transformed) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(local);
        }
        bounds(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        float z = layers.level(bx0, by0, bx1, by1);
        layers.add(bx0, by0, bx1, by1, z + p.paint(graphics, c, value, z, pointerX, pointerY));
        if (transformed) {
            graphics.pose().popPose();
        }
        return null;
    }

    @Override
    public Void visitPushClip(DrawCommand.PushClip c) {
        flush();
        bounds(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        graphics.enableScissor((int) Math.floor(bx0), (int) Math.floor(by0), (int) Math.ceil(bx1), (int) Math.ceil(by1));
        return null;
    }

    @Override
    public Void visitPopClip(DrawCommand.PopClip c) {
        flush();
        graphics.disableScissor();
        return null;
    }

    @Override
    public Void visitPushTransform(DrawCommand.PushTransform c) {
        outer.push(transform);
        setTransform(transform.then(c.matrix()));
        return null;
    }

    @Override
    public Void visitPopTransform(DrawCommand.PopTransform c) {
        setTransform(outer.pop());
        return null;
    }

    private void setTransform(Affine m) {
        transform = m;
        local.set(m.a(), m.b(), 0, 0, m.c(), m.d(), 0, 0, 0, 0, 1, 0, m.e(), m.f(), 0, 1);
        pose.set(base).mul(local);
    }

    /** The depth for the rectangle {@code (x0, y0)}-{@code (x1, y1)}, which it then occupies. */
    private float level(float x0, float y0, float x1, float y1) {
        bounds(x0, y0, x1, y1);
        float z = layers.level(bx0, by0, bx1, by1);
        layers.add(bx0, by0, bx1, by1, z);
        return z;
    }

    /** Sets {@link #bx0}…{@link #by1} to the box of the rectangle through the current transform. */
    private void bounds(float x0, float y0, float x1, float y1) {
        Affine m = transform;
        if (m == Affine.IDENTITY) {
            bx0 = x0;
            by0 = y0;
            bx1 = x1;
            by1 = y1;
            return;
        }
        float ax = m.x(x0, y0), ay = m.y(x0, y0);
        float bx = m.x(x1, y0), by = m.y(x1, y0);
        float cx = m.x(x0, y1), cy = m.y(x0, y1);
        float dx = m.x(x1, y1), dy = m.y(x1, y1);
        bx0 = Math.min(Math.min(ax, bx), Math.min(cx, dx));
        by0 = Math.min(Math.min(ay, by), Math.min(cy, dy));
        bx1 = Math.max(Math.max(ax, bx), Math.max(cx, dx));
        by1 = Math.max(Math.max(ay, by), Math.max(cy, dy));
    }

    private void begin(Batch next, ResourceLocation nextTexture) {
        begin(next, nextTexture, DrawCommand.Image.Filter.AUTO);
    }

    private void begin(Batch next, ResourceLocation nextTexture, DrawCommand.Image.Filter nextFilter) {
        if (batch == next && texture == nextTexture && filter == nextFilter) {
            return;
        }
        flush();
        batch = next;
        texture = nextTexture;
        filter = nextFilter;
        if (next == Batch.COLOR || next == Batch.TEXTURE || next == Batch.SHAPE) {
            // A text or item flush runs a render type's cleanup, which turns blending off again.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        switch (next) {
            case COLOR -> {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            case TEXTURE, SHAPE -> {
                RenderSystem.setShaderTexture(0, nextTexture);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            case TEXT, NONE -> {
            }
        }
    }

    /** Draws the pending quads; for {@link MaskTexture}'s reset, which drops the masks they sample. */
    void flushBatch() {
        flush();
    }

    private void flush() {
        switch (batch) {
            case COLOR -> draw();
            case TEXTURE -> {
                if (filter == DrawCommand.Image.Filter.AUTO) {
                    draw();
                } else {
                    drawFiltered();
                }
            }
            case SHAPE -> {
                boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                RenderSystem.disableCull();
                draw();
                if (cull) {
                    RenderSystem.enableCull();
                }
            }
            case TEXT -> graphics.flush();
            case NONE -> {
            }
        }
        batch = Batch.NONE;
        texture = null;
        filter = DrawCommand.Image.Filter.AUTO;
    }

    private void draw() {
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        buffer = null;
    }

    /** Draws the texture batch with {@link #filter} as its texture's filters, then restores them. */
    private void drawFiltered() {
        int id = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
        int unit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(id);
        int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        int f = filter == DrawCommand.Image.Filter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, f);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, f);
        draw();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        GlStateManager._activeTexture(unit);
    }
}
