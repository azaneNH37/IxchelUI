package com.azane.ixchel.kit.docs;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.kit.schema.Schema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Finds names in hand-written docs that the library does not have: an {@code <ix-*>} or {@code <mc-*>}
 * element or component, an {@code --ix-*} token, an {@code .ix-*} class, or a code span that is a
 * diagnostic code id of a known area. These are what a rename leaves stale.
 */
public final class NameCheck {
    private static final Pattern TAG = Pattern.compile("</?((?:ix|mc)-[a-z][a-z0-9-]*)");
    private static final Pattern TOKEN = Pattern.compile("--ix-[a-z][a-z0-9-]*");
    private static final Pattern CLASS = Pattern.compile("(?<![\\w.-])\\.(ix-[a-z][a-z0-9-]*)");
    private static final Pattern CODE_SPAN = Pattern.compile("`([a-z0-9]+)/([a-z0-9-]+)`");

    /** An unknown name at {@code file:line}. */
    public record Problem(Path file, int line, String kind, String name) {
        @Override
        public String toString() {
            return file + ":" + line + ": unknown " + kind + " '" + name + "'";
        }
    }

    private final Set<String> tags = new HashSet<>();
    private final Set<String> tokens;
    private final Set<String> classes;
    private final Set<String> codes = new HashSet<>();
    private final Set<String> areas = new HashSet<>();

    public NameCheck(Schema schema, List<String> tokens, List<DiagCode> codes) {
        schema.tags().forEach(t -> tags.add(t.name()));
        schema.components().forEach(c -> tags.add(c.name()));
        this.tokens = Set.copyOf(tokens);
        this.classes = Set.copyOf(schema.classes());
        for (DiagCode c : codes) {
            this.codes.add(c.id());
            areas.add(c.id().substring(0, c.id().indexOf('/')));
        }
    }

    /**
     * The problems in every {@code .md} file under {@code root}, skipping the directory {@code skip},
     * {@code node_modules} and hidden directories such as the site's {@code .vuepress}.
     */
    public List<Problem> check(Path root, Path skip) {
        Path skipped = skip.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    boolean ignored = !dir.equals(root) && (name.startsWith(".") || name.equals("node_modules"));
                    return ignored || dir.toAbsolutePath().normalize().equals(skipped)
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".md")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            List<Problem> out = new ArrayList<>();
            for (Path f : files.stream().sorted().toList()) {
                check(f, Files.readAllLines(f, StandardCharsets.UTF_8), out);
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the docs under " + root, e);
        }
    }

    private void check(Path file, List<String> lines, List<Problem> out) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            find(TAG, 1, line, tags, file, i + 1, "element", out);
            find(TOKEN, 0, line, tokens, file, i + 1, "token", out);
            find(CLASS, 1, line, classes, file, i + 1, "class", out);
            Matcher m = CODE_SPAN.matcher(line);
            while (m.find()) {
                String id = m.group(1) + "/" + m.group(2);
                if (areas.contains(m.group(1)) && !codes.contains(id)) {
                    out.add(new Problem(file, i + 1, "diagnostic code", id));
                }
            }
        }
    }

    private static void find(Pattern p, int group, String line, Set<String> known, Path file, int n, String kind,
                             List<Problem> out) {
        Matcher m = p.matcher(line);
        while (m.find()) {
            if (!known.contains(m.group(group))) {
                out.add(new Problem(file, n, kind, m.group(group)));
            }
        }
    }
}
