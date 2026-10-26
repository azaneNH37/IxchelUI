package com.azane.ixchel.core.template;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tags a template may use and the plain attributes each accepts. Immutable; hosts add native
 * elements with {@link #extend()}.
 *
 * <p>Every tag except {@code <style>} accepts {@link #GLOBAL_ATTRIBUTES}. {@code bind:*} attributes
 * are accepted on any known element without being registered.
 */
public final class TagRegistry {
    /** Attributes every element except {@code <style>} accepts. */
    public static final Set<String> GLOBAL_ATTRIBUTES = Set.of("class", "id", "style");

    /** The HTML subset core understands: {@code div}, {@code span}, {@code img src}, {@code style}. */
    public static final TagRegistry HTML = new Builder(Map.of())
            .tag("div")
            .tag("span")
            .tag("img", "src")
            .tag("style")
            .build();

    private final Map<String, Set<String>> attributes;

    private TagRegistry(Map<String, Set<String>> attributes) {
        this.attributes = attributes;
    }

    /** A builder starting from this registry's tags and attributes. */
    public Builder extend() {
        return new Builder(attributes);
    }

    public boolean isKnownTag(String tag) {
        return attributes.containsKey(tag);
    }

    /** Whether {@code attribute} is a plain attribute of the known tag {@code tag}. */
    public boolean isAllowedAttribute(String tag, String attribute) {
        if (tag.equals("style")) {
            return false;
        }
        return GLOBAL_ATTRIBUTES.contains(attribute) || attributes.get(tag).contains(attribute);
    }

    /** The registered tags. */
    public Set<String> tags() {
        return attributes.keySet();
    }

    /** Collects tags; {@link #build()} produces an immutable registry. */
    public static final class Builder {
        private final Map<String, Set<String>> attributes = new LinkedHashMap<>();

        private Builder(Map<String, Set<String>> from) {
            from.forEach((tag, attrs) -> attributes.put(tag, new LinkedHashSet<>(attrs)));
        }

        /**
         * Registers {@code tag} with extra plain attributes, or adds the attributes to an already
         * registered tag.
         *
         * @throws IllegalArgumentException if a name is malformed, an attribute is global, a
         *         {@code bind:} attribute or reserved, or attributes are given for {@code style}
         */
        public Builder tag(String tag, String... extraAttributes) {
            if (!isName(tag, false)) {
                throw new IllegalArgumentException("malformed tag name '" + tag + "'");
            }
            if (tag.equals("style") && extraAttributes.length > 0) {
                throw new IllegalArgumentException("<style> takes no attributes");
            }
            Set<String> set = attributes.computeIfAbsent(tag, k -> new LinkedHashSet<>());
            for (String a : extraAttributes) {
                if (!isName(a, true)) {
                    throw new IllegalArgumentException("malformed attribute name '" + a + "'");
                }
                if (GLOBAL_ATTRIBUTES.contains(a) || a.startsWith("bind:") || TemplateParser.isReserved(a)) {
                    throw new IllegalArgumentException("attribute '" + a + "' cannot be registered on <" + tag + ">");
                }
                set.add(a);
            }
            return this;
        }

        public TagRegistry build() {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            attributes.forEach((tag, attrs) -> copy.put(tag, Set.copyOf(attrs)));
            return new TagRegistry(Map.copyOf(copy));
        }

        /** Same grammar as the template parser: ASCII letter first, then letters, digits or '-'. */
        private static boolean isName(String s, boolean attribute) {
            boolean segmentStart = true;
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
                if (segmentStart) {
                    if (!letter) {
                        return false;
                    }
                    segmentStart = false;
                } else if (attribute && c == ':') {
                    segmentStart = true;
                } else if (!letter && !(c >= '0' && c <= '9') && c != '-') {
                    return false;
                }
            }
            return !segmentStart;
        }
    }
}
