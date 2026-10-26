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
    private final Map<String, Shorthand> shorthands = new HashMap<>();
    private boolean frozen;
    private int[] layoutIds;

    public <T> PropertyDef<T> register(String name, Syntax<T> syntax, T initial,
                                       boolean inherited, boolean affectsLayout, boolean animatable) {
        checkFree(name);
        PropertyDef<T> def = new PropertyDef<>(name, byId.size(), syntax, initial, inherited, affectsLayout, animatable);
        byId.add(def);
        byName.put(name, def);
        layoutIds = null;
        return def;
    }

    public Shorthand registerShorthand(String name, Shorthand.Expander expander, PropertyDef<?>... longhands) {
        checkFree(name);
        Shorthand s = new Shorthand(name, List.of(longhands), expander);
        shorthands.put(name, s);
        return s;
    }

    private void checkFree(String name) {
        if (frozen) {
            throw new IllegalStateException("registry is frozen; cannot register " + name);
        }
        if (byName.containsKey(name) || shorthands.containsKey(name)) {
            throw new IllegalArgumentException("property already registered: " + name);
        }
    }

    /** Rejects further registration; computed style arrays sized before a registration would be too short. */
    public void freeze() {
        frozen = true;
    }

    /** Ids of properties with {@link PropertyDef#affectsLayout()}; do not modify the array. */
    public int[] layoutAffectingIds() {
        if (layoutIds == null) {
            layoutIds = byId.stream().filter(PropertyDef::affectsLayout).mapToInt(PropertyDef::id).toArray();
        }
        return layoutIds;
    }

    /** {@code null} if not a longhand. */
    public PropertyDef<?> byName(String name) {
        return byName.get(name);
    }

    /** {@code null} if not a shorthand. */
    public Shorthand shorthand(String name) {
        return shorthands.get(name);
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
