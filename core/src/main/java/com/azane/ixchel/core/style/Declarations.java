package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Shorthand;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.List;

/** Validates declarations against the registry, expanding shorthands into longhands. */
public final class Declarations {
    private Declarations() {
    }

    /** CSS-wide keywords, stored in place of a value. */
    public enum Wide { INHERIT, INITIAL }

    /** {@code value} is the property's value type or a {@link Wide}. */
    public record Compiled(PropertyDef<?> property, Object value, boolean important) {
    }

    /** Invalid declarations are reported and dropped; the rest keep source order. */
    public static List<Compiled> compile(List<Rule.Declaration> declarations, PropertyRegistry registry, Diagnostics diag) {
        List<Compiled> out = new ArrayList<>(declarations.size());
        for (Rule.Declaration d : declarations) {
            compileOne(d, registry, diag, out);
        }
        return out;
    }

    private static void compileOne(Rule.Declaration d, PropertyRegistry registry, Diagnostics diag, List<Compiled> out) {
        if (d.name().startsWith("--")) {
            diag.error(d.pos(), "custom properties are not supported yet");
            return;
        }
        PropertyDef<?> def = registry.byName(d.name());
        Shorthand shorthand = def == null ? registry.shorthand(d.name()) : null;
        if (def == null && shorthand == null) {
            diag.error(d.pos(), "unsupported property '" + d.name() + "'");
            return;
        }
        if (d.value().isEmpty()) {
            diag.error(d.pos(), "missing value for '" + d.name() + "'");
            return;
        }
        Wide wide = wideKeyword(d.value());
        if (def != null) {
            Object v = wide != null ? wide : def.syntax().parse(d.value(), d.pos(), diag);
            if (v != null) {
                out.add(new Compiled(def, v, d.important()));
            }
            return;
        }
        if (wide != null) {
            for (PropertyDef<?> l : shorthand.longhands()) {
                out.add(new Compiled(l, wide, d.important()));
            }
            return;
        }
        Object[] values = shorthand.expander().expand(d.value(), d.pos(), diag);
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                out.add(new Compiled(shorthand.longhands().get(i), values[i], d.important()));
            }
        }
    }

    private static Wide wideKeyword(List<ComponentValue> value) {
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
