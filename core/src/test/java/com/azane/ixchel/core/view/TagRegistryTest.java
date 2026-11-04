package com.azane.ixchel.core.view;

import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.html.DeclaredElements;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagRegistryTest {
    /** Stands in for an adapter interface such as the mc layer's painter. */
    interface Painter<T> extends ElementPayload<T> {
    }

    @UiElement(tag = "x-item", payload = "item", attributes = "count")
    static final class Item implements Painter<String> {
    }

    @UiElement(tag = "x-slot", attributes = "index")
    static final class Slot {
    }

    @UiElement(tag = "x-bad", payload = "item")
    static final class PayloadWithoutType {
    }

    @UiElement(tag = "x-bad")
    static final class TypeWithoutPayload implements ElementPayload<String> {
    }

    @Test
    void declareReadsTheTagAttributesAndPayloadTypeThroughAnAdapterInterface() {
        TagRegistry tags = com.azane.ixchel.core.html.HtmlElements.TAGS.extend()
                .add(DeclaredElements.declare(Item.class))
                .add(DeclaredElements.declare(Slot.class)).build();
        ElementType item = tags.type("x-item");
        assertEquals("item", item.payload());
        assertEquals(String.class, item.payloadType());
        assertEquals(Set.of("count"), item.attributes());
        ElementType slot = tags.type("x-slot");
        assertNull(slot.payload());
        assertEquals(Set.of("index"), slot.attributes());
    }

    @Test
    void declareRejectsAPayloadWithoutItsTypeAndTheOtherWayRound() {
        assertThrows(IllegalArgumentException.class, () -> DeclaredElements.declare(PayloadWithoutType.class));
        assertThrows(IllegalArgumentException.class, () -> DeclaredElements.declare(TypeWithoutPayload.class));
        assertThrows(IllegalArgumentException.class, () -> DeclaredElements.declare(String.class));
    }
}
