package com.azane.ixchel.core.binding.expr;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.expr.Types.Num;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Type-checks a parsed {@link Expr} and compiles it into a function of the distinct paths it reads.
 * Arithmetic follows Java's numeric promotion; {@code null} (a path through a {@code null} record) makes
 * arithmetic {@code null}, order comparisons and boolean operands false.
 */
public final class Expressions {
    /** A resolved path: the cell to read and its declared type. */
    public interface Operand {
        Cell<?> cell();

        Type type();
    }

    /** Resolves a path; reports and returns {@code null} on failure. */
    public interface Paths {
        Operand resolve(Expr.Path path);
    }

    /** Reports a problem at an offset of the directive value. */
    public interface Errors {
        void report(ExprCode code, int at, String message);
    }

    /** An expression's value as {@code eval} over the current values of {@code inputs}, of type {@code type}. */
    public record Compiled(Cell<?>[] inputs, Function<Object[], Object> eval, Type type) {
    }

    private interface Eval {
        Object eval(Object[] in);
    }

    private record Node(Type type, Eval eval) {
    }

    private final Paths paths;
    private final Function<Object, String> textOf;
    private final Errors errors;
    private final List<Cell<?>> inputs = new ArrayList<>();
    private final Map<String, Integer> inputIndex = new HashMap<>();
    private final List<Type> typeOfInput = new ArrayList<>();

    private Expressions(Paths paths, Function<Object, String> textOf, Errors errors) {
        this.paths = paths;
        this.textOf = textOf;
        this.errors = errors;
    }

    /**
     * Compiles {@code e}; {@code null} after reporting every problem found. A bare path compiles to a
     * function of its own cell; callers that need the path's cell itself resolve it directly.
     */
    public static Compiled compile(Expr e, Paths paths, Function<Object, String> textOf, Errors errors) {
        Expressions c = new Expressions(paths, textOf, errors);
        Node n = c.node(e);
        if (n == null) {
            return null;
        }
        Eval eval = n.eval;
        return new Compiled(c.inputs.toArray(new Cell<?>[0]), eval::eval, n.type);
    }

    private Node node(Expr e) {
        if (e instanceof Expr.Literal l) {
            Object v = l.value();
            return new Node(v.getClass(), in -> v);
        }
        if (e instanceof Expr.Path p) {
            return path(p);
        }
        if (e instanceof Expr.Unary u) {
            return unary(u);
        }
        if (e instanceof Expr.Binary b) {
            return binary(b);
        }
        return conditional((Expr.Conditional) e);
    }

    private Node path(Expr.Path p) {
        String key = p.text();
        Integer index = inputIndex.get(key);
        Operand ref;
        if (index == null) {
            ref = paths.resolve(p);
            if (ref == null) {
                return null;
            }
            index = inputs.size();
            inputs.add(ref.cell());
            inputIndex.put(key, index);
            typeOfInput.add(ref.type());
        }
        int k = index;
        return new Node(typeOfInput.get(k), in -> in[k]);
    }

    private Node unary(Expr.Unary u) {
        Node operand = node(u.operand());
        if (operand == null) {
            return null;
        }
        Eval x = operand.eval;
        if (u.op().equals("!")) {
            if (!Types.isBoolean(operand.type)) {
                errors.report(ExprCode.OPERAND_TYPE, u.at(), "'!' needs a Boolean, got " + operand.type.getTypeName());
                return null;
            }
            return new Node(Boolean.class, in -> !truth(x.eval(in)));
        }
        Num num = Num.of(operand.type);
        if (num == null) {
            errors.report(ExprCode.OPERAND_TYPE, u.at(), "'-' needs a number, got " + operand.type.getTypeName());
            return null;
        }
        return new Node(num.boxed, in -> {
            Object v = x.eval(in);
            return v == null ? null : arithmetic("-", num, num.convert(0), (Number) v);
        });
    }

    private Node binary(Expr.Binary b) {
        Node left = node(b.left());
        Node right = node(b.right());
        if (left == null || right == null) {
            return null;
        }
        Eval l = left.eval;
        Eval r = right.eval;
        String op = b.op();
        Num ln = Num.of(left.type);
        Num rn = Num.of(right.type);
        switch (op) {
            case "&&", "||" -> {
                if (!Types.isBoolean(left.type) || !Types.isBoolean(right.type)) {
                    errors.report(ExprCode.OPERAND_TYPE, b.at(), "'" + op + "' needs Booleans, got " + pair(left, right));
                    return null;
                }
                boolean and = op.equals("&&");
                return new Node(Boolean.class, in -> truth(l.eval(in)) == and ? truth(r.eval(in)) : !and);
            }
            case "==", "!=" -> {
                boolean equal = op.equals("==");
                if (ln != null && rn != null) {
                    Num k = Num.promote(ln, rn);
                    return new Node(Boolean.class, in -> {
                        Object x = l.eval(in);
                        Object y = r.eval(in);
                        boolean same = x == null || y == null ? x == y : relation("==", k, (Number) x, (Number) y);
                        return same == equal;
                    });
                }
                Class<?> lc = Types.boxed(left.type);
                Class<?> rc = Types.boxed(right.type);
                if (!lc.isAssignableFrom(rc) && !rc.isAssignableFrom(lc)) {
                    errors.report(ExprCode.UNRELATED_TYPES, b.at(), "'" + op + "' compares unrelated types "
                            + pair(left, right));
                    return null;
                }
                return new Node(Boolean.class, in -> Objects.equals(l.eval(in), r.eval(in)) == equal);
            }
            case "<", "<=", ">", ">=" -> {
                if (ln == null || rn == null) {
                    errors.report(ExprCode.OPERAND_TYPE, b.at(), "'" + op + "' needs numbers, got " + pair(left, right));
                    return null;
                }
                Num k = Num.promote(ln, rn);
                return new Node(Boolean.class, in -> {
                    Object x = l.eval(in);
                    Object y = r.eval(in);
                    if (x == null || y == null) {
                        return false;
                    }
                    return relation(op, k, (Number) x, (Number) y);
                });
            }
            default -> {
                if (op.equals("+") && (Types.boxed(left.type) == String.class || Types.boxed(right.type) == String.class)) {
                    return new Node(String.class, in -> textOf.apply(l.eval(in)) + textOf.apply(r.eval(in)));
                }
                if (ln == null || rn == null) {
                    errors.report(ExprCode.OPERAND_TYPE, b.at(), "'" + op + "' needs numbers"
                            + (op.equals("+") ? " or a String" : "") + ", got " + pair(left, right));
                    return null;
                }
                Num k = Num.promote(ln, rn);
                return new Node(k.boxed, in -> {
                    Object x = l.eval(in);
                    Object y = r.eval(in);
                    return x == null || y == null ? null : arithmetic(op, k, (Number) x, (Number) y);
                });
            }
        }
    }

    private Node conditional(Expr.Conditional c) {
        Node test = node(c.test());
        Node then = node(c.then());
        Node otherwise = node(c.otherwise());
        if (test == null || then == null || otherwise == null) {
            return null;
        }
        if (!Types.isBoolean(test.type)) {
            errors.report(ExprCode.OPERAND_TYPE, c.test().at(), "the condition of '?:' needs a Boolean, got "
                    + test.type.getTypeName());
            return null;
        }
        Type type = common(then.type, otherwise.type);
        if (type == null) {
            errors.report(ExprCode.UNRELATED_TYPES, c.at(), "the branches of '?:' have unrelated types "
                    + pair(then, otherwise));
            return null;
        }
        Eval t = test.eval;
        Eval a = then.eval;
        Eval b = otherwise.eval;
        Num num = Num.of(type);
        if (num != null) {
            return new Node(type, in -> {
                Object v = truth(t.eval(in)) ? a.eval(in) : b.eval(in);
                return v == null ? null : num.convert((Number) v);
            });
        }
        return new Node(type, in -> truth(t.eval(in)) ? a.eval(in) : b.eval(in));
    }

    /** The type both branches fit: the same, the promoted number, or the wider of two related types. */
    private static Type common(Type a, Type b) {
        if (a.equals(b)) {
            return a;
        }
        Num an = Num.of(a);
        Num bn = Num.of(b);
        if (an != null && bn != null) {
            return Num.promote(an, bn).boxed;
        }
        Class<?> ac = Types.boxed(a);
        Class<?> bc = Types.boxed(b);
        if (ac.isAssignableFrom(bc)) {
            return a;
        }
        return bc.isAssignableFrom(ac) ? b : null;
    }

    private static String pair(Node a, Node b) {
        return a.type.getTypeName() + " and " + b.type.getTypeName();
    }

    /** A Boolean operand: {@code null} counts as false. */
    private static boolean truth(Object v) {
        return Boolean.TRUE.equals(v);
    }

    /** {@code x op y} for a comparison operator, as Java compares the promoted primitives (NaN is unordered). */
    private static boolean relation(String op, Num k, Number x, Number y) {
        if (k == Num.INT || k == Num.LONG) {
            long a = x.longValue();
            long b = y.longValue();
            return switch (op) {
                case "<" -> a < b;
                case "<=" -> a <= b;
                case ">" -> a > b;
                case ">=" -> a >= b;
                default -> a == b;
            };
        }
        double a = x.doubleValue();
        double b = y.doubleValue();
        return switch (op) {
            case "<" -> a < b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case ">=" -> a >= b;
            default -> a == b;
        };
    }

    private static Object arithmetic(String op, Num k, Number x, Number y) {
        switch (k) {
            case INT -> {
                int a = x.intValue();
                int b = y.intValue();
                return switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> a / b;
                    default -> a % b;
                };
            }
            case LONG -> {
                long a = x.longValue();
                long b = y.longValue();
                return switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> a / b;
                    default -> a % b;
                };
            }
            case FLOAT -> {
                float a = x.floatValue();
                float b = y.floatValue();
                return switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> a / b;
                    default -> a % b;
                };
            }
            default -> {
                double a = x.doubleValue();
                double b = y.doubleValue();
                return switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> a / b;
                    default -> a % b;
                };
            }
        }
    }
}
