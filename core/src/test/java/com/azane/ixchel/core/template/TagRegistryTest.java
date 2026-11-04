package com.azane.ixchel.core.template;

import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.html.NativeElements;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagRegistryTest {
    /** Stands in for an adapter interface such as the mc layer's painter. */
    interface Painter<T> extends NativePayload<T> {
    }

    @NativeElement(tag = "x-item", payload = "item", attributes = "count")
    static final class Item implements Painter<String> {
    }

    @NativeElement(tag = "x-slot", attributes = "index")
    static final class Slot {
    }

    @NativeElement(tag = "x-bad", payload = "item")
    static final class PayloadWithoutType {
    }

    @NativeElement(tag = "x-bad")
    static final class TypeWithoutPayload implements NativePayload<String> {
    }

    @Test
    void declareReadsTheTagAttributesAndPayloadTypeThroughAnAdapterInterface() {
        TagRegistry tags = com.azane.ixchel.core.html.HtmlElements.TAGS.extend()
                .add(NativeElements.declare(Item.class))
                .add(NativeElements.declare(Slot.class)).build();
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
        assertThrows(IllegalArgumentException.class, () -> NativeElements.declare(PayloadWithoutType.class));
        assertThrows(IllegalArgumentException.class, () -> NativeElements.declare(TypeWithoutPayload.class));
        assertThrows(IllegalArgumentException.class, () -> NativeElements.declare(String.class));
    }
}
