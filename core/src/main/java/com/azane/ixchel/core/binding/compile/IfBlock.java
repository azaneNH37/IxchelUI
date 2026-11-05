package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.Presence;

import java.util.List;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * {@code <if test="…">}: mounted while the Boolean is true; recreated on each false-to-true change.
 * The copy is a {@link Presence} unit: a true-to-false change makes it exit first, and turning true
 * again while it exits takes the same copy back. An {@code <else>} or {@code <empty>} is one too,
 * testing that its partner shows nothing.
 */
final class IfBlock extends Block {
    private final Cell<?> test;
    /** The {@code test} directive, where a throwing test is reported; {@code null} when its partner reports it. */
    private final Directive attribute;
    private Instance instance;
    private Presence.Unit unit;
    private boolean failing;

    IfBlock(Compiler compiler, Compiler.Env env, List<Node> content, Element parent, Anchor prev, Cell<?> test,
            Directive attribute) {
        super(compiler, env, content, parent, prev);
        this.test = test;
        this.attribute = attribute;
    }

    /** True exactly while this block's test is not: the test of its {@code <else>}. */
    Cell<Boolean> otherwise() {
        return MODEL.derived(new Cell<?>[]{test}, args -> !Boolean.TRUE.equals(args[0]));
    }

    @Override
    Node lastOwn() {
        return instance == null ? null : instance.last();
    }

    @Override
    void nodes(List<Node> out) {
        if (instance != null) {
            instance.nodes(out);
        }
    }

    @Override
    public void close() {
        if (instance != null) {
            instance.group.close();
        }
    }

    @Override
    public boolean update() {
        boolean exiting = unit != null && unit.exiting();
        boolean on;
        try {
            on = Boolean.TRUE.equals(test.get());
            failing = false;
        } catch (RuntimeException ex) {
            if (!failing) {
                failing = true;
                if (attribute != null) {
                    env.diag().report(BindingCode.RUNTIME_EXCEPTION, attribute.valuePos(),
                            "'" + attribute.value() + "' threw " + ex);
                }
            }
            on = instance != null && !exiting;
        }
        boolean wrote = false;
        if (on && exiting) {
            compiler.presence.reenter(unit);
            wrote = true;
        } else if (on && instance == null) {
            instance = instantiate(env, null, prev);
            int index = indexAfter(prev);
            for (Object item : instance.items) {
                if (item instanceof Node n) {
                    parent.insertChild(index++, n);
                }
            }
            unit = compiler.presence.enter(elements(instance));
            wrote = true;
        } else if (!on && instance != null && !exiting) {
            Instance gone = instance;
            compiler.presence.exit(unit, true, () -> {
                unmount(gone);
                gone.group.close();
                instance = null;
                unit = null;
            });
            return true;
        }
        if (instance != null && !unit.exiting()) {
            wrote |= instance.group.update();
        }
        return wrote;
    }
}
