package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.spell.SpellWheel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * {@code -Pplaytest=spell}: the wheel lays out eight sectors and both rune rings; the pointer's
 * direction picks a sector, the dead zone none; releasing R casts and closes, spending mana and
 * starting the cooldown, which shows on reopening; a spell costing more than the mana left goes
 * invalid and does not cast; a click casts too. {@code -Precord=spell} records it.
 */
public final class SpellPlaytest {
    /** Distance from the screen's center the pointer aims from, in GUI pixels. */
    private static final float AIM = 60;

    private SpellPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("spell", s -> {
            s.run(SpellWheel::open);
            s.waitUntil("the wheel opens", Playtest::isOpen, Playtest.WAIT);
            s.expect("the wheel reports no errors", () -> Playtest.document().diagnostics().errorCount() == 0);
            s.expect("eight sectors and two rings of runes", () -> Playtest.query(".sector").size() == 8
                    && Playtest.query(".ring.outer .rune").size() == 24 && Playtest.query(".ring.inner .rune").size() == 14);
            s.take("spell", t -> {
                t.hoverAt(() -> toward(0));
                t.hold(400);
                // Fails if the dead zone picks a sector.
                t.hoverAt(() -> toward(-1));
                t.pause(2);
                t.expect("at the center nothing is picked and the hint shows", () ->
                        Playtest.query(".sector.selected").isEmpty() && Playtest.query(".hint.shown").size() == 1);
                t.hold(500);
                // Around the wheel: each direction picks its own sector.
                for (int i = 0; i < 8; i++) {
                    int sector = i;
                    t.hoverAt(() -> toward(sector));
                    t.pause(2);
                    t.expect("pointing at sector " + sector + " picks it", () -> selected() == sector);
                    t.hold(250);
                }
                t.hoverAt(() -> toward(0));
                t.pause(2);
                t.expect("the readout names the fireball and its price", () ->
                        Playtest.text(".readout.shown .name").equals("Fireball")
                                && Playtest.text(".readout.shown .price").equals("20 mana"));
                t.hold(500);
                t.key(GLFW.GLFW_KEY_R);
                t.pause(2);
                t.expect("releasing R closes the wheel", () -> !Playtest.isOpen());
                t.hold(400);
                t.run(SpellWheel::open);
                t.waitUntil("the wheel opens again", Playtest::isOpen, Playtest.WAIT);
                // Fails if casting does not spend mana or start the cooldown.
                t.expect("the fireball cost 20 mana and is cooling down", () -> mana() >= 80 && mana() < 90
                        && cooling(0) && sectorText(0, ".seconds").length() > 0);
                t.hoverAt(() -> toward(7));
                t.hold(500);
                t.key(GLFW.GLFW_KEY_R);
                t.pause(2);
                t.hold(300);
                t.run(SpellWheel::open);
                t.waitUntil("the wheel opens a third time", Playtest::isOpen, Playtest.WAIT);
                t.expect("the storm took 60 mana and is cooling down", () -> mana() < 40 && cooling(7));
                t.hoverAt(() -> toward(3));
                t.pause(2);
                // Fails if the sector does not follow the mana left.
                t.expect("Mend costs more than is left: invalid and blocked", () ->
                        Playtest.query(".sector.selected.ix-invalid").size() == 1
                                && Playtest.query(".readout.shown .name.blocked").size() == 1);
                t.hold(800);
                t.key(GLFW.GLFW_KEY_R);
                t.pause(2);
                t.run(SpellWheel::open);
                t.waitUntil("the wheel opens a fourth time", Playtest::isOpen, Playtest.WAIT);
                t.expect("Mend did not cast", () -> !cooling(3) && mana() < 40);
                t.hold(300);
                // A click casts as the release does.
                t.clickNth(".sector", 6);
                t.pause(2);
                t.expect("clicking Light casts it and closes the wheel", () -> !Playtest.isOpen());
                t.run(SpellWheel::open);
                t.waitUntil("the wheel opens a fifth time", Playtest::isOpen, Playtest.WAIT);
                t.expect("Light and the storm are cooling down", () -> cooling(6) && cooling(7));
                t.hoverAt(() -> toward(5));
                t.hold(1500);
            });
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    /** The point {@link #AIM} pixels from the screen's center toward sector {@code i}, or the center for -1. */
    private static float[] toward(int i) {
        var w = Minecraft.getInstance().getWindow();
        float cx = w.getGuiScaledWidth() / 2f;
        float cy = w.getGuiScaledHeight() / 2f;
        if (i < 0) {
            return new float[]{cx, cy};
        }
        double a = Math.toRadians(i * 45);
        return new float[]{cx + (float) (AIM * Math.sin(a)), cy - (float) (AIM * Math.cos(a))};
    }

    private static int selected() {
        var sectors = Playtest.query(".sector");
        for (int i = 0; i < sectors.size(); i++) {
            if (sectors.get(i).hasClass("selected")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean cooling(int i) {
        return Playtest.query(".sector").get(i).hasClass("cooling");
    }

    private static String sectorText(int i, String part) {
        return Playtest.texts(".sector " + part).get(i);
    }

    private static int mana() {
        String text = Playtest.text(".mana-text");
        return Integer.parseInt(text.substring(0, text.indexOf(' ')));
    }
}
