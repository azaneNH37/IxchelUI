package com.azane.ixchel.core.paint;

/**
 * The contract between the core and the mc painter. Coordinates are document GUI pixels; colors are
 * ARGB. Java 17 has no pattern-matching switch, so consumers dispatch through {@link Visitor}, which
 * keeps the exhaustiveness check when a command type is added.
 */
public sealed interface DrawCommand
        permits DrawCommand.Rect, DrawCommand.Text, DrawCommand.Image, DrawCommand.PushClip, DrawCommand.PopClip {

    <R> R accept(Visitor<R> v);

    interface Visitor<R> {
        R visitRect(Rect c);

        R visitText(Text c);

        R visitImage(Image c);

        R visitPushClip(PushClip c);

        R visitPopClip(PopClip c);
    }

    record Rect(float x, float y, float width, float height, int color) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitRect(this);
        }
    }

    /** {@code (x, y)} is the top-left of the line box; {@code size} is the font size in GUI pixels. */
    record Text(float x, float y, float size, int color, String text) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitText(this);
        }
    }

    /** {@code src} is a resource location such as {@code modid:textures/gui/icon.png}; {@code tint} multiplies it. */
    record Image(float x, float y, float width, float height, String src, int tint) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitImage(this);
        }
    }

    /** Intersects the current clip with this rectangle until the matching {@link PopClip}. */
    record PushClip(float x, float y, float width, float height) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPushClip(this);
        }
    }

    record PopClip() implements DrawCommand {
        public static final PopClip INSTANCE = new PopClip();

        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPopClip(this);
        }
    }
}
