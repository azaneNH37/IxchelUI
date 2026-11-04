package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.azane.ixchel.core.css.property.Syntaxes.significant;

/** Grid container and item syntaxes: track lists, areas, auto flow, placement and its shorthands. */
final class GridSyntaxes {
    private GridSyntaxes() {
    }

    /**
     * {@code grid-template-rows}/{@code -columns}: {@code none}, or tracks, {@code repeat()} and
     * {@code [line names]}. Line names inside or after an {@code auto-fill}/{@code auto-fit} repeat
     * are not supported: where they fall depends on the container's size.
     */
    static Syntax<GridTracks> view() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return GridTracks.NONE;
            }
            List<GridTracks.Entry> entries = new ArrayList<>();
            List<GridTracks.LineName> names = new ArrayList<>();
            int line = 1;
            boolean autoRepeat = false;
            for (ComponentValue v : parts) {
                if (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACKET) {
                    if (autoRepeat) {
                        diag.report(PropertyCode.UNSUPPORTED_VALUE, b.pos(), "line names after an auto-fill or auto-fit repeat are not supported");
                        return null;
                    }
                    if (!lineNames(b, line, names, diag)) {
                        return null;
                    }
                } else if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("repeat")) {
                    GridTracks.Entry e = repeat(f, line, names, diag);
                    if (e == null) {
                        return null;
                    }
                    if (e.count() < 0) {
                        if (autoRepeat) {
                            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "a track list has at most one auto-fill or auto-fit repeat");
                            return null;
                        }
                        autoRepeat = true;
                    } else {
                        line += e.count() * e.sizes().size();
                    }
                    entries.add(e);
                } else {
                    GridTracks.Size s = size(v, diag);
                    if (s == null) {
                        return null;
                    }
                    entries.add(new GridTracks.Entry(1, List.of(s)));
                    line++;
                }
            }
            if (entries.isEmpty()) {
                diag.report(PropertyCode.INVALID_VALUE, parts.isEmpty() ? pos : parts.get(0).pos(), "expected 'none' or at least one track");
                return null;
            }
            return new GridTracks(List.copyOf(entries), List.copyOf(names));
        };
    }

    /** {@code grid-auto-rows}/{@code -columns}: one or more track sizes, repeated in turn. */
    static Syntax<GridTracks> autoTracks() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.isEmpty()) {
                diag.report(PropertyCode.VALUE_COUNT, pos, "expected at least one track size");
                return null;
            }
            List<GridTracks.Entry> entries = new ArrayList<>(parts.size());
            for (ComponentValue v : parts) {
                GridTracks.Size s = size(v, diag);
                if (s == null) {
                    return null;
                }
                entries.add(new GridTracks.Entry(1, List.of(s)));
            }
            return new GridTracks(List.copyOf(entries), List.of());
        };
    }

    private static boolean lineNames(ComponentValue.Block b, int line, List<GridTracks.LineName> names, Diagnostics diag) {
        for (ComponentValue n : significant(b.contents())) {
            if (!(n instanceof Token t && t.is(Token.Kind.IDENT)) || t.isIdent("span") || t.isIdent("auto")) {
                diag.report(PropertyCode.INVALID_VALUE, n.pos(), "expected a line name");
                return false;
            }
            names.add(new GridTracks.LineName(t.value(), line));
        }
        return true;
    }

    /** {@code repeat(count | auto-fill | auto-fit, tracks)}; names in a counted repeat are added for every repetition. */
    private static GridTracks.Entry repeat(ComponentValue.Function f, int line, List<GridTracks.LineName> names, Diagnostics diag) {
        List<List<ComponentValue>> args = Syntaxes.splitCommas(f.args(), f.pos(), diag);
        if (args == null) {
            return null;
        }
        if (args.size() != 2 || args.get(0).size() != 1) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "expected repeat(count, tracks)");
            return null;
        }
        int count;
        ComponentValue c = args.get(0).get(0);
        if (c instanceof Token t && t.isIdent("auto-fill")) {
            count = GridTracks.AUTO_FILL;
        } else if (c instanceof Token t && t.isIdent("auto-fit")) {
            count = GridTracks.AUTO_FIT;
        } else if (c instanceof Token t && t.is(Token.Kind.NUMBER) && t.number() >= 1 && t.number() == Math.rint(t.number())) {
            count = (int) t.number();
        } else {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, c.pos(), "expected a positive integer, auto-fill or auto-fit");
            return null;
        }
        List<GridTracks.Size> sizes = new ArrayList<>();
        // Per repetition: names at their offset in the repeated tracks.
        List<GridTracks.LineName> inner = new ArrayList<>();
        for (ComponentValue v : args.get(1)) {
            if (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACKET) {
                if (count < 0) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, b.pos(), "line names inside an auto-fill or auto-fit repeat are not supported");
                    return null;
                }
                if (!lineNames(b, sizes.size(), inner, diag)) {
                    return null;
                }
                continue;
            }
            GridTracks.Size s = size(v, diag);
            if (s == null) {
                return null;
            }
            if (count < 0 && !isFixed(s)) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, v.pos(), "an auto-fill or auto-fit repeat takes fixed sizes: a length, or minmax() with one");
                return null;
            }
            sizes.add(s);
        }
        if (sizes.isEmpty()) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "expected repeat(count, tracks)");
            return null;
        }
        for (int k = 0; k < Math.max(count, 0); k++) {
            for (GridTracks.LineName n : inner) {
                names.add(new GridTracks.LineName(n.name(), line + k * sizes.size() + n.line()));
            }
        }
        return new GridTracks.Entry(count, List.copyOf(sizes));
    }

    private static boolean isFixed(GridTracks.Size s) {
        return s.kind() == GridTracks.Size.Kind.LENGTH
                || s.kind() == GridTracks.Size.Kind.MINMAX && (isFixed(s.min()) || isFixed(s.max()));
    }

    private static GridTracks.Size size(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.DIMENSION) && t.unit().equalsIgnoreCase("fr")) {
            if (t.number() < 0) {
                diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
                return null;
            }
            return new GridTracks.Size(GridTracks.Size.Kind.FR, null, (float) t.number(), null, null);
        }
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            switch (t.value().toLowerCase(Locale.ROOT)) {
                case "auto" -> {
                    return GridTracks.Size.AUTO;
                }
                case "min-content" -> {
                    return GridTracks.Size.MIN_CONTENT;
                }
                case "max-content" -> {
                    return GridTracks.Size.MAX_CONTENT;
                }
                default -> {
                    diag.report(PropertyCode.INVALID_VALUE, t.pos(), "expected a track size");
                    return null;
                }
            }
        }
        if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("minmax")) {
            List<List<ComponentValue>> args = Syntaxes.splitCommas(f.args(), f.pos(), diag);
            if (args == null) {
                return null;
            }
            if (args.size() != 2 || args.get(0).size() != 1 || args.get(1).size() != 1) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "expected minmax(min, max)");
                return null;
            }
            GridTracks.Size min = size(args.get(0).get(0), diag);
            if (min == null) {
                return null;
            }
            if (min.kind() == GridTracks.Size.Kind.FR || min.kind() == GridTracks.Size.Kind.MINMAX) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(0).get(0).pos(), "the minimum of minmax() cannot be fr or minmax()");
                return null;
            }
            GridTracks.Size max = size(args.get(1).get(0), diag);
            if (max == null) {
                return null;
            }
            if (max.kind() == GridTracks.Size.Kind.MINMAX) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(1).get(0).pos(), "minmax() cannot nest");
                return null;
            }
            return new GridTracks.Size(GridTracks.Size.Kind.MINMAX, null, 0, min, max);
        }
        if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("fit-content")) {
            List<ComponentValue> args = significant(f.args());
            if (args.size() != 1) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "expected fit-content(length)");
                return null;
            }
            Length l = Syntaxes.lengthOf(args.get(0), true, false, false, diag);
            return l == null ? null : new GridTracks.Size(GridTracks.Size.Kind.FIT_CONTENT, l, 0, null, null);
        }
        Length l = Syntaxes.lengthOf(v, true, false, false, diag);
        return l == null ? null : new GridTracks.Size(GridTracks.Size.Kind.LENGTH, l, 0, null, null);
    }

    /** {@code grid-template-areas}: {@code none}, or one string per row; each name must cover a rectangle. */
    static Syntax<GridAreas> areas() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return GridAreas.NONE;
            }
            if (parts.isEmpty()) {
                diag.report(PropertyCode.VALUE_COUNT, pos, "expected 'none' or a string per row");
                return null;
            }
            List<String[]> rows = new ArrayList<>(parts.size());
            for (ComponentValue v : parts) {
                if (!(v instanceof Token t && t.is(Token.Kind.STRING))) {
                    diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a string of area names");
                    return null;
                }
                String[] cells = t.value().trim().split("\\s+");
                if (cells.length == 0 || cells[0].isEmpty()) {
                    diag.report(PropertyCode.INVALID_VALUE, t.pos(), "a row has no cells");
                    return null;
                }
                if (!rows.isEmpty() && cells.length != rows.get(0).length) {
                    diag.report(PropertyCode.INVALID_VALUE, t.pos(), "every row needs " + rows.get(0).length + " cells, as the first has");
                    return null;
                }
                rows.add(cells);
            }
            // Per name: row start, row end, column start, column end (lines from 1), and the cell count.
            Map<String, int[]> found = new LinkedHashMap<>();
            for (int r = 0; r < rows.size(); r++) {
                String[] cells = rows.get(r);
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c].chars().allMatch(ch -> ch == '.')) {
                        continue;
                    }
                    int[] a = found.computeIfAbsent(cells[c], k -> new int[]{Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0, 0});
                    a[0] = Math.min(a[0], r + 1);
                    a[1] = Math.max(a[1], r + 2);
                    a[2] = Math.min(a[2], c + 1);
                    a[3] = Math.max(a[3], c + 2);
                    a[4]++;
                }
            }
            List<GridAreas.Area> areas = new ArrayList<>(found.size());
            for (Map.Entry<String, int[]> e : found.entrySet()) {
                int[] a = e.getValue();
                if ((a[1] - a[0]) * (a[3] - a[2]) != a[4]) {
                    diag.report(PropertyCode.INVALID_VALUE, parts.get(a[0] - 1).pos(), "area '" + e.getKey() + "' is not a rectangle");
                    return null;
                }
                areas.add(new GridAreas.Area(e.getKey(), a[0], a[1], a[2], a[3]));
            }
            return new GridAreas(List.copyOf(areas));
        };
    }

    /** {@code grid-auto-flow}: {@code row} or {@code column}, optionally {@code dense}. */
    static Syntax<Keywords.GridAutoFlow> autoFlow() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.isEmpty() || parts.size() > 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.size() > 2 ? parts.get(2).pos() : pos, "expected row or column, and dense");
                return null;
            }
            boolean column = false;
            boolean axis = false;
            boolean dense = false;
            for (ComponentValue v : parts) {
                if (v instanceof Token t && (t.isIdent("row") || t.isIdent("column")) && !axis) {
                    axis = true;
                    column = t.isIdent("column");
                } else if (v instanceof Token t && t.isIdent("dense") && !dense) {
                    dense = true;
                } else {
                    diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected row or column, and dense");
                    return null;
                }
            }
            return column ? dense ? Keywords.GridAutoFlow.COLUMN_DENSE : Keywords.GridAutoFlow.COLUMN
                    : dense ? Keywords.GridAutoFlow.ROW_DENSE : Keywords.GridAutoFlow.ROW;
        };
    }

    /** A placement longhand such as {@code grid-row-start}. */
    static Syntax<GridLine> line() {
        return GridSyntaxes::lineOf;
    }

    private static GridLine lineOf(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        List<ComponentValue> parts = significant(value);
        if (parts.isEmpty() || parts.size() > 3) {
            diag.report(PropertyCode.VALUE_COUNT, parts.size() > 3 ? parts.get(3).pos() : pos, "expected a grid line");
            return null;
        }
        if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("auto")) {
            return GridLine.AUTO;
        }
        boolean span = false;
        Integer number = null;
        String name = null;
        for (ComponentValue v : parts) {
            if (v instanceof Token t && t.isIdent("span") && !span) {
                span = true;
            } else if (v instanceof Token t && t.is(Token.Kind.NUMBER) && number == null && t.number() == Math.rint(t.number())) {
                number = (int) t.number();
                if (number == 0 || span && number < 0) {
                    diag.report(PropertyCode.INVALID_VALUE, t.pos(), span ? "a span must be positive" : "line 0 does not exist");
                    return null;
                }
            } else if (v instanceof Token t && t.is(Token.Kind.IDENT) && name == null && !t.isIdent("auto") && !t.isIdent("span")) {
                name = t.value();
            } else {
                diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected auto, a line number, a line name or span");
                return null;
            }
        }
        if (span) {
            if (number == null && name == null) {
                diag.report(PropertyCode.INVALID_VALUE, parts.get(0).pos(), "span needs a count or a line name");
                return null;
            }
            return name == null ? new GridLine(GridLine.Kind.SPAN, number, null)
                    : new GridLine(GridLine.Kind.NAMED_SPAN, number == null ? 1 : number, name);
        }
        if (name != null) {
            return new GridLine(GridLine.Kind.NAMED, number == null ? 1 : number, name);
        }
        return new GridLine(GridLine.Kind.LINE, number, null);
    }

    /**
     * {@code grid-row}/{@code grid-column} ({@code lines} 2: start, end) and {@code grid-area}
     * ({@code lines} 4: row start, column start, row end, column end), separated by {@code /}. An
     * omitted line is the matching start's name when that is a bare name, otherwise {@code auto}.
     */
    static Shorthand.Expander placement(int lines) {
        return (value, pos, diag) -> {
            List<List<ComponentValue>> groups = new ArrayList<>();
            List<ComponentValue> current = new ArrayList<>();
            for (ComponentValue v : significant(value)) {
                if (v instanceof Token t && t.isDelim('/')) {
                    groups.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(v);
                }
            }
            groups.add(current);
            if (groups.size() > lines) {
                diag.report(PropertyCode.VALUE_COUNT, pos, "expected at most " + lines + " lines separated by '/'");
                return null;
            }
            Object[] out = new Object[lines];
            for (int i = 0; i < groups.size(); i++) {
                out[i] = lineOf(groups.get(i), pos, diag);
                if (out[i] == null) {
                    return null;
                }
            }
            for (int i = groups.size(); i < lines; i++) {
                // grid-area: column start copies row start; row end row start; column end column start.
                GridLine from = (GridLine) out[lines == 4 && i == 3 ? 1 : 0];
                out[i] = from.kind() == GridLine.Kind.NAMED && from.number() == 1 ? from : GridLine.AUTO;
            }
            return out;
        };
    }
}
