package com.azane.ixchel.core.dom;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * The state of a form control ({@link Element#control()}): an {@code <input>} of one of the
 * {@link Kind}s, or a {@code <select>}. Text and number fields keep their text in a {@link TextField};
 * a checkbox its checkedness; a range and a select a value string. As in the DOM a value is a string;
 * setting one from code fires no event. Editing is the event dispatcher's default action.
 */
public final class FormControl {
    /** {@code <input type>} values, plus {@link #SELECT} for {@code <select>}. */
    public enum Kind {
        TEXT, NUMBER, CHECKBOX, RANGE, SELECT;

        /** The kind of {@code <input type="…">}, or {@code null} for an unsupported type. */
        public static Kind ofInputType(String type) {
            return switch (type == null ? "text" : type) {
                case "text" -> TEXT;
                case "number" -> NUMBER;
                case "checkbox" -> CHECKBOX;
                case "range" -> RANGE;
                default -> null;
            };
        }
    }

    /** Width of a range's thumb, in GUI pixels; its center runs over the content box less half of it at either end. */
    public static final float THUMB_WIDTH = 5;

    private final Element owner;
    private final Kind kind;
    private final TextField text;
    private String value;
    private boolean checked;

    FormControl(Element owner, Kind kind) {
        this.owner = owner;
        this.kind = kind;
        this.text = kind == Kind.TEXT || kind == Kind.NUMBER ? new TextField(owner) : null;
    }

    public Kind kind() {
        return kind;
    }

    /** The editing state of a text or number field; {@code null} for the other kinds. */
    public TextField text() {
        return text;
    }

    /**
     * The value as the DOM's {@code value}: a field's text; a range's number clamped to its range and
     * snapped to its step (the midpoint when never set); a select's selected option's value, or
     * {@code ""} when none is selected; {@code "on"} for a checkbox.
     */
    public String value() {
        return switch (kind) {
            case TEXT, NUMBER -> text.value();
            case CHECKBOX -> "on";
            case RANGE -> format(number());
            case SELECT -> {
                Element o = selectedOption();
                yield o == null ? "" : optionValue(o);
            }
        };
    }

    /**
     * Sets the value from code. A select keeps {@code value} even when no option has it yet (options
     * may arrive later); an unknown value selects nothing. Changes ask for a repaint, and a select's
     * change restyles its options for {@code :checked}.
     */
    public void setValue(String value) {
        switch (kind) {
            case TEXT, NUMBER -> text.setValue(value);
            case CHECKBOX -> {
            }
            case RANGE, SELECT -> {
                if (!value.equals(this.value)) {
                    this.value = value;
                    if (kind == Kind.SELECT) {
                        owner.markStyleDirty();
                    }
                    owner.requestRepaint();
                }
            }
        }
    }

    public boolean checked() {
        return checked;
    }

    /** Sets a checkbox's checkedness ({@code :checked}). */
    public void setChecked(boolean checked) {
        if (checked != this.checked) {
            this.checked = checked;
            owner.setState(Element.CHECKED, checked);
        }
    }

    /** {@code min} attribute, 0 when absent or not a number. */
    public double min() {
        return number("min", 0);
    }

    /** {@code max} attribute, 100 when absent or not a number; never below {@link #min()}. */
    public double max() {
        return Math.max(min(), number("max", 100));
    }

    /** {@code step} attribute, 1 when absent, not a number or not positive. */
    public double step() {
        double s = number("step", 1);
        return s > 0 ? s : 1;
    }

    /** A range's value as a number: see {@link #value()}. */
    public double number() {
        double lo = min();
        double hi = max();
        double v;
        try {
            v = value == null ? lo + (hi - lo) / 2 : Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            v = lo + (hi - lo) / 2;
        }
        return snap(v);
    }

    /** {@code v} clamped to {@code [min, max]} and rounded to the nearest step from {@code min}. */
    public double snap(double v) {
        double lo = min();
        double hi = max();
        double step = step();
        double snapped = lo + Math.round((Math.max(lo, Math.min(hi, v)) - lo) / step) * step;
        if (snapped > hi) {
            snapped -= step;
        }
        return Math.max(lo, snapped);
    }

    /** Where a range's value sits between its ends, 0 to 1. */
    public float fraction() {
        double lo = min();
        double hi = max();
        return hi == lo ? 0 : (float) ((number() - lo) / (hi - lo));
    }

    /** The {@code <option>} children of a select, in order. */
    public List<Element> options() {
        return owner.children().stream().filter(n -> n instanceof Element e && e.tag().equals("option"))
                .map(n -> (Element) n).toList();
    }

    /**
     * The selected option of a select: the first whose value equals the value set, or the first option
     * when no value was ever set; {@code null} when none matches.
     */
    public Element selectedOption() {
        List<Node> children = owner.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Element o && o.tag().equals("option")
                    && (value == null || value.equals(optionValue(o)))) {
                return o;
            }
        }
        return null;
    }

    /** The text a select shows: its selected option's label. */
    public String label() {
        Element o = selectedOption();
        return o == null ? "" : o.textContent().strip();
    }

    /** An option's {@code value} attribute, else its label, as in HTML. */
    public static String optionValue(Element option) {
        String v = option.attribute("value");
        return v != null ? v : option.textContent().strip();
    }

    /** {@code d} without a fractional part when it is whole, as HTML serializes numbers. */
    public static String format(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        // Steps like 0.1 accumulate binary error; 12 significant digits hide it.
        return new BigDecimal(d).round(new MathContext(12)).stripTrailingZeros().toPlainString();
    }

    private double number(String attribute, double fallback) {
        String raw = owner.attribute(attribute);
        if (raw == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(raw.trim());
            return Double.isFinite(d) ? d : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
