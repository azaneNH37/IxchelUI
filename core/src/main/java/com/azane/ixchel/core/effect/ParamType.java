package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.css.property.Syntax;
import com.azane.ixchel.core.css.property.Syntaxes;

/**
 * The value type of an effect param, its {@code syntax} descriptor. Each computes to a {@link Float}
 * but {@code <color>}, an ARGB {@link Integer}; a shader sees a {@code float}, or for a color a
 * premultiplied {@code vec4}. Lengths are px, percentages fractions, angles degrees (radians in the
 * shader), times seconds.
 */
public enum ParamType {
    NUMBER("<number>", Syntaxes.number(true)),
    LENGTH("<length>", Syntaxes.pxLength()),
    PERCENTAGE("<percentage>", Syntaxes.percentage()),
    COLOR("<color>", Syntaxes.color()),
    ANGLE("<angle>", Syntaxes.angle()),
    TIME("<time>", Syntaxes.interpolatingTime());

    private final String syntaxName;
    private final Syntax<?> syntax;

    ParamType(String syntaxName, Syntax<?> syntax) {
        this.syntaxName = syntaxName;
        this.syntax = syntax;
    }

    /** As written after {@code syntax:}, e.g. {@code <length>}. */
    public String syntaxName() {
        return syntaxName;
    }

    public Syntax<?> syntax() {
        return syntax;
    }

    /** How many floats the shader's uniform takes: 4 for a color, otherwise 1. */
    public int components() {
        return this == COLOR ? 4 : 1;
    }

    /** The GLSL type of the param's uniform. */
    public String glslType() {
        return this == COLOR ? "vec4" : "float";
    }

    /** {@code null} for a name that is none of these. */
    static ParamType of(String syntaxName) {
        for (ParamType t : values()) {
            if (t.syntaxName.equals(syntaxName)) {
                return t;
            }
        }
        return null;
    }

    /** Writes the computed {@code value} into {@code out} at {@code at} as the shader takes it, in {@link #components()} floats. */
    void pack(Object value, float[] out, int at) {
        if (this == COLOR) {
            int c = (Integer) value;
            float a = (c >>> 24) / 255f;
            out[at] = (c >> 16 & 0xFF) / 255f * a;
            out[at + 1] = (c >> 8 & 0xFF) / 255f * a;
            out[at + 2] = (c & 0xFF) / 255f * a;
            out[at + 3] = a;
        } else if (this == ANGLE) {
            out[at] = (float) Math.toRadians((Float) value);
        } else {
            out[at] = (Float) value;
        }
    }
}
