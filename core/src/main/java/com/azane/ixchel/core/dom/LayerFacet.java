package com.azane.ixchel.core.dom;

/** Presentation policy shared by layout, paint and input. No policy identifies an HTML tag. */
public record LayerFacet(Placement placement, Pointer pointer, boolean modal, boolean selfContent,
                         boolean hiddenInFlow, boolean dismissOnBlur, boolean dismissOnScroll,
                         boolean dismissOutside, boolean dismissWhenInactive) {
    public enum Placement { FLOW, CENTER, BELOW_ANCHOR, POINTER_OFFSET, POINTER_CENTER }
    public enum Pointer { NONE, TOOLTIP, DRAG_PREVIEW }

    public static final LayerFacet NONE = new LayerFacet(Placement.FLOW, Pointer.NONE,
            false, true, false, false, false, false, false);
    public static final Facet<LayerFacet> KEY = new Facet<>(NONE);

    public boolean atPointer() { return pointer != Pointer.NONE; }

    public int contentCount(Element entry) {
        return selfContent ? 1 : entry.children().size();
    }

    /** Null for a child that does not participate in the entry's presentation. No list allocation. */
    public Element contentAt(Element entry, int index) {
        if (selfContent) return entry;
        Node n = entry.children().get(index);
        return n instanceof Element e && e.type().facet(FormFacet.KEY).option() ? e : null;
    }

    public boolean contains(Element entry, Element target) {
        if (selfContent) {
            for (Element e = target; e != null; e = e.parent()) {
                if (e == entry) return true;
            }
            return false;
        }
        return target.parent() == entry && target.type().facet(FormFacet.KEY).option();
    }
}
