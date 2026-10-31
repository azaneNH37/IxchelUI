package com.azane.ixchel.kit;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Writes the CLI's {@code --json} output from maps, lists, strings, numbers, booleans and {@code null}. */
final class Json {
    private Json() {
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            string(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                newline(sb, depth + 1);
                string((String) e.getKey(), sb);
                sb.append(": ");
                write(e.getValue(), sb, depth + 1);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            newline(sb, depth);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                newline(sb, depth + 1);
                write(list.get(i), sb, depth + 1);
                if (i < list.size() - 1) {
                    sb.append(',');
                }
            }
            newline(sb, depth);
            sb.append(']');
        } else {
            throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
        }
    }

    private static void newline(StringBuilder sb, int depth) {
        sb.append('\n');
        sb.append("  ".repeat(depth));
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
