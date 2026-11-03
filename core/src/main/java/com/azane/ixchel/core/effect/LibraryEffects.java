package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.Diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The effects shipped in the library, linked as {@value #PREFIX}{@code <name>}: read from the classpath
 * next to this class (so they survive relocation and resource packs cannot replace them) and parsed
 * once; a problem in one is a bug in the library.
 */
public final class LibraryEffects {
    /** The {@code href} prefix of a library effect. */
    public static final String PREFIX = "@ixchel/effects/";
    private static final List<String> NAMES = List.of("dissolve", "glint");
    private static final Map<String, Effect> PARSED = new ConcurrentHashMap<>();

    private LibraryEffects() {
    }

    /** The names linkable after {@value #PREFIX}, sorted. */
    public static List<String> names() {
        return NAMES;
    }

    /** The library effect {@code name}, {@code null} if there is none. */
    public static Effect effect(String name) {
        return NAMES.contains(name) ? PARSED.computeIfAbsent(name, LibraryEffects::parse) : null;
    }

    private static Effect parse(String name) {
        String file = name + Effect.EXTENSION;
        try (InputStream in = LibraryEffects.class.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("missing " + file);
            }
            Diagnostics diag = new Diagnostics();
            Effect e = EffectParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), PREFIX + name, diag);
            if (e == null || diag.hasErrors()) {
                throw new IllegalStateException("invalid " + file + ": " + diag.entries());
            }
            return e;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
