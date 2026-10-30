package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Action;

import java.lang.reflect.Type;
import java.util.function.Consumer;

/**
 * What a name in a template stands for. A page sees its view model's non-private fields; a
 * component's template sees its parameters and events; both see their local states, {@code as} rows
 * and, in handlers, {@code $event}.
 */
sealed interface Name permits Ref, Name.Act, Name.Event, Name.Unset {
    /** An {@link Action} field; {@code param} is its argument type, {@code Void} for none. */
    record Act(Action<Object> action, Type param) implements Name {
    }

    /** A view model field holding {@code null}. */
    record Unset(String field) implements Name {
    }

    /**
     * A component event. Its payload type is fixed by the first call compiled in the component's
     * template; the user's {@code on:<event>} handler is set after that template is compiled.
     */
    final class Event implements Name {
        final String name;
        /** Whether a call has fixed {@link #payload}. */
        boolean called;
        /** The payload type, or {@code null} for an event without payload. */
        Type payload;
        Consumer<Object> listener;

        Event(String name) {
            this.name = name;
        }

        void fire(Object payload) {
            if (listener != null) {
                listener.accept(payload);
            }
        }
    }
}
