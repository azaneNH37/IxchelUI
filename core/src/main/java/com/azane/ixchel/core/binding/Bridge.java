package com.azane.ixchel.core.binding;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** The one {@link ModelAccess}, kept here so the members it reaches stay package-private. */
final class Bridge implements ModelAccess {
    private static final AtomicBoolean CLAIMED = new AtomicBoolean();

    private Bridge() {
    }

    static ModelAccess claim() {
        if (!CLAIMED.compareAndSet(false, true)) {
            throw new IllegalStateException("ModelAccess is already claimed");
        }
        return new Bridge();
    }

    @Override
    public <T> Cell<T> held(T initial) {
        return new Held<>(null, initial);
    }

    @Override
    public <T> Value<T> state(ViewModel owner) {
        return new Value<>(owner, null);
    }

    @Override
    public <T> void replace(Cell<T> cell, T value) {
        if (cell instanceof Value<T> v) {
            v.replace(value);
        } else {
            ((Held<T>) cell).replace(value);
        }
    }

    @Override
    public <T> Cell<T> derived(Cell<?>[] inputs, Function<Object[], T> compute) {
        return new Derived<>(inputs, compute);
    }

    @Override
    public <T> Cell<T> flat(Cell<? extends Cell<T>> outer) {
        return new Flat<>(outer);
    }

    @Override
    public <T> Cell<T> current(Cell<T> flat) {
        return ((Flat<T>) flat).current();
    }

    @Override
    public Object read(ViewModel model, ModelShape.Member member) {
        Field f = ModelShape.of(model.getClass()).fields.get(member.name());
        try {
            f.setAccessible(true);
            return f.get(model);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public <A> Action<A> idle(ViewModel owner) {
        return new Action<>(owner, a -> { }, null);
    }

    @Override
    public <T> void wire(Param<T> port, Cell<? extends T> cell) {
        port.wire(cell);
    }

    @Override
    public <T> void wire(TwoWay<T> port, Cell<T> cell, Consumer<T> writer) {
        port.wire(cell, writer);
    }

    @Override
    public <T> void wire(Event<T> port, Consumer<? super T> listener) {
        port.wire(listener);
    }

    @Override
    public int unwiredPorts(ViewModel model) {
        return model.unwiredPorts();
    }

    @Override
    public void nextPass() {
        Feed.nextPass();
    }

    @Override
    public boolean poll(ViewModel model) {
        return model.poll();
    }
}
