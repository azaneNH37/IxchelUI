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
 * <p>Above every entry, outside {@link #entries()}, the {@linkplain #tooltip() tooltip}: at most one
 * {@code <ix-tooltip>} the host shows at the pointer. It is never hit, never focused and makes
 * nothing inert; it is also {@link Element#OPEN} while shown.
 */
public final class TopLayer {
    /** The tag of tooltip elements. */
    public static final String TOOLTIP = "ix-tooltip";

    private final List<Element> entries = new ArrayList<>();
    private final List<Element> view = Collections.unmodifiableList(entries);
    private Element tooltip;
    private float tooltipX;
    private float tooltipY;

    /** Bottom first; unmodifiable live view. */
    public List<Element> entries() {
        return view;
    }

    /** The shown {@code <ix-tooltip>}, or {@code null}. */
    public Element tooltip() {
        return tooltip;
    }

    /** The pointer the tooltip is placed at, in GUI pixels. */
    public float tooltipX() {
        return tooltipX;
    }

    public float tooltipY() {
        return tooltipY;
    }

    /** Shows {@code tip}, an {@code <ix-tooltip>}, at the pointer {@code (x, y)} instead of the current one; {@code null} hides it. */
    public void showTooltip(Element tip, float x, float y) {
        if (tip != tooltip) {
            if (tooltip != null) {
                tooltip.setState(Element.OPEN, false);
            }
            tooltip = tip;
            if (tip != null) {
                tip.setState(Element.OPEN, true);
            }
        }
        tooltipX = x;
        tooltipY = y;
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
            if (entries.get(i).tag().equals("dialog")) {
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
