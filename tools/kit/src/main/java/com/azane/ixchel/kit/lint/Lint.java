package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.BindingCode;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.effect.EffectParser;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.Translations;
import com.azane.ixchel.kit.project.Components;
import com.azane.ixchel.kit.project.Project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a project's templates without running the game:
 *
 * <ul>
 * <li>each page a view model's {@link UiTemplate} lists or matches, against that class's
 *     {@link ModelShape}: structure, stylesheets and binding types;</li>
 * <li>each other page under the resource roots for structure only; names it binds cannot be
 *     checked without a model, and it is flagged as {@link KitCode#UNASSOCIATED_TEMPLATE};</li>
 * <li>each {@code .ixeffect} file's header, linked or not; its GLSL body is checked only when the
 *     game compiles it;</li>
 * <li>the annotations themselves: ids naming no file, malformed ids, classes that are not view models.</li>
 * </ul>
 *
 * Component files are checked through the pages that use them, and a problem found through several
 * pages is reported once. A component file a view model's {@link UiTemplate} names is that
 * component's Java half, checked against its {@link ModelShape} at each use. Pages may use the
 * library's components and the framework's values as a host offers them.
 */
public final class Lint {
    /** The framework's values with the names of their declared types; a type missing from the classpath is left out. */
    private static final Map<String, String> FRAMEWORK = Map.of(
            "$player", "net.minecraft.client.player.LocalPlayer",
            "$menu", "net.minecraft.world.inventory.AbstractContainerMenu");

    private Lint() {
    }

    /** A concrete id a view model claims. */
    private record Claim(String id, ModelShape shape) {
    }

    /**
     * Every problem found, ordered by file and position; diagnostics about an annotation are
     * positioned at its class, by binary name, line 0.
     *
     * @param translations the base language, which every {@code t} key must be in
     * @param enabled      the {@linkplain DiagCode#optIn() opt-in} checks to run
     */
    public static List<Diagnostic> run(Project project, TagRegistry tags, Translations translations,
                                       Set<? extends DiagCode> enabled) {
        List<String> templates = project.templates();
        Components components = Components.scan(project, tags);
        Set<String> componentFiles = components.files();
        Map<String, UiHosting.JavaComponent> javaComponents = new HashMap<>();
        List<Claim> pages = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();
        Set<Diagnostic> out = new LinkedHashSet<>();
        for (Project.Annotated a : project.annotated()) {
            SourcePos at = new SourcePos(a.type().getName(), 0, 0);
            if (!ViewModel.class.isAssignableFrom(a.type())) {
                out.add(new Diagnostic(KitCode.NOT_A_VIEW_MODEL, a.type().getName() + " has @UiTemplate but does not extend "
                        + ViewModel.class.getSimpleName(), at, null));
                continue;
            }
            Class<? extends ViewModel> type = a.type().asSubclass(ViewModel.class);
            ModelShape shape = ModelShape.of(type);
            for (String pattern : a.patterns()) {
                int colon = pattern.indexOf(':');
                if (colon <= 0 || colon == pattern.length() - 1 || pattern.substring(0, colon).indexOf('*') >= 0) {
                    out.add(new Diagnostic(KitCode.INVALID_TEMPLATE_ID, "'" + pattern + "' is not a template id",
                            at, "use the full id 'namespace:path', such as 'examplemod:ui/screen.html'"));
                    continue;
                }
                String namespace = pattern.substring(0, colon);
                if (!project.ownsNamespace(namespace)) {
                    out.add(new Diagnostic(KitCode.UNCHECKED_TEMPLATE, "'" + pattern + "' is not checked: no resource "
                            + "root has assets/" + namespace + "/", at, null));
                    continue;
                }
                boolean wildcard = pattern.indexOf('*') >= 0;
                List<String> ids = wildcard
                        ? templates.stream().filter(id -> UiTemplate.Ids.matches(pattern, id)).toList()
                        : project.source(pattern) != null ? List.of(pattern) : List.of();
                if (ids.isEmpty()) {
                    out.add(new Diagnostic(KitCode.MISSING_TEMPLATE, wildcard
                            ? "no template matches '" + pattern + "'" : "template '" + pattern + "' not found",
                            at, "expected under assets/" + namespace + "/" + pattern.substring(colon + 1)));
                    continue;
                }
                for (String id : ids) {
                    claimed.add(id);
                    if (!componentFiles.contains(id)) {
                        pages.add(new Claim(id, shape));
                    } else if (!wildcard) {
                        javaComponents.put(id, new UiHosting.JavaComponent(type, null));
                    }
                }
            }
        }
        UiHosting hosting = new UiHosting(javaComponents,
                components.library().stream().map(Components.Library::hosted).toList(), framework(project));
        for (Claim page : pages) {
            out.addAll(lint(project, tags, translations, enabled, hosting, page.id(), page.shape()).entries());
        }
        for (String id : templates) {
            if (claimed.contains(id) || componentFiles.contains(id)) {
                continue;
            }
            for (Diagnostic d : lint(project, tags, translations, enabled, hosting, id, null).entries()) {
                if (d.code() != BindingCode.UNKNOWN_NAME) {
                    out.add(d);
                }
            }
            out.add(new Diagnostic(KitCode.UNASSOCIATED_TEMPLATE, "no view model claims '" + id
                    + "'; its bindings are not checked", new SourcePos(id, 1, 1),
                    "add @UiTemplate(\"" + id + "\") to the view model it is mounted with"));
        }
        for (String id : project.effects()) {
            Diagnostics diag = new Diagnostics();
            EffectParser.parse(project.read(id), id, diag);
            out.addAll(diag.entries());
        }
        List<Diagnostic> sorted = new ArrayList<>(out);
        sorted.sort(Comparator.comparing((Diagnostic d) -> d.pos().file())
                .thenComparingInt(d -> d.pos().line())
                .thenComparingInt(d -> d.pos().column()));
        return sorted;
    }

    /** The framework's values whose types {@code project}'s classpath has, without values. */
    private static Map<String, UiHosting.FrameworkValue<?>> framework(Project project) {
        Map<String, UiHosting.FrameworkValue<?>> out = new HashMap<>();
        FRAMEWORK.forEach((name, type) -> {
            try {
                out.put(name, new UiHosting.FrameworkValue<>(Class.forName(type, false, project.loader()), null));
            } catch (ClassNotFoundException | LinkageError e) {
                // Without the game on the classpath no template can use it either.
            }
        });
        return out;
    }

    private static Diagnostics lint(Project project, TagRegistry tags, Translations translations,
                                    Set<? extends DiagCode> enabled, UiHosting hosting, String id, ModelShape shape) {
        return UiDocument.lint(project.read(id), id, project::read, tags, translations, shape, enabled, hosting);
    }
}
