package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.template.TemplateCode;
import com.azane.ixchel.kit.project.ClassFiles;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.LanguageFiles;
import com.azane.ixchel.kit.project.Project;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LintTest {
    /** Claims a page whose {@code <mc-item>} binds a String where the test declaration accepts Integer. */
    @UiTemplate("kitfixture:ui/claimed.html")
    static final class ClaimedView extends ViewModel {
        final Value<String> label = state("");
    }

    /** Claims a page that does not exist. */
    @UiTemplate("kitfixture:ui/missing.html")
    static final class MissingView extends ViewModel {
    }

    static Path resources() throws URISyntaxException {
        return Path.of(LintTest.class.getResource("/fixture").toURI());
    }

    static Project fixture() throws URISyntaxException {
        return new Project(List.of(resources()), List.of(ClassFiles.root(LintTest.class)), LintTest.class.getClassLoader());
    }

    private static List<String> lint(Set<? extends DiagCode> enabled) throws URISyntaxException {
        Project project = fixture();
        return Lint.run(project, ElementDeclarations.load(project.loader()).tags(),
                LanguageFiles.base(List.of(resources()), List.of()), enabled).stream().map(LintTest::at).toList();
    }

    private static String at(Diagnostic d) {
        return d.code().id() + " " + d.pos();
    }

    @Test
    void reportsTypeErrorsMissingTemplatesUnclaimedPagesAndKeysMissingFromTheBaseLanguage() throws URISyntaxException {
        // part.html is a component file, checked through claimed.html; the unclaimed page's unknown
        // name is not reported, since without a model every name is unknown.
        assertEquals(List.of(
                "model/missing-template " + MissingView.class.getName() + ":0:0",
                "binding/payload-type kitfixture:ui/claimed.html:3:23",
                "model/unassociated-template kitfixture:ui/unclaimed.html:1:1",
                "i18n/missing-translation kitfixture:ui/unclaimed.html:4:12"),
                lint(Set.of()));
    }

    @Test
    void anEnabledOptInCheckReportsAComponentFilesProblemOnce() throws URISyntaxException {
        assertEquals(List.of("i18n/literal-text kitfixture:ui/part.html:2:9"),
                lint(Set.of(TemplateCode.LITERAL_TEXT)).stream().filter(s -> s.startsWith("i18n/literal")).toList());
    }
}
