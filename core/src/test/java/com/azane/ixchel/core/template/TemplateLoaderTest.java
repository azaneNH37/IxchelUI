package com.azane.ixchel.core.template;

import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.library.Library;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** File ids an {@code href} names, and which files are component files. */
class TemplateLoaderTest {
    @Test
    void aRelativeHrefStaysInTheNamespaceOfItsFile() {
        assertEquals("mod:ui/common/a.css", TemplateLoader.resolve("mod:ui/page/p.html", "../common/a.css"));
        assertEquals("@ixchel/mc/player-inventory.css", TemplateLoader.resolve("@ixchel/mc/player-inventory.html", "player-inventory.css"));
        assertEquals("@ixchel/themes/x.css", TemplateLoader.resolve("@ixchel/mc/a.html", "../themes/x.css"));
    }

    @Test
    void aLibraryHrefFromAHostFileNamesTheLibraryFile() {
        assertEquals("@ixchel/mc/a.html", TemplateLoader.resolve("mod:ui/p.html", "@ixchel/mc/./a.html"));
    }

    @Test
    void dotDotCannotLeaveTheLibrary() {
        assertThrows(IllegalArgumentException.class, () -> TemplateLoader.resolve("@ixchel/mc/a.html", "../../x.css"));
        assertThrows(IllegalArgumentException.class, () -> TemplateLoader.resolve("mod:ui/p.html", "@ixchel/../mod/x.css"));
    }

    @Test
    void aFileIsAComponentFileExactlyWhenItHasATopLevelTemplate() {
        Map<String, String> files = Map.of(
                "m:broken.html", "<link rel=\"stylesheet\" href=\"a.css\"/><template name=\"ui-a\"></template><div></div>",
                "m:links.html", "<link rel=\"stylesheet\" href=\"a.css\"/>",
                "m:page.html", "<div></div>");
        assertEquals("ui-a", TemplateLoader.declared("m:broken.html", files::get, Library.CORE, HtmlElements.TAGS).name(),
                "extra content is the file's error, reported where it is used");
        assertNull(TemplateLoader.declared("m:links.html", files::get, Library.CORE, HtmlElements.TAGS));
        assertNull(TemplateLoader.declared("m:page.html", files::get, Library.CORE, HtmlElements.TAGS));
        assertNull(TemplateLoader.declared("m:missing.html", files::get, Library.CORE, HtmlElements.TAGS));
    }
}
