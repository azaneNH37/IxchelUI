package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.view.ViewCode;
import com.azane.ixchel.core.view.Translations;
import com.azane.ixchel.kit.project.ClassFiles;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.FixtureLibrary;
import com.azane.ixchel.kit.project.LanguageFiles;
import com.azane.ixchel.kit.project.Project;
import com.azane.ixchel.kit.project.ProjectClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LintTest {
    /** Claims a page whose {@code <mc-item>} binds a String where the test declaration accepts Integer. */
    @UiView("kitfixture:ui/claimed.html")
    static final class ClaimedModel extends ViewModel {
        final Value<String> label = state("");
    }

    /** Claims a page that does not exist. */
    @UiView("kitfixture:ui/missing.html")
    static final class MissingModel extends ViewModel {
    }

    /** The Java half of gauge.html, whose {@code <component>} declares {@code level}: a port by another name. */
    @UiView("kitfixture:ui/gauge.html")
    static final class GaugeModel extends ViewModel {
        final Param<Integer> amount = param();
    }

    /** Claims the page that uses {@code <kit-gauge>}. */
    @UiView("kitfixture:ui/gauge-page.html")
    static final class GaugePageModel extends ViewModel {
        final Value<Integer> count = state(0);
    }

    /** The Java half of meter.html, matching it; no page uses it. */
    @UiView("kitfixture:ui/meter.html")
    static final class MeterModel extends ViewModel {
        final Param<Integer> amount = param();
    }

    static Path resources() throws URISyntaxException {
        return Path.of(LintTest.class.getResource("/fixture").toURI());
    }

    static Project fixture() throws URISyntaxException {
        return new Project(List.of(resources()), List.of(ClassFiles.root(LintTest.class)), LintTest.class.getClassLoader());
    }

    private static List<String> lint(Set<? extends DiagCode> enabled) throws URISyntaxException {
        Project project = fixture();
        return Lint.run(project, ElementDeclarations.load(project).tags(),
                LanguageFiles.base(List.of(resources()), List.of()), enabled).stream().map(LintTest::at).toList();
    }

    private static String at(Diagnostic d) {
        return d.code().id() + " " + d.pos();
    }

    @Test
    void reportsTypeErrorsMissingTemplatesUnclaimedPagesAndKeysMissingFromTheBaseLanguage() throws URISyntaxException {
        // part.html is a component file, checked through claimed.html; gauge.html is one too, checked
        // through gauge-page.html against GaugeModel. The unclaimed page's unknown name is not
        // reported, since without a model every name is unknown. No page links unlinked.ixeffect,
        // and its header is still checked.
        assertEquals(List.of(
                "model/missing-view " + MissingModel.class.getName() + ":0:0",
                "binding/payload-type kitfixture:ui/claimed.html:4:25",
                "effect/unsupported-syntax kitfixture:ui/fx/unlinked.ixeffect:2:15",
                "component/signature-mismatch kitfixture:ui/gauge.html:1:1",
                "component/signature-mismatch kitfixture:ui/gauge.html:1:37",
                "model/unassociated-view kitfixture:ui/unclaimed.html:1:1",
                "i18n/missing-translation kitfixture:ui/unclaimed.html:5:14"),
                lint(Set.of()));
    }

    @Test
    void anEnabledOptInCheckReportsAComponentFilesProblemOnce() throws URISyntaxException {
        assertEquals(List.of("i18n/literal-text kitfixture:ui/part.html:2:9"),
                lint(Set.of(ViewCode.LITERAL_TEXT)).stream().filter(s -> s.startsWith("i18n/literal")).toList());
    }

    @Test
    void reportsAJavaComponentWhosePortsAreNotItsTemplatesParams() throws URISyntaxException {
        assertEquals(List.of(
                "component/signature-mismatch kitfixture:ui/gauge.html:1:1",
                "component/signature-mismatch kitfixture:ui/gauge.html:1:37"),
                lint(Set.of()).stream().filter(s -> s.contains("gauge")).toList());
    }

    /** meter.html would fail as a page: its root is a {@code <component>}. */
    @Test
    void aComponentFileAViewModelClaimsIsNotLintedAsAPage() throws URISyntaxException {
        assertEquals(List.of(), lint(Set.of()).stream().filter(s -> s.contains("meter")).toList());
    }

    /** No view model claims the page, so only its structure, links and tags are checked. */
    @Test
    void aPageLinkingALibraryComponentResolvesItFromTheMcLayer(@TempDir Path tmp) throws IOException {
        Path resources = tmp.resolve("resources");
        Path page = resources.resolve("assets/kitlib/ui/page.html");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "<page><link href=\"@ixchel/kitfixture/kit-gauge.html\"/>\n<div>\n"
                + "  <mc-kit-gauge bind:level=\"count\"></mc-kit-gauge>\n</div></page>\n", StandardCharsets.UTF_8);
        Path mcLayer = FixtureLibrary.mcLayer(tmp.resolve("mc"));

        try (ProjectClassLoader loader = new ProjectClassLoader(List.of(mcLayer), LintTest.class.getClassLoader())) {
            Project project = new Project(List.of(resources), List.of(), loader);
            assertEquals(List.of("model/unassociated-view kitlib:ui/page.html:1:1"),
                    Lint.run(project, ElementDeclarations.load(project).tags(), Translations.NONE, Set.of())
                            .stream().map(LintTest::at).toList());
        }
        Project withoutLibrary = new Project(List.of(resources), List.of(), LintTest.class.getClassLoader());
        assertEquals(List.of("model/unassociated-view kitlib:ui/page.html:1:1", "link/file-not-found kitlib:ui/page.html:1:7",
                        "view/unknown-tag kitlib:ui/page.html:3:3"),
                Lint.run(withoutLibrary, ElementDeclarations.load(withoutLibrary).tags(), Translations.NONE, Set.of())
                        .stream().map(LintTest::at).toList());
    }
}
