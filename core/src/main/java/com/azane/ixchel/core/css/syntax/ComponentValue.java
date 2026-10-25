package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/**
 * CSS Syntax Level 3 component values: a preserved {@link Token}, a function, or a simple block.
 * Comments never appear; whitespace survives as {@link Token.Kind#WHITESPACE} tokens.
 */
public sealed interface ComponentValue permits Token, ComponentValue.Function, ComponentValue.Block {
    SourcePos pos();

    /** {@code name(args)}; {@code name} is as written, compare case-insensitively. */
    record Function(String name, List<ComponentValue> args, SourcePos pos) implements ComponentValue {
    }

    /** {@code open} is one of {@code LBRACKET}, {@code LPAREN}, {@code LBRACE}. */
    record Block(Token.Kind open, List<ComponentValue> contents, SourcePos pos) implements ComponentValue {
    }
}
