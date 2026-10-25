package com.azane.ixchel.core.css.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single list of supported properties: parsing, validation, dev-time errors and generated editor
 * data all read from it. Ids are assigned densely in registration order.
 */
public final class PropertyRegistry {
    private final List<PropertyDef<?>> byId = new ArrayList<>();
    private final Map<String, PropertyDef<?>> byName = new HashMap<>();

    public <T> PropertyDef<T> register(String name, T initial,
                                       boolean inherited, boolean affectsLayout, boolean animatable) {
        if (byName.containsKey(name)) {
            throw new IllegalArgumentException("property already registered: " + name);
        }
        PropertyDef<T> def = new PropertyDef<>(name, byId.size(), initial, inherited, affectsLayout, animatable);
        byId.add(def);
        byName.put(name, def);
        return def;
    }

    /** {@code null} if unsupported. */
    public PropertyDef<?> byName(String name) {
        return byName.get(name);
    }

    public PropertyDef<?> byId(int id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public List<PropertyDef<?>> all() {
        return Collections.unmodifiableList(byId);
    }
}
