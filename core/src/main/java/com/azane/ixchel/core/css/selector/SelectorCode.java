package com.azane.ixchel.core.css.selector;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of a rule's selector; the rule is dropped. */
public enum SelectorCode implements DiagCode {
    EXPECTED_SELECTOR("css/expected-selector", Severity.ERROR,
            "A selector is missing where one is expected, such as an empty item of a selector list or a combinator with nothing before it."),
    UNEXPECTED_TOKEN("css/unexpected-selector-token", Severity.ERROR,
            "A selector holds a token, block or function that is not part of the supported selector syntax."),
    UNSUPPORTED_COMBINATOR("css/unsupported-combinator", Severity.ERROR,
            "A selector uses the '+' or '~' combinator; only descendant and child ('>') combinators are supported."),
    CONFLICTING_IDS("css/conflicting-ids", Severity.ERROR,
            "A compound selector requires two different ids, so it can never match."),
    INVALID_ID("css/invalid-id-selector", Severity.ERROR,
            "A '#' in a selector is not followed by a valid identifier."),
    MISSING_CLASS_NAME("css/missing-class-name", Severity.ERROR,
            "A '.' in a selector is not followed by a class name."),
    MISSING_PSEUDO_NAME("css/missing-pseudo-name", Severity.ERROR,
            "A ':' in a selector is not followed by a pseudo-class name."),
    UNSUPPORTED_PSEUDO("css/unsupported-pseudo", Severity.ERROR,
            "A selector uses a pseudo-element or a pseudo-class other than :root, :lang(), :hover, :active, :focus, :disabled, :checked and :open."),
    INVALID_LANG("css/invalid-lang", Severity.ERROR,
            "A :lang() pseudo-class holds no language ranges, something other than identifiers or strings separated by commas, or appears twice in one compound selector."),
    INVALID_ATTRIBUTE("css/invalid-attribute-selector", Severity.ERROR,
            "An attribute selector is not of the form [name] or [name=value] with an identifier or quoted string value."),
    UNSUPPORTED_ATTRIBUTE_OPERATOR("css/unsupported-attribute-operator", Severity.ERROR,
            "An attribute selector uses an operator other than '=', such as '^=' or '~='.");

    private final String id;
    private final Severity severity;
    private final String summary;

    SelectorCode(String id, Severity severity, String summary) {
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
