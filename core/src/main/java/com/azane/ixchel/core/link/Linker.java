package com.azane.ixchel.core.link;

import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.CssSyntaxCode;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.effect.Aliases;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.view.ComponentDef;
import com.azane.ixchel.core.view.ParsedView;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.view.Translations;
import com.azane.ixchel.core.view.ViewParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
 * Loads a page and every file it links, directly or through other files: its stylesheet, component
 * files and their stylesheets, the partials those link and the effects any css file links. Files are
 * named by id, {@code namespace:path}, {@value Library#PREFIX}{@code path} or a bare path; an
 * {@code href} with a {@code :} or starting with {@value Library#PREFIX} is an id, any other is
 * relative to the directory of the file that links it, inside the same namespace. Every {@code href}
 * names the file with its extension, which says what it is: {@value #CSS} a css file, {@value #HTML}
 * a view, {@value #EFFECT} an effect. Library ids are read through the {@link Library} and are not
 * among the files read through the host.
 *
 * <p>A view links one {@code @stylesheet} and any number of {@code <component>} files; a css file
 * links {@code @partial}s and effects with {@code @link}. A component file is loaded once however
 * many files link it. A css file expands depth first, each file after what it links, each file once.
 * The {@link Loaded#layers() layers} are every partial reached, lowest, then the views'
 * stylesheets in load order, the components' below the page's, each file a layer of its own: a
 * partial reaches every element, a stylesheet only the elements of the views it is the stylesheet of.
 * A file linking itself through others and two files declaring the same component name are errors.
 * An effect file linked several times is read once.
 *
 * <p>Each css file names the effects it links by its own {@link Aliases}; its rules, and the inline
 * styles of the views whose stylesheet it is, come out with the aliases rewritten to internal names.
 */
public final class Linker {
    /**
     * A css file of a layer: its id, its stylesheet, parsed and {@linkplain Aliases#rewrite rewritten},
     * and the effects it links by alias.
     */
    public record Sheet(String id, ParsedSheet sheet, Aliases aliases) {
    }

    static final String CSS = ".css";
    static final String HTML = ".html";
    static final String EFFECT = ".ixeffect";

    /**
     * A layer of the cascade: a css file and the views it serves. For a stylesheet, {@code views} are
     * those it is the stylesheet of, whose elements its rules reach; for a partial, which reaches
     * every element, the views whose stylesheet links it, directly or through other partials.
     */
    public record Layer(Sheet sheet, boolean partial, Set<String> views) {
    }

    /**
     * @param components     every component reachable from the page, by name
     * @param componentFiles the file id of each of them, by name
     * @param layers         the layers, lowest first: the partials, then the stylesheets, the page's last
     * @param effects        the texts of the effect files read, by id, in first-link order
     * @param aliases        the effect aliases of each view's stylesheet, by view id; a view without
     *                       one is absent
     * @param files          the ids of the page and every file read for it through the host, in first-read order
     */
    public record Loaded(ParsedView page, Map<String, ComponentDef> components, Map<String, String> componentFiles,
                         List<Layer> layers, Map<String, String> effects,
                         Map<String, Aliases> aliases, List<String> files) {
        /** The effect aliases of the stylesheet of the view {@code id}. */
        public Aliases aliasesOf(String id) {
            return aliases.getOrDefault(id, Aliases.NONE);
        }
    }

    /** A layer being collected: its file and the views seen so far. */
    private record Collecting(Sheet sheet, Set<String> views) {
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
    private final Map<String, Collecting> partials = new LinkedHashMap<>();
    private final Map<String, Collecting> stylesheets = new LinkedHashMap<>();
    /** The files each css file expands into, lowest first, itself last, by id; once expanded. */
    private final Map<String, List<Sheet>> expansions = new HashMap<>();
    /** Each css file parsed, by id; {@code null} when it cannot be read. */
    private final Map<String, Sheet> sheets = new HashMap<>();
    private final Set<String> files = new LinkedHashSet<>();
    private final Map<String, String> effects = new LinkedHashMap<>();
    private final Map<String, Aliases> viewAliases = new HashMap<>();

    private Linker(Function<String, String> read, Library library, TagRegistry tags,
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
     * @param library      the library files
     * @param translations resolves the {@code t} attributes of the page and its components
     */
    public static Loaded load(String view, String id, Function<String, String> read, Library library,
                              TagRegistry tags, Translations translations, Diagnostics diag) {
        Linker l = new Linker(read, library, tags, translations, diag);
        l.files.add(id);
        l.loading.push(id);
        ParsedView page = ViewParser.parse(view, id, tags, l.new FileLinker(id), translations, diag);
        if (page.kind() == ParsedView.Kind.COMPONENT) {
            diag.report(LinkCode.WRONG_KIND, new SourcePos(id, 1, 1), "'" + id + "' is a component, not a page;"
                    + " a screen or HUD shows a <page>");
        }
        l.stylesheet(id, page);
        List<Layer> layers = new ArrayList<>(l.partials.size() + l.stylesheets.size());
        l.partials.values().forEach(c -> layers.add(new Layer(c.sheet(), true, Set.copyOf(c.views()))));
        l.stylesheets.values().forEach(c -> layers.add(new Layer(c.sheet(), false, Set.copyOf(c.views()))));
        return new Loaded(page, Map.copyOf(l.components), Map.copyOf(l.fileOfName), List.copyOf(layers),
                Collections.unmodifiableMap(l.effects), Map.copyOf(l.viewAliases), List.copyOf(l.files));
    }

    /**
     * The component the file {@code id} declares, loaded as a link to it would load it, or
     * {@code null} when it is not a component file: one whose root is not a {@code <component>}.
     * Problems are not reported; a page using the file reports them.
     *
     * @param read the text of a host file id, {@code null} if there is no such file
     */
    public static ComponentDef declared(String id, Function<String, String> read, Library library, TagRegistry tags) {
        Linker l = new Linker(read, library, tags, Translations.NONE, new Diagnostics());
        SourcePos at = new SourcePos(id, 1, 1);
        return l.component(id, id, at);
    }

    /**
     * The files the css file {@code id} expands into, lowest first, itself last, loaded as a link to
     * it would load them; empty if it cannot be read. Problems are not reported; a page linking the
     * file reports them.
     *
     * @param read the text of a host file id, {@code null} if there is no such file
     */
    public static List<Sheet> expand(String id, Function<String, String> read, Library library) {
        Linker l = new Linker(read, library, TagRegistry.builder().build(), Translations.NONE, new Diagnostics());
        return l.expand(id, id, new SourcePos(id, 1, 1), null, new ArrayDeque<>());
    }

    /** The components the file {@code from} links. */
    private final class FileLinker implements ViewParser.Links {
        private final String from;

        FileLinker(String from) {
            this.from = from;
        }

        @Override
        public ComponentDef link(String href, SourcePos at) {
            if (!href.endsWith(HTML)) {
                diag.report(LinkCode.WRONG_KIND, at, "a view links one " + CSS + " stylesheet and " + HTML
                        + " component files, not '" + href + "'", href.endsWith(EFFECT)
                        ? "link the effect from the stylesheet with @link \"" + href + "\" as Alias;" : null);
                return null;
            }
            String id = id(from, href, at);
            return id == null ? null : component(id, href, at);
        }
    }

    /** The id {@code href} names from {@code from}; {@code null} once reported. */
    private String id(String from, String href, SourcePos at) {
        try {
            return resolve(from, href);
        } catch (IllegalArgumentException e) {
            diag.report(LinkCode.UNREADABLE_FILE, at, "cannot load '" + href + "': " + e.getMessage());
            return null;
        }
    }

    /** The component of the file {@code id}, parsed the first time. */
    private ComponentDef component(String id, String href, SourcePos at) {
        if (loading.contains(id)) {
            diag.report(LinkCode.CYCLE, at, "component file '" + href + "' links back to itself through "
                    + String.join(" <- ", loading));
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
        ParsedView t = ViewParser.parse(text, id, tags, new FileLinker(id), translations, diag);
        loading.pop();
        if (t.kind() == ParsedView.Kind.PAGE) {
            diag.report(LinkCode.WRONG_KIND, at, "'" + href + "' is a page, not a component");
            return null;
        }
        stylesheet(id, t);
        ComponentDef def = t.component();
        if (def == null) {
            return null;
        }
        String other = fileOfName.putIfAbsent(def.name(), id);
        if (other != null) {
            diag.report(LinkCode.DUPLICATE_COMPONENT, def.pos(), "component <" + def.name() + "> is also declared in " + other);
            return null;
        }
        components.put(def.name(), def);
        byFile.put(id, def);
        return def;
    }

    /**
     * Collects the layers of the stylesheet the view {@code from} links, counting {@code from} among
     * the views of each, and rewrites the view's inline styles with the stylesheet's aliases.
     */
    private void stylesheet(String from, ParsedView view) {
        ParsedView.Link link = view.stylesheet();
        String id = link == null ? null : id(from, link.href(), link.pos());
        Aliases aliases = Aliases.NONE;
        if (id != null) {
            for (Sheet s : expand(id, link.href(), link.pos(), ParsedSheet.Kind.STYLESHEET, new ArrayDeque<>())) {
                (s.id().equals(id) ? stylesheets : partials)
                        .computeIfAbsent(s.id(), k -> new Collecting(s, new LinkedHashSet<>())).views().add(from);
            }
            Sheet sheet = sheets.get(id);
            if (sheet != null && sheet.sheet().kind() != ParsedSheet.Kind.PARTIAL) {
                aliases = sheet.aliases();
                viewAliases.put(from, aliases);
            }
        }
        inline(view.roots(), aliases);
        if (view.component() != null) {
            inline(view.component().body(), aliases);
        }
    }

    private void inline(List<Node> nodes, Aliases aliases) {
        for (Node n : nodes) {
            if (n instanceof Element e) {
                if (!e.inlineStyle().isEmpty()) {
                    e.setInlineStyle(aliases.rewrite(e.inlineStyle(), diag));
                }
                inline(e.children(), aliases);
            }
        }
    }

    /**
     * The css file {@code id} after the partials it links, depth first, each file once; empty when
     * it cannot be read or is not the {@code kind} the linking file expects ({@code null} for
     * anything). A file is expanded once; later links reuse the result.
     */
    private List<Sheet> expand(String id, String href, SourcePos at, ParsedSheet.Kind kind, Deque<String> chain) {
        if (chain.contains(id)) {
            diag.report(LinkCode.CYCLE, at, "'" + href + "' links back to itself through " + String.join(" <- ", chain));
            return List.of();
        }
        Sheet linked = sheet(id, href, at);
        if (linked == null) {
            return List.of();
        }
        ParsedSheet sheet = linked.sheet();
        if (kind != null && sheet.kind() != null && sheet.kind() != kind) {
            diag.report(LinkCode.WRONG_KIND, at, "'" + href + "' is " + (sheet.kind() == ParsedSheet.Kind.PARTIAL
                    ? "a @partial; a view links a @stylesheet and its stylesheet links the partial"
                    : "a @stylesheet; a css file links only @partial files"));
            return List.of();
        }
        List<Sheet> done = expansions.get(id);
        if (done != null) {
            return done;
        }
        Map<String, Sheet> out = new LinkedHashMap<>();
        chain.push(id);
        for (ParsedSheet.Link l : sheet.links()) {
            if (l.href().endsWith(EFFECT)) {
                continue; // read with the file
            }
            String partial = id(id, l.href(), l.pos());
            if (partial == null) {
                continue;
            }
            if (l.href().endsWith(CSS)) {
                for (Sheet s : expand(partial, l.href(), l.pos(), ParsedSheet.Kind.PARTIAL, chain)) {
                    out.putIfAbsent(s.id(), s);
                }
            } else {
                diag.report(LinkCode.WRONG_KIND, l.pos(), "a css file links " + CSS + " partials and " + EFFECT
                        + " effects, not '" + l.href() + "'");
            }
        }
        chain.pop();
        out.putIfAbsent(id, linked);
        done = List.copyOf(out.values());
        expansions.put(id, done);
        return done;
    }

    /**
     * The css file {@code id}, parsed, with the effects it links read and its aliases rewritten, the
     * first time; {@code null} when it cannot be read.
     */
    private Sheet sheet(String id, String href, SourcePos at) {
        if (sheets.containsKey(id)) {
            return sheets.get(id);
        }
        String css = text(id, href, at, "css file");
        Sheet sheet = null;
        if (css != null) {
            ParsedSheet parsed = CssParser.parseStylesheet(css, new SourcePos(id, 1, 1), diag);
            if (parsed.kind() == null) {
                diag.report(CssSyntaxCode.MISSING_KIND, new SourcePos(id, 1, 1), "'" + href
                        + "' must start with @stylesheet; or @partial;");
            }
            Aliases.Builder aliases = new Aliases.Builder();
            for (ParsedSheet.Link l : parsed.links()) {
                String effect = l.href().endsWith(EFFECT) ? id(id, l.href(), l.pos()) : null;
                if (effect != null) {
                    effect(effect, l.href(), l.pos());
                    aliases.add(l.alias(), effect, l.href(), l.pos(), diag);
                }
            }
            Aliases built = aliases.build();
            sheet = new Sheet(id, built.rewrite(withFontIds(id, parsed), diag), built);
        }
        sheets.put(id, sheet);
        return sheet;
    }

    /** {@code parsed} with each {@code @font-face} src resolved from {@code id} to a file id; one that cannot be is reported and dropped. */
    private ParsedSheet withFontIds(String id, ParsedSheet parsed) {
        if (parsed.fontFaces().isEmpty()) {
            return parsed;
        }
        List<ParsedSheet.FontFace> faces = new ArrayList<>(parsed.fontFaces().size());
        for (ParsedSheet.FontFace f : parsed.fontFaces()) {
            String file = id(id, f.src(), f.pos());
            if (file != null) {
                faces.add(new ParsedSheet.FontFace(f.family(), file, f.pos()));
            }
        }
        return new ParsedSheet(parsed.kind(), parsed.links(), parsed.rules(), parsed.keyframes(), List.copyOf(faces));
    }

    private void effect(String id, String href, SourcePos at) {
        if (effects.containsKey(id)) {
            return;
        }
        String text = text(id, href, at, "effect");
        if (text != null) {
            effects.put(id, text);
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
                diag.report(LinkCode.FILE_NOT_FOUND, at, what + " '" + href + "' not found");
            }
            return text;
        } catch (RuntimeException e) {
            diag.report(LinkCode.UNREADABLE_FILE, at, "cannot load " + what + " '" + href + "': " + e.getMessage());
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
