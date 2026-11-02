package com.azane.ixchel.core.dom;

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
    private final Class<?> payloadType;
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
    public Class<?> payloadType() { return payloadType; }

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
        private Class<?> payloadType;

        private Builder(String tag, Set<String> attributes) {
            this.tag = tag;
            this.attributes = attributes;
        }

        public Builder payload(String name, Class<?> type) {
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
