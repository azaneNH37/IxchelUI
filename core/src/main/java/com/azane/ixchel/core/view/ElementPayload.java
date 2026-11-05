package com.azane.ixchel.core.view;

import com.azane.ixchel.core.dom.ElementType;

import java.util.function.Supplier;

/**
 * Implemented by the class declaring a {@link UiElement} with a payload: {@code T} is the
 * payload's type. A binding may hand a {@code T} or a {@code java.util.function.Supplier} of one,
 * which the host reads where it uses the value ({@link #valueOf}). Adapter interfaces extend it, so a
 * host only names the type once, as their type argument.
 */
public interface ElementPayload<T> {
    /**
     * The value a payload of {@code type} stands for right now: the payload, or what its
     * {@code Supplier} gives; {@code null} for none.
     *
     * @throws IllegalStateException if the value is not of the type's payload type
     */
    static Object valueOf(Object payload, ElementType type) {
        Object v = payload instanceof Supplier<?> s ? s.get() : payload;
        Class<?> expected = type.payloadClass();
        if (v != null && expected != null && !expected.isInstance(v)) {
            throw new IllegalStateException(type + " needs a " + expected.getName() + " or a Supplier of one, got "
                    + (payload instanceof Supplier<?> ? "a Supplier of " : "") + v.getClass().getName());
        }
        return v;
    }
}
