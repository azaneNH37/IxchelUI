package com.azane.ixchel.core.view;

import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A translation split into text, argument references and marks; see {@link Translations} for the
 * syntax. Problems are collected as messages; the parts are the text as far as it can be shown.
 */
final class Message {
    sealed interface Part permits Text, Arg, Mark {
    }

    /** Text; a {@code \n} of the translation is a {@link TextShaper#LINE_BREAK}. */
    record Text(String text) implements Part {
    }

    /** The argument at {@code index}, from 0. */
    record Arg(int index) implements Part {
    }

    /** A copy of mark {@code name} holding {@code text}. */
    record Mark(String name, String text) implements Part {
    }

    /**
     * @param problems   what makes the translation invalid
     * @param undeclared tags written in it that name no mark, shown as text
     */
    record Parsed(List<Part> parts, List<String> problems, List<String> undeclared) {
    }

    private final String s;
    private final Set<String> marks;
    private final int argCount;
    private final List<Part> parts = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> undeclared = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private String openMark;
    private int next;
    private int i;

    private Message(String s, Set<String> marks, int argCount) {
        this.s = s;
        this.marks = marks;
        this.argCount = argCount;
    }

    /**
     * @param complete whether every mark and argument must be used (the base language)
     */
    static Parsed parse(String translation, Set<String> marks, int argCount, boolean complete) {
        Message m = new Message(translation, marks, argCount);
        m.run();
        if (complete) {
            for (String name : marks) {
                if (m.parts.stream().noneMatch(p -> p instanceof Mark k && k.name().equals(name))) {
                    m.problems.add("mark '" + name + "' is not used");
                }
            }
            for (int k = 0; k < argCount; k++) {
                int index = k;
                if (m.parts.stream().noneMatch(p -> p instanceof Arg a && a.index() == index)) {
                    m.problems.add("argument " + (k + 1) + " is not used");
                }
            }
        }
        return new Parsed(List.copyOf(m.parts), List.copyOf(m.problems), List.copyOf(m.undeclared));
    }

    private void run() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                percent();
            } else if (c == '<' && tag()) {
                continue;
            } else {
                text.append(c == '\n' ? TextShaper.LINE_BREAK : c);
                i++;
            }
        }
        if (openMark != null) {
            problems.add("<" + openMark + "> is never closed");
            parts.add(new Mark(openMark, text.toString()));
            text.setLength(0);
            openMark = null;
        }
        flush();
    }

    /** {@code %%}, {@code %s} or {@code %n$s} at {@code i}; anything else is a problem and stays text. */
    private void percent() {
        if (s.startsWith("%%", i)) {
            text.append('%');
            i += 2;
            return;
        }
        int k = i + 1;
        while (k < s.length() && Character.isDigit(s.charAt(k))) {
            k++;
        }
        int index;
        int end;
        if (k == i + 1 && s.startsWith("s", k)) {
            index = next++;
            end = k + 1;
        } else if (k > i + 1 && s.startsWith("$s", k)) {
            index = Integer.parseInt(s.substring(i + 1, k)) - 1;
            end = k + 2;
        } else {
            int to = Math.min(s.length(), Math.max(k, i + 1) + 1);
            problems.add("unsupported conversion '" + s.substring(i, to) + "'; only %s, %n$s and %% are");
            text.append('%');
            i++;
            return;
        }
        String ref = s.substring(i, end);
        i = end;
        if (openMark != null) {
            problems.add("'" + ref + "' is inside <" + openMark + ">; a mark holds only text");
            return;
        }
        if (index < 0 || index >= argCount) {
            problems.add("'" + ref + "' refers to argument " + (index + 1) + " but the element has "
                    + argCount + (argCount == 1 ? " argument" : " arguments"));
            return;
        }
        flush();
        parts.add(new Arg(index));
    }

    /** A mark tag at {@code i}: consumed when it names a mark, else left for the caller to take as text. */
    private boolean tag() {
        boolean closing = s.startsWith("</", i);
        int start = i + (closing ? 2 : 1);
        int k = start;
        while (k < s.length() && isNameChar(s.charAt(k), k == start)) {
            k++;
        }
        if (k == start || k >= s.length() || s.charAt(k) != '>') {
            return false;
        }
        String name = s.substring(start, k);
        if (!marks.contains(name)) {
            undeclared.add(s.substring(i, k + 1));
            return false;
        }
        i = k + 1;
        if (closing) {
            if (name.equals(openMark)) {
                parts.add(new Mark(name, text.toString()));
                text.setLength(0);
                openMark = null;
            } else {
                problems.add("</" + name + "> closes no open <" + name + ">");
            }
        } else if (openMark != null) {
            problems.add("<" + name + "> is inside <" + openMark + ">; marks do not nest");
        } else {
            flush();
            openMark = name;
        }
        return true;
    }

    private void flush() {
        if (!text.isEmpty()) {
            parts.add(new Text(text.toString()));
            text.setLength(0);
        }
    }

    private static boolean isNameChar(char c, boolean first) {
        boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
        return first ? letter : letter || c >= '0' && c <= '9' || c == '-' || c == '_';
    }
}
