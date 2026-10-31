package com.azane.ixchel.core.template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagRegistryTest {
    @Test
    void declareSkipsCommentsAndBlankLinesAndRegistersAttributesAndPayloads() {
        TagRegistry tags = TagRegistry.HTML.extend().declare("""
                # native elements

                mc-item payload=item accepts=java.lang.String attrs=count
                   # indented comment
                mc-slot attrs=index,size
                """, "tags.txt").build();
        assertEquals(Set.of("count"), tags.attributes("mc-item"));
        assertEquals("item", tags.payloadAttribute("mc-item"));
        assertEquals(List.of("java.lang.String"), tags.payloadTypes("mc-item"));
        assertEquals(Set.of("index", "size"), tags.attributes("mc-slot"));
        assertEquals(TagRegistry.HTML.tags().size() + 2, tags.tags().size());
    }

    @Test
    void declareRejectsAnUnknownField() {
        TagRegistry.Builder b = TagRegistry.HTML.extend();
        assertThrows(IllegalArgumentException.class, () -> b.declare("mc-item colour=red", "tags.txt"));
    }
}
