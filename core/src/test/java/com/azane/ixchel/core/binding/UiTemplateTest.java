package com.azane.ixchel.core.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTemplateTest {
    @Test
    void aStarMatchesWithinOnePathSegmentButNotAcrossASlash() {
        assertTrue(UiTemplate.Ids.matches("sample:ui/guide/*.html", "sample:ui/guide/intro.html"));
        assertFalse(UiTemplate.Ids.matches("sample:ui/guide/*.html", "sample:ui/guide/deep/intro.html"));
    }

    @Test
    void theNamespaceMustMatch() {
        assertFalse(UiTemplate.Ids.matches("sample:ui/*.html", "other:ui/a.html"));
    }

    @UiTemplate("sample:ui/guide/*.html")
    static final class PatternView extends ViewModel {
    }

    @UiTemplate({"sample:ui/a.html", "sample:ui/b.html"})
    static final class TwoIdsView extends ViewModel {
    }

    @Test
    void singleRejectsAPattern() {
        assertThrows(IllegalArgumentException.class, () -> UiTemplate.Ids.single(PatternView.class));
    }

    @Test
    void singleRejectsSeveralIds() {
        assertThrows(IllegalArgumentException.class, () -> UiTemplate.Ids.single(TwoIdsView.class));
    }
}
