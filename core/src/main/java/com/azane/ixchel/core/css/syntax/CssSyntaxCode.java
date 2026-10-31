package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of a stylesheet's tokens and rule structure, before selectors and values are read. */
public enum CssSyntaxCode implements DiagCode {
    INVALID_ESCAPE("css/invalid-escape", Severity.ERROR,
            "A backslash is not followed by a valid escape."),
    UNCLOSED_COMMENT("css/unclosed-comment", Severity.WARNING,
            "A comment is still open at the end of the stylesheet."),
    UNCLOSED_STRING("css/unclosed-string", Severity.WARNING,
            "A string is still open at the end of the stylesheet."),
    NEWLINE_IN_STRING("css/newline-in-string", Severity.ERROR,
            "A string runs into a line break before its closing quote."),
    UNCLOSED_URL("css/unclosed-url", Severity.WARNING,
            "An unquoted url() is still open at the end of the stylesheet."),
    INVALID_URL("css/invalid-url", Severity.ERROR,
            "An unquoted url() holds whitespace inside the address, a quote, a '(' or an invalid escape."),
    UNCLOSED_BLOCK("css/unclosed-block", Severity.WARNING,
            "A block is still open at the end of the stylesheet."),
    UNSUPPORTED_AT_RULE("css/unsupported-at-rule", Severity.ERROR,
            "The stylesheet uses an at-rule, which is not supported; it is skipped."),
    EXPECTED_BLOCK("css/expected-block", Severity.ERROR,
            "A selector is not followed by a '{' block."),
    NESTED_RULE_NOT_ALLOWED("css/nested-rule-not-allowed", Severity.ERROR,
            "A nested rule appears in a declaration list that is not part of a stylesheet, such as an inline style."),
    EXPECTED_DECLARATION("css/expected-declaration", Severity.ERROR,
            "A declaration block holds something that is neither a declaration nor a nested rule."),
    MISSING_COLON("css/missing-colon", Severity.ERROR,
            "A property name is not followed by ':'.");

    private final String id;
    private final Severity severity;
    private final String summary;

    CssSyntaxCode(String id, Severity severity, String summary) {
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
