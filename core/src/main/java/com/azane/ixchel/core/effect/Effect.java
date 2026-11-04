package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/**
 * A parsed {@code .ixeffect} file: its header ({@code kind} and typed {@code param}s) and the GLSL
 * body after it, which defines {@code effect()} and may define helpers. Immutable; the host compiles
 * {@code body} once per {@code source} text.
 *
 * @param id       the file id: the effect's identity, and its internal name in {@code paint()} and
 *                 {@code -ix-paint()} once {@link Aliases} rewrote the alias a css file gives it
 * @param name     the file name without {@code .ixeffect}, from which the default alias is made
 * @param bodyLine the line of the file where {@code body} starts, for mapping compile errors back
 * @param source   the whole file, which identifies the program built from it
 */
public record Effect(String id, String name, Kind kind, List<Param> params, String body, int bodyLine, String source) {
    public static final String EXTENSION = ".ixeffect";

    public enum Kind {
        /** Draws an element's background layer: {@code background-image: paint(Alias)}. */
        BOX,
        /** Maps the element's drawn layer: {@code filter: -ix-paint(Alias)}. */
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

    /** The internal property of {@code p}: {@link #property(String, String)} of this effect. */
    public String property(Param p) {
        return property(id, p.name());
    }

    /**
     * The internal property of the param {@code param} of the effect {@code id}, which an author's
     * {@code --Alias-param} becomes; built from the file id, it cannot clash with another effect's.
     */
    public static String property(String id, String param) {
        return "--" + id + "-" + param;
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
