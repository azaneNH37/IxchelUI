package com.azane.ixchel.core.paint;

import java.util.ArrayList;

/** Draw commands in paint order. Reused across frames: {@link #clear()} keeps the backing array. */
public final class DisplayList {
    private final ArrayList<DrawCommand> commands = new ArrayList<>();

    public void add(DrawCommand command) {
        commands.add(command);
    }

    public void clear() {
        commands.clear();
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
