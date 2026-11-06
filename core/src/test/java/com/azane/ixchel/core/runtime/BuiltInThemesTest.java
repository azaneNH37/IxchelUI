package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuiltInThemesTest {
    private static final List<String> THEMES = List.of("vanilla", "dark", "holo", "amber");
    private static final String PAGE = """
            <link href="page.css"/>
            <link href="@ixchel/components/tabs.html"/><link href="@ixchel/components/tab.html"/>
            <link href="@ixchel/components/bar.html"/><link href="@ixchel/components/stepper.html"/>
            <link href="@ixchel/components/cycle.html"/><link href="@ixchel/components/pager.html"/>
            <link href="@ixchel/components/tree-row.html"/><link href="@ixchel/components/search.html"/>
            <div class="ix-panel">
              <span class="ix-title">Title</span><span class="ix-muted">muted</span><div class="ix-divider"></div>
              <div class="ix-well">well</div>
              <button>a</button><button disabled="">b</button><button class="ix-selected">c</button>
              <button class="ix-invalid">d</button><button class="ix-busy">e</button><button class="ix-done">f</button>
              <input placeholder="text"/><input type="checkbox"/><input type="range"/>
              <select><option value="1">one</option><option value="2">two</option></select>
              <input type="checkbox" class="ix-switch" bind:checked="open"/><input type="radio" bind:group="tab" value="a"/>
              <div class="ix-row">row</div><div class="ix-row ix-selected">row</div>
              <span class="ix-badge">3</span><span class="ix-key">R</span>
              <details bind:open="open"><summary>more</summary><span>inside</span></details>
              <ix-tabs bind:value="tab"><ix-tab value="a" label="A"></ix-tab><ix-tab value="b" label="B"></ix-tab></ix-tabs>
              <ix-bar style="width: 40px; height: 6px" bind:value="hp" bind:max="max"></ix-bar>
              <ix-bar style="width: 6px; height: 40px" bind:value="hp" bind:max="max" dir="up"></ix-bar>
              <ix-stepper bind:value="hp" bind:min="zero" bind:max="max"></ix-stepper>
              <ix-cycle bind:value="mode" bind:options="modes"></ix-cycle>
              <ix-pager bind:page="mode" bind:count="max"></ix-pager>
              <ix-tree-row bind:depth="mode" bind:expanded="open" bind:leaf="leaf"><span>node</span></ix-tree-row>
              <ix-search bind:value="query"></ix-search>
            </div>
            """;

    static final class Model extends ViewModel {
        final Value<String> tab = state("a");
        final Value<Boolean> open = state(true);
        final Value<Boolean> leaf = state(false);
        final Value<Integer> hp = state(7);
        final Value<Integer> zero = state(0);
        final Value<Integer> max = state(20);
        final Value<Integer> mode = state(1);
        final Value<List<String>> modes = state(List.of("off", "on", "auto"));
        final Value<String> query = state("");
    }

    private static UiDocument load(String theme) {
        Map<String, String> files = Map.of("page.css", "@stylesheet; @link \"@ixchel/themes/" + theme + ".css\";");
        UiDocument doc = UiDocument.load(GoldenTest.page(PAGE), "t.html", files::get, com.azane.ixchel.core.html.HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, new Model(), Bindings.PLAIN_TEXT);
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

    // Fails if a theme frames a field twice, with its border and with the well's shadow inside it.
    @Test
    void aFieldHasOneFrame() {
        for (String theme : THEMES) {
            UiDocument doc = load(theme);
            for (Element field : doc.querySelectorAll("input, select")) {
                assertEquals(List.of(), field.computed().get(CssProperties.BOX_SHADOW), theme + " <" + field.tag() + ">");
            }
        }
    }

    // Fails if a slot without a class goes unthemed: it has no look of its own, and would show nothing.
    @Test
    void aSlotTakesTheWellSurfaceWithoutAClass() {
        var tags = com.azane.ixchel.core.html.HtmlElements.TAGS.extend()
                .add(com.azane.ixchel.core.html.DeclaredElements.type("mc-slot", java.util.Set.of(), "menuslot", null)).build();
        for (String theme : THEMES) {
            Map<String, String> files = Map.of("page.css", "@stylesheet; @link \"@ixchel/themes/" + theme + ".css\";");
            UiDocument doc = UiDocument.load(GoldenTest.page("<link href=\"page.css\"/><mc-slot></mc-slot><div class=\"ix-well\"></div>"),
                    "t.html", files::get, tags, GoldenTest.MONO, GoldenTest.NO_IMAGES, new Model(), Bindings.PLAIN_TEXT);
            doc.frame(400, 300);
            assertEquals(doc.querySelectorAll(".ix-well").get(0).computed().get(CssProperties.BACKGROUND_COLOR),
                    doc.querySelectorAll("mc-slot").get(0).computed().get(CssProperties.BACKGROUND_COLOR), theme);
        }
    }
}
