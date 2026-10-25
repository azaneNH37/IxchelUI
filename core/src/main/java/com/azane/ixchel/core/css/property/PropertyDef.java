package com.azane.ixchel.core.css.property;

/**
 * One supported CSS property. {@code id} is the dense index into {@link ComputedStyle}; obtain
 * instances from {@link PropertyRegistry#register} only.
 */
public record PropertyDef<T>(String name, int id, T initial,
                             boolean inherited, boolean affectsLayout, boolean animatable) {
}
