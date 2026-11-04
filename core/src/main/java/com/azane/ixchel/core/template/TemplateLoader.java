package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.library.Library;

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
import java.util.function.Supplier;

/**
 * Loads a page and every file it links, directly or through components: stylesheets, component
 * files and effect files. Files are named by id, {@code namespace:path}, {@value Library#PREFIX}{@code path}
 * or a bare path; an {@code href} with a {@code :} or starting with {@value Library#PREFIX} is an id, any
 * other is relative to the directory of the file that links it, inside the same namespace. An effect
 * {@code href} leaves out {@value #EFFECT_EXTENSION}.
 *
 * <p>Ids under {@value Library#PREFIX} are read through the {@link Library} and are not among the files
 * read through the host. A tag in the library's {@linkplain Library#prelude() prelude} needs no link: its
 * first use in any file loads the component like a link to its file would. Only library files may
 * declare names starting with {@code ix-} or {@code mc-}.
 *
 * <p>A component file is loaded once however many files link it, and its stylesheets go into the
 * component layer once, dependencies before dependents. A file linking itself through components and
 * two files declaring the same component name are errors. An effect file linked by several files is
 * read once.
 *
 * <p>A stylesheet {@code href} written as {@value Library#PREFIX}{@code <name>} names a theme of the
 * library; only the page may link one, since it styles the whole document.
 */
public final class TemplateLoader {
    /** CSS text to parse; {@code start} is the position of its first character. */
    public record Sheet(String css, SourcePos start) {
    }

    /** A library theme the page links: {@code name} follows {@value Library#PREFIX} in the {@code href}. */
    public record Theme(String name, SourcePos pos) {
    }

    private static final String EFFECT_EXTENSION = ".ixeffect";

    /** An effect file linked by the page or a component: its id and text; {@code link} is where it was first linked. */
    public record EffectFile(String id, String text, SourcePos link) {
    }

    /**
     * @param components every component reachable from the page, by name
     * @param componentFiles the file id of each of them, by name
     * @param componentSheets the component layer, in load order
     * @param pageSheets the author layer, in document order
     * @param library the library themes the page links, each once, in document order
     * @param effects the effect files read, each once, in first-link order
     * @param files the ids of the page and every file read for it through the host, in first-read order
     */
    public record Loaded(Template page, Map<String, ComponentDef> components, Map<String, String> componentFiles,
                         List<Sheet> componentSheets, List<Sheet> pageSheets, List<Theme> library,
                         List<EffectFile> effects, List<String> files) {
    }

    private final Function<String, String> read;
    private final Library library;
    private final TagRegistry tags;
    private final Translations translations;
    private final Diagnostics diag;
    private final Map<String, ComponentDef> byFile = new HashMap<>();
    private final Map<String, String> fileOfName = new HashMap<>();
    private final Map<String, ComponentDef> components = new LinkedHashMap<>();
    private final Deque<String> loading = new ArrayDeque<>();
    private final List<Sheet> componentSheets = new ArrayList<>();
    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private final Set<String> files = new LinkedHashSet<>();
    private final Map<String, EffectFile> effects = new LinkedHashMap<>();

    private TemplateLoader(Function<String, String> read, Library library, TagRegistry tags,
                           Translations translations, Diagnostics diag) {
        this.read = read;
        this.library = library;
        this.tags = tags;
        this.translations = translations;
        this.diag = diag;
    }

    /**
     * @param read         the text of a host file id, {@code null} if there is no such file; a thrown
     *                     exception's message is reported as the reason the file cannot be loaded
     * @param library      the library files and the tags known without a link
     * @param translations resolves the {@code t} attributes of the page and its components
     */
    public static Loaded load(String template, String id, Function<String, String> read, Library library,
                              TagRegistry tags, Translations translations, Diagnostics diag) {
        TemplateLoader l = new TemplateLoader(read, library, tags, translations, diag);
        l.files.add(id);
        l.loading.push(id);
        Template page = TemplateParser.parse(template, id, tags, l.new FileLinker(id), translations,
                TemplateParser.FileKind.PAGE, diag);
        List<Sheet> sheets = l.sheets(id, page, true);
        l.effects(id, page);
        return new Loaded(page, Map.copyOf(l.components), Map.copyOf(l.fileOfName), List.copyOf(l.componentSheets), sheets,
                List.copyOf(l.themes.values()), List.copyOf(l.effects.values()), List.copyOf(l.files));
    }

    /**
     * The component the file {@code id} declares, loaded as a link to it would load it, or
     * {@code null} when it is not a component file: one without a top-level {@code <template>},
     * which only a component file may have. Problems are not reported; a page using the file
     * reports them.
     *
     * @param read the text of a host file id, {@code null} if there is no such file
     */
    public static ComponentDef declared(String id, Function<String, String> read, Library library, TagRegistry tags) {
        TemplateLoader l = new TemplateLoader(read, library, tags, Translations.NONE, new Diagnostics());
        SourcePos at = new SourcePos(id, 1, 1);
        return l.component(id, id, at, () -> l.text(id, id, at, "component file"));
    }

    /** What the file {@code from} links. */
    private final class FileLinker implements TemplateParser.Linker {
        private final String from;

        FileLinker(String from) {
            this.from = from;
        }

        @Override
        public ComponentDef link(String href, SourcePos at) {
            String id;
            try {
                id = resolve(from, href);
            } catch (IllegalArgumentException e) {
                diag.report(TemplateCode.UNREADABLE_FILE, at, "cannot load component '" + href + "': " + e.getMessage());
                return null;
            }
            return component(id, href, at, () -> text(id, href, at, "component file"));
        }

        @Override
        public ComponentDef library(String tag, SourcePos at) {
            String id = library.prelude().get(tag);
            if (id == null) {
                return null;
            }
            ComponentDef def = component(id, id, at, () -> text(id, id, at, "library component"));
            if (def != null && !def.name().equals(tag)) {
                diag.report(TemplateCode.INVALID_COMPONENT_NAME, def.pos(), "library file '" + id + "' declares <"
                        + def.name() + ">, not <" + tag + ">");
                return null;
            }
            return def;
        }
    }

    /** The component of the file {@code id}, parsed from {@code source} the first time. */
    private ComponentDef component(String id, String href, SourcePos at, Supplier<String> source) {
        if (loading.contains(id)) {
            diag.report(TemplateCode.COMPONENT_CYCLE, at, "component file '" + href + "' links back to itself through " + String.join(" <- ", loading));
            return null;
        }
        if (byFile.containsKey(id)) {
            return byFile.get(id);
        }
        byFile.put(id, null);
        String text = source.get();
        if (text == null) {
            return null;
        }
        loading.push(id);
        TemplateParser.FileKind kind = Library.isLibrary(id) ? TemplateParser.FileKind.LIBRARY_COMPONENT
                : TemplateParser.FileKind.COMPONENT;
        Template t = TemplateParser.parse(text, id, tags, new FileLinker(id), translations, kind, diag);
        loading.pop();
        componentSheets.addAll(sheets(id, t, false));
        effects(id, t);
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
            } else if (s instanceof Template.Link link && link.href().startsWith(Library.PREFIX)) {
                if (page) {
                    String name = link.href().substring(Library.PREFIX.length());
                    themes.putIfAbsent(name, new Theme(name, link.pos()));
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

    private void effects(String from, Template t) {
        for (Template.Link link : t.effects()) {
            String id;
            try {
                id = resolve(from, link.href() + EFFECT_EXTENSION);
            } catch (IllegalArgumentException e) {
                diag.report(TemplateCode.UNREADABLE_FILE, link.pos(), "cannot load effect '" + link.href() + "': " + e.getMessage());
                continue;
            }
            if (effects.containsKey(id)) {
                continue;
            }
            String text = text(id, link.href(), link.pos(), "effect");
            if (text != null) {
                effects.put(id, new EffectFile(id, text, link.pos()));
            }
        }
    }

    /** Reads {@code id} through the library or the host, reporting a failure at {@code at}. */
    private String text(String id, String href, SourcePos at, String what) {
        if (Library.isLibrary(id)) {
            return fetch(() -> library.read(id), href, at, what);
        }
        files.add(id);
        return fetch(() -> read.apply(id), href, at, what);
    }

    private String fetch(Supplier<String> source, String href, SourcePos at, String what) {
        try {
            String text = source.get();
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
     * The id {@code href} names from the file {@code from}: itself if it has a {@code namespace:} or
     * starts with {@value Library#PREFIX}, otherwise relative to {@code from}'s directory in
     * {@code from}'s namespace; {@code .} and {@code ..} segments are resolved.
     *
     * @throws IllegalArgumentException if {@code ..} leaves the namespace root
     */
    static String resolve(String from, String href) {
        String ns;
        String dir;
        String path;
        int colon = href.indexOf(':');
        if (href.startsWith(Library.PREFIX)) {
            ns = Library.PREFIX;
            dir = "";
            path = href.substring(Library.PREFIX.length());
        } else if (colon >= 0) {
            ns = href.substring(0, colon + 1);
            dir = "";
            path = href.substring(colon + 1);
        } else {
            String fromPath;
            if (from.startsWith(Library.PREFIX)) {
                ns = Library.PREFIX;
                fromPath = from.substring(Library.PREFIX.length());
            } else {
                int c = from.indexOf(':');
                ns = from.substring(0, c + 1);
                fromPath = from.substring(c + 1);
            }
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
