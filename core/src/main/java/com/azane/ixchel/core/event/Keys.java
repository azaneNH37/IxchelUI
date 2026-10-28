package com.azane.ixchel.core.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Key, button and modifier names a template may use in {@code on:keydown.<key>[.<modifier>…]},
 * {@code on:keyup.…} and {@code on:click[.<button>][.<modifier>…]} (also {@code mousedown},
 * {@code mouseup}) filters, mapped to GLFW codes. Keys: {@code enter escape space tab backspace
 * delete insert up down left right home end pageup pagedown f1…f12 a…z 0…9}; buttons: {@code left
 * right middle}; modifiers: {@code shift ctrl alt}.
 */
public final class Keys {
    /** GLFW modifier bits. */
    public static final int MOD_SHIFT = 1;
    public static final int MOD_CTRL = 2;
    public static final int MOD_ALT = 4;
    /** Command on macOS, the Windows key elsewhere; text editing treats it as Ctrl, filters ignore it. */
    public static final int MOD_SUPER = 8;
    /** The modifiers a filter compares: Shift, Ctrl and Alt. */
    public static final int FILTER_MODIFIERS = MOD_SHIFT | MOD_CTRL | MOD_ALT;

    private static final Map<String, Integer> CODES = new HashMap<>();
    private static final Map<String, Integer> MODIFIERS = Map.of("shift", MOD_SHIFT, "ctrl", MOD_CTRL, "alt", MOD_ALT);
    private static final Map<String, Integer> BUTTONS = Map.of("left", 0, "right", 1, "middle", 2);

    static {
        CODES.put("space", 32);
        for (char c = '0'; c <= '9'; c++) {
            CODES.put(String.valueOf(c), 48 + (c - '0'));
        }
        for (char c = 'a'; c <= 'z'; c++) {
            CODES.put(String.valueOf(c), 65 + (c - 'a'));
        }
        CODES.put("escape", 256);
        CODES.put("enter", 257);
        CODES.put("tab", 258);
        CODES.put("backspace", 259);
        CODES.put("insert", 260);
        CODES.put("delete", 261);
        CODES.put("right", 262);
        CODES.put("left", 263);
        CODES.put("down", 264);
        CODES.put("up", 265);
        CODES.put("pageup", 266);
        CODES.put("pagedown", 267);
        CODES.put("home", 268);
        CODES.put("end", 269);
        for (int f = 1; f <= 12; f++) {
            CODES.put("f" + f, 289 + f);
        }
    }

    private Keys() {
    }

    /** The GLFW key code of {@code name}, or -1 for an unknown name. */
    public static int code(String name) {
        Integer code = CODES.get(name);
        return code == null ? -1 : code;
    }

    /** The GLFW modifier bit of {@code name} ({@code shift}, {@code ctrl}, {@code alt}), or 0 for an unknown name. */
    public static int modifier(String name) {
        Integer bit = MODIFIERS.get(name);
        return bit == null ? 0 : bit;
    }

    /** The mouse button of {@code name} ({@code left} 0, {@code right} 1, {@code middle} 2), or -1 for an unknown name. */
    public static int button(String name) {
        Integer button = BUTTONS.get(name);
        return button == null ? -1 : button;
    }
}
