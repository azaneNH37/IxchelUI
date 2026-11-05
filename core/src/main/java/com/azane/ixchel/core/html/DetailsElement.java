package com.azane.ixchel.core.html;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.FormFacet;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.event.InteractionFacet;
import com.azane.ixchel.core.view.ContentModel;
import com.azane.ixchel.core.view.ViewCode;
import java.util.List;

/**
 * {@code <details>} and its {@code <summary>}: {@code :open} while the details has an {@code open}
 * attribute; the user agent sheet hides every other child while it has none. Activating the summary
 * (a click on it or its non-focusable content, Enter or Space) flips the attribute and dispatches
 * {@code change} at the details, which a {@code bind:open} writes back.
 */
final class DetailsElement {
    private DetailsElement() {}

    static final ContentModel CONTENT = new ContentModel() {
        @Override public boolean children(Element e, List<Node> children, Diagnostics d) {
            boolean first = true;
            for (Node n : children) {
                if (n instanceof TextNode t) {
                    if (!t.text().isBlank()) {
                        d.report(ViewCode.DETAILS_WITHOUT_SUMMARY, t.pos(), "text inside <details> must be in an element",
                                "the content of a closed <details> is hidden by element");
                        first = false;
                    }
                    continue;
                }
                if (first && !(n instanceof Element s && s.type() == SUMMARY)) {
                    d.report(ViewCode.DETAILS_WITHOUT_SUMMARY, n.pos(), "<details> must start with a <summary>");
                }
                first = false;
            }
            if (first) d.report(ViewCode.DETAILS_WITHOUT_SUMMARY, e.pos(), "<details> must start with a <summary>");
            return true;
        }
    };

    static final ElementType TYPE = ElementType.builder("details", "open")
            .facet(ContentModel.KEY, CONTENT)
            .facet(FormFacet.KEY, new FormFacet() {
                @Override public boolean binds(Element e, String property) { return property.equals("open"); }
                @Override public boolean openByAttribute() { return true; }
            }).build();

    static final InteractionFacet SUMMARY_INTERACTION = new InteractionFacet() {
        @Override public boolean focusable() { return true; }
        @Override public boolean clickSoundBefore() { return true; }
        @Override public boolean activatedByDescendants() { return true; }
        @Override public void click(Element e, Actions a) { toggle(e, a); }
        @Override public boolean key(Element e, int key, int mods, Actions a) {
            if (key != FormEditing.KEY_SPACE && !FormEditing.isEnter(key)) return false;
            a.clickSound(e);
            UiEvent click = UiEvent.mouse(UiEvent.Type.CLICK, e, Float.NaN, Float.NaN, 0, 0);
            a.dispatch(click);
            if (!click.isConsumed()) toggle(e, a);
            return true;
        }
    };

    static final ElementType SUMMARY = ElementType.builder("summary").facet(InteractionFacet.KEY, SUMMARY_INTERACTION).build();

    /** Opens or closes the details {@code summary} is the first element child of; any other summary does nothing. */
    private static void toggle(Element summary, InteractionFacet.Actions a) {
        Element details = summary.parent();
        if (details == null || details.type() != TYPE || firstElement(details) != summary) return;
        if (details.attribute("open") != null) {
            details.removeAttribute("open");
        } else {
            details.setAttribute("open", "");
        }
        a.change(details);
    }

    private static Element firstElement(Element e) {
        for (Node n : e.children()) {
            if (n instanceof Element c && !c.type().facet(LayerFacet.KEY).atPointer()) return c;
        }
        return null;
    }
}
