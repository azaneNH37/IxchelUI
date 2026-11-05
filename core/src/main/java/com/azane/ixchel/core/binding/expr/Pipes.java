package com.azane.ixchel.core.binding.expr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** The built-in pipes' formatting: pure, {@link Locale#ROOT}, never {@code null} for a non-null input. */
final class Pipes {
    private static final String[] UNITS = {"", "k", "M", "G", "T"};
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private Pipes() {
    }

    /** {@code v} in steps of 1000 (k, M, G, T) with at most {@code digits} decimals, trailing zeros stripped. */
    static String compact(Number v, int digits) {
        BigDecimal d = decimal(v);
        if (d == null) {
            return String.valueOf(v);
        }
        int scale = Math.max(0, digits);
        BigDecimal a = d.abs();
        int unit = 0;
        while (unit < UNITS.length - 1 && a.compareTo(THOUSAND) >= 0) {
            a = a.movePointLeft(3);
            unit++;
        }
        BigDecimal r = a.setScale(scale, RoundingMode.HALF_UP);
        if (r.compareTo(THOUSAND) >= 0 && unit < UNITS.length - 1) {
            r = a.movePointLeft(3).setScale(scale, RoundingMode.HALF_UP);
            unit++;
        }
        String digitsText = r.signum() == 0 ? "0" : r.stripTrailingZeros().toPlainString();
        return (d.signum() < 0 && r.signum() != 0 ? "-" : "") + digitsText + UNITS[unit];
    }

    /** Game ticks (20 per second) as {@code m:ss}, or {@code h:mm:ss} from one hour; negative counts as 0. */
    static String duration(Number ticks) {
        long s = Math.max(0, ticks.longValue()) / 20;
        long h = s / 3600;
        long m = s % 3600 / 60;
        long sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** A 0–1 fraction as a rounded whole percent. */
    static String percent(Number v) {
        BigDecimal d = decimal(v);
        if (d == null) {
            return v + "%";
        }
        return d.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** {@code v} exactly as written in decimal ({@code Float} by its own shortest form); {@code null} if not finite. */
    private static BigDecimal decimal(Number v) {
        if (v instanceof Double || v instanceof Float) {
            double d = v.doubleValue();
            if (!Double.isFinite(d)) {
                return null;
            }
            return v instanceof Float f ? new BigDecimal(Float.toString(f)) : BigDecimal.valueOf(d);
        }
        return BigDecimal.valueOf(v.longValue());
    }
}
