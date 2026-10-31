package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.UiLoadException;

import java.util.List;

/**
 * Renders diagnostics as {@code area/name@file:line:col}, the code and position tests pin; message
 * wording is free to change and is never asserted outside {@code DiagnosticsTest}.
 */
public final class DiagCodes {
    private DiagCodes() {
    }

    public static String of(Diagnostic d) {
        return d.code().id() + "@" + d.pos();
    }

    public static List<String> of(List<Diagnostic> entries) {
        return entries.stream().map(DiagCodes::of).toList();
    }

    public static List<String> of(Diagnostics diag) {
        return of(diag.entries());
    }

    public static List<String> of(UiLoadException e) {
        return of(e.diagnostics());
    }
}
