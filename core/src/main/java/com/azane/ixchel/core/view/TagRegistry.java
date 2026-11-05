package com.azane.ixchel.core.view;

import com.azane.ixchel.core.dom.ElementType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable vocabulary of runtime elements. ParsedView syntax is not part of this registry. */
public final class TagRegistry {
    public static final Set<String> GLOBAL_ATTRIBUTES = Set.of("class", "id", "style", "tabindex", "disabled", "title",
            "lang", "translate", "mark", "draggable", "part");
    private final Map<String, ElementType> types;

    private TagRegistry(Map<String, ElementType> types) { this.types = types; }
    public static Builder builder() { return new Builder(Map.of()); }
    public Builder extend() { return new Builder(types); }
    public ElementType type(String tag) { return types.get(tag); }
    public boolean isKnownTag(String tag) { return types.containsKey(tag); }
    public Set<String> tags() { return types.keySet(); }
    public boolean isAllowedAttribute(String tag, String attribute) {
        ElementType type = types.get(tag);
        return type != null && (GLOBAL_ATTRIBUTES.contains(attribute) || type.attributes().contains(attribute));
    }

    public static final class Builder {
        private final Map<String, ElementType> types = new LinkedHashMap<>();
        private Builder(Map<String, ElementType> from) { types.putAll(from); }

        public Builder add(ElementType type) {
            String tag = type.tag();
            if (!isName(tag, false)) throw new IllegalArgumentException("malformed tag name '" + tag + "'");
            if (Set.of("link", "for", "if", "else", "empty", "slot", "page", "component").contains(tag)) {
                throw new IllegalArgumentException("<" + tag + "> is view syntax, not an element");
            }
            if (types.containsKey(tag)) throw new IllegalArgumentException(type + " is already registered");
            for (String a : type.attributes()) {
                if (!isName(a, true)) throw new IllegalArgumentException("malformed attribute name '" + a + "'");
                if (GLOBAL_ATTRIBUTES.contains(a) || ViewParser.isDirective(a) || ViewParser.isTranslation(a)) {
                    throw new IllegalArgumentException("attribute '" + a + "' cannot be registered on " + type);
                }
            }
            String payload = type.payload();
            if (payload != null && (!isName(payload, false) || GLOBAL_ATTRIBUTES.contains(payload)
                    || ViewParser.isTranslation(payload) || ViewParser.isDirective(payload)
                    || type.attributes().contains(payload))) {
                throw new IllegalArgumentException("'" + payload + "' cannot be the payload of " + type);
            }
            types.put(tag, type);
            return this;
        }
        public Builder addAll(List<ElementType> list) { list.forEach(this::add); return this; }
        public TagRegistry build() { return new TagRegistry(Map.copyOf(types)); }

        private static boolean isName(String s, boolean attribute) {
            boolean segmentStart = true;
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
                if (segmentStart) {
                    if (!letter) return false;
                    segmentStart = false;
                } else if (attribute && c == ':') segmentStart = true;
                else if (!letter && !(c >= '0' && c <= '9') && c != '-') return false;
            }
            return !segmentStart;
        }
    }
}
