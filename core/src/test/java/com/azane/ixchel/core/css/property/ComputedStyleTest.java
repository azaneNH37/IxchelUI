package com.azane.ixchel.core.css.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComputedStyleTest {
    @Test
    void childInheritsOnlyInheritedPropertiesFromParent() {
        PropertyRegistry reg = new PropertyRegistry();
        PropertyDef<Integer> color = reg.register("color", Syntaxes.color(), 0xFFFFFFFF, true, false, true);
        PropertyDef<Float> width = reg.register("width", Syntaxes.number(false), 0f, false, true, true);

        ComputedStyle parent = ComputedStyle.initial(reg);
        parent.set(color, 0xFFAAAAAA);
        parent.set(width, 40f);

        ComputedStyle child = ComputedStyle.inheriting(reg, parent, parent.custom());
        assertEquals(0xFFAAAAAA, child.get(color));
        assertEquals(0f, child.get(width));
    }

    @Test
    void duplicateNameIsRejectedAndIdsStayDense() {
        PropertyRegistry reg = new PropertyRegistry();
        PropertyDef<Float> a = reg.register("a", Syntaxes.number(false), 0f, false, false, false);
        assertThrows(IllegalArgumentException.class, () -> reg.register("a", Syntaxes.number(false), 1f, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> reg.registerShorthand("a", Shorthand.pair(Syntaxes.number(false)), a, a));
        PropertyDef<Float> b = reg.register("b", Syntaxes.number(false), 0f, false, false, false);
        assertEquals(1, b.id());
        assertSame(a, reg.byName("a"));
        assertEquals(2, reg.size());
    }
}
