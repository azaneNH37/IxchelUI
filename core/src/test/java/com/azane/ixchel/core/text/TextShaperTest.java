package com.azane.ixchel.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextShaperTest {
    /** One pixel per character (two bold), 10px lines with the baseline at 8. */
    private static final TextMeasurer ONE_PER_CHAR = new TextMeasurer() {
        @Override
        public boolean hasFont(String font) {
            return true;
        }

        @Override
        public float width(String text, float size, boolean bold, String font) {
            return text.length() * (bold ? 2 : 1);
        }

        @Override
        public float lineHeight(float size) {
            return 10;
        }

        @Override
        public float baseline(float size) {
            return 8;
        }
    };

    private static List<String> lines(TextShaper.Paragraph p) {
        return p.lines().stream()
                .map(l -> l.pieces().stream().map(x -> x.text() == null ? "[]" : x.text()).collect(Collectors.joining("|")))
                .toList();
    }

    private static TextShaper.Run run(String text) {
        return new TextShaper.Run(text, 9, false, false, 10, null, 0);
    }

    private static TextShaper.Run spaced(String text, float spacing) {
        return new TextShaper.Run(text, 9, false, false, 10, null, spacing);
    }

    @Test
    void breaksAtSpacesAndGivesOverlongWordsTheirOwnLine() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(run("ab cd extraordinary e"));
        TextShaper.Paragraph p = shaper.layout(items, 9, 10, 5);
        assertEquals(List.of("ab cd", "extraordinary", "e"), lines(p));
        assertEquals(13, p.width());
        assertEquals(30, p.height());
        assertEquals(List.of("ab cd extraordinary e"), lines(shaper.layout(items, 9, 10, Float.POSITIVE_INFINITY)));
        assertEquals(13, shaper.minContentWidth(items));
    }

    @Test
    void breaksBetweenCjkCharactersButNotBeforeClosingPunctuation() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        assertEquals(List.of("今天天气很", "好。"), lines(shaper.layout(List.of(run("今天天气很好。")), 9, 10, 6)));
        assertEquals(2, shaper.minContentWidth(List.of(run("今天天气很好。"))));
    }

    @Test
    void runsBreakAcrossFontsAndNowrapRunsStayWhole() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(run("aa "), new TextShaper.Run("bb cc", 9, true, true, 10, null, 0), run(" dd"));
        // the bold nowrap run is 10px wide and cannot break inside
        assertEquals(List.of("aa", "bb cc", "dd"), lines(shaper.layout(items, 9, 10, 8)));
        assertEquals(List.of("aa |bb cc| dd"), lines(shaper.layout(items, 9, 10, 100)));
    }

    @Test
    void aLineBreakEndsTheLineEvenWhenItFitsAndTwoMakeAnEmptyLine() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        String br = String.valueOf(TextShaper.LINE_BREAK);
        List<TextShaper.Item> items = List.of(run("ab" + br + br + "cd ef"));
        assertEquals(List.of("ab", "", "cd ef"), lines(shaper.layout(items, 9, 10, 100)));
        assertEquals(2, shaper.minContentWidth(List.of(run("ab" + br + "cd"))));
    }

    @Test
    void atomicBoxSitsOnTheBaselineAndRaisesTheLine() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        TextShaper.Paragraph p = shaper.layout(List.of(run("ab"), new TextShaper.Box(3, 20, 0), run("cd")), 9, 10, 100);
        TextShaper.Line line = p.lines().get(0);
        assertEquals(20, line.baseline());
        assertEquals(22, line.height(), "the strut's 2px descent stays below the baseline");
        assertEquals(List.of(0f, 2f, 5f), line.pieces().stream().map(TextShaper.Piece::x).toList());
        assertEquals(7, line.width());
    }

    @Test
    void ellipsizeCutsTheLineAndDropsTrailingSpaces() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(run("ab cdef"));
        TextShaper.Line line = shaper.layout(items, 9, 10, Float.POSITIVE_INFINITY).lines().get(0);
        List<TextShaper.Piece> cut = shaper.ellipsize(line, items, 4);
        assertEquals(List.of("ab", TextShaper.ELLIPSIS), cut.stream().map(TextShaper.Piece::text).toList());
        assertEquals(2, cut.get(1).x());
    }

    @Test
    void letterSpacingFollowsEveryCharacterInMeasuringAndBreaking() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(spaced("ab cd", 2));
        assertEquals(15, shaper.layout(items, 9, 10, Float.POSITIVE_INFINITY).width(), "5 characters of 1px, each followed by 2px");
        TextShaper.Paragraph p = shaper.layout(items, 9, 10, 10);
        assertEquals(List.of("ab", "cd"), lines(p));
        assertEquals(6, p.width(), "the dropped space takes its spacing with it");
        assertEquals(List.of(0f, 6f), shaper.layout(List.of(spaced("ab", 2), run("cd")), 9, 10, 100).lines().get(0).pieces()
                .stream().map(TextShaper.Piece::x).toList());
    }

    @Test
    void letterSpacedCjkStillBreaksBetweenCharactersAndKeepsClosingPunctuation() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(spaced("今天天气很好。", 1));
        assertEquals(List.of("今天天", "气很", "好。"), lines(shaper.layout(items, 9, 10, 6)));
        assertEquals(4, shaper.minContentWidth(items), "好。 cannot break and is two spaced characters");
    }

    @Test
    void ellipsisIsPlacedAfterTheSpacedText() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        List<TextShaper.Item> items = List.of(spaced("ab cdef", 1));
        TextShaper.Line line = shaper.layout(items, 9, 10, Float.POSITIVE_INFINITY).lines().get(0);
        List<TextShaper.Piece> cut = shaper.ellipsize(line, items, 8);
        assertEquals(List.of("ab", TextShaper.ELLIPSIS), cut.stream().map(TextShaper.Piece::text).toList());
        assertEquals(4, cut.get(1).x());
        assertEquals(2, cut.get(1).width());
    }
}
