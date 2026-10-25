package com.azane.ixchel.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextShaperTest {
    private static final TextMeasurer ONE_PER_CHAR = new TextMeasurer() {
        @Override
        public float width(String text, float size) {
            return text.length();
        }

        @Override
        public float lineHeight(float size) {
            return 10;
        }
    };

    private static List<String> lines(TextShaper.Shaped s) {
        return s.lines().stream().map(TextShaper.Line::text).toList();
    }

    @Test
    void breaksAtSpacesAndGivesOverlongWordsTheirOwnLine() {
        TextShaper shaper = new TextShaper(ONE_PER_CHAR);
        TextShaper.Shaped s = shaper.shape("ab cd extraordinary e", 9, 5);
        assertEquals(List.of("ab cd", "extraordinary", "e"), lines(s));
        assertEquals(13, s.width());
        assertEquals(30, s.height());
        assertEquals(List.of("ab cd extraordinary e"), lines(shaper.shape("ab cd extraordinary e", 9, Float.POSITIVE_INFINITY)));
    }
}
