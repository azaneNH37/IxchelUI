package com.azane.ixchel.core.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewTest {
    @Test
    void aStarMatchesWithinOnePathSegmentButNotAcrossASlash() {
        assertTrue(UiView.Ids.matches("sample:ui/guide/*.html", "sample:ui/guide/intro.html"));
        assertFalse(UiView.Ids.matches("sample:ui/guide/*.html", "sample:ui/guide/deep/intro.html"));
    }

    @Test
    void theNamespaceMustMatch() {
        assertFalse(UiView.Ids.matches("sample:ui/*.html", "other:ui/a.html"));
    }

    @UiView("sample:ui/guide/*.html")
    static final class PatternModel extends ViewModel {
    }

    @UiView({"sample:ui/a.html", "sample:ui/b.html"})
    static final class TwoIdsModel extends ViewModel {
    }

    @Test
    void singleRejectsAPattern() {
        assertThrows(IllegalArgumentException.class, () -> UiView.Ids.single(PatternModel.class));
    }

    @Test
    void singleRejectsSeveralIds() {
        assertThrows(IllegalArgumentException.class, () -> UiView.Ids.single(TwoIdsModel.class));
    }
}
