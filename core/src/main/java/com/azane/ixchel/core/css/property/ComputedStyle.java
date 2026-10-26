package com.azane.ixchel.core.css.property;

/** Computed values of one element, indexed by {@link PropertyDef#id()}. */
public final class ComputedStyle {
    private final Object[] values;

    private ComputedStyle(Object[] values) {
        this.values = values;
    }

    /** Every property at its initial value; the root element's starting point. */
    public static ComputedStyle initial(PropertyRegistry registry) {
        Object[] values = new Object[registry.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = registry.byId(i).initial();
        }
        return new ComputedStyle(values);
    }

    /** Inherited properties copied from {@code parent}, the rest at their initial values. */
    public static ComputedStyle inheriting(PropertyRegistry registry, ComputedStyle parent) {
        Object[] values = new Object[registry.size()];
        for (int i = 0; i < values.length; i++) {
            PropertyDef<?> def = registry.byId(i);
            values[i] = def.inherited() ? parent.values[i] : def.initial();
        }
        return new ComputedStyle(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyDef<T> def) {
        return (T) values[def.id()];
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
