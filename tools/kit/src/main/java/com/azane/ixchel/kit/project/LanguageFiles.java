package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.template.Translations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The base language ({@code en_us}) as the game would merge it: {@code assets/<namespace>/lang/en_us.json}
 * of every namespace under the resource roots, then of the classpath's directories and jars (other
 * mods, Minecraft); the first file holding a key wins.
 */
public final class LanguageFiles {
    /** The language the game falls back to and the lint checks translations in. */
    public static final String BASE = "en_us";
    private static final Pattern ENTRY = Pattern.compile("assets/[^/]+/lang/" + BASE + "\\.json");

    private LanguageFiles() {
    }

    /** @throws UncheckedIOException if a language file cannot be read or is not a JSON object of strings */
    public static Translations base(List<Path> resources, List<Path> classpath) {
        Map<String, String> entries = new HashMap<>();
        for (Path root : resources) {
            directory(root, entries);
        }
        for (Path entry : classpath) {
            if (Files.isDirectory(entry)) {
                directory(entry, entries);
            } else if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".jar")) {
                jar(entry, entries);
            }
        }
        return new Translations() {
            @Override
            public String language() {
                return BASE.replace('_', '-');
            }

            @Override
            public boolean base() {
                return true;
            }

            @Override
            public String get(String key) {
                return entries.get(key);
            }
        };
    }

    private static void directory(Path root, Map<String, String> entries) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(assets)) {
            for (Path ns : namespaces.sorted().toList()) {
                Path file = ns.resolve("lang").resolve(BASE + ".json");
                if (Files.isRegularFile(file)) {
                    parse(Files.readString(file, StandardCharsets.UTF_8), file.toString(), entries);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the language files under " + assets + ": " + e.getMessage(), e);
        }
    }

    private static void jar(Path jar, Map<String, String> entries) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                if (ENTRY.matcher(e.getName()).matches()) {
                    try (InputStream in = zip.getInputStream(e)) {
                        parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), jar + "!/" + e.getName(), entries);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + jar + ": " + e.getMessage(), e);
        }
    }

    /** Adds the keys of a flat JSON object of strings that {@code entries} lacks. */
    static void parse(String json, String file, Map<String, String> entries) {
        Reader r = new Reader(json, file);
        r.skip();
        r.expect('{');
        r.skip();
        if (r.peek() == '}') {
            return;
        }
        while (true) {
            r.skip();
            String key = r.string();
            r.skip();
            r.expect(':');
            r.skip();
            entries.putIfAbsent(key, r.string());
            r.skip();
            if (r.peek() == ',') {
                r.i++;
                continue;
            }
            r.expect('}');
            return;
        }
    }

    private static final class Reader {
        private final String s;
        private final String file;
        private int i;

        Reader(String s, String file) {
            this.s = s.startsWith("\uFEFF") ? s.substring(1) : s;
            this.file = file;
        }

        char peek() {
            if (i >= s.length()) {
                throw fail("unexpected end");
            }
            return s.charAt(i);
        }

        void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        void expect(char c) {
            if (peek() != c) {
                throw fail("expected '" + c + "'");
            }
            i++;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = peek();
                i++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"', '\\', '/' -> sb.append(e);
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw fail("unexpected end");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException x) {
                            throw fail("invalid \\u escape");
                        }
                        i += 4;
                    }
                    default -> throw fail("invalid escape '\\" + e + "'");
                }
            }
        }

        UncheckedIOException fail(String what) {
            String message = file + ": not a JSON object of strings: " + what + " at offset " + i;
            return new UncheckedIOException(message, new IOException(message));
        }
    }
}
