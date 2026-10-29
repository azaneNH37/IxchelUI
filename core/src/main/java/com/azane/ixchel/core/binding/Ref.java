package com.azane.ixchel.core.binding;

import java.lang.reflect.Type;

/**
 * A resolved expression: the cell to read, its declared type, and how an assignment or a two-way
 * binding writes it.
 */
record Ref(Cell<?> cell, Type type, Write write) {
    enum Write {
        /** Read-only: a source, a derived value, an expression, a row. */
        NONE,
        /** A local state {@link Value}: {@code cell} itself is set. */
        VALUE,
        /** A row component of type {@code Value<T>}: the {@link Flat}'s current inner value is set. */
        ROW
    }

    Ref(Cell<?> cell, Type type) {
        this(cell, type, Write.NONE);
    }
}
