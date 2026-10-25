package com.azane.ixchel.core.diag;

public record Diagnostic(Severity severity, String message, SourcePos pos) {
    public enum Severity { ERROR, WARNING }

    /** {@code file:line:col: error: message}, the form editors and terminals link to. */
    @Override
    public String toString() {
        return pos + ": " + (severity == Severity.ERROR ? "error" : "warning") + ": " + message;
    }
}
