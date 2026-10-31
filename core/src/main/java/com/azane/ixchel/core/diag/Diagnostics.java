package com.azane.ixchel.core.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects problems found while loading one document, so a single load reports all of them at once.
 */
public final class Diagnostics {
    private final List<Diagnostic> entries = new ArrayList<>();
    private final Diagnostics target;
    private final String note;
    private int errorCount;

    public Diagnostics() {
        this(null, "");
    }

    private Diagnostics(Diagnostics target, String note) {
        this.target = target;
        this.note = note;
    }

    /** A view reporting into this collection with {@code note} appended to every message. */
    public Diagnostics within(String note) {
        return new Diagnostics(this, note);
    }

    /** Reports a problem; its severity is {@code code}'s. */
    public void report(DiagCode code, SourcePos pos, String message) {
        report(code, pos, message, null);
    }

    /** Reports a problem with a fix suggestion ({@code null} for none); its severity is {@code code}'s. */
    public void report(DiagCode code, SourcePos pos, String message, String hint) {
        if (target != null) {
            target.report(code, pos, message + note, hint);
            return;
        }
        entries.add(new Diagnostic(code, message, pos, hint));
        if (code.severity() == Diagnostic.Severity.ERROR) {
            errorCount++;
        }
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public int errorCount() {
        return target != null ? target.errorCount() : errorCount;
    }

    public List<Diagnostic> entries() {
        return target != null ? target.entries() : Collections.unmodifiableList(entries);
    }

    /** Throws if any error was reported; the message lists every entry, warnings included. */
    public void throwIfErrors() {
        if (target != null) {
            target.throwIfErrors();
            return;
        }
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
