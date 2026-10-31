package com.azane.ixchel.core.diag;

/**
 * A stable identifier of one kind of problem an author can fix. Codes are a public contract: an id
 * is never renamed or reused, while messages may change freely. Each package that reports problems
 * declares its codes in its own enum.
 *
 * <pre>{@code
 * public enum BindingCode implements DiagCode {
 *     UNKNOWN_NAME("binding/unknown-name", Severity.ERROR, "the name is not a member of the view model");
 *     ...
 * }
 * diag.report(BindingCode.UNKNOWN_NAME, pos, "'" + name + "' is not a field of " + owner);
 * }</pre>
 */
public interface DiagCode {
    /** {@code area/kebab-name}, such as {@code binding/unknown-name}. */
    String id();

    /** The severity of every problem reported with this code. */
    Diagnostic.Severity severity();

    /** One English sentence describing the problem, for {@code --explain} and the reference docs. */
    String summary();
}
