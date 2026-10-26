package com.azane.ixchel.core.binding;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Something the user can do, taking at most one argument ({@code Void} for none). An action only
 * emits: new state comes back through sources, never from the action's result.
 *
 * <p>A synchronous action runs its handler and is done. An asynchronous one (built with
 * {@code task}) returns a {@link CompletableFuture}: {@link #pending()} is true until it completes,
 * triggering it again meanwhile is ignored, and a failure lands in {@link #error()}. Completion is
 * handed to the client thread through the model's {@link Scope}, so after the view closes it is dropped.
 */
public final class Action<A> {
    private final Scope scope;
    private final Consumer<A> sync;
    private final Function<A, CompletableFuture<?>> async;
    private final Value<Boolean> pending;
    private final Value<Throwable> error;

    Action(ViewModel owner, Consumer<A> sync, Function<A, CompletableFuture<?>> async) {
        this.scope = owner.scope();
        this.sync = sync;
        this.async = async;
        this.pending = new Value<>(owner, null, false);
        this.error = new Value<>(owner, null, null);
    }

    /** Whether an asynchronous run is in progress; always false for a synchronous action. */
    public Cell<Boolean> pending() {
        return pending;
    }

    /** Why the last asynchronous run failed, or {@code null}; cleared when the action runs again. */
    public Cell<Throwable> error() {
        return error;
    }

    /** Runs the action unless a previous asynchronous run is still pending. Client thread. */
    public void run(A argument) {
        if (scope.isClosed() || pending.get()) {
            return;
        }
        if (sync != null) {
            sync.accept(argument);
            return;
        }
        error.set(null);
        CompletableFuture<?> f = async.apply(argument);
        pending.set(true);
        f.whenComplete((result, failure) -> scope.post(() -> {
            pending.set(false);
            error.set(failure instanceof CompletionException c && c.getCause() != null ? c.getCause() : failure);
        }));
    }
}
