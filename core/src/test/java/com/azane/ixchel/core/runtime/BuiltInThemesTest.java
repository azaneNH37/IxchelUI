package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltInThemesTest {
    private static final List<String> THEMES = List.of("vanilla", "dark", "holo", "cyber");
    private static final String PAGE = """
            <link href="page.css"/>
            <div class="ix-panel">
              <span class="ix-title">Title</span><span class="ix-muted">muted</span><div class="ix-divider"></div>
              <div class="ix-well">well</div>
              <button>a</button><button disabled="">b</button><button class="ix-selected">c</button>
              <button class="ix-invalid">d</button><button class="ix-busy">e</button><button class="ix-done">f</button>
              <input placeholder="text"/><input type="checkbox"/><input type="range"/>
              <select><option value="1">one</option><option value="2">two</option></select>
            </div>
            """;

    static final class Empty extends ViewModel {
    }

    private static UiDocument load(String theme) {
        Map<String, String> files = Map.of("page.css", "@stylesheet; @link \"@ixchel/themes/" + theme + ".css\";");
        UiDocument doc = UiDocument.load(GoldenTest.page(PAGE), "t.html", files::get, com.azane.ixchel.core.html.HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, new Empty(), Bindings.PLAIN_TEXT);
        doc.frame(400, 300);
        return doc;
    }

    private static void boxes(Element e, StringBuilder out) {
        out.append(e.tag()).append(' ').append(e.box().x()).append(' ').append(e.box().y()).append(' ')
                .append(e.box().width()).append(' ').append(e.box().height()).append('\n');
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                boxes(c, out);
            }
        }
    }

    // Fails if a built-in theme has a rule or value the engine rejects, or one moves a box: a page must
    // keep its layout whichever theme it links.
    @Test
    void everyBuiltInThemeLoadsCleanAndLeavesTheLayoutAsItIs() {
        String first = null;
        for (String theme : THEMES) {
            UiDocument doc = load(theme);
            assertEquals(List.of(), doc.diagnostics().entries(), theme);
            StringBuilder out = new StringBuilder();
            boxes(doc.root(), out);
            if (first == null) {
                first = out.toString();
            } else {
                assertEquals(first, out.toString(), theme);
            }
        }
    }
}
