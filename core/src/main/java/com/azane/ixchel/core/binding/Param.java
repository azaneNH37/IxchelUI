package com.azane.ixchel.core.binding;

/**
 * A component's read-only input, made with {@link ViewModel#param()} and stored in a field named after
 * the {@code <template params>} entry. It reads what the user passed, or the template's default. It is
 * wired after the constructor: {@link #get()} throws until then, while {@code derive} and suppliers,
 * which read later, can use it freely.
 */
public final class Param<T> extends Cell<T> {
    private final ViewModel owner;
    private Cell<? extends T> source;

    Param(ViewModel owner) {
        this.owner = owner;
    }

    @Override
    public T get() {
        if (source == null) {
            throw new IllegalStateException("a Param has no value in its model's constructor: read it in derive or a supplier");
        }
        return source.get();
    }

    @Override
    long version() {
        return source == null ? 0 : source.version();
    }

    void wire(Cell<? extends T> cell) {
        source = cell;
        owner.wired(this);
    }
}
