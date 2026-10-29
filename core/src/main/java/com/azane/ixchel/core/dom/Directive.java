package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;

/**
 * A template attribute interpreted by the binding layer ({@code bind:*}, {@code on:*}, {@code state:*}
 * and the structural {@code each}, {@code as}, {@code key}, {@code if}), kept verbatim.
 * {@code name} includes the prefix; {@code value} has entities decoded; {@code valuePos} is the first
 * character after the opening quote.
 */
public record Directive(String name, String value, SourcePos namePos, SourcePos valuePos) {
}
