package com.azane.ixchel.core.layout;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.tree.LayoutOutput;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.RunMode;
import dev.vfyjxf.taffy.tree.TaffyTree;

import java.util.HashMap;
import java.util.Map;

/**
 * A taffy tree whose layout cache hits only on the inputs a result was computed for, as taffy's Rust
 * cache does now. taffy-java's cache also hits when a known dimension equals the size a result came
 * out at: a flex item first sized for its content suggestion (its own {@code width} not applied, text
 * wrapped at min-content) that happens to come out at its {@code width} is then reused when it is
 * laid out at that width, taller by the wrapped lines.
 *
 * <p>The tree's own cache still holds every result, as the tree reads it to know which nodes are dirty;
 * entries here are only trusted while that cache is not empty, and dropped when it fills again after
 * being cleared.
 */
final class ExactCacheTree extends TaffyTree {
    /** One axis of a query: the known dimension, or the available space when there is none. */
    private record Key(float width, AvailableSpace availableWidth, float height, AvailableSpace availableHeight) {
        static Key of(FloatSize known, TaffySize<AvailableSpace> available) {
            boolean w = !Float.isNaN(known.width);
            boolean h = !Float.isNaN(known.height);
            return new Key(known.width, w ? null : available.width, known.height, h ? null : available.height);
        }
    }

    private static final class Entries {
        final Map<Key, FloatSize> sizes = new HashMap<>();
        Key layoutKey;
        LayoutOutput layout;
    }

    private final Map<NodeId, Entries> entries = new HashMap<>();

    @Override
    public LayoutOutput getCacheEntry(NodeId node, FloatSize knownDimensions, TaffySize<AvailableSpace> availableSpace,
                                      RunMode runMode) {
        Entries e = entries.get(node);
        if (e == null || isDirty(node)) {
            return null;
        }
        Key key = Key.of(knownDimensions, availableSpace);
        if (runMode == RunMode.PERFORM_LAYOUT) {
            return key.equals(e.layoutKey) ? e.layout : null;
        }
        FloatSize size = e.sizes.get(key);
        if (size == null && key.equals(e.layoutKey)) {
            size = e.layout.size();
        }
        return size == null ? null : LayoutOutput.fromOuterSize(size);
    }

    @Override
    public void storeCacheEntry(NodeId node, FloatSize knownDimensions, TaffySize<AvailableSpace> availableSpace,
                                RunMode runMode, LayoutOutput output) {
        boolean cleared = isDirty(node);
        super.storeCacheEntry(node, knownDimensions, availableSpace, runMode, output);
        Entries e = entries.computeIfAbsent(node, n -> new Entries());
        if (cleared) {
            e.sizes.clear();
            e.layoutKey = null;
            e.layout = null;
        }
        Key key = Key.of(knownDimensions, availableSpace);
        if (runMode == RunMode.PERFORM_LAYOUT) {
            e.layoutKey = key;
            e.layout = output;
        } else {
            e.sizes.put(key, output.size());
        }
    }

    @Override
    public void remove(NodeId node) {
        super.remove(node);
        entries.remove(node);
    }

    @Override
    public void clear() {
        super.clear();
        entries.clear();
    }
}
