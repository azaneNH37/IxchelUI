package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.Translations;
import com.azane.ixchel.mc.paint.McImageMeasurer;
import com.azane.ixchel.mc.paint.McTextMeasurer;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads UI files from the host mod's resources ({@code assets/<hostModId>/<path>}), or from its
 * source tree when a {@link DevResources} is given.
 */
final class McResources {
    private final String hostModId;
    private final DevResources dev;

    /** @param dev the development source, or {@code null} to read packed resources */
    public McResources(String hostModId, DevResources dev) {
        this.hostModId = hostModId;
        this.dev = dev;
    }

    /**
     * A loaded document and the files it was read from.
     *
     * @param files the template and every file it links, directly or through components, under the
     *              host namespace, as paths under {@code assets/<hostModId>/} in load order; what a
     *              development watcher should watch
     */
    public record Loaded(UiDocument document, List<String> files) {
    }

    /**
     * Loads a document from a template and the files it links, measured with the vanilla font;
     * images for {@code background-size: auto} are measured from {@code resources}. A {@code <link>}
     * href of the form {@code ns:path} names that resource (the host namespace reads through the
     * development source when present, other namespaces read packed resources); an href starting
     * with {@code @ixchel/} names a stylesheet of the library, such as {@code @ixchel/vanilla.css}; any
     * other href is relative to the linking file's directory ({@code ../common.css} allowed). Error positions name
     * files as {@code modid:path}. A bound {@link Component} keeps its colors and formatting in
     * {@code bind:text} and is its plain string elsewhere. {@code t} attributes are translated into the
     * selected language, a key it lacks into {@code en_us} as the game does; the root's {@code lang} is
     * the language code with {@code -} for {@code _} ({@code zh-cn}).
     *
     * @param model   the view model {@code bind:*} attributes refer to, or {@code null}
     * @param hosting the library and the host's Java components
     */
    public Loaded load(ResourceManager resources, String templatePath, TagRegistry tags, ViewModel model, UiHosting hosting) {
        UiDocument document = UiDocument.load(read(resources, templatePath), name(templatePath), id -> {
            int colon = id.indexOf(':');
            String ns = id.substring(0, colon);
            String path = id.substring(colon + 1);
            return ns.equals(hostModId) ? read(resources, path) : readPacked(resources, new ResourceLocation(ns, path));
        }, tags, translations(), new McTextMeasurer(Minecraft.getInstance().font), new McImageMeasurer(resources), model, TEXT,
                hosting);
        String host = name("");
        List<String> files = new ArrayList<>();
        for (String id : document.files()) {
            if (id.startsWith(host)) {
                files.add(id.substring(host.length()));
            }
        }
        return new Loaded(document, List.copyOf(files));
    }

    public String read(ResourceManager resources, String path) {
        if (dev != null) {
            return dev.read(path);
        }
        return readPacked(resources, new ResourceLocation(hostModId, path));
    }

    private static String readPacked(ResourceManager resources, ResourceLocation id) {
        try (InputStream in = resources.getResourceOrThrow(id).open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + id, e);
        }
    }

    /** The game's current translations: the selected language over {@code en_us}. */
    private static Translations translations() {
        Language language = Language.getInstance();
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        String tag = selected.replace('_', '-');
        boolean base = selected.equals(Language.DEFAULT);
        return new Translations() {
            @Override
            public String language() {
                return tag;
            }

            @Override
            public boolean base() {
                return base;
            }

            @Override
            public String get(String key) {
                return language.has(key) ? language.getOrDefault(key) : null;
            }
        };
    }

    /** A {@link Component} keeps its colors and formatting in {@code bind:text}; elsewhere it is its plain string. */
    static final Bindings.TextConverter TEXT = new Bindings.TextConverter() {
        @Override
        public String plain(Object value) {
            return value instanceof Component c ? c.getString() : Bindings.PLAIN_TEXT.plain(value);
        }

        @Override
        public StyledText styled(Object value) {
            if (!(value instanceof Component c)) {
                return null;
            }
            List<StyledText.Span> spans = new ArrayList<>();
            c.visit((style, text) -> {
                if (!text.isEmpty()) {
                    spans.add(new StyledText.Span(text, color(style), flags(style)));
                }
                return Optional.empty();
            }, Style.EMPTY);
            return new StyledText(spans);
        }
    };

    private static int color(Style style) {
        TextColor c = style.getColor();
        return c == null ? 0 : 0xFF000000 | c.getValue();
    }

    private static int flags(Style style) {
        return (style.isBold() ? StyledText.BOLD : 0) | (style.isItalic() ? StyledText.ITALIC : 0)
                | (style.isUnderlined() ? StyledText.UNDERLINE : 0) | (style.isStrikethrough() ? StyledText.STRIKETHROUGH : 0)
                | (style.isObfuscated() ? StyledText.OBFUSCATED : 0);
    }

    private String name(String path) {
        return hostModId + ":" + path;
    }
}
