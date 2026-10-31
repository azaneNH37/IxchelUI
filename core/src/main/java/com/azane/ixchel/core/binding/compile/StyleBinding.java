package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.style.Declarations;

import java.util.List;
import java.util.Objects;

/**
 * {@code bind:style:<property>}: writes the element's bound value of one longhand. An unchanged
 * value is not parsed again; an invalid one keeps the last valid value and is reported once
 * until a valid value arrives.
 */
final class StyleBinding extends Binding {
    private static final Object NONE = new Object();
    private final Element element;
    private final PropertyDef<?> property;
    private Object last = NONE;
    private boolean invalid;

    StyleBinding(Cell<?> cell, Directive a, Diagnostics diag, Element element, PropertyDef<?> property) {
        super(cell, a, diag);
        this.element = element;
        this.property = property;
    }

    @Override
    boolean write(Object value) {
        if (Objects.equals(value, last)) {
            return false;
        }
        last = value;
        Object parsed = null;
        if (value != null) {
            Diagnostics problems = new Diagnostics();
            parsed = parse(value, problems);
            if (parsed == null) {
                if (!invalid) {
                    invalid = true;
                    diag.report(BindingCode.INVALID_STYLE_VALUE, attribute.valuePos(), "'" + attribute.value() + "' gave "
                            + describe(value) + " for '" + property.name() + "': " + firstError(problems));
                }
                return false;
            }
        }
        invalid = false;
        if (Objects.equals(parsed, element.boundStyle().get(property))) {
            return false;
        }
        element.setBoundStyle(property, parsed);
        return true;
    }

    /** The property's value or a CSS-wide keyword; {@code null} after reporting to {@code problems}. */
    private Object parse(Object value, Diagnostics problems) {
        SourcePos pos = attribute.valuePos();
        if (value instanceof String s) {
            List<ComponentValue> css = CssParser.parseValue(s, pos, problems);
            return problems.hasErrors() ? null : Declarations.value(property, css, pos, problems);
        }
        if (value instanceof Number n) {
            String unit = property.syntax().numberUnit();
            double d = n.doubleValue();
            if (unit == null) {
                problems.report(BindingCode.INVALID_STYLE_VALUE, pos,
                        "this property takes no plain numbers; give a String with its CSS value");
                return null;
            }
            if (!Double.isFinite(d)) {
                problems.report(BindingCode.INVALID_STYLE_VALUE, pos, "not a finite number");
                return null;
            }
            Token t = unit.isEmpty() ? new Token(Token.Kind.NUMBER, "", d, "", pos)
                    : new Token(Token.Kind.DIMENSION, "", d, unit, pos);
            return property.syntax().parse(List.of(t), pos, problems);
        }
        problems.report(BindingCode.INVALID_STYLE_VALUE, pos, "expected a String or a Number");
        return null;
    }

    private static String describe(Object value) {
        if (value instanceof String s) {
            return "'" + s + "'";
        }
        return value instanceof Number ? value.toString() : "a " + value.getClass().getName();
    }

    private static String firstError(Diagnostics problems) {
        for (Diagnostic d : problems.entries()) {
            if (d.severity() == Diagnostic.Severity.ERROR) {
                return d.message();
            }
        }
        throw new IllegalStateException("no error reported");
    }
}
