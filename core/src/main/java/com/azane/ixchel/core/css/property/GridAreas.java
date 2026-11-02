package com.azane.ixchel.core.css.property;

import java.util.List;

/** {@code grid-template-areas}: each named rectangle by its lines, counted from 1; empty for {@code none}. */
public record GridAreas(List<Area> areas) {
    public static final GridAreas NONE = new GridAreas(List.of());

    public record Area(String name, int rowStart, int rowEnd, int columnStart, int columnEnd) {
    }
}
