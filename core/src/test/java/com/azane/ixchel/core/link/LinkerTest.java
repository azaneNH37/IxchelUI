package com.azane.ixchel.core.link;

import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.runtime.UiHosting;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** File ids an {@code href} names, and which files are component files. */
class LinkerTest {
    @Test
    void aRelativeHrefStaysInTheNamespaceOfItsFile() {
        assertEquals("mod:ui/common/a.css", Linker.resolve("mod:ui/page/p.html", "../common/a.css"));
        assertEquals("@ixchel/mc/player-inventory.css", Linker.resolve("@ixchel/mc/player-inventory.html", "player-inventory.css"));
        assertEquals("@ixchel/themes/x.css", Linker.resolve("@ixchel/mc/a.html", "../themes/x.css"));
    }

    @Test
    void aLibraryHrefFromAHostFileNamesTheLibraryFile() {
        assertEquals("@ixchel/mc/a.html", Linker.resolve("mod:ui/p.html", "@ixchel/mc/./a.html"));
    }

    @Test
    void dotDotCannotLeaveTheLibrary() {
        assertThrows(IllegalArgumentException.class, () -> Linker.resolve("@ixchel/mc/a.html", "../../x.css"));
        assertThrows(IllegalArgumentException.class, () -> Linker.resolve("mod:ui/p.html", "@ixchel/../mod/x.css"));
    }

    @Test
    void aFileIsAComponentFileExactlyWhenItsRootIsAComponent() {
        Map<String, String> files = Map.of(
                "m:broken.html", "<component name=\"ui-a\"><link href=\"a.css\"/></component><div></div>",
                "m:links.html", "<link href=\"a.css\"/>",
                "m:page.html", "<page><div></div></page>");
        assertEquals("ui-a", Linker.declared("m:broken.html", files::get, UiHosting.NONE.library(), HtmlElements.TAGS).name(),
                "extra content is the file's error, reported where it is used");
        assertNull(Linker.declared("m:links.html", files::get, UiHosting.NONE.library(), HtmlElements.TAGS));
        assertNull(Linker.declared("m:page.html", files::get, UiHosting.NONE.library(), HtmlElements.TAGS));
        assertNull(Linker.declared("m:missing.html", files::get, UiHosting.NONE.library(), HtmlElements.TAGS));
    }
}
