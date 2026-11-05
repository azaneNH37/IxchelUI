package com.azane.ixchel.core.binding.expr;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.function.Supplier;

/** The reflective type questions of binding checks. */
public final class Types {
    private Types() {
    }

    /** The first type argument of a parameterized type, or {@code null}. */
    public static Type typeArgument(Type t) {
        return t instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
    }

    public static Class<?> rawClass(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) {
            return c;
        }
        return Object.class;
    }

    static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        return switch (c.getName()) {
            case "boolean" -> Boolean.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            default -> Void.class;
        };
    }

    /** The boxed raw class of {@code t}. */
    public static Class<?> boxed(Type t) {
        return box(rawClass(t));
    }

    public static boolean isBoolean(Type t) {
        return boxed(t) == Boolean.class;
    }

    /** The numeric types of expressions, in Java's promotion order. */
    enum Num {
        INT(Integer.class), LONG(Long.class), FLOAT(Float.class), DOUBLE(Double.class);

        final Class<?> boxed;

        Num(Class<?> boxed) {
            this.boxed = boxed;
        }

        /** {@code null} for a non-numeric type. */
        static Num of(Type t) {
            Class<?> c = boxed(t);
            for (Num n : values()) {
                if (n.boxed == c) {
                    return n;
                }
            }
            return null;
        }

        static Num promote(Num a, Num b) {
            return a.compareTo(b) >= 0 ? a : b;
        }

        Number convert(Number v) {
            return switch (this) {
                case INT -> v.intValue();
                case LONG -> v.longValue();
                case FLOAT -> v.floatValue();
                case DOUBLE -> v.doubleValue();
            };
        }
    }

    /** Whether {@code t} is one of the numeric types of expressions. */
    public static boolean isNumber(Type t) {
        return Num.of(t) != null;
    }

    /** {@code text} as a number of type {@code to} (a numeric type), or {@code null} if it is not one. */
    public static Number parseNumber(String text, Type to) {
        try {
            return switch (Num.of(to)) {
                case INT -> Integer.valueOf(text.trim());
                case LONG -> Long.valueOf(text.trim());
                case FLOAT -> Float.valueOf(text.trim());
                case DOUBLE -> Double.valueOf(text.trim());
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether a value of type {@code from} can be stored as {@code to}, widening numbers as Java does
     * and, as bound values often come from a {@code Double} literal, taking a {@code Double} as a
     * {@code Float}.
     */
    public static boolean assignable(Type from, Type to) {
        if (boxed(to).isAssignableFrom(boxed(from))) {
            return true;
        }
        Num f = Num.of(from);
        Num t = Num.of(to);
        return f != null && t != null && (f.compareTo(t) < 0 || f == Num.DOUBLE && t == Num.FLOAT);
    }

    /** Whether {@link #convert} changes the class of a {@code from} value stored as {@code to}. */
    public static boolean converts(Type from, Type to) {
        Num f = Num.of(from);
        Num t = Num.of(to);
        return f != null && t != null && f != t;
    }

    /** {@code value} as {@code to}, after {@link #assignable} said yes. */
    public static Object convert(Object value, Type to) {
        Num t = Num.of(to);
        return t != null && value instanceof Number n ? t.convert(n) : value;
    }

    /**
     * Whether a value of type {@code t} can be a drawn element's payload of type {@code payload}:
     * a {@code payload} or a {@code Supplier} of one. True when {@code payload} is {@code null} or
     * {@code Object}, and when {@code t} (or a type argument of either) is {@code Object} or unknown,
     * since nothing can be checked then.
     */
    public static boolean acceptsPayload(Type t, Type payload) {
        Class<?> raw = boxed(t);
        if (payload == null || raw == Object.class || fits(t, payload)) {
            return true;
        }
        if (!Supplier.class.isAssignableFrom(raw)) {
            return false;
        }
        Type arg = bound(typeArgument(t));
        return arg == null || rawClass(arg) == Object.class || fits(arg, payload);
    }

    /** Whether a {@code t} is a {@code payload}, comparing the first type argument when both have one. */
    private static boolean fits(Type t, Type payload) {
        if (!rawClass(payload).isAssignableFrom(boxed(t))) {
            return false;
        }
        Type want = bound(typeArgument(payload));
        Type got = bound(typeArgument(t));
        return want == null || got == null || rawClass(got) == Object.class
                || rawClass(want).isAssignableFrom(rawClass(got));
    }

    private static Type bound(Type t) {
        return t instanceof WildcardType w ? w.getUpperBounds()[0] : t;
    }
}
