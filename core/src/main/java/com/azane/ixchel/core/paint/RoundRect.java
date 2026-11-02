package com.azane.ixchel.core.paint;

/**
 * A rectangle with elliptical corners in GUI pixels. Radii are the horizontal then vertical radius of
 * each corner, already reduced so that adjacent corners do not overlap (CSS Backgrounds 3 §5.5).
 */
public record RoundRect(float x, float y, float width, float height, Radii radii) {

    public record Radii(float topLeftX, float topLeftY, float topRightX, float topRightY,
                        float bottomRightX, float bottomRightY, float bottomLeftX, float bottomLeftY) {
        public static final Radii ZERO = new Radii(0, 0, 0, 0, 0, 0, 0, 0);

        public boolean isZero() {
            return equals(ZERO);
        }

        /**
         * These radii for a box of {@code width} by {@code height}: scaled down together where two
         * corners on one side would add up to more than the side, as CSS requires.
         */
        public Radii fit(float width, float height) {
            float f = 1;
            f = scale(f, width, topLeftX + topRightX);
            f = scale(f, width, bottomLeftX + bottomRightX);
            f = scale(f, height, topLeftY + bottomLeftY);
            f = scale(f, height, topRightY + bottomRightY);
            if (f >= 1) {
                return this;
            }
            return new Radii(topLeftX * f, topLeftY * f, topRightX * f, topRightY * f,
                    bottomRightX * f, bottomRightY * f, bottomLeftX * f, bottomLeftY * f);
        }

        private static float scale(float f, float side, float sum) {
            return sum > side && sum > 0 ? Math.min(f, Math.max(0, side) / sum) : f;
        }

        /**
         * The radii of the curve {@code top}, {@code right}, {@code bottom} and {@code left} pixels
         * inside (outside when negative) this one, as CSS draws padding and inner border edges;
         * a radius never goes below 0.
         */
        public Radii inset(float top, float right, float bottom, float left) {
            return new Radii(Math.max(0, topLeftX - left), Math.max(0, topLeftY - top),
                    Math.max(0, topRightX - right), Math.max(0, topRightY - top),
                    Math.max(0, bottomRightX - right), Math.max(0, bottomRightY - bottom),
                    Math.max(0, bottomLeftX - left), Math.max(0, bottomLeftY - bottom));
        }
    }

    public RoundRect inset(float top, float right, float bottom, float left) {
        return new RoundRect(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom),
                radii.inset(top, right, bottom, left));
    }
}
