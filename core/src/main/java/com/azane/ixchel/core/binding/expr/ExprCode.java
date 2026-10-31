package com.azane.ixchel.core.binding.expr;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of a directive's expression or statements themselves: their syntax and operator types. */
public enum ExprCode implements DiagCode {
    SYNTAX("binding/expression-syntax", Severity.ERROR,
            "A directive's expression or statement list is malformed."),
    OPERAND_TYPE("binding/operand-type", Severity.ERROR,
            "An operator is applied to a value of a type it does not take."),
    UNRELATED_TYPES("binding/unrelated-types", Severity.ERROR,
            "Two values that must share a type, the sides of == or != or the branches of ?:, have unrelated types.");

    private final String id;
    private final Severity severity;
    private final String summary;

    ExprCode(String id, Severity severity, String summary) {
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
