package com.azane.ixchel.core.diag;

/**
 * One reported problem.
 *
 * @param hint how to fix it, or {@code null}
 */
public record Diagnostic(DiagCode code, String message, SourcePos pos, String hint) {
    public enum Severity { ERROR, WARNING }

    public Severity severity() {
        return code.severity();
    }

    /**
     * {@code file:line:col: error[area/name]: message}, the form editors and terminals link to, with
     * {@code   hint: …} on the next line when there is a hint.
     */
    @Override
    public String toString() {
        String text = pos + ": " + (severity() == Severity.ERROR ? "error" : "warning")
                + "[" + code.id() + "]: " + message;
        return hint == null ? text : text + "\n  hint: " + hint;
    }
}
