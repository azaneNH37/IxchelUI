package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;

/**
 * A {@code bind:*} template attribute, kept verbatim for the binding layer. {@code name} includes the
 * {@code bind:} prefix; {@code value} has entities decoded; {@code valuePos} is the first character
 * after the opening quote.
 */
public record BindAttribute(String name, String value, SourcePos namePos, SourcePos valuePos) {
}
