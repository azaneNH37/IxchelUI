package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/** Parses a declaration value; returns {@code null} after reporting an error if the value is invalid. */
@FunctionalInterface
public interface Syntax<T> {
    /**
     * @param value declaration value with leading/trailing whitespace removed, never empty
     * @param pos   declaration position, for errors that concern the whole value
     */
    T parse(List<ComponentValue> value, SourcePos pos, Diagnostics diag);
}
