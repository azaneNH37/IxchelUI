package com.azane.ixchel.core.dom;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable declaration shared by every instance of a tag. Pipeline contracts are composed using
 * identity-keyed facets; the DOM does not depend on their implementations or the pipeline packages.
 * The builder is an internal assembly contract, not the host element registration API.
 */
public final class ElementType {
    private final String tag;
    private final Set<String> attributes;
    private final String payload;
    private final Type payloadType;
    private final Map<Facet<?>, Object> facets;

    private ElementType(Builder b) {
        tag = b.tag;
        attributes = b.attributes;
        payload = b.payload;
        payloadType = b.payloadType;
        facets = new IdentityHashMap<>(b.facets);
    }

    public static Builder builder(String tag, String... attributes) {
        return new Builder(tag, Set.of(attributes));
    }

    public static ElementType plain(String tag, String... attributes) {
        return builder(tag, attributes).build();
    }

    public String tag() { return tag; }
    public Set<String> attributes() { return attributes; }
    public String payload() { return payload; }
    /** The payload's declared type, with its type arguments ({@code List<ItemStack>}); {@code null} if unchecked. */
    public Type payloadType() { return payloadType; }
    /** The raw class of {@link #payloadType}, what a value can be checked against at run time. */
    public Class<?> payloadClass() {
        return payloadType instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) payloadType;
    }

    @SuppressWarnings("unchecked")
    public <F> F facet(Facet<F> key) {
        Object value = facets.get(key);
        return value == null ? key.fallback() : (F) value;
    }

    @Override
    public String toString() { return "<" + tag + ">"; }

    public static final class Builder {
        private final String tag;
        private final Set<String> attributes;
        private final Map<Facet<?>, Object> facets = new IdentityHashMap<>();
        private String payload;
        private Type payloadType;

        private Builder(String tag, Set<String> attributes) {
            this.tag = tag;
            this.attributes = attributes;
        }

        public Builder payload(String name, Type type) {
            payload = name;
            payloadType = type;
            return this;
        }

        public <F> Builder facet(Facet<F> key, F value) {
            facets.put(key, value);
            return this;
        }

        public ElementType build() { return new ElementType(this); }
    }
}
