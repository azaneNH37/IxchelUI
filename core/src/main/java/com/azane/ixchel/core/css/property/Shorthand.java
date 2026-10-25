package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/** A property that expands to longhands at parse time; computed styles only hold longhands. */
public record Shorthand(String name, List<PropertyDef<?>> longhands, Expander expander) {

    @FunctionalInterface
    public interface Expander {
        /** One value per longhand, in order; {@code null} after reporting an error. */
        Object[] expand(List<ComponentValue> value, SourcePos pos, Diagnostics diag);
    }

    /**
     * CSS box shorthand ({@code margin}, {@code padding}): 1-4 values for top, right, bottom, left,
     * each parsed with {@code syntax}; longhands must be given in that order.
     */
    public static Expander box(Syntax<?> syntax) {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = Syntaxes.significant(value);
            if (parts.isEmpty() || parts.size() > 4) {
                diag.error(parts.size() > 4 ? parts.get(4).pos() : pos, "expected 1 to 4 values");
                return null;
            }
            Object[] v = new Object[parts.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = syntax.parse(List.of(parts.get(i)), parts.get(i).pos(), diag);
                if (v[i] == null) {
                    return null;
                }
            }
            return switch (v.length) {
                case 1 -> new Object[]{v[0], v[0], v[0], v[0]};
                case 2 -> new Object[]{v[0], v[1], v[0], v[1]};
                case 3 -> new Object[]{v[0], v[1], v[2], v[1]};
                default -> v;
            };
        };
    }

    /** {@code gap}: one value for both, or row then column. */
    public static Expander pair(Syntax<?> syntax) {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = Syntaxes.significant(value);
            if (parts.isEmpty() || parts.size() > 2) {
                diag.error(parts.size() > 2 ? parts.get(2).pos() : pos, "expected 1 or 2 values");
                return null;
            }
            Object a = syntax.parse(List.of(parts.get(0)), parts.get(0).pos(), diag);
            if (a == null) {
                return null;
            }
            if (parts.size() == 1) {
                return new Object[]{a, a};
            }
            Object b = syntax.parse(List.of(parts.get(1)), parts.get(1).pos(), diag);
            return b == null ? null : new Object[]{a, b};
        };
    }
}
