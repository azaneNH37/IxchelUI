package com.azane.ixchel.core.paint;

import java.util.ArrayList;

/**
 * Draw commands in paint order. Reused across frames: {@link #clear()} keeps the backing array and
 * starts a new {@link #version()}, so a painter can keep its vertex buffers while the version holds.
 */
public final class DisplayList {
    private final ArrayList<DrawCommand> commands = new ArrayList<>();
    private long version;

    public void add(DrawCommand command) {
        commands.add(command);
    }

    public void clear() {
        commands.clear();
        version++;
    }

    /** Changes exactly when the list is repainted. */
    public long version() {
        return version;
    }

    public int size() {
        return commands.size();
    }

    public DrawCommand get(int index) {
        return commands.get(index);
    }

    /** Index loop rather than an iterator, so per-frame replay does not allocate. */
    public void replay(DrawCommand.Visitor<?> visitor) {
        for (int i = 0, n = commands.size(); i < n; i++) {
            commands.get(i).accept(visitor);
        }
    }
}
