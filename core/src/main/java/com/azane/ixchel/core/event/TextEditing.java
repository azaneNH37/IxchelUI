package com.azane.ixchel.core.event;

import com.azane.ixchel.core.dom.TextField;

/**
 * Default actions of a focused {@code <input>}: caret movement, selection, deletion, the clipboard
 * shortcuts and inserting typed or pasted text. Ctrl and Super (Command) both make the shortcuts; a
 * word is a run of characters other than spaces.
 */
final class TextEditing {
    private static final int KEY_A = 65;
    private static final int KEY_C = 67;
    private static final int KEY_V = 86;
    private static final int KEY_X = 88;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;

    private TextEditing() {
    }

    /** Applies the action of {@code key}, if it has one; whether the value changed. */
    static boolean key(TextField f, int key, int modifiers, Clipboard clipboard, int maxLength) {
        boolean shift = (modifiers & Keys.MOD_SHIFT) != 0;
        boolean shortcut = (modifiers & (Keys.MOD_CTRL | Keys.MOD_SUPER)) != 0;
        String v = f.value();
        int caret = f.caret();
        switch (key) {
            case KEY_LEFT -> move(f, shortcut ? wordLeft(v, caret)
                    : f.hasSelection() && !shift ? f.selectionStart() : previous(v, caret), shift);
            case KEY_RIGHT -> move(f, shortcut ? wordRight(v, caret)
                    : f.hasSelection() && !shift ? f.selectionEnd() : next(v, caret), shift);
            case KEY_HOME -> move(f, 0, shift);
            case KEY_END -> move(f, v.length(), shift);
            case KEY_BACKSPACE -> {
                return delete(f, shortcut ? wordLeft(v, caret) : previous(v, caret));
            }
            case KEY_DELETE -> {
                return delete(f, shortcut ? wordRight(v, caret) : next(v, caret));
            }
            case KEY_A -> {
                if (shortcut) {
                    f.select(0, v.length());
                }
            }
            case KEY_C -> {
                if (shortcut && f.hasSelection()) {
                    clipboard.set(f.selectedText());
                }
            }
            case KEY_X -> {
                if (shortcut && f.hasSelection()) {
                    clipboard.set(f.selectedText());
                    f.replaceSelection("");
                    return true;
                }
            }
            case KEY_V -> {
                if (shortcut) {
                    return insert(f, clipboard.get(), maxLength);
                }
            }
            default -> {
            }
        }
        return false;
    }

    /**
     * Replaces the selection with {@code text} without its control characters, cut to what
     * {@code maxLength} (UTF-16 units, negative for none) leaves room for; whether the value changed.
     */
    static boolean insert(TextField f, String text, int maxLength) {
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                clean.append(c);
            }
        }
        if (maxLength >= 0) {
            int room = maxLength - (f.value().length() - (f.selectionEnd() - f.selectionStart()));
            if (clean.length() > Math.max(0, room)) {
                int cut = Math.max(0, room);
                if (cut > 0 && Character.isHighSurrogate(clean.charAt(cut - 1))) {
                    cut--;
                }
                clean.setLength(cut);
            }
        }
        if (clean.length() == 0) {
            return false;
        }
        f.replaceSelection(clean.toString());
        return true;
    }

    /** The selection if there is one, else from the caret to {@code to}. */
    private static boolean delete(TextField f, int to) {
        if (!f.hasSelection()) {
            if (to == f.caret()) {
                return false;
            }
            f.select(to, f.caret());
        }
        f.replaceSelection("");
        return true;
    }

    private static void move(TextField f, int to, boolean extend) {
        f.select(extend ? f.anchor() : to, to);
    }

    private static int previous(String v, int i) {
        return i == 0 ? 0 : Character.offsetByCodePoints(v, i, -1);
    }

    private static int next(String v, int i) {
        return i == v.length() ? i : Character.offsetByCodePoints(v, i, 1);
    }

    /** The start of the word before {@code i}, spaces before the caret skipped. */
    static int wordLeft(String v, int i) {
        while (i > 0 && v.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && v.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    /** Past the end of the word at {@code i} and the spaces after it. */
    static int wordRight(String v, int i) {
        int n = v.length();
        while (i < n && v.charAt(i) != ' ') {
            i++;
        }
        while (i < n && v.charAt(i) == ' ') {
            i++;
        }
        return i;
    }
}
