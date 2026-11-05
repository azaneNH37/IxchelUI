package com.azane.ixchel.core.html;

import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.view.ContentModel;
import com.azane.ixchel.core.view.TagRegistry;
import java.util.List;

/** Internal composition root. Pipeline packages consume contracts, never this vocabulary. */
public final class HtmlElements {
    private HtmlElements() {}

    /** The engine's elements: the HTML subset and its top-layer extensions. */
    public static final List<ElementType> ALL = List.of(
            ElementType.plain("div"), ElementType.plain("span"), ElementType.plain("a"),
            ImageElement.TYPE, ButtonElement.TYPE, InputElement.TYPE, SelectElement.TYPE, SelectElement.OPTION,
            DialogElement.TYPE, DetailsElement.TYPE, DetailsElement.SUMMARY,
            pointer("ix-tooltip", "the tooltip", LayerFacet.Placement.POINTER_OFFSET, LayerFacet.Pointer.TOOLTIP),
            pointer("ix-drag-preview", "the drag preview", LayerFacet.Placement.POINTER_CENTER, LayerFacet.Pointer.DRAG_PREVIEW));

    public static final TagRegistry TAGS = TagRegistry.builder().addAll(ALL).build();

    private static ElementType pointer(String tag, String description, LayerFacet.Placement placement, LayerFacet.Pointer role) {
        return ElementType.builder(tag).facet(ContentModel.KEY, HtmlContent.pointer(description))
                .facet(LayerFacet.KEY, new LayerFacet(placement, role, false, true, true, false, false, false, false)).build();
    }
}
