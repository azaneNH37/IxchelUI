package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The lifetime of a view model: owns its subscriptions and the inbox through which other threads
 * hand work to the client thread. After {@link #close()} posted work is dropped, so a late reply
 * never touches a closed view.
 */
@HostApi
public final class Scope implements AutoCloseable {
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final List<AutoCloseable> owned = new ArrayList<>();
    private volatile boolean closed;

    Scope() {
    }

    /** Runs {@code work} on the client thread at the start of the next frame. Any thread. */
    public void post(Runnable work) {
        if (!closed) {
            inbox.add(work);
        }
    }

    /** Closes {@code resource} with this scope; closes it right away if the scope is already closed. Client thread. */
    public void own(AutoCloseable resource) {
        if (closed) {
            closeResource(resource);
        } else {
            owned.add(resource);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /** Drops pending posts and closes everything owned, last owned first. Idempotent. Client thread. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        inbox.clear();
        for (int i = owned.size() - 1; i >= 0; i--) {
            closeResource(owned.get(i));
        }
        owned.clear();
    }

    /** Runs posted work; returns whether there was any. */
    boolean drain() {
        boolean ran = false;
        Runnable r;
        while (!closed && (r = inbox.poll()) != null) {
            r.run();
            ran = true;
        }
        return ran;
    }

    private static void closeResource(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception e) {
            throw new IllegalStateException("closing " + c + " failed", e);
        }
    }
}
