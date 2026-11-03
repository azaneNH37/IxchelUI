package com.azane.ixchel.core.css.property;

/**
 * One {@code filter} or {@code backdrop-filter} function, as computed. {@code amount} is the argument
 * of the color functions (a factor; degrees for {@code hue-rotate}); {@code sigma} the standard
 * deviation of {@code blur} and of a {@code drop-shadow}'s blur, in pixels; {@code x}, {@code y} and
 * {@code color} the shadow's offset and ARGB color; {@code effect} the name of a {@code -ix-paint()}'s
 * layer effect, {@code null} for the other functions.
 */
public record Filter(Kind kind, float amount, float x, float y, float sigma, int color, String effect) {
    public enum Kind {
        GRAYSCALE, SEPIA, SATURATE, HUE_ROTATE, INVERT, BRIGHTNESS, CONTRAST, OPACITY, BLUR, DROP_SHADOW,
        /** {@code -ix-paint(name)}: the document's layer effect {@code name}. */
        PAINT;

        /** The argument that leaves the input as it is, which an omitted function interpolates from. */
        float identity() {
            return switch (this) {
                case SATURATE, BRIGHTNESS, CONTRAST, OPACITY -> 1;
                default -> 0;
            };
        }

        /** Whether the function maps each pixel's color on its own, as a color matrix. */
        public boolean colorMatrix() {
            return this != BLUR && this != DROP_SHADOW && this != PAINT;
        }
    }

    public static Filter amount(Kind kind, float amount) {
        return new Filter(kind, amount, 0, 0, 0, 0, null);
    }

    /** {@code -ix-paint(effect)}. */
    public static Filter paint(String effect) {
        return new Filter(Kind.PAINT, 0, 0, 0, 0, 0, effect);
    }

    /** The function of {@code kind} that changes nothing; there is none for {@link Kind#PAINT}. */
    static Filter identity(Kind kind) {
        return new Filter(kind, kind.identity(), 0, 0, 0, 0, null);
    }

    /** How far, in pixels, what this function draws reaches beyond its input's edges. */
    public float reach() {
        return switch (kind) {
            case BLUR -> 3 * sigma;
            case DROP_SHADOW -> Math.max(Math.abs(x), Math.abs(y)) + 3 * sigma;
            default -> 0;
        };
    }
}
