package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.html.HtmlElements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code <else>} after {@code <if>} and {@code <empty>} after {@code <for>}. */
class UiDocumentElseTest {
    private static UiDocument load(String html, ViewModel model) {
        return UiDocument.load(GoldenTest.page(html), "t.html", Map.<String, String>of()::get, HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    /** The children of the element {@code id} as {@code tag:text}, in order. */
    private static String shape(UiDocument doc, String id) {
        StringBuilder out = new StringBuilder();
        for (Node n : doc.querySelectorAll("#" + id).get(0).children()) {
            if (n instanceof Element c) {
                out.append(c.tag()).append(':').append(c.textContent()).append(' ');
            }
        }
        return out.toString().trim();
    }

    static final class Model extends ViewModel {
        final Value<Boolean> show = state(false);
        final Value<List<String>> rows = state(List.of());
    }

    @Test
    void elseMountsExactlyWhileTheTestIsFalse() {
        Model m = new Model();
        UiDocument doc = load("<div id=\"x\"><a>[</a><if test=\"show\"><span>y</span></if> <else><div>n</div><div>o</div></else>"
                + "<a>]</a></div>", m);
        doc.frame(100, 100);
        assertEquals("a:[ div:n div:o a:]", shape(doc, "x"));

        m.show.set(true);
        doc.frame(100, 100);
        assertEquals("a:[ span:y a:]", shape(doc, "x"));

        m.show.set(false);
        doc.frame(100, 100);
        assertEquals("a:[ div:n div:o a:]", shape(doc, "x"));
    }

    @Test
    void emptyMountsExactlyWhileTheListIsEmpty() {
        Model m = new Model();
        UiDocument doc = load("<div id=\"x\"><for each=\"rows\" as=\"r\" key=\"r\"><span bind:text=\"r\"></span></for>"
                + "<empty><div>none</div></empty><a>]</a></div>", m);
        doc.frame(100, 100);
        assertEquals("div:none a:]", shape(doc, "x"));

        m.rows.set(List.of("a"));
        doc.frame(100, 100);
        assertEquals("span:a a:]", shape(doc, "x"));

        m.rows.set(List.of());
        doc.frame(100, 100);
        assertEquals("div:none a:]", shape(doc, "x"));
    }

    @Test
    void elseOrEmptyNotDirectlyAfterItsPartnerIsReported() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div>
                <if test="show"><span></span></if><span></span><else></else>
                <for each="rows" as="r" key="r"></for><if test="show"></if><empty></empty>
                <else></else>
                </div>""", new Model()));
        assertEquals(List.of("view/orphan-else@t.html:2:48", "view/orphan-else@t.html:3:60",
                "view/orphan-else@t.html:4:1"), DiagCodes.of(e));
    }
}
