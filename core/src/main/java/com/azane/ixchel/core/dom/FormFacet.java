package com.azane.ixchel.core.dom;

/** Form state and binding capabilities; implementations belong to element definitions. */
public interface FormFacet {
    Facet<FormFacet> KEY = new Facet<>(new FormFacet() {});

    default FormControl.Kind kind(Element e) { return null; }
    default boolean option() { return false; }
    default boolean optionOwner() { return false; }
    default boolean checked(Element e) { return false; }
    default boolean binds(Element e, String property) { return false; }
    /** Whether {@code :open} follows the element's own {@code open} attribute (a details) rather than the {@link TopLayer}. */
    default boolean openByAttribute() { return false; }
}
