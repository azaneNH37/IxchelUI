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

    /**
     * The value {@code p} of the way from {@code from} to {@code to}, clamped to the values the
     * syntax accepts; {@code p} leaves {@code [0, 1]} under an overshooting easing. {@code null} when
     * the two values cannot be interpolated (different units, {@code auto}, keywords).
     */
    default T interpolate(T from, T to, float p) {
        return null;
    }
}
