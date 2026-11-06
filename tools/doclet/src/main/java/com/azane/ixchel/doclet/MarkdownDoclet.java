package com.azane.ixchel.doclet;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Writes one module's {@code @HostApi} types as Markdown pages of the docs site, and the module's
 * index. Options: {@code -d} the module's directory ({@code docs/java-api/<module>}), {@code -module}
 * its name, and {@code -link <rootPackage>=<module>} for it and each documented module it links to.
 */
public final class MarkdownDoclet implements Doclet {
    private Reporter reporter;
    private Path out;
    private String module;
    private final Map<String, String> roots = new LinkedHashMap<>();

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "ixchel-markdown";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of(
                option("-d", "<directory>", "The module's page directory", v -> out = Path.of(v)),
                option("-module", "<name>", "The module's name", v -> module = v),
                option("-link", "<package>=<module>", "A documented module and its root package", v -> {
                    int eq = v.indexOf('=');
                    roots.put(v.substring(0, eq), v.substring(eq + 1));
                }));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment env) {
        if (out == null || module == null) {
            reporter.print(Diagnostic.Kind.ERROR, "-d and -module are required");
            return false;
        }
        Signatures signatures = new Signatures(env.getTypeUtils());
        Site site = new Site(roots, signatures, env.getElementUtils());
        Prose prose = new Prose(env.getDocTrees(), site);
        Pages pages = new Pages(env.getDocTrees(), env.getElementUtils(), site, signatures, prose);
        String prefix = "java-api/" + module + "/";
        List<TypeElement> tops = ElementFilter.typesIn(env.getIncludedElements()).stream()
                .filter(t -> t.getNestingKind() == NestingKind.TOP_LEVEL)
                .sorted(Comparator.comparing(t -> t.getQualifiedName().toString()))
                .toList();
        for (TypeElement t : tops) {
            String page = site.page(t);
            if (page == null || !page.startsWith(prefix)) {
                reporter.print(Diagnostic.Kind.ERROR, t, "not a @HostApi type under a -link root of " + module);
                return false;
            }
        }
        try {
            for (TypeElement t : tops) {
                write(out.resolve(site.page(t).substring(prefix.length())), pages.type(t));
            }
            write(out.resolve("README.md"), pages.index(module, tops));
        } catch (UncheckedIOException e) {
            reporter.print(Diagnostic.Kind.ERROR, e.getCause().toString());
            return false;
        }
        return true;
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Option option(String name, String parameters, String description, Consumer<String> process) {
        return new Option() {
            @Override
            public int getArgumentCount() {
                return 1;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Kind getKind() {
                return Kind.STANDARD;
            }

            @Override
            public List<String> getNames() {
                return List.of(name);
            }

            @Override
            public String getParameters() {
                return parameters;
            }

            @Override
            public boolean process(String option, List<String> arguments) {
                process.accept(arguments.get(0));
                return true;
            }
        };
    }
}
