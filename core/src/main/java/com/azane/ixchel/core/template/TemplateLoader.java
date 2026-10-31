package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads a page and every file it links, directly or through components: stylesheets and component
 * files. Files are named by id, {@code namespace:path} or a bare path; an {@code href} with a
 * {@code :} is an id, any other is relative to the directory of the file that links it.
 *
 * <p>A component file is loaded once however many files link it, and its stylesheets go into the
 * component layer once, dependencies before dependents. A file linking itself through components and
 * two files declaring the same component name are errors.
 *
 * <p>A stylesheet {@code href} starting with {@value #LIBRARY} names a stylesheet of the library,
 * such as a theme; it is not read through the host. Only the page may link one, since it styles the
 * whole document.
 */
public final class TemplateLoader {
    /** CSS text to parse; {@code start} is the position of its first character. */
    public record Sheet(String css, SourcePos start) {
    }

    /** The prefix of a library stylesheet's {@code href}. */
    public static final String LIBRARY = "@ixchel/";

    /** A library stylesheet the page links: {@code name} follows {@link #LIBRARY} in the {@code href}. */
    public record Library(String name, SourcePos pos) {
    }

    /**
     * @param components every component reachable from the page, by name
     * @param componentSheets the component layer, in load order
     * @param pageSheets the author layer, in document order
     * @param library the library stylesheets the page links, each once, in document order
     * @param files the ids of the page and every file read for it, in first-read order
     */
    public record Loaded(Template page, Map<String, ComponentDef> components, List<Sheet> componentSheets,
                         List<Sheet> pageSheets, List<Library> library, List<String> files) {
    }

    private final Function<String, String> read;
    private final TagRegistry tags;
    private final Diagnostics diag;
    private final Map<String, ComponentDef> byFile = new HashMap<>();
    private final Map<String, String> fileOfName = new HashMap<>();
    private final Map<String, ComponentDef> components = new LinkedHashMap<>();
    private final Deque<String> loading = new ArrayDeque<>();
    private final List<Sheet> componentSheets = new ArrayList<>();
    private final Map<String, Library> library = new LinkedHashMap<>();
    private final Set<String> files = new LinkedHashSet<>();

    private TemplateLoader(Function<String, String> read, TagRegistry tags, Diagnostics diag) {
        this.read = read;
        this.tags = tags;
        this.diag = diag;
    }

    /**
     * @param read the text of a file id, {@code null} if there is no such file; a thrown exception's
     *             message is reported as the reason the file cannot be loaded
     */
    public static Loaded load(String template, String id, Function<String, String> read, TagRegistry tags,
                              Diagnostics diag) {
        TemplateLoader l = new TemplateLoader(read, tags, diag);
        l.files.add(id);
        l.loading.push(id);
        Template page = TemplateParser.parse(template, id, tags, (href, at) -> l.link(id, href, at), false, diag);
        List<Sheet> sheets = l.sheets(id, page, true);
        return new Loaded(page, Map.copyOf(l.components), List.copyOf(l.componentSheets), sheets,
                List.copyOf(l.library.values()), List.copyOf(l.files));
    }

    private ComponentDef link(String from, String href, SourcePos at) {
        String id;
        try {
            id = resolve(from, href);
        } catch (IllegalArgumentException e) {
            diag.report(TemplateCode.UNREADABLE_FILE, at, "cannot load component '" + href + "': " + e.getMessage());
            return null;
        }
        if (loading.contains(id)) {
            diag.report(TemplateCode.COMPONENT_CYCLE, at, "component file '" + href + "' links back to itself through " + String.join(" <- ", loading));
            return null;
        }
        if (byFile.containsKey(id)) {
            return byFile.get(id);
        }
        byFile.put(id, null);
        String text = text(id, href, at, "component file");
        if (text == null) {
            return null;
        }
        loading.push(id);
        Template t = TemplateParser.parse(text, id, tags, (h, p) -> link(id, h, p), true, diag);
        loading.pop();
        componentSheets.addAll(sheets(id, t, false));
        ComponentDef def = t.component();
        if (def == null) {
            return null;
        }
        String other = fileOfName.putIfAbsent(def.name(), id);
        if (other != null) {
            diag.report(TemplateCode.DUPLICATE_COMPONENT, def.pos(), "component <" + def.name() + "> is also declared in " + other);
            return null;
        }
        components.put(def.name(), def);
        byFile.put(id, def);
        return def;
    }

    private List<Sheet> sheets(String from, Template t, boolean page) {
        List<Sheet> out = new ArrayList<>();
        for (Template.StyleSource s : t.styles()) {
            if (s instanceof Template.Inline inline) {
                out.add(new Sheet(inline.css(), inline.start()));
            } else if (s instanceof Template.Link link && link.href().startsWith(LIBRARY)) {
                if (page) {
                    String name = link.href().substring(LIBRARY.length());
                    library.putIfAbsent(name, new Library(name, link.pos()));
                } else {
                    diag.report(TemplateCode.LIBRARY_SHEET_NOT_ON_PAGE, link.pos(), "only the page can link the library stylesheet '" + link.href() + "'");
                }
            } else if (s instanceof Template.Link link) {
                String id;
                try {
                    id = resolve(from, link.href());
                } catch (IllegalArgumentException e) {
                    diag.report(TemplateCode.UNREADABLE_FILE, link.pos(), "cannot load stylesheet '" + link.href() + "': " + e.getMessage());
                    continue;
                }
                String css = text(id, link.href(), link.pos(), "stylesheet");
                if (css != null) {
                    out.add(new Sheet(css, new SourcePos(id, 1, 1)));
                }
            }
        }
        return out;
    }

    /** Reads {@code id}, reporting a failure at {@code at}. */
    private String text(String id, String href, SourcePos at, String what) {
        files.add(id);
        try {
            String text = read.apply(id);
            if (text == null) {
                diag.report(TemplateCode.FILE_NOT_FOUND, at, what + " '" + href + "' not found");
            }
            return text;
        } catch (RuntimeException e) {
            diag.report(TemplateCode.UNREADABLE_FILE, at, "cannot load " + what + " '" + href + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * The id {@code href} names from the file {@code from}: itself if it has a {@code namespace:},
     * otherwise relative to {@code from}'s directory; {@code .} and {@code ..} segments are resolved.
     *
     * @throws IllegalArgumentException if {@code ..} leaves the namespace root
     */
    static String resolve(String from, String href) {
        int colon = href.indexOf(':');
        String ns;
        String dir;
        String path;
        if (colon >= 0) {
            ns = href.substring(0, colon + 1);
            dir = "";
            path = href.substring(colon + 1);
        } else {
            int c = from.indexOf(':');
            ns = from.substring(0, c + 1);
            String fromPath = from.substring(c + 1);
            int slash = fromPath.lastIndexOf('/');
            dir = slash < 0 ? "" : fromPath.substring(0, slash);
            path = href;
        }
        Deque<String> parts = new ArrayDeque<>();
        for (String p : (dir + "/" + path).split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (parts.isEmpty()) {
                    throw new IllegalArgumentException("'" + href + "' leaves the resource root");
                }
                parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return ns + String.join("/", parts);
    }
}
