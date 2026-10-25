package com.azane.ixchel.core.diag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticsTest {
    @Test
    void failureReportsEveryEntryWithItsLocation() {
        Diagnostics d = new Diagnostics();
        d.error(new SourcePos("hud.html", 2, 5), "unknown tag <dvi>");
        d.warning(new SourcePos("hud.html", 3, 7), "literal text");
        d.error(new SourcePos("hud.css", 10, 3), "unsupported property 'float'");

        UiLoadException e = assertThrows(UiLoadException.class, d::throwIfErrors);
        assertEquals("""
                2 errors
                hud.html:2:5: error: unknown tag <dvi>
                hud.html:3:7: warning: literal text
                hud.css:10:3: error: unsupported property 'float'""", e.getMessage());
        assertEquals(3, e.diagnostics().size());
    }
}
