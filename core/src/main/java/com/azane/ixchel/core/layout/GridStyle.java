package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.GridAreas;
import com.azane.ixchel.core.css.property.GridLine;
import com.azane.ixchel.core.css.property.GridTracks;
import dev.vfyjxf.taffy.geometry.TaffyLine;
import dev.vfyjxf.taffy.style.GridAutoFlow;
import dev.vfyjxf.taffy.style.GridPlacement;
import dev.vfyjxf.taffy.style.GridRepetition;
import dev.vfyjxf.taffy.style.GridTemplateArea;
import dev.vfyjxf.taffy.style.GridTemplateComponent;
import dev.vfyjxf.taffy.style.NamedGridLine;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.style.TrackSizingFunction;

import java.util.ArrayList;
import java.util.List;

/** The grid container and grid item properties of a computed style, as taffy takes them. */
final class GridStyle {
    private GridStyle() {
    }

    static void apply(ComputedStyle cs, TaffyStyle s) {
        view(cs.get(CssProperties.GRID_TEMPLATE_ROWS), s.gridTemplateRows, s.gridTemplateRowsWithRepeat, s.gridTemplateRowNames);
        view(cs.get(CssProperties.GRID_TEMPLATE_COLUMNS), s.gridTemplateColumns, s.gridTemplateColumnsWithRepeat, s.gridTemplateColumnNames);
        for (GridAreas.Area a : cs.get(CssProperties.GRID_TEMPLATE_AREAS).areas()) {
            s.gridTemplateAreas.add(new GridTemplateArea(a.name(), a.rowStart(), a.rowEnd(), a.columnStart(), a.columnEnd()));
        }
        for (GridTracks.Entry e : cs.get(CssProperties.GRID_AUTO_ROWS).entries()) {
            s.gridAutoRows.add(track(e.sizes().get(0)));
        }
        for (GridTracks.Entry e : cs.get(CssProperties.GRID_AUTO_COLUMNS).entries()) {
            s.gridAutoColumns.add(track(e.sizes().get(0)));
        }
        s.gridAutoFlow = GridAutoFlow.valueOf(cs.get(CssProperties.GRID_AUTO_FLOW).name());
        s.gridRow = new TaffyLine<>(placement(cs.get(CssProperties.GRID_ROW_START)), placement(cs.get(CssProperties.GRID_ROW_END)));
        s.gridColumn = new TaffyLine<>(placement(cs.get(CssProperties.GRID_COLUMN_START)), placement(cs.get(CssProperties.GRID_COLUMN_END)));
    }

    /**
     * Taffy takes the view with an auto repeat in {@code withRepeat}, yet still counts the explicit
     * tracks from {@code plain}; so {@code plain} always holds the counted tracks, expanded, and
     * {@code withRepeat} is filled only when there is an auto repeat.
     */
    private static void view(GridTracks t, List<TrackSizingFunction> plain, List<GridTemplateComponent> withRepeat,
                                 List<NamedGridLine> names) {
        boolean auto = false;
        for (GridTracks.Entry e : t.entries()) {
            auto |= e.count() < 0;
        }
        for (GridTracks.Entry e : t.entries()) {
            List<TrackSizingFunction> tracks = new ArrayList<>(e.sizes().size());
            for (GridTracks.Size size : e.sizes()) {
                tracks.add(track(size));
            }
            if (e.count() > 0) {
                for (int k = 0; k < e.count(); k++) {
                    plain.addAll(tracks);
                }
            }
            if (auto) {
                withRepeat.add(switch (e.count()) {
                    case GridTracks.AUTO_FILL -> GridTemplateComponent.repeat(GridRepetition.autoFill(tracks));
                    case GridTracks.AUTO_FIT -> GridTemplateComponent.repeat(GridRepetition.autoFit(tracks));
                    case 1 -> tracks.size() == 1 ? GridTemplateComponent.single(tracks.get(0))
                            : GridTemplateComponent.repeat(GridRepetition.count(1, tracks));
                    default -> GridTemplateComponent.repeat(GridRepetition.count(e.count(), tracks));
                });
            }
        }
        for (GridTracks.LineName n : t.names()) {
            names.add(new NamedGridLine(n.name(), n.line()));
        }
    }

    private static TrackSizingFunction track(GridTracks.Size size) {
        return switch (size.kind()) {
            case LENGTH -> TrackSizingFunction.fixed(LayoutEngine.lp(size.length()));
            case FR -> TrackSizingFunction.fr(size.fr());
            case AUTO -> TrackSizingFunction.auto();
            case MIN_CONTENT -> TrackSizingFunction.minContent();
            case MAX_CONTENT -> TrackSizingFunction.maxContent();
            case FIT_CONTENT -> TrackSizingFunction.fitContent(LayoutEngine.lp(size.length()));
            case MINMAX -> TrackSizingFunction.minmax(track(size.min()), track(size.max()));
        };
    }

    private static GridPlacement placement(GridLine l) {
        return switch (l.kind()) {
            case AUTO -> GridPlacement.auto();
            case LINE -> GridPlacement.line(l.number());
            case NAMED -> GridPlacement.namedLine(l.name(), l.number());
            case SPAN -> GridPlacement.span(l.number());
            case NAMED_SPAN -> GridPlacement.namedSpan(l.name(), l.number());
        };
    }
}
