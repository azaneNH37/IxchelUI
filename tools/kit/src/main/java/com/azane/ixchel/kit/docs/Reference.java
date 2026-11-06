package com.azane.ixchel.kit.docs;

import com.azane.ixchel.core.binding.expr.Expr;
import com.azane.ixchel.core.binding.expr.Expressions;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.view.ViewParser;
import com.azane.ixchel.kit.schema.Schema;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The generated reference pages of the docs site: one Markdown page per kind of name a view author
 * writes, taken from a {@link Schema} of the library, the library files' own leading comments, the
 * built-in pipes and every diagnostic code. Names and facts only; the guides around them are written
 * by hand. The pages use the site theme's {@code <Badge>} types {@code ix}, {@code mc},
 * {@code inherited} and {@code animatable}, which the site's stylesheet defines.
 */
public final class Reference {
    private Reference() {
    }

    private static final String MDN_HTML = "https://developer.mozilla.org/en-US/docs/Web/HTML/Element/";
    private static final String MDN_CSS = "https://developer.mozilla.org/en-US/docs/Web/CSS/";
    private static final String THEMES = "@ixchel/themes/";
    private static final Pattern STYLESHEET = Pattern.compile("<link\\s+href=\"([^\"]+\\.css)\"");
    private static final Pattern START_TAG = Pattern.compile("<([a-z][a-z0-9-]*)((?:\\s+[^\\s=/>]+(?:\\s*=\\s*\"[^\"]*\")?)*)\\s*/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([^\\s=/>]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern PROVIDE = Pattern.compile("\\sprovide:([A-Za-z0-9_-]+)=");
    private static final Pattern LIBRARY_CLASS = Pattern.compile("\\.(ix-[A-Za-z0-9_-]+)");
    private static final Pattern CSS_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern PARAM_COMMENT = Pattern.compile("(?m)^\\s*param\\s+(\\S+)\\s*\\{[^}]*}\\s*//\\s*(.+)$");

    private static final String IXCHEL = badge("ix", "Ixchel");
    private static final String MINECRAFT = badge("mc", "Minecraft");

    /**
     * Writes the pages into {@code out}, replacing any of the same name.
     *
     * @param read the text of a library file id, {@code null} if there is no such file
     */
    public static void write(Schema schema, List<DiagCode> codes, Function<String, String> read, Path out)
            throws IOException {
        Files.createDirectories(out);
        page(out, "README.md", index());
        page(out, "elements.md", elements(schema));
        page(out, "css.md", css(schema));
        page(out, "themes.md", themes(schema, read));
        page(out, "components.md", components(schema, read));
        page(out, "effects.md", effects(schema, read));
        page(out, "expressions.md", expressions());
        page(out, "diagnostics.md", diagnostics(codes));
    }

    private static void page(Path out, String name, String text) throws IOException {
        Files.writeString(out.resolve(name), text, StandardCharsets.UTF_8);
    }

    private static String index() {
        StringBuilder sb = new StringBuilder(header("Reference"));
        sb.append("Generated from the library by `ixchel-kit docs`; the in-project `ixchel-kit schema` lists the same ")
                .append("names together with a project's own components and view models.\n\n<CardGrid>\n");
        card(sb, "elements", "Elements", "Tags, their attributes and the payload a drawn element binds.");
        card(sb, "css", "CSS properties", "Longhands and shorthands, inherited and animatable.");
        card(sb, "themes", "Themes", "What each theme sets the variables to, and the classes it styles.");
        card(sb, "components", "Library components", "Params, events, slots, parts and source.");
        card(sb, "effects", "Effects", "Shader effects a stylesheet links, with their parameters.");
        card(sb, "expressions", "Expression pipes", "Formatting a binding's value after a bar.");
        card(sb, "diagnostics", "Diagnostic codes", "Every code lint and the runtime report.");
        return sb.append("</CardGrid>\n").toString();
    }

    private static void card(StringBuilder sb, String page, String title, String description) {
        sb.append("<LinkCard title=\"").append(title).append("\" href=\"/reference/").append(page)
                .append(".html\" description=\"").append(description).append("\" />\n");
    }

    private static String elements(Schema s) {
        StringBuilder sb = new StringBuilder(header("Elements"));
        sb.append("Every element also takes the global attributes ")
                .append(codes(new TreeSet<>(TagRegistry.GLOBAL_ATTRIBUTES))).append(".\n\n");
        sb.append("Standard HTML elements link to MDN; Ixchel supports the attributes listed here, not every one MDN describes. ")
                .append(IXCHEL).append(" and ").append(MINECRAFT).append(" mark the elements that are not HTML's.\n\n");
        sb.append("| Element | Attributes | Payload |\n|---|---|---|\n");
        for (Schema.Tag t : s.tags()) {
            List<String> own = t.attributes().stream().filter(a -> !TagRegistry.GLOBAL_ATTRIBUTES.contains(a)).toList();
            String payload = t.payload() == null ? ""
                    : code("bind:" + t.payload()) + (t.payloadType() == null ? "" : " " + code(t.payloadType()));
            String name = code("<" + t.name() + ">");
            boolean standard = !t.name().contains("-") && !ViewParser.RESOURCE_TAGS.containsKey(t.name());
            String shown = standard ? link(name, MDN_HTML + t.name())
                    : name + " " + (t.name().startsWith("mc-") ? MINECRAFT : IXCHEL);
            row(sb, shown, codes(own), payload);
        }
        return sb.toString();
    }

    private static String css(Schema s) {
        StringBuilder sb = new StringBuilder(header("CSS properties"));
        sb.append("Standard properties link to MDN; ").append(IXCHEL).append(" marks Ixchel's own `-ix-` ones.\n\n");
        sb.append("| Property | Inherited | Animatable |\n|---|---|---|\n");
        for (Schema.Property p : s.properties()) {
            row(sb, property(p.name()), p.inherited() ? badge("inherited", "Inherited") : "",
                    p.animatable() ? badge("animatable", "Animatable") : "");
        }
        sb.append("\n## Shorthands\n\n| Shorthand | Longhands |\n|---|---|\n");
        for (Schema.Shorthand h : s.shorthands()) {
            row(sb, property(h.name()), codes(h.longhands()));
        }
        return sb.toString();
    }

    /**
     * Each theme with its own description, then the variables and the library classes the themes
     * style, each as a matrix with one column per theme.
     */
    private static String themes(Schema s, Function<String, String> read) {
        StringBuilder sb = new StringBuilder(header("Themes"));
        sb.append("A stylesheet links one theme; the theme links the ones it builds on.\n");
        Map<String, String> texts = new TreeMap<>();
        for (Schema.Theme t : s.themes()) {
            String text = read.apply(t.href());
            texts.put(t.href(), text == null ? "" : text);
            sb.append("\n## ").append(themeName(t.href())).append("\n\n");
            fence(sb, "css", "@link \"" + t.href() + "\";");
            String lead = lead(texts.get(t.href()));
            if (!lead.isEmpty()) {
                sb.append('\n').append(lead).append('\n');
            }
        }

        List<Schema.Theme> setting = s.themes().stream().filter(t -> !t.variables().isEmpty()).toList();
        sb.append("\n## Variables\n\nWhat each variable ends up as with a theme linked, its bases included; ")
                .append("a page or a theme of the project sets its own on `:root`.\n\n");
        Set<String> variables = new TreeSet<>();
        setting.forEach(t -> variables.addAll(t.variables().keySet()));
        matrixHead(sb, "Variable", setting.stream().map(Schema.Theme::href).toList());
        for (String v : variables) {
            List<String> cells = new ArrayList<>();
            cells.add(code(v));
            for (Schema.Theme t : setting) {
                String value = t.variables().get(v);
                cells.add(value == null ? "" : code(value));
            }
            row(sb, cells.toArray(String[]::new));
        }

        Map<String, Set<String>> styled = new TreeMap<>();
        for (Schema.Theme t : s.themes()) {
            styled.put(t.href(), libraryClasses(texts.get(t.href())));
        }
        Set<String> classes = new TreeSet<>();
        styled.values().forEach(classes::addAll);
        sb.append("\n## Classes\n\nThe library classes a page puts on its own elements for the themes to style; ")
                .append("a mark is a theme with rules for it. The classes inside a component are listed with the ")
                .append("[component](./components.md).\n\n");
        List<String> styling = s.themes().stream().map(Schema.Theme::href).filter(h -> !styled.get(h).isEmpty()).toList();
        matrixHead(sb, "Class", styling);
        for (String c : classes) {
            List<String> cells = new ArrayList<>();
            cells.add(code("." + c));
            for (String href : styling) {
                cells.add(styled.get(href).contains(c) ? "✓" : "");
            }
            row(sb, cells.toArray(String[]::new));
        }
        return sb.toString();
    }

    private static void matrixHead(StringBuilder sb, String first, List<String> themeHrefs) {
        StringBuilder head = new StringBuilder("| ").append(first).append(" |");
        StringBuilder rule = new StringBuilder("|---|");
        for (String href : themeHrefs) {
            head.append(' ').append(code(themeName(href))).append(" |");
            rule.append("---|");
        }
        sb.append(head).append('\n').append(rule).append('\n');
    }

    /** The {@code ix-*} classes the selectors of {@code css} name, comments aside. */
    private static Set<String> libraryClasses(String css) {
        Set<String> out = new TreeSet<>();
        Matcher m = LIBRARY_CLASS.matcher(CSS_COMMENT.matcher(css).replaceAll(""));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** {@code dark} for {@code @ixchel/themes/dark.css}; other hrefs as they are. */
    private static String themeName(String href) {
        return href.startsWith(THEMES) && href.endsWith(".css")
                ? href.substring(THEMES.length(), href.length() - ".css".length()) : href;
    }

    /** The components grouped by the directory of their file, each with its usage, ports and source. */
    private static String components(Schema s, Function<String, String> read) {
        StringBuilder sb = new StringBuilder(header("Library components"));
        sb.append("A view links a component's file, then writes its tag. **Interface** is what the tag takes; ")
                .append("**Styling** is each element inside it a stylesheet can reach, by `::part()` from outside ")
                .append("or by class from a theme.\n");
        Map<String, String> sources = new TreeMap<>();
        Map<String, List<String>> providers = new TreeMap<>();
        for (Schema.Component c : s.components()) {
            String html = read.apply(c.file());
            if (html != null) {
                sources.put(c.name(), html);
                Matcher m = PROVIDE.matcher(html);
                while (m.find()) {
                    providers.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(c.name());
                }
            }
        }
        Map<String, List<Schema.Component>> byDir = new TreeMap<>();
        for (Schema.Component c : s.components()) {
            byDir.computeIfAbsent(c.file().substring(0, c.file().lastIndexOf('/')), d -> new ArrayList<>()).add(c);
        }
        byDir.forEach((dir, list) -> {
            sb.append("\n## `").append(dir).append("`\n");
            for (Schema.Component c : list) {
                component(sb, c, sources.get(c.name()), providers, read);
            }
        });
        return sb.toString();
    }

    private static void component(StringBuilder sb, Schema.Component c, String html, Map<String, List<String>> providers,
                                  Function<String, String> read) {
        sb.append("\n### ").append(c.name()).append("\n\n");
        String lead = html == null ? "" : lead(html);
        if (!lead.isEmpty()) {
            sb.append(lead).append("\n\n");
        }
        StringBuilder use = new StringBuilder("<link href=\"" + c.file() + "\"/>\n<" + c.name());
        for (String p : c.required()) {
            use.append(" bind:").append(p).append("=\"…\"");
        }
        for (String e : c.events()) {
            use.append(" on:").append(e).append("=\"…\"");
        }
        fence(sb, "html", use.append("></").append(c.name()).append('>').toString());

        List<String[]> ports = new ArrayList<>();
        for (String p : c.params()) {
            String fallback = c.defaults().get(p);
            ports.add(new String[]{code("bind:" + p), badge("tip", "param"),
                    fallback == null ? "**required**" : "default " + code(fallback)});
        }
        for (String e : c.events()) {
            ports.add(new String[]{code("on:" + e), badge("warning", "event"), ""});
        }
        for (String slot : c.slots()) {
            ports.add(new String[]{slot.isEmpty() ? "content" : code("slot=\"" + slot + "\""), badge("info", "slot"),
                    slot.isEmpty() ? "the tag's children" : ""});
        }
        for (String u : c.uses()) {
            List<String> from = providers.getOrDefault(u, List.of()).stream()
                    .map(p -> link(code("<" + p + ">"), "#" + p)).toList();
            ports.add(new String[]{code(u), badge("info", "context"),
                    from.isEmpty() ? "" : "inside " + String.join(" or ", from)});
        }
        if (!ports.isEmpty()) {
            sb.append("\n**Interface**\n\n| Name | Kind | Notes |\n|---|---|---|\n");
            ports.forEach(r -> row(sb, r));
        }

        List<String[]> styling = new ArrayList<>();
        if (html != null) {
            Set<List<String>> seen = new LinkedHashSet<>();
            Matcher m = START_TAG.matcher(html);
            while (m.find()) {
                String part = null;
                String classes = null;
                Matcher a = ATTRIBUTE.matcher(m.group(2));
                while (a.find()) {
                    if (a.group(1).equals("part")) {
                        part = a.group(2).trim();
                    } else if (a.group(1).equals("class")) {
                        classes = a.group(2).trim();
                    }
                }
                if ((part != null || classes != null) && seen.add(List.of(m.group(1), String.valueOf(part), String.valueOf(classes)))) {
                    styling.add(new String[]{
                            part == null ? "" : code("::part(" + part + ")"),
                            classes == null ? "" : codes(List.of(classes.split("\\s+")).stream().map(x -> "." + x).toList()),
                            code("<" + m.group(1) + ">")});
                }
            }
        }
        if (!styling.isEmpty()) {
            sb.append("\n**Styling**\n\n| Part | Class | Element |\n|---|---|---|\n");
            styling.forEach(r -> row(sb, r));
        }

        if (html == null) {
            return;
        }
        sb.append("\n:::: details Source\n\n::: code-tabs\n");
        sb.append("@tab ").append(fileName(c.file())).append("\n\n");
        fence(sb, "html", html);
        Matcher m = STYLESHEET.matcher(html);
        if (m.find()) {
            String css = URI.create("lib:///" + c.file()).resolve(m.group(1)).getPath().substring(1);
            String text = read.apply(css);
            if (text != null) {
                sb.append("\n@tab ").append(fileName(css)).append("\n\n");
                fence(sb, "css", text);
            }
        }
        sb.append(":::\n\n::::\n");
    }

    private static String fileName(String id) {
        return id.substring(id.lastIndexOf('/') + 1);
    }

    private static void fence(StringBuilder sb, String lang, String text) {
        sb.append("```").append(lang).append('\n').append(text.replace("\r\n", "\n").stripTrailing()).append("\n```\n");
    }

    /** The effects grouped by the directory of their file, each with its usage and parameters. */
    private static String effects(Schema s, Function<String, String> read) {
        StringBuilder sb = new StringBuilder(header("Effects"));
        sb.append("A stylesheet links an effect, then paints with it under its alias: a `box` effect as a ")
                .append("`background-image`, a `layer` effect as a `filter` over the element and its content. ")
                .append("A parameter is a custom property, set on the element or, when inherited, above it.\n");
        Map<String, List<Schema.Effect>> byDir = new TreeMap<>();
        for (Schema.Effect e : s.effects()) {
            byDir.computeIfAbsent(e.href().substring(0, e.href().lastIndexOf('/')), d -> new ArrayList<>()).add(e);
        }
        byDir.forEach((dir, list) -> {
            sb.append("\n## `").append(dir).append("`\n");
            for (Schema.Effect e : list) {
                effect(sb, e, read.apply(e.href()));
            }
        });
        return sb.toString();
    }

    private static void effect(StringBuilder sb, Schema.Effect e, String source) {
        sb.append("\n### ").append(e.alias()).append(" ").append(badge("info", e.kind())).append("\n\n");
        String lead = source == null ? "" : lead(source);
        if (!lead.isEmpty()) {
            sb.append(lead).append("\n\n");
        }
        String paint = e.kind().equals("box") ? "background-image: paint(" : "filter: -ix-paint(";
        fence(sb, "css", "@link \"" + e.href() + "\";\n\n.target {\n    " + paint + e.alias() + ");\n}");
        if (e.params().isEmpty()) {
            return;
        }
        Map<String, String> notes = new TreeMap<>();
        if (source != null) {
            Matcher m = PARAM_COMMENT.matcher(source.replace("\r\n", "\n"));
            while (m.find()) {
                notes.put(m.group(1), m.group(2).trim());
            }
        }
        String prefix = "--" + e.alias() + "-";
        boolean noted = !notes.isEmpty();
        sb.append("\n| Parameter | Syntax | Initial | Inherited |").append(noted ? " Note |" : "")
                .append("\n|---|---|---|---|").append(noted ? "---|" : "").append('\n');
        for (Schema.EffectParam p : e.params()) {
            String note = notes.get(p.property().substring(prefix.length()));
            List<String> cells = new ArrayList<>(List.of(code(p.property()), code(p.syntax()), code(p.initial()),
                    p.inherits() ? badge("inherited", "Inherited") : ""));
            if (noted) {
                cells.add(note == null ? "" : cell(note));
            }
            row(sb, cells.toArray(String[]::new));
        }
    }

    /** Each pipe with its signature, what it gives and its examples evaluated. */
    private static String expressions() {
        StringBuilder sb = new StringBuilder(header("Expression pipes"));
        sb.append("A pipe formats the value of everything before its bar into a `String`, in any binding: ")
                .append("`bind:text=\"gold | compact\"`. Parentheses end it early: `(gold | compact) + ' G'`. ")
                .append("A pipe's input must have the type it takes, so a pipe cannot follow another.\n");
        for (Expressions.Pipe p : Expressions.PIPES) {
            sb.append("\n## ").append(p.name()).append("\n\n");
            fence(sb, "text", p.signature());
            sb.append('\n').append(prose(p.summary())).append("\n\n| Expression | Value |\n|---|---|\n");
            for (String example : p.examples()) {
                row(sb, code(example), code("\"" + evaluate(example) + "\""));
            }
        }
        return sb.toString();
    }

    /** The value of {@code src} with no paths in scope; a documented example that fails to compile is a bug. */
    private static Object evaluate(String src) {
        List<String> errors = new ArrayList<>();
        try {
            Expressions.Compiled c = Expressions.compile(Expr.parseExpression(src), p -> null, String::valueOf,
                    (code, at, message) -> errors.add(message));
            if (c == null) {
                throw new IllegalStateException("pipe example '" + src + "': " + errors);
            }
            return c.eval().apply(new Object[0]);
        } catch (Expr.SyntaxError e) {
            throw new IllegalStateException("pipe example '" + src + "'", e);
        }
    }

    private static String diagnostics(List<DiagCode> codes) {
        StringBuilder sb = new StringBuilder(header("Diagnostic codes"));
        sb.append(badge("danger", "ERROR")).append(" stops the view from loading; ")
                .append(badge("warning", "WARNING")).append(" is reported and the view loads. ")
                .append(badge("info", "OPT-IN")).append(" codes are only reported when enabled, such as ")
                .append("`ixchel { lint { enable(\"i18n/literal-text\") } }`.\n");
        Map<String, List<DiagCode>> byArea = new TreeMap<>();
        for (DiagCode c : codes) {
            byArea.computeIfAbsent(c.id().substring(0, c.id().indexOf('/')), a -> new ArrayList<>()).add(c);
        }
        byArea.forEach((area, list) -> {
            sb.append("\n## ").append(area).append("\n\n| Code | Severity | Description |\n|---|---|---|\n");
            for (DiagCode c : list) {
                String severity = c.severity() == Diagnostic.Severity.ERROR ? badge("danger", "ERROR")
                        : badge("warning", "WARNING");
                if (c.optIn()) {
                    severity += " " + badge("info", "OPT-IN");
                }
                row(sb, "<span id=\"" + c.id().replace('/', '-') + "\"></span>" + code(c.id()), severity, cell(c.summary()));
            }
        });
        return sb.toString();
    }

    /** The front matter; the theme shows {@code title} as the page heading. */
    private static String header(String title) {
        return "---\ntitle: " + title + "\n---\n\n<!-- Generated by ixchel-kit docs; do not edit. -->\n\n";
    }

    private static String badge(String type, String text) {
        return "<Badge type=\"" + type + "\" text=\"" + text + "\" />";
    }

    private static void row(StringBuilder sb, String... cells) {
        sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
    }

    private static String codes(java.util.Collection<String> names) {
        return String.join(" ", names.stream().map(Reference::code).toList());
    }

    /** {@code text} as an inline code span inside a table cell, where a bare {@code |} would end the cell. */
    private static String code(String text) {
        return "`" + text.replace("|", "\\|") + "`";
    }

    private static String link(String text, String url) {
        return "[" + text + "](" + url + ")";
    }

    /** A property's name, linked to MDN unless it is Ixchel's own {@code -ix-} one. */
    private static String property(String name) {
        return name.startsWith("-") ? code(name) + " " + IXCHEL : link(code(name), MDN_CSS + name);
    }

    /**
     * The comment a library file opens with ({@code <!-- -->}, {@code /* * /} or {@code //} lines) as
     * Markdown paragraphs, {@code ""} if it opens with none.
     */
    private static String lead(String text) {
        String t = text.replace("\r\n", "\n").stripLeading();
        List<String> lines = new ArrayList<>();
        if (t.startsWith("<!--") && t.contains("-->")) {
            lines.addAll(List.of(t.substring(4, t.indexOf("-->")).split("\n", -1)));
        } else if (t.startsWith("/*") && t.contains("*/")) {
            for (String l : t.substring(2, t.indexOf("*/")).split("\n", -1)) {
                String s = l.strip();
                lines.add(s.startsWith("*") ? s.substring(1) : s);
            }
        } else {
            for (String l : t.split("\n", -1)) {
                if (!l.startsWith("//")) {
                    break;
                }
                lines.add(l.substring(2));
            }
        }
        List<String> paragraphs = new ArrayList<>();
        StringBuilder p = new StringBuilder();
        for (String l : lines) {
            String s = l.strip();
            if (s.isEmpty()) {
                if (!p.isEmpty()) {
                    paragraphs.add(prose(p.toString()));
                    p.setLength(0);
                }
            } else {
                p.append(p.isEmpty() ? "" : " ").append(s);
            }
        }
        if (!p.isEmpty()) {
            paragraphs.add(prose(p.toString()));
        }
        return String.join("\n\n", paragraphs);
    }

    /**
     * Plain text as Markdown prose: Markdown's own punctuation escaped, and what the site would read as
     * markup made literal. The site compiles each page as a Vue template, so a {@code <} would open an
     * element and a double brace an interpolation; the theme reads {@code ::name::} as an icon.
     */
    private static String prose(String text) {
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

    /** Prose for a table cell, where a bare {@code |} would end the cell. */
    private static String cell(String text) {
        return text.replace("|", "\\|").replace("<", "&lt;").replace("{{", "&#123;&#123;");
    }
}
