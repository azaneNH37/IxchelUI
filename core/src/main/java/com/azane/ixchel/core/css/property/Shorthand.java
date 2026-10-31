package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
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
                diag.report(PropertyCode.VALUE_COUNT, parts.size() > 4 ? parts.get(4).pos() : pos, "expected 1 to 4 values");
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
                diag.report(PropertyCode.VALUE_COUNT, parts.size() > 2 ? parts.get(2).pos() : pos, "expected 1 or 2 values");
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

    /** {@code background-position}: x then y; a single value is x, and y is centered ({@code 50%}) as in CSS. */
    public static Expander position(Syntax<Length> syntax) {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = Syntaxes.significant(value);
            if (parts.isEmpty() || parts.size() > 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.size() > 2 ? parts.get(2).pos() : pos, "expected 1 or 2 values");
                return null;
            }
            Length x = syntax.parse(List.of(parts.get(0)), parts.get(0).pos(), diag);
            if (x == null) {
                return null;
            }
            if (parts.size() == 1) {
                return new Object[]{x, Length.percent(50)};
            }
            Length y = syntax.parse(List.of(parts.get(1)), parts.get(1).pos(), diag);
            return y == null ? null : new Object[]{x, y};
        };
    }

    /**
     * {@code border} ({@code sides} 4) and {@code border-top} etc. ({@code sides} 1): a width, a style
     * and a color in any order, each at most once; omitted ones are reset to their initial values.
     * Longhands: the {@code sides} widths, then the styles, then the colors. A number or dimension is
     * the width, a style keyword the style, anything else the color.
     */
    public static Expander border(Syntax<Length> width, Syntax<Keywords.BorderStyle> style, Syntax<Integer> color,
                                  Length initialWidth, Keywords.BorderStyle initialStyle, int initialColor, int sides) {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = Syntaxes.significant(value);
            if (parts.isEmpty() || parts.size() > 3) {
                diag.report(PropertyCode.VALUE_COUNT, parts.size() > 3 ? parts.get(3).pos() : pos,
                        "expected a width, a style and a color, each at most once");
                return null;
            }
            Object w = null;
            Object s = null;
            Object c = null;
            for (ComponentValue v : parts) {
                List<ComponentValue> one = List.of(v);
                if (v instanceof Token t && (t.is(Token.Kind.DIMENSION) || t.is(Token.Kind.NUMBER))) {
                    if (w != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "more than one width");
                        return null;
                    }
                    w = width.parse(one, v.pos(), diag);
                    if (w == null) {
                        return null;
                    }
                } else if (v instanceof Token t && t.is(Token.Kind.IDENT) && isBorderStyle(t.value())) {
                    if (s != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "more than one style");
                        return null;
                    }
                    s = style.parse(one, v.pos(), diag);
                } else {
                    if (c != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "more than one color");
                        return null;
                    }
                    c = color.parse(one, v.pos(), diag);
                    if (c == null) {
                        return null;
                    }
                }
            }
            Object[] out = new Object[sides * 3];
            for (int i = 0; i < sides; i++) {
                out[i] = w == null ? initialWidth : w;
                out[sides + i] = s == null ? initialStyle : s;
                out[2 * sides + i] = c == null ? initialColor : c;
            }
            return out;
        };
    }

    private static boolean isBorderStyle(String ident) {
        for (Keywords.BorderStyle s : Keywords.BorderStyle.values()) {
            if (s.name().equalsIgnoreCase(ident)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code transition}: comma-separated items, each with at most one property name, duration,
     * easing and delay (the first time is the duration). Longhands: property, duration, timing
     * function, delay.
     */
    public static Expander transition(PropertyRegistry registry) {
        return (value, pos, diag) -> {
            List<List<ComponentValue>> items = Syntaxes.splitCommas(value, pos, diag);
            if (items == null) {
                return null;
            }
            List<String> properties = new ArrayList<>();
            List<Float> durations = new ArrayList<>();
            List<Easing> easings = new ArrayList<>();
            List<Float> delays = new ArrayList<>();
            boolean none = false;
            for (List<ComponentValue> item : items) {
                String property = null;
                Float duration = null;
                Easing easing = null;
                Float delay = null;
                for (ComponentValue v : item) {
                    if (v instanceof Token t && (t.is(Token.Kind.DIMENSION) || t.is(Token.Kind.NUMBER))) {
                        if (delay != null) {
                            diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "at most two times: duration then delay");
                            return null;
                        }
                        Float time = Syntaxes.timeOf(v, duration != null, diag);
                        if (time == null) {
                            return null;
                        }
                        if (duration == null) {
                            duration = time;
                        } else {
                            delay = time;
                        }
                    } else if (Syntaxes.looksLikeEasing(v)) {
                        if (easing != null) {
                            diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "more than one easing");
                            return null;
                        }
                        easing = Syntaxes.easingOf(v, diag);
                        if (easing == null) {
                            return null;
                        }
                    } else {
                        if (property != null) {
                            diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "more than one property");
                            return null;
                        }
                        property = Syntaxes.transitionNameOf(v, registry, diag);
                        if (property == null) {
                            return null;
                        }
                    }
                }
                if ("none".equals(property)) {
                    if (items.size() > 1) {
                        diag.report(PropertyCode.NONE_NOT_ALONE, item.get(0).pos(), "'none' must be the only item");
                        return null;
                    }
                    none = true;
                } else {
                    properties.add(property == null ? "all" : property);
                }
                durations.add(duration == null ? 0f : duration);
                easings.add(easing == null ? Easing.EASE : easing);
                delays.add(delay == null ? 0f : delay);
            }
            return new Object[]{none ? List.of() : List.copyOf(properties), List.copyOf(durations),
                    List.copyOf(easings), List.copyOf(delays)};
        };
    }
}
