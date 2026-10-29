package com.azane.ixchel.core.binding;

/**
 * The value of the cell another cell holds: a row component of type {@code Value<T>} or
 * {@code Derived<T>} read through. Its version moves when the outer cell switches to another inner
 * cell or the inner cell's value changes, so a binding on {@code row.label} follows the row's own
 * cell without the list changing.
 */
final class Flat<T> extends Cell<T> {
    private final Cell<? extends Cell<T>> outer;
    private long outerSeen = -1;
    private Cell<T> inner;
    private long innerSeen = -1;
    private T value;
    private long version;

    Flat(Cell<? extends Cell<T>> outer) {
        this.outer = outer;
    }

    @Override
    public T get() {
        refresh();
        return value;
    }

    @Override
    long version() {
        refresh();
        return version;
    }

    /** The inner cell now; {@code null} while the outer value is {@code null}. */
    Cell<T> current() {
        refresh();
        return inner;
    }

    private void refresh() {
        long ov = outer.version();
        boolean stale = false;
        if (ov != outerSeen) {
            outerSeen = ov;
            Cell<T> next = outer.get();
            if (next != inner) {
                inner = next;
                innerSeen = -1;
                stale = true;
            }
        }
        long iv = inner == null ? 0 : inner.version();
        if (iv != innerSeen) {
            innerSeen = iv;
            stale = true;
        }
        if (stale) {
            value = inner == null ? null : inner.get();
            version++;
        }
    }
}
