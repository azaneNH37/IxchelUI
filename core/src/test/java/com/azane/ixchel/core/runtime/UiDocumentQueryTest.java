package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentQueryTest {
    static final class Model extends ViewModel {
        final Value<Boolean> open = state(false);
        final Value<List<String>> rows = state(List.of("a", "b"));
    }

    private static String names(List<Element> elements) {
        StringBuilder out = new StringBuilder();
        for (Element e : elements) {
            out.append(e.attribute("id") != null ? e.attribute("id") : e.tag()).append(' ');
        }
        return out.toString().trim();
    }

    @Test
    void matchesInDocumentOrderAndFollowsMountedBlocks() {
        Model m = new Model();
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <div id="list" class="list">
                  <for each="rows" as="r" key="r"><span class="row" bind:text="r"></span></for>
                </div>
                <if test="open"><div id="panel"><span id="inner" class="row"></span></div></if>
                <span id="last" class="row"></span>
                """), "t.html", GoldenTest.NO_SHEETS, com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        doc.frame(100, 100);
        assertEquals("span span last", names(doc.querySelectorAll(".row")));
        assertEquals("span span", names(doc.querySelectorAll(".list > .row")));
        assertEquals("list last", names(doc.querySelectorAll("#last, #list")));

        m.open.set(true);
        m.rows.set(List.of("a"));
        doc.frame(100, 100);
        assertEquals("span inner last", names(doc.querySelectorAll(".row")));
    }

    @Test
    void invalidSelectorThrows() {
        UiDocument doc = UiDocument.load(GoldenTest.page("<div></div>"), "t.html", GoldenTest.NO_SHEETS, com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO,
                GoldenTest.NO_IMAGES);
        assertThrows(IllegalArgumentException.class, () -> doc.querySelectorAll("div >"));
    }
}
