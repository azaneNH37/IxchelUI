package com.azane.ixchel.testmod.probe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Player inventory whose slots are moved by writing {@code Slot.x/y}; vanilla keeps doing hit
 * testing, clicks, drags and item drawing at the new positions.
 *
 * <p>F6 cycles the layout, F7 toggles per-slot transformed drawing (protected {@code renderSlot}
 * wrapped in a 0.75 scale), F8 toggles an overlay drawn at {@link #OVERLAY_Z}.
 */
final class SlotProbeScreen extends AbstractContainerScreen<InventoryMenu> {
    private enum Layout { VANILLA, MIRRORED, ANIMATED }

    /** Above slot items (100 + item depth), below the carried item (232 + item depth). */
    private static final int OVERLAY_Z = 300;
    private static final int REPORT_FRAMES = 300;

    private final int[] baseX;
    private final int[] baseY;
    private Layout layout = Layout.VANILLA;
    private boolean selfDraw;
    private boolean overlay;
    private long renderNanos;
    private int frames;

    SlotProbeScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.literal("Slot probe"));
        int n = menu.slots.size();
        baseX = new int[n];
        baseY = new int[n];
        for (int i = 0; i < n; i++) {
            baseX[i] = menu.slots.get(i).x;
            baseY[i] = menu.slots.get(i).y;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        applyLayout();
        long t0 = System.nanoTime();
        super.render(g, mouseX, mouseY, partialTick);
        renderNanos += System.nanoTime() - t0;
        if (++frames == REPORT_FRAMES) {
            ProbeClient.LOG.info("[ixchel-probe] render {} us/frame over {} frames ({})",
                    String.format("%.1f", renderNanos / 1000.0 / frames), frames, state());
            frames = 0;
            renderNanos = 0;
        }
        renderTooltip(g, mouseX, mouseY);
        g.drawString(font, state(), 4, height - 12, 0xFFFFFF);
    }

    private String state() {
        return "layout=" + layout + " selfDraw=" + selfDraw + " overlay=" + overlay;
    }

    private void applyLayout() {
        int dx = layout == Layout.ANIMATED ? Math.round(12 * Mth.sin((System.currentTimeMillis() % 4000) / 4000f * Mth.TWO_PI)) : 0;
        for (int i = 0; i < baseX.length; i++) {
            int x = layout == Layout.MIRRORED ? imageWidth - 16 - baseX[i] : baseX[i] + dx;
            SlotWriter.set(menu.slots.get(i), x, baseY[i]);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        for (Slot s : menu.slots) {
            int x = leftPos + s.x;
            int y = topPos + s.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        if (!selfDraw) {
            super.renderSlot(g, slot);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(slot.x + 8, slot.y + 8, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.pose().translate(-slot.x - 8, -slot.y - 8, 0);
        super.renderSlot(g, slot);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (overlay) {
            g.pose().pushPose();
            g.pose().translate(0, 0, OVERLAY_Z);
            for (Slot s : menu.slots) {
                g.fill(s.x, s.y, s.x + 8, s.y + 16, 0x80FF0000);
            }
            g.pose().popPose();
        }
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_F6 -> layout = Layout.values()[(layout.ordinal() + 1) % Layout.values().length];
            case GLFW.GLFW_KEY_F7 -> selfDraw = !selfDraw;
            case GLFW.GLFW_KEY_F8 -> overlay = !overlay;
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        frames = 0;
        renderNanos = 0;
        ProbeClient.LOG.info("[ixchel-probe] {}", state());
        return true;
    }

    @Override
    public void removed() {
        // The menu outlives this screen; put the slots back where the vanilla inventory expects them.
        for (int i = 0; i < baseX.length; i++) {
            SlotWriter.set(menu.slots.get(i), baseX[i], baseY[i]);
        }
        super.removed();
    }
}
