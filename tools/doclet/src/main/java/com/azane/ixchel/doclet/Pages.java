package com.azane.ixchel.doclet;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.util.DocTrees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Markdown pages: one per {@code @HostApi} type with its public and protected members and nested
 * types, and a module's index. A member's parameters, return and exceptions share one table; every
 * member and nested type has an anchor {@link Site#id} names.
 */
final class Pages {
    private final DocTrees trees;
    private final Elements elements;
    private final Site site;
    private final Signatures signatures;
    private final Prose prose;

    Pages(DocTrees trees, Elements elements, Site site, Signatures signatures, Prose prose) {
        this.trees = trees;
        this.elements = elements;
        this.site = site;
        this.signatures = signatures;
        this.prose = prose;
    }

    /** What a doc comment's block tags say, each rendered. */
    private record Tags(Map<String, String> params, Map<String, String> typeParams, String returns,
                        List<String[]> throwsList, List<String> notes) {
    }

    String type(TypeElement top) {
        StringBuilder sb = new StringBuilder(header(top.getSimpleName().toString()));
        sb.append(badge("tip", kind(top))).append(' ')
                .append(Prose.code(elements.getPackageOf(top).getQualifiedName().toString())).append("\n\n");
        section(sb, top, top, 0);
        return sb.toString();
    }

    /** The module's index: its types by package, each with its first sentence. */
    String index(String module, List<TypeElement> tops) {
        StringBuilder sb = new StringBuilder(header(module));
        sb.append("The `@HostApi` types of `").append(module).append("`.\n");
        Map<String, List<TypeElement>> byPackage = new TreeMap<>();
        for (TypeElement t : tops) {
            byPackage.computeIfAbsent(elements.getPackageOf(t).getQualifiedName().toString(), p -> new ArrayList<>()).add(t);
        }
        byPackage.forEach((pkg, list) -> {
            sb.append("\n## ").append(pkg).append("\n\n| Type | Summary |\n|---|---|\n");
            for (TypeElement t : list) {
                DocCommentTree dc = trees.getDocCommentTree(t);
                // The first sentence was written for the type's own page: its links within that page go there.
                String summary = dc == null ? "" : prose.render(t, dc, dc.getFirstSentence()).replace('\n', ' ')
                        .replace("](#", "](/" + site.page(t) + "#");
                sb.append("| [").append(t.getSimpleName()).append("](/").append(site.page(t)).append(") ")
                        .append(badge("tip", kind(t))).append(" | ").append(cell(summary)).append(" |\n");
            }
        });
        return sb.toString();
    }

    /**
     * {@code t}'s declaration, comment and members. The top-level type ({@code depth} 0) groups its
     * members under headings; a nested type has a heading of its own and its members below it.
     */
    private void section(StringBuilder sb, TypeElement t, TypeElement top, int depth) {
        if (depth > 0) {
            sb.append("\n<span id=\"").append(site.id(t)).append("\"></span>\n\n")
                    .append(heading(depth + 2)).append(Signatures.nestedName(t)).append(' ')
                    .append(badge("tip", kind(t))).append(deprecatedBadge(t)).append("\n\n");
        }
        fence(sb, signatures.typeHeader(t));
        DocCommentTree dc = trees.getDocCommentTree(t);
        Tags tags = tags(t, dc);
        body(sb, t, dc);
        List<Field> rows = new ArrayList<>();
        for (RecordComponentElement c : t.getRecordComponents()) {
            String name = c.getSimpleName().toString();
            rows.add(new Field(Prose.code(name), typeCell(c.asType(), top), tags.params().getOrDefault(name, "")));
        }
        tags.typeParams().forEach((name, text) -> rows.add(new Field(Prose.code("<" + name + ">"), "", text)));
        table(sb, t.getKind() == ElementKind.RECORD ? "Component" : "Type parameter", rows);
        notes(sb, tags);

        boolean inInterface = t.getKind().isInterface();
        List<Element> constants = new ArrayList<>();
        Map<String, List<Element>> groups = new LinkedHashMap<>();
        for (String g : List.of("Fields", "Constructors", "Methods", "Nested types")) {
            groups.put(g, new ArrayList<>());
        }
        for (Element e : t.getEnclosedElements()) {
            // A record's equals, hashCode and toString count as explicit but have no source.
            if (elements.getOrigin(e) != Elements.Origin.EXPLICIT || trees.getTree(e) == null || !Site.shown(e)) {
                continue;
            }
            switch (e.getKind()) {
                case ENUM_CONSTANT -> constants.add(e);
                case FIELD -> groups.get("Fields").add(e);
                case CONSTRUCTOR -> groups.get("Constructors").add(e);
                case METHOD -> groups.get("Methods").add(e);
                case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> groups.get("Nested types").add(e);
                default -> {
                }
            }
        }
        if (!constants.isEmpty()) {
            if (depth == 0) {
                sb.append("\n## Enum constants\n");
            }
            sb.append("\n| Constant | Description |\n|---|---|\n");
            for (Element c : constants) {
                DocCommentTree cdc = trees.getDocCommentTree(c);
                sb.append("| <span id=\"").append(site.id(c)).append("\"></span>").append(Prose.code(c.getSimpleName().toString()))
                        .append(" | ").append(cdc == null ? "" : cell(prose.render(c, cdc, cdc.getFullBody()))).append(" |\n");
            }
        }
        groups.forEach((group, members) -> {
            if (members.isEmpty()) {
                return;
            }
            if (depth == 0) {
                sb.append("\n## ").append(group).append('\n');
            }
            for (Element m : members) {
                if (m instanceof TypeElement nested) {
                    section(sb, nested, top, depth + 1);
                } else {
                    member(sb, m, top, depth + 3, inInterface);
                }
            }
        });
    }

    private void member(StringBuilder sb, Element e, TypeElement top, int level, boolean inInterface) {
        String title = e instanceof ExecutableElement x ? signatures.erasedName(x) : e.getSimpleName().toString();
        sb.append("\n<span id=\"").append(site.id(e)).append("\"></span>\n\n").append(heading(level)).append(Prose.code(title))
                .append(e.getModifiers().contains(Modifier.STATIC) ? " " + badge("info", "static") : "")
                .append(deprecatedBadge(e)).append("\n\n");
        fence(sb, e instanceof ExecutableElement x ? signatures.executable(x, inInterface)
                : signatures.field((VariableElement) e, inInterface));
        DocCommentTree dc = trees.getDocCommentTree(e);
        Tags tags = tags(e, dc);
        body(sb, e, dc);
        if (e instanceof ExecutableElement x) {
            List<Field> rows = new ArrayList<>();
            for (VariableElement p : x.getParameters()) {
                String name = p.getSimpleName().toString();
                rows.add(new Field(Prose.code(name), typeCell(p.asType(), top), tags.params().getOrDefault(name, "")));
            }
            tags.typeParams().forEach((name, text) -> rows.add(new Field(Prose.code("<" + name + ">"), "", text)));
            if (tags.returns() != null) {
                rows.add(new Field("*returns*", typeCell(x.getReturnType(), top), tags.returns()));
            }
            tags.throwsList().forEach(th -> rows.add(new Field("*throws*", th[0], th[1])));
            table(sb, "Parameter", rows);
        }
        notes(sb, tags);
    }

    /** A row of a member's table: its name and type cells as Markdown, and what the comment says. */
    private record Field(String name, String type, String text) {
    }

    /**
     * {@code rows} as one table: the parameters, then what the member returns and throws. Left out
     * where no row has a description, as the signature already gives the names and types.
     */
    private static void table(StringBuilder sb, String head, List<Field> rows) {
        if (rows.stream().allMatch(r -> r.text().isEmpty())) {
            return;
        }
        sb.append("\n| ").append(head).append(" | Type | Description |\n|---|---|---|\n");
        for (Field r : rows) {
            sb.append("| ").append(r.name()).append(" | ").append(r.type()).append(" | ").append(cell(r.text())).append(" |\n");
        }
    }

    /** {@code type} as code, linked to its section where the site documents it. */
    private String typeCell(TypeMirror type, TypeElement top) {
        String shown = cell(Prose.code(signatures.type(type)));
        if (type.getKind() != TypeKind.DECLARED) {
            return shown;
        }
        String link = site.link(((DeclaredType) type).asElement(), top);
        return link == null || link.equals("#") ? shown : "[" + shown + "](" + link + ")";
    }

    private void body(StringBuilder sb, Element e, DocCommentTree dc) {
        if (dc == null) {
            return;
        }
        String text = prose.render(e, dc, dc.getFullBody());
        if (!text.isEmpty()) {
            sb.append('\n').append(text).append('\n');
        }
    }

    private void notes(StringBuilder sb, Tags tags) {
        if (!tags.notes().isEmpty()) {
            sb.append('\n');
            tags.notes().forEach(n -> sb.append("- ").append(n).append('\n'));
        }
    }

    private Tags tags(Element owner, DocCommentTree dc) {
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, String> typeParams = new LinkedHashMap<>();
        String returns = null;
        List<String[]> throwsList = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (dc == null) {
            return new Tags(params, typeParams, null, throwsList, notes);
        }
        for (DocTree t : dc.getBlockTags()) {
            switch (t.getKind()) {
                case PARAM -> {
                    ParamTree p = (ParamTree) t;
                    (p.isTypeParameter() ? typeParams : params).put(p.getName().getName().toString(),
                            prose.render(owner, dc, p.getDescription()));
                }
                case RETURN -> returns = prose.render(owner, dc, ((ReturnTree) t).getDescription());
                case THROWS, EXCEPTION -> {
                    ThrowsTree x = (ThrowsTree) t;
                    throwsList.add(new String[]{prose.render(owner, dc, List.of(x.getExceptionName())),
                            prose.render(owner, dc, x.getDescription())});
                }
                case SEE -> notes.add("**See** " + prose.render(owner, dc, ((SeeTree) t).getReference()));
                case SINCE -> notes.add("**Since** " + prose.render(owner, dc, ((SinceTree) t).getBody()));
                case DEPRECATED -> notes.add("**Deprecated** " + prose.render(owner, dc, ((DeprecatedTree) t).getBody()));
                case UNKNOWN_BLOCK_TAG -> {
                    UnknownBlockTagTree u = (UnknownBlockTagTree) t;
                    notes.add("**@" + u.getTagName() + "** " + prose.render(owner, dc, u.getContent()));
                }
                default -> {
                }
            }
        }
        return new Tags(params, typeParams, returns, throwsList, notes);
    }

    private String deprecatedBadge(Element e) {
        return elements.isDeprecated(e) ? " " + badge("danger", "deprecated") : "";
    }

    private static String kind(TypeElement t) {
        return switch (t.getKind()) {
            case INTERFACE -> "interface";
            case ANNOTATION_TYPE -> "annotation";
            case ENUM -> "enum";
            case RECORD -> "record";
            default -> t.getModifiers().contains(Modifier.ABSTRACT) ? "abstract class" : "class";
        };
    }

    private static String header(String title) {
        return "---\ntitle: " + title + "\n---\n\n<!-- Generated by ixchel-doclet; do not edit. -->\n\n";
    }

    private static String heading(int level) {
        return "#".repeat(Math.min(level, 6)) + " ";
    }

    private static String badge(String type, String text) {
        return "<Badge type=\"" + type + "\" text=\"" + text + "\" />";
    }

    private static void fence(StringBuilder sb, String code) {
        sb.append("```java\n").append(code).append("\n```\n");
    }

    /** Markdown for a table cell: one line, and a {@code |} a code span keeps escaped so it does not end the cell. */
    private static String cell(String text) {
        return text.replaceAll("(?<!\\\\)\\|", "\\\\|").replace("\n\n", "<br>").replace('\n', ' ');
    }
}
