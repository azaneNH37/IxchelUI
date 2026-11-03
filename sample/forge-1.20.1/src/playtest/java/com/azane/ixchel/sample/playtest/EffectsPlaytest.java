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
 * and backdrop filters blur and gray the stripes behind glass; all of it still after a resource
 * reload. Read from the drawn pixels.
 */
public final class EffectsPlaytest {
    /** A gold square over a blue canvas: page.css {@code .blob} and {@code .blends}. */
    private static final int[] GOLD = {208, 160, 64};
    private static final int[] CANVAS = {48, 128, 192};
    private static CompletableFuture<Void> reload = CompletableFuture.completedFuture(null);

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
            // Fails if native elements are drawn outside the group's layer and keep full opacity.
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
            // F3+T: vanilla's reload deletes the shader objects in its program table, ours included.
            // Fails if a layer, blend mode or backdrop draws wrong with the programs built before it.
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
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
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
