package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;

import java.util.Objects;
import java.util.function.Function;

/** One bound attribute; writes the DOM only when the value differs from the last write. */
abstract class Binding implements Part {
    private final Cell<?> cell;
    final Directive attribute;
    final Diagnostics diag;
    private boolean failing;

    Binding(Cell<?> cell, Directive attribute, Diagnostics diag) {
        this.cell = cell;
        this.attribute = attribute;
        this.diag = diag;
    }

    /** A failing computation keeps the last written value and is reported once until it recovers. */
    @Override
    public final boolean update() {
        Object v;
        try {
            v = cell.get();
        } catch (RuntimeException ex) {
            if (!failing) {
                failing = true;
                diag.report(BindingCode.RUNTIME_EXCEPTION, attribute.valuePos(), "'" + attribute.value() + "' threw " + ex);
            }
            return false;
        }
        failing = false;
        return write(v);
    }

    abstract boolean write(Object value);

    /** {@code bind:text}: the element's only child, a text node; styled when the converter gives styles. */
    static final class Text extends Binding {
        private final Bindings.TextConverter converter;
        private final TextNode text;

        Text(Cell<?> cell, Directive a, Diagnostics diag, Bindings.TextConverter converter, TextNode text) {
            super(cell, a, diag);
            this.converter = converter;
            this.text = text;
        }

        @Override
        boolean write(Object value) {
            StyledText styled = converter.styled(value);
            if (styled != null) {
                if (styled.equals(text.styled())) {
                    return false;
                }
                text.setStyled(styled);
                return true;
            }
            String s = converter.plain(value);
            if (text.styled() == null && s.equals(text.text())) {
                return false;
            }
            text.setText(s);
            return true;
        }
    }

    /** {@code bind:class:<name>}: the class is present while the value is true. */
    static final class ClassToggle extends Binding {
        private final Element element;
        private final String cls;

        ClassToggle(Cell<?> cell, Directive a, Diagnostics diag, Element element, String cls) {
            super(cell, a, diag);
            this.element = element;
            this.cls = cls;
        }

        @Override
        boolean write(Object value) {
            boolean on = Boolean.TRUE.equals(value);
            if (on == element.hasClass(cls)) {
                return false;
            }
            if (on) {
                element.addClass(cls);
            } else {
                element.removeClass(cls);
            }
            return true;
        }
    }

    /** {@code bind:disabled}, or {@code bind:open} on a details: the boolean attribute is present while the value is true. */
    static final class BooleanAttribute extends Binding {
        private final Element element;
        private final String name;

        BooleanAttribute(Cell<?> cell, Directive a, Diagnostics diag, Element element, String name) {
            super(cell, a, diag);
            this.element = element;
            this.name = name;
        }

        @Override
        boolean write(Object value) {
            boolean on = Boolean.TRUE.equals(value);
            if (on == (element.attribute(name) != null)) {
                return false;
            }
            if (on) {
                element.setAttribute(name, "");
            } else {
                element.removeAttribute(name);
            }
            return true;
        }
    }

    /** {@code bind:value}: the control shows the cell (a String, or a number as text); user edits write it back through an input listener. */
    static final class ControlValue extends Binding {
        private final Element element;

        ControlValue(Cell<?> cell, Directive a, Diagnostics diag, Element element) {
            super(cell, a, diag);
            this.element = element;
        }

        /** Repaint-only, or a restyle of a select's options: the control marks what it needs itself. */
        @Override
        boolean write(Object value) {
            element.control().setValue(value == null ? "" : value.toString());
            return false;
        }
    }

    /** {@code bind:checked}: the checkbox follows the cell; toggles write it back through an input listener. Also a radio's group test. */
    static final class Checked extends Binding {
        private final Element element;

        Checked(Cell<?> cell, Directive a, Diagnostics diag, Element element) {
            super(cell, a, diag);
            this.element = element;
        }

        @Override
        boolean write(Object value) {
            element.control().setChecked(Boolean.TRUE.equals(value));
            return false;
        }
    }

    /** {@code bind:open}: the dialog is in the top layer while the cell is true; Escape writes false back. */
    static final class Open extends Binding {
        private final Element element;
        private final TopLayer topLayer;

        Open(Cell<?> cell, Directive a, Diagnostics diag, Element element, TopLayer topLayer) {
            super(cell, a, diag);
            this.element = element;
            this.topLayer = topLayer;
        }

        @Override
        boolean write(Object value) {
            if (Boolean.TRUE.equals(value)) {
                topLayer.open(element);
            } else {
                topLayer.close(element);
            }
            return false;
        }
    }

    /**
     * {@code bind:scrolltop}: the element scrolls to the field; scrolling, and clamping to the
     * scroll range after layout, write the offset back through a scroll listener.
     */
    static final class Scroll extends Binding {
        private final Element element;

        Scroll(Cell<?> cell, Directive a, Diagnostics diag, Element element) {
            super(cell, a, diag);
            this.element = element;
        }

        /**
         * Requests the offset against the next layout's range, so content bound in the same frame
         * counts; reports a write only past the current range, to make that layout run.
         */
        @Override
        boolean write(Object value) {
            int top = value == null ? 0 : (Integer) value;
            if (top == Math.round(element.scrollTop())) {
                return false;
            }
            element.requestScrollTop(top);
            return top > element.maxScrollTop();
        }
    }

    /** {@code bind:<attribute>}: a plain attribute as text. */
    static final class Attribute extends Binding {
        private final Function<Object, String> textOf;
        private final Element element;
        private final String name;

        Attribute(Cell<?> cell, Directive a, Diagnostics diag, Function<Object, String> textOf, Element element,
                  String name) {
            super(cell, a, diag);
            this.textOf = textOf;
            this.element = element;
            this.name = name;
        }

        @Override
        boolean write(Object value) {
            String s = textOf.apply(value);
            if (Objects.equals(s, element.attribute(name))) {
                return false;
            }
            element.setAttribute(name, s);
            return true;
        }
    }

    /** Payloads are host objects without a reliable {@code equals}; any new identity is a change. */
    static final class Payload extends Binding {
        private final Element element;

        Payload(Cell<?> cell, Directive a, Diagnostics diag, Element element) {
            super(cell, a, diag);
            this.element = element;
        }

        @Override
        boolean write(Object value) {
            if (value == element.payload()) {
                return false;
            }
            element.setPayload(value);
            return true;
        }
    }
}
