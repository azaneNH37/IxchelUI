package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.Filter;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.StyledText;

import java.util.List;

/**
 * The contract between the core and the mc painter. Coordinates are document GUI pixels; colors are
 * ARGB. Java 17 has no pattern-matching switch, so consumers dispatch through {@link Visitor}, which
 * keeps the exhaustiveness check when a command type is added.
 */
public sealed interface DrawCommand
        permits DrawCommand.Rect, DrawCommand.Fill, DrawCommand.Border, DrawCommand.Shadow, DrawCommand.Text,
        DrawCommand.Image, DrawCommand.Custom, DrawCommand.PushClip, DrawCommand.PopClip, DrawCommand.PushTransform,
        DrawCommand.PopTransform, DrawCommand.PushLayer, DrawCommand.PopLayer, DrawCommand.Backdrop {

    <R> R accept(Visitor<R> v);

    interface Visitor<R> {
        R visitRect(Rect c);

        R visitFill(Fill c);

        R visitBorder(Border c);

        R visitShadow(Shadow c);

        R visitText(Text c);

        R visitImage(Image c);

        R visitCustom(Custom c);

        R visitPushClip(PushClip c);

        R visitPopClip(PopClip c);

        R visitPushTransform(PushTransform c);

        R visitPopTransform(PopTransform c);

        R visitPushLayer(PushLayer c);

        R visitPopLayer(PopLayer c);

        R visitBackdrop(Backdrop c);
    }

    record Rect(float x, float y, float width, float height, int color) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitRect(this);
        }
    }

    /**
     * Paints {@code shape} where it overlaps the clip rectangle, in {@code color} or, when
     * {@code gradient} is not {@code null}, in the gradient's colors. Hosts draw these through
     * {@link ShapeTessellator}.
     */
    record Fill(RoundRect shape, float clipX, float clipY, float clipWidth, float clipHeight, int color,
                Gradient gradient) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitFill(this);
        }
    }

    /**
     * A rounded border: the ring between {@code outer} and its inner edge {@code top}, {@code right},
     * {@code bottom} and {@code left} pixels inside. Where two sides of different colors meet, the
     * corner is split along the line from the outer to the inner corner of the border edges.
     */
    record Border(RoundRect outer, float top, float right, float bottom, float left,
                  int topColor, int rightColor, int bottomColor, int leftColor) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitBorder(this);
        }
    }

    /**
     * {@code shape} blurred by a Gaussian of standard deviation {@code sigma} (half the CSS blur
     * radius) in {@code color}. An outer shadow is drawn only outside {@code clip} (the border box),
     * an inset one only inside it (the padding box), where it is the shadow of the area around
     * {@code shape}.
     */
    record Shadow(RoundRect shape, float sigma, int color, boolean inset, RoundRect clip) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitShadow(this);
        }
    }

    /**
     * A native element ({@link com.azane.ixchel.core.template.NativeElement}) for the
     * host to draw in its border box. {@code kind} is the tag; the core never looks inside {@code payload}.
     */
    record Custom(String kind, Object payload, float x, float y, float width, float height) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitCustom(this);
        }
    }

    /**
     * {@code (x, y)} is the top-left of a line of the text's font; {@code size} is the font size in GUI
     * pixels. {@code flags} are {@link StyledText}'s: bold, italic, underline, strikethrough, obfuscated.
     */
    record Text(float x, float y, float size, int color, String text, int flags) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitText(this);
        }
    }

    /**
     * Draws the texture region {@code (u0, v0)}-{@code (u1, v1)}, in fractions of the whole image, into
     * the rectangle. {@code src} is a resource location such as {@code modid:textures/gui/icon.png};
     * {@code tint} multiplies it; {@code filter} is how it is sampled when scaled.
     */
    record Image(float x, float y, float width, float height, String src, float u0, float v0, float u1, float v1,
                 int tint, Filter filter) implements DrawCommand {
        /** {@code AUTO} keeps the texture's own setting; the others force linear or nearest-texel sampling. */
        public enum Filter { AUTO, LINEAR, NEAREST }

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

    /**
     * Maps the coordinates of the commands up to the matching {@link PopTransform} through
     * {@code matrix}, after any outer transform; clip rectangles pushed inside are in those mapped
     * coordinates too.
     */
    record PushTransform(Affine matrix) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPushTransform(this);
        }
    }

    record PopTransform() implements DrawCommand {
        public static final PopTransform INSTANCE = new PopTransform();

        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPopTransform(this);
        }
    }

    /**
     * Draws the commands up to the matching {@link PopLayer} on their own transparent layer, then runs
     * {@code filters} over it in order and composites the result at {@code opacity} with {@code blend}.
     * The rectangle, in the coordinates current at this command (an outer transform maps it too),
     * bounds everything they draw and what the filters spread it to; the host may clip it further to
     * the current clip and the screen.
     */
    record PushLayer(float x, float y, float width, float height, float opacity, List<Filter> filters,
                     Keywords.MixBlendMode blend) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPushLayer(this);
        }
    }

    record PopLayer() implements DrawCommand {
        public static final PopLayer INSTANCE = new PopLayer();

        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitPopLayer(this);
        }
    }

    /**
     * {@code backdrop-filter}: what is drawn behind {@code shape} so far, run through {@code filters} in
     * order and drawn back inside the shape. With {@code belowGroup} it is the first command of its
     * element's own layer and what is behind is read from the target under that layer, so the
     * element's opacity fades the filtered backdrop too.
     */
    record Backdrop(RoundRect shape, List<Filter> filters, boolean belowGroup) implements DrawCommand {
        @Override
        public <R> R accept(Visitor<R> v) {
            return v.visitBackdrop(this);
        }
    }
}
