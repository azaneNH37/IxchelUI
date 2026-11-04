package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.effect.Aliases;

import java.util.List;
import java.util.Objects;

/**
 * {@code bind:style:--name}: writes the element's bound value of one custom property. A String is
 * its CSS text, a Number a plain number; {@code null} removes the value. Text that does not tokenize
 * keeps the last good value and is reported once until a good value arrives. A String names effects by
 * the aliases of the view's stylesheet.
 */
final class CustomPropertyBinding extends Binding {
    private static final Object NONE = new Object();
    private final Element element;
    private final String name;
    private final Aliases aliases;
    private Object last = NONE;
    private boolean invalid;

    CustomPropertyBinding(Cell<?> cell, Directive a, Diagnostics diag, Element element, String name,
                          Aliases aliases) {
        super(cell, a, diag);
        this.element = element;
        this.name = name;
        this.aliases = aliases;
    }

    @Override
    boolean write(Object value) {
        if (Objects.equals(value, last)) {
            return false;
        }
        last = value;
        SourcePos pos = attribute.valuePos();
        List<ComponentValue> css = null;
        if (value instanceof Number n) {
            css = List.of(new Token(Token.Kind.NUMBER, "", n.doubleValue(), "", pos));
        } else if (value != null) {
            Diagnostics problems = new Diagnostics();
            css = CssParser.parseValue(value.toString(), pos, problems);
            if (!problems.hasErrors()) {
                css = aliases.value(css, problems);
            }
            if (problems.hasErrors()) {
                if (!invalid) {
                    invalid = true;
                    diag.report(StyleBinding.code(problems), pos, "'" + attribute.value() + "' gave '" + value
                            + "' for '" + name + "': " + problems.entries().get(0).message());
                }
                return false;
            }
        }
        invalid = false;
        if (Objects.equals(css, element.boundCustom().get(name))) {
            return false;
        }
        element.setBoundCustom(name, css);
        return true;
    }
}
