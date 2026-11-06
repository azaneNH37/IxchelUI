package com.azane.ixchel.doclet;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A doc comment's HTML and inline tags as Markdown for the site: links to the site's sections, other
 * references as code, {@code <pre>} as fenced Java. The site compiles each page as a Vue template, so
 * prose never carries a raw {@code <} or a double brace.
 */
final class Prose {
    private static final Map<String, String> ENTITIES = Map.of("lt", "<", "gt", ">", "amp", "&", "quot", "\"",
            "apos", "'", "nbsp", " ");

    private final DocTrees trees;
    private final Site site;

    Prose(DocTrees trees, Site site) {
        this.trees = trees;
        this.site = site;
    }

    /** {@code body}, a part of the doc comment {@code dc} of {@code owner}, as Markdown; {@code ""} for none. */
    String render(Element owner, DocCommentTree dc, List<? extends DocTree> body) {
        TreePath path = trees.getPath(owner);
        Writer w = new Writer(owner, path == null ? null : new DocTreePath(path, dc));
        for (DocTree t : body) {
            w.node(t);
        }
        return w.out.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    /** Markdown's own punctuation escaped and what the site would read as markup made literal. */
    static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("\\`*_[]|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else if (c == '<') {
                sb.append("&lt;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replace("{{", "&#123;&#123;").replace("::", ":&#58;");
    }

    /** {@code text} as one inline code span, its fence longer than any run of backticks inside. */
    static String code(String text) {
        String t = text.replace('\n', ' ').strip();
        String fence = "`";
        while (t.contains(fence)) {
            fence += "`";
        }
        String pad = t.startsWith("`") || t.endsWith("`") ? " " : "";
        return fence + pad + t + pad + fence;
    }

    private final class Writer {
        final Element owner;
        final DocTreePath base;
        final StringBuilder out = new StringBuilder();
        final Deque<String> hrefs = new ArrayDeque<>();
        boolean pre;
        int preStart;
        StringBuilder code;

        Writer(Element owner, DocTreePath base) {
            this.owner = owner;
            this.base = base;
        }

        void node(DocTree t) {
            switch (t.getKind()) {
                case TEXT -> text(((TextTree) t).getBody());
                case ENTITY -> entity(((EntityTree) t).getName().toString());
                case START_ELEMENT -> start((StartElementTree) t);
                case END_ELEMENT -> end(((EndElementTree) t).getName().toString().toLowerCase(Locale.ROOT));
                case LINK, LINK_PLAIN -> {
                    LinkTree l = (LinkTree) t;
                    String label = l.getLabel().stream().map(DocTree::toString).reduce("", String::concat).strip();
                    reference(l.getReference(), label, t.getKind() == DocTree.Kind.LINK);
                }
                case REFERENCE -> reference((ReferenceTree) t, "", true);
                case CODE -> literal(((LiteralTree) t).getBody().getBody(), true);
                case LITERAL -> literal(((LiteralTree) t).getBody().getBody(), false);
                case SUMMARY -> ((SummaryTree) t).getSummary().forEach(this::node);
                case INDEX -> text(((IndexTree) t).getSearchTerm().toString());
                case VALUE -> value((ValueTree) t);
                case INHERIT_DOC, COMMENT -> {
                }
                default -> text(t.toString());
            }
        }

        private void append(String s) {
            (code != null ? code : out).append(s);
        }

        private void text(String s) {
            if (pre || code != null) {
                append(s);
                return;
            }
            String[] lines = s.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                sb.append(i == 0 ? lines[i] : "\n" + lines[i].stripLeading());
            }
            append(escape(sb.toString()));
        }

        private void entity(String name) {
            if (pre || code != null) {
                append(decode(name));
            } else {
                append("&" + name + ";");
            }
        }

        private String decode(String name) {
            if (name.startsWith("#x") || name.startsWith("#X")) {
                return Character.toString(Integer.parseInt(name.substring(2), 16));
            }
            if (name.startsWith("#")) {
                return Character.toString(Integer.parseInt(name.substring(1)));
            }
            return ENTITIES.getOrDefault(name, "&" + name + ";");
        }

        private void start(StartElementTree t) {
            String name = t.getName().toString().toLowerCase(Locale.ROOT);
            switch (name) {
                case "p" -> out.append("\n\n");
                case "pre" -> {
                    out.append("\n\n```java\n");
                    pre = true;
                    preStart = out.length();
                }
                case "code", "tt" -> {
                    if (!pre) {
                        code = new StringBuilder();
                    }
                }
                case "em", "i" -> out.append('*');
                case "strong", "b" -> out.append("**");
                case "ul", "ol" -> out.append("\n");
                case "li" -> out.append("\n- ");
                case "br" -> out.append("  \n");
                case "a" -> {
                    String href = null;
                    for (DocTree a : t.getAttributes()) {
                        if (a instanceof AttributeTree at && at.getName().contentEquals("href")) {
                            href = at.getValue().stream().map(DocTree::toString).reduce("", String::concat);
                        }
                    }
                    hrefs.push(href == null ? "" : href);
                    out.append('[');
                }
                default -> out.append(t);
            }
        }

        private void end(String name) {
            switch (name) {
                case "p", "li" -> {
                }
                case "pre" -> {
                    String body = dedent(out.substring(preStart));
                    out.setLength(preStart);
                    out.append(body).append("\n```\n\n");
                    pre = false;
                }
                case "code", "tt" -> {
                    if (code != null) {
                        out.append(code(code.toString()));
                        code = null;
                    }
                }
                case "em", "i" -> out.append('*');
                case "strong", "b" -> out.append("**");
                case "ul", "ol" -> out.append("\n\n");
                case "a" -> out.append("](").append(hrefs.isEmpty() ? "" : hrefs.pop()).append(')');
                default -> out.append("</").append(name).append('>');
            }
        }

        private void literal(String body, boolean asCode) {
            if (pre || code != null) {
                append(body);
            } else {
                append(asCode ? code(body) : escape(body));
            }
        }

        /** A link to {@code ref}'s section on the site, or just its label where the site has none. */
        private void reference(ReferenceTree ref, String label, boolean asCode) {
            if (label.isEmpty()) {
                String sig = ref.getSignature();
                label = sig.startsWith("#") ? sig.substring(1) : sig.replace('#', '.');
            }
            String shown = asCode ? code(label) : escape(label);
            Element target = resolve(ref);
            String url = target == null ? null : site.link(target, Site.top(owner));
            append(url == null ? shown : "[" + shown + "](" + url + ")");
        }

        private void value(ValueTree t) {
            Element target = t.getReference() == null ? owner : resolve(t.getReference());
            Object v = target instanceof VariableElement var ? var.getConstantValue() : null;
            append(code(v instanceof String s ? '"' + s + '"' : String.valueOf(v)));
        }

        private Element resolve(ReferenceTree ref) {
            if (base == null) {
                return null;
            }
            return trees.getElement(DocTreePath.getPath(base, ref));
        }
    }

    /** {@code body} without its leading blank lines and the indentation all its lines share. */
    private static String dedent(String body) {
        String[] lines = body.replaceFirst("^\\s*\\n", "").stripTrailing().split("\n", -1);
        int indent = Integer.MAX_VALUE;
        for (String l : lines) {
            if (!l.isBlank()) {
                indent = Math.min(indent, l.length() - l.stripLeading().length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l.isBlank() ? "" : l.substring(Math.min(indent, l.length()))).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}

