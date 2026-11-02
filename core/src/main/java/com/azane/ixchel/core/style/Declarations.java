package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.PropertyCode;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Shorthand;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates declarations against the registry, expanding shorthands into longhands. A value with
 * {@code var()} is checked only for well-formed {@code var()}s here; it is substituted and parsed
 * for each element when its style is computed ({@link Pending}). So is a value with a relative
 * length unit ({@link Units}), which is checked here against stand-in sizes.
 */
public final class Declarations {
    private Declarations() {
    }

    /** CSS-wide keywords, stored in place of a value. */
    public enum Wide { INHERIT, INITIAL }

    /** {@code value} is the property's value type, a {@link Wide} or a {@link Pending}. */
    public record Longhand(PropertyDef<?> property, Object value, boolean important) {
    }

    /** {@code --name: value}; {@code value} is kept as written, {@code var()}s included. */
    public record Custom(String name, List<ComponentValue> value, boolean important) {
    }

    /** One declaration block, in source order within each list. */
    public record Compiled(List<Longhand> longhands, List<Custom> customs) {
        public static final Compiled EMPTY = new Compiled(List.of(), List.of());
    }

    /**
     * The value of one longhand of a declaration that contains {@code var()} or a relative unit. The
     * longhands of one shorthand share their {@link Source}, which substitutes and expands once per
     * distinct input.
     */
    public record Pending(Source source, int index) {
        /**
         * The value for an element whose custom properties are {@code custom}, with relative units
         * against {@code units}: the property's value type or a {@link Wide}; {@code null} when the
         * value is invalid at computed-value time, reported once per declaration.
         */
        Object resolve(Map<String, List<ComponentValue>> custom, Units units, Diagnostics diag) {
            Object[] values = source.resolve(custom, units, diag);
            return values == null ? null : values[index];
        }
    }

    /** A declaration with {@code var()} or a relative unit, for {@link Pending}. */
    public static final class Source {
        private final String name;
        private final List<ComponentValue> value;
        private final SourcePos pos;
        private final PropertyDef<?> property;
        private final Shorthand shorthand;
        private boolean reported;
        private List<ComponentValue> lastInput;
        private Object[] lastOutput;

        private Source(String name, List<ComponentValue> value, SourcePos pos, PropertyDef<?> property, Shorthand shorthand) {
            this.name = name;
            this.value = value;
            this.pos = pos;
            this.property = property;
            this.shorthand = shorthand;
        }

        Object[] resolve(Map<String, List<ComponentValue>> custom, Units units, Diagnostics diag) {
            List<ComponentValue> substituted = Vars.substitute(value, custom::get);
            if (substituted == null) {
                report(diag, StyleCode.UNDEFINED_VAR,
                        "'" + name + "' uses a custom property that is not defined here, without a fallback");
                return null;
            }
            substituted = units.rewrite(substituted);
            if (Objects.equals(substituted, lastInput)) {
                return lastOutput;
            }
            Diagnostics problems = new Diagnostics();
            Object[] out;
            if (property != null) {
                Object v = value(property, substituted, pos, problems);
                out = v == null ? null : new Object[] {v};
            } else {
                Wide wide = wideKeyword(substituted);
                if (wide != null) {
                    out = new Object[shorthand.longhands().size()];
                    Arrays.fill(out, wide);
                } else {
                    out = substituted.isEmpty() ? null : shorthand.expander().expand(substituted, pos, problems);
                }
            }
            if (out == null) {
                report(diag, StyleCode.INVALID_AFTER_VAR,
                        "'" + name + "' is invalid after var() substitution: " + firstError(problems));
            }
            lastInput = substituted;
            lastOutput = out;
            return out;
        }

        private void report(Diagnostics diag, StyleCode code, String message) {
            if (!reported) {
                reported = true;
                diag.report(code, pos, message);
            }
        }

        private static String firstError(Diagnostics problems) {
            for (Diagnostic d : problems.entries()) {
                if (d.severity() == Diagnostic.Severity.ERROR) {
                    return d.message();
                }
            }
            return "empty value";
        }
    }

    /** Invalid declarations are reported and dropped; the rest keep source order. */
    public static Compiled compile(List<Rule.Declaration> declarations, PropertyRegistry registry, Diagnostics diag) {
        List<Longhand> longhands = new ArrayList<>(declarations.size());
        List<Custom> customs = new ArrayList<>(0);
        for (Rule.Declaration d : declarations) {
            compileOne(d, registry, diag, longhands, customs);
        }
        return longhands.isEmpty() && customs.isEmpty() ? Compiled.EMPTY
                : new Compiled(List.copyOf(longhands), List.copyOf(customs));
    }

    /**
     * A longhand's value as a declaration in a stylesheet gives it, without {@code var()}: the
     * property's value type or a {@link Wide}; {@code null} after reporting an invalid or empty value.
     */
    public static Object value(PropertyDef<?> property, List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        if (value.isEmpty()) {
            diag.report(PropertyCode.MISSING_VALUE, pos, "missing value for '" + property.name() + "'");
            return null;
        }
        if (Units.contains(value)) {
            return parse(property, Units.sample().rewrite(value), pos, diag) == null ? null
                    : new Pending(new Source(property.name(), value, pos, property, null), 0);
        }
        return parse(property, value, pos, diag);
    }

    private static Object parse(PropertyDef<?> property, List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        Wide wide = wideKeyword(value);
        return wide != null ? wide : property.syntax().parse(value, pos, diag);
    }

    private static void compileOne(Rule.Declaration d, PropertyRegistry registry, Diagnostics diag,
                                   List<Longhand> out, List<Custom> customs) {
        if (d.name().startsWith("--")) {
            if (Vars.check(d.value(), diag)) {
                customs.add(new Custom(d.name(), d.value(), d.important()));
            }
            return;
        }
        PropertyDef<?> def = registry.byName(d.name());
        Shorthand shorthand = def == null ? registry.shorthand(d.name()) : null;
        if (def == null && shorthand == null) {
            diag.report(PropertyCode.UNKNOWN_PROPERTY, d.pos(), "unsupported property '" + d.name() + "'");
            return;
        }
        if (Vars.contains(d.value())) {
            if (Vars.check(d.value(), diag)) {
                Source source = new Source(d.name(), d.value(), d.pos(), def, shorthand);
                List<PropertyDef<?>> targets = def != null ? List.of(def) : shorthand.longhands();
                for (int i = 0; i < targets.size(); i++) {
                    out.add(new Longhand(targets.get(i), new Pending(source, i), d.important()));
                }
            }
            return;
        }
        if (def != null) {
            Object v = value(def, d.value(), d.pos(), diag);
            if (v != null) {
                out.add(new Longhand(def, v, d.important()));
            }
            return;
        }
        if (d.value().isEmpty()) {
            diag.report(PropertyCode.MISSING_VALUE, d.pos(), "missing value for '" + d.name() + "'");
            return;
        }
        Wide wide = wideKeyword(d.value());
        if (wide != null) {
            for (PropertyDef<?> l : shorthand.longhands()) {
                out.add(new Longhand(l, wide, d.important()));
            }
            return;
        }
        if (Units.contains(d.value())) {
            if (shorthand.expander().expand(Units.sample().rewrite(d.value()), d.pos(), diag) != null) {
                Source source = new Source(d.name(), d.value(), d.pos(), null, shorthand);
                for (int i = 0; i < shorthand.longhands().size(); i++) {
                    out.add(new Longhand(shorthand.longhands().get(i), new Pending(source, i), d.important()));
                }
            }
            return;
        }
        Object[] values = shorthand.expander().expand(d.value(), d.pos(), diag);
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                out.add(new Longhand(shorthand.longhands().get(i), values[i], d.important()));
            }
        }
    }

    static Wide wideKeyword(List<ComponentValue> value) {
        if (value.size() == 1 && value.get(0) instanceof Token t) {
            if (t.isIdent("inherit")) {
                return Wide.INHERIT;
            }
            if (t.isIdent("initial")) {
                return Wide.INITIAL;
            }
        }
        return null;
    }
}
