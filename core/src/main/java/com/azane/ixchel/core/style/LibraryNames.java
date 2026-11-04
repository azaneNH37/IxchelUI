package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.library.Library;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code ix-} names the library's css files export, which host files may use but not make up:
 * the {@code .ix-*} classes library selectors name and the {@code --ix-*} tokens library
 * declarations set. A host file naming any other is warned, which catches misspellings too. The
 * runtime never lists the library's files, so only a tool that lists them can build this.
 */
public final class LibraryNames {
    private static final String CLASS = "ix-";
    private static final String TOKEN = "--ix-";

    private final Set<String> classes = new HashSet<>();
    private final Set<String> tokens = new HashSet<>();

    private LibraryNames() {
    }

    /** Collects the names of the css files {@code cssIds} of {@code library}; a missing file has none. */
    public static LibraryNames of(Library library, List<String> cssIds) {
        LibraryNames names = new LibraryNames();
        for (String id : cssIds) {
            String text = library.read(id);
            if (text == null) {
                continue;
            }
            for (Rule r : CssParser.parseStylesheet(text, id, new Diagnostics()).rules()) {
                names.classes(r.prelude());
                for (Rule.Declaration d : r.declarations()) {
                    if (d.name().startsWith(TOKEN)) {
                        names.tokens.add(d.name());
                    }
                }
            }
        }
        return names;
    }

    private void classes(List<ComponentValue> prelude) {
        for (int i = 0; i < prelude.size(); i++) {
            String c = className(prelude, i);
            if (c != null) {
                classes.add(c);
            } else if (prelude.get(i) instanceof ComponentValue.Function f) {
                classes(f.args());
            }
        }
    }

    /** Warns of the unknown library names in {@code sheet}, the css file {@code id}, unless it is the library's. */
    public void check(String id, ParsedSheet sheet, Diagnostics diag) {
        if (Library.isLibrary(id)) {
            return;
        }
        for (Rule r : sheet.rules()) {
            selector(r.prelude(), diag);
            declarations(r.declarations(), diag);
        }
        for (ParsedSheet.Keyframes k : sheet.keyframes()) {
            for (ParsedSheet.Keyframe f : k.frames()) {
                declarations(f.declarations(), diag);
            }
        }
    }

    /** Warns of the unknown library tokens in the inline styles of {@code nodes} written in host files. */
    public void inline(List<Node> nodes, Diagnostics diag) {
        for (Node n : nodes) {
            if (n instanceof Element e) {
                if (!e.inlineStyle().isEmpty() && !Library.isLibrary(e.inlineStyle().get(0).pos().file())) {
                    declarations(e.inlineStyle(), diag);
                }
                inline(e.children(), diag);
            }
        }
    }

    private void declarations(List<Rule.Declaration> declarations, Diagnostics diag) {
        for (Rule.Declaration d : declarations) {
            if (d.name().startsWith(TOKEN) && !tokens.contains(d.name())) {
                diag.report(StyleCode.UNKNOWN_LIBRARY_TOKEN, d.pos(), unknownToken(d.name()));
            }
            uses(d.value(), false, diag);
        }
    }

    private void uses(List<ComponentValue> value, boolean inVar, Diagnostics diag) {
        for (ComponentValue v : value) {
            if (inVar && v instanceof Token t && t.is(Token.Kind.IDENT) && t.value().startsWith(TOKEN)
                    && !tokens.contains(t.value())) {
                diag.report(StyleCode.UNKNOWN_LIBRARY_TOKEN, t.pos(), unknownToken(t.value()));
            } else if (v instanceof ComponentValue.Function f) {
                uses(f.args(), f.name().toLowerCase(Locale.ROOT).equals("var"), diag);
            } else if (v instanceof ComponentValue.Block b) {
                uses(b.contents(), false, diag);
            }
        }
    }

    private void selector(List<ComponentValue> prelude, Diagnostics diag) {
        for (int i = 0; i < prelude.size(); i++) {
            String c = className(prelude, i);
            if (c != null && !classes.contains(c)) {
                diag.report(StyleCode.UNKNOWN_LIBRARY_CLASS, prelude.get(i).pos(), "." + c
                        + " is not a class of the library; ix- names belong to the library");
            } else if (prelude.get(i) instanceof ComponentValue.Function f) {
                selector(f.args(), diag);
            }
        }
    }

    /** The {@code ix-} class {@code .name} starting at {@code i}, or {@code null}. */
    private static String className(List<ComponentValue> prelude, int i) {
        if (i + 1 < prelude.size() && prelude.get(i) instanceof Token dot && dot.isDelim('.')
                && prelude.get(i + 1) instanceof Token name && name.is(Token.Kind.IDENT) && name.value().startsWith(CLASS)) {
            return name.value();
        }
        return null;
    }

    private static String unknownToken(String name) {
        return name + " is not a token of the library; ix- names belong to the library";
    }
}
