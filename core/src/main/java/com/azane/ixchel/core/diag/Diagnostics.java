package com.azane.ixchel.core.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects problems found while loading one document, so a single load reports all of them at once.
 */
public final class Diagnostics {
    private final List<Diagnostic> entries = new ArrayList<>();
    private int errorCount;

    public void error(SourcePos pos, String message) {
        entries.add(new Diagnostic(Diagnostic.Severity.ERROR, message, pos));
        errorCount++;
    }

    public void warning(SourcePos pos, String message) {
        entries.add(new Diagnostic(Diagnostic.Severity.WARNING, message, pos));
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public List<Diagnostic> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Throws if any error was reported; the message lists every entry, warnings included. */
    public void throwIfErrors() {
        if (errorCount == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(errorCount).append(errorCount == 1 ? " error" : " errors");
        for (Diagnostic d : entries) {
            sb.append('\n').append(d);
        }
        throw new UiLoadException(sb.toString(), List.copyOf(entries));
    }
}
