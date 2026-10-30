package com.azane.ixchel.core.binding.expr;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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

    /** Whether a value of type {@code from} can be stored as {@code to}, widening numbers as Java does. */
    public static boolean assignable(Type from, Type to) {
        if (boxed(to).isAssignableFrom(boxed(from))) {
            return true;
        }
        Num f = Num.of(from);
        Num t = Num.of(to);
        return f != null && t != null && f.compareTo(t) < 0;
    }

    /** {@code value} as {@code to}, after {@link #assignable} said yes. */
    public static Object convert(Object value, Type to) {
        Num t = Num.of(to);
        return t != null && value instanceof Number n ? t.convert(n) : value;
    }
}
