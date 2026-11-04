package com.azane.ixchel.core.template;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an element the host layer adds to the templates, on the class that implements it: the
 * adapter's painter of a native element, or the owner of a plain one.
 * The runtime and kit read this declaration without initializing the class, so the tag, attributes
 * and payload type are written once. Layout and interaction facets are library-internal contracts.
 *
 * <pre>{@code
 * @NativeElement(tag = "sample-gauge", payload = "fraction")
 * final class GaugeElement implements NativePainter<Float> { ... }
 * }</pre>
 *
 * <p>With a {@link #payload} the class implements {@link NativePayload}, whose type argument is the
 * payload's type; without one the element is plain flow content with {@link #attributes}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NativeElement {
    /** The tag: a hyphenated name outside the HTML subset. */
    String tag();

    /** The attribute {@code bind:<payload>} hands the host the payload through; {@code ""} for none. */
    String payload() default "";

    /** Extra plain attributes. */
    String[] attributes() default {};

    /** Whether only container screens accept the element; read by tools, not by the runtime. */
    boolean containerOnly() default false;
}
