package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Problems of directives, blocks, component instances and the view model they bind to. */
public enum BindingCode implements DiagCode {
    MODEL_RESERVED_NAME("model/reserved-name", Severity.ERROR,
            "A view model field name starts with '$', which belongs to the framework."),
    MODEL_UNDECLARED_WRITABLE("model/undeclared-writable", Severity.WARNING,
            "A field declared as a read-only cell holds a Value; templates cannot write it unless its declared type is Value."),
    MODEL_NULL_FIELD("model/null-field", Severity.ERROR,
            "A view model field the template uses is null when the template binds."),

    UNKNOWN_NAME("binding/unknown-name", Severity.ERROR,
            "A name is not a member of the view model, nor a parameter, event, row or state visible here."),
    UNAVAILABLE_NAME("binding/unavailable-name", Severity.ERROR,
            "A framework name such as $event is used where it is not defined."),
    NOT_A_VALUE("binding/not-a-value", Severity.ERROR,
            "An action or a component event is used as a value; of an action only pending and error are values."),
    UNKNOWN_MEMBER("binding/unknown-member", Severity.ERROR,
            "A path names a member its value does not have: a missing record component, or anything but size of a List."),
    INVALID_NAME("binding/invalid-name", Severity.ERROR,
            "A local state or row name is not an identifier, or starts with '$'."),
    HIDDEN_NAME("binding/hidden-name", Severity.ERROR,
            "A new local state or row name would hide a name already visible."),
    VALUE_TYPE("binding/value-type", Severity.ERROR,
            "A directive's expression has a type the directive does not take."),
    PAYLOAD_TYPE("binding/payload-type", Severity.ERROR,
            "The bound value's type is not one the native element accepts as its payload."),
    INVALID_KEY("binding/invalid-key", Severity.ERROR,
            "The key of a <for> is not a path starting at the row."),
    DUPLICATE_KEY("binding/duplicate-key", Severity.ERROR,
            "Rows of a <for> share a key; the later rows with it are skipped."),
    UNKNOWN_TARGET("binding/unknown-target", Severity.ERROR,
            "A bind: directive names something the element cannot bind."),
    INCOMPLETE_TARGET("binding/incomplete-target", Severity.ERROR,
            "bind:class or bind:style lacks the class or property name that follows it."),
    INVALID_CLASS_NAME("binding/invalid-class-name", Severity.ERROR,
            "bind:class:<name> names a class that is not a valid class name."),
    TEXT_NOT_EMPTY("binding/text-not-empty", Severity.ERROR,
            "An element with bind:text has content of its own, which the bound text would replace."),
    UNKNOWN_STYLE_PROPERTY("binding/unknown-style-property", Severity.ERROR,
            "bind:style:<property> names a property that is not supported."),
    SHORTHAND_STYLE("binding/shorthand-style", Severity.ERROR,
            "bind:style:<property> names a shorthand; only longhands and custom properties can be bound."),
    INVALID_STYLE_VALUE("binding/invalid-style-value", Severity.ERROR,
            "A bound style value is not a valid value of its property."),
    TWO_WAY_TARGET("binding/two-way-target", Severity.ERROR,
            "A two-way binding's expression is not a writable state of the type the binding writes back."),
    UNKNOWN_EVENT("binding/unknown-event", Severity.ERROR,
            "An on: directive names an event that does not exist."),
    UNEXPECTED_FILTER("binding/unexpected-filter", Severity.ERROR,
            "An on: directive gives a filter to an event that takes none."),
    UNKNOWN_FILTER("binding/unknown-filter", Severity.ERROR,
            "An event filter names an unknown key, button or modifier."),
    NOT_CALLABLE("binding/not-callable", Severity.ERROR,
            "A statement calls a name that is neither an Action field nor, in a component, one of its events."),
    ARGUMENT_ARITY("binding/argument-arity", Severity.ERROR,
            "An action is called without the argument it takes, or with one it does not take."),
    ARGUMENT_TYPE("binding/argument-type", Severity.ERROR,
            "An action's argument has a type the action does not take."),
    EVENT_PAYLOAD("binding/event-payload", Severity.ERROR,
            "A component raises one of its events with different payload types in different places, or with one its Event field does not declare."),
    NOT_ASSIGNABLE("binding/not-assignable", Severity.ERROR,
            "An assignment's target is not writable."),
    ASSIGNMENT_TYPE("binding/assignment-type", Severity.ERROR,
            "An assigned value's type does not fit the type of its target."),
    COMPONENT_TEXT("binding/component-text", Severity.ERROR,
            "bind:text is used on a component instance, whose content is the component's template."),
    DUPLICATE_PARAMETER("binding/duplicate-parameter", Severity.ERROR,
            "A component parameter is given both as a plain attribute and with bind:."),
    UNKNOWN_SLOT("binding/unknown-slot", Severity.ERROR,
            "Content of a component instance goes to a slot the component does not have."),
    MISSING_PARAMETER("binding/missing-parameter", Severity.ERROR,
            "A component instance omits a parameter that has no default."),
    SIGNATURE_MISMATCH("component/signature-mismatch", Severity.ERROR,
            "The parameters and events of a Java component's <template> are not exactly the ports of its view model."),
    PARAM_TYPE("component/param-type", Severity.ERROR,
            "A value given to a Java component's port does not fit the port's declared type."),
    READ_ONLY_TWO_WAY("component/read-only-two-way", Severity.ERROR,
            "A Java component's TwoWay port is given something that is not a writable path."),
    UNBOUND_PORT("component/unbound-port", Severity.ERROR,
            "A Java component's model made a port that no visible port field holds."),
    RUNTIME_EXCEPTION("binding/runtime-exception", Severity.ERROR,
            "Evaluating a bound expression, running a handler, or building or closing a component's model threw an exception.");

    private final String id;
    private final Severity severity;
    private final String summary;

    BindingCode(String id, Severity severity, String summary) {
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
