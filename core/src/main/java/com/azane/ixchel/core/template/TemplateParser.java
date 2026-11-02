package com.azane.ixchel.core.template;

import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict template parser: no HTML5 error recovery, never closes elements implicitly. Every problem is
 * reported to {@link Diagnostics}; parsing continues past errors so one load reports as many as it can.
 *
 * <p>Stylesheets come from top-level {@code <link rel="stylesheet" href="…"/>} (self-closing, no other
 * attributes) and {@code <style>} elements; neither appears in the tree, both are collected in document
 * order as {@link Template#styles()}. A top-level {@code <link rel="component" href="…"/>} before the
 * first element makes the component of that file usable as a tag.
 *
 * <p>The reserved tags {@code <for each as key>}, {@code <if test>}, {@code <slot name>} and
 * {@code <template name params events>} stay in the tree with their attributes as
 * {@linkplain Element#directives() directives}; the binding layer interprets them.
 *
 * <p>Translations are resolved here, so the binding layer compiles the translated tree: {@code t="key"}
 * replaces an element's children with its translation, laying out the argument children and copies of
 * the {@code mark} children where it refers to them, and {@code t:<attribute>="key"} sets the
 * attribute to its translation (see {@link Translations}).
 */
public final class TemplateParser {
    /** Element attributes replaced by the {@code <for>} and {@code <if>} blocks. */
    private static final Set<String> MOVED = Set.of("each", "as", "key", "if");
    /** Names a parameter or event cannot take: they are attributes or targets of the component's element. */
    private static final Set<String> SHELL_NAMES = Set.of("class", "id", "style", "tabindex", "disabled", "title",
            "lang", "translate", "mark", "draggable", "text", "scrolltop", "slot", "t");
    /** The attributes {@code t:<attribute>} translates, besides a component's parameters. */
    private static final Set<String> TRANSLATABLE = Set.of("title", "placeholder");

    /** Loads the component of a {@code <link rel="component">}. */
    interface Linker {
        /** The component of the file {@code href} names, or {@code null} once the failure is reported. */
        ComponentDef link(String href, SourcePos at);
    }

    private final String src;
    private final String file;
    private final TagRegistry tags;
    private final Linker linker;
    private final Diagnostics diag;
    private final Translations translations;
    private final int[] lineStarts;
    private final List<Template.StyleSource> styles = new ArrayList<>();
    private final Map<String, ComponentDef> components = new HashMap<>();
    private boolean sawElement;
    private int templateDepth;
    private int forInTemplateDepth;
    private int tooltipDepth;
    /** Inside {@code translate="no"}: literal text is meant. */
    private boolean untranslated;
    private int i;

    private TemplateParser(String source, String file, TagRegistry tags, Linker linker, Translations translations,
                           Diagnostics diag) {
        this.src = source.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n');
        this.file = file;
        this.tags = tags;
        this.linker = linker;
        this.translations = translations;
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

    /** Parses a page that links no components and translates nothing. */
    public static Template parse(String source, String file, TagRegistry tags, Diagnostics diag) {
        return parse(source, file, tags, null, Translations.NONE, false, diag);
    }

    /**
     * @param linker        loads linked components; {@code null} when the source may not link any
     * @param componentFile the source is a component file: {@code <link>}s and one {@code <template>}
     */
    static Template parse(String source, String file, TagRegistry tags, Linker linker, Translations translations,
                          boolean componentFile, Diagnostics diag) {
        TemplateParser p = new TemplateParser(source, file, tags, linker, translations, diag);
        List<Node> roots = new ArrayList<>();
        while (true) {
            p.parseNodes(roots, true, componentFile);
            if (p.i >= p.src.length()) {
                break;
            }
            SourcePos at = p.pos(p.i);
            String name = p.readClosingTag();
            p.diag.report(TemplateCode.MISMATCHED_CLOSING_TAG, at, "closing tag </" + name + "> has no matching opening tag");
        }
        p.checkSlotPlacement(roots, null);
        p.checkMarkPlacement(roots, false);
        if (!componentFile) {
            return new Template(roots, p.styles, null);
        }
        Element template = null;
        for (Node n : roots) {
            if (template == null && n instanceof Element e && e.tag().equals("template")) {
                template = e;
            } else {
                diag.report(TemplateCode.COMPONENT_FILE_CONTENT, n.pos(), "a component file holds only <link> declarations and one <template>");
            }
        }
        if (template == null) {
            diag.report(TemplateCode.MISSING_COMPONENT_TEMPLATE, new SourcePos(file, 1, 1), "a component file needs a <template name=\"…\">");
            return new Template(List.of(), p.styles, null);
        }
        return new Template(List.of(), p.styles, p.define(template));
    }

    /** Parses nodes into {@code out} until EOF or a closing tag, which is left unconsumed. */
    private void parseNodes(List<Node> out, boolean topLevel, boolean componentFile) {
        int from = out.size();
        parseNodesRaw(out, topLevel, componentFile);
        // Whitespace-only text at the start or end of an element, or between top-level nodes, never renders.
        for (int k = out.size() - 1; k >= from; k--) {
            if (out.get(k) instanceof TextNode t && t.text().isBlank()
                    && (topLevel || k == from || k == out.size() - 1)) {
                out.remove(k);
            }
        }
    }

    private void parseNodesRaw(List<Node> out, boolean topLevel, boolean componentFile) {
        while (i < src.length()) {
            if (src.charAt(i) != '<') {
                int start = i;
                while (i < src.length() && src.charAt(i) != '<') {
                    i++;
                }
                TextNode text = text(start, i);
                if (!untranslated && hasLetter(text.text())) {
                    diag.report(TemplateCode.LITERAL_TEXT, text.pos(), "text '" + text.text().strip() + "' is not translated",
                            "move it to the language files and write t=\"key\" on the element, or mark it translate=\"no\"");
                }
                out.add(text);
            } else if (src.startsWith("</", i)) {
                return;
            } else if (src.startsWith("<!--", i)) {
                int end = src.indexOf("-->", i + 4);
                if (end < 0) {
                    diag.report(TemplateCode.SYNTAX, pos(i), "unterminated comment");
                    i = src.length();
                } else {
                    i = end + 3;
                }
            } else if (i + 1 < src.length() && isAsciiLetter(src.charAt(i + 1))) {
                Element e = parseElement(topLevel, componentFile);
                if (e != null) {
                    out.add(e);
                }
            } else {
                diag.report(TemplateCode.UNSUPPORTED_MARKUP, pos(i), src.startsWith("<!", i) || src.startsWith("<?", i)
                        ? "doctype, CDATA and processing instructions are not supported"
                        : "stray '<'; write &lt; for a literal '<'");
                i++;
            }
        }
    }

    /** Returns null for elements that must not appear in the tree (unknown, {@code <style>}, {@code <link>}). */
    private Element parseElement(boolean topLevel, boolean componentFile) {
        int open = i;
        SourcePos openPos = pos(open);
        i++;
        String tag = readName(false);
        ComponentDef component = components.get(tag);
        boolean block = tag.equals("for") || tag.equals("if");
        boolean reserved = block || tag.equals("slot") || tag.equals("template");
        boolean known = reserved || component != null || tags.isKnownTag(tag);
        if (!known) {
            diag.report(TemplateCode.UNKNOWN_TAG, openPos, "unknown tag <" + tag + ">");
        }
        boolean isStyle = tag.equals("style");
        boolean isLink = tag.equals("link");
        if ((isStyle || isLink) && !topLevel) {
            diag.report(TemplateCode.MISPLACED_ELEMENT, openPos, "<" + tag + "> is only allowed at the top level");
        }
        if (!isStyle && !isLink) {
            sawElement = true;
        }
        if (tag.equals("template") && !(componentFile && topLevel)) {
            diag.report(TemplateCode.MISPLACED_ELEMENT, openPos, "<template> only appears at the top level of a component file");
            known = false;
        } else if (tag.equals("slot") && templateDepth == 0) {
            diag.report(TemplateCode.MISPLACED_ELEMENT, openPos, "<slot> only appears inside a component's <template>");
            known = false;
        } else if (tag.equals("slot") && forInTemplateDepth > 0) {
            diag.report(TemplateCode.SLOT_IN_FOR, openPos, "<slot> cannot be inside <for>: each slot is filled once");
        }
        Element element = new Element(tag, openPos);
        Set<String> seen = new HashSet<>();
        List<Directive> messages = new ArrayList<>();
        boolean selfClosing = false;
        while (true) {
            skipWhitespace();
            if (i >= src.length()) {
                diag.report(TemplateCode.SYNTAX, openPos, "unterminated start tag <" + tag + ">");
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
                diag.report(TemplateCode.SYNTAX, pos(i), "unexpected '" + c + "' in start tag <" + tag + ">");
                i++;
                continue;
            }
            parseAttribute(element, known, reserved, component, seen, messages);
        }
        if (known && reserved) {
            checkReservedAttributes(element);
        }
        if (known && tag.equals("img") && element.attribute("src") == null
                && element.directives().stream().noneMatch(b -> b.name().equals("bind:src"))) {
            diag.report(TemplateCode.IMG_WITHOUT_SOURCE, openPos, "<img> requires a 'src' attribute or a 'bind:src' binding");
        }
        if (known && tag.equals("input") && FormControl.Kind.ofInputType(element.attribute("type")) == null) {
            diag.report(TemplateCode.UNSUPPORTED_INPUT_TYPE, openPos, "unsupported <input type=\"" + element.attribute("type")
                    + "\">; supported: text, number, checkbox, range");
        }
        String translate = element.attribute("translate");
        if (translate != null && !translate.equals("yes") && !translate.equals("no")) {
            diag.report(TemplateCode.UNKNOWN_ATTRIBUTE, openPos, "translate takes \"yes\" or \"no\", not \"" + translate + "\"");
        }
        boolean ownUntranslated = translate == null ? untranslated : translate.equals("no");
        Directive message = null;
        for (Directive d : messages) {
            if (isStyle || isLink) {
                diag.report(TemplateCode.UNKNOWN_ATTRIBUTE, d.namePos(), "<" + tag + "> takes no translations");
            } else if (d.name().equals("t")) {
                message = d;
            } else {
                translateAttribute(element, d, component);
            }
        }
        if (known && !ownUntranslated) {
            for (String a : TRANSLATABLE) {
                String v = element.attribute(a);
                if (v != null && hasLetter(v) && messages.stream().noneMatch(d -> d.name().equals("t:" + a))) {
                    diag.report(TemplateCode.LITERAL_TEXT, openPos, a + " '" + v + "' is not translated",
                            "write t:" + a + "=\"key\" instead, or mark the element translate=\"no\"");
                }
            }
        }

        if (isStyle) {
            if (!selfClosing) {
                parseStyleContent(openPos);
            }
            return null;
        }
        if (isLink) {
            if (!selfClosing) {
                diag.report(TemplateCode.INVALID_LINK, openPos, "<link> must be self-closing: <link rel=\"stylesheet\" href=\"…\"/>");
            }
            String rel = element.attribute("rel");
            boolean valid = topLevel;
            if (!"stylesheet".equals(rel) && !"component".equals(rel)) {
                diag.report(TemplateCode.INVALID_LINK, openPos, "<link> requires rel=\"stylesheet\" or rel=\"component\"");
                valid = false;
            }
            String href = element.attribute("href");
            if (href == null || href.isEmpty()) {
                diag.report(TemplateCode.INVALID_LINK, openPos, "<link> requires an 'href' attribute");
                valid = false;
            }
            if (valid && "component".equals(rel)) {
                if (sawElement) {
                    diag.report(TemplateCode.LINK_AFTER_ELEMENT, openPos, "<link rel=\"component\"> must come before the first element");
                } else if (linker == null) {
                    diag.report(TemplateCode.COMPONENT_LINK_UNAVAILABLE, openPos, "components can only be linked from a loaded document");
                } else {
                    ComponentDef def = linker.link(href, openPos);
                    if (def != null) {
                        components.put(def.name(), def);
                    }
                }
            } else if (valid) {
                styles.add(new Template.Link(href, openPos));
            }
            return null;
        }
        boolean atPointer = tag.equals(TopLayer.TOOLTIP) || tag.equals(TopLayer.DRAG_PREVIEW);
        if (known && atPointer && (topLevel || tooltipDepth > 0)) {
            String what = tag.equals(TopLayer.TOOLTIP) ? "the tooltip" : "the drag preview";
            diag.report(TemplateCode.MISPLACED_ELEMENT, openPos, topLevel
                    ? "<" + tag + "> is " + what + " of its parent element; it cannot be at the top level"
                    : "<" + tag + "> cannot be inside an <ix-tooltip> or <ix-drag-preview>");
        }
        if (!selfClosing) {
            List<Node> children = new ArrayList<>();
            boolean inTemplate = tag.equals("template");
            boolean forInTemplate = tag.equals("for") && templateDepth > 0;
            boolean inTooltip = atPointer;
            templateDepth += inTemplate ? 1 : 0;
            forInTemplateDepth += forInTemplate ? 1 : 0;
            tooltipDepth += inTooltip ? 1 : 0;
            boolean outerUntranslated = untranslated;
            untranslated = ownUntranslated;
            parseNodes(children, false, false);
            untranslated = outerUntranslated;
            templateDepth -= inTemplate ? 1 : 0;
            forInTemplateDepth -= forInTemplate ? 1 : 0;
            tooltipDepth -= inTooltip ? 1 : 0;
            if (i >= src.length()) {
                diag.report(TemplateCode.UNCLOSED_TAG, openPos, "<" + tag + "> is never closed");
            } else {
                SourcePos closePos = pos(i);
                String closing = readClosingTag();
                if (!closing.equals(tag)) {
                    diag.report(TemplateCode.MISMATCHED_CLOSING_TAG, closePos, "closing tag </" + closing + "> does not match <" + tag
                            + "> opened at " + openPos);
                }
            }
            Node notTooltip = known && (tag.equals("img") || tag.equals("input")) ? firstNonTooltip(children) : null;
            if (notTooltip != null) {
                diag.report(TemplateCode.INVALID_CHILDREN, notTooltip.pos(), "<" + tag + "> may only contain an <ix-tooltip>");
            } else if (known && tags.hasLineContent(tag) && !children.stream().allMatch(TemplateParser::isBlank)) {
                diag.report(TemplateCode.INVALID_CHILDREN, openPos, "<" + tag + "> must not have children: its lines are filled in");
            } else if (known && tag.equals("select") && !onlyOptions(children, true)) {
                diag.report(TemplateCode.INVALID_CHILDREN, openPos, "<select> may only contain <option> elements, directly or in <for> and <if>, and an <ix-tooltip>");
            } else if (known && tag.equals("option") && children.stream().anyMatch(n -> !(n instanceof TextNode))) {
                diag.report(TemplateCode.INVALID_CHILDREN, openPos, "<option> may only contain text");
            } else {
                checkSlotPlacement(children, component);
                for (Node child : children) {
                    if (!(known && (tag.equals("img") || tag.equals("input") || tags.isReplaced(tag) || tags.hasLineContent(tag))
                            && isBlank(child))) {
                        element.appendChild(child);
                    }
                }
            }
        }
        if (known) {
            checkMarkPlacement(element.children(), message != null);
            if (message != null) {
                expandMessage(element, message, component);
            }
        }
        return known ? element : null;
    }

    private static boolean isBlank(Node n) {
        return n instanceof TextNode t && t.text().isBlank();
    }

    private static Node firstNonTooltip(List<Node> children) {
        for (Node n : children) {
            if (!isBlank(n) && !(n instanceof Element c && c.tag().equals(TopLayer.TOOLTIP))) {
                return n;
            }
        }
        return null;
    }

    /** Options, directly or in blocks; {@code direct}: also the select's own {@code <ix-tooltip>}. */
    private static boolean onlyOptions(List<Node> children, boolean direct) {
        for (Node n : children) {
            if (isBlank(n)) {
                continue;
            }
            if (!(n instanceof Element c) || !(c.tag().equals("option") || direct && c.tag().equals(TopLayer.TOOLTIP)
                    || ((c.tag().equals("for") || c.tag().equals("if")) && onlyOptions(c.children(), false)))) {
                return false;
            }
        }
        return true;
    }

    /** {@code slot=} only goes on a direct child, element or block, of a component's element. */
    private void checkSlotPlacement(List<Node> children, ComponentDef parent) {
        if (parent != null) {
            return;
        }
        for (Node n : children) {
            if (n instanceof Element c && slotOf(c) != null) {
                diag.report(TemplateCode.MISPLACED_SLOT_ATTRIBUTE, c.pos(), "slot=\"" + slotOf(c) + "\" only goes on a direct child of a component's element");
            }
        }
    }

    /** The {@code slot} an element or block fills, or {@code null}. */
    public static String slotOf(Element e) {
        if (e.tag().equals("for") || e.tag().equals("if")) {
            for (Directive d : e.directives()) {
                if (d.name().equals("slot")) {
                    return d.value();
                }
            }
            return null;
        }
        return e.attribute("slot");
    }

    private void checkReservedAttributes(Element e) {
        List<String> allowed = switch (e.tag()) {
            case "for" -> List.of("each", "as", "key", "index", "slot");
            case "if" -> List.of("test", "slot");
            case "slot" -> List.of("name");
            default -> List.of("name", "params", "events");
        };
        List<String> required = switch (e.tag()) {
            case "for" -> List.of("each", "as", "key");
            case "if" -> List.of("test");
            case "slot" -> List.of();
            default -> List.of("name");
        };
        Set<String> present = new HashSet<>();
        for (Directive d : e.directives()) {
            present.add(d.name());
            if (!allowed.contains(d.name())) {
                diag.report(TemplateCode.UNKNOWN_BLOCK_ATTRIBUTE, d.namePos(), "<" + e.tag() + "> takes only " + String.join(", ", allowed));
            }
        }
        for (String r : required) {
            if (!present.contains(r)) {
                diag.report(TemplateCode.MISSING_BLOCK_ATTRIBUTE, e.pos(), "<" + e.tag() + "> requires '" + r + "'");
            }
        }
    }

    /** {@code mark=} only goes on a direct child of an element with {@code t}. */
    private void checkMarkPlacement(List<Node> children, boolean message) {
        if (message) {
            return;
        }
        for (Node n : children) {
            if (n instanceof Element c && c.attribute("mark") != null) {
                diag.report(TemplateCode.MISPLACED_MARK, c.pos(), "mark=\"" + c.attribute("mark")
                        + "\" only goes on a direct child of an element with t=\"…\"");
            }
        }
    }

    /** Sets the attribute {@code t:<attribute>} names to its translation. */
    private void translateAttribute(Element element, Directive d, ComponentDef component) {
        String attr = d.name().substring(2);
        boolean param = component != null && component.hasParam(attr);
        if (!param && !(TRANSLATABLE.contains(attr) && (component != null || tags.isAllowedAttribute(element.tag(), attr)))) {
            diag.report(TemplateCode.INVALID_TRANSLATED_ATTRIBUTE, d.namePos(), "<" + element.tag() + "> has no translatable attribute '"
                    + attr + "'; t: translates " + String.join(", ", TRANSLATABLE.stream().sorted().toList()) + " and component parameters");
            return;
        }
        if (element.attribute(attr) != null || element.directives().stream().anyMatch(b -> b.name().equals("bind:" + attr))) {
            diag.report(TemplateCode.INVALID_TRANSLATED_ATTRIBUTE, d.namePos(), "'" + attr + "' is given twice: by " + d.name() + " and directly");
            return;
        }
        Message.Parsed m = translation(d, Set.of(), 0);
        StringBuilder text = new StringBuilder();
        if (m != null) {
            for (Message.Part p : m.parts()) {
                if (p instanceof Message.Text t) {
                    text.append(t.text().replace(TextShaper.LINE_BREAK, '\n'));
                }
            }
        }
        element.setAttribute(attr, m != null ? text.toString() : d.value());
    }

    /** Replaces the children of the element with {@code t} by its translation; see {@link Translations}. */
    private void expandMessage(Element element, Directive d, ComponentDef component) {
        String tag = element.tag();
        String host = component != null ? "a component's element"
                : tag.equals("img") || tag.equals("input") || tag.equals("select") || tags.isReplaced(tag) || tags.hasLineContent(tag)
                ? "<" + tag + ">" : null;
        if (host != null) {
            diag.report(TemplateCode.INVALID_MESSAGE_ELEMENT, d.namePos(), "t=\"…\" cannot go on " + host + ": its content is not its own");
            return;
        }
        if (element.directives().stream().anyMatch(b -> b.name().equals("bind:text"))) {
            diag.report(TemplateCode.INVALID_MESSAGE_ELEMENT, d.namePos(), "t=\"…\" and bind:text both set the element's text");
            return;
        }
        List<Element> args = new ArrayList<>();
        Map<String, Element> marks = new LinkedHashMap<>();
        List<Element> tooltips = new ArrayList<>();
        boolean valid = true;
        for (Node n : element.children()) {
            if (n instanceof TextNode t) {
                if (!t.text().isBlank()) {
                    diag.report(TemplateCode.INVALID_MESSAGE_CHILD, t.pos(), "text inside an element with t=\"…\" belongs in the translation");
                    valid = false;
                }
                continue;
            }
            Element c = (Element) n;
            String mark = c.attribute("mark");
            if (c.tag().equals(TopLayer.TOOLTIP)) {
                tooltips.add(c);
            } else if (c.tag().equals("for") || c.tag().equals("if") || c.tag().equals("slot")) {
                diag.report(TemplateCode.INVALID_MESSAGE_CHILD, c.pos(), "<" + c.tag() + "> makes a varying number of elements;"
                        + " wrap it in one element to use it as an argument or mark");
                valid = false;
            } else if (mark == null) {
                args.add(c);
            } else if (!c.children().isEmpty()) {
                diag.report(TemplateCode.INVALID_MESSAGE_CHILD, c.pos(), "mark '" + mark + "' must be empty: the translation gives its text");
                valid = false;
            } else if (marks.putIfAbsent(mark, c) != null) {
                diag.report(TemplateCode.INVALID_MESSAGE_CHILD, c.pos(), "mark '" + mark + "' is declared twice");
                valid = false;
            }
        }
        if (!valid) {
            // the arguments and marks are not what the author meant, so neither are the translation's problems
            if (translations.get(d.value()) == null) {
                translation(d, Set.of(), 0);
            }
            return;
        }
        Message.Parsed m = translation(d, marks.keySet(), args.size());
        if (m == null) {
            return;
        }
        for (Node n : new ArrayList<>(element.children())) {
            element.removeChild(n);
        }
        boolean[] placed = new boolean[args.size()];
        for (Message.Part p : m.parts()) {
            if (p instanceof Message.Text t) {
                element.appendChild(new TextNode(t.text(), d.valuePos()));
            } else if (p instanceof Message.Arg a) {
                Element arg = args.get(a.index());
                element.appendChild(placed[a.index()] ? arg.copyTemplate() : arg);
                placed[a.index()] = true;
            } else if (p instanceof Message.Mark k) {
                Element copy = marks.get(k.name()).copyTemplate();
                if (!k.text().isEmpty()) {
                    copy.appendChild(new TextNode(k.text(), d.valuePos()));
                }
                element.appendChild(copy);
            }
        }
        for (Element tip : tooltips) {
            element.appendChild(tip);
        }
    }

    /** The translation {@code d} names, its problems reported; {@code null} once a missing key is reported. */
    private Message.Parsed translation(Directive d, Set<String> marks, int args) {
        String raw = translations.get(d.value());
        if (raw == null) {
            diag.report(TemplateCode.MISSING_TRANSLATION, d.valuePos(), "no translation of '" + d.value() + "'",
                    "add it to the language files, the base language first");
            return null;
        }
        Message.Parsed m = Message.parse(raw, marks, args, translations.base());
        String of = "translation of '" + d.value() + "'"
                + (translations.language() == null ? "" : " (" + translations.language() + ")") + ": ";
        for (String p : m.problems()) {
            diag.report(translations.base() ? TemplateCode.INVALID_TRANSLATION : TemplateCode.LOCALE_TRANSLATION, d.valuePos(), of + p);
        }
        for (String u : m.undeclared()) {
            diag.report(TemplateCode.UNDECLARED_MARK, d.valuePos(), of + u + " names no mark of the element and is shown as text");
        }
        return m;
    }

    private static boolean hasLetter(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }

    /** Checks a component file's {@code <template>} and describes its component. */
    private ComponentDef define(Element template) {
        String name = "";
        Directive params = null;
        Directive events = null;
        for (Directive d : template.directives()) {
            switch (d.name()) {
                case "name" -> name = d.value();
                case "params" -> params = d;
                case "events" -> events = d;
                default -> {
                }
            }
        }
        if (!name.contains("-") || name.startsWith("ix-") || name.startsWith("mc-") || !isTagName(name)) {
            diag.report(TemplateCode.INVALID_COMPONENT_NAME, template.pos(), "component name '" + name
                    + "' must be a tag name with a '-' that does not start with 'ix-' or 'mc-'");
        } else if (tags.isKnownTag(name)) {
            diag.report(TemplateCode.INVALID_COMPONENT_NAME, template.pos(), "component name '" + name + "' is a registered tag");
        }
        Set<String> names = new HashSet<>();
        List<ComponentDef.Param> paramList = new ArrayList<>();
        if (params != null) {
            for (Word w : words(params)) {
                int eq = w.text.indexOf('=');
                String p = eq < 0 ? w.text : w.text.substring(0, eq);
                if (checkMemberName(p, w.pos, "parameter", names)) {
                    Directive fallback = eq < 0 ? null
                            : new Directive(p, w.text.substring(eq + 1), w.pos, advance(w.pos, w.text, eq + 1));
                    paramList.add(new ComponentDef.Param(p, w.pos, fallback));
                }
            }
        }
        List<String> eventList = new ArrayList<>();
        if (events != null) {
            for (Word w : words(events)) {
                if (checkMemberName(w.text, w.pos, "event", names)) {
                    if (UiEvent.Type.byTemplateName(w.text) != null) {
                        diag.report(TemplateCode.DOM_EVENT_NAME, w.pos, "event '" + w.text + "' has the name of a DOM event");
                    } else {
                        eventList.add(w.text);
                    }
                }
            }
        }
        Set<String> slots = new LinkedHashSet<>();
        collectSlots(template.children(), slots);
        return new ComponentDef(name, template.pos(), paramList, eventList, template.children(), slots);
    }

    private void collectSlots(List<Node> nodes, Set<String> slots) {
        for (Node n : nodes) {
            if (n instanceof Element e) {
                if (e.tag().equals("slot")) {
                    String slot = "";
                    for (Directive d : e.directives()) {
                        slot = d.name().equals("name") ? d.value() : slot;
                    }
                    if (!slots.add(slot)) {
                        diag.report(TemplateCode.DUPLICATE_SLOT, e.pos(), slot.isEmpty() ? "the default <slot> appears twice"
                                : "<slot name=\"" + slot + "\"> appears twice");
                    }
                }
                collectSlots(e.children(), slots);
            }
        }
    }

    private boolean checkMemberName(String name, SourcePos at, String what, Set<String> names) {
        boolean ok = !name.isEmpty() && isAsciiLetter(name.charAt(0));
        for (int k = 1; k < name.length() && ok; k++) {
            ok = isAsciiLetter(name.charAt(k)) || isDigit(name.charAt(k));
        }
        if (!ok) {
            diag.report(TemplateCode.INVALID_MEMBER_NAME, at, what + " name '" + name + "' must be a letter followed by letters or digits");
        } else if (SHELL_NAMES.contains(name)) {
            diag.report(TemplateCode.RESERVED_MEMBER_NAME, at, what + " name '" + name + "' is taken by the component's element");
        } else if (!names.add(name)) {
            diag.report(TemplateCode.DUPLICATE_MEMBER, at, "'" + name + "' is declared twice");
        } else {
            return true;
        }
        return false;
    }

    private record Word(String text, SourcePos pos) {
    }

    /** Splits a directive's value at whitespace outside quotes, keeping each word's position. */
    private static List<Word> words(Directive d) {
        List<Word> out = new ArrayList<>();
        String v = d.value();
        int k = 0;
        while (k < v.length()) {
            if (isSpace(v.charAt(k))) {
                k++;
                continue;
            }
            int start = k;
            char quote = 0;
            while (k < v.length() && (quote != 0 || !isSpace(v.charAt(k)))) {
                char c = v.charAt(k);
                quote = quote == 0 && (c == '\'' || c == '"') ? c : (c == quote ? 0 : quote);
                k++;
            }
            out.add(new Word(v.substring(start, k), advance(d.valuePos(), v, start)));
        }
        return out;
    }

    /** The position {@code offset} characters into {@code text}, which starts at {@code start}. */
    private static SourcePos advance(SourcePos start, String text, int offset) {
        int line = start.line();
        int column = start.column();
        for (int k = 0; k < offset; k++) {
            if (text.charAt(k) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SourcePos(start.file(), line, column);
    }

    private static boolean isTagName(String s) {
        if (s.isEmpty() || !isAsciiLetter(s.charAt(0))) {
            return false;
        }
        for (int k = 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (!isAsciiLetter(c) && !isDigit(c) && c != '-') {
                return false;
            }
        }
        return true;
    }

    /** {@code messages} receives {@code t} and {@code t:<attribute>}, resolved once the element's attributes are all read. */
    private void parseAttribute(Element element, boolean tagKnown, boolean reserved, ComponentDef component,
                                Set<String> seen, List<Directive> messages) {
        String tag = element.tag();
        SourcePos namePos = pos(i);
        String name = readName(true);
        skipWhitespace();
        if (i >= src.length() || src.charAt(i) != '=') {
            diag.report(TemplateCode.UNQUOTED_ATTRIBUTE, namePos, "attribute '" + name + "' needs a quoted value");
            return;
        }
        i++;
        skipWhitespace();
        if (i >= src.length() || (src.charAt(i) != '"' && src.charAt(i) != '\'')) {
            diag.report(TemplateCode.UNQUOTED_ATTRIBUTE, namePos, "value of attribute '" + name + "' must be quoted");
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
            diag.report(TemplateCode.SYNTAX, namePos, "unterminated value of attribute '" + name + "'");
            i = src.length();
            return;
        }
        i = end + 1;
        if (!seen.add(name)) {
            diag.report(TemplateCode.DUPLICATE_ATTRIBUTE, namePos, "duplicate attribute '" + name + "' on <" + tag + ">");
            return;
        }
        if (!tagKnown) {
            return;
        }
        if (reserved || isDirective(name)) {
            if (tag.equals("style") || tag.equals("link")) {
                diag.report(TemplateCode.UNKNOWN_ATTRIBUTE, namePos, "<" + tag + "> takes no " + (tag.equals("style") ? "attributes" : "bindings"));
            } else {
                element.addDirective(new Directive(name, decode(valueStart, end, false), namePos, pos(valueStart)));
            }
            return;
        }
        if (MOVED.contains(name)) {
            diag.report(TemplateCode.MOVED_ATTRIBUTE, namePos, name.equals("if")
                    ? "'if' is a block now: wrap the element in <if test=\"…\">"
                    : "'" + name + "' is a block attribute now: wrap the element in <for each=\"…\" as=\"…\" key=\"…\">");
            return;
        }
        if (isTranslation(name)) {
            messages.add(new Directive(name, decode(valueStart, end, false), namePos, pos(valueStart)));
            return;
        }
        boolean allowed = name.equals("slot") && !tag.equals("style") && !tag.equals("link")
                || (component != null ? TagRegistry.GLOBAL_ATTRIBUTES.contains(name) || component.hasParam(name)
                : tags.isAllowedAttribute(tag, name));
        if (!allowed) {
            diag.report(TemplateCode.UNKNOWN_ATTRIBUTE, namePos, "unknown attribute '" + name + "' on <" + tag + ">");
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

    /** Attributes interpreted by the binding layer: {@code bind:*}, {@code on:*}, {@code state:*}. */
    static boolean isDirective(String attribute) {
        return attribute.startsWith("bind:") || attribute.startsWith("on:") || attribute.startsWith("state:");
    }

    /** {@code t} and {@code t:<attribute>}: the parser translates them, so no element may register them. */
    static boolean isTranslation(String attribute) {
        return attribute.equals("t") || attribute.startsWith("t:");
    }

    private void parseStyleContent(SourcePos openPos) {
        int start = i;
        int end = src.indexOf("</style", start);
        if (end < 0) {
            diag.report(TemplateCode.UNCLOSED_TAG, openPos, "<style> is never closed");
            i = src.length();
            return;
        }
        styles.add(new Template.Inline(src.substring(start, end), pos(start)));
        i = end;
        SourcePos closePos = pos(i);
        String closing = readClosingTag();
        if (!closing.equals("style")) {
            diag.report(TemplateCode.MISMATCHED_CLOSING_TAG, closePos, "closing tag </" + closing + "> does not match <style> opened at " + openPos);
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
            diag.report(TemplateCode.SYNTAX, pos(i), "expected '>' to end closing tag </" + name + ">");
        }
        return name;
    }

    /**
     * Tag or attribute name. Attribute names may continue with {@code :} before a letter ({@code bind:text})
     * or {@code --} ({@code bind:style:--accent}), and {@code .} before a letter or digit
     * ({@code on:keydown.f1}, the key filters of {@code on:*}).
     */
    private String readName(boolean attribute) {
        int start = i;
        if (i < src.length() && isAsciiLetter(src.charAt(i))) {
            i++;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (isAsciiLetter(c) || isDigit(c) || c == '-') {
                    i++;
                } else if (attribute && c == ':' && i + 1 < src.length() && (isAsciiLetter(src.charAt(i + 1))
                        || src.startsWith("--", i + 1))) {
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
            diag.report(TemplateCode.SYNTAX, pos(i), "expected a name");
        }
        return src.substring(start, i);
    }

    /**
     * Text with whitespace runs collapsed to one space, as {@code white-space: normal} renders it; the
     * spaces at a line's start and end are dropped by layout, not here, so {@code a <b>b</b>} keeps its
     * space. Whitespace-only text is {@code " "} and kept only between two siblings; see {@link #parseNodes}.
     */
    private TextNode text(int start, int end) {
        return new TextNode(decode(start, end, true), pos(start));
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
                    diag.report(TemplateCode.INVALID_CHARACTER_REFERENCE, pos(k), "invalid character reference; write &amp; for a literal '&'");
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
