package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/**
 * A qualified rule: {@code prelude} is the raw selector text as component values, trimmed of
 * whitespace.
 */
public record Rule(List<ComponentValue> prelude, List<Declaration> declarations, SourcePos pos) {

    /**
     * {@code name} is lower-cased (custom properties {@code --x} keep their case); {@code value} has
     * leading/trailing whitespace and the {@code !important} marker removed.
     */
    public record Declaration(String name, List<ComponentValue> value, boolean important, SourcePos pos) {
    }
}
