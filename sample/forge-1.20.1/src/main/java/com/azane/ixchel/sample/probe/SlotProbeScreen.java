package com.azane.ixchel.sample.probe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Player inventory whose slots are moved by writing {@code Slot.x/y}; vanilla keeps doing hit
 * testing, clicks, drags and item drawing at the new positions.
 *
 * <p>F6 cycles the layout, F7 toggles self-drawn items (zero-area scissor over the vanilla slot
 * loop, items redrawn at 0.75 scale in {@link #renderLabels}), F8 toggles an overlay drawn at
 * {@link #OVERLAY_Z}, F9 toggles moving the scissor renderable back to the end every frame; turning
 * it off rebuilds the widgets so the event-appended button sits after the scissor again.
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
    private boolean keepScissorLast = true;
    private final Renderable scissorOn = (g, mx, my, pt) -> {
        if (selfDraw) {
            g.enableScissor(0, 0, 0, 0);
        }
    };
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
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("own btn"), b -> ProbeClient.LOG.info("[ixchel-probe] own btn clicked"))
                .bounds(4, 4, 80, 20).build());
        addRenderableOnly(scissorOn);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        applyLayout();
        if (keepScissorLast) {
            renderables.remove(scissorOn);
            renderables.add(scissorOn);
        }
        renderBackground(g);
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
        return "layout=" + layout + " selfDraw=" + selfDraw + " overlay=" + overlay + " scissorLast=" + keepScissorLast;
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
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (selfDraw) {
            g.disableScissor();
            for (Slot s : menu.slots) {
                if (!s.isActive()) {
                    continue;
                }
                ItemStack stack = s.getItem();
                g.pose().pushPose();
                g.pose().translate(s.x + 8, s.y + 8, 100);
                g.pose().scale(0.75f, 0.75f, 1);
                g.pose().translate(-8, -8, 0);
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0);
                g.pose().popPose();
            }
            if (hoveredSlot != null) {
                renderSlotHighlight(g, hoveredSlot.x, hoveredSlot.y, 0);
            }
        }
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
            case GLFW.GLFW_KEY_F9 -> {
                keepScissorLast = !keepScissorLast;
                if (!keepScissorLast) {
                    rebuildWidgets();
                }
            }
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
