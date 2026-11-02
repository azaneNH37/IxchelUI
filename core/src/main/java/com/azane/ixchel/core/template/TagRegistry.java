package com.azane.ixchel.core.template;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tags a template may use and the plain attributes each accepts. Immutable; the mc layer adds its
 * native elements with {@link #extend()}. Tags outside the HTML subset carry a hyphen, and the
 * library keeps the prefixes {@code ix-} and {@code mc-} for its own.
 *
 * <p>Every tag except {@code <style>} and {@code <link>} accepts {@link #GLOBAL_ATTRIBUTES};
 * {@code <link>} takes only {@code rel} and {@code href}. {@code bind:*} attributes are accepted on
 * any known element without being registered.
 *
 * <p>A native element is drawn by the adapter layer, not the core: it paints as a
 * {@link com.azane.ixchel.core.paint.DrawCommand.Custom} carrying its payload, an adapter object that
 * only a binding can set ({@code <mc-item bind:item="gun"></mc-item>}). A native element with
 * {@linkplain #hasLineContent line content} is not drawn by the host: the host fills it with one
 * child element per line, laid out and painted as ordinary content.
 */
public final class TagRegistry {
    /**
     * Attributes every element except {@code <style>} and {@code <link>} accepts; {@code tabindex} makes
     * an element focusable, {@code disabled} (a boolean attribute) blocks mouse buttons and focus inside
     * it, {@code title} is tooltip text for the host to show, {@code lang} is the language
     * {@code :lang()} matches, {@code translate="no"} marks content that is not translated,
     * {@code mark} names a mark of the parent's translation, and {@code draggable="true"} lets the left
     * button drag the element.
     */
    public static final Set<String> GLOBAL_ATTRIBUTES = Set.of("class", "id", "style", "tabindex", "disabled", "title",
            "lang", "translate", "mark", "draggable");

    /** The only attributes of {@code <link rel="stylesheet" href="…"/>}. */
    public static final Set<String> LINK_ATTRIBUTES = Set.of("rel", "href");

    /**
     * The HTML subset core understands: {@code div}, {@code span}, {@code a} (no {@code href}: a link is
     * an {@code on:click} handler), {@code img src}, {@code input type
     * placeholder maxlength min max step} (type {@code text}, {@code number}, {@code checkbox} or
     * {@code range}; its value is {@code bind:value}, a checkbox's state {@code bind:checked}),
     * {@code select} of {@code option value} (its value is {@code bind:value}), {@code button},
     * {@code dialog} (open while {@code bind:open}), {@code style}, {@code link rel href}, and the
     * library's {@code ix-tooltip} (its parent's tooltip, shown by the host) and {@code ix-drag-preview}
     * (shown at the pointer while its parent is dragged).
     */
    public static final TagRegistry HTML = new Builder(Map.of(), Map.of(), Map.of(), Set.of())
            .tag("div")
            .tag("span")
            .tag("a")
            .tag("img", "src")
            .tag("input", "type", "placeholder", "maxlength", "min", "max", "step")
            .tag("select")
            .tag("option", "value")
            .tag("button")
            .tag("dialog")
            .tag("ix-tooltip")
            .tag("ix-drag-preview")
            .tag("style")
            .tag("link")
            .build();

    private final Map<String, Set<String>> attributes;
    private final Map<String, String> payloads;
    private final Map<String, List<String>> payloadTypes;
    private final Set<String> lineContent;

    private TagRegistry(Map<String, Set<String>> attributes, Map<String, String> payloads,
                        Map<String, List<String>> payloadTypes, Set<String> lineContent) {
        this.attributes = attributes;
        this.payloads = payloads;
        this.payloadTypes = payloadTypes;
        this.lineContent = lineContent;
    }

    /** A builder starting from this registry's tags and attributes. */
    public Builder extend() {
        return new Builder(attributes, payloads, payloadTypes, lineContent);
    }

    public boolean isKnownTag(String tag) {
        return attributes.containsKey(tag);
    }

    /** Whether {@code attribute} is a plain attribute of the known tag {@code tag}. */
    public boolean isAllowedAttribute(String tag, String attribute) {
        if (tag.equals("style")) {
            return false;
        }
        if (tag.equals("link")) {
            return LINK_ATTRIBUTES.contains(attribute);
        }
        return GLOBAL_ATTRIBUTES.contains(attribute) || attributes.get(tag).contains(attribute);
    }

    /** The payload attribute of native element {@code tag}, or {@code null} for an ordinary tag. */
    public String payloadAttribute(String tag) {
        return payloads.get(tag);
    }

    /** The types a native element's payload may have, as declared; empty when unchecked or for an ordinary tag. */
    public List<String> payloadTypes(String tag) {
        return payloadTypes.getOrDefault(tag, List.of());
    }

    /**
     * Whether the native element {@code tag} is filled by the host with one child element per line
     * (declared {@code content=lines}) instead of being drawn by it.
     */
    public boolean hasLineContent(String tag) {
        return lineContent.contains(tag);
    }

    /** Whether {@code tag} is a native element the host draws: its box is its own, its children are not laid out. */
    public boolean isReplaced(String tag) {
        return payloads.containsKey(tag) && !lineContent.contains(tag);
    }

    /** The registered tags. */
    public Set<String> tags() {
        return attributes.keySet();
    }

    /** The extra plain attributes registered for the known tag {@code tag}, without the global ones. */
    public Set<String> attributes(String tag) {
        return attributes.get(tag);
    }

    /** Collects tags; {@link #build()} produces an immutable registry. */
    public static final class Builder {
        private final Map<String, Set<String>> attributes = new LinkedHashMap<>();
        private final Map<String, String> payloads = new LinkedHashMap<>();
        private final Map<String, List<String>> payloadTypes = new LinkedHashMap<>();
        private final Set<String> lineContent = new LinkedHashSet<>();

        private Builder(Map<String, Set<String>> from, Map<String, String> fromPayloads,
                        Map<String, List<String>> fromPayloadTypes, Set<String> fromLineContent) {
            from.forEach((tag, attrs) -> attributes.put(tag, new LinkedHashSet<>(attrs)));
            payloads.putAll(fromPayloads);
            payloadTypes.putAll(fromPayloadTypes);
            lineContent.addAll(fromLineContent);
        }

        /**
         * Registers {@code tag} with extra plain attributes, or adds the attributes to an already
         * registered tag.
         *
         * @throws IllegalArgumentException if a name is malformed, an attribute is global, a
         *         {@code bind:} attribute or reserved, or attributes are given for {@code style} or
         *         {@code link}
         */
        public Builder tag(String tag, String... extraAttributes) {
            if (!isName(tag, false)) {
                throw new IllegalArgumentException("malformed tag name '" + tag + "'");
            }
            if ((tag.equals("style") || tag.equals("link")) && extraAttributes.length > 0) {
                throw new IllegalArgumentException("<" + tag + "> takes no extra attributes");
            }
            Set<String> set = attributes.computeIfAbsent(tag, k -> new LinkedHashSet<>());
            for (String a : extraAttributes) {
                if (!isName(a, true)) {
                    throw new IllegalArgumentException("malformed attribute name '" + a + "'");
                }
                if (GLOBAL_ATTRIBUTES.contains(a) || TemplateParser.isDirective(a) || TemplateParser.isTranslation(a)) {
                    throw new IllegalArgumentException("attribute '" + a + "' cannot be registered on <" + tag + ">");
                }
                set.add(a);
            }
            return this;
        }

        /**
         * Registers a native element drawn by the host. {@code payload} names the attribute whose
         * bound value is handed to the host painter; it can only be set with {@code bind:<payload>}.
         *
         * @throws IllegalArgumentException as {@link #tag}, or if {@code tag} is already registered
         *         or {@code payload} is malformed, global or also a plain attribute
         */
        public Builder nativeTag(String tag, String payload, String... extraAttributes) {
            if (attributes.containsKey(tag)) {
                throw new IllegalArgumentException("<" + tag + "> is already registered");
            }
            if (!isName(payload, false) || GLOBAL_ATTRIBUTES.contains(payload)
                    || TemplateParser.isTranslation(payload) || TemplateParser.isDirective(payload)
                    || Set.of(extraAttributes).contains(payload)) {
                throw new IllegalArgumentException("'" + payload + "' cannot be the payload of <" + tag + ">");
            }
            tag(tag, extraAttributes);
            payloads.put(tag, payload);
            return this;
        }

        /**
         * Registers the tags of a declaration file, one per line; blank lines and lines starting with
         * {@code #} are skipped. A line is a tag followed by {@code key=value} fields:
         *
         * <pre>{@code
         * # payload: the attribute bind:<payload> sets; accepts: the payload's types (binary names,
         * # one type argument allowed); attrs: extra plain attributes; content=lines: the host fills
         * # the native element with one child element per line instead of drawing it
         * mc-item payload=item accepts=net.minecraft.world.item.ItemStack,java.util.function.Supplier<net.minecraft.world.item.ItemStack>
         * mc-slot attrs=index
         * }</pre>
         *
         * A line with {@code payload} registers a native element as {@link #nativeTag} does.
         *
         * @param file names the file in messages
         * @throws IllegalArgumentException for an unknown field or as {@link #tag} and {@link #nativeTag}
         */
        public Builder declare(String text, String file) {
            String[] lines = text.split("\r?\n");
            for (int n = 0; n < lines.length; n++) {
                String line = lines[n].strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\s+");
                String payload = null;
                List<String> accepts = List.of();
                String[] attrs = new String[0];
                boolean lineFilled = false;
                for (int k = 1; k < fields.length; k++) {
                    int eq = fields[k].indexOf('=');
                    String key = eq < 0 ? fields[k] : fields[k].substring(0, eq);
                    String value = eq < 0 ? "" : fields[k].substring(eq + 1);
                    switch (key) {
                        case "payload" -> payload = value;
                        case "accepts" -> accepts = List.of(value.split(","));
                        case "attrs" -> attrs = value.split(",");
                        case "content" -> {
                            if (!value.equals("lines")) {
                                throw new IllegalArgumentException(file + ":" + (n + 1) + ": content must be 'lines'");
                            }
                            lineFilled = true;
                        }
                        default -> throw new IllegalArgumentException(
                                file + ":" + (n + 1) + ": unknown field '" + key + "'");
                    }
                }
                try {
                    if (payload != null) {
                        nativeTag(fields[0], payload, attrs);
                        payloadTypes.put(fields[0], accepts);
                        if (lineFilled) {
                            lineContent.add(fields[0]);
                        }
                    } else if (!accepts.isEmpty() || lineFilled) {
                        throw new IllegalArgumentException("'" + (lineFilled ? "content" : "accepts") + "' needs a payload");
                    } else {
                        tag(fields[0], attrs);
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(file + ":" + (n + 1) + ": " + ex.getMessage(), ex);
                }
            }
            return this;
        }

        public TagRegistry build() {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            attributes.forEach((tag, attrs) -> copy.put(tag, Set.copyOf(attrs)));
            return new TagRegistry(Map.copyOf(copy), Map.copyOf(payloads), Map.copyOf(payloadTypes), Set.copyOf(lineContent));
        }

        /** Same grammar as the template parser: ASCII letter first, then letters, digits or '-'. */
        private static boolean isName(String s, boolean attribute) {
            boolean segmentStart = true;
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
                if (segmentStart) {
                    if (!letter) {
                        return false;
                    }
                    segmentStart = false;
                } else if (attribute && c == ':') {
                    segmentStart = true;
                } else if (!letter && !(c >= '0' && c <= '9') && c != '-') {
                    return false;
                }
            }
            return !segmentStart;
        }
    }
}
