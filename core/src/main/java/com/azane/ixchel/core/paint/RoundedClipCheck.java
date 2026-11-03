package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CornerRadius;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.style.StyleEngine;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Warns where an element both rounds its corners and clips its overflow: the {@link Painter} clips to
 * the padding box's rectangle, so children show past the curve. Runs on every restyle, so lint finds
 * the template's styles and a loaded document the states it reaches; each position is reported once.
 */
public final class RoundedClipCheck implements StyleEngine.Hook {
    private final Diagnostics diag;
    private final Set<SourcePos> reported = new HashSet<>();

    public RoundedClipCheck(Diagnostics diag) {
        this.diag = diag;
    }

    @Override
    public void computed(Element e, ComputedStyle previous, ComputedStyle next, StyleEngine.Cascade cascade) {
        if (roundedClip(next) && (previous == null || !roundedClip(previous)) && reported.add(e.pos())) {
            diag.report(PaintCode.ROUNDED_OVERFLOW_CLIP, e.pos(), "<" + e.tag() + "> has rounded corners and overflow: "
                    + next.get(CssProperties.OVERFLOW).name().toLowerCase(Locale.ROOT)
                    + "; its children are clipped to a rectangle, not to the curve");
        }
    }

    private static boolean roundedClip(ComputedStyle cs) {
        return cs.get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE
                && !(cs.get(CssProperties.BORDER_TOP_LEFT_RADIUS).equals(CornerRadius.ZERO)
                && cs.get(CssProperties.BORDER_TOP_RIGHT_RADIUS).equals(CornerRadius.ZERO)
                && cs.get(CssProperties.BORDER_BOTTOM_RIGHT_RADIUS).equals(CornerRadius.ZERO)
                && cs.get(CssProperties.BORDER_BOTTOM_LEFT_RADIUS).equals(CornerRadius.ZERO));
    }
}
