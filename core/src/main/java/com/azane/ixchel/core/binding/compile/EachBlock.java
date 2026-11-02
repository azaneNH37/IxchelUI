package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * {@code <for each="list" as="row" key="row.id" index="i">}: one copy of the content per element, matched
 * to the previous frame's copies by key. A kept copy keeps its nodes (and so hover, focus and layout
 * state) and only sees the new row value and position; copies are moved into list order. The optional
 * {@code index} names the row's position among the shown rows, from 0.
 */
final class EachBlock extends Block {
    private final Cell<?> list;
    private final String alias;
    private final Type rowType;
    /** The {@code index} name, or {@code null}. */
    private final String indexName;
    private final Cell<Object> keyInput;
    private final Cell<?> key;
    private final Directive eachAttribute;
    private final Directive keyAttribute;
    private Map<Object, Instance> rows = new LinkedHashMap<>();
    /** The index cell of each row while {@link #indexName} is set. */
    private final Map<Instance, Cell<Object>> indexes = new IdentityHashMap<>();
    private boolean failing;
    private boolean reportedDuplicate;

    EachBlock(Compiler compiler, Compiler.Env env, List<Node> content, Element parent, Anchor prev, Cell<?> list,
              String alias, Type rowType, String indexName, Cell<Object> keyInput, Cell<?> key, Directive eachAttribute,
              Directive keyAttribute) {
        super(compiler, env, content, parent, prev);
        this.list = list;
        this.alias = alias;
        this.rowType = rowType;
        this.indexName = indexName;
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
                env.diag().report(BindingCode.RUNTIME_EXCEPTION, eachAttribute.valuePos(),
                        "'" + eachAttribute.value() + "' threw " + ex);
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
                        env.diag().report(BindingCode.DUPLICATE_KEY, keyAttribute.valuePos(), "duplicate key " + k + " in '"
                                + eachAttribute.value() + "'; later rows with it are skipped");
                    }
                    continue;
                }
                Instance row = rows.remove(k);
                if (row == null) {
                    Cell<Object> cell = MODEL.held(null);
                    MODEL.replace(cell, item);
                    Compiler.Env rowEnv = env.with(alias, new Ref(cell, rowType));
                    Cell<Object> index = null;
                    if (indexName != null) {
                        index = MODEL.held(null);
                        MODEL.replace(index, next.size());
                        rowEnv = rowEnv.with(indexName, new Ref(index, Integer.class));
                    }
                    Instance[] self = new Instance[1];
                    row = instantiate(rowEnv, cell, () -> lastBefore(self[0]));
                    self[0] = row;
                    if (index != null) {
                        indexes.put(row, index);
                    }
                } else {
                    MODEL.replace(row.row, item);
                    if (indexName != null) {
                        MODEL.replace(indexes.get(row), next.size());
                    }
                }
                next.put(k, row);
            }
        }
        for (Instance gone : rows.values()) {
            indexes.remove(gone);
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
