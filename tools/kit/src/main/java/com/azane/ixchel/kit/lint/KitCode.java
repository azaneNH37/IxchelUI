package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of the association between templates and view models that only a whole-project view finds. */
public enum KitCode implements DiagCode {
    MISSING_TEMPLATE("model/missing-template", Severity.ERROR,
            "A template a view model's @UiTemplate names, or a pattern it lists, matches no file in the project's resources."),
    INVALID_TEMPLATE_ID("model/invalid-template-id", Severity.ERROR,
            "A @UiTemplate entry is not a full id 'namespace:path'."),
    NOT_A_VIEW_MODEL("model/not-a-view-model", Severity.ERROR,
            "@UiTemplate is on a class that does not extend ViewModel."),
    UNCHECKED_TEMPLATE("model/unchecked-template", Severity.WARNING,
            "A @UiTemplate id is in a namespace outside the project's resources, so the lint does not check it."),
    UNASSOCIATED_TEMPLATE("model/unassociated-template", Severity.WARNING,
            "No view model claims the page with @UiTemplate, so only its structure is checked, not its bindings.");

    private final String id;
    private final Severity severity;
    private final String summary;

    KitCode(String id, Severity severity, String summary) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String summary() {
        return summary;
    }
}
