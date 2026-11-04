package com.azane.ixchel.core.binding;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelShapeTest {
    record Quest(String name) {
    }

    static class Base extends ViewModel {
        final Value<String> title = state("base");
        final Value<Integer> shared = state(1);
    }

    static final class View extends Base {
        final Cell<String> shared = state("sub");
        final Value<String> filter = state("");
        final Cell<String> readOnly = state("x");
        final Action<Quest> track = action(q -> { });
        private final Value<String> hidden = state("");
        static Value<String> global;
        final String plain = "p";
    }

    private static Map<String, ModelShape.Member> members() {
        return ModelShape.of(View.class).members();
    }

    @Test
    void aCellIsWritableIffItsDeclaredTypeIsValue() {
        assertTrue(members().get("filter").writable());
        assertFalse(members().get("readOnly").writable(), "it holds a Value, but is declared a Cell");
    }

    @Test
    void aSubclassFieldHidesTheSuperclassFieldOfTheSameName() {
        ModelShape.Member shared = members().get("shared");
        assertEquals(View.class, shared.owner());
        assertEquals(String.class, shared.type());
        assertFalse(shared.writable());
    }

    @Test
    void privateStaticAndNonCellFieldsAreNotMembers() {
        assertEquals(Set.of("shared", "filter", "readOnly", "track", "title"), members().keySet());
    }

    @Test
    void anActionsTypeIsItsArgumentType() {
        ModelShape.Member track = members().get("track");
        assertEquals(ModelShape.Kind.ACTION, track.kind());
        assertEquals(Quest.class, track.type());
    }

    static final class Component extends ViewModel {
        final Param<Integer> pos = param();
        final TwoWay<Boolean> open = twoWay();
        final Event<Quest> picked = event();
        final Event<Void> done = event();
        final Cell<String> machine = state("m");
        @SuppressWarnings("rawtypes")
        final Param raw = param();
        private final Param<String> hidden = param();
    }

    @Test
    void portsAreMembersByKindOfUseMarkedByTheirDeclaredType() {
        Map<String, ModelShape.Member> members = ModelShape.of(Component.class).members();
        assertEquals(new ModelShape.Member("pos", ModelShape.Kind.CELL, Integer.class, false, true, Component.class), members.get("pos"));
        assertEquals(new ModelShape.Member("open", ModelShape.Kind.CELL, Boolean.class, true, true, Component.class), members.get("open"));
        assertEquals(new ModelShape.Member("picked", ModelShape.Kind.EVENT, Quest.class, false, true, Component.class), members.get("picked"));
        assertEquals(Void.class, members.get("done").type());
        assertFalse(members.get("machine").port());
        assertEquals(Set.of("pos", "open", "picked", "done", "machine"), members.keySet(), "a raw or private port is not a member");
    }
}
