package com.azane.ixchel.core.binding.expr;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of a directive's expression or statements themselves: their syntax, operator and pipe types. */
public enum ExprCode implements DiagCode {
    SYNTAX("binding/expression-syntax", Severity.ERROR,
            "A directive's expression or statement list is malformed."),
    OPERAND_TYPE("binding/operand-type", Severity.ERROR,
            "An operator is applied to a value of a type it does not take."),
    UNRELATED_TYPES("binding/unrelated-types", Severity.ERROR,
            "Two values that must share a type, the sides of == or != or the branches of ?:, have unrelated types."),
    UNKNOWN_PIPE("binding/unknown-pipe", Severity.ERROR,
            "A '|' names a pipe that is not built in."),
    PIPE_TYPE("binding/pipe-type", Severity.ERROR,
            "A pipe gets an input of a type it does not take, or arguments of the wrong number or type.");

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
