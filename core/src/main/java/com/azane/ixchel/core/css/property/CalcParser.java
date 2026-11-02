package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code calc()}, {@code min()}, {@code max()} and {@code clamp()} of lengths: {@code + - * /},
 * parentheses and nesting, with {@code +} and {@code -} between spaces as CSS requires. Relative units
 * are pixels by the time a value is parsed for an element (see {@code style.Units}). Pixels fold to one
 * number and pixels with percentages to one {@link Calc.Linear}; a minimum or maximum over a percentage
 * stays a tree for layout to resolve.
 */
final class CalcParser {
    private CalcParser() {
    }

    /** A length, or a plain number when {@code length} is {@code null}. */
    private record Term(Calc length, double number) {
        boolean isNumber() {
            return length == null;
        }
    }

    static boolean isMath(ComponentValue v) {
        if (!(v instanceof ComponentValue.Function f)) {
            return false;
        }
        String n = f.name().toLowerCase(Locale.ROOT);
        return n.equals("calc") || n.equals("min") || n.equals("max") || n.equals("clamp");
    }

    /** A length from a math function; below 0 it is 0 unless {@code negative}; {@code null} after reporting. */
    static Length length(ComponentValue.Function f, boolean percent, boolean negative, Diagnostics diag) {
        Term t = function(f, percent, diag);
        if (t == null) {
            return null;
        }
        if (t.isNumber()) {
            diag.report(PropertyCode.INVALID_VALUE, f.pos(), f.name() + "() gives a number where a length is expected");
            return null;
        }
        return Length.of(negative ? t.length : Calc.nonNegative(t.length));
    }

    private static Term function(ComponentValue.Function f, boolean percent, Diagnostics diag) {
        String name = f.name().toLowerCase(Locale.ROOT);
        if (name.equals("calc")) {
            return sum(f.args(), f.pos(), percent, diag);
        }
        List<Term> args = new ArrayList<>(3);
        List<ComponentValue> current = new ArrayList<>();
        List<List<ComponentValue>> parts = new ArrayList<>();
        for (ComponentValue v : f.args()) {
            if (v instanceof Token t && t.is(Token.Kind.COMMA)) {
                parts.add(current);
                current = new ArrayList<>();
            } else {
                current.add(v);
            }
        }
        parts.add(current);
        for (List<ComponentValue> p : parts) {
            Term t = sum(p, f.pos(), percent, diag);
            if (t == null) {
                return null;
            }
            if (!args.isEmpty() && args.get(0).isNumber() != t.isNumber()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), name + "() mixes numbers and lengths");
                return null;
            }
            args.add(t);
        }
        if (name.equals("clamp") && args.size() != 3) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "clamp() takes a minimum, a value and a maximum");
            return null;
        }
        if (args.get(0).isNumber()) {
            double v = name.equals("clamp") ? Math.max(args.get(0).number, Math.min(args.get(1).number, args.get(2).number))
                    : args.get(0).number;
            if (!name.equals("clamp")) {
                for (Term t : args) {
                    v = name.equals("min") ? Math.min(v, t.number) : Math.max(v, t.number);
                }
            }
            return new Term(null, v);
        }
        if (allPixels(args)) {
            float v;
            if (name.equals("clamp")) {
                v = Math.max(px(args.get(0)), Math.min(px(args.get(1)), px(args.get(2))));
            } else {
                v = px(args.get(0));
                for (Term t : args) {
                    v = name.equals("min") ? Math.min(v, px(t)) : Math.max(v, px(t));
                }
            }
            return new Term(new Calc.Linear(v, 0), 0);
        }
        List<Calc> of = new ArrayList<>(args.size());
        for (Term t : args) {
            of.add(t.length);
        }
        return new Term(switch (name) {
            case "min" -> new Calc.Min(List.copyOf(of));
            case "max" -> new Calc.Max(List.copyOf(of));
            default -> new Calc.Clamp(of.get(0), of.get(1), of.get(2));
        }, 0);
    }

    private static boolean allPixels(List<Term> args) {
        for (Term t : args) {
            if (!(t.length instanceof Calc.Linear l && l.percent() == 0)) {
                return false;
            }
        }
        return true;
    }

    private static float px(Term t) {
        return ((Calc.Linear) t.length).px();
    }

    /** Products joined by {@code +} and {@code -}, each operator between whitespace. */
    private static Term sum(List<ComponentValue> raw, SourcePos pos, boolean percent, Diagnostics diag) {
        Term acc = null;
        int sign = 1;
        List<ComponentValue> product = new ArrayList<>();
        for (int i = 0; i <= raw.size(); i++) {
            ComponentValue v = i < raw.size() ? raw.get(i) : null;
            boolean op = v instanceof Token t && (t.isDelim('+') || t.isDelim('-'));
            if (op && !(spaced(raw, i - 1) && spaced(raw, i + 1))) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, v.pos(), "'" + ((Token) v).value() + "' needs a space on both sides");
                return null;
            }
            if (v != null && !op) {
                if (!(v instanceof Token t && t.is(Token.Kind.WHITESPACE))) {
                    product.add(v);
                }
                continue;
            }
            if (product.isEmpty()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, v != null ? v.pos() : pos, "missing a value in a calculation");
                return null;
            }
            Term t = product(product, percent, diag);
            if (t == null) {
                return null;
            }
            if (acc == null) {
                acc = t;
            } else if (acc.isNumber() != t.isNumber()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, product.get(0).pos(), "cannot add a number and a length");
                return null;
            } else {
                acc = acc.isNumber() ? new Term(null, acc.number + sign * t.number) : new Term(add(acc.length, t.length, sign), 0);
            }
            product.clear();
            if (v != null) {
                sign = ((Token) v).isDelim('+') ? 1 : -1;
            }
        }
        return acc;
    }

    private static boolean spaced(List<ComponentValue> raw, int i) {
        return i >= 0 && i < raw.size() && raw.get(i) instanceof Token t && t.is(Token.Kind.WHITESPACE);
    }

    /** Values joined by {@code *} and {@code /}; a length is scaled by numbers only. */
    private static Term product(List<ComponentValue> items, boolean percent, Diagnostics diag) {
        Term acc = value(items.get(0), percent, diag);
        for (int i = 1; acc != null && i < items.size(); i += 2) {
            ComponentValue op = items.get(i);
            boolean times = op instanceof Token t && t.isDelim('*');
            if (!times && !(op instanceof Token t && t.isDelim('/')) || i + 1 >= items.size()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, op.pos(), "expected + - * or / between values");
                return null;
            }
            Term r = value(items.get(i + 1), percent, diag);
            if (r == null) {
                return null;
            }
            if (!times && !r.isNumber()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, op.pos(), "can only divide by a number");
                return null;
            }
            if (!times && r.number == 0) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, op.pos(), "division by zero");
                return null;
            }
            if (!acc.isNumber() && !r.isNumber()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, op.pos(), "cannot multiply two lengths");
                return null;
            }
            double k = times ? (acc.isNumber() ? acc.number : r.number) : 1 / r.number;
            Calc len = acc.isNumber() ? r.length : acc.length;
            if (acc.isNumber() && r.isNumber()) {
                acc = new Term(null, times ? acc.number * r.number : acc.number / r.number);
            } else {
                acc = new Term(scale(len, (float) k), 0);
            }
        }
        return acc;
    }

    private static Term value(ComponentValue v, boolean percent, Diagnostics diag) {
        if (v instanceof Token t) {
            switch (t.kind()) {
                case NUMBER:
                    return new Term(null, t.number());
                case DIMENSION:
                    if (t.unit().equalsIgnoreCase("px")) {
                        return new Term(new Calc.Linear((float) t.number(), 0), 0);
                    }
                    diag.report(PropertyCode.UNSUPPORTED_UNIT, t.pos(), "unsupported unit '" + t.unit() + "'",
                            "use px, em, rem, vw or vh");
                    return null;
                case PERCENTAGE:
                    if (percent) {
                        return new Term(new Calc.Linear(0, (float) t.number()), 0);
                    }
                    break;
                default:
                    break;
            }
        } else if (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LPAREN) {
            return sum(b.contents(), b.pos(), percent, diag);
        } else if (isMath(v)) {
            return function((ComponentValue.Function) v, percent, diag);
        }
        diag.report(PropertyCode.FUNCTION_ARGUMENTS, v.pos(), "expected a number, a length" + (percent ? ", a percentage" : "")
                + " or a nested calculation");
        return null;
    }

    private static Calc add(Calc a, Calc b, int sign) {
        if (a instanceof Calc.Linear x && b instanceof Calc.Linear y) {
            return new Calc.Linear(x.px() + sign * y.px(), x.percent() + sign * y.percent());
        }
        return new Calc.Sum(a, sign < 0 ? scale(b, -1) : b);
    }

    private static Calc scale(Calc c, float k) {
        return c instanceof Calc.Linear l ? new Calc.Linear(l.px() * k, l.percent() * k) : new Calc.Scale(c, k);
    }
}
