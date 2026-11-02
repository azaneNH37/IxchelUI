package com.azane.ixchel.core.html;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.layout.LayoutFacet;
import com.azane.ixchel.core.paint.PaintFacet;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.Painter;
import com.azane.ixchel.core.template.ContentModel;
import com.azane.ixchel.core.template.TemplateCode;

final class ImageElement {
    private ImageElement() {}
    static final ContentModel CONTENT = new HtmlContent.TooltipOnly() {
        @Override public void attributes(Element e, Diagnostics d) {
            if (e.attribute("src") == null && e.directives().stream().noneMatch(a -> a.name().equals("bind:src"))) {
                d.report(TemplateCode.IMG_WITHOUT_SOURCE, e.pos(), "<img> requires a 'src' attribute or a 'bind:src' binding");
            }
        }
    };

    static final PaintFacet PAINT = new PaintFacet() {
        @Override public void paint(Element e, float y, DisplayList out, Context context) {
            LayoutBox b = e.box();
            out.add(new DrawCommand.Image(b.x(), y, b.width(), b.height(), e.attribute("src"), 0, 0, 1, 1, context.fade(0xFFFFFFFF),
                    Painter.filter(e.computed())));
        }
    };
    static final ElementType TYPE = ElementType.builder("img", "src")
            .facet(ContentModel.KEY, CONTENT).facet(LayoutFacet.KEY, new LayoutFacet() {
                @Override public boolean atomic() { return true; }
            }).facet(PaintFacet.KEY, PAINT).build();
}
