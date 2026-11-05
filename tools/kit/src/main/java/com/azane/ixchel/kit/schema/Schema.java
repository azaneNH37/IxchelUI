package com.azane.ixchel.kit.schema;

import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.effect.Aliases;
import com.azane.ixchel.core.effect.EffectParser;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.effect.ParamType;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.style.Declarations;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.view.ComponentDef;
import com.azane.ixchel.core.link.Linker;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.binding.expr.Expressions;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.style.LibraryNames;
import com.azane.ixchel.kit.project.Components;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.LibraryFiles;
import com.azane.ixchel.kit.project.Project;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a view author can write in a project: tags and their attributes, the themes a stylesheet can
 * link with their variables, the supported CSS properties and shorthands, the effects a stylesheet can
 * link with their params, the library's and the project's components and its view models' members,
 * the library's {@code .ix-*} classes and the built-in expression pipes. Value syntax is not described:
 * the property registry does not expose a description of it.
 */
public record Schema(List<Tag> tags, List<Theme> themes, List<Property> properties,
                     List<Shorthand> shorthands, List<Effect> effects,
                     List<Component> components, List<Model> models, List<String> classes, List<String> pipes) {
    /**
     * @param attributes  the plain attributes the tag accepts, global ones included
     * @param payload     the attribute a drawn element's payload is bound to, or {@code null}
     * @param payloadType the payload's type (a binding may also hand a {@code Supplier} of it), or
     *                    {@code null} when unchecked or not a drawn element
     */
    public record Tag(String name, List<String> attributes, String payload, String payloadType) {
    }

    /** A longhand CSS property. */
    public record Property(String name, boolean inherited, boolean animatable) {
    }

    /** A CSS shorthand with the longhands it sets, in its expansion order. */
    public record Shorthand(String name, List<String> longhands) {
    }

    /**
     * A theme a stylesheet can link: one of the library's or a theme file of the project.
     *
     * @param href      the {@code @link} href: {@code @ixchel/themes/<name>.css}, or the file id
     * @param variables the {@code --ix-*} custom properties it and its bases set on {@code :root}, sorted
     */
    public record Theme(String href, List<String> variables) {
    }

    /**
     * An effect a stylesheet can link: one of the library's or a {@code .ixeffect} file of the project.
     *
     * @param href  the {@code @link} href: {@code @ixchel/effects/<name>.ixeffect}, or the file id
     * @param alias the alias a link without {@code as} gives it: its file name in PascalCase
     * @param kind  {@code box} ({@code paint()}) or {@code layer} ({@code -ix-paint()})
     */
    public record Effect(String href, String alias, String kind, List<EffectParam> params) {
    }

    /** @param property the custom property that sets it under the default alias, {@code --<Alias>-<param>} */
    public record EffectParam(String property, String syntax, boolean inherits, String initial) {
    }

    /**
     * @param file      the id of the file declaring it; a library component's starts with {@code @ixchel/}
     * @param params    parameter names; {@code required} lists those without a default
     * @param uses      the names it takes from a {@code provide:} above it
     * @param slots     slot names, {@code ""} for the default slot
     * @param parts     the {@code part} names of its view's elements, which {@code ::part()} selects, sorted
     */
    public record Component(String name, String file, List<String> params, List<String> required,
                            List<String> events, List<String> uses, List<String> slots, List<String> parts) {
    }

    /** A view model class with its {@code @UiView} ids or patterns and its members. */
    public record Model(String type, List<String> views, List<Member> members) {
    }

    /**
     * @param kind {@code cell}, {@code action} or {@code event}
     * @param type the cell's value type, the action's argument type or the event's payload type,
     *             {@code null} for a raw action
     * @param port whether a component's user wires it: a param, two-way param or event
     */
    public record Member(String name, String kind, String type, boolean writable, boolean port) {
    }

    /** Collects the schema of {@code project} with the tags of {@code elements}. */
    public static Schema of(Project project, ElementDeclarations elements) {
        TagRegistry registry = elements.tags();
        List<Tag> tags = new ArrayList<>();
        for (String name : new TreeSet<>(registry.tags())) {
            ElementType type = registry.type(name);
            Set<String> attributes = new TreeSet<>(type.attributes());
            attributes.addAll(TagRegistry.GLOBAL_ATTRIBUTES);
            tags.add(new Tag(name, List.copyOf(attributes), type.payload(),
                    type.payloadType() == null ? null : type.payloadType().getTypeName()));
        }
        com.azane.ixchel.core.view.ViewParser.RESOURCE_TAGS.forEach((name, attrs) ->
                tags.add(new Tag(name, attrs.stream().sorted().toList(), null, null)));
        tags.sort(Comparator.comparing(Tag::name));
        List<Property> properties = new ArrayList<>();
        for (PropertyDef<?> p : CssProperties.REGISTRY.all()) {
            properties.add(new Property(p.name(), p.inherited(), p.animatable()));
        }
        properties.sort(Comparator.comparing(Property::name));
        List<Shorthand> shorthands = new ArrayList<>();
        for (com.azane.ixchel.core.css.property.Shorthand s : CssProperties.REGISTRY.shorthands()) {
            shorthands.add(new Shorthand(s.name(), s.longhands().stream().<String>map(PropertyDef::name).toList()));
        }
        shorthands.sort(Comparator.comparing(Shorthand::name));
        UiHosting hosting = project.hosting();
        Library library = hosting.library();
        List<Theme> themes = new ArrayList<>();
        for (String href : LibraryFiles.list(library, ".css")) {
            if (CssParser.parseStylesheet(library.read(href), href, new Diagnostics()).kind() == ParsedSheet.Kind.PARTIAL) {
                themes.add(theme(href, project, library));
            }
        }
        Components scanned = Components.scan(project, hosting, registry);
        for (String id : scanned.themes()) {
            themes.add(theme(id, project, library));
        }
        List<Effect> effects = new ArrayList<>();
        for (String id : LibraryFiles.list(library, com.azane.ixchel.core.effect.Effect.EXTENSION)) {
            effects.add(effect(id, EffectParser.parse(library.read(id), id, new Diagnostics())));
        }
        for (String id : project.effects()) {
            com.azane.ixchel.core.effect.Effect e = EffectParser.parse(project.read(id), id, new Diagnostics());
            if (e != null) {
                effects.add(effect(id, e));
            }
        }
        List<Component> components = new ArrayList<>();
        for (Components.LibraryComponent l : scanned.library()) {
            components.add(component(l.def(), l.id()));
        }
        for (ComponentDef def : scanned.defs()) {
            components.add(component(def, def.pos().file()));
        }
        components.sort(Comparator.comparing(Component::name));
        List<Model> models = new ArrayList<>();
        for (Project.Annotated a : project.annotated()) {
            if (!ViewModel.class.isAssignableFrom(a.type())) {
                continue;
            }
            List<Member> members = new ArrayList<>();
            for (ModelShape.Member m : ModelShape.of(a.type().asSubclass(ViewModel.class)).members().values()) {
                Type t = m.type();
                members.add(new Member(m.name(), m.kind().name().toLowerCase(), t == null ? null : t.getTypeName(),
                        m.writable(), m.port()));
            }
            models.add(new Model(a.type().getName(), a.patterns(), members));
        }
        return new Schema(tags, themes, properties, shorthands, effects, components, models,
                LibraryNames.of(library, LibraryFiles.list(library, ".css")).classes(), Expressions.PIPE_SIGNATURES);
    }

    /** The schema as JSON-ready maps, lists, strings and booleans. */
    public Map<String, Object> json() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tags", tags.stream().map(t -> object(
                "name", t.name(), "attributes", t.attributes(), "payload", t.payload(),
                "payloadType", t.payloadType())).toList());
        out.put("themes", themes.stream().map(s -> object(
                "href", s.href(), "variables", s.variables())).toList());
        out.put("cssProperties", properties.stream().map(p -> object(
                "name", p.name(), "inherited", p.inherited(), "animatable", p.animatable())).toList());
        out.put("cssShorthands", shorthands.stream().map(s -> object(
                "name", s.name(), "longhands", s.longhands())).toList());
        out.put("effects", effects.stream().map(e -> object(
                "href", e.href(), "alias", e.alias(), "kind", e.kind(),
                "params", e.params().stream().map(p -> object("property", p.property(), "syntax", p.syntax(),
                        "inherits", p.inherits(), "initial", p.initial())).toList())).toList());
        out.put("components", components.stream().map(c -> object(
                "name", c.name(), "file", c.file(), "params", c.params(), "required", c.required(),
                "events", c.events(), "uses", c.uses(), "slots", c.slots(), "parts", c.parts())).toList());
        out.put("models", models.stream().map(m -> object(
                "class", m.type(), "views", m.views(),
                "members", m.members().stream().map(x -> object(
                        "name", x.name(), "kind", x.kind(), "type", x.type(), "writable", x.writable(),
                        "port", x.port())).toList()))
                .toList());
        out.put("classes", classes);
        out.put("pipes", pipes);
        return out;
    }

    /** The schema as plain text, one section per kind. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        sb.append("tags:\n");
        for (Tag t : tags) {
            sb.append("  <").append(t.name()).append('>');
            if (t.payload() != null) {
                sb.append(" payload=").append(t.payload());
                if (t.payloadType() != null) {
                    sb.append(" (").append(t.payloadType()).append(" or a Supplier of one)");
                }
            }
            sb.append("\n    attributes: ").append(t.attributes().isEmpty() ? "none" : String.join(" ", t.attributes()))
                    .append('\n');
        }
        sb.append("themes:\n");
        for (Theme s : themes) {
            sb.append("  ").append(s.href()).append('\n');
            if (!s.variables().isEmpty()) {
                sb.append("    variables: ").append(String.join(" ", s.variables())).append('\n');
            }
        }
        sb.append("css properties:\n");
        for (Property p : properties) {
            sb.append("  ").append(p.name());
            if (p.inherited()) {
                sb.append(" inherited");
            }
            if (p.animatable()) {
                sb.append(" animatable");
            }
            sb.append('\n');
        }
        sb.append("css shorthands:\n");
        for (Shorthand s : shorthands) {
            sb.append("  ").append(s.name()).append(" -> ").append(String.join(" ", s.longhands())).append('\n');
        }
        sb.append("effects:\n");
        for (Effect e : effects) {
            sb.append("  ").append(e.kind().equals("box") ? "paint(" : "-ix-paint(").append(e.alias()).append(") ")
                    .append(e.href()).append('\n');
            for (EffectParam p : e.params()) {
                sb.append("    ").append(p.property()).append(' ').append(p.syntax()).append(" = ").append(p.initial());
                if (p.inherits()) {
                    sb.append(" inherited");
                }
                sb.append('\n');
            }
        }
        sb.append("components:\n");
        for (Component c : components) {
            sb.append("  <").append(c.name()).append("> ").append(c.file()).append('\n');
            sb.append("    params: ").append(params(c)).append('\n');
            sb.append("    events: ").append(c.events().isEmpty() ? "none" : String.join(" ", c.events())).append('\n');
            if (!c.uses().isEmpty()) {
                sb.append("    uses: ").append(String.join(" ", c.uses())).append('\n');
            }
            sb.append("    slots: ").append(c.slots().isEmpty() ? "none"
                    : String.join(" ", c.slots().stream().map(s -> s.isEmpty() ? "(default)" : s).toList())).append('\n');
            if (!c.parts().isEmpty()) {
                sb.append("    parts: ").append(String.join(" ", c.parts())).append('\n');
            }
        }
        sb.append("view models:\n");
        for (Model m : models) {
            sb.append("  ").append(m.type()).append(" -> ").append(String.join(", ", m.views())).append('\n');
            for (Member x : m.members()) {
                sb.append("    ").append(x.name()).append(": ").append(x.kind()).append(' ')
                        .append(x.type() == null ? "?" : x.type());
                if (x.writable()) {
                    sb.append(" writable");
                }
                if (x.port()) {
                    sb.append(" port");
                }
                sb.append('\n');
            }
        }
        sb.append("classes: ").append(classes.stream().map(c -> "." + c).collect(java.util.stream.Collectors.joining(" "))).append('\n');
        sb.append("pipes:\n");
        for (String p : pipes) {
            sb.append("  ").append(p).append('\n');
        }
        return sb.toString();
    }

    /** The theme {@code href} with the {@code --ix-*} variables it and the partials it links set on {@code :root}. */
    private static Theme theme(String href, Project project, Library library) {
        Set<String> variables = new TreeSet<>();
        for (Linker.Sheet t : Linker.expand(href, project::read, library)) {
            StyleSheet sheet = StyleSheet.compile(t.sheet(), CssProperties.REGISTRY, new Diagnostics());
            for (StyleSheet.StyleRule r : sheet.rules()) {
                if (!isRoot(r.selector())) {
                    continue;
                }
                for (Declarations.Custom c : r.declarations().customs()) {
                    if (c.name().startsWith("--ix-")) {
                        variables.add(c.name());
                    }
                }
            }
        }
        return new Theme(href, List.copyOf(variables));
    }

    /** Whether {@code s} is a lone {@code :root}. */
    private static boolean isRoot(Selector s) {
        if (s.compounds().size() != 1 || s.pseudoElement() != Selector.PseudoElement.NONE) {
            return false;
        }
        Selector.Compound c = s.compounds().get(0);
        return c.root() && c.tag() == null && c.id() == null && c.classes().isEmpty() && c.attributes().isEmpty()
                && c.stateMask() == 0 && c.logical().isEmpty();
    }

    private static Component component(ComponentDef def, String file) {
        List<String> params = new ArrayList<>();
        List<String> required = new ArrayList<>();
        for (ComponentDef.Param p : def.params()) {
            params.add(p.name());
            if (p.fallback() == null) {
                required.add(p.name());
            }
        }
        Set<String> parts = new TreeSet<>();
        parts(def.body(), parts);
        return new Component(def.name(), file, params, required, def.events(), def.uses(),
                List.copyOf(new TreeSet<>(def.slots())), List.copyOf(parts));
    }

    private static void parts(List<Node> nodes, Set<String> out) {
        for (Node n : nodes) {
            if (n instanceof Element e) {
                String part = e.attribute("part");
                if (part != null) {
                    for (String p : part.trim().split("\\s+")) {
                        if (!p.isEmpty()) {
                            out.add(p);
                        }
                    }
                }
                parts(e.children(), out);
            }
        }
    }

    private static String params(Component c) {
        if (c.params().isEmpty()) {
            return "none";
        }
        List<String> out = new ArrayList<>();
        for (String p : c.params()) {
            out.add(c.required().contains(p) ? p : p + "?");
        }
        return String.join(" ", out);
    }

    private static Effect effect(String href, com.azane.ixchel.core.effect.Effect e) {
        String alias = Aliases.defaultAlias(e.id());
        List<EffectParam> params = new ArrayList<>();
        for (com.azane.ixchel.core.effect.Effect.Param p : e.params()) {
            params.add(new EffectParam("--" + alias + "-" + p.name(), p.type().syntaxName(), p.inherits(), initial(p)));
        }
        return new Effect(href, alias, e.kind().name().toLowerCase(), params);
    }

    /** {@code p}'s initial value as CSS writes it. */
    private static String initial(com.azane.ixchel.core.effect.Effect.Param p) {
        if (p.type() == ParamType.COLOR) {
            int c = (Integer) p.initial();
            return String.format("#%06x%02x", c & 0xFFFFFF, c >>> 24);
        }
        float v = (Float) p.initial();
        return switch (p.type()) {
            case LENGTH -> number(v) + "px";
            case PERCENTAGE -> number(v * 100) + "%";
            case ANGLE -> number(v) + "deg";
            case TIME -> number(v) + "s";
            default -> number(v);
        };
    }

    private static String number(float v) {
        return v == (int) v ? Integer.toString((int) v) : Float.toString(v);
    }

    /** An ordered JSON object from alternating keys and values; values may be {@code null}. */
    private static Map<String, Object> object(Object... keysAndValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }
}
