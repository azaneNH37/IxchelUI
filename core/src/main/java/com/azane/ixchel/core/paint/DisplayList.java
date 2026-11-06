package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.dom.StyledText;

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

    /** Replaces the command at {@code index}, such as a placeholder written before what follows it was known. */
    void set(int index, DrawCommand command) {
        commands.set(index, command);
    }

    /** Drops the commands from {@code size} on. */
    void truncate(int size) {
        commands.subList(size, commands.size()).clear();
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

    /**
     * The rectangle a run of commands draws in, in the coordinates current at its start: each
     * command's extent (an outer shadow's blur included, a text's measured width) through the
     * transforms pushed in the run and cut by the clips pushed in it; a nested layer's mask, which
     * only takes away, adds nothing. One pixel larger on every side for antialiased edges and slanted
     * text.
     */
    static final class Bounds implements DrawCommand.Visitor<Void> {
        /** The width of {@code text} at font size {@code size}, as layout measures it. */
        interface TextWidth {
            float width(String text, float size);
        }

        private static final float PAD = 1;

        private final TextWidth textWidth;
        private final ArrayList<Affine> transforms = new ArrayList<>();
        private final ArrayList<float[]> clips = new ArrayList<>();
        private Affine m;
        /** The current clip in the run's coordinates, or {@code null} for none. */
        private float[] clip;
        private float x0, y0, x1, y1;
        /** How many layers the run has opened and not closed, and at which of those depths a mask is being drawn, or -1. */
        private int depth, maskDepth;
        /** Written by {@link #map}. */
        private float ml, mt, mr, mb;

        Bounds(TextWidth textWidth) {
            this.textWidth = textWidth;
        }

        /** {@code {x0, y0, x1, y1}} of the commands {@code [from, to)}; {@code null} when they draw nothing. */
        float[] of(DisplayList list, int from, int to) {
            m = Affine.IDENTITY;
            clip = null;
            transforms.clear();
            clips.clear();
            depth = 0;
            maskDepth = -1;
            x0 = y0 = Float.POSITIVE_INFINITY;
            x1 = y1 = Float.NEGATIVE_INFINITY;
            for (int i = from; i < to; i++) {
                list.get(i).accept(this);
            }
            if (!(x1 > x0 && y1 > y0)) {
                return null;
            }
            return new float[]{x0 - PAD, y0 - PAD, x1 + PAD, y1 + PAD};
        }

        /** Sets {@link #ml}…{@link #mb} to the box of the local rectangle through the current transform, cut by the clip; whether it is not empty. */
        private boolean map(float ax, float ay, float bx, float by) {
            float px = m.x(ax, ay), py = m.y(ax, ay);
            float qx = m.x(bx, ay), qy = m.y(bx, ay);
            float rx = m.x(ax, by), ry = m.y(ax, by);
            float sx = m.x(bx, by), sy = m.y(bx, by);
            ml = Math.min(Math.min(px, qx), Math.min(rx, sx));
            mt = Math.min(Math.min(py, qy), Math.min(ry, sy));
            mr = Math.max(Math.max(px, qx), Math.max(rx, sx));
            mb = Math.max(Math.max(py, qy), Math.max(ry, sy));
            if (clip != null) {
                ml = Math.max(ml, clip[0]);
                mt = Math.max(mt, clip[1]);
                mr = Math.min(mr, clip[2]);
                mb = Math.min(mb, clip[3]);
            }
            return mr > ml && mb > mt;
        }

        private Void add(float ax, float ay, float bx, float by) {
            if (maskDepth < 0 && map(ax, ay, bx, by)) {
                x0 = Math.min(x0, ml);
                y0 = Math.min(y0, mt);
                x1 = Math.max(x1, mr);
                y1 = Math.max(y1, mb);
            }
            return null;
        }

        @Override
        public Void visitRect(DrawCommand.Rect c) {
            return add(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        }

        @Override
        public Void visitFill(DrawCommand.Fill c) {
            RoundRect s = c.shape();
            return add(Math.max(s.x(), c.clipX()), Math.max(s.y(), c.clipY()),
                    Math.min(s.x() + s.width(), c.clipX() + c.clipWidth()), Math.min(s.y() + s.height(), c.clipY() + c.clipHeight()));
        }

        @Override
        public Void visitBorder(DrawCommand.Border c) {
            RoundRect s = c.outer();
            return add(s.x(), s.y(), s.x() + s.width(), s.y() + s.height());
        }

        @Override
        public Void visitShadow(DrawCommand.Shadow c) {
            RoundRect s = c.inset() ? c.clip() : c.shape();
            // A crisp shadow rounds its edges and reach out to whole pixels.
            float e = c.inset() ? 0 : c.crisp() ? (float) Math.ceil(3 * c.sigma()) + 0.5f : 3 * c.sigma();
            return add(s.x() - e, s.y() - e, s.x() + s.width() + e, s.y() + s.height() + e);
        }

        @Override
        public Void visitText(DrawCommand.Text c) {
            // Bold glyphs are drawn twice, one font pixel apart; a quarter of the size covers it.
            float extra = (c.flags() & StyledText.BOLD) != 0 ? c.size() / 4 : 0;
            float spacing = c.spacing() * c.text().codePointCount(0, c.text().length());
            return add(c.x(), c.y(), c.x() + textWidth.width(c.text(), c.size()) + spacing + extra, c.y() + c.size());
        }

        @Override
        public Void visitImage(DrawCommand.Image c) {
            return add(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        }

        @Override
        public Void visitCustom(DrawCommand.Custom c) {
            return add(c.x(), c.y(), c.x() + c.width(), c.y() + c.height());
        }

        @Override
        public Void visitPaint(DrawCommand.Paint c) {
            RoundRect s = c.shape();
            return add(s.x(), s.y(), s.x() + s.width(), s.y() + s.height());
        }

        @Override
        public Void visitPushClip(DrawCommand.PushClip c) {
            clips.add(clip);
            clip = map(c.x(), c.y(), c.x() + c.width(), c.y() + c.height()) ? new float[]{ml, mt, mr, mb} : new float[]{0, 0, 0, 0};
            return null;
        }

        @Override
        public Void visitPopClip(DrawCommand.PopClip c) {
            clip = clips.remove(clips.size() - 1);
            return null;
        }

        @Override
        public Void visitPushTransform(DrawCommand.PushTransform c) {
            transforms.add(m);
            m = m.then(c.matrix());
            return null;
        }

        @Override
        public Void visitPopTransform(DrawCommand.PopTransform c) {
            m = transforms.remove(transforms.size() - 1);
            return null;
        }

        /** A nested layer's rectangle, less its padding, covers what its filters spread its commands to. */
        @Override
        public Void visitPushLayer(DrawCommand.PushLayer c) {
            add(c.x() + PAD, c.y() + PAD, c.x() + c.width() - PAD, c.y() + c.height() - PAD);
            depth++;
            return null;
        }

        @Override
        public Void visitMask(DrawCommand.Mask c) {
            if (maskDepth < 0) {
                maskDepth = depth;
            }
            return null;
        }

        @Override
        public Void visitPopLayer(DrawCommand.PopLayer c) {
            depth--;
            if (depth < maskDepth) {
                maskDepth = -1;
            }
            return null;
        }

        @Override
        public Void visitBackdrop(DrawCommand.Backdrop c) {
            RoundRect s = c.shape();
            return add(s.x(), s.y(), s.x() + s.width(), s.y() + s.height());
        }
    }
}
