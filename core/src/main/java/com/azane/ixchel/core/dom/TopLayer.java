package com.azane.ixchel.core.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 */
public final class TopLayer {
    private final List<Element> entries = new ArrayList<>();
    private final List<Element> view = Collections.unmodifiableList(entries);
    private Element atPointer;
    private float pointerX;
    private float pointerY;
    private boolean moved;

    /** Bottom first; unmodifiable live view. */
    public List<Element> entries() {
        return view;
    }

    /** The element shown at the pointer: a tooltip or a drag preview, or {@code null}. */
    public Element atPointer() {
        return atPointer;
    }

    /** The shown {@code <ix-tooltip>}, or {@code null}. */
    public Element tooltip() {
        return atPointer != null && atPointer.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.TOOLTIP ? atPointer : null;
    }

    /** The shown {@code <ix-drag-preview>}, or {@code null}. */
    public Element dragPreview() {
        return atPointer != null && atPointer.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.DRAG_PREVIEW ? atPointer : null;
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
     * {@code (x, y)} instead of the current one; {@code null} hides it.
     */
    public void showAtPointer(Element e, float x, float y) {
        if (e != atPointer) {
            if (atPointer != null) {
                atPointer.setState(Element.OPEN, false);
            }
            atPointer = e;
            if (e != null) {
                e.setState(Element.OPEN, true);
            }
        } else if (e != null && (x != pointerX || y != pointerY)) {
            moved = true;
        }
        pointerX = x;
        pointerY = y;
    }

    /** Whether the shown element only moved since the last call: it needs placing again, not layout. */
    public boolean takeMoved() {
        boolean m = moved;
        moved = false;
        return m;
    }

    public boolean isOpen(Element e) {
        return entries.contains(e);
    }

    /** Puts {@code e} on top; nothing when it is already open. */
    public void open(Element e) {
        if (!entries.contains(e)) {
            entries.add(e);
            e.setState(Element.OPEN, true);
        }
    }

    /** Closes {@code e} and every entry opened after it; returns those closed, top first. */
    public List<Element> close(Element e) {
        int i = entries.indexOf(e);
        if (i < 0) {
            return List.of();
        }
        List<Element> closed = new ArrayList<>();
        for (int j = entries.size() - 1; j >= i; j--) {
            Element c = entries.remove(j);
            c.setState(Element.OPEN, false);
            closed.add(c);
        }
        return closed;
    }

    /** Closes only {@code e}, leaving the entries above it open; whether it was open. */
    public boolean remove(Element e) {
        if (!entries.remove(e)) {
            return false;
        }
        e.setState(Element.OPEN, false);
        return true;
    }

    /** The topmost entry, or {@code null}. */
    public Element top() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    /** The topmost open dialog, or {@code null}: it and the entries above it are all that is not inert. */
    public Element modal() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).type().facet(LayerFacet.KEY).modal()) {
                return entries.get(i);
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
