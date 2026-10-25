package com.azane.ixchel.core.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Greedy line breaking at spaces (text arrives with whitespace already collapsed). A word wider than
 * the limit gets a line of its own and overflows; words are never split.
 */
public final class TextShaper {
    public record Line(String text, float width) {
    }

    public record Shaped(List<Line> lines, float width, float height) {
    }

    private record Key(String text, float size, float maxWidth) {
    }

    /** Changing texts (counters) would otherwise grow the cache without bound. */
    private static final int MAX_CACHE = 2048;

    private final TextMeasurer measurer;
    private final Map<Key, Shaped> cache = new HashMap<>();

    public TextShaper(TextMeasurer measurer) {
        this.measurer = measurer;
    }

    /** {@code maxWidth} may be {@link Float#POSITIVE_INFINITY} for no wrapping. */
    public Shaped shape(String text, float size, float maxWidth) {
        Key key = new Key(text, size, maxWidth);
        Shaped s = cache.get(key);
        if (s == null) {
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
            s = wrap(text, size, maxWidth);
            cache.put(key, s);
        }
        return s;
    }

    /** Width of the widest word: the narrowest the text can get without overflowing. */
    public float minContentWidth(String text, float size) {
        float max = 0;
        for (String word : text.split(" ")) {
            max = Math.max(max, measurer.width(word, size));
        }
        return max;
    }

    public float lineHeight(float size) {
        return measurer.lineHeight(size);
    }

    /** Fonts reloaded: cached widths are stale. */
    public void invalidate() {
        cache.clear();
    }

    private Shaped wrap(String text, float size, float maxWidth) {
        float full = measurer.width(text, size);
        float lh = measurer.lineHeight(size);
        if (full <= maxWidth) {
            return new Shaped(List.of(new Line(text, full)), full, lh);
        }
        List<Line> lines = new ArrayList<>();
        float widest = 0;
        int lineStart = 0;
        int lastBreak = -1;
        float lastBreakWidth = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != ' ') {
                continue;
            }
            float w = measurer.width(text.substring(lineStart, i), size);
            if (w > maxWidth && lastBreak > lineStart) {
                lines.add(new Line(text.substring(lineStart, lastBreak), lastBreakWidth));
                widest = Math.max(widest, lastBreakWidth);
                lineStart = lastBreak + 1;
                w = measurer.width(text.substring(lineStart, i), size);
            }
            lastBreak = i;
            lastBreakWidth = w;
        }
        lines.add(new Line(text.substring(lineStart), lastBreakWidth));
        widest = Math.max(widest, lastBreakWidth);
        return new Shaped(List.copyOf(lines), widest, lh * lines.size());
    }
}
