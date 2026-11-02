package com.azane.ixchel.core.event;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Facet;
import com.azane.ixchel.core.dom.UiEvent;

/** Internal element default actions. Event routing, transforms, focus order and capture stay in the dispatcher. */
public interface InteractionFacet {
    Facet<InteractionFacet> KEY = new Facet<>(new InteractionFacet() {});
    default boolean focusable() { return false; }
    default boolean hitChildren() { return true; }
    default boolean dragSourceAllowed() { return true; }
    default boolean clickSoundBefore() { return false; }
    default boolean capturesKeys(Element e) { return false; }
    default void beforePress(Element e, float x, int modifiers, Actions a) {}
    /** True requests pointer capture for this default action, until release. */
    default boolean press(Element e, float x, Actions a) { return false; }
    default void move(Element e, float x, Actions a) {}
    default void release(Element e, Actions a) {}
    default void click(Element e, Actions a) {}
    default boolean key(Element e, int key, int modifiers, Actions a) { return false; }
    default boolean character(Element e, int codePoint, Actions a) { return false; }
    default void focus(Element e, Actions a) {}
    default void blur(Element e, Actions a) {}
    default void close(Element e, Actions a) {}

    interface Actions {
        void dispatch(UiEvent event);
        void clickSound(Element e);
        boolean isOpen(Element e);
        void open(Element e);
        void close(Element e);
        Clipboard clipboard();
        float textWidth(String text, float size);
        default void input(Element e) { dispatch(UiEvent.of(UiEvent.Type.INPUT, e)); }
        default void change(Element e) { dispatch(UiEvent.of(UiEvent.Type.CHANGE, e)); }
        default void commit(Element e) { input(e); change(e); }
    }
}
