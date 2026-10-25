package com.azane.ixchel.core.template;

import java.util.Map;
import java.util.Set;

/** Tags and attributes a template may use. */
final class TagRegistry {
    static final Set<String> GLOBAL_ATTRIBUTES = Set.of("class", "id", "style");

    private static final Map<String, Set<String>> EXTRA_ATTRIBUTES = Map.of(
            "div", Set.of(),
            "span", Set.of(),
            "img", Set.of("src"),
            "style", Set.of());

    private TagRegistry() {
    }

    static boolean isKnownTag(String tag) {
        return EXTRA_ATTRIBUTES.containsKey(tag);
    }

    static boolean isAllowedAttribute(String tag, String attribute) {
        if (tag.equals("style")) {
            return false;
        }
        return GLOBAL_ATTRIBUTES.contains(attribute) || EXTRA_ATTRIBUTES.get(tag).contains(attribute);
    }
}
