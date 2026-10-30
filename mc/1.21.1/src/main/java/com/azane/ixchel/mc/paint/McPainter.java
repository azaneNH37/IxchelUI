package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.dom.StyledText;
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
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

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

    private enum Batch { NONE, COLOR, TEXTURE, TEXT }

    private final Font font;
    private final Map<String, NativePainter> natives;
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
        base = g.pose().last().pose();
        transform = Affine.IDENTITY;
        outer.clear();
        pose.set(base);
        layers.clear();
        list.replay(this);
        flush();
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
        float z = level(x0, y0, x1, y1);
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

    /** @throws IllegalStateException for a kind without a painter */
    @Override
    public Void visitCustom(DrawCommand.Custom c) {
        NativePainter p = natives.get(c.kind());
        if (p == null) {
            throw new IllegalStateException("no painter for native element <" + c.kind() + ">");
        }
        flush();
        boolean transformed = transform != Affine.IDENTITY;
        if (transformed) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(local);
        }
        bounds(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        float z = layers.level(bx0, by0, bx1, by1);
        layers.add(bx0, by0, bx1, by1, z + p.paint(graphics, c, z, pointerX, pointerY));
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
