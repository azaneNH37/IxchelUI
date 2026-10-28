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
    public static final String ELLIPSIS = "\u2026";

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

    /**
     * {@code line} cut to the longest prefix that fits {@code maxWidth} together with an ellipsis
     * (trailing spaces dropped); {@code line} itself when it fits. When not even the ellipsis fits it
     * is returned alone, for the clip to cut.
     */
    public String ellipsize(String line, float size, float maxWidth) {
        if (measurer.width(line, size) <= maxWidth) {
            return line;
        }
        for (int end = line.length() - 1; end > 0; end--) {
            String cut = line.substring(0, end).stripTrailing() + ELLIPSIS;
            if (measurer.width(cut, size) <= maxWidth) {
                return cut;
            }
        }
        return ELLIPSIS;
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
