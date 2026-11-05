package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.effects.EffectsScreen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.concurrent.CompletableFuture;

/**
 * {@code -Pplaytest=effects}: group opacity fades overlapping squares and items as one, unlike the
 * same squares faded one by one, and a card fades out with its item; each filter function changes its
 * tile the way CSS defines, each blend mode mixes a gold square with the canvas as its formula says,
 * and backdrop filters blur and gray the stripes behind glass; shader effects: a glint runs around a
 * button, a tint lights up under the pointer, a card burns away on a transition and another dissolves
 * part way by a binding; each corner shape cuts its corner, crisp-edges steps curves and shadows by
 * whole GUI pixels, and fonts and upper case change text widths; all of it still after a resource
 * reload. Read from the drawn pixels.
 */
public final class EffectsPlaytest {
    /** A gold square over a blue canvas: page.css {@code .blob} and {@code .blends}. */
    private static final int[] GOLD = {208, 160, 64};
    private static final int[] CANVAS = {48, 128, 192};
    /** The opaque ground behind the layer effects and the shape tiles: page.css {@code .ground}. */
    private static final int[] GROUND = {32, 36, 44};
    /** spotlight.ixeffect's default color. */
    private static final int[] SPOT = {72, 144, 224};
    private static CompletableFuture<Void> reload = CompletableFuture.completedFuture(null);
    /** The glint row a moment ago and the whole card's left padding, kept between steps. */
    private static final int[][] glint = {null};
    private static final int[][] well = {null};

    private EffectsPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("effects", s -> {
            s.run(EffectsScreen::open);
            s.waitUntil("the page opens", () -> Playtest.isOpen() && Playtest.query(".square").size() == 4, Playtest.WAIT);
            s.expect("the page reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            s.shot("effects-opacity");
            // Fails if a group's opacity fades each descendant on its own: the overlap would show the square under it.
            s.expect("as one group, the overlap shows only the top square", () ->
                    near(overlap(".stack.whole"), topOnly(".stack.whole"), 3));
            s.expect("square by square, the overlap shows both", () ->
                    !near(overlap(".stack.apart"), topOnly(".stack.apart"), 10));
            // Fails if drawn elements are drawn outside the group's layer and keep full opacity.
            s.expect("items in a faded group fade too", () ->
                    !near(center(".items.whole .item", 1), center(".items:not(.whole) .item", 1), 10));
            s.click(".toggle");
            s.waitUntil("the card starts fading", () -> Playtest.query(".card.faded").size() == 1, Playtest.WAIT);
            s.pause(30);
            s.shot("effects-faded");

            s.click(".filters-tab");
            s.waitUntil("the filter tiles show", () -> Playtest.query(".tile").size() == 11
                    && Playtest.query(".tiles").get(0).box().width() > 0, Playtest.WAIT);
            s.pause(2);
            s.shot("effects-filters");
            s.expect("grayscale(1) leaves red gray", () -> {
                int[] c = rgb(bar(1));
                return Math.abs(c[0] - c[1]) <= 4 && Math.abs(c[1] - c[2]) <= 4;
            });
            s.expect("sepia(1) turns red brown", () -> {
                int[] c = rgb(bar(2));
                return c[0] > c[1] + 8 && c[1] > c[2] + 8 && c[0] < rgb(bar(0))[0] - 40;
            });
            s.expect("saturate(3) drains green and blue from red", () -> {
                int[] c = rgb(bar(3)), ref = rgb(bar(0));
                return c[1] < ref[1] - 20 && c[2] < ref[2] - 20;
            });
            s.expect("hue-rotate(90deg) moves red away", () -> rgb(bar(4))[0] < rgb(bar(0))[0] - 60);
            s.expect("invert(1) inverts", () -> {
                int[] c = rgb(bar(5)), ref = rgb(bar(0));
                return Math.abs(c[0] - (255 - ref[0])) <= 4 && Math.abs(c[1] - (255 - ref[1])) <= 4
                        && Math.abs(c[2] - (255 - ref[2])) <= 4;
            });
            s.expect("brightness(1.6) brightens", () -> rgb(bar(6))[1] > rgb(bar(0))[1] + 30);
            s.expect("contrast(2) pushes the dim channels down", () -> rgb(bar(7))[1] < 30);
            s.expect("opacity(0.4) lets the panel show through", () -> rgb(bar(8))[0] < rgb(bar(0))[0] - 60);
            // Fails if the layer is not grown by the blur's reach or the blur does not run: the pixel left of the bar stays panel.
            s.expect("blur(2px) spreads red outside the bar", () ->
                    rgb(beside(9))[0] > rgb(beside(0))[0] + 15);
            s.expect("drop-shadow draws a light shadow below the bar", () ->
                    rgb(below(10))[1] > rgb(below(0))[1] + 60);

            s.click(".blend-tab");
            s.waitUntil("the blend squares show", () -> Playtest.query(".blob").size() == 6
                    && Playtest.query(".blob").get(0).box().width() > 0, Playtest.WAIT);
            s.pause(2);
            s.shot("effects-blend");
            s.expect("normal draws the square", () -> blend(0, GOLD));
            s.expect("multiply multiplies", () -> blend(1, mix((a, b) -> a * b / 255)));
            s.expect("screen screens", () -> blend(2, mix((a, b) -> 255 - (255 - a) * (255 - b) / 255)));
            s.expect("plus-lighter adds", () -> blend(3, mix((a, b) -> Math.min(255, a + b))));
            s.expect("darken keeps the darker", () -> blend(4, mix(Math::min)));
            s.expect("lighten keeps the lighter", () -> blend(5, mix(Math::max)));
            // Fails if the backdrop is not filtered or not drawn back: the stripes behind the glass would stay sharp.
            s.expect("backdrop blur smooths the stripes behind the glass only", () -> {
                LayoutBox canvas = Playtest.query(".stripes").get(0).box();
                LayoutBox glass = Playtest.query(".glass.blurred").get(0).box();
                float inside = glass.y() + 7, outside = canvas.y() + 3;
                return contrast(canvas.x() + 20, canvas.x() + 28, inside) < 40
                        && contrast(canvas.x() + 20, canvas.x() + 28, outside) > 150;
            });
            s.expect("backdrop grayscale grays the stripes behind the glass", () -> {
                LayoutBox canvas = Playtest.query(".stripes").get(0).box();
                LayoutBox glass = Playtest.query(".glass.gray").get(0).box();
                int[] c = rgb(pixel(canvas.x() + 164, glass.y() + 7));
                return Math.abs(c[0] - c[1]) <= 6 && Math.abs(c[1] - c[2]) <= 6;
            });

            s.click(".shaders-tab");
            s.waitUntil("the shader tab shows", () -> Playtest.query(".spotlight").get(0).box().width() > 0, Playtest.WAIT);
            s.run(() -> Playtest.hoverAt(0, 0));
            s.pause(2);
            s.shot("effects-shaders");
            // Fails if the box effect's program does not draw, or draws with the pointer it does not have.
            s.expect("the spotlight tints its box dimly with the pointer away", () -> spotlight(0.3));
            s.run(() -> Playtest.hoverAt(spot()[0], spot()[1]));
            s.pause(2);
            s.expect("the spotlight lights up under the pointer", () -> spotlight(1));
            s.run(() -> Playtest.hoverAt(0, 0));
            s.run(() -> glint[0] = glintRow());
            s.pause(10);
            s.expect("the glint moves along the button's edge", () -> {
                int[] now = glintRow();
                for (int i = 0; i < now.length; i++) {
                    if (!near(now[i], glint[0][i], 30)) {
                        return true;
                    }
                }
                return false;
            });
            s.run(() -> well[0] = cardPixels(".burn"));
            s.expect("at progress 0 dissolve leaves the card whole", () -> uniform(well[0], well[0][0]));
            s.click(".burn-toggle");
            s.pause(40);
            s.shot("effects-burnt");
            // Fails if the typed parameter does not transition to 1 or the layer program does not run.
            s.expect("the burnt card is gone", () -> uniform(cardGrid(".burn"), beside(".burn")));
            s.click(".step");
            s.click(".step");
            s.waitUntil("progress reaches 0.5", () -> Playtest.text(".progress").equals("0.5"), Playtest.WAIT);
            s.pause(2);
            s.shot("effects-dissolving");
            s.expect("at 0.5 the stepped card is partly gone", () -> {
                int[] c = cardGrid(".scrub");
                int bg = beside(".scrub");
                boolean gone = false, kept = false;
                for (int p : c) {
                    gone |= near(p, bg, 4);
                    kept |= near(p, well[0][0], 4);
                }
                return gone && kept;
            });

            s.click(".shapes-tab");
            s.waitUntil("the shape tiles show", () -> Playtest.query(".shape").size() == 12
                    && Playtest.query(".shape").get(0).box().width() > 0, Playtest.WAIT);
            s.pause(2);
            s.shot("effects-shapes");
            // Fails if corner-shape does not reach the masks: every corner would stay round. Crisp, so each GUI pixel is in or out.
            s.expect("each corner shape cuts its own corner", () -> {
                // Pixel (2, 1) of a 6px corner: inside round and square only; pixel (5, 3): outside notch only.
                boolean[] keeps21 = {true, false, false, false, true};
                boolean[] keeps53 = {true, true, false, true, true};
                try (NativeImage image = screenshot()) {
                    for (int i = 0; i < 5; i++) {
                        LayoutBox b = Playtest.query(".shape-row.crisp .shape").get(i).box();
                        if (is(at(image, b.x() + 2.5f, b.y() + 1.5f), GOLD) != keeps21[i]
                                || is(at(image, b.x() + 5.5f, b.y() + 3.5f), GOLD) != keeps53[i]) {
                            return false;
                        }
                    }
                    return true;
                }
            });
            // Fails if the crisp mask is filtered linearly or baked finer than a GUI pixel: corner pixels would blend.
            s.expect("crisp-edges steps the curve by whole GUI pixels", () -> cornerStepped(".shape-row.crisp .shape.round"));
            s.expect("auto antialiases the curve", () -> !cornerStepped(".shape-row.smooth .shape.round"));
            s.expect("a crisp shadow fades GUI pixel by GUI pixel", () -> {
                LayoutBox b = Playtest.query(".shape-row.crisp .shape.shadowed").get(0).box();
                float y = b.y() + b.height() / 2;
                try (NativeImage image = screenshot()) {
                    if (is(at(image, b.x() - 0.5f, y), GROUND)) {
                        return false;
                    }
                    for (int d = 1; d <= 4; d++) {
                        if (!near(at(image, b.x() - d + 0.25f, y), at(image, b.x() - d + 0.75f, y), 3)) {
                            return false;
                        }
                    }
                    return true;
                }
            });
            // Fails if the font or the case does not reach measuring: every line would be as wide as the default one.
            s.expect("font-family and text-transform change how wide the text is", () -> {
                float plain = width(".font.plain");
                return width(".font.uniform") != plain && width(".font.alt") != plain && width(".font.upper") > plain;
            });
            s.expect("minecraft:uniform and minecraft:alt are fonts the game has", () ->
                    Playtest.document().diagnostics().entries().stream().noneMatch(d -> d.code().id().equals("layout/unknown-font")));
            // Fails if a § is read as a formatting code when measuring: the line would take no room.
            s.expect("a § is text, measured as drawn", () -> width(".font.codes") > 0);
            // Fails if small text is measured at the size written rather than drawn at a crisp one: its
            // box would not match its glyphs, or strokes would come out of uneven thickness.
            s.expect("6px text takes its crisp size's width", () -> {
                double gui = Minecraft.getInstance().getWindow().getGuiScale();
                float crisp = com.azane.ixchel.mc.paint.McTextMeasurer.crispSize(6, gui);
                return Math.abs(width(".font.small") - width(".font.plain") * crisp / 9) < 0.01f;
            });
            // F3+T: vanilla's reload deletes the shader objects in its program table, ours included.
            // Fails if a layer, blend mode, backdrop or effect draws wrong with the programs built before it.
            s.run(() -> {
                Minecraft.getInstance().setScreen(null);
                reload = Minecraft.getInstance().reloadResourcePacks();
            });
            s.waitUntil("the resources reload", () -> reload.isDone() && Minecraft.getInstance().getOverlay() == null,
                    4 * Playtest.WAIT);
            s.run(EffectsScreen::open);
            s.waitUntil("the page opens again", () -> Playtest.isOpen() && Playtest.query(".square").size() == 4, Playtest.WAIT);
            s.pause(2);
            s.expect("after a reload, the overlap of a group shows only the top square", () ->
                    near(overlap(".stack.whole"), topOnly(".stack.whole"), 3));
            s.click(".blend-tab");
            s.waitUntil("the blend squares show again", () -> Playtest.query(".blob").size() == 6
                    && Playtest.query(".blob").get(0).box().width() > 0, Playtest.WAIT);
            s.pause(2);
            s.shot("effects-reloaded");
            s.expect("after a reload, multiply multiplies", () -> blend(1, mix((a, b) -> a * b / 255)));
            s.expect("after a reload, backdrop blur smooths the stripes", () -> {
                LayoutBox canvas = Playtest.query(".stripes").get(0).box();
                LayoutBox glass = Playtest.query(".glass.blurred").get(0).box();
                return contrast(canvas.x() + 20, canvas.x() + 28, glass.y() + 7) < 40;
            });
            s.click(".shaders-tab");
            s.waitUntil("the shader tab shows again", () -> Playtest.query(".spotlight").get(0).box().width() > 0, Playtest.WAIT);
            s.run(() -> Playtest.hoverAt(0, 0));
            s.pause(2);
            s.expect("after a reload, the spotlight still draws", () -> spotlight(0.3));
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    /** A point of the spotlight box clear of its caption: 10px in, 5px down. */
    private static float[] spot() {
        LayoutBox b = Playtest.query(".spotlight").get(0).box();
        return new float[]{b.x() + 10, b.y() + 5};
    }

    private static float width(String selector) {
        return Playtest.query(selector).get(0).box().width();
    }

    /**
     * Whether every GUI pixel of the top-left 6px corner of {@code selector} is one color, gold or the
     * ground, as far as two physical pixels inside it show.
     */
    private static boolean cornerStepped(String selector) {
        LayoutBox b = Playtest.query(selector).get(0).box();
        try (NativeImage image = screenshot()) {
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 6; x++) {
                    int p = at(image, b.x() + x + 0.25f, b.y() + y + 0.25f);
                    if (!near(p, at(image, b.x() + x + 0.75f, b.y() + y + 0.75f), 3) || !is(p, GOLD) && !is(p, GROUND)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static NativeImage screenshot() {
        return Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
    }

    /** The color of {@code image} at GUI point {@code (x, y)}, ABGR. */
    private static int at(NativeImage image, float x, float y) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return image.getPixelRGBA((int) (x * scale), (int) (y * scale));
    }

    /** Whether the ABGR pixel {@code abgr} is {@code rgb} within a rounding margin. */
    private static boolean is(int abgr, int[] rgb) {
        int[] c = rgb(abgr);
        return Math.abs(c[0] - rgb[0]) <= 8 && Math.abs(c[1] - rgb[1]) <= 8 && Math.abs(c[2] - rgb[2]) <= 8;
    }

    /** Whether the spotlight shows its color at {@code strength} over the panel beside it. */
    private static boolean spotlight(double strength) {
        float[] p = spot();
        int[] c = rgb(pixel(p[0], p[1]));
        int[] bg = rgb(beside(".spotlight"));
        for (int i = 0; i < 3; i++) {
            if (Math.abs(c[i] - (bg[i] * (1 - strength) + SPOT[i] * strength)) > 8) {
                return false;
            }
        }
        return true;
    }

    /** The glint button's pixels 1px inside its top edge, every 2px. */
    private static int[] glintRow() {
        LayoutBox b = Playtest.query(".glint").get(0).box();
        int[] row = new int[(int) (b.width() / 2)];
        for (int i = 0; i < row.length; i++) {
            row[i] = pixel(b.x() + 1 + 2 * i, b.y() + 1);
        }
        return row;
    }

    /** Pixels of the card {@code selector} in its left padding, top to bottom, clear of the item and text. */
    private static int[] cardPixels(String selector) {
        LayoutBox b = Playtest.query(selector).get(0).box();
        int[] c = new int[(int) b.height() - 4];
        for (int i = 0; i < c.length; i++) {
            c[i] = pixel(b.x() + 3, b.y() + 2 + i);
        }
        return c;
    }

    /** The pixel 3px left of the element {@code selector}, the panel behind it. */
    private static int beside(String selector) {
        LayoutBox b = Playtest.query(selector).get(0).box();
        return pixel(b.x() - 3, b.y() + b.height() / 2);
    }

    /** Whether every pixel of {@code c} is {@code expected} within a rounding margin. */
    private static boolean uniform(int[] c, int expected) {
        for (int p : c) {
            if (!near(p, expected, 4)) {
                return false;
            }
        }
        return true;
    }

    /** Pixels of the card {@code selector} every 3px across and down from 2px in, item and text included. */
    private static int[] cardGrid(String selector) {
        LayoutBox b = Playtest.query(selector).get(0).box();
        int columns = (int) ((b.width() - 2) / 3), rows = (int) ((b.height() - 2) / 3);
        int[] c = new int[columns * rows];
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            for (int i = 0; i < c.length; i++) {
                c[i] = image.getPixelRGBA((int) ((b.x() + 2 + 3 * (i % columns)) * scale),
                        (int) ((b.y() + 2 + 3 * (i / columns)) * scale));
            }
        }
        return c;
    }

    /** The middle of the overlap of the two squares in {@code stack}. */
    private static int overlap(String stack) {
        LayoutBox a = Playtest.query(stack + " .square.a").get(0).box();
        LayoutBox b = Playtest.query(stack + " .square.b").get(0).box();
        return pixel((b.x() + a.x() + a.width()) / 2, b.y() + b.height() / 2);
    }

    /** A point of the top square in {@code stack} that the other does not cover. */
    private static int topOnly(String stack) {
        LayoutBox b = Playtest.query(stack + " .square.b").get(0).box();
        return pixel(b.x() + b.width() - 4, b.y() + b.height() / 2);
    }

    /** The center of match {@code index} of {@code selector}. */
    private static int center(String selector, int index) {
        LayoutBox b = Playtest.query(selector).get(index).box();
        return pixel(b.x() + b.width() / 2, b.y() + b.height() / 2);
    }

    /** The middle of the red bar of filter tile {@code tile}. */
    private static int bar(int tile) {
        return center(".bar.red", tile);
    }

    /** The point just left of the red bar of tile {@code tile}, in its padding. */
    private static int beside(int tile) {
        LayoutBox b = Playtest.query(".bar.red").get(tile).box();
        return pixel(b.x() - 1, b.y() + b.height() / 2);
    }

    /** The point 2px below the middle of the red bar of tile {@code tile}. */
    private static int below(int tile) {
        LayoutBox b = Playtest.query(".bar.red").get(tile).box();
        return pixel(b.x() + b.width() / 2, b.y() + b.height() + 2);
    }

    /** Whether blend square {@code index} shows {@code expected} within a rounding margin. */
    private static boolean blend(int index, int[] expected) {
        int[] c = rgb(center(".blob", index));
        for (int i = 0; i < 3; i++) {
            if (Math.abs(c[i] - expected[i]) > 6) {
                return false;
            }
        }
        return true;
    }

    private interface Channel {
        int apply(int square, int canvas);
    }

    /** {@code f} of the square and the canvas, channel by channel. */
    private static int[] mix(Channel f) {
        return new int[]{f.apply(GOLD[0], CANVAS[0]), f.apply(GOLD[1], CANVAS[1]), f.apply(GOLD[2], CANVAS[2])};
    }

    /** The summed channel difference between the points {@code (x0, y)} and {@code (x1, y)}. */
    private static int contrast(float x0, float x1, float y) {
        int[] a = rgb(pixel(x0, y)), b = rgb(pixel(x1, y));
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    /** Red, green and blue of an ABGR pixel. */
    private static int[] rgb(int abgr) {
        return new int[]{abgr & 0xFF, abgr >> 8 & 0xFF, abgr >> 16 & 0xFF};
    }

    /** The color drawn at GUI point {@code (x, y)} in the last frame, ABGR as {@link NativeImage} keeps it. */
    private static int pixel(float x, float y) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            return image.getPixelRGBA((int) (x * scale), (int) (y * scale));
        }
    }

    /** Whether every color channel of {@code a} and {@code b} is within {@code tolerance}. */
    private static boolean near(int a, int b, int tolerance) {
        for (int shift = 0; shift < 24; shift += 8) {
            if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > tolerance) {
                return false;
            }
        }
        return true;
    }
}
