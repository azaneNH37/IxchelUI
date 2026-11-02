package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UiDocumentOpacityTest {
    @Test
    void opacityMultipliesDownTheSubtreeAndZeroPaintsNothingButStillHits() {
        UiDocument doc = UiDocument.load("""
                <link rel="stylesheet" href="t.css"/>
                <div class="outer"><div class="inner">a</div><img class="pic" src="x:a.png"/></div>
                <div class="gone"></div>
                """, "t.html", GoldenTest.sheet("""
                .outer { opacity: 0.5; background-color: #ff000080 }
                .inner { opacity: 0.5; height: 10px; background-color: #fff; color: #00ff00 }
                .pic { width: 4px; height: 4px }
                .gone { opacity: 0; width: 10px; height: 10px; background-color: #fff }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("""
                rect 0 0 100 14 #40FF0000
                rect 0 0 100 10 #40FFFFFF
                text 0 0 9 #4000FF00 "a"
                image 0 10 4 4 x:a.png uv 0 0 1 1 #80FFFFFF""", GoldenTest.print(doc.frame(100, 100)));
        Element gone = (Element) doc.root().children().get(1);
        assertSame(gone, doc.events().hitTest(5, 20));
    }
}
