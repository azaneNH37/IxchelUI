package com.azane.ixchel.sample.network;

/**
 * The client's copy of one replicated object ({@link Replication#watch}): the latest snapshot, or
 * {@code null} before the first one and after the server ended the watch. Closing it stops watching.
 */
public final class Replica<V> implements AutoCloseable {
    private Runnable onClose = () -> {
    };
    private V value;
    private boolean closed;

    Replica() {
    }

    void onClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public V get() {
        return value;
    }

    @SuppressWarnings("unchecked")
    void set(Object value) {
        this.value = (V) value;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            onClose.run();
        }
    }
}
