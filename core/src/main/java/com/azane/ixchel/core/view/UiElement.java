package com.azane.ixchel.core.view;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an element the host layer adds to the views, on the class that implements it: the
 * painter of a drawn element, or the owner of a plain one; a
 * {@link com.azane.ixchel.core.runtime.UiRegistry} registers it. The runtime and kit read this
 * declaration without initializing the class, so the tag, attributes and payload type are written
 * once. Layout and interaction facets stay with the engine's elements.
 *
 * <pre>{@code
 * @UiElement(tag = "sample-gauge", payload = "fraction")
 * final class GaugeElement implements ElementPainter<Float> { ... }
 * }</pre>
 *
 * <p>With a {@link #payload} the class implements {@link ElementPayload}, whose type argument is the
 * payload's type; without one the element is plain flow content with {@link #attributes}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UiElement {
    /** The tag: a hyphenated name outside the HTML subset. */
    String tag();

    /** The attribute {@code bind:<payload>} hands the host the payload through; {@code ""} for none. */
    String payload() default "";

    /** Extra plain attributes. */
    String[] attributes() default {};

    /** Whether only container screens accept the element. */
    boolean containerOnly() default false;
}
