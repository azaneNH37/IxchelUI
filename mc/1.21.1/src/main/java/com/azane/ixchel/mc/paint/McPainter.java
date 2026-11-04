package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.effect.Effect;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.RoundRect;
import com.azane.ixchel.core.paint.ShapeTessellator;
import com.azane.ixchel.core.view.ElementPayload;
import com.azane.ixchel.core.view.TagRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Replays a {@link DisplayList} into a {@link GuiGraphics}. Rects and images are written as raw
 * quads and submitted only when the render state (shader, texture, clip) changes or a text run
 * starts; text goes through {@link Font#drawInBatch} and is flushed before the next non-text command,
 * so paint order is kept. Drawn elements are drawn by their {@link ElementPainter}.
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
 *
 * <p>A layer ({@code DrawCommand.PushLayer}) draws what follows, native drawings included, into an
 * {@link OffscreenLayers} target over its rectangle on the screen, cut by the clip, and composites it
 * at its opacity when it closes. Inside, colors blend with separate alpha factors so the target holds
 * premultiplied color, and clips scissor the target.
 *
 * <p>A box effect ({@code DrawCommand.Paint}) is its {@link EffectPrograms} program drawn over the
 * shape; a layer's effects run as its filter passes. Neither draws when its program did not compile.
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
    private final Map<String, ElementPainter<?>> painters;
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
    private final OffscreenLayers offscreen = new OffscreenLayers();
    private final String modid;
    /** Built on the first filter, blend mode, backdrop or effect; see {@link #passes()}. */
    private FilterPasses passes;
    private final int[] backdropSize = new int[2];
    /** The clips pushed in this paint, intersected: x0, y0, x1, y1 in GUI pixels per level. */
    private int[] clips = new int[4 * 8];
    private int clipDepth;
    private float pointerX = Float.NaN;
    private float pointerY = Float.NaN;
    private float time;

    /**
     * @param modid   the host mod, whose mask atlas the shapes are drawn from
     * @param tags    the types of the drawn elements, whose payload types {@code painters} are handed
     * @param painters the painter of each drawn element kind ({@code DrawCommand.Custom#kind})
     */
    public McPainter(String modid, Font font, TagRegistry tags, Map<String, ElementPainter<?>> painters) {
        this.modid = modid;
        this.masks = MaskTexture.forHost(modid);
        this.shapes = new ShapeTessellator(masks.atlas);
        this.font = font;
        this.tags = tags;
        this.painters = Map.copyOf(painters);
    }

    /**
     * Builds the programs of a document's effects, as it loads, and reports each that does not compile
     * to {@code diag}.
     */
    public void compileEffects(Collection<Effect> effects, Diagnostics diag) {
        passes().effects().compile(effects, diag);
    }

    /**
     * Draws {@code list} in the current pose of {@code g}, with the pointer drawn elements and effects
     * may react to (NaN in a HUD) and the document's time in nanoseconds; leaves blending disabled,
     * like vanilla blits.
     */
    public void paint(GuiGraphics g, DisplayList list, float pointerX, float pointerY, long time) {
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        this.time = Math.floorMod(time, 3_600_000_000_000L) / 1e9f;
        graphics = g;
        base = g.pose().last().pose();
        transform = Affine.IDENTITY;
        outer.clear();
        pose.set(base);
        layers.clear();
        clipDepth = 0;
        masks.active = this;
        list.replay(this);
        flush();
        masks.active = null;
        shapes.endFrame();
        OffscreenLayers.evictIdle();
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
        ElementPainter<Object> p = (ElementPainter<Object>) painters.get(c.kind());
        if (p == null) {
            throw new IllegalStateException("no painter for drawn element <" + c.kind() + ">");
        }
        Object value = ElementPayload.valueOf(c.payload(), tags.type(c.kind()));
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
        int x0 = (int) Math.floor(bx0);
        int y0 = (int) Math.floor(by0);
        int x1 = (int) Math.ceil(bx1);
        int y1 = (int) Math.ceil(by1);
        graphics.enableScissor(x0, y0, x1, y1);
        if (clips.length < 4 * (clipDepth + 1)) {
            clips = Arrays.copyOf(clips, clips.length * 2);
        }
        int o = 4 * clipDepth++;
        if (o > 0) {
            x0 = Math.max(x0, clips[o - 4]);
            y0 = Math.max(y0, clips[o - 3]);
            x1 = Math.min(x1, clips[o - 2]);
            y1 = Math.min(y1, clips[o - 1]);
        }
        clips[o] = x0;
        clips[o + 1] = y0;
        clips[o + 2] = Math.max(x0, x1);
        clips[o + 3] = Math.max(y0, y1);
        if (offscreen.active()) {
            layerScissor();
        }
        return null;
    }

    @Override
    public Void visitPopClip(DrawCommand.PopClip c) {
        flush();
        graphics.disableScissor();
        clipDepth--;
        if (offscreen.active()) {
            layerScissor();
        }
        return null;
    }

    /** Opens the layer over its rectangle through the current transform, cut by the current clip. */
    @Override
    public Void visitPushLayer(DrawCommand.PushLayer c) {
        flush();
        graphics.flush();
        bounds(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        if (clipDepth > 0) {
            int o = 4 * (clipDepth - 1);
            bx0 = Math.max(bx0, clips[o]);
            by0 = Math.max(by0, clips[o + 1]);
            bx1 = Math.min(bx1, clips[o + 2]);
            by1 = Math.min(by1, clips[o + 3]);
        }
        offscreen.push(bx0, by0, bx1, by1, c, transform, pointerX, pointerY, time);
        layerScissor();
        return null;
    }

    /** Binds the parent again and draws the layer onto it, above what it overlaps there. */
    @Override
    public Void visitPopLayer(DrawCommand.PopLayer c) {
        flush();
        graphics.flush();
        OffscreenLayers.Layer l = offscreen.pop();
        offscreen.filter(l, passes());
        restoreScissor();
        if (l.visible()) {
            float z = layers.level(l.guiX0, l.guiY0, l.guiX1, l.guiY1);
            layers.add(l.guiX0, l.guiY0, l.guiX1, l.guiY1, z);
            offscreen.composite(l, base, z, passes().shaders());
            offscreen.release(l);
        }
        return null;
    }

    /** Draws the box effect's program over its shape, above what it overlaps. */
    @Override
    public Void visitPaint(DrawCommand.Paint c) {
        EffectPrograms effects = passes().effects();
        ShaderInstance s = effects.program(c.effect());
        if (s == null) {
            return null;
        }
        flush();
        graphics.flush();
        RoundRect shape = c.shape();
        float z = level(shape.x(), shape.y(), shape.x() + shape.width(), shape.y() + shape.height());
        Affine back = Float.isNaN(pointerX) ? null : transform.invert();
        float mx = back == null ? Float.NaN : back.x(pointerX, pointerY) - shape.x();
        float my = back == null ? Float.NaN : back.y(pointerX, pointerY) - shape.y();
        effects.bind(s, c.effect(), c.values(), shape, (float) Minecraft.getInstance().getWindow().getGuiScale(), time, mx, my);
        effects.drawBox(s, shape, pose, z);
        return null;
    }

    /**
     * Copies what is drawn behind the shape so far (on the target under the element's own layer when
     * it is {@code belowGroup}), filters it and draws it back inside the shape.
     */
    @Override
    public Void visitBackdrop(DrawCommand.Backdrop c) {
        flush();
        graphics.flush();
        RoundRect shape = c.shape();
        bounds(shape.x(), shape.y(), shape.x() + shape.width(), shape.y() + shape.height());
        if (clipDepth > 0) {
            int o = 4 * (clipDepth - 1);
            bx0 = Math.max(bx0, clips[o]);
            by0 = Math.max(by0, clips[o + 1]);
            bx1 = Math.min(bx1, clips[o + 2]);
            by1 = Math.min(by1, clips[o + 3]);
        }
        Window window = Minecraft.getInstance().getWindow();
        double s = window.getGuiScale();
        int h = window.getHeight();
        // The rectangle in window pixels, y up, cut by the target the backdrop is read from.
        OffscreenLayers.Layer source = offscreen.open(c.belowGroup() ? 1 : 0);
        OffscreenLayers.Layer current = offscreen.open(0);
        int left = (int) Math.floor(bx0 * s);
        int right = (int) Math.ceil(bx1 * s);
        int bottom = h - (int) Math.ceil(by1 * s);
        int top = h - (int) Math.floor(by0 * s);
        int fbo;
        if (source != null) {
            if (!source.visible()) {
                return null;
            }
            left = Math.max(left, source.x);
            right = Math.min(right, source.x + source.width);
            bottom = Math.max(bottom, source.y);
            top = Math.min(top, source.y + source.height);
            fbo = source.target.fbo.frameBufferId;
        } else {
            left = Math.max(left, 0);
            right = Math.min(right, window.getWidth());
            bottom = Math.max(bottom, 0);
            top = Math.min(top, h);
            fbo = offscreen.baseFbo();
        }
        if (right <= left || top <= bottom || (current != null && !current.visible())) {
            return null;
        }
        int boundFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int vx = GlStateManager.Viewport.x(), vy = GlStateManager.Viewport.y();
        int vw = GlStateManager.Viewport.width(), vh = GlStateManager.Viewport.height();
        int sx = source != null ? source.x : 0, sy = source != null ? source.y : 0;
        OffscreenLayers.Target t = passes().backdrop(fbo, left - sx, bottom - sy, right - left, top - bottom,
                c.filters(), (float) s, backdropSize);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundFbo);
        RenderSystem.viewport(vx, vy, vw, vh);
        restoreScissor();
        int cx = current != null ? current.x : 0, cy = current != null ? current.y : 0;
        float z = level(shape.x(), shape.y(), shape.x() + shape.width(), shape.y() + shape.height());
        passes().drawBackdrop(t, backdropSize[0], backdropSize[1], left - cx, bottom - cy, right - left, top - bottom,
                shape, pose, z, source == null);
        OffscreenLayers.release(t);
        return null;
    }

    private FilterPasses passes() {
        if (passes == null) {
            passes = new FilterPasses(FilterShaders.forHost(modid), EffectPrograms.forHost(modid));
        }
        return passes;
    }

    /** Applies the current clip again after a layer closed or a filter pass turned the scissor off. */
    private void restoreScissor() {
        if (offscreen.active()) {
            layerScissor();
        } else {
            // A rectangle covering everything: pushing it applies the stack's top again, popping it keeps that or turns the scissor off.
            graphics.enableScissor(-1_000_000, -1_000_000, 1_000_000, 1_000_000);
            graphics.disableScissor();
        }
    }

    /** Scissors the innermost layer to the current clip. */
    private void layerScissor() {
        int o = 4 * (clipDepth - 1);
        if (clipDepth > 0) {
            offscreen.scissor(true, clips[o], clips[o + 1], clips[o + 2], clips[o + 3]);
        } else {
            offscreen.scissor(false, 0, 0, 0, 0);
        }
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
            if (offscreen.active()) {
                // Keeps the layer's alpha: it is composited with its own coverage.
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            } else {
                RenderSystem.defaultBlendFunc();
            }
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
