package com.azane.ixchel.core.view;

import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.library.Library;
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
 * Strict view parser: no HTML5 error recovery, never closes elements implicitly. Every problem is
 * reported to {@link Diagnostics}; parsing continues past errors so one load reports as many as it can.
 *
 * <p>A view file is {@code <link href="…"/>}s (self-closing, nothing but {@code href}), then one root
 * element, {@code <page>} or {@code <component name params events uses>}, which declares its kind; only
 * comments and whitespace may be between them and after the root. The links name what the view uses:
 * one {@code .css} file, its stylesheet ({@link ParsedView#stylesheet()}), and any number of component
 * files, whose components become usable as tags. The root is a definition, not part of the tree: a
 * page's children are its top-level elements, a component's children its body. What other files a view
 * names and whether a linked file is of the kind its extension says are the {@link Links}' to check.
 *
 * <p>The reserved tags {@code <for each as key>}, {@code <if test>}, {@code <else>}, {@code <empty>},
 * {@code <slot name>} and the root keep their attributes as {@linkplain Element#directives() directives};
 * the binding layer interprets the blocks. An {@code <else>} directly follows an {@code <if>}, an
 * {@code <empty>} a {@code <for>}, with only whitespace between: the pair is one block for slots and
 * translations, and an orphan is dropped.
 *
 * <p>Translations are resolved here, so the binding layer compiles the translated tree: {@code t="key"}
 * replaces an element's children with its translation, laying out the argument children and copies of
 * the {@code mark} children where it refers to them, and {@code t:<attribute>="key"} sets the
 * attribute to its translation (see {@link Translations}).
 */
public final class ViewParser {
    /** Resource directives are view syntax, not runtime elements; also used by the schema. */
    public static final Map<String, Set<String>> RESOURCE_TAGS = Map.of("link", Set.of("href"));
    private static final String CSS = ".css";
    /** Element attributes replaced by the {@code <for>} and {@code <if>} blocks. */
    private static final Set<String> MOVED = Set.of("each", "as", "key", "if");
    /** Names a parameter or event cannot take: they are attributes or targets of the component's element. */
    private static final Set<String> SHELL_NAMES = Set.of("class", "id", "style", "tabindex", "disabled", "title",
            "lang", "translate", "mark", "draggable", "part", "text", "scrolltop", "slot", "t");
    /** The attributes {@code t:<attribute>} translates, besides a component's parameters. */
    private static final Set<String> TRANSLATABLE = Set.of("title", "placeholder");

    /** Where nodes being parsed sit. */
    private enum Level {
        /** Outside the root: the links and the root. */
        DOCUMENT,
        /** Directly in a {@code <page>}: its top-level elements. */
        PAGE,
        /** Anywhere else, a component's body included. */
        NESTED
    }

    /** Loads the files a view links, other than its stylesheet. */
    public interface Links {
        /** The component of the file {@code href} names, or {@code null} once a failure is reported. */
        ComponentDef link(String href, SourcePos at);
    }

    private final String src;
    private final String file;
    private final TagRegistry tags;
    private final Links linker;
    private final Diagnostics diag;
    private final Translations translations;
    private final int[] lineStarts;
    private ParsedView.Link stylesheet;
    private final Map<String, ComponentDef> components = new HashMap<>();
    private boolean sawElement;
    private int componentDepth;
    private int forInComponentDepth;
    private int tooltipDepth;
    /** Inside {@code translate="no"}: literal text is meant. */
    private boolean untranslated;
    private int i;

    private ViewParser(String source, String file, TagRegistry tags, Links linker, Translations translations,
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

    /** Parses a view that links no components and translates nothing. */
    public static ParsedView parse(String source, String file, TagRegistry tags, Diagnostics diag) {
        return parse(source, file, tags, null, Translations.NONE, diag);
    }

    /**
     * @param linker loads linked components; {@code null} when the source may not link any
     */
    public static ParsedView parse(String source, String file, TagRegistry tags, Links linker, Translations translations,
                                   Diagnostics diag) {
        ViewParser p = new ViewParser(source, file, tags, linker, translations, diag);
        List<Node> document = new ArrayList<>();
        while (true) {
            p.parseNodes(document, Level.DOCUMENT);
            if (p.i >= p.src.length()) {
                break;
            }
            SourcePos at = p.pos(p.i);
            String name = p.readClosingTag();
            p.diag.report(ViewCode.MISMATCHED_CLOSING_TAG, at, "closing tag </" + name + "> has no matching opening tag");
        }
        Element root = null;
        boolean reported = false;
        for (Node n : document) {
            reported = true;
            if (n instanceof TextNode) {
                diag.report(ViewCode.MISSING_ROOT, n.pos(), "only comments and whitespace may surround the root <page> or <component>");
            } else if (!isRoot(((Element) n).tag())) {
                diag.report(ViewCode.MISSING_ROOT, n.pos(), "the root element must be <page> or <component>, not <"
                        + ((Element) n).tag() + ">");
            } else if (root == null) {
                root = (Element) n;
                reported = false;
            } else {
                diag.report(ViewCode.MISSING_ROOT, n.pos(), "a view has one root element; <" + root.tag() + "> is at " + root.pos());
            }
        }
        if (root == null) {
            if (!reported) {
                diag.report(ViewCode.MISSING_ROOT, new SourcePos(file, 1, 1), "a view needs a root <page> or <component name=\"…\">");
            }
            return new ParsedView(null, List.of(), p.stylesheet, null);
        }
        List<Node> children = new ArrayList<>(root.children());
        if (root.tag().equals("component")) {
            return new ParsedView(ParsedView.Kind.COMPONENT, List.of(), p.stylesheet, p.define(root));
        }
        for (Node n : children) {
            root.removeChild(n);
        }
        return new ParsedView(ParsedView.Kind.PAGE, children, p.stylesheet, null);
    }

    /** Parses nodes into {@code out} until EOF or a closing tag, which is left unconsumed. */
    private void parseNodes(List<Node> out, Level level) {
        int from = out.size();
        parseNodesRaw(out, level);
        // Whitespace-only text at the start or end of an element, or between top-level nodes, never renders.
        boolean all = level == Level.DOCUMENT || level == Level.PAGE;
        for (int k = out.size() - 1; k >= from; k--) {
            if (out.get(k) instanceof TextNode t && t.text().isBlank() && (all || k == from || k == out.size() - 1)) {
                out.remove(k);
            }
        }
    }

    private void parseNodesRaw(List<Node> out, Level level) {
        while (i < src.length()) {
            if (src.charAt(i) != '<') {
                int start = i;
                while (i < src.length() && src.charAt(i) != '<') {
                    i++;
                }
                TextNode text = text(start, i);
                if (level != Level.DOCUMENT && !untranslated && hasLetter(text.text())) {
                    diag.report(ViewCode.LITERAL_TEXT, text.pos(), "text '" + text.text().strip() + "' is not translated",
                            "move it to the language files and write t=\"key\" on the element, or mark it translate=\"no\"");
                }
                out.add(text);
            } else if (src.startsWith("</", i)) {
                return;
            } else if (src.startsWith("<!--", i)) {
                int end = src.indexOf("-->", i + 4);
                if (end < 0) {
                    diag.report(ViewCode.SYNTAX, pos(i), "unterminated comment");
                    i = src.length();
                } else {
                    i = end + 3;
                }
            } else if (i + 1 < src.length() && isAsciiLetter(src.charAt(i + 1))) {
                Element e = parseElement(level);
                if (e != null) {
                    out.add(e);
                }
            } else {
                diag.report(ViewCode.UNSUPPORTED_MARKUP, pos(i), src.startsWith("<!", i) || src.startsWith("<?", i)
                        ? "doctype, CDATA and processing instructions are not supported"
                        : "stray '<'; write &lt; for a literal '<'");
                i++;
            }
        }
    }

    /** Returns null for elements that must not appear in the tree (unknown, misplaced, {@code <link>}). */
    private Element parseElement(Level level) {
        int open = i;
        SourcePos openPos = pos(open);
        i++;
        String tag = readName(false);
        ComponentDef component = components.get(tag);
        boolean root = isRoot(tag);
        boolean block = isBlock(tag);
        boolean reserved = block || root || tag.equals("slot");
        boolean isLink = tag.equals("link");
        boolean topLevel = level == Level.PAGE;
        boolean linkable = level == Level.DOCUMENT;
        boolean known = reserved || isLink || component != null || tags.isKnownTag(tag);
        if (!known) {
            diag.report(ViewCode.UNKNOWN_TAG, openPos, "unknown tag <" + tag + ">");
        }
        if (isLink && !linkable) {
            diag.report(ViewCode.MISPLACED_ELEMENT, openPos, "<link> only appears at the top of the file, before the root <page> or <component>");
        }
        if (!isLink && level == Level.DOCUMENT) {
            sawElement = true;
        }
        if (root && level != Level.DOCUMENT) {
            diag.report(ViewCode.MISPLACED_ELEMENT, openPos, "<" + tag + "> is a view's root and appears nowhere else");
            known = false;
        } else if (tag.equals("slot") && componentDepth == 0) {
            diag.report(ViewCode.MISPLACED_ELEMENT, openPos, "<slot> only appears inside a <component>");
            known = false;
        } else if (tag.equals("slot") && forInComponentDepth > 0) {
            diag.report(ViewCode.SLOT_IN_FOR, openPos, "<slot> cannot be inside <for>: each slot is filled once");
        }
        ElementType registered = tags.type(tag);
        ElementType type = registered != null ? registered : ElementType.plain(tag);
        ContentModel content = type.facet(ContentModel.KEY);
        Element element = new Element(type, openPos);
        Set<String> seen = new HashSet<>();
        List<Directive> messages = new ArrayList<>();
        boolean selfClosing = false;
        while (true) {
            skipWhitespace();
            if (i >= src.length()) {
                diag.report(ViewCode.SYNTAX, openPos, "unterminated start tag <" + tag + ">");
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
                diag.report(ViewCode.SYNTAX, pos(i), "unexpected '" + c + "' in start tag <" + tag + ">");
                i++;
                continue;
            }
            parseAttribute(element, known, reserved, component, seen, messages);
        }
        if (known && reserved) {
            checkReservedAttributes(element);
        }
        if (known) content.attributes(element, diag);
        String translate = element.attribute("translate");
        if (translate != null && !translate.equals("yes") && !translate.equals("no")) {
            diag.report(ViewCode.UNKNOWN_ATTRIBUTE, openPos, "translate takes \"yes\" or \"no\", not \"" + translate + "\"");
        }
        boolean ownUntranslated = translate == null ? untranslated : translate.equals("no");
        Directive message = null;
        for (Directive d : messages) {
            if (isLink || root) {
                diag.report(ViewCode.UNKNOWN_ATTRIBUTE, d.namePos(), "<" + tag + "> takes no translations");
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
                    diag.report(ViewCode.LITERAL_TEXT, openPos, a + " '" + v + "' is not translated",
                            "write t:" + a + "=\"key\" instead, or mark the element translate=\"no\"");
                }
            }
        }

        if (isLink) {
            if (!selfClosing) {
                diag.report(ViewCode.INVALID_LINK, openPos, "<link> must be self-closing: <link href=\"…\"/>");
            }
            if (linkable) {
                link(element.attribute("href"), openPos);
            }
        }
        boolean atPointer = known && content.pointerBoundary();
        if (known && !root && !isLink) content.placement(element, topLevel, tooltipDepth, diag);
        if (!selfClosing) {
            List<Node> children = new ArrayList<>();
            boolean inComponent = tag.equals("component");
            boolean forInComponent = tag.equals("for") && componentDepth > 0;
            boolean inTooltip = atPointer;
            componentDepth += inComponent ? 1 : 0;
            forInComponentDepth += forInComponent ? 1 : 0;
            tooltipDepth += inTooltip ? 1 : 0;
            boolean outerUntranslated = untranslated;
            untranslated = ownUntranslated;
            Level inner = level == Level.DOCUMENT && root && !inComponent ? Level.PAGE : Level.NESTED;
            parseNodes(children, inner);
            untranslated = outerUntranslated;
            componentDepth -= inComponent ? 1 : 0;
            forInComponentDepth -= forInComponent ? 1 : 0;
            tooltipDepth -= inTooltip ? 1 : 0;
            if (i >= src.length()) {
                diag.report(ViewCode.UNCLOSED_TAG, openPos, "<" + tag + "> is never closed");
            } else {
                SourcePos closePos = pos(i);
                String closing = readClosingTag();
                if (!closing.equals(tag)) {
                    diag.report(ViewCode.MISMATCHED_CLOSING_TAG, closePos, "closing tag </" + closing + "> does not match <" + tag
                            + "> opened at " + openPos);
                }
            }
            checkPartners(children);
            if (content.children(element, children, diag)) {
                checkSlotPlacement(children, component);
                for (Node child : children) {
                    if (content.preserveWhitespace() || !isBlank(child)) {
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
        return known && !isLink ? element : null;
    }

    private static boolean isRoot(String tag) {
        return tag.equals("page") || tag.equals("component");
    }

    /** A {@code <link href>} above the root: the stylesheet, or a file the {@link Links} load. */
    private void link(String href, SourcePos at) {
        if (href == null || href.isEmpty()) {
            diag.report(ViewCode.INVALID_LINK, at, "<link> requires an 'href' attribute");
        } else if (sawElement) {
            diag.report(ViewCode.LINK_AFTER_ELEMENT, at, "<link> must come before the root <page> or <component>");
        } else if (href.endsWith(CSS)) {
            if (stylesheet != null) {
                diag.report(ViewCode.INVALID_LINK, at, "a view links at most one stylesheet; " + stylesheet.href()
                        + " is linked at " + stylesheet.pos(), "link partials from that stylesheet with @link");
            } else {
                stylesheet = new ParsedView.Link(href, at);
            }
        } else if (linker == null) {
            diag.report(ViewCode.LINK_UNAVAILABLE, at, "only a view loaded from files can link '" + href + "'");
        } else {
            ComponentDef def = linker.link(href, at);
            if (def != null) {
                components.put(def.name(), def);
            }
        }
    }

    private static boolean isBlank(Node n) {
        return n instanceof TextNode t && t.text().isBlank();
    }

    /** Whether {@code tag} is a block: {@code <for>}, {@code <if>} or their {@code <empty>} and {@code <else>}. */
    public static boolean isBlock(String tag) {
        return tag.equals("for") || tag.equals("if") || tag.equals("else") || tag.equals("empty");
    }

    /** The block an {@code <else>} or {@code <empty>} follows, or {@code null} for any other tag. */
    public static String partnerOf(String tag) {
        return switch (tag) {
            case "else" -> "if";
            case "empty" -> "for";
            default -> null;
        };
    }

    /** Drops each {@code <else>} or {@code <empty>} that does not directly follow its partner, reporting it. */
    private void checkPartners(List<Node> children) {
        String before = null;
        for (int k = 0; k < children.size(); k++) {
            Node n = children.get(k);
            if (isBlank(n)) {
                continue;
            }
            String tag = n instanceof Element e ? e.tag() : null;
            String partner = tag == null ? null : partnerOf(tag);
            if (partner != null && !partner.equals(before)) {
                diag.report(ViewCode.ORPHAN_ELSE, n.pos(), "<" + tag + "> must directly follow a <" + partner
                        + ">, with only whitespace between");
                children.remove(k--);
            }
            before = tag;
        }
    }

    /** {@code slot=} only goes on a direct child, element or block, of a component's element. */
    private void checkSlotPlacement(List<Node> children, ComponentDef parent) {
        if (parent != null) {
            return;
        }
        for (Node n : children) {
            if (n instanceof Element c && slotOf(c) != null) {
                diag.report(ViewCode.MISPLACED_SLOT_ATTRIBUTE, c.pos(), "slot=\"" + slotOf(c) + "\" only goes on a direct child of a component's element");
            }
        }
    }

    /** The {@code slot} an element or block fills, or {@code null}; always for an {@code <else>} or {@code <empty>}, which fills its partner's. */
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
            case "page", "else", "empty" -> List.of();
            default -> List.of("name", "params", "events", "uses");
        };
        List<String> required = switch (e.tag()) {
            case "for" -> List.of("each", "as", "key");
            case "if" -> List.of("test");
            case "slot", "page", "else", "empty" -> List.of();
            default -> List.of("name");
        };
        Set<String> present = new HashSet<>();
        for (Directive d : e.directives()) {
            present.add(d.name());
            if (!allowed.contains(d.name()) && !(e.tag().equals("slot") && d.name().startsWith("provide:"))) {
                diag.report(ViewCode.UNKNOWN_BLOCK_ATTRIBUTE, d.namePos(), allowed.isEmpty() ? "<" + e.tag() + "> takes no attributes"
                        : "<" + e.tag() + "> takes only " + String.join(", ", allowed));
            }
        }
        for (String r : required) {
            if (!present.contains(r)) {
                diag.report(ViewCode.MISSING_BLOCK_ATTRIBUTE, e.pos(), "<" + e.tag() + "> requires '" + r + "'");
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
                diag.report(ViewCode.MISPLACED_MARK, c.pos(), "mark=\"" + c.attribute("mark")
                        + "\" only goes on a direct child of an element with t=\"…\"");
            }
        }
    }

    /** Sets the attribute {@code t:<attribute>} names to its translation. */
    private void translateAttribute(Element element, Directive d, ComponentDef component) {
        String attr = d.name().substring(2);
        boolean param = component != null && component.hasParam(attr);
        if (!param && !(TRANSLATABLE.contains(attr) && (component != null || tags.isAllowedAttribute(element.tag(), attr)))) {
            diag.report(ViewCode.INVALID_TRANSLATED_ATTRIBUTE, d.namePos(), "<" + element.tag() + "> has no translatable attribute '"
                    + attr + "'; t: translates " + String.join(", ", TRANSLATABLE.stream().sorted().toList()) + " and component parameters");
            return;
        }
        if (element.attribute(attr) != null || element.directives().stream().anyMatch(b -> b.name().equals("bind:" + attr))) {
            diag.report(ViewCode.INVALID_TRANSLATED_ATTRIBUTE, d.namePos(), "'" + attr + "' is given twice: by " + d.name() + " and directly");
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
        String host = component != null ? "a component's element"
                : !element.type().facet(ContentModel.KEY).ownsText() ? "<" + element.tag() + ">" : null;
        if (host != null) {
            diag.report(ViewCode.INVALID_MESSAGE_ELEMENT, d.namePos(), "t=\"…\" cannot go on " + host + ": its content is not its own");
            return;
        }
        if (element.directives().stream().anyMatch(b -> b.name().equals("bind:text"))) {
            diag.report(ViewCode.INVALID_MESSAGE_ELEMENT, d.namePos(), "t=\"…\" and bind:text both set the element's text");
            return;
        }
        List<Element> args = new ArrayList<>();
        Map<String, Element> marks = new LinkedHashMap<>();
        List<Element> tooltips = new ArrayList<>();
        boolean valid = true;
        for (Node n : element.children()) {
            if (n instanceof TextNode t) {
                if (!t.text().isBlank()) {
                    diag.report(ViewCode.INVALID_MESSAGE_CHILD, t.pos(), "text inside an element with t=\"…\" belongs in the translation");
                    valid = false;
                }
                continue;
            }
            Element c = (Element) n;
            String mark = c.attribute("mark");
            if (c.type().facet(com.azane.ixchel.core.dom.LayerFacet.KEY).pointer() == com.azane.ixchel.core.dom.LayerFacet.Pointer.TOOLTIP) {
                tooltips.add(c);
            } else if (isBlock(c.tag()) || c.tag().equals("slot")) {
                diag.report(ViewCode.INVALID_MESSAGE_CHILD, c.pos(), "<" + c.tag() + "> makes a varying number of elements;"
                        + " wrap it in one element to use it as an argument or mark");
                valid = false;
            } else if (mark == null) {
                args.add(c);
            } else if (!c.children().isEmpty()) {
                diag.report(ViewCode.INVALID_MESSAGE_CHILD, c.pos(), "mark '" + mark + "' must be empty: the translation gives its text");
                valid = false;
            } else if (marks.putIfAbsent(mark, c) != null) {
                diag.report(ViewCode.INVALID_MESSAGE_CHILD, c.pos(), "mark '" + mark + "' is declared twice");
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
            diag.report(ViewCode.MISSING_TRANSLATION, d.valuePos(), "no translation of '" + d.value() + "'",
                    "add it to the language files, the base language first");
            return null;
        }
        Message.Parsed m = Message.parse(raw, marks, args, translations.base());
        String of = "translation of '" + d.value() + "'"
                + (translations.language() == null ? "" : " (" + translations.language() + ")") + ": ";
        for (String p : m.problems()) {
            diag.report(translations.base() ? ViewCode.INVALID_TRANSLATION : ViewCode.LOCALE_TRANSLATION, d.valuePos(), of + p);
        }
        for (String u : m.undeclared()) {
            diag.report(ViewCode.UNDECLARED_MARK, d.valuePos(), of + u + " names no mark of the element and is shown as text");
        }
        return m;
    }

    private static boolean hasLetter(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }

    /** Checks a {@code <component>} root and describes its component; only library files may use the reserved prefixes. */
    private ComponentDef define(Element view) {
        boolean library = Library.isLibrary(file);
        String name = "";
        Directive params = null;
        Directive events = null;
        Directive uses = null;
        for (Directive d : view.directives()) {
            switch (d.name()) {
                case "name" -> name = d.value();
                case "params" -> params = d;
                case "events" -> events = d;
                case "uses" -> uses = d;
                default -> {
                }
            }
        }
        boolean reserved = !library && (name.startsWith("ix-") || name.startsWith("mc-"));
        if (!name.contains("-") || reserved || !isTagName(name)) {
            diag.report(ViewCode.INVALID_COMPONENT_NAME, view.pos(), "component name '" + name
                    + "' must be a tag name with a '-'" + (library ? "" : " that does not start with 'ix-' or 'mc-'"));
        } else if (tags.isKnownTag(name)) {
            diag.report(ViewCode.INVALID_COMPONENT_NAME, view.pos(), "component name '" + name + "' is a registered tag");
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
                    if (UiEvent.Type.byViewName(w.text) != null) {
                        diag.report(ViewCode.DOM_EVENT_NAME, w.pos, "event '" + w.text + "' has the name of a DOM event");
                    } else {
                        eventList.add(w.text);
                    }
                }
            }
        }
        List<String> usesList = new ArrayList<>();
        if (uses != null) {
            for (Word w : words(uses)) {
                if (checkMemberName(w.text, w.pos, "used", names)) {
                    usesList.add(w.text);
                }
            }
        }
        Set<String> slots = new LinkedHashSet<>();
        collectSlots(view.children(), slots);
        return new ComponentDef(name, view.pos(), paramList, eventList, usesList, view.children(), slots);
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
                        diag.report(ViewCode.DUPLICATE_SLOT, e.pos(), slot.isEmpty() ? "the default <slot> appears twice"
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
            diag.report(ViewCode.INVALID_MEMBER_NAME, at, what + " name '" + name + "' must be a letter followed by letters or digits");
        } else if (SHELL_NAMES.contains(name)) {
            diag.report(ViewCode.RESERVED_MEMBER_NAME, at, what + " name '" + name + "' is taken by the component's element");
        } else if (!names.add(name)) {
            diag.report(ViewCode.DUPLICATE_MEMBER, at, "'" + name + "' is declared twice");
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
            diag.report(ViewCode.UNQUOTED_ATTRIBUTE, namePos, "attribute '" + name + "' needs a quoted value");
            return;
        }
        i++;
        skipWhitespace();
        if (i >= src.length() || (src.charAt(i) != '"' && src.charAt(i) != '\'')) {
            diag.report(ViewCode.UNQUOTED_ATTRIBUTE, namePos, "value of attribute '" + name + "' must be quoted");
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
            diag.report(ViewCode.SYNTAX, namePos, "unterminated value of attribute '" + name + "'");
            i = src.length();
            return;
        }
        i = end + 1;
        if (!seen.add(name)) {
            diag.report(ViewCode.DUPLICATE_ATTRIBUTE, namePos, "duplicate attribute '" + name + "' on <" + tag + ">");
            return;
        }
        if (!tagKnown) {
            return;
        }
        if (reserved || isDirective(name)) {
            if (tag.equals("link")) {
                diag.report(ViewCode.UNKNOWN_ATTRIBUTE, namePos, "<link> takes no bindings");
            } else {
                element.addDirective(new Directive(name, decode(valueStart, end, false), namePos, pos(valueStart)));
            }
            return;
        }
        if (MOVED.contains(name)) {
            diag.report(ViewCode.MOVED_ATTRIBUTE, namePos, name.equals("if")
                    ? "'if' is a block now: wrap the element in <if test=\"…\">"
                    : "'" + name + "' is a block attribute now: wrap the element in <for each=\"…\" as=\"…\" key=\"…\">");
            return;
        }
        if (isTranslation(name)) {
            messages.add(new Directive(name, decode(valueStart, end, false), namePos, pos(valueStart)));
            return;
        }
        boolean allowed = RESOURCE_TAGS.containsKey(tag) ? RESOURCE_TAGS.get(tag).contains(name)
                : name.equals("slot")
                || (component != null ? TagRegistry.GLOBAL_ATTRIBUTES.contains(name) || component.hasParam(name)
                : tags.isAllowedAttribute(tag, name));
        if (!allowed) {
            diag.report(ViewCode.UNKNOWN_ATTRIBUTE, namePos, "unknown attribute '" + name + "' on <" + tag + ">");
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

    /** Attributes interpreted by the binding layer: {@code bind:*}, {@code on:*}, {@code state:*}, {@code provide:*}. */
    static boolean isDirective(String attribute) {
        return attribute.startsWith("bind:") || attribute.startsWith("on:") || attribute.startsWith("state:")
                || attribute.startsWith("provide:");
    }

    /** {@code t} and {@code t:<attribute>}: the parser translates them, so no element may register them. */
    static boolean isTranslation(String attribute) {
        return attribute.equals("t") || attribute.startsWith("t:");
    }

    /** Consumes {@code </name>} at {@code i} and returns the name. */
    private String readClosingTag() {
        i += 2;
        String name = readName(false);
        skipWhitespace();
        if (i < src.length() && src.charAt(i) == '>') {
            i++;
        } else {
            diag.report(ViewCode.SYNTAX, pos(i), "expected '>' to end closing tag </" + name + ">");
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
            diag.report(ViewCode.SYNTAX, pos(i), "expected a name");
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
                    diag.report(ViewCode.INVALID_CHARACTER_REFERENCE, pos(k), "invalid character reference; write &amp; for a literal '&'");
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
