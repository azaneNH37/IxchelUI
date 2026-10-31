package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.util.List;

/** {@code <if test="…">}: mounted while the Boolean is true; recreated on each false-to-true change. */
final class IfBlock extends Block {
    private final Cell<?> test;
    private final Directive attribute;
    private Instance instance;
    private boolean failing;

    IfBlock(Compiler compiler, Compiler.Env env, List<Node> content, Element parent, Anchor prev, Cell<?> test,
            Directive attribute) {
        super(compiler, env, content, parent, prev);
        this.test = test;
        this.attribute = attribute;
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
    public boolean update() {
        boolean on;
        try {
            on = Boolean.TRUE.equals(test.get());
            failing = false;
        } catch (RuntimeException ex) {
            if (!failing) {
                failing = true;
                env.diag().report(BindingCode.RUNTIME_EXCEPTION, attribute.valuePos(),
                        "'" + attribute.value() + "' threw " + ex);
            }
            on = instance != null;
        }
        boolean wrote = false;
        if (on && instance == null) {
            instance = instantiate(env, null, prev);
            int index = indexAfter(prev);
            for (Object item : instance.items) {
                if (item instanceof Node n) {
                    parent.insertChild(index++, n);
                }
            }
            wrote = true;
        } else if (!on && instance != null) {
            unmount(instance);
            instance = null;
            return true;
        }
        if (instance != null) {
            wrote |= instance.group.update();
        }
        return wrote;
    }
}
