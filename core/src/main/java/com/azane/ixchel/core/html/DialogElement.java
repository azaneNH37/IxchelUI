package com.azane.ixchel.core.html;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.FormFacet;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.event.InteractionFacet;

final class DialogElement {
    private DialogElement() {}
    static final InteractionFacet INTERACTION = new InteractionFacet() {
        @Override public void close(Element e, Actions a) { a.dispatch(UiEvent.of(UiEvent.Type.CLOSE, e)); }
    };
    static final ElementType TYPE = ElementType.builder("dialog")
            .facet(LayerFacet.KEY, new LayerFacet(LayerFacet.Placement.CENTER, LayerFacet.Pointer.NONE,
                    true, true, true, false, false, false, false))
            .facet(InteractionFacet.KEY, INTERACTION)
            .facet(FormFacet.KEY, new FormFacet() {
                @Override public boolean binds(Element e, String property) { return property.equals("open"); }
            }).build();
}
