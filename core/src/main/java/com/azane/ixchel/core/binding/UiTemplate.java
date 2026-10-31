package com.azane.ixchel.core.binding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The templates a view model serves, as full ids {@code namespace:path}. A {@code *} in the path
 * matches any characters but {@code /}; the namespace is always literal. The mount API takes the
 * template from here when the annotation names exactly one concrete id, and the lint checks every
 * listed template against this class's {@link ModelShape}.
 *
 * <pre>{@code
 * @UiTemplate("sample:ui/quests/quests.html")
 * class QuestsView extends ViewModel { ... }
 * mc.setScreen(new UiScreen(new QuestsView()));
 *
 * @UiTemplate("sample:ui/guide/entries/*.html")
 * class GuideView extends ViewModel { ... }
 * mc.setScreen(new UiScreen(new ResourceLocation("sample", "ui/guide/entries/" + page + ".html"), view));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UiTemplate {
    String[] value();

    /** Questions about a class's {@link UiTemplate}; not inherited, each class declares its own. */
    final class Ids {
        private Ids() {
        }

        /**
         * The one concrete template {@code type} declares.
         *
         * @throws IllegalArgumentException if {@code type} has no annotation, or it lists several ids or a pattern
         */
        public static String single(Class<?> type) {
            UiTemplate t = type.getAnnotation(UiTemplate.class);
            if (t == null || t.value().length != 1 || t.value()[0].indexOf('*') >= 0) {
                throw new IllegalArgumentException(type.getName() + " must declare exactly one concrete template "
                        + "with @UiTemplate to be mounted without an id; pass the template id instead");
            }
            return t.value()[0];
        }

        /** Whether {@code type}'s annotation lists or matches {@code id}; false without an annotation. */
        public static boolean covers(Class<?> type, String id) {
            UiTemplate t = type.getAnnotation(UiTemplate.class);
            if (t == null) {
                return false;
            }
            for (String pattern : t.value()) {
                if (matches(pattern, id)) {
                    return true;
                }
            }
            return false;
        }

        /** Whether {@code id} matches {@code pattern}: same namespace, and each {@code *} of the path matches a run without {@code /}. */
        public static boolean matches(String pattern, String id) {
            return matches(pattern, 0, id, 0);
        }

        private static boolean matches(String p, int i, String s, int j) {
            while (i < p.length()) {
                char c = p.charAt(i);
                if (c == '*') {
                    for (int k = j; ; k++) {
                        if (matches(p, i + 1, s, k)) {
                            return true;
                        }
                        if (k >= s.length() || s.charAt(k) == '/' || s.charAt(k) == ':') {
                            return false;
                        }
                    }
                }
                if (j >= s.length() || s.charAt(j) != c) {
                    return false;
                }
                i++;
                j++;
            }
            return j == s.length();
        }
    }
}
