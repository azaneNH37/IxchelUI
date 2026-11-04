package com.azane.ixchel.core.view;

/**
 * The translated texts of {@code t="key"} and {@code t:<attribute>="key"}: the host's language files
 * in the current language. Read once when a document loads; a language change reloads the document.
 *
 * <p>A translation is text in which {@code %s} and {@code %n$s} insert the message element's argument
 * children, {@code <name>…</name>} a copy of its {@code mark="name"} child with the enclosed text,
 * {@code \n} breaks the line and {@code %%} is a {@code %}. In the {@linkplain #base() base language}
 * every problem with a translation is an error and every mark and argument must be used; in any other
 * a problem is a warning and the text is shown as far as it can be.
 */
public interface Translations {
    /** No language files: every key is missing. */
    Translations NONE = new Translations() {
        @Override
        public String language() {
            return null;
        }

        @Override
        public boolean base() {
            return true;
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    /** The language as a language tag such as {@code en-us}, set as the document root's {@code lang}; {@code null} for none. */
    String language();

    /** Whether this is the language the original texts are written in. */
    boolean base();

    /** The translation of {@code key}, or {@code null} when there is none. */
    String get(String key);
}
