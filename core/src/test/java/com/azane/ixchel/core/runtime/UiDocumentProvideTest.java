package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.view.Translations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code provide:<name>} and the components that declare {@code uses="<name>"}. */
class UiDocumentProvideTest {
    private static UiDocument load(String html, Map<String, String> files, ViewModel model) {
        return UiDocument.load(GoldenTest.page(html), "t.html", files::get, HtmlElements.TAGS, GoldenTest.MONO,
                GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    private static String text(UiDocument doc, String id) {
        return doc.querySelectorAll("#" + id).get(0).textContent();
    }

    /** Provides its {@code label} around its slot, as {@code heading}. */
    private static final String FRAME = """
            <component name="ui-frame" params="label"><div provide:heading="label"><slot></slot></div></component>""";
    /** Shows the {@code heading} it uses. */
    private static final String LABEL = """
            <component name="ui-label" uses="heading"><span bind:text="heading"></span></component>""";
    private static final Map<String, String> FILES = Map.of("frame.html", FRAME, "label.html", LABEL);

    static final class Page extends ViewModel {
        final Value<String> msg = state("hi");
        final Value<String> tab = state("a");
    }

    @Test
    void slottedContentTakesWhatTheComponentAroundItProvides() {
        Page m = new Page();
        UiDocument doc = load("""
                <link href="frame.html"/><link href="label.html"/>
                <ui-frame bind:label="msg"><ui-label id="l"></ui-label></ui-frame>
                """, FILES, m);
        doc.frame(100, 100);
        assertEquals("hi", text(doc, "l"));

        m.msg.set("yo");
        doc.frame(100, 100);
        assertEquals("yo", text(doc, "l"));
    }

    @Test
    void theNearestProviderInTheExpandedTreeWins() {
        UiDocument doc = load("""
                <link href="frame.html"/><link href="label.html"/>
                <div provide:heading="'page'">
                <ui-frame bind:label="msg"><ui-label id="in"></ui-label></ui-frame>
                <div provide:heading="'near'"><ui-label id="near"></ui-label></div>
                <ui-label id="out"></ui-label>
                </div>
                """, FILES, new Page());
        doc.frame(100, 100);
        assertEquals("hi", text(doc, "in"), "the frame's body is nearer than the page's element the slot content is written in");
        assertEquals("near", text(doc, "near"));
        assertEquals("page", text(doc, "out"));
    }

    private static final Map<String, String> TABS = Map.of(
            "tabs.html", "<component name=\"ui-tabs\" params=\"value\"><slot provide:tabs=\"value\"></slot></component>",
            "tab.html", "<link href=\"tab.css\"/><component name=\"ui-tab\" params=\"value\" uses=\"tabs\">"
                    + "<button class=\"b\" bind:class:sel=\"tabs == value\" on:click=\"tabs = value\"></button></component>",
            "tab.css", "@stylesheet; .b { width: 20px; height: 10px; padding: 0; border-style: none }",
            "page.css", "@stylesheet; ui-tab { width: 20px; height: 10px }");
    private static final String TABS_PAGE = """
            <link href="page.css"/><link href="tabs.html"/><link href="tab.html"/>
            <ui-tabs bind:value="tab"><ui-tab id="a" value="a"></ui-tab><ui-tab id="b" value="b"></ui-tab></ui-tabs>
            """;

    @Test
    void aUserWritesThroughAProvidedWritableParameterToThePagesState() {
        Page m = new Page();
        UiDocument doc = load(TABS_PAGE, TABS, m);
        doc.frame(100, 100);
        assertEquals(1, doc.querySelectorAll("#a .sel").size());

        doc.events().mousePressed(5, 15, 0, 0);
        doc.events().mouseReleased(5, 15, 0, 0);
        doc.frame(100, 100);
        assertEquals("b", m.tab.get());
        assertEquals(0, doc.querySelectorAll("#a .sel").size());
        assertEquals(1, doc.querySelectorAll("#b .sel").size());
    }

    @Test
    void lintResolvesProvidersThroughSlots() {
        assertEquals(List.of(), DiagCodes.of(UiDocument.lint(GoldenTest.page(TABS_PAGE), "t.html", TABS::get,
                HtmlElements.TAGS, Translations.NONE, ModelShape.of(Page.class), Set.of())));
    }

    @Test
    void assigningAReadOnlyProvidedValueIsReported() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="setter.html"/>
                <div provide:heading="msg + '!'"><ui-setter></ui-setter></div>
                """, Map.of("setter.html", "<component name=\"ui-setter\" uses=\"heading\"><button on:click=\"heading = 'x'\"></button></component>"),
                new Page()));
        assertEquals(List.of("binding/not-assignable@setter.html:1:62"), DiagCodes.of(e));
    }

    @Test
    void aUseWithoutProviderIsReportedAtTheInstance() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="label.html"/>
                <div><ui-label></ui-label></div>
                """, FILES, new Page()));
        assertEquals(List.of("component/missing-provider@t.html:2:6"), DiagCodes.of(e));
    }
}
