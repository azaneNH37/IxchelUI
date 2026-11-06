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
 * whole GUI pixels, and fonts and upper case change text widths; on the CSS tab, an outline rings its
 * box past its offset with beveled corners, letter-spacing widens Latin and CJK text where it is
 * measured and drawn, inset places a box, radial, conic and repeating gradients put their colors where
 * CSS says, animated background-position moves stripes, aspect-ratio sizes a box, a skewed box hovers
 * where it is drawn, and the ported health meter's damage bar shrinks after a delay; all of it still
 * after a resource reload. Read from the drawn pixels. {@code -Precord=effects} records each tab as
 * its own take, {@code recordings/effects-<tab>.mp4}.
 */
public final class EffectsPlaytest {
    /** A gold square over a blue canvas: page.css {@code .blob} and {@code .blends}. */
    private static final int[] GOLD = {208, 160, 64};
    private static final int[] CANVAS = {48, 128, 192};
    /** The opaque ground behind the layer effects and the shape tiles: page.css {@code .ground}. */
    private static final int[] GROUND = {32, 36, 44};
    /** spotlight.ixeffect's default color. */
    private static final int[] SPOT = {72, 144, 224};
    /** The CSS tab's outline and inset color, its blue and its hovered skew: page.css {@code .outlined}, {@code .repeating-linear}, {@code .skewed:hover}. */
    private static final int[] OUTLINE = {72, 192, 224};
    private static final int[] BLUE = {72, 144, 224};
    private static final int[] RED = {224, 72, 72};
    private static CompletableFuture<Void> reload = CompletableFuture.completedFuture(null);
    /** The glint's edge a moment ago and the whole card's left padding, kept between steps. */
    private static final int[][] glint = {null};
    private static final int[][] well = {null};
    /** The sliding stripes a moment ago. */
    private static final int[][] slide = {null};

    private EffectsPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("effects", s -> {
            s.run(EffectsScreen::open);
            s.waitUntil("the page opens", () -> Playtest.isOpen() && Playtest.query(".square").size() == 4, Playtest.WAIT);
            s.expect("the page reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            // Recorded: one take per tab.
            s.take("effects-opacity", t -> {
                t.shot("effects-opacity");
                // Fails if a group's opacity fades each descendant on its own: the overlap would show the square under it.
                t.expect("as one group, the overlap shows only the top square", () ->
                        near(overlap(".stack.whole"), topOnly(".stack.whole"), 3));
                t.expect("square by square, the overlap shows both", () ->
                        !near(overlap(".stack.apart"), topOnly(".stack.apart"), 10));
                // Fails if drawn elements are drawn outside the group's layer and keep full opacity.
                t.expect("items in a faded group fade too", () ->
                        !near(center(".items.whole .item", 1), center(".items:not(.whole) .item", 1), 10));
                t.hold(800);
                t.click(".toggle");
                t.waitUntil("the card starts fading", () -> Playtest.query(".card.faded").size() == 1, Playtest.WAIT);
                t.pause(30);
                t.shot("effects-faded");
                t.hold(600);
            });

            s.take("effects-filters", t -> {
                t.click(".filters-tab");
                t.waitUntil("the filter tiles show", () -> Playtest.query(".tile").size() == 11
                        && Playtest.query(".tiles").get(0).box().width() > 0, Playtest.WAIT);
                t.pause(2);
                t.shot("effects-filters");
                t.expect("grayscale(1) leaves red gray", () -> {
                    int[] c = rgb(bar(1));
                    return Math.abs(c[0] - c[1]) <= 4 && Math.abs(c[1] - c[2]) <= 4;
                });
                t.expect("sepia(1) turns red brown", () -> {
                    int[] c = rgb(bar(2));
                    return c[0] > c[1] + 8 && c[1] > c[2] + 8 && c[0] < rgb(bar(0))[0] - 40;
                });
                t.expect("saturate(3) drains green and blue from red", () -> {
                    int[] c = rgb(bar(3)), ref = rgb(bar(0));
                    return c[1] < ref[1] - 20 && c[2] < ref[2] - 20;
                });
                t.expect("hue-rotate(90deg) moves red away", () -> rgb(bar(4))[0] < rgb(bar(0))[0] - 60);
                t.expect("invert(1) inverts", () -> {
                    int[] c = rgb(bar(5)), ref = rgb(bar(0));
                    return Math.abs(c[0] - (255 - ref[0])) <= 4 && Math.abs(c[1] - (255 - ref[1])) <= 4
                            && Math.abs(c[2] - (255 - ref[2])) <= 4;
                });
                t.expect("brightness(1.6) brightens", () -> rgb(bar(6))[1] > rgb(bar(0))[1] + 30);
                t.expect("contrast(2) pushes the dim channels down", () -> rgb(bar(7))[1] < 30);
                t.expect("opacity(0.4) lets the panel show through", () -> rgb(bar(8))[0] < rgb(bar(0))[0] - 60);
                // Fails if the layer is not grown by the blur's reach or the blur does not run: the pixel left of the bar stays panel.
                t.expect("blur(2px) spreads red outside the bar", () ->
                        rgb(beside(9))[0] > rgb(beside(0))[0] + 15);
                t.expect("drop-shadow draws a light shadow below the bar", () ->
                        rgb(below(10))[1] > rgb(below(0))[1] + 60);
                t.hold(1500);
            });

            s.take("effects-blend", t -> {
                t.click(".blend-tab");
                t.waitUntil("the blend squares show", () -> Playtest.query(".blob").size() == 6
                        && Playtest.query(".blob").get(0).box().width() > 0, Playtest.WAIT);
                t.pause(2);
                t.shot("effects-blend");
                t.expect("normal draws the square", () -> blend(0, GOLD));
                t.expect("multiply multiplies", () -> blend(1, mix((a, b) -> a * b / 255)));
                t.expect("screen screens", () -> blend(2, mix((a, b) -> 255 - (255 - a) * (255 - b) / 255)));
                t.expect("plus-lighter adds", () -> blend(3, mix((a, b) -> Math.min(255, a + b))));
                t.expect("darken keeps the darker", () -> blend(4, mix(Math::min)));
                t.expect("lighten keeps the lighter", () -> blend(5, mix(Math::max)));
                // Fails if the backdrop is not filtered or not drawn back: the stripes behind the glass would stay sharp.
                t.expect("backdrop blur smooths the stripes behind the glass only", () -> {
                    LayoutBox canvas = Playtest.query(".stripes").get(0).box();
                    LayoutBox glass = Playtest.query(".glass.blurred").get(0).box();
                    float inside = glass.y() + 7, outside = canvas.y() + 3;
                    return contrast(canvas.x() + 20, canvas.x() + 28, inside) < 40
                            && contrast(canvas.x() + 20, canvas.x() + 28, outside) > 150;
                });
                t.expect("backdrop grayscale grays the stripes behind the glass", () -> {
                    LayoutBox canvas = Playtest.query(".stripes").get(0).box();
                    LayoutBox glass = Playtest.query(".glass.gray").get(0).box();
                    int[] c = rgb(pixel(canvas.x() + 164, glass.y() + 7));
                    return Math.abs(c[0] - c[1]) <= 6 && Math.abs(c[1] - c[2]) <= 6;
                });
                t.hold(1500);
            });

            s.take("effects-shaders", t -> {
                t.click(".shaders-tab");
                t.waitUntil("the shader tab shows", () -> Playtest.query(".spotlight").get(0).box().width() > 0, Playtest.WAIT);
                t.hoverAt(() -> new float[]{0, 0});
                t.pause(2);
                t.shot("effects-shaders");
                // Fails if the box effect's program does not draw, or draws with the pointer it does not have.
                t.expect("the spotlight tints its box dimly with the pointer away", () -> spotlight(0.3));
                t.hoverAt(EffectsPlaytest::spot);
                t.pause(2);
                t.expect("the spotlight lights up under the pointer", () -> spotlight(1));
                t.hold(800);
                t.hoverAt(() -> new float[]{0, 0});
                // The glint is somewhere on the edge at any time, so a check all the way round sees it
                // whatever the time; half a second later it has moved a fifth of the way on.
                t.run(() -> glint[0] = glintRing());
                t.pause(10);
                t.expect("the glint moves along the button's edge", () -> {
                    int[] now = glintRing();
                    for (int i = 0; i < now.length; i++) {
                        if (!near(now[i], glint[0][i], 30)) {
                            return true;
                        }
                    }
                    return false;
                });
                t.run(() -> well[0] = cardPixels(".burn"));
                t.expect("at progress 0 dissolve leaves the card whole", () -> uniform(well[0], well[0][0]));
                t.click(".burn-toggle");
                t.pause(40);
                t.shot("effects-burnt");
                // Fails if the typed parameter does not transition to 1 or the layer program does not run.
                t.expect("the burnt card is gone", () -> uniform(cardGrid(".burn"), beside(".burn")));
                t.click(".step");
                t.click(".step");
                t.waitUntil("progress reaches 0.5", () -> Playtest.text(".progress").equals("0.5"), Playtest.WAIT);
                t.pause(2);
                t.shot("effects-dissolving");
                t.expect("at 0.5 the stepped card is partly gone", () -> {
                    int[] c = cardGrid(".scrub");
                    int bg = beside(".scrub");
                    boolean gone = false, kept = false;
                    for (int p : c) {
                        gone |= near(p, bg, 4);
                        kept |= near(p, well[0][0], 4);
                    }
                    return gone && kept;
                });
                t.hold(1000);
            });

            s.take("effects-masks", t -> {
                t.click(".masks-tab");
                t.waitUntil("the mask cells show", () -> Playtest.query(".clip-circle").get(0).box().width() > 0, Playtest.WAIT);
                t.pause(2);
                t.shot("effects-masks");
                // Fails if clip-path does not reach the composite: the corners would stay gold.
                t.expect("each clip-path shape shows the ground outside it and the box inside", () -> {
                    try (NativeImage image = screenshot()) {
                        return clipped(image, ".clip-inset", 16, 16, 3, 16) && clipped(image, ".clip-inset", 16, 16, 6.5f, 4.5f)
                                && clipped(image, ".clip-circle", 16, 16, 1.5f, 1.5f)
                                && clipped(image, ".clip-ellipse", 16, 16, 16, 3)
                                && clipped(image, ".clip-star", 16, 12, 16, 29.5f) && clipped(image, ".clip-star", 16, 12, 2, 2);
                    }
                });
                // Fails if hit testing ignores the clip: the circle's clipped-out corner would still hit it.
                t.expect("a clipped-out corner is not hit, the inside is", () -> {
                    var circle = Playtest.query(".clip-circle").get(0);
                    LayoutBox b = circle.box();
                    var events = Playtest.document().events();
                    return events.hitTest(b.x() + 16, b.y() + 16) == circle && events.hitTest(b.x() + 1.5f, b.y() + 1.5f) != circle;
                });
                // Fails if the mask is ignored (all blue) or applied as a cut (no partial alpha).
                t.expect("mask-image fades the list from opaque to the ground", () -> {
                    LayoutBox b = Playtest.query(".fade-list").get(0).box();
                    try (NativeImage image = screenshot()) {
                        int middle = at(image, b.x() + 1, b.y() + b.height() * 0.625f);
                        return is(at(image, b.x() + 1, b.y() + 2), SPOT) && is(at(image, b.x() + 1, b.y() + b.height() - 0.5f), GROUND)
                                && !is(middle, SPOT) && !is(middle, GROUND) && rgb(middle)[2] > GROUND[2] + 40;
                    }
                });
                t.expect("a radial mask keeps the center and fades to the edge", () -> {
                    LayoutBox b = Playtest.query(".spot").get(0).box();
                    try (NativeImage image = screenshot()) {
                        int half = at(image, b.x() + 16, b.y() + 4);
                        return is(at(image, b.x() + 16, b.y() + 16), GOLD) && is(at(image, b.x() + 1, b.y() + 1), GROUND)
                                && !is(half, GOLD) && !is(half, GROUND);
                    }
                });
                // Fails if a url() mask is not sized and repeated at its natural 16px: the second tile would differ.
                t.expect("an item texture masks by its alpha, tile by tile", () -> {
                    LayoutBox b = Playtest.query(".stars").get(0).box();
                    try (NativeImage image = screenshot()) {
                        return !is(at(image, b.x() + 8.5f, b.y() + 8.5f), GROUND) && !is(at(image, b.x() + 24.5f, b.y() + 24.5f), GROUND)
                                && is(at(image, b.x() + 0.5f, b.y() + 0.5f), GROUND) && is(at(image, b.x() + 17.5f, b.y() + 31.5f), GROUND);
                    }
                });
                // Fails if the background fills the box (no ground inside it) or the text keeps its color (no gradient).
                t.expect("background-clip: text paints the gradient in the glyphs only", () -> {
                    LayoutBox b = Playtest.query(".gradient-text").get(0).box();
                    int ground = 0;
                    int left = 0;
                    int right = 0;
                    try (NativeImage image = screenshot()) {
                        for (float y = b.y() + 0.5f; y < b.y() + b.height(); y++) {
                            for (float x = b.x() + 0.5f; x < b.x() + b.width(); x++) {
                                int[] c = rgb(at(image, x, y));
                                if (is(at(image, x, y), GROUND)) {
                                    ground++;
                                } else if (x < b.x() + b.width() / 3) {
                                    left += c[0] > c[2] ? 1 : -1;
                                } else if (x > b.x() + b.width() * 2 / 3) {
                                    right += c[2] > c[0] ? 1 : -1;
                                }
                            }
                        }
                    }
                    return ground > b.width() * b.height() / 4 && left > 0 && right > 0;
                });
                t.hold(1000);
            });

            s.take("effects-shapes", t -> {
                t.click(".shapes-tab");
                t.waitUntil("the shape tiles show", () -> Playtest.query(".shape").size() == 12
                        && Playtest.query(".shape").get(0).box().width() > 0, Playtest.WAIT);
                t.pause(2);
                t.shot("effects-shapes");
                // Fails if corner-shape does not reach the masks: every corner would stay round. Crisp, so each GUI pixel is in or out.
                t.expect("each corner shape cuts its own corner", () -> {
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
                t.expect("crisp-edges steps the curve by whole GUI pixels", () -> cornerStepped(".shape-row.crisp .shape.round"));
                t.expect("auto antialiases the curve", () -> !cornerStepped(".shape-row.smooth .shape.round"));
                t.expect("a crisp shadow fades GUI pixel by GUI pixel", () -> {
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
                t.expect("font-family and text-transform change how wide the text is", () -> {
                    float plain = width(".font.plain");
                    return width(".font.uniform") != plain && width(".font.alt") != plain && width(".font.upper") > plain;
                });
                t.expect("minecraft:uniform, minecraft:alt and the bundled fonts are fonts the game has", () ->
                        Playtest.document().diagnostics().entries().stream().noneMatch(d -> d.code().id().equals("layout/unknown-font")));
                // Fails if a bundled font does not reach measuring: both lines would take the default font's width.
                t.expect("the two bundled fonts lay the same line out apart", () -> {
                    float orbitron = width(".font.orbitron");
                    return orbitron > 0 && orbitron != width(".font.mono");
                });
                // Fails if the face's glyphs, or the Chinese it lacks, are not drawn: part of the line stays dark.
                t.expect("a bundled font draws its glyphs, and the Chinese it lacks in the default font", () -> {
                    LayoutBox b = Playtest.box(".font.orbitron");
                    try (NativeImage image = screenshot()) {
                        return lit(image, b, 0, 0.5f) > 10 && lit(image, b, 0.8f, 1) > 10;
                    }
                });
                // Fails if a § is read as a formatting code when measuring: the line would take no room.
                t.expect("a § is text, measured as drawn", () -> width(".font.codes") > 0);
                // Fails if small text is measured at the size written rather than drawn at a crisp one: its
                // box would not match its glyphs, or strokes would come out of uneven thickness.
                t.expect("6px text takes its crisp size's width", () -> {
                    double gui = Minecraft.getInstance().getWindow().getGuiScale();
                    float crisp = com.azane.ixchel.mc.paint.McTextMeasurer.crispSize(6, gui);
                    return Math.abs(width(".font.small") - width(".font.plain") * crisp / 9) < 0.01f;
                });
                t.hold(1500);
            });

            s.take("effects-css", t -> {
                t.click(".css-tab");
                t.waitUntil("the CSS cells show", () -> Playtest.query(".cell").size() == 10
                        && Playtest.query(".cell").get(0).box().width() > 0, Playtest.WAIT);
                t.run(() -> Playtest.hoverAt(0, 0));
                t.pause(2);
                t.shot("effects-css");
                t.expect("the CSS tab reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
                // Fails if the outline is not drawn, drawn on the border edge, or its corners do not follow corner-shape.
                t.expect("outline draws a ring past its offset, its corners beveled", () -> {
                    LayoutBox b = Playtest.box(".outlined");
                    float y = b.y() + b.height() / 2;
                    try (NativeImage image = screenshot()) {
                        return is(at(image, b.x() - 3, y), OUTLINE) && is(at(image, b.x() - 1, y), GROUND)
                                && is(at(image, b.x() - 5.5f, y), GROUND)
                                && is(at(image, b.x() + b.width() / 2, b.y() - 3), OUTLINE)
                                && is(at(image, b.x() - 3.5f, b.y() - 3.5f), GROUND);
                    }
                });
                // Fails if measuring leaves the spacing out, or drawing does: the last glyphs would sit where they are unspaced.
                t.expect("letter-spacing widens Latin text by its spacing per character", () ->
                        spaced(".plain-latin", ".spaced-latin", 6, 2));
                t.expect("letter-spacing widens CJK text by its spacing per character", () ->
                        spaced(".plain-cjk", ".spaced-cjk", 2, 3));
                t.expect("inset places the box by all four sides", () -> {
                    LayoutBox f = Playtest.box(".frame"), i = Playtest.box(".inset");
                    return i.x() == f.x() + 11 && i.y() == f.y() + 5 && i.width() == f.width() - 22
                            && i.height() == f.height() - 10 && is(center(".inset", 0), OUTLINE);
                });
                t.expect("radial-gradient runs from its first color at the center to its last at the corner", () -> {
                    LayoutBox b = Playtest.box(".radial");
                    try (NativeImage image = screenshot()) {
                        return is(at(image, b.x() + b.width() / 2, b.y() + b.height() / 2), new int[]{255, 224, 112})
                                && is(at(image, b.x() + 0.5f, b.y() + 0.5f), new int[]{192, 48, 32});
                    }
                });
                // Fails if the wedges leave gaps or the angle starts anywhere but the top, clockwise.
                t.expect("conic-gradient turns clockwise from the top and covers its circle", () -> {
                    LayoutBox b = Playtest.box(".conic");
                    float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
                    try (NativeImage image = screenshot()) {
                        for (int r = 2; r <= 10; r += 2) {
                            for (int a = 0; a < 360; a += 15) {
                                double angle = Math.toRadians(a);
                                if (is(at(image, cx + (float) (r * Math.sin(angle)), cy - (float) (r * Math.cos(angle))), GROUND)) {
                                    return false;
                                }
                            }
                        }
                        return near(at(image, cx + 0.5f, cy - 9), rgb(224, 72, 72), 16)
                                && near(at(image, cx + 9, cy + 0.5f), rgb(110, 162, 72), 16)
                                && near(at(image, cx + 0.5f, cy + 9), rgb(72, 168, 148), 16)
                                && near(at(image, cx - 9, cy + 0.5f), rgb(110, 126, 186), 16);
                    }
                });
                t.expect("repeating-linear-gradient repeats its 8px stripes across the box", () -> {
                    LayoutBox b = Playtest.box(".repeating-linear");
                    float y = b.y() + b.height() / 2;
                    try (NativeImage image = screenshot()) {
                        return is(at(image, b.x() + 2, y), GOLD) && is(at(image, b.x() + 6, y), BLUE)
                                && is(at(image, b.x() + 34, y), GOLD) && is(at(image, b.x() + 38, y), BLUE);
                    }
                });
                t.expect("repeating-radial-gradient repeats its 6px rings out from the center", () -> {
                    LayoutBox b = Playtest.box(".repeating-radial");
                    float cx = b.x() + b.width() / 2, y = b.y() + b.height() / 2;
                    int[] white = {240, 240, 240};
                    try (NativeImage image = screenshot()) {
                        return is(at(image, cx + 1.5f, y), BLUE) && is(at(image, cx + 4.5f, y), white)
                                && is(at(image, cx + 7.5f, y), BLUE) && is(at(image, cx + 10.5f, y), white);
                    }
                });
                t.run(() -> slide[0] = row(".sliding"));
                t.pause(5);
                // Fails if keyframes do not reach background-position: the stripes would stand still.
                t.expect("animated background-position moves the stripes", () -> {
                    int[] now = row(".sliding");
                    boolean gold = false, blue = false, moved = false;
                    for (int i = 0; i < now.length; i++) {
                        gold |= is(now[i], GOLD);
                        blue |= is(now[i], BLUE);
                        moved |= !near(now[i], slide[0][i], 30);
                    }
                    return gold && blue && moved;
                });
                t.expect("aspect-ratio 3 / 1 makes a 42px wide box 14px tall", () ->
                        Math.abs(Playtest.box(".ratio").height() - 14) < 0.01f);
                // Fails if hit testing uses the layout box: the slanted corner outside it would not hover.
                t.run(() -> {
                    LayoutBox b = Playtest.box(".skewed");
                    pointTo(b.x() + b.width() + 3, b.y() + b.height() - 1.5f);
                });
                t.pause(2);
                t.expect("a skewed box hovers where it is drawn past its layout box", () -> {
                    LayoutBox b = Playtest.box(".skewed");
                    return Playtest.query(".skewed:hover").size() == 1
                            && is(pixel(b.x() + b.width() + 3, b.y() + b.height() - 1.5f), RED);
                });
                t.run(() -> {
                    LayoutBox b = Playtest.box(".skewed");
                    pointTo(b.x() + b.width() - 3, b.y() + 1.5f);
                });
                t.pause(2);
                t.expect("a skewed box does not hover in the part of its layout box it slants away from", () -> {
                    LayoutBox b = Playtest.box(".skewed");
                    return Playtest.query(".skewed:hover").isEmpty()
                            && is(pixel(b.x() + b.width() - 3, b.y() + 1.5f), GROUND) && is(center(".skewed", 0), GOLD);
                });
                t.run(() -> pointTo(0, 0));
                // Each meter is read right after its own click: in a recording the pointer's travel to the
                // second meter outlasts the first one's half-second delay.
                t.click(".meter.p1");
                t.pause(6);
                // Fails if a mirroring transform turns plain rectangles' quads away and culling drops them.
                t.expect("the mirrored meter shows the damage bar red where health was lost", () -> {
                    LayoutBox a = Playtest.box(".meter.p1");
                    int[] c = rgb(pixel(a.x() + a.width() * 0.13f, a.y() + a.height() / 2));
                    return c[0] > c[1] + 60;
                });
                t.click(".meter.p2");
                t.pause(6);
                t.shot("effects-css-hit");
                // Fails if transition-delay is lost: the damage bar would shrink with the health bar.
                t.expect("the health bar drops at once while the damage bar waits", () -> {
                    float meter = Playtest.box(".meter.p2").width();
                    float health = Playtest.box(".meter.p2 .health").width();
                    return Math.abs(health - meter * 0.8f) < 1 && Playtest.box(".meter.p2 .damage").width() > health + meter * 0.15f;
                });
                t.expect("the plain meter shows the damage bar red where health was lost", () -> {
                    LayoutBox b = Playtest.box(".meter.p2");
                    int[] c = rgb(pixel(b.x() + b.width() * 0.87f, b.y() + b.height() / 2));
                    return c[0] > c[1] + 60;
                });
                t.pause(30);
                t.expect("the damage bar then catches up", () ->
                        Math.abs(Playtest.box(".meter.p2 .damage").width() - Playtest.box(".meter.p2 .health").width()) < 0.5f);
                t.hold(1500);
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

    /** Moves the cursor to GUI point {@code (x, y)} and tells the document, so {@code :hover} follows. */
    private static void pointTo(float x, float y) {
        Playtest.hoverAt(x, y);
        Playtest.document().events().mouseMoved(x, y);
    }

    /** A point of the spotlight box clear of its caption: 10px in, 5px down. */
    private static float[] spot() {
        LayoutBox b = Playtest.query(".spotlight").get(0).box();
        return new float[]{b.x() + 10, b.y() + 5};
    }

    /**
     * Whether {@code spaced} is {@code count} characters of spacing {@code spacing} wider than {@code plain},
     * the same text unspaced, with the spacing rounded to whole screen pixels, and has ink past the
     * plain width: drawn spaced, not only measured so.
     */
    private static boolean spaced(String plain, String spaced, int count, float spacing) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        float used = (float) (Math.round(spacing * gui) / gui);
        float w = width(plain);
        LayoutBox b = Playtest.box(spaced);
        if (Math.abs(b.width() - w - count * used) > 0.01f) {
            return false;
        }
        try (NativeImage image = screenshot()) {
            for (float y = b.y() + 0.25f; y < b.y() + b.height(); y += 0.5f) {
                for (float x = b.x() + w + 0.25f; x < b.x() + b.width(); x += 0.5f) {
                    if (rgb(at(image, x, y))[0] > 180) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** The pixels across the middle of {@code selector}, every GUI pixel. */
    private static int[] row(String selector) {
        LayoutBox b = Playtest.box(selector);
        int[] row = new int[(int) b.width()];
        try (NativeImage image = screenshot()) {
            for (int i = 0; i < row.length; i++) {
                row[i] = at(image, b.x() + i + 0.5f, b.y() + b.height() / 2);
            }
        }
        return row;
    }

    /** The ABGR pixel of {@code (r, g, b)}, as {@link NativeImage} keeps it. */
    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | b << 16 | g << 8 | r;
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

    /** Whether {@code selector}'s gold box shows at {@code (inX, inY)} and the ground at {@code (outX, outY)}, offsets in its box. */
    private static boolean clipped(NativeImage image, String selector, float inX, float inY, float outX, float outY) {
        LayoutBox b = Playtest.query(selector).get(0).box();
        return is(at(image, b.x() + inX, b.y() + inY), GOLD) && is(at(image, b.x() + outX, b.y() + outY), GROUND);
    }

    private static NativeImage screenshot() {
        return Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
    }

    /** The screen pixels in {@code b}, between the fractions {@code from} and {@code to} of its width, lit like text. */
    private static int lit(NativeImage image, LayoutBox b, float from, float to) {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        int n = 0;
        for (int y = (int) (b.y() * s); y < (int) ((b.y() + b.height()) * s); y++) {
            for (int x = (int) ((b.x() + b.width() * from) * s); x < (int) ((b.x() + b.width() * to) * s); x++) {
                int[] c = rgb(image.getPixelRGBA(x, y));
                if (c[0] > 120 && c[1] > 120 && c[2] > 120) {
                    n++;
                }
            }
        }
        return n;
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

    /**
     * The glint button's pixels 1px inside its edge all the way round, every 2px, clear of its 4px
     * rounded corners: top, right, bottom, left.
     */
    private static int[] glintRing() {
        LayoutBox b = Playtest.query(".glint").get(0).box();
        float left = b.x() + 1, right = b.x() + b.width() - 1, top = b.y() + 1, bottom = b.y() + b.height() - 1;
        int across = (int) ((b.width() - 8) / 2), down = (int) ((b.height() - 8) / 2);
        int[] ring = new int[2 * across + 2 * down];
        try (NativeImage image = screenshot()) {
            for (int i = 0; i < across; i++) {
                ring[i] = at(image, b.x() + 4 + 2 * i, top);
                ring[across + down + i] = at(image, b.x() + 4 + 2 * i, bottom);
            }
            for (int i = 0; i < down; i++) {
                ring[across + i] = at(image, right, b.y() + 4 + 2 * i);
                ring[2 * across + down + i] = at(image, left, b.y() + 4 + 2 * i);
            }
        }
        return ring;
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
