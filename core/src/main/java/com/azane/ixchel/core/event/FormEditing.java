package com.azane.ixchel.core.event;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.TextField;

import java.util.List;

/**
 * Default actions of the form controls other than text editing: toggling a checkbox, stepping and
 * dragging a range, stepping a select's selection and a number field's value.
 */
final class FormEditing {
    static final int KEY_SPACE = 32;
    static final int KEY_ENTER = 257;
    static final int KEY_KP_ENTER = 335;
    static final int KEY_RIGHT = 262;
    static final int KEY_LEFT = 263;
    static final int KEY_DOWN = 264;
    static final int KEY_UP = 265;
    static final int KEY_PAGE_UP = 266;
    static final int KEY_PAGE_DOWN = 267;
    static final int KEY_HOME = 268;
    static final int KEY_END = 269;

    private FormEditing() {
    }

    static boolean isEnter(int key) {
        return key == KEY_ENTER || key == KEY_KP_ENTER;
    }

    static void toggle(FormControl checkbox) {
        checkbox.setChecked(!checkbox.checked());
    }

    /**
     * A range's key: arrows step by {@code step} (Right and Up up), Page Up/Down by a tenth of the
     * range (at least a step), Home and End to the ends. Returns whether the key is one of those;
     * {@code changed[0]} tells whether the value moved.
     */
    static boolean rangeKey(FormControl c, int key, boolean[] changed) {
        double v = c.number();
        double step = c.step();
        double page = Math.max(step, (c.max() - c.min()) / 10);
        double next;
        switch (key) {
            case KEY_RIGHT, KEY_UP -> next = v + step;
            case KEY_LEFT, KEY_DOWN -> next = v - step;
            case KEY_PAGE_UP -> next = v + page;
            case KEY_PAGE_DOWN -> next = v - page;
            case KEY_HOME -> next = c.min();
            case KEY_END -> next = c.max();
            default -> {
                return false;
            }
        }
        changed[0] = setNumber(c, next);
        return true;
    }

    /** Moves a range's value to where {@code x} falls on its track; whether it changed. */
    static boolean rangeAt(Element range, float x) {
        FormControl c = range.control();
        LayoutBox b = range.box();
        float left = b.x() + b.borderLeft() + b.paddingLeft() + FormControl.THUMB_WIDTH / 2;
        float width = b.width() - b.borderLeft() - b.paddingLeft() - b.paddingRight() - b.borderRight() - FormControl.THUMB_WIDTH;
        double f = width <= 0 ? 0 : Math.max(0, Math.min(1, (x - left) / width));
        return setNumber(c, c.min() + f * (c.max() - c.min()));
    }

    private static boolean setNumber(FormControl c, double v) {
        String before = c.value();
        c.setValue(FormControl.format(c.snap(v)));
        return !c.value().equals(before);
    }

    /** Selects the option {@code direction} (-1 or 1) from the selected one, stopping at the ends; whether it changed. */
    static boolean stepSelect(FormControl select, int direction) {
        List<Element> options = select.options();
        if (options.isEmpty()) {
            return false;
        }
        int i = options.indexOf(select.selectedOption());
        int next = i < 0 ? 0 : Math.max(0, Math.min(options.size() - 1, i + direction));
        if (next == i) {
            return false;
        }
        select.setValue(FormControl.optionValue(options.get(next)));
        return true;
    }

    /**
     * Steps a number field's value by {@code step} within {@code min}/{@code max} when they are set (a
     * value that is not a number starts from 0, or {@code min}); whether it changed.
     */
    static boolean stepNumber(Element input, int direction) {
        FormControl c = input.control();
        TextField f = c.text();
        double v;
        try {
            v = Double.parseDouble(f.value().trim());
        } catch (NumberFormatException ex) {
            v = input.attribute("min") != null ? c.min() : 0;
            direction = 0;
        }
        double next = v + direction * c.step();
        if (input.attribute("min") != null) {
            next = Math.max(c.min(), next);
        }
        if (input.attribute("max") != null) {
            next = Math.min(c.max(), next);
        }
        String text = FormControl.format(next);
        if (text.equals(f.value())) {
            return false;
        }
        f.setValue(text);
        return true;
    }
}
