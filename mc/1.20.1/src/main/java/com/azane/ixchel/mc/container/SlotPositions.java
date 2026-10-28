package com.azane.ixchel.mc.container;

import net.minecraft.world.inventory.Slot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Writes the {@code final} {@code Slot.x/y} through reflection (#008): hosts need no access
 * transformer. Fields are looked up by the development name first, then the SRG name used in
 * production; a missing field fails when this class initializes, i.e. when the first container
 * screen opens.
 */
final class SlotPositions {
    /** Off-screen position for hidden slots; the vanilla creative inventory uses the same value. */
    static final int HIDDEN = -2000;

    private static final MethodHandle SET_X = setter("x", "f_40220_");
    private static final MethodHandle SET_Y = setter("y", "f_40221_");

    private SlotPositions() {
    }

    static void set(Slot slot, int x, int y) {
        if (slot.x == x && slot.y == y) {
            return;
        }
        try {
            SET_X.invokeExact(slot, x);
            SET_Y.invokeExact(slot, y);
        } catch (Throwable t) {
            throw new IllegalStateException("writing Slot.x/y failed", t);
        }
    }

    private static MethodHandle setter(String... names) {
        for (String name : names) {
            try {
                Field f = Slot.class.getDeclaredField(name);
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return MethodHandles.lookup().unreflectSetter(f);
                }
            } catch (NoSuchFieldException ignored) {
                // try the next candidate name
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Slot." + name + " is not writable", e);
            }
        }
        throw new IllegalStateException("Slot field not found under " + Arrays.toString(names));
    }
}
