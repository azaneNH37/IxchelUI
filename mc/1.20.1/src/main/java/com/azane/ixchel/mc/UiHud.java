package com.azane.ixchel.mc;

import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.runtime.UiDocument;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-only UI over the whole GUI-scaled screen; the loader layer calls {@link #render} from its
 * HUD layer. Each diagnostic of the document (load warnings, errors found while running) is logged
 * once.
 */
public final class UiHud {
    private static final Logger LOG = LogUtils.getLogger();

    private final UiDocument document;
    private final McPainter painter = new McPainter(Minecraft.getInstance().font);
    private int logged;

    public UiHud(UiDocument document) {
        this.document = document;
    }

    public void render(GuiGraphics g) {
        painter.paint(g, document.frame(g.guiWidth(), g.guiHeight()));
        List<Diagnostic> entries = document.diagnostics().entries();
        for (; logged < entries.size(); logged++) {
            LOG.warn("Ixchel UI: {}", entries.get(logged));
        }
    }

    public UiDocument document() {
        return document;
    }

    /** Errors found after load, such as a derived value that threw; empty for a healthy document. */
    public List<Diagnostic> runtimeErrors() {
        if (document.diagnostics().errorCount() == 0) {
            return List.of();
        }
        List<Diagnostic> out = new ArrayList<>();
        for (Diagnostic d : document.diagnostics().entries()) {
            if (d.severity() == Diagnostic.Severity.ERROR) {
                out.add(d);
            }
        }
        return out;
    }
}
