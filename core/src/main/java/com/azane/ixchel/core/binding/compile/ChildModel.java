package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.TopLayer;

import java.util.List;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * The view model of one Java component instance and its view's bindings, a part after its
 * parameter defaults: its first update joins the models polled each pass and polls the model, with
 * every port readable, so the bindings start from real values. It closes with the copy holding the
 * instance.
 *
 * <p>While the shell is inside an element of a top layer kind ({@link LayerFacet}: a tooltip, a drag
 * preview, a dialog, a select) that is not in the top layer, the instance is dormant: neither the
 * model is polled, its first poll included, nor its bindings updated. A pass in which that element
 * is in the top layer when the pass starts or once a binding opens it polls the model before its
 * bindings, before style and layout, so it shows current values at once.
 */
final class ChildModel implements Part {
    private final ViewModel model;
    private final Element shell;
    private final TopLayer topLayer;
    private final Part.Group body;
    private final List<ChildModel> polled;
    private final Diagnostics diag;
    private boolean joined;
    /**
     * Whether a pass skipped the instance, the first one included, since its model was last polled:
     * its bindings may lag behind their inputs too.
     */
    private boolean behind = true;
    private boolean closed;

    /**
     * @param body   the view's bindings, updated only after the model's first poll
     * @param polled the document's component models polled each pass, after its page's
     * @param diag   where a failure to close is reported, at the shell
     */
    ChildModel(ViewModel model, Element shell, TopLayer topLayer, Part.Group body, List<ChildModel> polled,
               Diagnostics diag) {
        this.model = model;
        this.shell = shell;
        this.topLayer = topLayer;
        this.body = body;
        this.polled = polled;
        this.diag = diag;
    }

    /** Whether the innermost top layer kind element around the shell is out of the top layer. */
    boolean dormant() {
        for (Element e = shell; e != null; e = e.parent()) {
            if (e.type().facet(LayerFacet.KEY) != LayerFacet.NONE) {
                return !topLayer.shows(e);
            }
        }
        return false;
    }

    /** Polls the model's sources unless dormant; whether a value changed or the instance woke up. */
    boolean poll() {
        if (dormant()) {
            behind = true;
            return false;
        }
        boolean woke = behind;
        behind = false;
        return MODEL.poll(model) || woke;
    }

    @Override
    public boolean update() {
        if (!joined) {
            joined = true;
            polled.add(this);
        }
        if (dormant()) {
            return false;
        }
        if (behind) {
            poll();
        }
        return body.update();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        polled.remove(this);
        body.close();
        try {
            model.close();
        } catch (RuntimeException ex) {
            diag.report(BindingCode.RUNTIME_EXCEPTION, shell.pos(), "closing " + model.getClass().getSimpleName() + " threw " + ex);
        }
    }
}
