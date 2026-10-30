package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;

import java.util.List;
import java.util.Map;

/**
 * Computed values of one element, indexed by {@link PropertyDef#id()}, and its custom properties:
 * name to value with every {@code var()} substituted. Custom properties always inherit.
 */
public final class ComputedStyle {
    private final Object[] values;
    private final Map<String, List<ComponentValue>> custom;

    private ComputedStyle(Object[] values, Map<String, List<ComponentValue>> custom) {
        this.values = values;
        this.custom = custom;
    }

    /** Every property at its initial value and no custom properties; the root element's starting point. */
    public static ComputedStyle initial(PropertyRegistry registry) {
        Object[] values = new Object[registry.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = registry.byId(i).initial();
        }
        return new ComputedStyle(values, Map.of());
    }

    /** Inherited properties copied from {@code parent}, the rest at their initial values; {@code custom} is not copied. */
    public static ComputedStyle inheriting(PropertyRegistry registry, ComputedStyle parent,
                                           Map<String, List<ComponentValue>> custom) {
        Object[] values = new Object[registry.size()];
        for (int i = 0; i < values.length; i++) {
            PropertyDef<?> def = registry.byId(i);
            values[i] = def.inherited() ? parent.values[i] : def.initial();
        }
        return new ComputedStyle(values, custom);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyDef<T> def) {
        return (T) values[def.id()];
    }

    /** Custom properties by name ({@code --x}); unmodifiable. */
    public Map<String, List<ComponentValue>> custom() {
        return custom;
    }

    /** Whether both styles hold equal values for every property id in {@code ids}. */
    public boolean sameValues(ComputedStyle other, int[] ids) {
        for (int id : ids) {
            if (!values[id].equals(other.values[id])) {
                return false;
            }
        }
        return true;
    }

    public <T> void set(PropertyDef<T> def, T value) {
        values[def.id()] = value;
    }
}
