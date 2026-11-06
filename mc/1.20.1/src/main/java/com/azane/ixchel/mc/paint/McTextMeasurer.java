package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.text.TextMeasurer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Vanilla font metrics: CSS {@code 9px} is the font at scale 1 (line height 9, baseline 7). Text is
 * measured and drawn at its {@linkplain #crispSize crisp size}, so each pixel of a bitmap glyph covers
 * whole screen pixels and no stroke comes out thicker than another. Bold glyphs
 * are one pixel wider. A font id is measured through {@link Style#withFont}; the host has it when a
 * resource pack defines {@code assets/<namespace>/font/<path>.json}. A font id naming a {@code .ttf}
 * or {@code .otf} file, an {@code @font-face}'s, is a {@link Face}; the characters it lacks are
 * measured, and drawn, in the default font. One per document, so a resource
 * reload, which loads the document again, sees the new packs. Widths are the font's fractional
 * advances, not {@link Font#width}'s rounded-up whole pixels: summed over a line's runs, those would add
 * up to a pixel per run where advances are fractional (TrueType fonts, or every font under ModernUI).
 */
public final class McTextMeasurer implements TextMeasurer {
    private static final float VANILLA_SIZE = 9f;
    private static final float VANILLA_BASELINE = 7f;
    private static final Style BOLD = Style.EMPTY.withBold(true);

    private final Font font;
    private final ResourceManager resources;
    private final Function<String, byte[]> files;
    /** Each font id's regular and bold style. */
    private final Map<String, Style[]> styles = new HashMap<>();
    private final Map<String, Boolean> fonts = new HashMap<>();

    /**
     * The size text of {@code size} is drawn at under {@code guiScale}: the largest at which a glyph
     * pixel covers a whole number of screen pixels, at least one, so never larger than {@code size}
     * unless even one screen pixel per glyph pixel is: {@code 9px} at GUI scale 1, {@code 6px} at 3, but
     * {@code 7px} is {@code 4.5px} at 2. Under ModernUI fonts are TrueType and any size is smooth; sizes
     * step all the same, so a page lays out alike with and without it.
     */
    public static float crispSize(float size, double guiScale) {
        // The epsilon keeps a size that is exactly a step, 6px at scale 3, from rounding down a step.
        int pixels = Math.max(1, (int) Math.floor(size * guiScale / VANILLA_SIZE + 1e-4));
        return (float) (pixels * VANILLA_SIZE / guiScale);
    }

    private static float crispSize(float size) {
        return crispSize(size, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** The screen pixels a glyph pixel of text of {@code size} covers under {@code guiScale}, at its crisp size. */
    static int glyphPixels(float size, double guiScale) {
        return (int) Math.round(crispSize(size, guiScale) * guiScale / VANILLA_SIZE);
    }

    /** @param files the bytes of a font file id, {@code null} if there is no such file */
    public McTextMeasurer(Font font, ResourceManager resources, Function<String, byte[]> files) {
        this.font = font;
        this.resources = resources;
        this.files = files;
    }

    @Override
    public float width(String text, float size, boolean bold, String fontId) {
        if (fontId != null && Face.isFace(fontId)) {
            Face face = Face.get(fontId);
            if (face != null) {
                return faceWidth(font, face, text, size, bold, Minecraft.getInstance().getWindow().getGuiScale());
            }
            fontId = null;
        }
        Style style = fontId == null ? bold ? BOLD : Style.EMPTY
                : styles.computeIfAbsent(fontId, McTextMeasurer::styles)[bold ? 1 : 0];
        return font.getSplitter().stringWidth(sequence(text, style)) * crispSize(size) / VANILLA_SIZE;
    }

    /**
     * The width of {@code text} in {@code face}, in GUI pixels at {@code guiScale}: the rounded advances
     * of its glyphs, a glyph pixel more for each when bold as in the vanilla font, and the runs the face
     * lacks in the default font.
     */
    static float faceWidth(Font font, Face face, String text, float size, boolean bold, double guiScale) {
        int p = glyphPixels(size, guiScale);
        int px = 9 * p;
        double w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int g = face.glyph(cp);
            if (g != 0) {
                w += face.metrics(g, px)[0] + (bold ? p : 0);
                i += Character.charCount(cp);
            } else {
                int end = face.fallbackEnd(text, i);
                w += font.getSplitter().stringWidth(sequence(text.substring(i, end), bold ? BOLD : Style.EMPTY)) * p;
                i = end;
            }
        }
        return (float) (w / guiScale);
    }

    /**
     * {@code text} in {@code style}, every character as written: a {@code §} is drawn, not read as a
     * formatting code. Not {@link FormattedCharSequence#forward}, which ModernUI replaces on 1.21 with a
     * sequence it lays out with the codes applied.
     */
    static FormattedCharSequence sequence(String text, Style style) {
        return text.isEmpty() ? FormattedCharSequence.EMPTY : sink -> StringDecomposer.iterate(text, style, sink);
    }

    /** One character in a style, reused glyph by glyph where {@code letter-spacing} places each one. */
    static final class Glyph implements FormattedCharSequence {
        int codePoint;
        Style style = Style.EMPTY;

        @Override
        public boolean accept(FormattedCharSink sink) {
            return sink.accept(0, style, codePoint);
        }
    }

    private static Style[] styles(String fontId) {
        Style regular = Style.EMPTY.withFont(ResourceLocation.tryParse(fontId));
        return new Style[]{regular, regular.withBold(true)};
    }

    @Override
    public boolean hasFont(String fontId) {
        return fonts.computeIfAbsent(fontId, f -> {
            if (Face.isFace(f)) {
                return Face.load(f, () -> files.apply(f)) != null;
            }
            ResourceLocation id = ResourceLocation.tryParse(f);
            return id != null && resources.getResource(new ResourceLocation(id.getNamespace(), "font/" + id.getPath() + ".json")).isPresent();
        });
    }

    @Override
    public float lineHeight(float size) {
        return font.lineHeight * crispSize(size) / VANILLA_SIZE;
    }

    @Override
    public float baseline(float size) {
        return VANILLA_BASELINE * crispSize(size) / VANILLA_SIZE;
    }

    /** Whole screen pixels, as glyphs at the crisp size advance by, so every glyph stays on the pixel grid. */
    @Override
    public float letterSpacing(float spacing) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (Math.round(spacing * gui) / gui);
    }
}
