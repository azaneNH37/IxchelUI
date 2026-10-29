package com.azane.ixchel.core.template;

import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict template parser: no HTML5 error recovery, never closes elements implicitly. Every problem is
 * reported to {@link Diagnostics}; parsing continues past errors so one load reports as many as it can.
 *
 * <p>Stylesheets come from top-level {@code <link rel="stylesheet" href="…"/>} (self-closing, no other
 * attributes) and {@code <style>} elements; neither appears in the tree, both are collected in document
 * order as {@link Template#styles()}.
 */
public final class TemplateParser {
    private static final Set<String> RESERVED = Set.of("t");
    private static final Set<String> STRUCTURAL = Set.of("each", "as", "key", "if");

    private final String src;
    private final String file;
    private final TagRegistry tags;
    private final Diagnostics diag;
    private final int[] lineStarts;
    private final List<Template.StyleSource> styles = new ArrayList<>();
    private int i;

    private TemplateParser(String source, String file, TagRegistry tags, Diagnostics diag) {
        this.src = source.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n');
        this.file = file;
        this.tags = tags;
        this.diag = diag;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int k = 0; k < src.length(); k++) {
            if (src.charAt(k) == '\n') {
                starts.add(k + 1);
            }
        }
        lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    public static Template parse(String source, String file, TagRegistry tags, Diagnostics diag) {
        TemplateParser p = new TemplateParser(source, file, tags, diag);
        List<Node> roots = new ArrayList<>();
        while (true) {
            p.parseNodes(roots, true);
            if (p.i >= p.src.length()) {
                break;
            }
            SourcePos at = p.pos(p.i);
            String name = p.readClosingTag();
            p.diag.error(at, "closing tag </" + name + "> has no matching opening tag");
        }
        return new Template(roots, p.styles);
    }

    /** Parses nodes into {@code out} until EOF or a closing tag, which is left unconsumed. */
    private void parseNodes(List<Node> out, boolean topLevel) {
        while (i < src.length()) {
            if (src.charAt(i) != '<') {
                int start = i;
                while (i < src.length() && src.charAt(i) != '<') {
                    i++;
                }
                TextNode text = text(start, i);
                if (text != null) {
                    out.add(text);
                }
            } else if (src.startsWith("</", i)) {
                return;
            } else if (src.startsWith("<!--", i)) {
                int end = src.indexOf("-->", i + 4);
                if (end < 0) {
                    diag.error(pos(i), "unterminated comment");
                    i = src.length();
                } else {
                    i = end + 3;
                }
            } else if (i + 1 < src.length() && isAsciiLetter(src.charAt(i + 1))) {
                Element e = parseElement(topLevel);
                if (e != null) {
                    out.add(e);
                }
            } else {
                diag.error(pos(i), src.startsWith("<!", i) || src.startsWith("<?", i)
                        ? "doctype, CDATA and processing instructions are not supported"
                        : "stray '<'; write &lt; for a literal '<'");
                i++;
            }
        }
    }

    /** Returns null for elements that must not appear in the tree (unknown, {@code <style>}, {@code <link>}). */
    private Element parseElement(boolean topLevel) {
        int open = i;
        SourcePos openPos = pos(open);
        i++;
        String tag = readName(false);
        boolean known = tags.isKnownTag(tag);
        if (!known) {
            diag.error(openPos, "unknown tag <" + tag + ">");
        }
        boolean isStyle = tag.equals("style");
        boolean isLink = tag.equals("link");
        if ((isStyle || isLink) && !topLevel) {
            diag.error(openPos, "<" + tag + "> is only allowed at the top level");
        }
        Element element = new Element(tag, openPos);
        Set<String> seen = new HashSet<>();
        boolean selfClosing = false;
        while (true) {
            skipWhitespace();
            if (i >= src.length()) {
                diag.error(openPos, "unterminated start tag <" + tag + ">");
                return null;
            }
            char c = src.charAt(i);
            if (c == '>') {
                i++;
                break;
            }
            if (src.startsWith("/>", i)) {
                i += 2;
                selfClosing = true;
                break;
            }
            if (!isAsciiLetter(c)) {
                diag.error(pos(i), "unexpected '" + c + "' in start tag <" + tag + ">");
                i++;
                continue;
            }
            parseAttribute(element, known, seen);
        }
        if (known && tag.equals("img") && element.attribute("src") == null
                && element.directives().stream().noneMatch(b -> b.name().equals("bind:src"))) {
            diag.error(openPos, "<img> requires a 'src' attribute or a 'bind:src' binding");
        }
        if (known && tag.equals("input") && FormControl.Kind.ofInputType(element.attribute("type")) == null) {
            diag.error(openPos, "unsupported <input type=\"" + element.attribute("type")
                    + "\">; supported: text, number, checkbox, range");
        }

        if (isStyle) {
            if (!selfClosing) {
                parseStyleContent(openPos);
            }
            return null;
        }
        if (isLink) {
            if (!selfClosing) {
                diag.error(openPos, "<link> must be self-closing: <link rel=\"stylesheet\" href=\"…\"/>");
            }
            boolean valid = topLevel;
            if (!"stylesheet".equals(element.attribute("rel"))) {
                diag.error(openPos, "<link> requires rel=\"stylesheet\"");
                valid = false;
            }
            String href = element.attribute("href");
            if (href == null || href.isEmpty()) {
                diag.error(openPos, "<link> requires an 'href' attribute");
                valid = false;
            }
            if (valid) {
                styles.add(new Template.Link(href, openPos));
            }
            return null;
        }
        if (!selfClosing) {
            List<Node> children = new ArrayList<>();
            parseNodes(children, false);
            if (i >= src.length()) {
                diag.error(openPos, "<" + tag + "> is never closed");
            } else {
                SourcePos closePos = pos(i);
                String closing = readClosingTag();
                if (!closing.equals(tag)) {
                    diag.error(closePos, "closing tag </" + closing + "> does not match <" + tag
                            + "> opened at " + openPos);
                }
            }
            if (known && (tag.equals("img") || tag.equals("input")) && !children.isEmpty()) {
                diag.error(children.get(0).pos(), "<" + tag + "> must not have children");
            } else if (known && tag.equals("select") && children.stream().anyMatch(
                    n -> !(n instanceof Element c && c.tag().equals("option")))) {
                diag.error(openPos, "<select> may only contain <option> elements");
            } else if (known && tag.equals("option") && children.stream().anyMatch(n -> !(n instanceof TextNode))) {
                diag.error(openPos, "<option> may only contain text");
            } else {
                for (Node child : children) {
                    element.appendChild(child);
                }
            }
        }
        return known ? element : null;
    }

    private void parseAttribute(Element element, boolean tagKnown, Set<String> seen) {
        String tag = element.tag();
        SourcePos namePos = pos(i);
        String name = readName(true);
        skipWhitespace();
        if (i >= src.length() || src.charAt(i) != '=') {
            diag.error(namePos, "attribute '" + name + "' needs a quoted value");
            return;
        }
        i++;
        skipWhitespace();
        if (i >= src.length() || (src.charAt(i) != '"' && src.charAt(i) != '\'')) {
            diag.error(namePos, "value of attribute '" + name + "' must be quoted");
            while (i < src.length() && !Character.isWhitespace(src.charAt(i))
                    && src.charAt(i) != '>' && !src.startsWith("/>", i)) {
                i++;
            }
            return;
        }
        char quote = src.charAt(i++);
        int valueStart = i;
        int end = src.indexOf(quote, valueStart);
        if (end < 0) {
            diag.error(namePos, "unterminated value of attribute '" + name + "'");
            i = src.length();
            return;
        }
        i = end + 1;
        if (!seen.add(name)) {
            diag.error(namePos, "duplicate attribute '" + name + "' on <" + tag + ">");
            return;
        }
        if (!tagKnown) {
            return;
        }
        if (isDirective(name)) {
            if (tag.equals("style") || tag.equals("link")) {
                diag.error(namePos, "<" + tag + "> takes no " + (tag.equals("style") ? "attributes" : "bindings"));
            } else {
                element.addDirective(new Directive(name, decode(valueStart, end, false), namePos, pos(valueStart)));
            }
            return;
        }
        if (isReserved(name)) {
            diag.error(namePos, "attribute '" + name + "' is not supported yet");
            return;
        }
        if (!tags.isAllowedAttribute(tag, name)) {
            diag.error(namePos, "unknown attribute '" + name + "' on <" + tag + ">");
            return;
        }
        String value = decode(valueStart, end, false);
        switch (name) {
            case "class" -> {
                for (String cls : value.trim().split("\\s+")) {
                    if (!cls.isEmpty()) {
                        element.addClass(cls);
                    }
                }
            }
            case "style" -> element.setInlineStyle(CssParser.parseDeclarations(value, pos(valueStart), diag));
            default -> element.setAttribute(name, value);
        }
    }

    /** Attributes interpreted by the binding layer: {@code bind:*}, {@code on:*}, {@code state:*}, {@code each/as/key/if}. */
    static boolean isDirective(String attribute) {
        return attribute.startsWith("bind:") || attribute.startsWith("on:") || attribute.startsWith("state:")
                || STRUCTURAL.contains(attribute);
    }

    /** Template attributes with planned meaning that are not implemented yet (i18n). */
    static boolean isReserved(String attribute) {
        return RESERVED.contains(attribute);
    }

    private void parseStyleContent(SourcePos openPos) {
        int start = i;
        int end = src.indexOf("</style", start);
        if (end < 0) {
            diag.error(openPos, "<style> is never closed");
            i = src.length();
            return;
        }
        styles.add(new Template.Inline(src.substring(start, end), pos(start)));
        i = end;
        SourcePos closePos = pos(i);
        String closing = readClosingTag();
        if (!closing.equals("style")) {
            diag.error(closePos, "closing tag </" + closing + "> does not match <style> opened at " + openPos);
        }
    }

    /** Consumes {@code </name>} at {@code i} and returns the name. */
    private String readClosingTag() {
        i += 2;
        String name = readName(false);
        skipWhitespace();
        if (i < src.length() && src.charAt(i) == '>') {
            i++;
        } else {
            diag.error(pos(i), "expected '>' to end closing tag </" + name + ">");
        }
        return name;
    }

    /**
     * Tag or attribute name. Attribute names may continue with {@code :} before a letter ({@code bind:text})
     * and {@code .} before a letter or digit ({@code on:keydown.f1}, the key filters of {@code on:*}).
     */
    private String readName(boolean attribute) {
        int start = i;
        if (i < src.length() && isAsciiLetter(src.charAt(i))) {
            i++;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (isAsciiLetter(c) || isDigit(c) || c == '-') {
                    i++;
                } else if (attribute && c == ':' && i + 1 < src.length() && isAsciiLetter(src.charAt(i + 1))) {
                    i++;
                } else if (attribute && c == '.' && i + 1 < src.length()
                        && (isAsciiLetter(src.charAt(i + 1)) || isDigit(src.charAt(i + 1)))) {
                    i++;
                } else {
                    break;
                }
            }
        }
        if (start == i) {
            diag.error(pos(i), "expected a name");
        }
        return src.substring(start, i);
    }

    private TextNode text(int start, int end) {
        int first = start;
        while (first < end && isSpace(src.charAt(first))) {
            first++;
        }
        if (first == end) {
            return null;
        }
        return new TextNode(decode(first, end, true).trim(), pos(first));
    }

    /** Decodes entities in {@code [start, end)}; with {@code collapse}, whitespace runs become one space. */
    private String decode(int start, int end, boolean collapse) {
        StringBuilder sb = new StringBuilder(end - start);
        int k = start;
        while (k < end) {
            char c = src.charAt(k);
            if (collapse && isSpace(c)) {
                while (k < end && isSpace(src.charAt(k))) {
                    k++;
                }
                sb.append(' ');
            } else if (c == '&' && k + 1 < end && (isAsciiLetter(src.charAt(k + 1)) || src.charAt(k + 1) == '#')) {
                int semi = src.indexOf(';', k);
                String decoded = semi < 0 || semi >= end ? null : entity(src.substring(k + 1, semi));
                if (decoded == null) {
                    diag.error(pos(k), "invalid character reference; write &amp; for a literal '&'");
                    sb.append('&');
                    k++;
                } else {
                    sb.append(decoded);
                    k = semi + 1;
                }
            } else {
                sb.append(c);
                k++;
            }
        }
        return sb.toString();
    }

    private static String entity(String body) {
        switch (body) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "quot": return "\"";
            case "apos": return "'";
            default: break;
        }
        if (body.length() < 2 || body.charAt(0) != '#') {
            return null;
        }
        boolean hex = body.charAt(1) == 'x' || body.charAt(1) == 'X';
        String digits = body.substring(hex ? 2 : 1);
        if (digits.isEmpty() || digits.length() > 8) {
            return null;
        }
        for (int k = 0; k < digits.length(); k++) {
            if (Character.digit(digits.charAt(k), hex ? 16 : 10) < 0) {
                return null;
            }
        }
        long cp = Long.parseLong(digits, hex ? 16 : 10);
        if (cp == 0 || cp > Character.MAX_CODE_POINT || (cp >= 0xD800 && cp <= 0xDFFF)) {
            return null;
        }
        return Character.toString((int) cp);
    }

    private void skipWhitespace() {
        while (i < src.length() && isSpace(src.charAt(i))) {
            i++;
        }
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private SourcePos pos(int index) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= index) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return new SourcePos(file, lo + 1, index - lineStarts[lo] + 1);
    }
}
