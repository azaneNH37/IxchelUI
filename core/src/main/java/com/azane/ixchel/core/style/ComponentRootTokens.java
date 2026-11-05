package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.List;

/**
 * Custom properties a component's stylesheet sets on {@code :root}: a component's rules reach only its
 * own elements, never the page's top-level ones, so they set nothing; the page sets the tokens the
 * component follows.
 */
public final class ComponentRootTokens {
    private ComponentRootTokens() {
    }

    /** Warns of each custom property {@code sheet}, a component's stylesheet, sets on {@code :root}. */
    public static void check(ParsedSheet sheet, Diagnostics diag) {
        for (Rule r : sheet.rules()) {
            if (!root(r.prelude())) {
                continue;
            }
            for (Rule.Declaration d : r.declarations()) {
                if (d.name().startsWith("--")) {
                    diag.report(StyleCode.COMPONENT_ROOT_TOKEN, d.pos(), "a component's stylesheet sets " + d.name()
                            + " on :root, which is the page's and out of its reach", "follow the page through var(); the page's stylesheet sets tokens");
                }
            }
        }
    }

    private static boolean root(List<ComponentValue> prelude) {
        for (int i = 0; i + 1 < prelude.size(); i++) {
            if (prelude.get(i) instanceof Token colon && colon.is(Token.Kind.COLON)
                    && prelude.get(i + 1) instanceof Token name && name.isIdent("root")) {
                return true;
            }
        }
        return false;
    }
}
