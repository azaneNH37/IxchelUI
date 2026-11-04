package com.azane.ixchel.core.binding;

import java.util.function.Supplier;

/**
 * The view model class of a component file, the host's or the library's.
 *
 * @param factory builds the model of each instance; {@code null} for a lint
 */
public record JavaComponent(Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
}
