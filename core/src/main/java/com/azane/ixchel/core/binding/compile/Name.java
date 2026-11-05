package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Action;

import java.lang.reflect.Type;
import java.util.function.Consumer;

/**
 * What a name in a view stands for. A page sees its view model's non-private fields; a
 * component's view sees its parameters, events and used names, and those of a Java component also the other
 * members of its view model; all see their local states, {@code as} rows and, in handlers,
 * {@code $event}.
 */
sealed interface Name permits Ref, Name.Act, Name.Event, Name.Unset {
    /** An {@link Action} field; {@code param} is its argument type, {@code Void} for none. */
    record Act(Action<Object> action, Type param) implements Name {
    }

    /** A view model field holding {@code null}. */
    record Unset(String field) implements Name {
    }

    /**
     * A component event. Its payload type is declared by its {@code Event<T>} field, or else fixed by
     * the first call compiled in the component's view; the user's {@code on:<event>} handler is
     * set after that view is compiled.
     */
    final class Event implements Name {
        final String name;
        /** Whether its {@code Event<T>} field declares {@link #payload}: calls may widen to it. */
        final boolean declared;
        /** Whether {@link #payload} is known. */
        boolean called;
        /** The payload type, or {@code null} for an event without payload. */
        Type payload;
        Consumer<Object> listener;

        Event(String name) {
            this.name = name;
            this.declared = false;
        }

        /** @param payload the type {@code T} of the event's field, {@code Void} for none */
        Event(String name, Type payload) {
            this.name = name;
            this.declared = true;
            this.called = true;
            this.payload = payload == Void.class ? null : payload;
        }

        void fire(Object payload) {
            if (listener != null) {
                listener.accept(payload);
            }
        }
    }
}
