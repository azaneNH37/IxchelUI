package com.azane.ixchel.core.link;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of the files a view or a css file links. */
public enum LinkCode implements DiagCode {
    UNREADABLE_FILE("link/unreadable-file", Severity.ERROR,
            "A linked file's href is invalid or the file cannot be read."),
    FILE_NOT_FOUND("link/file-not-found", Severity.ERROR,
            "A linked file does not exist."),
    WRONG_KIND("link/wrong-kind", Severity.ERROR,
            "A linked file is of a kind the linking file cannot link: a view links one @stylesheet and <component> files, a css file links @partial files and effects."),
    CYCLE("link/cycle", Severity.ERROR,
            "A component file or a partial links back to itself through other files."),
    DUPLICATE_COMPONENT("link/duplicate-component", Severity.ERROR,
            "Two linked component files declare the same component name.");

    private final String id;
    private final Severity severity;
    private final String summary;

    LinkCode(String id, Severity severity, String summary) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String summary() {
        return summary;
    }
}
