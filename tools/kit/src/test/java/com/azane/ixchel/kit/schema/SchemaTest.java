package com.azane.ixchel.kit.schema;

import com.azane.ixchel.kit.project.ClassFiles;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.Project;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
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

        assertEquals(List.of("kit-part"), schema.components().stream().map(Schema.Component::name).toList());
        assertEquals("kitfixture:ui/part.html", schema.components().get(0).file());

        Schema.Model claimed = schema.models().stream()
                .filter(m -> m.type().endsWith("ClaimedView")).findFirst().orElseThrow();
        assertEquals(List.of("kitfixture:ui/claimed.html"), claimed.templates());
        assertEquals(List.of(new Schema.Member("label", "cell", "java.lang.String", true)), claimed.members());

        assertTrue(schema.properties().stream().anyMatch(p -> p.name().equals("color")));
    }

    /** Fails if the library's or the project's effects go missing, or a param's property or initial value is written wrong. */
    @Test
    void listsEffectsWithTheirParams() throws URISyntaxException {
        Path resources = Path.of(SchemaTest.class.getResource("/fixture").toURI());
        Project project = new Project(List.of(resources), List.of(), SchemaTest.class.getClassLoader());
        Schema schema = Schema.of(project, ElementDeclarations.load(project));

        // unlinked.ixeffect has an invalid header, so it is not offered.
        assertEquals(List.of("@ixchel/effects/dissolve", "@ixchel/effects/glint", "kitfixture:ui/fx/glow.ixeffect"),
                schema.effects().stream().map(Schema.Effect::href).toList());
        Schema.Effect glow = schema.effects().get(2);
        assertEquals("box", glow.kind());
        assertEquals(List.of(new Schema.EffectParam("--glow-tint", "<color>", true, "#ff000080"),
                new Schema.EffectParam("--glow-size", "<length>", false, "2.5px")), glow.params());
        assertEquals("layer", schema.effects().get(0).kind());
    }

    @Test
    void listsShorthandsAndThemeVariables() {
        Project project = new Project(List.of(), List.of(), SchemaTest.class.getClassLoader());
        Schema schema = Schema.of(project, ElementDeclarations.load(project));

        Schema.Shorthand margin = schema.shorthands().stream()
                .filter(s -> s.name().equals("margin")).findFirst().orElseThrow();
        assertEquals(List.of("margin-top", "margin-right", "margin-bottom", "margin-left"), margin.longhands());
        assertTrue(schema.properties().stream().noneMatch(p -> p.name().equals("margin")));

        assertEquals(List.of("dark.css", "vanilla.css"),
                schema.librarySheets().stream().map(Schema.LibrarySheet::name).toList());
        for (Schema.LibrarySheet sheet : schema.librarySheets()) {
            assertTrue(sheet.variables().contains("--ix-text"), sheet.name());
            assertTrue(sheet.variables().stream().allMatch(v -> v.startsWith("--ix-")), sheet.name());
            assertEquals(sheet.variables().stream().sorted().toList(), sheet.variables(), sheet.name());
        }
    }

    private static Schema.Tag tag(Schema schema, String name) {
        return schema.tags().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }
}
