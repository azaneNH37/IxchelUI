package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.TwoWay;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.expr.Expressions;

import java.lang.reflect.Type;

/**
 * A resolved expression: the cell to read, its declared type, and how an assignment or a two-way
 * binding writes it.
 */
record Ref(Cell<?> cell, Type type, Write write) implements Name, Expressions.Operand {
    enum Write {
        /** Read-only: a source, a derived value, an expression, a row, a parameter passed read-only. */
        NONE,
        /** A local state {@link Value} or a {@code Value} field: {@code cell} itself is set. */
        VALUE,
        /** A row component of type {@code Value<T>}: the flat cell's current inner value is set. */
        ROW,
        /** A {@link TwoWay} port: {@code cell} itself is set, which writes where the port leads. */
        PORT
    }

    Ref(Cell<?> cell, Type type) {
        this(cell, type, Write.NONE);
    }
}
