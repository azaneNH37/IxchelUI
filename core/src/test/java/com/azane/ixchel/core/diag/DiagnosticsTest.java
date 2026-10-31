package com.azane.ixchel.core.diag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticsTest {
    private enum TestCode implements DiagCode {
        BAD_TAG("test/bad-tag", Diagnostic.Severity.ERROR),
        LITERAL("test/literal", Diagnostic.Severity.WARNING);

        private final String id;
        private final Diagnostic.Severity severity;

        TestCode(String id, Diagnostic.Severity severity) {
            this.id = id;
            this.severity = severity;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Diagnostic.Severity severity() {
            return severity;
        }

        @Override
        public String summary() {
            return id;
        }
    }

    /** The one place the rendered form is pinned; every other test asserts codes and positions. */
    @Test
    void failureListsEveryEntryWithItsCodeLocationAndHint() {
        Diagnostics d = new Diagnostics();
        d.report(TestCode.BAD_TAG, new SourcePos("hud.html", 2, 5), "unknown tag <dvi>", "did you mean <div>?");
        d.report(TestCode.LITERAL, new SourcePos("hud.html", 3, 7), "literal text");
        d.report(TestCode.BAD_TAG, new SourcePos("hud.css", 10, 3), "unknown tag <x>");

        UiLoadException e = assertThrows(UiLoadException.class, d::throwIfErrors);
        assertEquals("""
                2 errors
                hud.html:2:5: error[test/bad-tag]: unknown tag <dvi>
                  hint: did you mean <div>?
                hud.html:3:7: warning[test/literal]: literal text
                hud.css:10:3: error[test/bad-tag]: unknown tag <x>""", e.getMessage());
        assertEquals(d.entries(), e.diagnostics());
    }
}
