package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * The view model of one Java component instance, a part between its parameter defaults and its
 * template's bindings: its first update polls the model, with every port readable, so those bindings
 * start from real values, and joins it to the models polled each pass. It closes with the copy
 * holding the instance.
 */
final class ChildModel implements Part {
    private final ViewModel model;
    private final List<ViewModel> polled;
    private final SourcePos at;
    private final Diagnostics diag;
    private boolean started;
    private boolean closed;

    /**
     * @param polled the document's models polled each pass, after its page's
     * @param at     the instance's shell, where a failure to close is reported
     */
    ChildModel(ViewModel model, List<ViewModel> polled, SourcePos at, Diagnostics diag) {
        this.model = model;
        this.polled = polled;
        this.at = at;
        this.diag = diag;
    }

    @Override
    public boolean update() {
        if (!started) {
            started = true;
            polled.add(model);
            MODEL.poll(model);
        }
        return false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        polled.remove(model);
        try {
            model.close();
        } catch (RuntimeException ex) {
            diag.report(BindingCode.RUNTIME_EXCEPTION, at, "closing " + model.getClass().getSimpleName() + " threw " + ex);
        }
    }
}
