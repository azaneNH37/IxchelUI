package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/**
 * A parsed {@code .ixeffect} file: its header ({@code kind} and typed {@code param}s) and the GLSL
 * body after it, which defines {@code effect()} and may define helpers. Immutable; the host compiles
 * {@code body} once per {@code source} text.
 *
 * @param id       the file id, or the {@code @ixchel/effects/<name>} href of a built-in effect
 * @param name     the file name without {@code .ixeffect}: the name {@code paint()} and
 *                 {@code -ix-paint()} use and the prefix of the params' properties
 * @param bodyLine the line of the file where {@code body} starts, for mapping compile errors back
 * @param source   the whole file, which identifies the program built from it
 */
public record Effect(String id, String name, Kind kind, List<Param> params, String body, int bodyLine, String source) {
    public static final String EXTENSION = ".ixeffect";

    public enum Kind {
        /** Draws an element's background layer: {@code background-image: paint(name)}. */
        BOX,
        /** Maps the element's drawn layer: {@code filter: -ix-paint(name)}. */
        LAYER
    }

    /**
     * @param initial the computed {@code initial-value}
     * @param pos     where the param is declared
     */
    public record Param(String name, ParamType type, boolean inherits, Object initial, SourcePos pos) {
        /** The uniform the body reads it as: the name with {@code -} as {@code _}. */
        public String uniform() {
            return name.replace('-', '_');
        }
    }

    public Effect {
        params = List.copyOf(params);
    }

    /** The custom property that sets {@code p}: {@code --<effect>-<param>}. */
    public String property(Param p) {
        return "--" + name + "-" + p.name();
    }

    /** How many floats the params' values take, as {@link ParamType#components()} counts them. */
    public int valueCount() {
        int n = 0;
        for (Param p : params) {
            n += p.type().components();
        }
        return n;
    }
}
