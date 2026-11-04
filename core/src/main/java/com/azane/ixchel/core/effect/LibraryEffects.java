package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.library.Library;

import java.util.List;

/**
 * The effects of {@link Library#CORE}, linked as {@value #PREFIX}{@code <name>}; the list tools offer.
 * Documents read them through the library like any linked file.
 */
public final class LibraryEffects {
    /** The {@code href} prefix of a library effect. */
    public static final String PREFIX = Library.PREFIX + "effects/";
    private static final List<String> NAMES = List.of("dissolve", "glint");

    private LibraryEffects() {
    }

    /** The names linkable after {@value #PREFIX}, sorted. */
    public static List<String> names() {
        return NAMES;
    }

    /** The library effect {@code name}, parsed; {@code null} if there is none. */
    public static Effect effect(String name) {
        if (!NAMES.contains(name)) {
            return null;
        }
        String id = PREFIX + name + Effect.EXTENSION;
        Diagnostics diag = new Diagnostics();
        Effect e = EffectParser.parse(Library.CORE.read(id), id, diag);
        if (e == null || diag.hasErrors()) {
            throw new IllegalStateException("invalid " + id + ": " + diag.entries());
        }
        return e;
    }
}
