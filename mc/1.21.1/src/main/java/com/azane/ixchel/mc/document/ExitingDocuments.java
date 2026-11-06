package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.runtime.UiDocument;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Documents of closed screens, still drawn until their exit ended. The screen is gone at once, so
 * input is back with the game or the next screen; the loader layer draws what is left from its GUI
 * and screen render events, over whatever shows. Each library copy has its own. Client thread only.
 */
@HostApi
public final class ExitingDocuments {
    private static final Logger LOG = LogUtils.getLogger();
    /** Above what the HUD or the next screen drew, which may include items and tooltips. */
    private static final float Z = 1000;
    private static final List<Exit> EXITS = new ArrayList<>();
    private static boolean hooked;
    private static boolean warned;

    private ExitingDocuments() {
    }

    private static final class Exit {
        final HostedDocument doc;
        boolean done;

        Exit(HostedDocument doc) {
            this.doc = doc;
        }
    }

    /** The loader layer calls {@link #drawOverGame} and {@link #drawOverScreen} from now on. */
    public static void hooked() {
        hooked = true;
    }

    /** Starts the exit of the closed {@code doc}, nothing when it cannot animate. */
    public static void add(HostedDocument doc) {
        UiDocument d = doc.document();
        if (d == null) {
            return;
        }
        Exit exit = new Exit(doc);
        d.exit(() -> exit.done = true);
        if (exit.done) {
            return;
        }
        if (hooked) {
            EXITS.add(exit);
        } else if (!warned) {
            warned = true;
            LOG.warn("Ixchel UI: a screen closed without its exit animation: the host never called the loader glue's installScreenExits()");
        }
    }

    /** Whether a closed screen's document is still drawn. */
    public static boolean any() {
        return !EXITS.isEmpty();
    }

    /** From the loader's event after the GUI layers: with no screen open, what is left draws over the game. */
    public static void drawOverGame(GuiGraphics g) {
        if (Minecraft.getInstance().screen == null) {
            draw(g);
        }
    }

    /** From the loader's event after a screen rendered. */
    public static void drawOverScreen(GuiGraphics g) {
        draw(g);
    }

    private static void draw(GuiGraphics g) {
        for (Iterator<Exit> it = EXITS.iterator(); it.hasNext(); ) {
            Exit exit = it.next();
            exit.doc.frame(g.guiWidth(), g.guiHeight(), Float.NaN, Float.NaN);
            if (exit.done) {
                // That frame shows the document present again; it is over.
                it.remove();
                continue;
            }
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(0, 0, Z);
            exit.doc.paint(g, Float.NaN, Float.NaN);
            g.flush();
            pose.popPose();
        }
    }
}
