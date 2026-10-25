package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTest {
    private static Element el(String tag) {
        return new Element(tag, SourcePos.UNKNOWN);
    }

    @Test
    void appendingAttachedNodeMovesItInsteadOfSharingIt() {
        Element a = el("a");
        Element b = el("b");
        Element child = el("c");
        a.appendChild(child);
        b.appendChild(child);
        assertEquals(List.of(), a.children());
        assertEquals(List.of(child), b.children());
        assertSame(b, child.parent());
    }

    @Test
    void appendingAnAncestorIsRejectedAndLeavesTreeIntact() {
        Element root = el("root");
        Element mid = el("mid");
        root.appendChild(mid);
        assertThrows(IllegalArgumentException.class, () -> mid.appendChild(root));
        assertThrows(IllegalArgumentException.class, () -> mid.appendChild(mid));
        assertSame(root, mid.parent());
        assertEquals(List.of(), mid.children());
    }

    @Test
    void classChangeOnDeepElementMarksOnlyAncestorsAsHavingDirtyDescendants() {
        Element root = el("root");
        Element mid = el("mid");
        Element leaf = el("leaf");
        Element sibling = el("sibling");
        root.appendChild(mid);
        mid.appendChild(leaf);
        root.appendChild(sibling);
        for (Element e : List.of(root, mid, leaf, sibling)) {
            e.clearStyleDirty();
        }

        leaf.addClass("empty");
        assertTrue(leaf.isStyleDirty());
        assertTrue(mid.hasStyleDirtyDescendant());
        assertTrue(root.hasStyleDirtyDescendant());
        assertFalse(mid.isStyleDirty());
        assertFalse(sibling.isStyleDirty());
        assertFalse(sibling.hasStyleDirtyDescendant());

        leaf.clearStyleDirty();
        leaf.addClass("empty");
        assertFalse(leaf.isStyleDirty(), "re-adding an existing class is not a change");
        leaf.setState(Element.HOVER, false);
        assertFalse(leaf.isStyleDirty(), "clearing an unset state bit is not a change");
    }

    @Test
    void attachingUnstyledSubtreeToCleanTreeFlagsTheNewAncestors() {
        Element root = el("root");
        root.clearStyleDirty();
        Element fresh = el("fresh");
        root.appendChild(fresh);
        assertTrue(root.hasStyleDirtyDescendant());
    }
}
