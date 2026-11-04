package com.azane.ixchel.kit.schema;

import com.azane.ixchel.kit.project.ClassFiles;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.FixtureLibrary;
import com.azane.ixchel.kit.project.Project;
import com.azane.ixchel.kit.project.ProjectClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTest {
    @Test
    void describesTagsComponentsAndModels() throws URISyntaxException {
        Path resources = Path.of(SchemaTest.class.getResource("/fixture").toURI());
        Project project = new Project(List.of(resources), List.of(ClassFiles.root(SchemaTest.class)),
                SchemaTest.class.getClassLoader());
        Schema schema = Schema.of(project, ElementDeclarations.load(project));

        Schema.Tag item = tag(schema, "mc-item");
        assertEquals("item", item.payload());
        assertEquals("java.lang.Integer", item.payloadType());
        assertFalse(item.containerOnly());
        assertTrue(tag(schema, "mc-slot").containerOnly());
        assertTrue(tag(schema, "mc-slot").attributes().contains("index"));

        assertEquals(List.of("kit-gauge", "kit-meter", "kit-part"),
                schema.components().stream().map(Schema.Component::name).toList());
        assertEquals("kitfixture:ui/part.html", schema.components().get(2).file());

        Schema.Model claimed = schema.models().stream()
                .filter(m -> m.type().endsWith("ClaimedModel")).findFirst().orElseThrow();
        assertEquals(List.of("kitfixture:ui/claimed.html"), claimed.views());
        assertEquals(List.of(new Schema.Member("label", "cell", "java.lang.String", true, false)), claimed.members());
        Schema.Model meter = schema.models().stream()
                .filter(m -> m.type().endsWith("MeterModel")).findFirst().orElseThrow();
        assertEquals(List.of(new Schema.Member("amount", "cell", "java.lang.Integer", false, true)), meter.members());

        assertTrue(schema.properties().stream().anyMatch(p -> p.name().equals("color")));
    }

    @Test
    void listsTheLibrarysComponentsOfTheMcLayer(@TempDir Path tmp) throws IOException {
        try (ProjectClassLoader loader = new ProjectClassLoader(List.of(FixtureLibrary.mcLayer(tmp)),
                SchemaTest.class.getClassLoader())) {
            Project project = new Project(List.of(), List.of(), loader);
            Schema schema = Schema.of(project, ElementDeclarations.load(project));
            assertEquals(List.of(new Schema.Component("mc-kit-gauge", "@ixchel/kitfixture/kit-gauge.html",
                    List.of("level"), List.of("level"), List.of(), List.of())), schema.components());
        }
    }

    /** Fails if the library's or the project's effects go missing, or a param's property or initial value is written wrong. */
    @Test
    void listsEffectsWithTheirParams() throws URISyntaxException {
        Path resources = Path.of(SchemaTest.class.getResource("/fixture").toURI());
        Project project = new Project(List.of(resources), List.of(), SchemaTest.class.getClassLoader());
        Schema schema = Schema.of(project, ElementDeclarations.load(project));

        // unlinked.ixeffect has an invalid header, so it is not offered.
        assertEquals(List.of("@ixchel/effects/dissolve.ixeffect", "@ixchel/effects/glint.ixeffect", "kitfixture:ui/fx/glow.ixeffect"),
                schema.effects().stream().map(Schema.Effect::href).toList());
        Schema.Effect glow = schema.effects().get(2);
        assertEquals("box", glow.kind());
        assertEquals("Glow", glow.alias());
        assertEquals(List.of(new Schema.EffectParam("--Glow-tint", "<color>", true, "#ff000080"),
                new Schema.EffectParam("--Glow-size", "<length>", false, "2.5px")), glow.params());
        assertEquals("layer", schema.effects().get(0).kind());
    }

    /** Fails if a theme's variables leave out its base's, or a project theme reached only through a page goes unlisted. */
    @Test
    void listsShorthandsAndThemesWithTheVariablesOfTheirBases(@TempDir Path tmp) throws IOException {
        Path ui = Files.createDirectories(tmp.resolve("assets/t/ui"));
        Files.writeString(ui.resolve("page.html"), "<page><link href=\"page.css\"/><div></div></page>");
        Files.writeString(ui.resolve("page.css"), "@stylesheet;\n@link \"main.css\";");
        Files.writeString(ui.resolve("main.css"), "@partial;\n@link \"@ixchel/themes/dark.css\";\n:root { --ix-extra: 1px; --own: 2px }");
        Project project = new Project(List.of(tmp), List.of(), SchemaTest.class.getClassLoader());
        Schema schema = Schema.of(project, ElementDeclarations.load(project));

        Schema.Shorthand margin = schema.shorthands().stream()
                .filter(s -> s.name().equals("margin")).findFirst().orElseThrow();
        assertEquals(List.of("margin-top", "margin-right", "margin-bottom", "margin-left"), margin.longhands());
        assertTrue(schema.properties().stream().noneMatch(p -> p.name().equals("margin")));

        assertEquals(List.of("@ixchel/themes/base.css", "@ixchel/themes/dark.css", "@ixchel/themes/vanilla.css", "t:ui/main.css"),
                schema.themes().stream().map(Schema.Theme::href).toList());
        Schema.Theme dark = schema.themes().get(1);
        Schema.Theme main = schema.themes().get(3);
        assertTrue(dark.variables().contains("--ix-panel-bg"));
        List<String> expected = new ArrayList<>(dark.variables());
        expected.add("--ix-extra");
        assertEquals(expected.stream().sorted().toList(), main.variables());
    }

    private static Schema.Tag tag(Schema schema, String name) {
        return schema.tags().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }
}
