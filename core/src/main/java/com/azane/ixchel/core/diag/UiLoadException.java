package com.azane.ixchel.core.diag;

import java.util.List;

/** A template or stylesheet failed validation; {@link #diagnostics()} keeps the structured entries. */
public final class UiLoadException extends RuntimeException {
    private final List<Diagnostic> diagnostics;

    public UiLoadException(String message, List<Diagnostic> diagnostics) {
        super(message);
        this.diagnostics = diagnostics;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
