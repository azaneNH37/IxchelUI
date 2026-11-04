package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code <for>} or {@code <if>}: template content mounted by data. A block never enters the tree;
 * its nodes go into {@code parent} right after {@code prev}, the content before it there.
 */
abstract class Block implements Part, Anchor {
    /** One mounted copy of a block's content: its top-level nodes and blocks, in order, and their parts. */
    static final class Instance {
        final List<Object> items = new ArrayList<>();
        final Part.Group group = new Part.Group();
        final Cell<Object> row;

        Instance(Cell<Object> row) {
            this.row = row;
        }

        Node last() {
            for (int k = items.size() - 1; k >= 0; k--) {
                Node n = items.get(k) instanceof Block b ? b.lastOwn() : (Node) items.get(k);
                if (n != null) {
                    return n;
                }
            }
            return null;
        }

        void nodes(List<Node> out) {
            for (Object item : items) {
                if (item instanceof Block b) {
                    b.nodes(out);
                } else {
                    out.add((Node) item);
                }
            }
        }
    }

    final Compiler compiler;
    final Compiler.Env env;
    final List<Node> content;
    final Element parent;
    final Anchor prev;

    Block(Compiler compiler, Compiler.Env env, List<Node> content, Element parent, Anchor prev) {
        this.compiler = compiler;
        this.env = env;
        this.content = content;
        this.parent = parent;
        this.prev = prev;
    }

    /** The last node this block has mounted, or {@code null}. */
    abstract Node lastOwn();

    /** Every node this block has mounted, in order. */
    abstract void nodes(List<Node> out);

    /** Closes the copies this block still holds, those exiting too. */
    @Override
    public abstract void close();

    @Override
    public final Node last() {
        Node own = lastOwn();
        return own != null ? own : prev.last();
    }

    /** The index in {@code parent} right after {@code at}. */
    final int indexAfter(Anchor at) {
        Node n = at.last();
        return n == null ? 0 : parent.children().indexOf(n) + 1;
    }

    /** A new copy of the content compiled in {@code instanceEnv}; its nodes are not mounted yet. */
    final Instance instantiate(Compiler.Env instanceEnv, Cell<Object> row, Anchor head) {
        Instance i = new Instance(row);
        compiler.compileCopy(parent, content, instanceEnv, head, i.group, i.items);
        return i;
    }

    /** {@code i}'s top-level elements now mounted: its {@code Presence} unit. */
    static List<Element> elements(Instance i) {
        List<Node> nodes = new ArrayList<>();
        i.nodes(nodes);
        List<Element> out = new ArrayList<>();
        for (Node n : nodes) {
            if (n instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    /** Removes {@code i}'s nodes; those an enclosing block already removed (a late exit) are skipped. */
    final void unmount(Instance i) {
        List<Node> nodes = new ArrayList<>();
        i.nodes(nodes);
        for (Node n : nodes) {
            if (n.parent() == parent) {
                parent.removeChild(n);
            }
        }
    }
}
