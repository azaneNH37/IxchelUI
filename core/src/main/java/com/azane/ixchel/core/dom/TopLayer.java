package com.azane.ixchel.core.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The document's top layer: elements drawn above the whole tree and outside every ancestor's clip, in
 * the order they were opened (the last one on top). An entry is an open {@code <select>} (its options
 * form the list) or an open {@code <dialog>} (modal: everything below it is inert). Entries stay in
 * the tree; opening one sets {@link Element#OPEN} on it, which restyles it and so runs layout, where
 * the entries are placed against the viewport.
 *
 * <p>Above every entry, outside {@link #entries()}, one element shown at the pointer
 * ({@link #atPointer()}): the {@linkplain #tooltip() tooltip}, an {@code <ix-tooltip>} the host shows,
 * or, while an element is dragged, its {@linkplain #dragPreview() drag preview}, an
 * {@code <ix-drag-preview>}. It is never hit, never focused and makes nothing inert; it is also
 * {@link Element#OPEN} while shown.
 *
 * <p>Each entry and the element at the pointer is a {@link Presence} unit. Closed, an entry exits:
 * it is no longer {@linkplain #isOpen open}, modal or hit, but stays in {@link #entries()} and
 * {@link Element#OPEN}, so it is laid out and painted, until its exit ends; a dialog is inert
 * meanwhile. A hidden element at the pointer exits in place the same way. Opening an exiting entry
 * takes it back.
 */
public final class TopLayer {
    private final Presence presence;
    private final List<Element> entries = new ArrayList<>();
    private final List<Element> view = Collections.unmodifiableList(entries);
    private final Map<Element, Presence.Unit> units = new IdentityHashMap<>();
    private Element atPointer;
    private Presence.Unit pointerUnit;
    private float pointerX;
    private float pointerY;
    private boolean moved;

    public TopLayer(Presence presence) {
        this.presence = presence;
    }

    /** Bottom first, exiting entries included; unmodifiable live view. */
    public List<Element> entries() {
        return view;
    }

    /** The element shown at the pointer, or exiting there: a tooltip or a drag preview, or {@code null}. */
    public Element atPointer() {
        return atPointer;
    }

    /** The shown {@code <ix-tooltip>}, not exiting, or {@code null}. */
    public Element tooltip() {
        return shownAtPointer(LayerFacet.Pointer.TOOLTIP);
    }

    /** The shown {@code <ix-drag-preview>}, not exiting, or {@code null}. */
    public Element dragPreview() {
        return shownAtPointer(LayerFacet.Pointer.DRAG_PREVIEW);
    }

    private Element shownAtPointer(LayerFacet.Pointer kind) {
        return atPointer != null && !pointerUnit.exiting() && atPointer.type().facet(LayerFacet.KEY).pointer() == kind
                ? atPointer : null;
    }

    /** The pointer {@link #atPointer()} is placed at, in GUI pixels. */
    public float pointerX() {
        return pointerX;
    }

    public float pointerY() {
        return pointerY;
    }

    /**
     * Shows {@code e}, an {@code <ix-tooltip>} or {@code <ix-drag-preview>}, at the pointer
     * {@code (x, y)} instead of the current one, which goes at once; {@code null} makes the current
     * one exit where it is.
     */
    public void showAtPointer(Element e, float x, float y) {
        if (e == null) {
            if (atPointer != null && !pointerUnit.exiting()) {
                Element gone = atPointer;
                presence.exit(pointerUnit, false, () -> {
                    gone.setState(Element.OPEN, false);
                    atPointer = null;
                    pointerUnit = null;
                });
            }
            return;
        }
        if (e != atPointer) {
            dropAtPointer();
            atPointer = e;
            e.setState(Element.OPEN, true);
            pointerUnit = presence.enter(List.of(e));
        } else {
            presence.reenter(pointerUnit);
            if (x != pointerX || y != pointerY) {
                moved = true;
            }
        }
        pointerX = x;
        pointerY = y;
    }

    /** Hides the element at the pointer at once, exiting or not. */
    public void dropAtPointer() {
        if (atPointer != null) {
            presence.cut(pointerUnit);
            atPointer.setState(Element.OPEN, false);
            atPointer = null;
            pointerUnit = null;
        }
    }

    /** Whether the shown element only moved since the last call: it needs placing again, not layout. */
    public boolean takeMoved() {
        boolean m = moved;
        moved = false;
        return m;
    }

    /** Whether {@code e} is drawn in the top layer: an entry or at the pointer, exiting or not. */
    public boolean shows(Element e) {
        return e == atPointer || units.containsKey(e);
    }

    /** Whether {@code e} is an entry and not exiting. */
    public boolean isOpen(Element e) {
        return entries.contains(e) && !isExiting(e);
    }

    /** Whether {@code e} is an entry that was closed and still exits. */
    public boolean isExiting(Element e) {
        Presence.Unit u = units.get(e);
        return u != null && u.exiting();
    }

    /** Puts {@code e} on top, taking it back if it exits; nothing when it is already open. */
    public void open(Element e) {
        Presence.Unit u = units.get(e);
        if (u == null) {
            entries.add(e);
            e.setState(Element.OPEN, true);
            units.put(e, presence.enter(List.of(e)));
        } else if (u.exiting()) {
            entries.remove(e);
            entries.add(e);
            presence.reenter(u);
        }
    }

    /** Closes {@code e} and every open entry opened after it; returns those closed, top first. */
    public List<Element> close(Element e) {
        if (!isOpen(e)) {
            return List.of();
        }
        int i = entries.indexOf(e);
        List<Element> closed = new ArrayList<>();
        for (int j = entries.size() - 1; j >= i; j--) {
            Element c = entries.get(j);
            if (!isExiting(c)) {
                closed.add(c);
            }
        }
        for (Element c : closed) {
            exit(c);
        }
        return closed;
    }

    /** Closes only {@code e}, leaving the entries above it open; whether it was open. */
    public boolean remove(Element e) {
        if (!isOpen(e)) {
            return false;
        }
        exit(e);
        return true;
    }

    private void exit(Element e) {
        presence.exit(units.get(e), e.type().facet(LayerFacet.KEY).selfContent(), () -> {
            entries.remove(e);
            units.remove(e);
            e.setState(Element.OPEN, false);
        });
    }

    /** The topmost open entry, or {@code null}. */
    public Element top() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (!isExiting(entries.get(i))) {
                return entries.get(i);
            }
        }
        return null;
    }

    /** The topmost open dialog, or {@code null}: it and the entries above it are all that is not inert. */
    public Element modal() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Element e = entries.get(i);
            if (e.type().facet(LayerFacet.KEY).modal() && !isExiting(e)) {
                return e;
            }
        }
        return null;
    }

    /** Whether {@code e} is an entry at or above the topmost dialog, or there is no dialog. */
    public boolean isActive(Element e) {
        Element m = modal();
        return m == null || entries.indexOf(e) >= entries.indexOf(m);
    }
}
