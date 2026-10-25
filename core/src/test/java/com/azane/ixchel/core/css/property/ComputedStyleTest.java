package com.azane.ixchel.core.css.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputedStyleTest {
    @Test
    void childInheritsOnlyInheritedPropertiesFromParent() {
        PropertyRegistry reg = new PropertyRegistry();
        PropertyDef<Integer> color = reg.register("color", 0xFFFFFFFF, true, false, true);
        PropertyDef<Float> width = reg.register("width", 0f, false, true, true);

        ComputedStyle parent = ComputedStyle.initial(reg);
        parent.set(color, 0xFFAAAAAA);
        parent.set(width, 40f);

        ComputedStyle child = ComputedStyle.inheriting(reg, parent);
        assertEquals(0xFFAAAAAA, child.get(color));
        assertEquals(0f, child.get(width));
    }

    @Test
    void duplicateNameIsRejectedAndIdsStayDense() {
        PropertyRegistry reg = new PropertyRegistry();
        PropertyDef<Integer> a = reg.register("a", 0, false, false, false);
        assertThrows(IllegalArgumentException.class, () -> reg.register("a", 1, false, false, false));
        PropertyDef<Integer> b = reg.register("b", 0, false, false, false);
        assertEquals(1, b.id());
        assertSame(a, reg.byName("a"));
        assertEquals(2, reg.size());
    }
}
