package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.kit.project.ClassFiles;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.Project;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

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

    static Project fixture() throws URISyntaxException {
        Path resources = Path.of(LintTest.class.getResource("/fixture").toURI());
        return new Project(List.of(resources), List.of(ClassFiles.root(LintTest.class)), LintTest.class.getClassLoader());
    }

    private static String at(Diagnostic d) {
        return d.code().id() + " " + d.pos();
    }

    @Test
    void reportsTypeErrorsMissingTemplatesAndUnclaimedPages() throws URISyntaxException {
        Project project = fixture();
        List<Diagnostic> found = Lint.run(project, ElementDeclarations.load(project.loader()).tags());

        // part.html is a component file, checked through claimed.html; the unclaimed page's unknown
        // name is not reported, since without a model every name is unknown.
        assertEquals(List.of(
                "model/missing-template " + MissingView.class.getName() + ":0:0",
                "binding/payload-type kitfixture:ui/claimed.html:3:23",
                "model/unassociated-template kitfixture:ui/unclaimed.html:1:1"),
                found.stream().map(LintTest::at).toList());
    }
}
