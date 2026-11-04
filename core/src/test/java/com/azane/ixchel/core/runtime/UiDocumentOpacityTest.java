package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.dom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UiDocumentOpacityTest {
    /** Fails if opacity multiplies colors again (overlaps blend inside the group) or a nested group is not its own layer. */
    @Test
    void opacityPaintsTheSubtreeAsOneLayerAndZeroPaintsNothingButStillHits() {
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <link href="t.css"/>
                <div class="outer"><div class="inner">a</div><img class="pic" src="x:a.png"/></div>
                <div class="gone"></div>
                """), "t.html", GoldenTest.sheet("""
                .outer { opacity: 0.5; background-color: #ff000080 }
                .inner { opacity: 0.5; height: 10px; background-color: #fff; color: #00ff00 }
                .pic { width: 4px; height: 4px }
                .gone { opacity: 0; width: 10px; height: 10px; background-color: #fff }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer -1 -1 102 16 0.5
                rect 0 0 100 14 #80FF0000
                layer -1 -1 102 12 0.5
                rect 0 0 100 10 #FFFFFFFF
                text 0 0 9 #FF00FF00 "a"
                unlayer
                image 0 10 4 4 x:a.png uv 0 0 1 1 #FFFFFFFF
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
        Element gone = (Element) doc.root().children().get(1);
        assertSame(gone, doc.events().hitTest(5, 20));
    }

    /**
     * Fails if the layer is opened inside the element's own transform, misses what the transform
     * moves, keeps what a clip cuts away, or a group that draws nothing still opens a layer.
     */
    @Test
    void layerBoundsFollowTransformsAndClipsAndAnEmptyGroupOpensNone() {
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <link href="t.css"/>
                <div class="moved"></div>
                <div class="clipper"><div class="wide"></div></div>
                <div class="empty"></div>
                """), "t.html", GoldenTest.sheet("""
                .moved { opacity: 0.5; width: 10px; height: 10px; background-color: #fff; transform: translate(20px, 0) scale(2) }
                .clipper { opacity: 0.5; width: 10px; height: 10px; overflow: hidden }
                .wide { width: 50px; height: 50px; background-color: #fff }
                .empty { opacity: 0.5; width: 10px; height: 10px }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer 14 -6 22 22 0.5
                transform 2 0 0 2 15 -5
                rect 0 0 10 10 #FFFFFFFF
                untransform
                unlayer
                layer -1 9 12 12 0.5
                clip 0 10 10 10
                rect 0 10 50 10 #FFFFFFFF
                unclip
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
    }

    /**
     * Fails if a filter or blend mode does not open a layer, the layer misses what a blur spreads to
     * (an enclosing group included), or a backdrop is not marked as under its element's own layer.
     */
    @Test
    void filtersAndBlendModesOpenLayersGrownByTheirSpread() {
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <link href="t.css"/>
                <div class="group"><div class="blurred"></div></div>
                <div class="blended"></div>
                <div class="glass"></div>
                """), "t.html", GoldenTest.sheet("""
                .group { opacity: 0.5 }
                .blurred { filter: blur(2px); width: 10px; height: 10px; background-color: #fff }
                .blended { mix-blend-mode: multiply; backdrop-filter: grayscale(1); width: 10px; height: 10px; background-color: #fff }
                .glass { backdrop-filter: blur(1px); width: 10px; height: 10px }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer -7 -7 24 24 0.5
                layer -7 -7 24 24 1 blur(2)
                rect 0 0 10 10 #FFFFFFFF
                unlayer
                unlayer
                layer -1 9 12 12 1 MULTIPLY
                backdrop 0 10 10 10 grayscale(1) below
                rect 0 10 10 10 #FFFFFFFF
                unlayer
                backdrop 0 20 10 10 blur(1)""", GoldenTest.print(doc.frame(100, 100)));
    }
}
