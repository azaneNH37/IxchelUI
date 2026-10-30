package com.azane.ixchel.core.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Greedy line breaking of a paragraph: runs of text in their own fonts and atomic boxes, laid on lines
 * that share a baseline.
 *
 * <p>Lines break after a space, and before and after a CJK character (Han, kana, Hangul, Bopomofo,
 * CJK and fullwidth punctuation) or an atomic box, except before a character that may not start a line
 * (closing brackets and punctuation, small kana, the prolonged sound mark) and after one that may not
 * end it (opening brackets). Never inside a {@code nowrap} run. A word wider than the limit gets a line
 * of its own and overflows; words are never split. Spaces at the start of a line are dropped, spaces at
 * its end hang past it and are not drawn.
 *
 * <p>Each line is as tall as the tallest of its pieces and the strut (an empty run in the paragraph's
 * own font), aligned on their baselines; an atomic box's baseline is given with it.
 */
public final class TextShaper {
    public static final String ELLIPSIS = "\u2026";

    /** Part of a paragraph's content. */
    public sealed interface Item permits Run, Box {
    }

    /** Text in one font, whitespace collapsed to single spaces; {@code nowrap}: no line breaks inside it. */
    public record Run(String text, float size, boolean bold, boolean nowrap) implements Item {
    }

    /** An atomic box: the width of its margin box and how far that reaches above and below the baseline. */
    public record Box(float width, float ascent, float descent) implements Item {
    }

    /**
     * The part of {@link Item} {@code item} on a line, {@code x} from the line's start: {@code text} is the
     * run's characters there, {@code null} for a box.
     */
    public record Piece(int item, String text, float x, float width) {
    }

    /** {@code baseline} is measured from the line's top. */
    public record Line(List<Piece> pieces, float width, float height, float baseline) {
    }

    /** {@code width} is the widest line's. */
    public record Paragraph(List<Line> lines, float width, float height) {
    }

    private static final Paragraph EMPTY = new Paragraph(List.of(), 0, 0);
    /** Rounding slack: a paragraph laid out at the width it measured must not wrap. */
    private static final float EPSILON = 1e-3f;
    /** Changing texts (counters) would otherwise grow the caches without bound. */
    private static final int MAX_CACHE = 2048;
    private static final String NO_START = "!%),.:;?]}\u00A2\u00B0\u2019\u201D\u2030\u2032\u2033\u2103\u2025\u2026"
            + "\u3001\u3002\u3003\u3005\u3009\u300B\u300D\u300F\u3011\u3015\u3017\u3019\u301B\u301C\u301F"
            + "\u309B\u309C\u309D\u309E\u30A0\u30FB\u30FC\u30FD\u30FE"
            + "\u3041\u3043\u3045\u3047\u3049\u3063\u3083\u3085\u3087\u308E\u3095\u3096"
            + "\u30A1\u30A3\u30A5\u30A7\u30A9\u30C3\u30E3\u30E5\u30E7\u30EE\u30F5\u30F6"
            + "\u31F0\u31F1\u31F2\u31F3\u31F4\u31F5\u31F6\u31F7\u31F8\u31F9\u31FA\u31FB\u31FC\u31FD\u31FE\u31FF"
            + "\uFF01\uFF05\uFF09\uFF0C\uFF0E\uFF1A\uFF1B\uFF1F\uFF3D\uFF5D\uFF61\uFF63\uFF64\uFF65\uFF70";
    private static final String NO_END = "([{\u00A3\u00A5\u2018\u201C\u3008\u300A\u300C\u300E\u3010\u3014\u3016\u3018\u301A\u301D"
            + "\uFF04\uFF08\uFF3B\uFF5B\uFF62\uFFE1\uFFE5";

    private static final byte TEXT = 0;
    private static final byte SPACE = 1;
    private static final byte BOX = 2;

    /** An unbreakable piece of one item: characters {@code [start, end)} of a run, or a whole box. */
    private record Unit(int item, int start, int end, float width, byte kind, boolean breakBefore) {
    }

    private record Key(List<Item> items, float strut, float maxWidth) {
    }

    private final TextMeasurer measurer;
    private final Map<List<Item>, List<Unit>> units = new HashMap<>();
    private final Map<Key, Paragraph> paragraphs = new HashMap<>();

    public TextShaper(TextMeasurer measurer) {
        this.measurer = measurer;
    }

    /**
     * Breaks {@code items} into lines no wider than {@code maxWidth} where it can;
     * {@link Float#POSITIVE_INFINITY} for no wrapping. {@code strut} is the paragraph's own font size.
     */
    public Paragraph layout(List<Item> items, float strut, float maxWidth) {
        Key key = new Key(items, strut, maxWidth);
        Paragraph p = paragraphs.get(key);
        if (p == null) {
            if (paragraphs.size() >= MAX_CACHE) {
                paragraphs.clear();
            }
            p = wrap(items, units(items), strut, maxWidth);
            paragraphs.put(key, p);
        }
        return p;
    }

    /** The widest piece that cannot be broken: the narrowest the paragraph gets without overflowing. */
    public float minContentWidth(List<Item> items) {
        float max = 0;
        float word = 0;
        for (Unit u : units(items)) {
            if (u.breakBefore() || u.kind() == SPACE) {
                word = 0;
            }
            if (u.kind() != SPACE) {
                word += u.width();
                max = Math.max(max, word);
            }
        }
        return max;
    }

    public float lineHeight(float size) {
        return measurer.lineHeight(size);
    }

    public float baseline(float size) {
        return measurer.baseline(size);
    }

    /** The advance of {@code text} on one line, unwrapped. */
    public float width(String text, float size, boolean bold) {
        return measurer.width(text, size, bold);
    }

    /**
     * {@code line}'s pieces cut to fit {@code maxWidth} together with an ellipsis in the font of the text
     * it follows (trailing spaces dropped); the pieces themselves when the line fits. A box that does not
     * fit is dropped whole; when not even the ellipsis fits it is returned alone, for the clip to cut.
     */
    public List<Piece> ellipsize(Line line, List<Item> items, float maxWidth) {
        if (line.width() <= maxWidth) {
            return line.pieces();
        }
        List<Piece> out = new ArrayList<>();
        int font = -1;
        for (Piece p : line.pieces()) {
            if (p.text() == null) {
                if (font >= 0 && p.x() + p.width() + ellipsis(items, font) <= maxWidth) {
                    out.add(p);
                    continue;
                }
                break;
            }
            font = p.item();
            Run r = (Run) items.get(font);
            float e = ellipsis(items, font);
            if (p.x() + p.width() + e <= maxWidth) {
                out.add(p);
                continue;
            }
            for (int end = p.text().length() - 1; end > 0; end--) {
                String cut = p.text().substring(0, end).stripTrailing();
                float w = measurer.width(cut, r.size(), r.bold());
                if (!cut.isEmpty() && p.x() + w + e <= maxWidth) {
                    out.add(new Piece(font, cut, p.x(), w));
                    break;
                }
            }
            break;
        }
        if (font < 0) {
            return out;
        }
        float x = 0;
        if (!out.isEmpty()) {
            Piece last = out.get(out.size() - 1);
            if (last.text() != null && last.text().endsWith(" ")) {
                Run r = (Run) items.get(last.item());
                String stripped = last.text().stripTrailing();
                last = new Piece(last.item(), stripped, last.x(), measurer.width(stripped, r.size(), r.bold()));
                out.set(out.size() - 1, last);
            }
            x = last.x() + last.width();
        }
        out.add(new Piece(font, ELLIPSIS, x, ellipsis(items, font)));
        return out;
    }

    /** Fonts reloaded: cached widths are stale. */
    public void invalidate() {
        units.clear();
        paragraphs.clear();
    }

    private float ellipsis(List<Item> items, int run) {
        Run r = (Run) items.get(run);
        return measurer.width(ELLIPSIS, r.size(), r.bold());
    }

    private List<Unit> units(List<Item> items) {
        List<Unit> u = units.get(items);
        if (u == null) {
            if (units.size() >= MAX_CACHE) {
                units.clear();
            }
            u = split(items);
            units.put(items, u);
        }
        return u;
    }

    private Paragraph wrap(List<Item> items, List<Unit> units, float strut, float maxWidth) {
        if (units.isEmpty()) {
            return EMPTY;
        }
        List<Line> lines = new ArrayList<>();
        float widest = 0;
        float height = 0;
        int n = units.size();
        int i = 0;
        while (true) {
            while (i < n && units.get(i).kind() == SPACE) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            int candidate = -1;
            float w = 0;
            int j = start;
            for (; j < n; j++) {
                Unit u = units.get(j);
                if (j > start && u.breakBefore()) {
                    candidate = j;
                }
                if (u.kind() != SPACE && w + u.width() > maxWidth + EPSILON && candidate > start) {
                    break;
                }
                w += u.width();
            }
            int end = j < n ? candidate : n;
            Line line = line(items, units, start, end, strut);
            lines.add(line);
            widest = Math.max(widest, line.width());
            height += line.height();
            i = end;
        }
        return new Paragraph(List.copyOf(lines), widest, height);
    }

    /** The line of units {@code [start, end)}, trailing spaces dropped; consecutive units of one run form one piece. */
    private Line line(List<Item> items, List<Unit> units, int start, int end, float strut) {
        while (end > start && units.get(end - 1).kind() == SPACE) {
            end--;
        }
        float ascent = measurer.baseline(strut);
        float descent = measurer.lineHeight(strut) - ascent;
        List<Piece> pieces = new ArrayList<>();
        float x = 0;
        int k = start;
        while (k < end) {
            Unit first = units.get(k);
            float w = first.width();
            int last = k;
            if (first.kind() != BOX) {
                while (last + 1 < end && units.get(last + 1).item() == first.item()) {
                    last++;
                    w += units.get(last).width();
                }
            }
            Item item = items.get(first.item());
            if (item instanceof Run r) {
                pieces.add(new Piece(first.item(), r.text().substring(first.start(), units.get(last).end()), x, w));
                float a = measurer.baseline(r.size());
                ascent = Math.max(ascent, a);
                descent = Math.max(descent, measurer.lineHeight(r.size()) - a);
            } else {
                Box b = (Box) item;
                pieces.add(new Piece(first.item(), null, x, w));
                ascent = Math.max(ascent, b.ascent());
                descent = Math.max(descent, b.descent());
            }
            x += w;
            k = last + 1;
        }
        return new Line(List.copyOf(pieces), x, ascent + descent, ascent);
    }

    /** Splits the items into units, each knowing whether a line may break before it. */
    private List<Unit> split(List<Item> items) {
        List<Unit> out = new ArrayList<>();
        // What precedes the next unit: its kind (-1 at the start), last character and nowrap.
        int prevKind = -1;
        int prevChar = 0;
        boolean prevNowrap = false;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item instanceof Box b) {
                boolean brk = prevKind == SPACE ? !prevNowrap
                        : prevKind == TEXT ? !noEnd(prevChar) : prevKind == BOX;
                out.add(new Unit(i, 0, 0, b.width(), BOX, brk));
                prevKind = BOX;
                prevNowrap = false;
                continue;
            }
            Run r = (Run) item;
            String s = r.text();
            int chunk = -1;
            for (int k = 0; k <= s.length(); ) {
                int c = k < s.length() ? s.codePointAt(k) : -1;
                boolean boundary = c == -1 || c == ' ' || chunk >= 0 && (cjk(c) || cjk(s.codePointBefore(k)));
                if (boundary && chunk >= 0) {
                    int first = s.codePointAt(chunk);
                    boolean brk = breakBefore(prevKind, prevChar, prevNowrap, first, r.nowrap());
                    out.add(new Unit(i, chunk, k, measurer.width(s.substring(chunk, k), r.size(), r.bold()), TEXT, brk));
                    prevKind = TEXT;
                    prevChar = s.codePointBefore(k);
                    prevNowrap = r.nowrap();
                    chunk = -1;
                }
                if (c == -1) {
                    break;
                }
                if (c == ' ') {
                    int e = k;
                    while (e < s.length() && s.charAt(e) == ' ') {
                        e++;
                    }
                    out.add(new Unit(i, k, e, measurer.width(s.substring(k, e), r.size(), r.bold()), SPACE, false));
                    prevKind = SPACE;
                    prevNowrap = r.nowrap();
                    k = e;
                    continue;
                }
                if (chunk < 0) {
                    chunk = k;
                }
                k += Character.charCount(c);
            }
        }
        return List.copyOf(out);
    }

    private static boolean breakBefore(int prevKind, int prevChar, boolean prevNowrap, int c, boolean nowrap) {
        if (prevKind == -1) {
            return false;
        }
        if (prevKind == SPACE) {
            return !prevNowrap;
        }
        if (prevNowrap && nowrap || noStart(c)) {
            return false;
        }
        if (prevKind == BOX) {
            return true;
        }
        return (cjk(prevChar) || cjk(c)) && !noEnd(prevChar);
    }

    private static boolean noStart(int c) {
        return c < 0x10000 && NO_START.indexOf(c) >= 0;
    }

    private static boolean noEnd(int c) {
        return c < 0x10000 && NO_END.indexOf(c) >= 0;
    }

    /** Characters a line may break around without a space: ideographs, kana, Hangul and CJK punctuation. */
    static boolean cjk(int c) {
        if (c < 0x2E80) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO) {
            return true;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || block == Character.UnicodeBlock.VERTICAL_FORMS;
    }
}
