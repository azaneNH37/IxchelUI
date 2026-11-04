package com.azane.ixchel.core.diag;

/**
 * A location in a view or stylesheet; {@code line} and {@code column} are 1-based.
 */
public record SourcePos(String file, int line, int column) {
    /** For nodes created from code rather than parsed from a file. */
    public static final SourcePos UNKNOWN = new SourcePos("<unknown>", 0, 0);

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
