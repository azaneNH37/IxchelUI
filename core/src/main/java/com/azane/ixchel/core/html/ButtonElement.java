package com.azane.ixchel.core.html;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.event.InteractionFacet;

final class ButtonElement {
    private ButtonElement() {}
    static final InteractionFacet INTERACTION = new InteractionFacet() {
        @Override public boolean focusable() { return true; }
        @Override public boolean clickSoundBefore() { return true; }
        @Override public boolean key(Element e, int key, int mods, Actions a) {
            if (key != FormEditing.KEY_SPACE && !FormEditing.isEnter(key)) return false;
            a.clickSound(e);
            a.dispatch(UiEvent.mouse(UiEvent.Type.CLICK, e, Float.NaN, Float.NaN, 0, 0));
            return true;
        }
    };
    static final ElementType TYPE = ElementType.builder("button").facet(InteractionFacet.KEY, INTERACTION).build();
}
