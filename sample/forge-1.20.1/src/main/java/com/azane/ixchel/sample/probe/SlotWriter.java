package com.azane.ixchel.sample.probe;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Writes the final {@code Slot.x/y} through reflection (#008); dev name first, then SRG. */
final class SlotWriter {
    private static final Field X = find("x", "f_40220_");
    private static final Field Y = find("y", "f_40221_");
    private static final MethodHandle SET_X = setter(X);
    private static final MethodHandle SET_Y = setter(Y);

    private SlotWriter() {
    }

    static void set(Slot slot, int x, int y) {
        try {
            SET_X.invokeExact(slot, x);
            SET_Y.invokeExact(slot, y);
        } catch (Throwable t) {
            throw new IllegalStateException("writing Slot.x/y failed", t);
        }
    }

    /** Logs module openness, the field names that resolved, a write round trip and the setter cost. */
    static void selfCheck() {
        Module m = Slot.class.getModule();
        String pkg = Slot.class.getPackageName();
        ProbeClient.LOG.info("[ixchel-probe] Slot module name={} named={} isOpen({})={} isOpen(to probe)={} fields x={} y={}",
                m.getName(), m.isNamed(), pkg, m.isOpen(pkg), m.isOpen(pkg, SlotWriter.class.getModule()),
                X.getName(), Y.getName());
        Slot s = new Slot(new SimpleContainer(1), 0, 5, 7);
        set(s, 42, 43);
        ProbeClient.LOG.info("[ixchel-probe] write round trip x={} y={} (expected 42 43)", s.x, s.y);
        int n = 2_000_000;
        for (int i = 0; i < n; i++) {
            set(s, i, i);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            set(s, i, i);
        }
        long dt = System.nanoTime() - t0;
        ProbeClient.LOG.info("[ixchel-probe] set(x,y) {} ns/op, last x={}", String.format("%.1f", dt / (double) n), s.x);
    }

    private static Field find(String... names) {
        for (String name : names) {
            try {
                Field f = Slot.class.getDeclaredField(name);
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
                // try the next candidate name
            }
        }
        throw new IllegalStateException("Slot field not found under " + Arrays.toString(names));
    }

    private static MethodHandle setter(Field f) {
        try {
            return MethodHandles.lookup().unreflectSetter(f);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
