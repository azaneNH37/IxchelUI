package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of a declaration's property name or value; the declaration is dropped. */
public enum PropertyCode implements DiagCode {
    UNKNOWN_PROPERTY("css/unknown-property", Severity.ERROR,
            "A declaration or transition names a property that is not supported."),
    MISSING_VALUE("css/missing-value", Severity.ERROR,
            "A declaration has no value after the ':'."),
    INVALID_VALUE("css/invalid-value", Severity.ERROR,
            "A value is not of a kind the property accepts, or is malformed."),
    UNSUPPORTED_VALUE("css/unsupported-value", Severity.ERROR,
            "A value uses a CSS feature the property does not support here, such as cover, several background layers or a named color outside the supported few."),
    VALUE_COUNT("css/value-count", Severity.ERROR,
            "A property is given more or fewer values than it takes."),
    REPEATED_COMPONENT("css/repeated-component", Severity.ERROR,
            "A shorthand gives the same component, such as the width of a border or the easing of a transition, more than once."),
    NONE_NOT_ALONE("css/none-not-alone", Severity.ERROR,
            "'none' is combined with other items in a list where it must stand alone."),
    EMPTY_LIST_ITEM("css/empty-list-item", Severity.ERROR,
            "A comma-separated list has an empty item before or after a ','."),
    NEGATIVE_VALUE("css/negative-value", Severity.ERROR,
            "A negative number, length or time is given to a property that does not allow it."),
    MISSING_UNIT("css/missing-unit", Severity.ERROR,
            "A number that needs a unit, such as a non-zero length or a zero time, is written without one."),
    UNSUPPORTED_UNIT("css/unsupported-unit", Severity.ERROR,
            "A length uses a unit other than px."),
    FUNCTION_ARGUMENTS("css/function-arguments", Severity.ERROR,
            "A CSS function such as url(), rgb(), cubic-bezier() or a transform function is given the wrong number or kind of arguments."),
    INVALID_RESOURCE_LOCATION("css/invalid-resource-location", Severity.ERROR,
            "An image url() is not a valid Minecraft resource location."),
    NOT_ANIMATABLE("css/not-animatable", Severity.ERROR,
            "A transition names a property that cannot be animated.");

    private final String id;
    private final Severity severity;
    private final String summary;

    PropertyCode(String id, Severity severity, String summary) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String summary() {
        return summary;
    }
}
