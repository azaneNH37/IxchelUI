package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.guide.Guide;
import com.azane.ixchel.sample.waypoints.Waypoints;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code -Pplaytest=language}: switching the game to Chinese, as the language screen does, gives a
 * reopened screen the zh_cn translations (view {@code t}, {@code t:attr}, marks, arguments and
 * Java components alike) and the document root {@code lang="zh-cn"}; the Chinese guide loads without
 * warnings; switching back restores English.
 */
public final class LanguagePlaytest {
    private static CompletableFuture<Void> reload = CompletableFuture.completedFuture(null);

    private LanguagePlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("language", s -> {
            s.run(() -> {
                Waypoints.clear();
                WaypointsScreen.open();
            });
            s.waitUntil("English to start with", () -> Playtest.isOpen() && Playtest.text(".ix-title").equals("Waypoints")
                    && Playtest.text(".count").equals("0 waypoints") && lang().equals("en-us"), Playtest.WAIT);

            s.run(() -> select("zh_cn"));
            s.waitUntil("the resources reload", LanguagePlaytest::reloaded, 4 * Playtest.WAIT);
            s.run(() -> {
                Minecraft.getInstance().setScreen(null);
                WaypointsScreen.open();
            });
            s.waitUntil("the reopened screen is Chinese", () -> Playtest.isOpen() && Playtest.text(".ix-title").equals("路径点")
                    && Playtest.text(".count").equals("0 个路径点") && lang().equals("zh-cn"), Playtest.WAIT);
            s.click("button");
            s.waitUntil("an argument fills the message; a Java component names the waypoint",
                    () -> Playtest.text(".count").equals("1 个路径点") && Playtest.text(".row .name").equals("路径点 1"), Playtest.WAIT);
            s.expect("t:title translates the attribute", () -> Playtest.attribute("button", "title").equals("保存你脚下的方块，限于当前维度"));
            s.shot("language-waypoints-zh");

            s.run(() -> {
                Minecraft.getInstance().setScreen(null);
                Guide.open();
            });
            s.waitUntil("the contents in Chinese, with marks, without warnings", () -> Playtest.isOpen()
                    && Playtest.texts(".toc a").equals(List.of("电路", "活塞", "存储", "排版"))
                    && Playtest.text(".text a").equals("链接")
                    && Playtest.document().diagnostics().entries().isEmpty(), Playtest.WAIT);
            s.shot("language-guide-zh");

            s.run(() -> {
                Minecraft.getInstance().setScreen(null);
                select("en_us");
            });
            s.waitUntil("the resources reload", LanguagePlaytest::reloaded, 4 * Playtest.WAIT);
            s.run(() -> {
                Waypoints.clear();
                WaypointsScreen.open();
            });
            s.waitUntil("English again", () -> Playtest.isOpen() && Playtest.text(".ix-title").equals("Waypoints")
                    && lang().equals("en-us"), Playtest.WAIT);
            s.run(() -> Minecraft.getInstance().setScreen(null));
        });
    }

    /** What the language screen does on Done, without saving the options file. */
    private static void select(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        reload = mc.reloadResourcePacks();
    }

    /** The reload is done and its overlay, which swallows input while it fades, is gone. */
    private static boolean reloaded() {
        return reload.isDone() && Minecraft.getInstance().getOverlay() == null;
    }

    private static String lang() {
        Element root = Playtest.document().root();
        String lang = root.attribute("lang");
        return lang == null ? "" : lang;
    }
}
