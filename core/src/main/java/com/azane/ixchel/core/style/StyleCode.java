package com.azane.ixchel.core.style;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/**
 * Problems of custom properties and {@code var()}, written in a declaration or found when it is
 * substituted, and of the library's names a host file uses.
 */
public enum StyleCode implements DiagCode {
    INVALID_VAR("css/invalid-var", Severity.ERROR,
            "A var() is not of the form var(--name) or var(--name, fallback)."),
    UNDEFINED_VAR("style/undefined-var", Severity.ERROR,
            "A declaration uses a custom property that no matching rule or ancestor defines, and its var() has no fallback."),
    INVALID_AFTER_VAR("style/invalid-after-var", Severity.ERROR,
            "A declaration's value is invalid for its property once its var() references are substituted."),
    UNKNOWN_LIBRARY_CLASS("style/unknown-library-class", Severity.WARNING,
            "A host selector names an .ix-* class no library css file of the document uses; ix- names belong to the library."),
    UNKNOWN_LIBRARY_TOKEN("style/unknown-library-token", Severity.WARNING,
            "A host file sets or reads an --ix-* property no library css file of the document sets; ix- names belong to the library."),
    COMPONENT_ROOT_TOKEN("style/component-root-token", Severity.WARNING,
            "A rule of the component layer sets a custom property on :root, which is the whole page; the page's stylesheet sets tokens.");

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
