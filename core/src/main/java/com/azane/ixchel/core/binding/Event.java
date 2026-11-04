package com.azane.ixchel.core.binding;

import java.util.function.Consumer;

/**
 * A component's output, made with {@link ViewModel#event()} and stored in a field named after the
 * {@code <component events>} entry: {@link #emit} runs the user's {@code on:<name>} statements, which
 * see the payload as {@code $event}. Declare {@code Event<Void>} for an event without payload. Without
 * a listener, or once its model is closed, emitting does nothing. Client thread only.
 */
public final class Event<T> {
    private final ViewModel owner;
    private Consumer<? super T> listener;

    Event(ViewModel owner) {
        this.owner = owner;
    }

    public void emit(T payload) {
        if (listener != null && !owner.scope().isClosed()) {
            listener.accept(payload);
        }
    }

    /** Emits an event without payload. */
    public void emit() {
        emit(null);
    }

    void wire(Consumer<? super T> handler) {
        listener = handler;
        owner.wired(this);
    }
}
