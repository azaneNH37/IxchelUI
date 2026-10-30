package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;
import java.util.Map;

/**
 * {@code <for each="list" as="row" key="row.id">}: one copy of the content per element, matched to
 * the previous frame's copies by key. A kept copy keeps its nodes (and so hover, focus and layout
 * state) and only sees the new row value; copies are moved into list order.
 */
final class EachBlock extends Block {
    private final Cell<?> list;
    private final String alias;
    private final Type rowType;
    private final Cell<Object> keyInput;
    private final Cell<?> key;
    private final Directive eachAttribute;
    private final Directive keyAttribute;
    private Map<Object, Instance> rows = new LinkedHashMap<>();
    private boolean failing;
    private boolean reportedDuplicate;

    EachBlock(Compiler compiler, Compiler.Env env, List<Node> content, Element parent, Anchor prev, Cell<?> list,
              String alias, Type rowType, Cell<Object> keyInput, Cell<?> key, Directive eachAttribute,
              Directive keyAttribute) {
        super(compiler, env, content, parent, prev);
        this.list = list;
        this.alias = alias;
        this.rowType = rowType;
        this.keyInput = keyInput;
        this.key = key;
        this.eachAttribute = eachAttribute;
        this.keyAttribute = keyAttribute;
    }

    @Override
    Node lastOwn() {
        Node last = null;
        for (Instance i : rows.values()) {
            Node l = i.last();
            last = l != null ? l : last;
        }
        return last;
    }

    @Override
    void nodes(List<Node> out) {
        for (Instance i : rows.values()) {
            i.nodes(out);
        }
    }

    /** The last mounted node of the rows before {@code row}, or of what precedes the block. */
    private Node lastBefore(Instance row) {
        Node last = null;
        for (Instance i : rows.values()) {
            if (i == row) {
                break;
            }
            Node l = i.last();
            last = l != null ? l : last;
        }
        return last != null ? last : prev.last();
    }

    @Override
    public boolean update() {
        List<?> items;
        try {
            items = (List<?>) list.get();
            failing = false;
        } catch (RuntimeException ex) {
            if (!failing) {
                failing = true;
                env.diag().error(eachAttribute.valuePos(), "'" + eachAttribute.value() + "' threw " + ex);
            }
            return updateRows();
        }
        boolean wrote = false;
        Map<Object, Instance> next = new LinkedHashMap<>();
        if (items != null) {
            for (Object item : items) {
                MODEL.replace(keyInput, item);
                Object k = key.get();
                if (next.containsKey(k)) {
                    if (!reportedDuplicate) {
                        reportedDuplicate = true;
                        env.diag().error(keyAttribute.valuePos(), "duplicate key " + k + " in '"
                                + eachAttribute.value() + "'; later rows with it are skipped");
                    }
                    continue;
                }
                Instance row = rows.remove(k);
                if (row == null) {
                    Cell<Object> cell = MODEL.held(null);
                    MODEL.replace(cell, item);
                    Instance[] self = new Instance[1];
                    row = instantiate(env.with(alias, new Ref(cell, rowType)), cell, () -> lastBefore(self[0]));
                    self[0] = row;
                } else {
                    MODEL.replace(row.row, item);
                }
                next.put(k, row);
            }
        }
        for (Instance gone : rows.values()) {
            unmount(gone);
            wrote = true;
        }
        rows = next;
        int index = indexAfter(prev);
        List<Node> nodes = new ArrayList<>();
        for (Instance row : rows.values()) {
            nodes.clear();
            row.nodes(nodes);
            for (Node n : nodes) {
                if (index >= parent.children().size() || parent.children().get(index) != n) {
                    parent.insertChild(index, n);
                    wrote = true;
                }
                index++;
            }
        }
        return updateRows() || wrote;
    }

    private boolean updateRows() {
        boolean wrote = false;
        for (Instance row : rows.values()) {
            wrote |= row.group.update();
        }
        return wrote;
    }
}
