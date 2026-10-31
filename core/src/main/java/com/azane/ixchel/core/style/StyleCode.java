package com.azane.ixchel.core.style;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of custom properties and {@code var()}, written in a declaration or found when it is substituted. */
public enum StyleCode implements DiagCode {
    INVALID_VAR("css/invalid-var", Severity.ERROR,
            "A var() is not of the form var(--name) or var(--name, fallback)."),
    UNDEFINED_VAR("style/undefined-var", Severity.ERROR,
            "A declaration uses a custom property that no matching rule or ancestor defines, and its var() has no fallback."),
    INVALID_AFTER_VAR("style/invalid-after-var", Severity.ERROR,
            "A declaration's value is invalid for its property once its var() references are substituted.");

    private final String id;
    private final Severity severity;
    private final String summary;

    StyleCode(String id, Severity severity, String summary) {
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
