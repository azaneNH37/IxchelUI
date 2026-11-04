package com.azane.ixchel.kit.schema;

import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.effect.EffectParser;
import com.azane.ixchel.core.effect.LibraryEffects;
import com.azane.ixchel.core.effect.ParamType;
import com.azane.ixchel.core.runtime.LibrarySheets;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.kit.project.Components;
import com.azane.ixchel.kit.project.ElementDeclarations;
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
 * What a template author can write in a project: tags and their attributes, the library
 * stylesheets with their theme variables, the supported CSS properties and shorthands, the effects
 * a page can link with their params, the library's and the project's components and its view
 * models' members. Value syntax is not described: the property registry does not expose a
 * description of it.
 */
public record Schema(List<Tag> tags, List<LibrarySheet> librarySheets, List<Property> properties,
                     List<Shorthand> shorthands, List<Effect> effects, List<Component> components, List<Model> models) {
    /**
     * @param attributes  the plain attributes the tag accepts, global ones included
     * @param payload     the attribute a native element's payload is bound to, or {@code null}
     * @param payloadType  the payload's type (a binding may also hand a {@code Supplier} of it), or
     *                     {@code null} when unchecked or not a native element
     * @param containerOnly whether only container screens accept the tag
     */
    public record Tag(String name, List<String> attributes, String payload, String payloadType,
                      boolean containerOnly) {
    }

    /** A longhand CSS property. */
    public record Property(String name, boolean inherited, boolean animatable) {
    }

    /** A CSS shorthand with the longhands it sets, in its expansion order. */
    public record Shorthand(String name, List<String> longhands) {
    }

    /**
     * A stylesheet a page links as {@code @ixchel/<name>}.
     *
     * @param variables the {@code --ix-*} custom properties it sets on {@code :root}, sorted
     */
    public record LibrarySheet(String name, List<String> variables) {
    }

    /**
     * An effect a page can link: one of the library's or a {@code .ixeffect} file of the project.
     *
     * @param href the {@code <link rel="effect">} href: {@code @ixchel/effects/<name>}, or the file id
     * @param kind {@code box} ({@code paint()}) or {@code layer} ({@code -ix-paint()})
     */
    public record Effect(String href, String name, String kind, List<EffectParam> params) {
    }

    /** @param property the custom property that sets it, {@code --<effect>-<param>} */
    public record EffectParam(String property, String syntax, boolean inherits, String initial) {
    }

    /**
     * @param file      the id of the file declaring it; a library component's starts with {@code @ixchel/}
     * @param params    parameter names; {@code required} lists those without a default
     * @param slots     slot names, {@code ""} for the default slot
     */
    public record Component(String name, String file, List<String> params, List<String> required,
                            List<String> events, List<String> slots) {
    }

    /** A view model class with its {@code @UiTemplate} ids or patterns and its members. */
    public record Model(String type, List<String> templates, List<Member> members) {
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
                    type.payloadType() == null ? null : type.payloadType().getName(), elements.containerOnly().contains(name)));
        }
        com.azane.ixchel.core.template.TemplateParser.RESOURCE_TAGS.forEach((name, attrs) ->
                tags.add(new Tag(name, attrs.stream().sorted().toList(), null, null, false)));
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
        List<LibrarySheet> sheets = new ArrayList<>();
        Map<String, List<String>> variables = LibrarySheets.themeVariables();
        for (String name : LibrarySheets.names()) {
            sheets.add(new LibrarySheet(name, variables.getOrDefault(name, List.of())));
        }
        List<Effect> effects = new ArrayList<>();
        for (String name : LibraryEffects.names()) {
            effects.add(effect(LibraryEffects.PREFIX + name, LibraryEffects.effect(name)));
        }
        for (String id : project.effects()) {
            com.azane.ixchel.core.effect.Effect e = EffectParser.parse(project.read(id), id, new Diagnostics());
            if (e != null) {
                effects.add(effect(id, e));
            }
        }
        List<Component> components = new ArrayList<>();
        Components scanned = Components.scan(project, project.library(), registry);
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
        return new Schema(tags, sheets, properties, shorthands, effects, components, models);
    }

    /** The schema as JSON-ready maps, lists, strings and booleans. */
    public Map<String, Object> json() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tags", tags.stream().map(t -> object(
                "name", t.name(), "attributes", t.attributes(), "payload", t.payload(),
                "payloadType", t.payloadType(), "containerOnly", t.containerOnly())).toList());
        out.put("librarySheets", librarySheets.stream().map(s -> object(
                "name", s.name(), "variables", s.variables())).toList());
        out.put("cssProperties", properties.stream().map(p -> object(
                "name", p.name(), "inherited", p.inherited(), "animatable", p.animatable())).toList());
        out.put("cssShorthands", shorthands.stream().map(s -> object(
                "name", s.name(), "longhands", s.longhands())).toList());
        out.put("effects", effects.stream().map(e -> object(
                "href", e.href(), "name", e.name(), "kind", e.kind(),
                "params", e.params().stream().map(p -> object("property", p.property(), "syntax", p.syntax(),
                        "inherits", p.inherits(), "initial", p.initial())).toList())).toList());
        out.put("components", components.stream().map(c -> object(
                "name", c.name(), "file", c.file(), "params", c.params(), "required", c.required(),
                "events", c.events(), "slots", c.slots())).toList());
        out.put("models", models.stream().map(m -> object(
                "class", m.type(), "templates", m.templates(),
                "members", m.members().stream().map(x -> object(
                        "name", x.name(), "kind", x.kind(), "type", x.type(), "writable", x.writable(),
                        "port", x.port())).toList()))
                .toList());
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
            if (t.containerOnly()) {
                sb.append(" [container screens only]");
            }
            sb.append("\n    attributes: ").append(t.attributes().isEmpty() ? "none" : String.join(" ", t.attributes()))
                    .append('\n');
        }
        sb.append("library stylesheets:\n");
        for (LibrarySheet s : librarySheets) {
            sb.append("  @ixchel/").append(s.name()).append('\n');
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
            sb.append("  ").append(e.kind().equals("box") ? "paint(" : "-ix-paint(").append(e.name()).append(") ")
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
            sb.append("    slots: ").append(c.slots().isEmpty() ? "none"
                    : String.join(" ", c.slots().stream().map(s -> s.isEmpty() ? "(default)" : s).toList())).append('\n');
        }
        sb.append("view models:\n");
        for (Model m : models) {
            sb.append("  ").append(m.type()).append(" -> ").append(String.join(", ", m.templates())).append('\n');
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
        return sb.toString();
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
        return new Component(def.name(), file, params, required, def.events(), List.copyOf(new TreeSet<>(def.slots())));
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
        List<EffectParam> params = new ArrayList<>();
        for (com.azane.ixchel.core.effect.Effect.Param p : e.params()) {
            params.add(new EffectParam(e.property(p), p.type().syntaxName(), p.inherits(), initial(p)));
        }
        return new Effect(href, e.name(), e.kind().name().toLowerCase(), params);
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
