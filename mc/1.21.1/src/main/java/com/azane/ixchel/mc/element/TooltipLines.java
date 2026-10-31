package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.runtime.UiDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fills the {@code <mc-tooltip-lines bind:item="…">} elements of a document with the lines of their
 * item's vanilla tooltip ({@link Screen#getTooltipFromItem}, so lines other mods add come along), one
 * text child per line in its colors and formatting. A host runs it around each frame; only the
 * elements shown are read: those outside any {@code <ix-tooltip>} and those in the shown one.
 */
public final class TooltipLines {
    static final String TAG = "mc-tooltip-lines";
    /** An empty line keeps its height. */
    private static final StyledText BLANK = new StyledText(List.of(new StyledText.Span("\u00A0", 0, 0)));

    private final Function<Component, StyledText> styled;
    private List<Element> known = List.of();
    private final Map<Element, List<Component>> filled = new IdentityHashMap<>();

    /** @param styled converts a line to styled text, as {@code bind:text} converts a {@link Component} */
    public TooltipLines(Function<Component, StyledText> styled) {
        this.styled = styled;
    }

    /**
     * Finds the line elements of {@code document} again, after a frame that may have added or removed
     * some; returns whether it found one it did not know.
     */
    public boolean discover(UiDocument document) {
        List<Element> found = new ArrayList<>();
        collect(document.root(), found);
        boolean added = false;
        for (Element e : found) {
            added |= !filled.containsKey(e);
        }
        filled.keySet().retainAll(found);
        known = found;
        return added;
    }

    private static void collect(Element e, List<Element> out) {
        if (e.tag().equals(TAG)) {
            out.add(e);
            return;
        }
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Element c) {
                collect(c, out);
            }
        }
    }

    /** Brings the lines of every shown line element up to date; returns whether any changed. */
    public boolean update(UiDocument document) {
        boolean changed = false;
        Element shownTip = document.topLayer().tooltip();
        for (int i = 0; i < known.size(); i++) {
            Element e = known.get(i);
            Element tip = enclosingTooltip(e);
            if (tip == null || tip == shownTip) {
                changed |= fill(e);
            }
        }
        return changed;
    }

    /** The nearest {@code <ix-tooltip>} around {@code e}, or {@code null}. */
    private static Element enclosingTooltip(Element e) {
        for (Element a = e.parent(); a != null; a = a.parent()) {
            if (a.tag().equals(TopLayer.TOOLTIP)) {
                return a;
            }
        }
        return null;
    }

    private boolean fill(Element e) {
        ItemStack stack = e.payload() == null ? ItemStack.EMPTY : ItemElement.stackOf(e.payload());
        List<Component> lines = stack.isEmpty() ? List.of() : Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        List<Component> before = filled.getOrDefault(e, List.of());
        if (lines.equals(before)) {
            return false;
        }
        List<Node> children = e.children();
        while (children.size() > lines.size()) {
            e.removeChild(children.get(children.size() - 1));
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i < before.size() && i < children.size() && lines.get(i).equals(before.get(i))) {
                continue;
            }
            StyledText text = styled.apply(lines.get(i));
            if (text.plain().isBlank()) {
                text = BLANK;
            }
            if (i < children.size()) {
                ((TextNode) children.get(i)).setStyled(text);
            } else {
                TextNode t = new TextNode("", e.pos());
                t.setStyled(text);
                e.appendChild(t);
            }
        }
        filled.put(e, List.copyOf(lines));
        return true;
    }
}
