package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.Presence;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.azane.ixchel.core.binding.compile.Bindings.MODEL;

/**
 * {@code <for each="list" as="row" key="row.id" index="i">}: one copy of the content per element,
 * matched to the previous frame's copies by key. A kept copy keeps its nodes (and so hover, focus
 * and layout state) and only sees the new row value and position; copies are moved into list order.
 * The optional {@code index} names the row's position among the shown rows, from 0. Each copy is a
 * {@link Presence} unit: a removed row's copy exits where it is, the shown rows ordered around it,
 * and its key coming back takes the same copy back.
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
    /** Copies of removed rows that are exiting, by key. */
    private final Map<Object, Instance> leavingRows = new LinkedHashMap<>();
    private final Map<Instance, Presence.Unit> units = new IdentityHashMap<>();
    /** The nodes of {@link #leavingRows}, which the shown rows are ordered around. */
    private final Set<Node> leavingNodes = Collections.newSetFromMap(new IdentityHashMap<>());
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

    /** True exactly while the list is null or empty: the test of its {@code <empty>}. */
    Cell<Boolean> empty() {
        return MODEL.derived(new Cell<?>[]{list}, args -> args[0] == null || ((List<?>) args[0]).isEmpty());
    }

    @Override
    Node lastOwn() {
        Node last = null;
        for (Instance i : rows.values()) {
            Node l = i.last();
            last = l != null ? l : last;
        }
        if (leavingNodes.isEmpty()) {
            return last;
        }
        // A leaving row may sit after every shown one.
        List<Node> children = parent.children();
        int at = last == null ? -1 : children.indexOf(last);
        for (Node n : leavingNodes) {
            if (n.parent() == parent) {
                at = Math.max(at, children.indexOf(n));
            }
        }
        return at < 0 ? null : children.get(at);
    }

    @Override
    void nodes(List<Node> out) {
        for (Instance i : rows.values()) {
            i.nodes(out);
        }
        for (Instance i : leavingRows.values()) {
            i.nodes(out);
        }
    }

    @Override
    public void close() {
        for (Instance i : rows.values()) {
            i.group.close();
        }
        for (Instance i : leavingRows.values()) {
            i.group.close();
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
        List<Instance> created = new ArrayList<>();
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
                if (row == null && (row = leavingRows.remove(k)) != null) {
                    compiler.presence.reenter(units.get(row));
                    forgetNodes(row);
                    wrote = true;
                }
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
                    created.add(row);
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
        for (Map.Entry<Object, Instance> e : rows.entrySet()) {
            Object k = e.getKey();
            Instance gone = e.getValue();
            leavingRows.put(k, gone);
            List<Node> nodes = new ArrayList<>();
            gone.nodes(nodes);
            leavingNodes.addAll(nodes);
            compiler.presence.exit(units.get(gone), true, () -> {
                leavingRows.remove(k, gone);
                units.remove(gone);
                indexes.remove(gone);
                forgetNodes(gone);
                unmount(gone);
                gone.group.close();
            });
            wrote = true;
        }
        rows = next;
        int index = indexAfter(prev);
        List<Node> nodes = new ArrayList<>();
        List<Node> children = parent.children();
        for (Instance row : rows.values()) {
            nodes.clear();
            row.nodes(nodes);
            for (Node n : nodes) {
                while (index < children.size() && leavingNodes.contains(children.get(index))) {
                    index++;
                }
                if (index >= children.size() || children.get(index) != n) {
                    parent.insertChild(index, n);
                    wrote = true;
                }
                index++;
            }
        }
        for (Instance row : created) {
            units.put(row, compiler.presence.enter(elements(row)));
        }
        return updateRows() || wrote;
    }

    private void forgetNodes(Instance row) {
        List<Node> nodes = new ArrayList<>();
        row.nodes(nodes);
        nodes.forEach(leavingNodes::remove);
    }

    private boolean updateRows() {
        boolean wrote = false;
        for (Instance row : rows.values()) {
            wrote |= row.group.update();
        }
        return wrote;
    }
}
