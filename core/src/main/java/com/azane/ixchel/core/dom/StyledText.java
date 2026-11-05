package com.azane.ixchel.core.dom;

import java.util.List;

/**
 * Text whose pieces carry styles of their own, as a Minecraft {@code Component} does; the host converts
 * such a bound value into this (see {@code Bindings.TextConverter}). A span's styles add to those of
 * the element holding the text: a color replaces its {@code color}, a font its {@code font-family}, and
 * each flag is on when the span or the element sets it, so a span cannot turn off the element's bold.
 */
public record StyledText(List<Span> spans) {
    public static final int BOLD = 1;
    public static final int ITALIC = 1 << 1;
    public static final int UNDERLINE = 1 << 2;
    public static final int STRIKETHROUGH = 1 << 3;
    /** Vanilla's obfuscated ({@code §k}) text: same advances, glyphs swapped every frame. */
    public static final int OBFUSCATED = 1 << 4;

    public StyledText {
        spans = List.copyOf(spans);
    }

    /**
     * @param color opaque ARGB, or {@code 0} to keep the element's {@code color}
     * @param flags {@link #BOLD}, {@link #ITALIC}, {@link #UNDERLINE}, {@link #STRIKETHROUGH}, {@link #OBFUSCATED}
     * @param font  a font id, or {@code null} to keep the element's {@code font-family}
     */
    public record Span(String text, int color, int flags, String font) {
    }

    /** The spans' text joined. */
    public String plain() {
        StringBuilder sb = new StringBuilder();
        for (Span s : spans) {
            sb.append(s.text());
        }
        return sb.toString();
    }
}
