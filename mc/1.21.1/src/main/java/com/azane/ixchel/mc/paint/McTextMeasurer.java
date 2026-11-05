package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla font metrics: CSS {@code 9px} is the font at scale 1 (line height 9, baseline 7). Bold glyphs
 * are one pixel wider. A font id is measured through {@link Style#withFont}; the host has it when a
 * resource pack defines {@code assets/<namespace>/font/<path>.json}. One per document, so a resource
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
    /** Each font id's regular and bold style. */
    private final Map<String, Style[]> styles = new HashMap<>();
    private final Map<String, Boolean> fonts = new HashMap<>();

    public McTextMeasurer(Font font, ResourceManager resources) {
        this.font = font;
        this.resources = resources;
    }

    @Override
    public float width(String text, float size, boolean bold, String fontId) {
        Style style = fontId == null ? bold ? BOLD : Style.EMPTY
                : styles.computeIfAbsent(fontId, McTextMeasurer::styles)[bold ? 1 : 0];
        return font.getSplitter().stringWidth(sequence(text, style)) * size / VANILLA_SIZE;
    }

    /**
     * {@code text} in {@code style}, every character as written: a {@code §} is drawn, not read as a
     * formatting code. Not {@link FormattedCharSequence#forward}, which ModernUI replaces on 1.21 with a
     * sequence it lays out with the codes applied.
     */
    static FormattedCharSequence sequence(String text, Style style) {
        return text.isEmpty() ? FormattedCharSequence.EMPTY : sink -> StringDecomposer.iterate(text, style, sink);
    }

    private static Style[] styles(String fontId) {
        Style regular = Style.EMPTY.withFont(ResourceLocation.tryParse(fontId));
        return new Style[]{regular, regular.withBold(true)};
    }

    @Override
    public boolean hasFont(String fontId) {
        return fonts.computeIfAbsent(fontId, f -> {
            ResourceLocation id = ResourceLocation.tryParse(f);
            return id != null && resources.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "font/" + id.getPath() + ".json")).isPresent();
        });
    }

    @Override
    public float lineHeight(float size) {
        return font.lineHeight * size / VANILLA_SIZE;
    }

    @Override
    public float baseline(float size) {
        return VANILLA_BASELINE * size / VANILLA_SIZE;
    }
}
