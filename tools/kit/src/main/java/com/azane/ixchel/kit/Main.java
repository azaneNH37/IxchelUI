package com.azane.ixchel.kit;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.kit.lint.CodeCatalog;
import com.azane.ixchel.kit.lint.Lint;
import com.azane.ixchel.kit.project.ElementDeclarations;
import com.azane.ixchel.kit.project.Project;
import com.azane.ixchel.kit.project.ProjectClassLoader;
import com.azane.ixchel.kit.schema.Schema;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code ixchel-kit} command line:
 *
 * <pre>
 * lint --resources &lt;dir&gt;... --classpath &lt;entry&gt;... [--json]
 * schema [--resources &lt;dir&gt;...] [--classpath &lt;entry&gt;...] [--json]
 * explain &lt;code-id&gt; [--classpath &lt;entry&gt;...]
 * </pre>
 *
 * A resource directory holds {@code assets/<namespace>/…}, the sources that diagnostics point at.
 * The classpath is the project's runtime classpath: its directories are the project's own output,
 * scanned for view models, and its jars are everything else (Minecraft, the Ixchel mc layer). All
 * of it loads through a {@link ProjectClassLoader}. Exit status: 0 success, 1 the lint found
 * errors, 2 bad usage or a failure to read the project.
 */
public final class Main {
    private static final String USAGE = """
            usage:
              lint --resources <dir>... --classpath <entry>... [--json]
                  checks every template under <dir>/assets/<namespace>/; exits 1 on errors
              schema [--resources <dir>...] [--classpath <entry>...] [--json]
                  tags, library stylesheets, CSS properties, components and view model members
              explain <code-id> [--classpath <entry>...]
                  describes a diagnostic code such as binding/unknown-name
            Every option takes several paths, separate or joined with the platform's path separator.
            --classpath takes the project's runtime classpath. Its directories are the project's
            compiled output, where view models are looked for; its jars supply their field types
            and the Ixchel mc layer's native element declarations. Pass it as an argument, not as
            the kit's own classpath: the kit reads it through a loader that skips jar signatures,
            which a patched development Minecraft jar fails.""";

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    /** Parsed arguments: the command, its positionals and the paths of each option. */
    private record Args(String command, List<String> positional, List<Path> resources, List<Path> classpath,
                        boolean json) {
        static Args parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("no command");
            }
            List<String> positional = new ArrayList<>();
            List<Path> resources = new ArrayList<>();
            List<Path> classpath = new ArrayList<>();
            boolean json = false;
            List<Path> target = null;
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--resources" -> target = resources;
                    case "--classpath" -> target = classpath;
                    case "--json" -> {
                        json = true;
                        target = null;
                    }
                    default -> {
                        if (a.startsWith("--")) {
                            throw new IllegalArgumentException("unknown option " + a);
                        }
                        if (target != null) {
                            // A value may join several paths with the platform's path separator, as a classpath does.
                            for (String p : a.split(Pattern.quote(File.pathSeparator))) {
                                if (!p.isEmpty()) {
                                    target.add(Path.of(p));
                                }
                            }
                        } else {
                            positional.add(a);
                        }
                    }
                }
            }
            return new Args(args[0], positional, resources, classpath, json);
        }

        /** The project's own compiled output: the directories of its classpath. */
        List<Path> classes() {
            return classpath.stream().filter(Files::isDirectory).toList();
        }
    }

    private static int run(String[] argv, PrintStream out, PrintStream err) {
        Args args;
        try {
            args = Args.parse(argv);
            switch (args.command()) {
                case "lint" -> {
                    if (args.resources().isEmpty() || args.classpath().isEmpty() || !args.positional().isEmpty()) {
                        throw new IllegalArgumentException("lint takes --resources <dir>... and --classpath <entry>...");
                    }
                }
                case "schema" -> {
                    if (!args.positional().isEmpty()) {
                        throw new IllegalArgumentException("schema takes no positional arguments");
                    }
                }
                case "explain" -> {
                    if (args.positional().size() != 1) {
                        throw new IllegalArgumentException("explain takes one code id");
                    }
                }
                default -> throw new IllegalArgumentException("unknown command " + args.command());
            }
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 2;
        }
        try (ProjectClassLoader loader = new ProjectClassLoader(args.classpath(), Main.class.getClassLoader())) {
            Project project = new Project(args.resources(), args.classes(), loader);
            return switch (args.command()) {
                case "lint" -> lint(project, args.json(), out);
                case "schema" -> schema(project, args.json(), out);
                default -> explain(loader, args.positional().get(0), out, err);
            };
        } catch (LinkageError | TypeNotPresentException e) {
            // A view model's field type or supertype is not on the given classpath.
            err.println("ixchel-kit: cannot load a class the project needs (" + e + "); pass the project's runtime "
                    + "classpath with --classpath");
            return 2;
        } catch (RuntimeException e) {
            err.println("ixchel-kit: " + e.getMessage());
            return 2;
        }
    }

    private static int lint(Project project, boolean json, PrintStream out) {
        ElementDeclarations elements = ElementDeclarations.load(project.loader());
        List<Diagnostic> found = Lint.run(project, elements.tags());
        long errors = found.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
        if (json) {
            List<Object> list = new ArrayList<>();
            for (Diagnostic d : found) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("code", d.code().id());
                o.put("severity", severity(d.severity()));
                o.put("file", file(project, d.pos().file()));
                o.put("line", d.pos().line());
                o.put("col", d.pos().column());
                o.put("message", d.message());
                o.put("hint", d.hint());
                list.add(o);
            }
            out.println(Json.write(list));
        } else {
            for (Diagnostic d : found) {
                String at = file(project, d.pos().file());
                if (d.pos().line() > 0) {
                    at += ":" + d.pos().line() + ":" + d.pos().column();
                }
                out.println(at + ": " + severity(d.severity()) + "[" + d.code().id() + "]: " + d.message());
                if (d.hint() != null) {
                    out.println("  hint: " + d.hint());
                }
            }
            out.println(errors + (errors == 1 ? " error, " : " errors, ") + (found.size() - errors)
                    + (found.size() - errors == 1 ? " warning" : " warnings"));
        }
        return errors > 0 ? 1 : 0;
    }

    private static int schema(Project project, boolean json, PrintStream out) {
        Schema schema = Schema.of(project, ElementDeclarations.load(project.loader()));
        out.print(json ? Json.write(schema.json()) + "\n" : schema.text());
        return 0;
    }

    private static int explain(ClassLoader loader, String id, PrintStream out, PrintStream err) {
        DiagCode code = CodeCatalog.find(loader, id);
        if (code == null) {
            err.println("ixchel-kit: no diagnostic code '" + id + "'");
            return 2;
        }
        out.println(code.id() + " (" + severity(code.severity()) + ")");
        out.println("  " + code.summary());
        return 0;
    }

    private static String severity(Diagnostic.Severity s) {
        return s == Diagnostic.Severity.ERROR ? "error" : "warning";
    }

    /**
     * The path to show for a diagnostic's file: a file id under a resource root as its path
     * relative to the working directory, anything else (a class name) as it is.
     */
    private static String file(Project project, String file) {
        Path source = project.source(file);
        if (source == null) {
            return file;
        }
        Path abs = source.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path shown = abs.getRoot() != null && abs.getRoot().equals(cwd.getRoot()) ? cwd.relativize(abs) : abs;
        return shown.toString().replace('\\', '/');
    }
}
