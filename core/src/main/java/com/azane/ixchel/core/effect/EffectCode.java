package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Effect files that cannot be used, and styles that use effects wrongly. */
public enum EffectCode implements DiagCode {
    INVALID_HEADER("effect/invalid-header", Severity.ERROR,
            "A kind or param statement at the top of an .ixeffect file is malformed."),
    MISSING_KIND("effect/missing-kind", Severity.ERROR,
            "An .ixeffect file does not start with its kind: kind box; or kind layer;."),
    UNSUPPORTED_KIND("effect/unsupported-kind", Severity.ERROR,
            "The effect's kind is not box or layer; backdrop effects are not supported yet."),
    INVALID_NAME("effect/invalid-name", Severity.ERROR,
            "An effect file or parameter name is not lower-case letters, digits and '-', starting with a letter."),
    MISSING_DESCRIPTOR("effect/missing-descriptor", Severity.ERROR,
            "A param lacks one of syntax, inherits and initial-value, which are all required."),
    UNSUPPORTED_SYNTAX("effect/unsupported-syntax", Severity.ERROR,
            "A param's syntax is not <number>, <length>, <percentage>, <color>, <angle> or <time>."),
    DUPLICATE_PARAM("effect/duplicate-param", Severity.ERROR,
            "An effect declares two params of the same name."),
    INVALID_ALIAS("effect/invalid-alias", Severity.ERROR,
            "The alias after 'as' in an effect @link is not PascalCase: an upper-case letter, then letters and digits."),
    DUPLICATE_ALIAS("effect/duplicate-alias", Severity.ERROR,
            "A css file links two effect files under the same alias; rename one with 'as'."),
    UNKNOWN_ALIAS("effect/unknown-alias", Severity.ERROR,
            "paint(), -ix-paint() or a --Alias-param property names an alias the file does not link."),
    WRONG_KIND("effect/wrong-kind", Severity.ERROR,
            "A box effect is used in filter, or a layer effect in background-image."),
    UNKNOWN_PARAM("effect/unknown-param", Severity.ERROR,
            "A --Alias-param property names a param the effect does not have, or no param at all."),
    UNMARKED_PARAM("effect/unmarked-param", Severity.WARNING,
            "A lower-case custom property spells a linked effect's param; params are written --Alias-param."),
    COMPILE_FAILED("effect/compile-failed", Severity.ERROR,
            "The effect's GLSL does not compile; box effects are skipped and layer effects composite without it.");

    private final String id;
    private final Severity severity;
    private final String summary;

    EffectCode(String id, Severity severity, String summary) {
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
